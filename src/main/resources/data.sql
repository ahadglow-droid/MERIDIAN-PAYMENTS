-- Meridian Payments Initial Seed Data

-- Clear existing data if re-running
DELETE FROM alerts;
DELETE FROM retry_history;
DELETE FROM job_execution_logs;
DELETE FROM scheduled_reports;

-- 1. Schedule 101: HEALTHY (Had a TIMEOUT then SUCCESS)
INSERT INTO scheduled_reports (id, tenant_id, report_id, scheduled_time)
VALUES (101, 'TENANT-001', 'REP-MONTHLY-STMT', '2026-10-10 08:00:00+00');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (1, 101, 'TENANT-001', '2026-10-10 08:01:00+00', 'TIMEOUT', 'NETWORK_TIMEOUT', 'Connection to report data source timed out');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (2, 101, 'TENANT-001', '2026-10-10 08:05:00+00', 'SUCCESS', NULL, NULL);

-- 2. Schedule 102: NON-RETRIABLE FAILURE (Status FAILED with REPORT_NOT_FOUND error code)
INSERT INTO scheduled_reports (id, tenant_id, report_id, scheduled_time)
VALUES (102, 'TENANT-002', 'REP-QUARTERLY-TAX', '2026-10-10 09:00:00+00');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (3, 102, 'TENANT-002', '2026-10-10 09:02:00+00', 'FAILED', 'REPORT_NOT_FOUND', 'Report template definition not found');

-- 3. Schedule 103: RETRIABLE FAILURE (TIMEOUT, 0 retries performed)
INSERT INTO scheduled_reports (id, tenant_id, report_id, scheduled_time)
VALUES (103, 'TENANT-003', 'REP-DAILY-SETTLEMENT', '2026-10-10 09:30:00+00');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (4, 103, 'TENANT-003', '2026-10-10 09:31:00+00', 'TIMEOUT', 'TIMEOUT', 'Socket read timed out after 30000ms');

-- 4. Schedule 104: RETRIABLE FAILURE (NETWORK_ERROR, 2 retries performed, 1 left)
INSERT INTO scheduled_reports (id, tenant_id, report_id, scheduled_time)
VALUES (104, 'TENANT-004', 'REP-AUDIT-LOG', '2026-10-10 10:00:00+00');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (5, 104, 'TENANT-004', '2026-10-10 10:01:00+00', 'NETWORK_ERROR', 'ERR_CONN_RESET', 'Connection reset by peer');

INSERT INTO retry_history (id, job_log_id, retry_time, retry_number, result)
VALUES (1, 5, '2026-10-10 10:02:00+00', 1, 'FAILED');

INSERT INTO retry_history (id, job_log_id, retry_time, retry_number, result)
VALUES (2, 5, '2026-10-10 10:03:00+00', 2, 'FAILED');

-- 5. Schedule 105: EXHAUSTED RETRIES (TEMPORARY_FAILURE, 3 retries performed, max reached)
INSERT INTO scheduled_reports (id, tenant_id, report_id, scheduled_time)
VALUES (105, 'TENANT-005', 'REP-YEARLY-SUMM', '2026-10-10 10:30:00+00');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (6, 105, 'TENANT-005', '2026-10-10 10:31:00+00', 'TEMPORARY_FAILURE', 'SERVICE_UNAVAILABLE', 'Downstream service unavailable');

INSERT INTO retry_history (id, job_log_id, retry_time, retry_number, result)
VALUES (3, 6, '2026-10-10 10:32:00+00', 1, 'FAILED');

INSERT INTO retry_history (id, job_log_id, retry_time, retry_number, result)
VALUES (4, 6, '2026-10-10 10:33:00+00', 2, 'FAILED');

INSERT INTO retry_history (id, job_log_id, retry_time, retry_number, result)
VALUES (5, 6, '2026-10-10 10:34:00+00', 3, 'FAILED');

INSERT INTO alerts (id, schedule_id, job_log_id, tenant_id, alert_type, reason, created_at, acknowledged)
VALUES (1, 105, 6, 'TENANT-005', 'RETRY_LIMIT_EXCEEDED', 'Max retries (3) exceeded for schedule 105', '2026-10-10 10:35:00+00', false);

-- 6. Schedule 106: UNKNOWN FAILURE (Requires manual triage in /api/jobs/unresolved)
INSERT INTO scheduled_reports (id, tenant_id, report_id, scheduled_time)
VALUES (106, 'TENANT-006', 'REP-LEDGER-EXPORT', '2026-10-10 11:00:00+00');

INSERT INTO job_execution_logs (id, schedule_id, tenant_id, execution_time, status, error_code, error_message)
VALUES (7, 106, 'TENANT-006', '2026-10-10 11:01:00+00', 'CORRUPT_PAYLOAD_UNKNOWN', 'UNK_999', 'Unrecognized runner execution code');

-- Advance identity sequences to avoid collisions with pre-seeded IDs
ALTER TABLE scheduled_reports ALTER COLUMN id RESTART WITH 200;
ALTER TABLE job_execution_logs ALTER COLUMN id RESTART WITH 200;
ALTER TABLE retry_history ALTER COLUMN id RESTART WITH 200;
ALTER TABLE alerts ALTER COLUMN id RESTART WITH 200;

