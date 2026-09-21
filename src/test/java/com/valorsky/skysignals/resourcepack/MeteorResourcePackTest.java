package com.valorsky.skysignals.resourcepack;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MeteorResourcePackTest {

    private static final Path PACK = Path.of("resource-pack");

    @Test
    void packTargetsMinecraft12111AndReferencesTheMeteorModel() throws Exception {
        var metadata = JsonParser.parseString(Files.readString(PACK.resolve("pack.mcmeta"))).getAsJsonObject();
        var pack = metadata.getAsJsonObject("pack");
        assertEquals(75, pack.getAsJsonArray("min_format").get(0).getAsInt());
        assertEquals(0, pack.getAsJsonArray("min_format").get(1).getAsInt());
        assertEquals(75, pack.getAsJsonArray("max_format").get(0).getAsInt());
        assertEquals(0, pack.getAsJsonArray("max_format").get(1).getAsInt());

        var definition = JsonParser.parseString(Files.readString(
                PACK.resolve("assets/skysignals/items/meteor.json"))).getAsJsonObject();
        assertEquals("skysignals:item/meteor",
                definition.getAsJsonObject("model").get("model").getAsString());
    }

    @Test
    void convertedModelKeepsEveryObjCuboidAndHasAValidTexture() throws Exception {
        var model = JsonParser.parseString(Files.readString(
                PACK.resolve("assets/skysignals/models/item/meteor.json"))).getAsJsonObject();
        assertEquals(46, model.getAsJsonArray("elements").size());
        assertTrue(model.getAsJsonArray("elements").asList().stream()
                .allMatch(element -> element.getAsJsonObject().getAsJsonObject("faces").size() == 6));
        assertEquals("skysignals:item/meteor",
                model.getAsJsonObject("textures").get("meteor").getAsString());

        var texture = ImageIO.read(PACK.resolve("assets/skysignals/textures/item/meteor.png").toFile());
        assertNotNull(texture);
        assertEquals(256, texture.getWidth());
        assertEquals(256, texture.getHeight());
    }
}
