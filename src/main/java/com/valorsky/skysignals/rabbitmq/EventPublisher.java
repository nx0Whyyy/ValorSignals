package com.valorsky.skysignals.rabbitmq;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.time.Instant;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventPublisher {

    private final JavaPlugin plugin;
    private final RabbitManager rabbitManager;
    private final Logger logger;
    private final Gson gson;

    public EventPublisher(JavaPlugin plugin, RabbitManager rabbitManager, Logger logger) {
        this.plugin = plugin;
        this.rabbitManager = rabbitManager;
        this.logger = logger;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new com.valorsky.skysignals.redis.InstantAdapter())
                .registerTypeAdapter(com.valorsky.skysignals.model.SkyEventType.class, new com.valorsky.skysignals.redis.EnumAdapter<>(com.valorsky.skysignals.model.SkyEventType.class))
                .registerTypeAdapter(com.valorsky.skysignals.model.SkyEventStatus.class, new com.valorsky.skysignals.redis.EnumAdapter<>(com.valorsky.skysignals.model.SkyEventStatus.class))
                .create();
    }

    public void publish(String routingKey, EventState state) {
        if (!rabbitManager.isConnected()) {
            logger.fine("RabbitMQ not connected, cannot publish " + routingKey);
            return;
        }
        FoliaScheduler.runAsync(plugin, () -> {
            try {
                String message = gson.toJson(state);
                Channel channel = rabbitManager.getChannel();
                if (channel != null && channel.isOpen()) {
                    synchronized (channel) {
                    channel.basicPublish(
                            rabbitManager.getExchange(),
                            routingKey,
                            new AMQP.BasicProperties.Builder()
                                    .contentType("application/json")
                                    .deliveryMode(2)
                                    .build(),
                            message.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    );
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to publish RabbitMQ message: " + e.getMessage());
            }
        });
    }

    public String serialize(EventState state) {
        return gson.toJson(state);
    }
}
