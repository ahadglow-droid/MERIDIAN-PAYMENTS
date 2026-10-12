package com.meridian.payments.controller;

import com.meridian.payments.dto.AlertDTO;
import com.meridian.payments.dto.FailedJobDTO;
import com.meridian.payments.dto.RetryResultDTO;
import com.meridian.payments.dto.UnresolvedJobDTO;
import com.meridian.payments.exception.GlobalExceptionHandler;
import com.meridian.payments.exception.MaxRetriesExceededException;
import com.meridian.payments.exception.NonRetriableJobException;
import com.meridian.payments.exception.ResourceNotFoundException;
import com.meridian.payments.service.AlertService;
import com.meridian.payments.service.FailureDetectionService;
import com.meridian.payments.service.RetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobRecoveryController.class)
@Import(GlobalExceptionHandler.class)
class JobRecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FailureDetectionService failureDetectionService;

    @MockBean
    private RetryService retryService;

    @MockBean
    private AlertService alertService;

    @Test
    @DisplayName("GET /api/jobs/failed should return 200 with list of failed jobs")
    void shouldReturnFailedJobs() throws Exception {
        FailedJobDTO failedJob = FailedJobDTO.builder()
                .scheduleId(102L)
                .tenantId("TENANT-002")
                .reportId("REP-QUARTERLY-TAX")
                .scheduledTime(Instant.now())
                .latestStatus("FAILED")
                .classification("NON_RETRIABLE_FAILURE")
                .errorCode("REPORT_NOT_FOUND")
                .retryCount(0)
                .canRetry(false)
                .build();

        when(failureDetectionService.detectFailedJobs()).thenReturn(List.of(failedJob));

        mockMvc.perform(get("/api/jobs/failed")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(102))
                .andExpect(jsonPath("$[0].tenantId").value("TENANT-002"))
                .andExpect(jsonPath("$[0].classification").value("NON_RETRIABLE_FAILURE"))
                .andExpect(jsonPath("$[0].canRetry").value(false));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/retry should return 200 with retry result DTO")
    void shouldRetryJobSuccessfully() throws Exception {
        RetryResultDTO result = RetryResultDTO.builder()
                .scheduleId(103L)
                .jobLogId(4L)
                .retryNumber(1)
                .result("SUCCESS")
                .message("Report generation retry completed successfully.")
                .timestamp(Instant.now())
                .build();

        when(retryService.retryJob(103L)).thenReturn(result);

        mockMvc.perform(post("/api/jobs/103/retry")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(103))
                .andExpect(jsonPath("$.retryNumber").value(1))
                .andExpect(jsonPath("$.result").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/retry should return 404 when schedule not found")
    void shouldReturn404WhenScheduleNotFound() throws Exception {
        when(retryService.retryJob(999L)).thenThrow(new ResourceNotFoundException("Scheduled report not found for id: 999"));

        mockMvc.perform(post("/api/jobs/999/retry")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Scheduled report not found for id: 999"));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/retry should return 400 when max retries exceeded")
    void shouldReturn400WhenMaxRetriesExceeded() throws Exception {
        when(retryService.retryJob(105L)).thenThrow(new MaxRetriesExceededException("Retry limit of 3 exceeded for schedule id: 105"));

        mockMvc.perform(post("/api/jobs/105/retry")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Retry limit of 3 exceeded for schedule id: 105"));
    }

    @Test
    @DisplayName("POST /api/jobs/{id}/retry should return 400 when failure is non-retriable")
    void shouldReturn400WhenNonRetriable() throws Exception {
        when(retryService.retryJob(102L)).thenThrow(new NonRetriableJobException("Cannot retry non-retriable failure"));

        mockMvc.perform(post("/api/jobs/102/retry")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Cannot retry non-retriable failure"));
    }

    @Test
    @DisplayName("GET /api/jobs/unresolved should return 200 with list of unresolved jobs")
    void shouldReturnUnresolvedJobs() throws Exception {
        UnresolvedJobDTO unresolved = UnresolvedJobDTO.builder()
                .scheduleId(102L)
                .tenantId("TENANT-002")
                .reportId("REP-QUARTERLY-TAX")
                .scheduledTime(Instant.now())
                .latestStatus("FAILED")
                .classification("NON_RETRIABLE_FAILURE")
                .errorCode("REPORT_NOT_FOUND")
                .unresolvedReason("Non-retriable failure detected")
                .retryCount(0)
                .build();

        when(failureDetectionService.detectUnresolvedJobs()).thenReturn(List.of(unresolved));

        mockMvc.perform(get("/api/jobs/unresolved")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduleId").value(102))
                .andExpect(jsonPath("$[0].unresolvedReason").value("Non-retriable failure detected"));
    }

    @Test
    @DisplayName("GET /api/alerts should return 200 with list of alert DTOs")
    void shouldReturnAllAlerts() throws Exception {
        AlertDTO alert = AlertDTO.builder()
                .alertId(1L)
                .scheduleId(105L)
                .jobLogId(6L)
                .tenantId("TENANT-005")
                .alertType("RETRY_LIMIT_EXCEEDED")
                .reason("Max retries (3) reached")
                .createdAt(Instant.now())
                .acknowledged(false)
                .build();

        when(alertService.getAllAlerts()).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value(1))
                .andExpect(jsonPath("$[0].alertType").value("RETRY_LIMIT_EXCEEDED"));
    }
}
