package com.valorsky.skysignals.rabbitmq;

import com.valorsky.skysignals.model.EventState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class EventPublisher {

    private final RabbitManager rabbitManager;
    private final Logger logger;
    private final Gson gson;
    private final Executor asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SkySignals-Rabbit-Publish");
        t.setDaemon(true);
        return t;
    });

    public EventPublisher(RabbitManager rabbitManager, Logger logger) {
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
            logger.warning("RabbitMQ not connected, cannot publish " + routingKey);
            return;
        }
        asyncExecutor.execute(() -> {
            try {
                String message = gson.toJson(state);
                Channel channel = rabbitManager.getChannel();
                if (channel != null && channel.isOpen()) {
                    channel.basicPublish(
                            rabbitManager.getExchange(),
                            routingKey,
                            new AMQP.BasicProperties.Builder()
                                    .contentType("application/json")
                                    .deliveryMode(2)
                                    .build(),
                            message.getBytes()
                    );
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
