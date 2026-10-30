package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.util.FUCTags;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BiomeTagsClassificationTest {
    @Test
    void installedBopOverworldClassificationExcludesOtherDimensionsAndIncludesCaves(MinecraftServer server) {
        Path output = Path.of("build", "biome-classification-tests");
        Map<Path, JsonElement> written = new ConcurrentHashMap<>();
        CachedOutput cache = (path, bytes, hash) -> {
            assertTrue(path.startsWith(output));
            assertNull(written.put(path, JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))));
        };
        new BiomeTagsProvider(new PackOutput(output),
                CompletableFuture.completedFuture(server.registryAccess())).run(cache).join();

        var biomeTag = FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG.location();
        var entries = written.get(output.resolve("data").resolve(biomeTag.getNamespace())
                .resolve("tags").resolve("worldgen").resolve("biome").resolve(biomeTag.getPath() + ".json"))
                .getAsJsonObject().getAsJsonArray("values");
        Set<Identifier> actual = StreamSupport.stream(entries.spliterator(), false).map(entry -> {
            var value = entry.getAsJsonObject();
            assertFalse(value.get("required").getAsBoolean());
            return Identifier.parse(value.get("id").getAsString());
        }).collect(Collectors.toSet());

        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        Set<Identifier> expected = biomes.getOrThrow(BiomeTags.IS_OVERWORLD).stream()
                .filter(holder -> !holder.is(BiomeTags.IS_NETHER) && !holder.is(BiomeTags.IS_END))
                .map(Holder::unwrapKey).map(java.util.Optional::orElseThrow)
                .map(key -> key.identifier())
                .filter(id -> id.getNamespace().equals("biomesoplenty"))
                .collect(Collectors.toSet());
        assertEquals(expected, actual);
        assertEquals(59, actual.size(), "Pinned BOP 26.2.0.0.28 has 59 classified overworld biomes");
        assertTrue(actual.contains(Identifier.parse("biomesoplenty:glowing_grotto")));
        assertTrue(actual.contains(Identifier.parse("biomesoplenty:spider_nest")));
        for (var id : actual) {
            Holder<Biome> biome = biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME, id));
            assertTrue(biome.is(BiomeTags.IS_OVERWORLD), id.toString());
            assertFalse(biome.is(BiomeTags.IS_NETHER), id.toString());
            assertFalse(biome.is(BiomeTags.IS_END), id.toString());
        }
    }
}
