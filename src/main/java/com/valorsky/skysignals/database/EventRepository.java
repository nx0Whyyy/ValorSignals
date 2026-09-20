package com.valorsky.skysignals.database;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.valorsky.skysignals.redis.EnumAdapter;
import com.valorsky.skysignals.redis.InstantAdapter;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class EventRepository {

    private final java.util.function.Supplier<DataSource> dataSourceProvider;
    private final Logger logger;
    private final Gson gson;

    public EventRepository(DataSource dataSource, Logger logger) {
        this(() -> dataSource, logger);
    }

    public EventRepository(java.util.function.Supplier<DataSource> dataSourceProvider, Logger logger) {
        this.dataSourceProvider = dataSourceProvider;
        this.logger = logger;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(SkyEventType.class, new EnumAdapter<>(SkyEventType.class))
            .registerTypeAdapter(SkyEventStatus.class, new EnumAdapter<>(SkyEventStatus.class))
            .create();
    }

    public void saveEventState(EventState state) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO sky_signals_events (event_id, type, server, scope, scheduled_at, started_at, ended_at, status, phase, elapsed_seconds, data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, state.id().toString());
                stmt.setString(2, state.type().name());
                stmt.setString(3, state.serverId());
                stmt.setString(4, state.scope().name());
                stmt.setTimestamp(5, Timestamp.from(state.scheduledAt()));
                stmt.setTimestamp(6, Timestamp.from(state.startedAt()));
                stmt.setTimestamp(7, state.endsAt() != null ? Timestamp.from(state.endsAt()) : null);
                stmt.setString(8, state.status().name());
                stmt.setString(9, state.phase().name());
                stmt.setLong(10, state.elapsedSeconds());
                stmt.setString(11, gson.toJson(state.data()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to save event state: " + e.getMessage());
        }
    }

    public void updateEventStatus(UUID eventId, SkyEventStatus status, Instant timestamp) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE sky_signals_events SET status = ?, phase = ?, ended_at = COALESCE(?, ended_at) WHERE event_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                stmt.setString(2, statusToPhase(status).name());
                stmt.setTimestamp(3, status == SkyEventStatus.FINISHED || status == SkyEventStatus.CANCELLED ? Timestamp.from(timestamp) : null);
                stmt.setString(4, eventId.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to update event status: " + e.getMessage());
        }
    }

    private com.valorsky.skysignals.model.SkyEventPhase statusToPhase(SkyEventStatus status) {
        return switch (status) {
            case SCHEDULED -> com.valorsky.skysignals.model.SkyEventPhase.SCHEDULED;
            case ANNOUNCING -> com.valorsky.skysignals.model.SkyEventPhase.ANNOUNCING;
            case WARNING -> com.valorsky.skysignals.model.SkyEventPhase.WARNING;
            case ACTIVE -> com.valorsky.skysignals.model.SkyEventPhase.ACTIVE;
            case COMPLETING -> com.valorsky.skysignals.model.SkyEventPhase.COMPLETING;
            case FINISHED -> com.valorsky.skysignals.model.SkyEventPhase.FINISHED;
            case CANCELLED -> com.valorsky.skysignals.model.SkyEventPhase.CANCELLED;
        };
    }

    public void recordParticipation(UUID eventId, UUID playerId, int contribution, boolean rewarded) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO sky_signals_participation (event_id, uuid, contribution, rewarded, created_at) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE contribution = VALUES(contribution), rewarded = VALUES(rewarded)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, eventId.toString());
                stmt.setString(2, playerId.toString());
                stmt.setInt(3, contribution);
                stmt.setBoolean(4, rewarded);
                stmt.setTimestamp(5, Timestamp.from(Instant.now()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to record participation: " + e.getMessage());
        }
    }

    public boolean hasBeenRewarded(UUID eventId, UUID playerId) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT rewarded FROM sky_signals_participation WHERE event_id = ? AND uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, eventId.toString());
                stmt.setString(2, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getBoolean("rewarded");
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to check reward status: " + e.getMessage());
            return false;
        }
    }

    public boolean hasBeenClaimed(String claimKey) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT 1 FROM sky_signals_rewards WHERE claim_key = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, claimKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to check reward claim: " + e.getMessage());
            return false;
        }
    }

    public void recordClaim(String claimKey, UUID eventId, UUID playerId, String eventType, String rewardType, String rewardData) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT IGNORE INTO sky_signals_rewards (claim_key, event_id, player_uuid, event_type, reward_type, reward_data) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, claimKey);
                stmt.setString(2, eventId.toString());
                stmt.setString(3, playerId.toString());
                stmt.setString(4, eventType);
                stmt.setString(5, rewardType);
                stmt.setString(6, rewardData);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to record reward claim: " + e.getMessage());
        }
    }

    public List<EventHistoryEntry> getRecentEvents(int limit) {
        DataSource dataSource = dataSourceProvider.get();
        if (dataSource == null) return List.of();
        List<EventHistoryEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT event_id, type, server, started_at, ended_at, status FROM sky_signals_events WHERE status IN ('FINISHED', 'CANCELLED') AND ended_at IS NOT NULL ORDER BY ended_at DESC LIMIT ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(new EventHistoryEntry(
                                UUID.fromString(rs.getString("event_id")),
                                SkyEventType.valueOf(rs.getString("type")),
                                rs.getString("server"),
                                rs.getTimestamp("started_at").toInstant(),
                                rs.getTimestamp("ended_at").toInstant(),
                                SkyEventStatus.valueOf(rs.getString("status"))
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to fetch recent events: " + e.getMessage());
        }
        return result;
    }

    public record EventHistoryEntry(
            UUID eventId,
            SkyEventType type,
            String serverId,
            Instant startedAt,
            Instant endedAt,
            SkyEventStatus status
    ) {}
}