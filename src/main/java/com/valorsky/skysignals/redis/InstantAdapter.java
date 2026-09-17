package com.valorsky.skysignals.redis;

import com.google.gson.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;

public final class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

    @Override
    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getEpochSecond());
    }

    @Override
    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isNumber()) {
                return Instant.ofEpochSecond(json.getAsJsonPrimitive().getAsLong());
            }
            return Instant.parse(json.getAsString());
        }
        return Instant.now();
    }
}
