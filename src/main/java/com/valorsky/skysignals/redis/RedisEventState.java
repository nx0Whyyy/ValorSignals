package com.valorsky.skysignals.redis;

import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RedisEventState(
    UUID id,
    SkyEventType type,
    String serverId,
    Instant startedAt,
    Instant endsAt,
    SkyEventStatus status,
    Map<String, Object> data
) {
    public String redisKey() {
        return "skysignals:event:" + id();
    }

    public String toRedisValue() {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(type.name()).append("\n");
        sb.append("server=").append(serverId).append("\n");
        sb.append("status=").append(status.name()).append("\n");
        sb.append("start=").append(startedAt.getEpochSecond()).append("\n");
        sb.append("end=").append(endsAt.getEpochSecond()).append("\n");
        return sb.toString();
    }
}
