package com.meridian.payments;

import com.meridian.payments.entity.JobExecutionLog;
import com.meridian.payments.entity.ScheduledReport;
import com.meridian.payments.repository.AlertRepository;
import com.meridian.payments.repository.JobExecutionLogRepository;
import com.meridian.payments.repository.RetryHistoryRepository;
import com.meridian.payments.repository.ScheduledReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScheduledReportRepository scheduledReportRepository;

    @Autowired
    private JobExecutionLogRepository jobExecutionLogRepository;

    @Autowired
    private RetryHistoryRepository retryHistoryRepository;

    @Autowired
    private AlertRepository alertRepository;

    @BeforeEach
    void setupDatabase() {
        alertRepository.deleteAll();
        retryHistoryRepository.deleteAll();
        jobExecutionLogRepository.deleteAll();
        scheduledReportRepository.deleteAll();

        // 1. Schedule 101: HEALTHY (TIMEOUT then SUCCESS)
        ScheduledReport s101 = scheduledReportRepository.save(ScheduledReport.builder()
                .tenantId("TENANT-001").reportId("REP-MONTHLY-STMT").scheduledTime(Instant.now().minusSeconds(7200)).build());

        jobExecutionLogRepository.save(JobExecutionLog.builder()
                .scheduleId(s101.getId()).tenantId("TENANT-001").executionTime(Instant.now().minusSeconds(5000))
                .status("TIMEOUT").errorCode("NETWORK_TIMEOUT").build());

        jobExecutionLogRepository.save(JobExecutionLog.builder()
                .scheduleId(s101.getId()).tenantId("TENANT-001").executionTime(Instant.now().minusSeconds(3000))
                .status("SUCCESS").build());

        // 2. Schedule 102: NON-RETRIABLE FAILURE (REPORT_NOT_FOUND)
        ScheduledReport s102 = scheduledReportRepository.save(ScheduledReport.builder()
                .tenantId("TENANT-002").reportId("REP-QUARTERLY-TAX").scheduledTime(Instant.now().minusSeconds(3600)).build());

        jobExecutionLogRepository.save(JobExecutionLog.builder()
                .scheduleId(s102.getId()).tenantId("TENANT-002").executionTime(Instant.now().minusSeconds(3000))
                .status("FAILED").errorCode("REPORT_NOT_FOUND").errorMessage("Report not found").build());

        // 3. Schedule 103: RETRIABLE FAILURE (TIMEOUT, 0 retries)
        ScheduledReport s103 = scheduledReportRepository.save(ScheduledReport.builder()
                .tenantId("TENANT-003").reportId("REP-DAILY").scheduledTime(Instant.now().minusSeconds(1800)).build());

        jobExecutionLogRepository.save(JobExecutionLog.builder()
                .scheduleId(s103.getId()).tenantId("TENANT-003").executionTime(Instant.now().minusSeconds(1000))
                .status("TIMEOUT").errorCode("TIMEOUT").build());
    }

    @Test
    @DisplayName("End-to-End: GET /api/jobs/failed should exclude Schedule 101 and include 102 and 103")
    void shouldReturnFailedJobsExcludingHealthy() throws Exception {
        mockMvc.perform(get("/api/jobs/failed").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.reportId == 'REP-MONTHLY-STMT')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.reportId == 'REP-QUARTERLY-TAX')]").exists())
                .andExpect(jsonPath("$[?(@.reportId == 'REP-DAILY')]").exists());
    }

    @Test
    @DisplayName("End-to-End: POST /api/jobs/{id}/retry should execute retry and update execution logs")
    void shouldExecuteRetryAndPersistResults() throws Exception {
        ScheduledReport s103 = scheduledReportRepository.findAll().stream()
                .filter(s -> "REP-DAILY".equals(s.getReportId()))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/jobs/" + s103.getId() + "/retry").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.retryNumber").value(1));

        // Verify database state: Schedule 103 now has a SUCCESS log
        long successCount = jobExecutionLogRepository.countByScheduleIdAndStatusIn(
                s103.getId(), java.util.Set.of("SUCCESS"));
        assertThat(successCount).isEqualTo(1L);

        // Subsequent call to /api/jobs/failed should no longer list s103
        mockMvc.perform(get("/api/jobs/failed").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reportId == 'REP-DAILY')]").doesNotExist());
    }

    @Test
    @DisplayName("End-to-End: Retrying non-retriable job should reject with 400 and persist an Alert")
    void shouldRejectNonRetriableAndGenerateAlert() throws Exception {
        ScheduledReport s102 = scheduledReportRepository.findAll().stream()
                .filter(s -> "REP-QUARTERLY-TAX".equals(s.getReportId()))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/jobs/" + s102.getId() + "/retry").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot retry non-retriable failure")));

        // Verify alert created
        mockMvc.perform(get("/api/alerts").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.scheduleId == " + s102.getId() + ")]").exists());
    }
}
