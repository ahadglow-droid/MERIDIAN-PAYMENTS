package com.meridian.payments.service;

import com.meridian.payments.dto.FailedJobDTO;
import com.meridian.payments.dto.UnresolvedJobDTO;
import com.meridian.payments.entity.JobExecutionLog;
import com.meridian.payments.entity.ScheduledReport;
import com.meridian.payments.repository.RetryHistoryRepository;
import com.meridian.payments.repository.ScheduledReportRepository;
import com.meridian.payments.strategy.ExecutionCategory;
import com.meridian.payments.strategy.StatusClassificationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailureDetectionService {

    private final ScheduledReportRepository scheduledReportRepository;
    private final RetryHistoryRepository retryHistoryRepository;
    private final StatusClassificationStrategy classificationStrategy;

    @Value("${recovery.max-retries:3}")
    private int maxRetries = 3;

    /**
     * Identifies all report schedules that have failed (no successful execution exists
     * and latest execution is a failure).
     */
    @Transactional(readOnly = true)
    public List<FailedJobDTO> detectFailedJobs() {
        List<ScheduledReport> schedules = scheduledReportRepository.findAllWithExecutionLogs();
        List<FailedJobDTO> failedJobs = new ArrayList<>();

        for (ScheduledReport schedule : schedules) {
            List<JobExecutionLog> logs = schedule.getExecutionLogs();
            if (logs == null || logs.isEmpty()) {
                continue;
            }

            // Rule 1: A report is healthy if at least one successful execution exists
            boolean hasSuccess = logs.stream()
                    .anyMatch(l -> classificationStrategy.isSuccess(l.getStatus(), l.getErrorCode()));

            if (hasSuccess) {
                log.debug("Schedule id={} is healthy (found successful execution)", schedule.getId());
                continue;
            }

            // Rule 2: A report is failed if no success exists and latest execution is failure
            JobExecutionLog latestLog = logs.get(0); // Ordered by executionTime DESC, id DESC
            ExecutionCategory category = classificationStrategy.classify(latestLog.getStatus(), latestLog.getErrorCode());

            if (category != ExecutionCategory.SUCCESS) {
                int retryCount = (int) retryHistoryRepository.countByScheduleId(schedule.getId());
                boolean canRetry = (category == ExecutionCategory.RETRIABLE_FAILURE) && (retryCount < maxRetries);

                failedJobs.add(FailedJobDTO.builder()
                        .scheduleId(schedule.getId())
                        .tenantId(schedule.getTenantId())
                        .reportId(schedule.getReportId())
                        .scheduledTime(schedule.getScheduledTime())
                        .latestExecutionLogId(latestLog.getId())
                        .latestStatus(latestLog.getStatus())
                        .classification(category.name())
                        .errorCode(latestLog.getErrorCode())
                        .errorMessage(latestLog.getErrorMessage())
                        .retryCount(retryCount)
                        .canRetry(canRetry)
                        .build());
            }
        }

        log.info("Detected {} failed report jobs out of {} total schedules", failedJobs.size(), schedules.size());
        return failedJobs;
    }

    /**
     * Returns jobs requiring operator intervention:
     * - Non-retriable failures
     * - Retry limits exceeded
     * - Unknown execution states
     */
    @Transactional(readOnly = true)
    public List<UnresolvedJobDTO> detectUnresolvedJobs() {
        List<ScheduledReport> schedules = scheduledReportRepository.findAllWithExecutionLogs();
        List<UnresolvedJobDTO> unresolvedJobs = new ArrayList<>();

        for (ScheduledReport schedule : schedules) {
            List<JobExecutionLog> logs = schedule.getExecutionLogs();
            if (logs == null || logs.isEmpty()) {
                continue;
            }

            boolean hasSuccess = logs.stream()
                    .anyMatch(l -> classificationStrategy.isSuccess(l.getStatus(), l.getErrorCode()));

            if (hasSuccess) {
                continue;
            }

            JobExecutionLog latestLog = logs.get(0);
            ExecutionCategory category = classificationStrategy.classify(latestLog.getStatus(), latestLog.getErrorCode());
            int retryCount = (int) retryHistoryRepository.countByScheduleId(schedule.getId());

            String reason = null;
            if (category == ExecutionCategory.NON_RETRIABLE_FAILURE) {
                reason = "Non-retriable failure detected: " + (latestLog.getErrorCode() != null ? latestLog.getErrorCode() : latestLog.getStatus());
            } else if (category == ExecutionCategory.UNKNOWN) {
                reason = "Unrecognized status or error code: status='" + latestLog.getStatus() + "', errorCode='" + latestLog.getErrorCode() + "'";
            } else if (category == ExecutionCategory.RETRIABLE_FAILURE && retryCount >= maxRetries) {
                reason = "Maximum retry limit (" + maxRetries + ") exceeded without successful recovery";
            }

            if (reason != null) {
                unresolvedJobs.add(UnresolvedJobDTO.builder()
                        .scheduleId(schedule.getId())
                        .tenantId(schedule.getTenantId())
                        .reportId(schedule.getReportId())
                        .scheduledTime(schedule.getScheduledTime())
                        .latestStatus(latestLog.getStatus())
                        .classification(category.name())
                        .errorCode(latestLog.getErrorCode())
                        .unresolvedReason(reason)
                        .retryCount(retryCount)
                        .build());
            }
        }

        return unresolvedJobs;
    }
}
