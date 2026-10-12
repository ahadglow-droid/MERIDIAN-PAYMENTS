package com.meridian.payments.service;

import com.meridian.payments.dto.RetryResultDTO;
import com.meridian.payments.entity.JobExecutionLog;
import com.meridian.payments.entity.RetryHistory;
import com.meridian.payments.entity.ScheduledReport;
import com.meridian.payments.exception.MaxRetriesExceededException;
import com.meridian.payments.exception.NonRetriableJobException;
import com.meridian.payments.repository.JobExecutionLogRepository;
import com.meridian.payments.repository.RetryHistoryRepository;
import com.meridian.payments.repository.ScheduledReportRepository;
import com.meridian.payments.strategy.DefaultStatusClassificationStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock
    private ScheduledReportRepository scheduledReportRepository;

    @Mock
    private JobExecutionLogRepository jobExecutionLogRepository;

    @Mock
    private RetryHistoryRepository retryHistoryRepository;

    @Mock
    private ReportExecutionClient reportExecutionClient;

    @Mock
    private AlertService alertService;

    private DefaultStatusClassificationStrategy classificationStrategy;
    private SimpleMeterRegistry meterRegistry;
    private RetryService retryService;

    @BeforeEach
    void setUp() {
        classificationStrategy = new DefaultStatusClassificationStrategy();
        meterRegistry = new SimpleMeterRegistry();
        retryService = new RetryService(
                scheduledReportRepository,
                jobExecutionLogRepository,
                retryHistoryRepository,
                classificationStrategy,
                reportExecutionClient,
                alertService,
                meterRegistry
        );
        ReflectionTestUtils.setField(retryService, "maxRetries", 3);
    }

    @Test
    @DisplayName("Should successfully retry a retriable failure and persist audit and log records")
    void shouldSuccessfullyRetryJob() {
        ScheduledReport schedule = ScheduledReport.builder()
                .id(103L)
                .tenantId("TENANT-003")
                .reportId("REP-DAILY-SETTLEMENT")
                .scheduledTime(Instant.now().minusSeconds(1800))
                .build();

        JobExecutionLog failedLog = JobExecutionLog.builder()
                .id(4L)
                .scheduleId(103L)
                .tenantId("TENANT-003")
                .executionTime(Instant.now().minusSeconds(600))
                .status("TIMEOUT")
                .errorCode("ERR_TIMEOUT")
                .build();

        when(scheduledReportRepository.findById(103L)).thenReturn(Optional.of(schedule));
        when(jobExecutionLogRepository.findByScheduleIdOrderByExecutionTimeDescIdDesc(103L)).thenReturn(List.of(failedLog));
        when(retryHistoryRepository.countByScheduleId(103L)).thenReturn(0L);
        when(reportExecutionClient.executeReport(103L, "TENANT-003", "REP-DAILY-SETTLEMENT"))
                .thenReturn(ReportExecutionClient.ReportExecutionResult.success());

        RetryResultDTO result = retryService.retryJob(103L);

        assertThat(result.getResult()).isEqualTo("SUCCESS");
        assertThat(result.getRetryNumber()).isEqualTo(1);

        // Verify audit record saved in retry_history
        ArgumentCaptor<RetryHistory> retryCaptor = ArgumentCaptor.forClass(RetryHistory.class);
        verify(retryHistoryRepository).save(retryCaptor.capture());
        assertThat(retryCaptor.getValue().getJobLogId()).isEqualTo(4L);
        assertThat(retryCaptor.getValue().getRetryNumber()).isEqualTo(1);
        assertThat(retryCaptor.getValue().getResult()).isEqualTo("SUCCESS");

        // Verify new log saved in job_execution_logs
        ArgumentCaptor<JobExecutionLog> logCaptor = ArgumentCaptor.forClass(JobExecutionLog.class);
        verify(jobExecutionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SUCCESS");

        // Verify metrics
        assertThat(meterRegistry.counter("report.recovery.retries.attempted").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("report.recovery.retries.succeeded").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should block retry, raise alert, and throw MaxRetriesExceededException when retry limit (3) is exceeded")
    void shouldBlockWhenMaxRetriesExceeded() {
        ScheduledReport schedule = ScheduledReport.builder()
                .id(105L)
                .tenantId("TENANT-005")
                .reportId("REP-YEARLY")
                .build();

        JobExecutionLog failedLog = JobExecutionLog.builder()
                .id(6L)
                .scheduleId(105L)
                .tenantId("TENANT-005")
                .status("TEMPORARY_FAILURE")
                .build();

        when(scheduledReportRepository.findById(105L)).thenReturn(Optional.of(schedule));
        when(jobExecutionLogRepository.findByScheduleIdOrderByExecutionTimeDescIdDesc(105L)).thenReturn(List.of(failedLog));
        when(retryHistoryRepository.countByScheduleId(105L)).thenReturn(3L);

        assertThatThrownBy(() -> retryService.retryJob(105L))
                .isInstanceOf(MaxRetriesExceededException.class)
                .hasMessageContaining("Retry limit of 3 exceeded");

        verify(alertService).raiseAlert(
                eq(105L), eq(6L), eq("TENANT-005"), eq("RETRY_LIMIT_EXCEEDED"), any()
        );
        verify(reportExecutionClient, never()).executeReport(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject retry and raise alert when status is non-retriable failure")
    void shouldRejectNonRetriableFailure() {
        ScheduledReport schedule = ScheduledReport.builder()
                .id(102L)
                .tenantId("TENANT-002")
                .reportId("REP-TAX")
                .build();

        JobExecutionLog failedLog = JobExecutionLog.builder()
                .id(3L)
                .scheduleId(102L)
                .tenantId("TENANT-002")
                .status("FAILED")
                .errorCode("REPORT_NOT_FOUND")
                .build();

        when(scheduledReportRepository.findById(102L)).thenReturn(Optional.of(schedule));
        when(jobExecutionLogRepository.findByScheduleIdOrderByExecutionTimeDescIdDesc(102L)).thenReturn(List.of(failedLog));

        assertThatThrownBy(() -> retryService.retryJob(102L))
                .isInstanceOf(NonRetriableJobException.class)
                .hasMessageContaining("Cannot retry non-retriable failure");

        verify(alertService).raiseAlert(
                eq(102L), eq(3L), eq("TENANT-002"), eq("NON_RETRIABLE_FAILURE"), any()
        );
        verify(reportExecutionClient, never()).executeReport(any(), any(), any());
    }
}
