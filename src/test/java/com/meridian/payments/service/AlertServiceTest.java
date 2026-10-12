package com.meridian.payments.service;

import com.meridian.payments.dto.AlertDTO;
import com.meridian.payments.entity.Alert;
import com.meridian.payments.repository.AlertRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    private SimpleMeterRegistry meterRegistry;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        alertService = new AlertService(alertRepository, meterRegistry);
    }

    @Test
    @DisplayName("Should raise and persist alert, and increment Micrometer counter")
    void shouldRaiseAlertSuccessfully() {
        when(alertRepository.existsByScheduleIdAndAlertType(102L, "NON_RETRIABLE_FAILURE")).thenReturn(false);

        Alert savedAlert = Alert.builder()
                .id(1L)
                .scheduleId(102L)
                .jobLogId(3L)
                .tenantId("TENANT-002")
                .alertType("NON_RETRIABLE_FAILURE")
                .reason("Report template not found")
                .createdAt(Instant.now())
                .acknowledged(false)
                .build();

        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);

        AlertDTO result = alertService.raiseAlert(102L, 3L, "TENANT-002", "NON_RETRIABLE_FAILURE", "Report template not found");

        assertThat(result).isNotNull();
        assertThat(result.getAlertId()).isEqualTo(1L);
        assertThat(result.getAlertType()).isEqualTo("NON_RETRIABLE_FAILURE");

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduleId()).isEqualTo(102L);

        // Verify metric
        assertThat(meterRegistry.counter("report.recovery.alerts.total", "type", "NON_RETRIABLE_FAILURE").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should deduplicate active alert if alert for schedule and type already exists")
    void shouldDeduplicateActiveAlert() {
        when(alertRepository.existsByScheduleIdAndAlertType(102L, "NON_RETRIABLE_FAILURE")).thenReturn(true);

        AlertDTO result = alertService.raiseAlert(102L, 3L, "TENANT-002", "NON_RETRIABLE_FAILURE", "Report template not found");

        assertThat(result).isNull();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve all alerts as DTOs")
    void shouldGetAllAlerts() {
        Alert alert1 = Alert.builder().id(1L).scheduleId(101L).tenantId("T1").alertType("RETRY_LIMIT_EXCEEDED").reason("R1").createdAt(Instant.now()).acknowledged(false).build();
        Alert alert2 = Alert.builder().id(2L).scheduleId(102L).tenantId("T2").alertType("NON_RETRIABLE_FAILURE").reason("R2").createdAt(Instant.now()).acknowledged(false).build();

        when(alertRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(alert2, alert1));

        List<AlertDTO> alerts = alertService.getAllAlerts();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).getAlertId()).isEqualTo(2L);
        assertThat(alerts.get(1).getAlertId()).isEqualTo(1L);
    }
}
