package com.meridian.payments.repository;

import com.meridian.payments.entity.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {

    List<JobExecutionLog> findByScheduleIdOrderByExecutionTimeDescIdDesc(Long scheduleId);

    Optional<JobExecutionLog> findTopByScheduleIdOrderByExecutionTimeDescIdDesc(Long scheduleId);

    long countByScheduleIdAndStatusIn(Long scheduleId, Collection<String> successStatuses);

    @Query("SELECT j FROM JobExecutionLog j WHERE j.scheduleId = :scheduleId ORDER BY j.executionTime DESC, j.id DESC")
    List<JobExecutionLog> findAllByScheduleIdOrdered(@Param("scheduleId") Long scheduleId);
}
