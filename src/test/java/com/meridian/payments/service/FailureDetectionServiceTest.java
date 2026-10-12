package com.meridian.payments.service;

import com.meridian.payments.dto.FailedJobDTO;
import com.meridian.payments.dto.UnresolvedJobDTO;
import com.meridian.payments.entity.JobExecutionLog;
import com.meridian.payments.entity.ScheduledReport;
import com.meridian.payments.repository.RetryHistoryRepository;
import com.meridian.payments.repository.ScheduledReportRepository;
import com.meridian.payments.strategy.DefaultStatusClassificationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailureDetectionServiceTest {

    @Mock
    private ScheduledReportRepository scheduledReportRepository;

    @Mock
    private RetryHistoryRepository retryHistoryRepository;

    private DefaultStatusClassificationStrategy classificationStrategy;
    private FailureDetectionService failureDetectionService;

    @BeforeEach
    void setUp() {
        classificationStrategy = new DefaultStatusClassificationStrategy();
        failureDetectionService = new FailureDetectionService(
                scheduledReportRepository,
                retryHistoryRepository,
                classificationStrategy
        );
        ReflectionTestUtils.setField(failureDetectionService, "maxRetries", 3);
    }

    @Test
    @DisplayName("Rule 1: Schedule with initial failure and subsequent SUCCESS should be considered HEALTHY")
    void shouldTreatScheduleWithAtLeastOneSuccessAsHealthy() {
        // Schedule 101 from user sample
        ScheduledReport schedule101 = ScheduledReport.builder()
                .id(101L)
                .tenantId("TENANT-001")
                .reportId("REP-MONTHLY-STMT")
                .scheduledTime(Instant.now().minusSeconds(3600))
                .executionLogs(new ArrayList<>())
                .build();

        JobExecutionLog log1 = JobExecutionLog.builder()
                .id(1L)
                .scheduleId(101L)
                .tenantId("TENANT-001")
                .executionTime(Instant.now().minusSeconds(1800))
                .status("TIMEOUT")
                .errorCode("NETWORK_TIMEOUT")
                .build();

        JobExecutionLog log2 = JobExecutionLog.builder()
                .id(2L)
                .scheduleId(101L)
                .tenantId("TENANT-001")
                .executionTime(Instant.now().minusSeconds(600))
                .status("SUCCESS")
                .build();

        // Execution logs ordered by executionTime DESC, id DESC
        schedule101.getExecutionLogs().add(log2);
        schedule101.getExecutionLogs().add(log1);

        when(scheduledReportRepository.findAllWithExecutionLogs()).thenReturn(List.of(schedule101));

        List<FailedJobDTO> failedJobs = failureDetectionService.detectFailedJobs();
        assertThat(failedJobs).isEmpty();
    }

    @Test
    @DisplayName("Rule 2: Schedule with no success and latest FAILED/REPORT_NOT_FOUND should be marked as FAILED with canRetry=false")
    void shouldDetectNonRetriableFailure() {
        // Schedule 102 from user sample
        ScheduledReport schedule102 = ScheduledReport.builder()
                .id(102L)
                .tenantId("TENANT-002")
                .reportId("REP-QUARTERLY-TAX")
                .scheduledTime(Instant.now().minusSeconds(3600))
                .executionLogs(new ArrayList<>())
                .build();

        JobExecutionLog log = JobExecutionLog.builder()
                .id(3L)
                .scheduleId(102L)
                .tenantId("TENANT-002")
                .executionTime(Instant.now().minusSeconds(1800))
                .status("FAILED")
                .errorCode("REPORT_NOT_FOUND")
                .errorMessage("Report definition missing")
                .build();

        schedule102.getExecutionLogs().add(log);

        when(scheduledReportRepository.findAllWithExecutionLogs()).thenReturn(List.of(schedule102));
        when(retryHistoryRepository.countByScheduleId(102L)).thenReturn(0L);

        List<FailedJobDTO> failedJobs = failureDetectionService.detectFailedJobs();
        assertThat(failedJobs).hasSize(1);
        FailedJobDTO failedJob = failedJobs.get(0);
        assertThat(failedJob.getScheduleId()).isEqualTo(102L);
        assertThat(failedJob.getClassification()).isEqualTo("NON_RETRIABLE_FAILURE");
        assertThat(failedJob.isCanRetry()).isFalse();
        assertThat(failedJob.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should detect retriable failure and allow retry when retryCount < maxRetries")
    void shouldDetectRetriableFailure() {
        ScheduledReport schedule103 = ScheduledReport.builder()
                .id(103L)
                .tenantId("TENANT-003")
                .reportId("REP-DAILY-SETTLEMENT")
                .scheduledTime(Instant.now().minusSeconds(3600))
                .executionLogs(new ArrayList<>())
                .build();

        JobExecutionLog log = JobExecutionLog.builder()
                .id(4L)
                .scheduleId(103L)
                .tenantId("TENANT-003")
                .executionTime(Instant.now().minusSeconds(1800))
                .status("TIMEOUT")
                .errorCode("ERR_GATEWAY_TIMEOUT")
                .build();

        schedule103.getExecutionLogs().add(log);

        when(scheduledReportRepository.findAllWithExecutionLogs()).thenReturn(List.of(schedule103));
        when(retryHistoryRepository.countByScheduleId(103L)).thenReturn(1L);

        List<FailedJobDTO> failedJobs = failureDetectionService.detectFailedJobs();
        assertThat(failedJobs).hasSize(1);
        FailedJobDTO failedJob = failedJobs.get(0);
        assertThat(failedJob.getClassification()).isEqualTo("RETRIABLE_FAILURE");
        assertThat(failedJob.isCanRetry()).isTrue();
        assertThat(failedJob.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should flag unresolved jobs correctly for non-retriable, max retries reached, and unknown statuses")
    void shouldDetectUnresolvedJobs() {
        // Schedule 102: Non-retriable
        ScheduledReport s102 = ScheduledReport.builder().id(102L).tenantId("T2").reportId("R2").scheduledTime(Instant.now()).executionLogs(new ArrayList<>()).build();
        s102.getExecutionLogs().add(JobExecutionLog.builder().id(2L).scheduleId(102L).tenantId("T2").executionTime(Instant.now()).status("FAILED").errorCode("REPORT_NOT_FOUND").build());

        // Schedule 105: 3 retries exhausted
        ScheduledReport s105 = ScheduledReport.builder().id(105L).tenantId("T5").reportId("R5").scheduledTime(Instant.now()).executionLogs(new ArrayList<>()).build();
        s105.getExecutionLogs().add(JobExecutionLog.builder().id(5L).scheduleId(105L).tenantId("T5").executionTime(Instant.now()).status("TIMEOUT").errorCode("TIMEOUT").build());

        when(scheduledReportRepository.findAllWithExecutionLogs()).thenReturn(List.of(s102, s105));
        when(retryHistoryRepository.countByScheduleId(102L)).thenReturn(0L);
        when(retryHistoryRepository.countByScheduleId(105L)).thenReturn(3L);

        List<UnresolvedJobDTO> unresolved = failureDetectionService.detectUnresolvedJobs();
        assertThat(unresolved).hasSize(2);
        assertThat(unresolved.stream().map(UnresolvedJobDTO::getScheduleId)).containsExactlyInAnyOrder(102L, 105L);
    }
}
