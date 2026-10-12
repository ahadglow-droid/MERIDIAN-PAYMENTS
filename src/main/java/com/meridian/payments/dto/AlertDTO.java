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
public class AlertDTO {
    private Long alertId;
    private Long scheduleId;
    private Long jobLogId;
    private String tenantId;
    private String alertType;
    private String reason;
    private Instant createdAt;
    private boolean acknowledged;
}
