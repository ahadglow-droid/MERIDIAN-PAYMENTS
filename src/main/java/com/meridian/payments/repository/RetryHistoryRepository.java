package com.meridian.payments.repository;

import com.meridian.payments.entity.RetryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetryHistoryRepository extends JpaRepository<RetryHistory, Long> {

    long countByJobLogId(Long jobLogId);

    @Query("SELECT COUNT(r) FROM RetryHistory r WHERE r.jobExecutionLog.scheduleId = :scheduleId")
    long countByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query("SELECT COALESCE(MAX(r.retryNumber), 0) FROM RetryHistory r WHERE r.jobExecutionLog.scheduleId = :scheduleId")
    int findMaxRetryNumberForSchedule(@Param("scheduleId") Long scheduleId);

    List<RetryHistory> findByJobLogIdOrderByRetryNumberDesc(Long jobLogId);

    Optional<RetryHistory> findTopByJobLogIdOrderByRetryNumberDesc(Long jobLogId);
}
