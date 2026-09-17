package com.valorsky.skysignals.database.schema;

import com.valorsky.skysignals.database.migration.Migration;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SchemaManager {

    private static final String MIGRATION_TABLE = "sky_signals_schema_version";

    public static void runMigrations(DataSource dataSource, Logger logger) {
        List<Migration> migrations = loadMigrations();
        if (migrations.isEmpty()) {
            logger.warning("No migrations found.");
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            createVersionTable(conn);

            int currentVersion = getVersion(conn);
            for (Migration migration : migrations) {
                if (migration.version() > currentVersion) {
                    logger.info("Applying migration V" + migration.version() + ": " + migration.description());
                    migration.execute(conn);
                    setVersion(conn, migration.version());
                    logger.info("Migration V" + migration.version() + " applied successfully.");
                }
            }
        } catch (SQLException e) {
            logger.severe("Migration failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static List<Migration> loadMigrations() {
        List<Migration> migrations = new ArrayList<>();
        try {
            Path migrationDir = Path.of("database/migration");
            if (Files.notExists(migrationDir)) {
                migrationDir = Path.of("src/main/resources/database/migration");
            }
            if (Files.notExists(migrationDir)) {
                migrationDir = Path.of("src/main/resources/migration");
            }

            if (Files.exists(migrationDir)) {
                try (var stream = Files.list(migrationDir)) {
                    stream.filter(p -> p.toString().endsWith(".sql"))
                            .sorted()
                            .forEach(p -> {
                                try {
                                    migrations.add(loadFromFile(p));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }

            if (migrations.isEmpty()) {
                migrations.addAll(loadBuiltinMigrations());
            }
        } catch (Exception e) {
            return loadBuiltinMigrations();
        }
        return migrations;
    }

    private static List<Migration> loadBuiltinMigrations() {
        List<Migration> migrations = new ArrayList<>();

        Migration v1 = new Migration(1, "create_events_table", """
                CREATE TABLE IF NOT EXISTS sky_signals_events (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(36) NOT NULL UNIQUE,
                    type VARCHAR(32) NOT NULL,
                    server VARCHAR(64) NOT NULL,
                    started_at TIMESTAMP NOT NULL,
                    ended_at TIMESTAMP NULL,
                    status VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        Migration v2 = new Migration(2, "create_participation_table", """
                CREATE TABLE IF NOT EXISTS sky_signals_participation (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(36) NOT NULL,
                    uuid VARCHAR(36) NOT NULL,
                    contribution INT DEFAULT 0,
                    rewarded BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (event_id) REFERENCES sky_signals_events(event_id) ON DELETE CASCADE
                )
                """);

        migrations.add(v1);
        migrations.add(v2);
        return migrations;
    }

    private static Migration loadFromFile(Path path) throws Exception {
        String fileName = path.getFileName().toString();
        String[] parts = fileName.split("__");
        int version = Integer.parseInt(parts[0].substring(1).replace("V", ""));
        String description = parts[1].replace(".sql", "");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return new Migration(version, description, content);
    }

    private static void createVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " (" +
                    "version INT NOT NULL, " +
                    "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "description VARCHAR(255), " +
                    "PRIMARY KEY (version)" +
                    ")");
        }
    }

    private static int getVersion(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT MAX(version) FROM " + MIGRATION_TABLE)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private static void setVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO " + MIGRATION_TABLE + " (version, description) VALUES (?, ?)")) {
            stmt.setInt(1, version);
            stmt.setString(2, "");
            stmt.executeUpdate();
        }
    }
}
