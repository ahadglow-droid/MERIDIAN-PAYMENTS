package com.meridian.payments.service;

import com.meridian.payments.dto.RetryResultDTO;
import com.meridian.payments.entity.JobExecutionLog;
import com.meridian.payments.entity.RetryHistory;
import com.meridian.payments.entity.ScheduledReport;
import com.meridian.payments.exception.MaxRetriesExceededException;
import com.meridian.payments.exception.NonRetriableJobException;
import com.meridian.payments.exception.ResourceNotFoundException;
import com.meridian.payments.repository.JobExecutionLogRepository;
import com.meridian.payments.repository.RetryHistoryRepository;
import com.meridian.payments.repository.ScheduledReportRepository;
import com.meridian.payments.strategy.ExecutionCategory;
import com.meridian.payments.strategy.StatusClassificationStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private final ScheduledReportRepository scheduledReportRepository;
    private final JobExecutionLogRepository jobExecutionLogRepository;
    private final RetryHistoryRepository retryHistoryRepository;
    private final StatusClassificationStrategy classificationStrategy;
    private final ReportExecutionClient reportExecutionClient;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    @Value("${recovery.max-retries:3}")
    private int maxRetries = 3;

    @Transactional
    public RetryResultDTO retryJob(Long scheduleId) {
        log.info("Initiating retry for scheduleId={}", scheduleId);

        ScheduledReport schedule = scheduledReportRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled report not found for id: " + scheduleId));

        List<JobExecutionLog> logs = jobExecutionLogRepository.findByScheduleIdOrderByExecutionTimeDescIdDesc(scheduleId);
        if (logs.isEmpty()) {
            throw new ResourceNotFoundException("No execution logs found for schedule: " + scheduleId);
        }

        // Rule 1: A report is healthy if at least one successful execution exists
        boolean hasSuccess = logs.stream()
                .anyMatch(l -> classificationStrategy.isSuccess(l.getStatus(), l.getErrorCode()));
        if (hasSuccess) {
            log.info("Schedule id={} already has a successful execution, retry not needed", scheduleId);
            return RetryResultDTO.builder()
                    .scheduleId(scheduleId)
                    .jobLogId(logs.get(0).getId())
                    .retryNumber(0)
                    .result("ALREADY_HEALTHY")
                    .message("Report schedule already has a successful run.")
                    .timestamp(Instant.now())
                    .build();
        }

        JobExecutionLog latestLog = logs.get(0);
        ExecutionCategory category = classificationStrategy.classify(latestLog.getStatus(), latestLog.getErrorCode());

        // Rule 5: Generate alert when non-retriable failure detected
        if (category == ExecutionCategory.NON_RETRIABLE_FAILURE) {
            alertService.raiseAlert(
                    scheduleId,
                    latestLog.getId(),
                    schedule.getTenantId(),
                    "NON_RETRIABLE_FAILURE",
                    "Execution failed with non-retriable error: " + (latestLog.getErrorCode() != null ? latestLog.getErrorCode() : latestLog.getStatus())
            );
            throw new NonRetriableJobException("Cannot retry non-retriable failure: status=" + latestLog.getStatus()
                    + ", errorCode=" + latestLog.getErrorCode());
        }

        int currentRetries = (int) retryHistoryRepository.countByScheduleId(scheduleId);

        // Rule 4 & 5: Maximum 3 retries; Alert when retry limit exceeded
        if (currentRetries >= maxRetries) {
            alertService.raiseAlert(
                    scheduleId,
                    latestLog.getId(),
                    schedule.getTenantId(),
                    "RETRY_LIMIT_EXCEEDED",
                    "Maximum retry limit (" + maxRetries + ") reached for schedule " + scheduleId
            );
            throw new MaxRetriesExceededException("Retry limit of " + maxRetries + " exceeded for schedule id: " + scheduleId);
        }

        int nextRetryNumber = currentRetries + 1;
        meterRegistry.counter("report.recovery.retries.attempted").increment();

        // Dispatch execution to report generator worker
        ReportExecutionClient.ReportExecutionResult result = reportExecutionClient.executeReport(
                scheduleId,
                schedule.getTenantId(),
                schedule.getReportId()
        );

        // Maintain audit in retry_history
        RetryHistory retryRecord = RetryHistory.builder()
                .jobLogId(latestLog.getId())
                .retryTime(Instant.now())
                .retryNumber(nextRetryNumber)
                .result(result.successful() ? "SUCCESS" : "FAILED")
                .build();
        retryHistoryRepository.save(retryRecord);

        // Append new execution log entry
        JobExecutionLog newLog = JobExecutionLog.builder()
                .scheduleId(scheduleId)
                .tenantId(schedule.getTenantId())
                .executionTime(Instant.now())
                .status(result.status())
                .errorCode(result.errorCode())
                .errorMessage(result.errorMessage())
                .build();
        jobExecutionLogRepository.save(newLog);

        if (result.successful()) {
            meterRegistry.counter("report.recovery.retries.succeeded").increment();
            log.info("Retry attempt #{} SUCCEEDED for scheduleId={}", nextRetryNumber, scheduleId);
            return RetryResultDTO.builder()
                    .scheduleId(scheduleId)
                    .jobLogId(newLog.getId())
                    .retryNumber(nextRetryNumber)
                    .result("SUCCESS")
                    .message("Report generation retry completed successfully.")
                    .timestamp(Instant.now())
                    .build();
        } else {
            meterRegistry.counter("report.recovery.retries.failed").increment();
            log.warn("Retry attempt #{} FAILED for scheduleId={}", nextRetryNumber, scheduleId);

            if (nextRetryNumber >= maxRetries) {
                alertService.raiseAlert(
                        scheduleId,
                        newLog.getId(),
                        schedule.getTenantId(),
                        "RETRY_LIMIT_EXCEEDED",
                        "Retry attempt " + nextRetryNumber + " failed. Maximum retry budget exhausted."
                );
            }

            return RetryResultDTO.builder()
                    .scheduleId(scheduleId)
                    .jobLogId(newLog.getId())
                    .retryNumber(nextRetryNumber)
                    .result("FAILED")
                    .message("Report generation retry failed: " + result.errorMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
}
