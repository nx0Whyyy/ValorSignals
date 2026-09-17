package com.valorsky.skysignals.rabbitmq;

import com.valorsky.skysignals.config.Config;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class RabbitManager {

    private final Config config;
    private final Logger logger;
    private Connection connection;
    private Channel channel;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean reconnecting = false;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SkySignals-Rabbit-Admin");
        t.setDaemon(true);
        return t;
    });

    public RabbitManager(Config config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void connect() {
        if (!config.rabbitEnabled()) {
            logger.info("RabbitMQ disabled in configuration.");
            return;
        }
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

            connected.set(true);
            logger.info("Connected to RabbitMQ.");
        } catch (Exception e) {
            logger.warning("Failed to connect to RabbitMQ: " + e.getMessage() + ". Will retry in background.");
            connected.set(false);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;

        Thread t = new Thread(() -> {
            while (!connected.get()) {
                try {
                    Thread.sleep(5000);
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
                    connected.set(true);
                    logger.info("Reconnected to RabbitMQ.");
                    break;
                } catch (Exception e) {
                    logger.warning("RabbitMQ reconnection failed, retrying in 5s: " + e.getMessage());
                }
            }
            reconnecting = false;
        });
        t.setName("SkySignals-Rabbit-Reconnect");
        t.setDaemon(true);
        t.start();
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
        connected.set(false);
        reconnecting = false;
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
        logger.info("RabbitMQ disconnected.");
    }
}
