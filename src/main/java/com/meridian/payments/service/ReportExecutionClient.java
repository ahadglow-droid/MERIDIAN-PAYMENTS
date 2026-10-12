package com.meridian.payments.service;

/**
 * Adapter client responsible for communicating with report generation runner workers.
 */
public interface ReportExecutionClient {

    ReportExecutionResult executeReport(Long scheduleId, String tenantId, String reportId);

    record ReportExecutionResult(
            boolean successful,
            String status,
            String errorCode,
            String errorMessage
    ) {
        public static ReportExecutionResult success() {
            return new ReportExecutionResult(true, "SUCCESS", null, null);
        }

        public static ReportExecutionResult failure(String status, String errorCode, String errorMessage) {
            return new ReportExecutionResult(false, status, errorCode, errorMessage);
        }
    }
}
