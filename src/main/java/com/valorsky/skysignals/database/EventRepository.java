package com.valorsky.skysignals.database;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class EventRepository {

    private final DataSource dataSource;
    private final Logger logger;

    public EventRepository(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public void saveEventState(EventState state) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO sky_signals_events (event_id, type, server, started_at, ended_at, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, state.id().toString());
                stmt.setString(2, state.type().name());
                stmt.setString(3, state.serverId());
                stmt.setTimestamp(4, Timestamp.from(state.startedAt()));
                stmt.setTimestamp(5, Timestamp.from(state.endsAt()));
                stmt.setString(6, state.status().name());
                stmt.setTimestamp(7, Timestamp.from(Instant.now()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to save event state: " + e.getMessage());
        }
    }

    public void updateEventStatus(UUID eventId, SkyEventStatus status, Instant timestamp) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE sky_signals_events SET status = ?, ended_at = ? WHERE event_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                stmt.setTimestamp(2, Timestamp.from(timestamp));
                stmt.setString(3, eventId.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.warning("Failed to update event status: " + e.getMessage());
        }
    }

    public void recordParticipation(UUID eventId, UUID playerId, int contribution, boolean rewarded) {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO sky_signals_participation (event_id, uuid, contribution, rewarded, created_at) VALUES (?, ?, ?, ?, ?)";
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

    public List<EventHistoryEntry> getRecentEvents(int limit) {
        if (dataSource == null) return List.of();
        List<EventHistoryEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT event_id, type, server, started_at, ended_at, status FROM sky_signals_events ORDER BY ended_at DESC LIMIT ?";
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
