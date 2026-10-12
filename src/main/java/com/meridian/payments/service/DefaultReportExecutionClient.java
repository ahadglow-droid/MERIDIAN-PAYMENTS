package com.meridian.payments.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default implementation simulating report generation execution.
 */
@Slf4j
@Component
public class DefaultReportExecutionClient implements ReportExecutionClient {

    @Override
    public ReportExecutionResult executeReport(Long scheduleId, String tenantId, String reportId) {
        log.info("Dispatched report regeneration for scheduleId={}, tenantId={}, reportId={}",
                scheduleId, tenantId, reportId);
        // By default, simulated execution recovers the report successfully
        return ReportExecutionResult.success();
    }
}
