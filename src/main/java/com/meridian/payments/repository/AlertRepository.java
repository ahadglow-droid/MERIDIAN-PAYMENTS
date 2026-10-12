package com.meridian.payments.repository;

import com.meridian.payments.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    boolean existsByScheduleIdAndAlertType(Long scheduleId, String alertType);

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByScheduleIdOrderByCreatedAtDesc(Long scheduleId);
}
