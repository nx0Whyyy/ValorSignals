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
        assertEquals("skysignals:item/meteor_flight",
                definition.getAsJsonObject("model").get("model").getAsString());
    }

    @Test
    void flightModelUsesClientSafeElementsAndHasAValidTexture() throws Exception {
        var model = JsonParser.parseString(Files.readString(
                PACK.resolve("assets/skysignals/models/item/meteor_flight.json"))).getAsJsonObject();
        assertEquals(13, model.getAsJsonArray("elements").size());
        assertTrue(model.getAsJsonArray("elements").asList().stream()
                .allMatch(element -> element.getAsJsonObject().getAsJsonObject("faces").size() == 6));
        assertTrue(model.getAsJsonArray("elements").asList().stream()
                .filter(element -> element.getAsJsonObject().has("rotation"))
                .allMatch(element -> {
                    var rotation = element.getAsJsonObject().getAsJsonObject("rotation");
                    return rotation.has("axis") && rotation.has("angle")
                            && !rotation.has("x") && !rotation.has("y") && !rotation.has("z");
                }));
        assertEquals("skysignals:item/meteor_flight",
                model.getAsJsonObject("textures").get("rock").getAsString());

        var texture = ImageIO.read(PACK.resolve("assets/skysignals/textures/item/meteor_flight.png").toFile());
        assertNotNull(texture);
        assertEquals(32, texture.getWidth());
        assertEquals(32, texture.getHeight());
    }
}
