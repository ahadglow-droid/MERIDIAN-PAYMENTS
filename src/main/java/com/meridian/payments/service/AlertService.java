package com.meridian.payments.service;

import com.meridian.payments.dto.AlertDTO;
import com.meridian.payments.entity.Alert;
import com.meridian.payments.repository.AlertRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final MeterRegistry meterRegistry;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AlertDTO raiseAlert(Long scheduleId, Long jobLogId, String tenantId, String alertType, String reason) {
        log.warn("RAISING ALERT: scheduleId={}, jobLogId={}, tenantId={}, type={}, reason={}",
                scheduleId, jobLogId, tenantId, alertType, reason);

        // Deduplication: Avoid duplicate active alerts for same schedule and alertType
        if (alertRepository.existsByScheduleIdAndAlertType(scheduleId, alertType)) {
            log.info("Alert of type {} already exists for scheduleId={}, skipping duplicate insert",
                    alertType, scheduleId);
            return null;
        }

        Alert alert = Alert.builder()
                .scheduleId(scheduleId)
                .jobLogId(jobLogId)
                .tenantId(tenantId)
                .alertType(alertType)
                .reason(reason)
                .createdAt(Instant.now())
                .acknowledged(false)
                .build();

        Alert saved = alertRepository.save(alert);

        meterRegistry.counter("report.recovery.alerts.total", "type", alertType).increment();

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AlertDTO> getAllAlerts() {
        return alertRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AlertDTO toDto(Alert alert) {
        return AlertDTO.builder()
                .alertId(alert.getId())
                .scheduleId(alert.getScheduleId())
                .jobLogId(alert.getJobLogId())
                .tenantId(alert.getTenantId())
                .alertType(alert.getAlertType())
                .reason(alert.getReason())
                .createdAt(alert.getCreatedAt())
                .acknowledged(Boolean.TRUE.equals(alert.getAcknowledged()))
                .build();
    }
}
