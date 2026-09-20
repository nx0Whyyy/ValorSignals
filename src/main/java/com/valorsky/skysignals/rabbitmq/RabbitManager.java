package com.valorsky.skysignals.rabbitmq;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class RabbitManager {

    private final JavaPlugin plugin;
    private final Config config;
    private final Logger logger;
    private volatile Connection connection;
    private volatile Channel channel;
    private volatile Runnable connectionListener = () -> {};

    public void onConnected(Runnable listener) {
        connectionListener = listener;
        if (isConnected()) listener.run();
    }
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean reconnecting = false;
    private volatile boolean shutdown = false;

    public RabbitManager(JavaPlugin plugin, Config config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    public void connect() {
        if (shutdown) return;
        if (!config.rabbitEnabled()) {
            logger.info("RabbitMQ disabled in configuration.");
            return;
        }
        FoliaScheduler.runAsync(plugin, () -> {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(config.rabbitHost());
                factory.setPort(config.rabbitPort());
                factory.setUsername(config.rabbitUsername());
                factory.setPassword(config.rabbitPassword());

                connection = factory.newConnection("SkySignals-" + config.serverId());
                channel = connection.createChannel();

                channel.exchangeDeclare(config.rabbitExchange(), "topic", true);
                String queue = config.rabbitExchange() + ".events." + config.serverId();
                channel.queueDeclare(queue, true, false, false, null);
                channel.queueBind(queue, config.rabbitExchange(), "event.*");

                if (shutdown) { channel.close(); connection.close(); return; }
                connected.set(true);
                connectionListener.run();
                logger.info("Connected to RabbitMQ.");
            } catch (Exception e) {
                logger.warning("Failed to connect to RabbitMQ: " + e.getMessage() + ". Will retry in background.");
                connected.set(false);
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (reconnecting || shutdown) return;
        reconnecting = true;
        retryConnect();
    }

    private void retryConnect() {
        if (shutdown || connected.get()) {
            reconnecting = false;
            return;
        }
        FoliaScheduler.runAsyncDelayed(plugin, () -> {
            if (shutdown) return;
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(config.rabbitHost());
                factory.setPort(config.rabbitPort());
                factory.setUsername(config.rabbitUsername());
                factory.setPassword(config.rabbitPassword());

                connection = factory.newConnection("SkySignals-" + config.serverId());
                channel = connection.createChannel();
                channel.exchangeDeclare(config.rabbitExchange(), "topic", true);
                String queue = config.rabbitExchange() + ".events." + config.serverId();
                channel.queueDeclare(queue, true, false, false, null);
                channel.queueBind(queue, config.rabbitExchange(), "event.*");
                if (shutdown) { channel.close(); connection.close(); return; }
                connected.set(true);
                connectionListener.run();
                logger.info("Reconnected to RabbitMQ.");
            } catch (Exception e) {
                if (!shutdown) {
                    logger.warning("RabbitMQ reconnection failed, retrying in 5s: " + e.getMessage());
                    retryConnect();
                }
            } finally {
                if (connected.get()) {
                    reconnecting = false;
                }
            }
        }, 100L);
    }

    public boolean isConnected() {
        return connected.get() && channel != null && channel.isOpen();
    }

    public Channel getChannel() {
        return channel;
    }

    public String getExchange() {
        return config.rabbitExchange();
    }

    public void disconnect() {
        shutdown = true;
        connected.set(false);
        reconnecting = false;
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception ignored) {
        }
        logger.info("RabbitMQ disconnected.");
    }
}
