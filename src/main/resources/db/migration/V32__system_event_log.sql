-- V32: Create system_event_log table for audit trail
CREATE TABLE system_event_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(30) NOT NULL,
    message VARCHAR(255) NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_system_event_log_event_type ON system_event_log(event_type);
CREATE INDEX idx_system_event_log_created_at ON system_event_log(created_at DESC);