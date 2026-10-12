package com.meridian.payments.scheduler;

import com.meridian.payments.dto.FailedJobDTO;
import com.meridian.payments.service.AlertService;
import com.meridian.payments.service.FailureDetectionService;
import com.meridian.payments.service.RetryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledJobScannerTest {

    @Mock
    private FailureDetectionService failureDetectionService;

    @Mock
    private RetryService retryService;

    @Mock
    private AlertService alertService;

    private SimpleMeterRegistry meterRegistry;
    private ScheduledJobScanner scanner;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scanner = new ScheduledJobScanner(
                failureDetectionService,
                retryService,
                alertService,
                meterRegistry
        );
    }

    @Test
    @DisplayName("Should scan failed jobs, dispatch retry for retriable jobs, and raise alerts for non-retriable jobs")
    void shouldExecutePeriodicScanFlow() {
        FailedJobDTO retriableJob = FailedJobDTO.builder()
                .scheduleId(103L)
                .tenantId("TENANT-003")
                .reportId("REP-DAILY")
                .scheduledTime(Instant.now())
                .classification("RETRIABLE_FAILURE")
                .retryCount(0)
                .canRetry(true)
                .build();

        FailedJobDTO nonRetriableJob = FailedJobDTO.builder()
                .scheduleId(102L)
                .tenantId("TENANT-002")
                .reportId("REP-TAX")
                .scheduledTime(Instant.now())
                .latestExecutionLogId(3L)
                .classification("NON_RETRIABLE_FAILURE")
                .errorCode("REPORT_NOT_FOUND")
                .retryCount(0)
                .canRetry(false)
                .build();

        when(failureDetectionService.detectFailedJobs()).thenReturn(List.of(retriableJob, nonRetriableJob));

        scanner.scanAndRecover();

        verify(retryService).retryJob(103L);
        verify(alertService).raiseAlert(
                eq(102L), eq(3L), eq("TENANT-002"), eq("NON_RETRIABLE_FAILURE"), any()
        );

        assertThat(meterRegistry.counter("report.recovery.scans.total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer("report.recovery.scanner.duration").count()).isEqualTo(1L);
    }
}
