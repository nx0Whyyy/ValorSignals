package com.valorsky.skysignals.database;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.schema.SchemaManager;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class DatabaseManager {

    private final JavaPlugin plugin;
    private final Config config;
    private final Logger logger;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile HikariDataSource dataSource;
    private volatile boolean shutdown = false;

    public DatabaseManager(JavaPlugin plugin, Config config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    public void connect() {
        FoliaScheduler.runAsync(plugin, () -> {
            try {
                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setJdbcUrl("jdbc:mysql://" + config.dbHost() + ":" + config.dbPort() + "/" + config.dbName());
                hikariConfig.setUsername(config.dbUsername());
                hikariConfig.setPassword(config.dbPassword());
                hikariConfig.setMaximumPoolSize(config.dbPoolSize());
                hikariConfig.setConnectionTimeout(10000);
                hikariConfig.setIdleTimeout(600000);
                hikariConfig.setMaxLifetime(1800000);
                hikariConfig.setLeakDetectionThreshold(30000);
                hikariConfig.setPoolName("SkySignals-MySQL");
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
                hikariConfig.addDataSourceProperty("useUnicode", "true");
                hikariConfig.addDataSourceProperty("characterEncoding", "utf8");

                HikariDataSource opened = new HikariDataSource(hikariConfig);
                try {
                    SchemaManager.runMigrations(opened, logger);
                    synchronized (this) {
                        if (shutdown) { opened.close(); return; }
                        dataSource = opened;
                        connected.set(true);
                    }
                    logger.info("Database connection established.");
                } catch (Exception e) { opened.close(); throw e; }
            } catch (Exception e) {
                logger.warning("Failed to connect to database: " + e.getMessage() + ". Database features disabled.");
                connected.set(false);
            }
        });
    }

    private void runMigrations() {
        if (dataSource == null || shutdown) return;
        try {
            SchemaManager.runMigrations(dataSource, logger);
        } catch (Exception e) {
            logger.warning("Database migration failed: " + e.getMessage());
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public boolean isConnected() {
        return connected.get() && dataSource != null && !dataSource.isClosed();
    }

    public Executor asyncExecutor() {
        return (Runnable r) -> FoliaScheduler.runAsync(plugin, r);
    }

    public synchronized void disconnect() {
        shutdown = true;
        connected.set(false);
        if (dataSource != null) {
            try {
                if (!dataSource.isClosed()) dataSource.close();
            } catch (Exception ignored) {
            }
        }
        logger.info("Database disconnected.");
    }
}
