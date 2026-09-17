-- V2__create_participation_table.sql
CREATE TABLE IF NOT EXISTS sky_signals_participation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    contribution INT DEFAULT 0,
    rewarded BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id) REFERENCES sky_signals_events(event_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_participation_event ON sky_signals_participation(event_id);
CREATE INDEX idx_participation_player ON sky_signals_participation(uuid);
