package com.valorsky.skysignals.redis;

import com.google.gson.*;

import java.io.IOException;
import java.lang.reflect.Type;

public final class EnumAdapter<E extends Enum<E>> implements JsonSerializer<E>, JsonDeserializer<E> {

    private final Class<E> enumClass;

    public EnumAdapter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public JsonElement serialize(E src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.name());
    }

    @Override
    public E deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            return Enum.valueOf(enumClass, json.getAsString());
        } catch (IllegalArgumentException e) {
            return enumClass.getEnumConstants()[0];
        }
    }
}
