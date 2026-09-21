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
        assertEquals("skysignals:item/meteor_flight_source",
                definition.getAsJsonObject("model").get("model").getAsString());

        var impactDefinition = JsonParser.parseString(Files.readString(
                PACK.resolve("assets/skysignals/items/meteor_impact.json"))).getAsJsonObject();
        assertEquals("skysignals:item/meteor_impact_1",
                impactDefinition.getAsJsonObject("model").get("model").getAsString());
    }

    @Test
    void suppliedImpactSceneKeepsAllObjElements() throws Exception {
        var model = JsonParser.parseString(Files.readString(
                PACK.resolve("assets/skysignals/models/item/meteor.json"))).getAsJsonObject();
        assertEquals(46, model.getAsJsonArray("elements").size());
        assertTrue(model.getAsJsonArray("elements").asList().stream()
                .allMatch(element -> element.getAsJsonObject().getAsJsonObject("faces").size() == 6));
    }

    @Test
    void suppliedAnimationIsSplitIntoProgressiveImpactStages() throws Exception {
        int[] expectedElements = {9, 21, 30, 46};
        for (int stage = 1; stage <= 4; stage++) {
            var model = JsonParser.parseString(Files.readString(PACK.resolve(
                    "assets/skysignals/models/item/meteor_impact_" + stage + ".json"))).getAsJsonObject();
            assertEquals(expectedElements[stage - 1], model.getAsJsonArray("elements").size());
        }
        var flight = JsonParser.parseString(Files.readString(PACK.resolve(
                "assets/skysignals/models/item/meteor_flight_source.json"))).getAsJsonObject();
        assertEquals(1, flight.getAsJsonArray("elements").size());
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
