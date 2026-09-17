package com.valorsky.skysignals.rabbitmq;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Logger;

public final class EventConsumer {

    private final RabbitManager rabbitManager;
    private final Config config;
    private final Logger logger;
    private final Gson gson;
    private final SkyEventManager eventManager;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public EventConsumer(RabbitManager rabbitManager, Config config, Logger logger, SkyEventManager eventManager, org.bukkit.plugin.java.JavaPlugin plugin) {
        this.rabbitManager = rabbitManager;
        this.config = config;
        this.logger = logger;
        this.eventManager = eventManager;
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new com.valorsky.skysignals.redis.InstantAdapter())
                .registerTypeAdapter(com.valorsky.skysignals.model.SkyEventType.class, new com.valorsky.skysignals.redis.EnumAdapter<>(com.valorsky.skysignals.model.SkyEventType.class))
                .registerTypeAdapter(com.valorsky.skysignals.model.SkyEventStatus.class, new com.valorsky.skysignals.redis.EnumAdapter<>(com.valorsky.skysignals.model.SkyEventStatus.class))
                .create();
    }

    public void startConsuming() {
        if (!rabbitManager.isConnected()) {
            logger.warning("RabbitMQ not connected, cannot start consumer.");
            return;
        }
        try {
            Channel channel = rabbitManager.getChannel();
            String queue = config.rabbitExchange() + ".events." + config.serverId();

            com.rabbitmq.client.Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String routingKey = envelope.getRoutingKey();
                    String message = new String(body, StandardCharsets.UTF_8);
                    long deliveryTag = envelope.getDeliveryTag();

                    try {
                        EventState state = gson.fromJson(message, EventState.class);
                        // Dispatch to Folia global scheduler for thread-safe event handling
                        FoliaScheduler.runGlobal(plugin, () -> handleMessage(routingKey, state));
                    } catch (Exception e) {
                        logger.warning("Failed to process RabbitMQ message: " + e.getMessage());
                    } finally {
                        channel.basicAck(deliveryTag, false);
                    }
                }
            };

            channel.basicConsume(queue, false, consumer);
            logger.info("RabbitMQ consumer started on queue: " + queue);
        } catch (Exception e) {
            logger.warning("Failed to start RabbitMQ consumer: " + e.getMessage());
        }
    }

    private void handleMessage(String routingKey, EventState state) {
        if (state.serverId().equals(config.serverId())) {
            return;
        }

        switch (routingKey) {
            case "event.created":
                eventManager.handleEventCreated(state);
                break;
            case "event.started":
                eventManager.handleEventStarted(state);
                break;
            case "event.finished":
            case "event.cancelled":
                eventManager.handleEventFinished(state);
                break;
            default:
                logger.warning("Unknown routing key: " + routingKey);
        }
    }
}