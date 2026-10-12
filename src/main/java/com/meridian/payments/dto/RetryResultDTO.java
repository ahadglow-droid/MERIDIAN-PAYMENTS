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
public class RetryResultDTO {
    private Long scheduleId;
    private Long jobLogId;
    private Integer retryNumber;
    private String result;
    private String message;
    private Instant timestamp;
}
