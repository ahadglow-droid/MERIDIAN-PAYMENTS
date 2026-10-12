package com.meridian.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedJobDTO {
    private Long scheduleId;
    private String tenantId;
    private String reportId;
    private Instant scheduledTime;
    private Long latestExecutionLogId;
    private String latestStatus;
    private String classification;
    private String errorCode;
    private String errorMessage;
    private int retryCount;
    private boolean canRetry;
}
