package com.valorsky.skysignals.redis;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class RedisService {

    private final Config config;
    private final Logger logger;
    private final Gson gson;
    private final Executor asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SkySignals-Redis-Async");
        t.setDaemon(true);
        return t;
    });

    private RedisClient client;
    private io.lettuce.core.api.StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> sync;
    private RedisAsyncCommands<String, String> async;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisPubSubAsyncCommands<String, String> pubSubAsync;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final String prefix = "skysignals:";
    private final List<RedisEventListener> listeners = new ArrayList<>();
    private volatile boolean reconnecting = false;
    private volatile boolean shutdown = false;

    public RedisService(Config config, Logger logger) {
        this.config = config;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(SkyEventType.class, new EnumAdapter<>(SkyEventType.class))
                .registerTypeAdapter(SkyEventStatus.class, new EnumAdapter<>(SkyEventStatus.class))
                .create();
        this.logger = logger;
    }

    public void connect() {
        if (shutdown) return;
        if (!config.redisEnabled()) {
            logger.info("Redis disabled in configuration.");
            return;
        }
         try {
             RedisURI uri = RedisURI.create(config.redisUri());
             if (!config.redisPassword().isEmpty()) {
                 uri.setPassword(config.redisPassword().toCharArray());
             }
             client = RedisClient.create(uri);
             connection = client.connect();
             sync = connection.sync();
             async = connection.async();
             connected.set(true);
             logger.info("Connected to Redis.");
             startPubSub();
         } catch (Exception e) {
             logger.warning("Failed to connect to Redis: " + e.getMessage() + ". Will retry in background.");
             client = null;
             connection = null;
             sync = null;
             async = null;
             connected.set(false);
             scheduleReconnect();
         }
    }

    private void startPubSub() {
        try {
            pubSubConnection = client.connectPubSub();
            pubSubAsync = pubSubConnection.async();

            pubSubConnection.addListener(new RedisPubSubListener<String, String>() {
                @Override
                public void message(String channel, String message) {
                    handlePubSubMessage(message);
                }

                @Override
                public void message(String pattern, String channel, String message) {
                    handlePubSubMessage(message);
                }

                @Override
                public void subscribed(String channel, long count) {
                }

                @Override
                public void unsubscribed(String channel, long count) {
                }

                @Override
                public void psubscribed(String pattern, long count) {
                }

                @Override
                public void punsubscribed(String pattern, long count) {
                }
            });

            pubSubAsync.psubscribe(config.redisChannel());
        } catch (Exception e) {
            logger.warning("Failed to start Redis pub/sub: " + e.getMessage());
        }
    }

    private void handlePubSubMessage(String message) {
        try {
            EventState state = gson.fromJson(message, EventState.class);
            for (RedisEventListener listener : listeners) {
                listener.onEventPublished(state);
            }
        } catch (Exception e) {
            logger.warning("Failed to process Redis pub/sub message: " + e.getMessage());
        }
    }

    public void storeEventState(EventState state) {
        if (!connected.get() || shutdown) {
            return;
        }
        asyncExecutor.execute(() -> {
            try {
                String key = prefix + "event:" + state.id();
                Map<String, String> data = new HashMap<>();
                data.put("type", state.type().name());
                data.put("server", state.serverId());
                data.put("status", state.status().name());
                data.put("start", String.valueOf(state.startedAt().getEpochSecond()));
                data.put("end", String.valueOf(state.endsAt().getEpochSecond()));
                data.put("json", gson.toJson(state));

                async.hset(key, data);
                long ttl = Math.max(60, state.endsAt().getEpochSecond() - Instant.now().getEpochSecond() + 60);
                async.expire(key, ttl);

                async.publish(config.redisChannel(), gson.toJson(state));
            } catch (Exception e) {
                logger.warning("Failed to store event in Redis: " + e.getMessage());
            }
        });
    }

    public void updateEventState(EventState state) {
        storeEventState(state);
    }

    public void removeEventState(String eventId) {
        if (!connected.get() || shutdown) return;
        asyncExecutor.execute(() -> {
            try {
                async.del(prefix + "event:" + eventId);
            } catch (Exception e) {
                logger.warning("Failed to remove event from Redis: " + e.getMessage());
            }
        });
    }

    public EventState getEventState(String eventId) {
        if (!connected.get()) return null;
        try {
            String json = sync.hget(prefix + "event:" + eventId, "json");
            if (json == null) return null;
            return gson.fromJson(json, EventState.class);
        } catch (Exception e) {
            logger.warning("Failed to get event from Redis: " + e.getMessage());
            return null;
        }
    }

    public List<EventState> getAllActiveEvents() {
        if (!connected.get()) return Collections.emptyList();
        try {
            List<String> keys = sync.keys(prefix + "event:*");
            List<EventState> result = new ArrayList<>();
            for (String key : keys) {
                String json = sync.hget(key, "json");
                if (json != null) {
                    EventState state = gson.fromJson(json, EventState.class);
                    if (state.status() == SkyEventStatus.ACTIVE) {
                        result.add(state);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            logger.warning("Failed to scan active events from Redis: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void addListener(RedisEventListener listener) {
        listeners.add(listener);
    }

    private void scheduleReconnect() {
        if (reconnecting || shutdown) return;
        reconnecting = true;

        Thread t = new Thread(() -> {
            while (!connected.get() && !shutdown) {
                try {
                    Thread.sleep(5000);
                    if (shutdown) break;
                    RedisURI uri = RedisURI.create(config.redisUri());
                    if (!config.redisPassword().isEmpty()) {
                        uri.setPassword(config.redisPassword().toCharArray());
                    }
                    client = RedisClient.create(uri);
                    connection = client.connect();
                    sync = connection.sync();
                    async = connection.async();
                    connected.set(true);
                    logger.info("Reconnected to Redis.");
                    startPubSub();
                    break;
                } catch (Exception e) {
                    if (shutdown) break;
                    logger.warning("Redis reconnection failed, retrying in 5s: " + e.getMessage());
                }
            }
            reconnecting = false;
        });
        t.setName("SkySignals-Redis-Reconnect");
        t.setDaemon(true);
        t.start();
    }

    public void disconnect() {
        shutdown = true;
        connected.set(false);
        reconnecting = false;
        try {
            if (pubSubConnection != null) pubSubConnection.close();
            if (async != null) async.getStatefulConnection().close();
        } catch (Exception ignored) {
        }
        if (client != null) client.shutdown();
        listeners.clear();
        logger.info("Redis disconnected.");
    }

    public RedisClient getClient() {
        return client;
    }

    @FunctionalInterface
    public interface RedisEventListener {
        void onEventPublished(EventState state);
    }
}