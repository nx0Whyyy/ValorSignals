CREATE TABLE IF NOT EXISTS sky_signals_rewards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_key VARCHAR(255) NOT NULL UNIQUE,
    event_id VARCHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    reward_type VARCHAR(32) NOT NULL,
    reward_data JSON,
    claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id) REFERENCES sky_signals_events(event_id) ON DELETE CASCADE
)