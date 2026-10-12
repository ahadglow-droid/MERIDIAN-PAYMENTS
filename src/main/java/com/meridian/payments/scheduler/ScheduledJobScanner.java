package com.meridian.payments.scheduler;

import com.meridian.payments.dto.FailedJobDTO;
import com.meridian.payments.service.AlertService;
import com.meridian.payments.service.FailureDetectionService;
import com.meridian.payments.service.RetryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Background scanner periodically inspecting report execution logs,
 * initiating automated retries for safe failures, and raising alerts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recovery.scanner.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobScanner {

    private final FailureDetectionService failureDetectionService;
    private final RetryService retryService;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    @Scheduled(
            fixedDelayString = "${recovery.scanner.fixed-delay-ms:60000}",
            initialDelayString = "${recovery.scanner.initial-delay-ms:10000}"
    )
    public void scanAndRecover() {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Starting scheduled report recovery scan...");

        try {
            List<FailedJobDTO> failedJobs = failureDetectionService.detectFailedJobs();
            meterRegistry.counter("report.recovery.scans.total").increment();

            for (FailedJobDTO job : failedJobs) {
                if (job.isCanRetry()) {
                    log.info("Triggering auto-retry for scheduleId={}, reportId={}, retryAttempt={}",
                            job.getScheduleId(), job.getReportId(), job.getRetryCount() + 1);
                    try {
                        retryService.retryJob(job.getScheduleId());
                    } catch (Exception e) {
                        log.error("Failed during auto-retry execution for scheduleId={}: {}",
                                job.getScheduleId(), e.getMessage());
                    }
                } else if ("NON_RETRIABLE_FAILURE".equals(job.getClassification())) {
                    log.warn("Escalating non-retriable failure for scheduleId={}", job.getScheduleId());
                    alertService.raiseAlert(
                            job.getScheduleId(),
                            job.getLatestExecutionLogId(),
                            job.getTenantId(),
                            "NON_RETRIABLE_FAILURE",
                            "Execution failed with non-retriable error: " + (job.getErrorCode() != null ? job.getErrorCode() : job.getLatestStatus())
                    );
                } else if ("RETRIABLE_FAILURE".equals(job.getClassification()) && job.getRetryCount() >= 3) {
                    log.warn("Escalating exceeded retry attempts for scheduleId={}", job.getScheduleId());
                    alertService.raiseAlert(
                            job.getScheduleId(),
                            job.getLatestExecutionLogId(),
                            job.getTenantId(),
                            "RETRY_LIMIT_EXCEEDED",
                            "Maximum retries (3) reached for schedule " + job.getScheduleId()
                    );
                } else if ("UNKNOWN".equals(job.getClassification())) {
                    log.warn("Unknown job execution status detected for scheduleId={}, manual review needed",
                            job.getScheduleId());
                }
            }

            log.info("Finished report recovery scan. Processed {} failed jobs.", failedJobs.size());
        } catch (Exception e) {
            log.error("Error occurred during scheduled recovery scan: ", e);
        } finally {
            sample.stop(meterRegistry.timer("report.recovery.scanner.duration"));
        }
    }
}
