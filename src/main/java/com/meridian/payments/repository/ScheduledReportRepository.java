package com.meridian.payments.repository;

import com.meridian.payments.entity.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, Long> {

    @Query("SELECT DISTINCT s FROM ScheduledReport s LEFT JOIN FETCH s.executionLogs")
    List<ScheduledReport> findAllWithExecutionLogs();
}
