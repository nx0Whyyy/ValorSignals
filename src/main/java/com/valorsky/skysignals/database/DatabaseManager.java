package com.valorsky.skysignals.database;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.schema.SchemaManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class DatabaseManager {

    private final Config config;
    private final Logger logger;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private HikariDataSource dataSource;
    private final Executor asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SkySignals-Database");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void connect() {
        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.dbHost() + ":" + config.dbPort() + "/" + config.dbName());
            hikariConfig.setUsername(config.dbUsername());
            hikariConfig.setPassword(config.dbPassword());
            hikariConfig.setMaximumPoolSize(config.dbPoolSize());
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setLeakDetectionThreshold(30000);
            hikariConfig.setPoolName("SkySignals-MySQL");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useUnicode", "true");
            hikariConfig.addDataSourceProperty("characterEncoding", "utf8");

            dataSource = new HikariDataSource(hikariConfig);
            connected.set(true);
            logger.info("Database connection established.");

            asyncExecutor.execute(this::runMigrations);
        } catch (Exception e) {
            logger.warning("Failed to connect to database: " + e.getMessage() + ". Database features disabled.");
            connected.set(false);
        }
    }

    private void runMigrations() {
        if (dataSource == null) return;
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
        return asyncExecutor;
    }

    public void disconnect() {
        connected.set(false);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        // Shutdown the async executor
        if (asyncExecutor instanceof java.util.concurrent.ExecutorService es) {
            es.shutdown();
        }
        logger.info("Database disconnected.");
    }
}