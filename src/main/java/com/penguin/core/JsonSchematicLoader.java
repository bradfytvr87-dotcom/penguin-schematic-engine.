package com.penguin.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the simple JSON format: {@code {"name": "...", "blocks": [{"x":0,"y":0,"z":0,"block":"minecraft:stone"}]}}.
 * "block" may carry state properties, e.g. {@code minecraft:oak_stairs[facing=north,half=bottom]}.
 */
public final class JsonSchematicLoader {
    private static final long MAX_BYTES = 5_000_000;

    private JsonSchematicLoader() {}

    public static Schematic load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("File not found: " + path);
        if (Files.size(path) > MAX_BYTES) throw new IOException("Schematic exceeds 5 MB limit");
        return parse(Files.readString(path));
    }

    public static Schematic parse(String json) throws IOException {
        try {
            JsonElement rootEl = JsonParser.parseString(json);
            if (!rootEl.isJsonObject()) throw new JsonParseException("Expected a JSON object");
            JsonObject root = rootEl.getAsJsonObject();
            if (!root.has("name") || !root.has("blocks") || !root.get("blocks").isJsonArray())
                throw new JsonParseException("Expected {name, blocks:[{x,y,z,block}]}");
            JsonArray blocks = root.getAsJsonArray("blocks");
            if (blocks.size() > Schematic.MAX_BLOCKS) throw new IOException("Too many blocks (max " + Schematic.MAX_BLOCKS + ")");
            Schematic.Builder b = Schematic.builder();
            for (JsonElement e : blocks) {
                JsonObject o = e.getAsJsonObject();
                b.add(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                        BlockSpec.parse(o.get("block").getAsString()));
            }
            return b.build(root.get("name").getAsString());
        } catch (JsonParseException | IllegalStateException | NullPointerException | IllegalArgumentException ex) {
            throw new IOException("Invalid schematic JSON: " + ex.getMessage(), ex);
        }
    }

    public static void ensureExample(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path example = dir.resolve("example.json");
        if (!Files.exists(example)) Files.writeString(example, """
            {
              "name": "Penguin Starter",
              "blocks": [
                {"x":0,"y":0,"z":0,"block":"minecraft:stone"},
                {"x":1,"y":0,"z":0,"block":"minecraft:stone_bricks"},
                {"x":0,"y":1,"z":0,"block":"minecraft:sea_lantern"},
                {"x":1,"y":1,"z":0,"block":"minecraft:packed_ice"},
                {"x":0,"y":0,"z":1,"block":"minecraft:oak_stairs[facing=north,half=bottom]"},
                {"x":1,"y":0,"z":1,"block":"minecraft:oak_stairs[facing=east,half=bottom]"}
              ]
            }
            """);
    }
}
