-- Meridian Payments Database Schema

CREATE TABLE IF NOT EXISTS scheduled_reports (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    report_id VARCHAR(64) NOT NULL,
    scheduled_time TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS job_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    execution_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(64) NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    CONSTRAINT fk_job_schedule FOREIGN KEY (schedule_id) REFERENCES scheduled_reports(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS retry_history (
    id BIGSERIAL PRIMARY KEY,
    job_log_id BIGINT NOT NULL,
    retry_time TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_number INT NOT NULL,
    result VARCHAR(64) NOT NULL,
    CONSTRAINT fk_retry_job_log FOREIGN KEY (job_log_id) REFERENCES job_execution_logs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    job_log_id BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    alert_type VARCHAR(64) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_jel_schedule_time ON job_execution_logs(schedule_id, execution_time DESC);
CREATE INDEX IF NOT EXISTS idx_jel_status ON job_execution_logs(status);
CREATE INDEX IF NOT EXISTS idx_rh_job_log ON retry_history(job_log_id, retry_number);
CREATE INDEX IF NOT EXISTS idx_alerts_schedule ON alerts(schedule_id, alert_type);
