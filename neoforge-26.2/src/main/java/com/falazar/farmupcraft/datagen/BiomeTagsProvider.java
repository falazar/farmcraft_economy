package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BiomeTagsProvider extends net.minecraft.data.tags.BiomeTagsProvider {
    public BiomeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, FarmUpCraftNeoForge.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(FUCTags.EMPTY_BIOME_TAG);
        var overworld = tag(FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG);
        // The 26.2 BOP API's overworld list includes End biomes and omits cave biomes.
        // Use the installed mod's dimension tags; datagen lookups can have unbound tags.
        var candidates = installedBiomeIds(BiomeTags.IS_OVERWORLD);
        candidates.removeAll(installedBiomeIds(BiomeTags.IS_NETHER));
        candidates.removeAll(installedBiomeIds(BiomeTags.IS_END));
        candidates.stream()
                .filter(id -> id.getNamespace().equals("biomesoplenty"))
                .sorted()
                .map(id -> ResourceKey.create(Registries.BIOME, id))
                .forEach(overworld::addOptional);
        tag(FUCTags.BIOMES_O_PLENTY_TAG).addOptionalTag(FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG);
        tag(FUCTags.OVERWORLD_NO_OCEAN).addTag(BiomeTags.IS_OVERWORLD).remove(BiomeTags.IS_OCEAN);
    }

    private static Set<Identifier> installedBiomeIds(TagKey<Biome> dimension) {
        var contents = ModList.get().getModFileById("biomesoplenty").getFile().getContents();
        var id = dimension.location();
        String path = "data/" + id.getNamespace() + "/tags/worldgen/biome/" + id.getPath() + ".json";
        try (var stream = contents.openFile(path);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var file = TagFile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(message -> new IllegalStateException("Invalid BOP dimension tag " + id + ": " + message));
            var ids = new HashSet<Identifier>();
            for (var entry : file.entries()) {
                if (entry.isTag()) {
                    throw new IllegalStateException("Expected direct biome IDs in installed BOP dimension tag " + id);
                }
                ids.add(entry.getId());
            }
            for (var entry : file.remove()) {
                if (entry.isTag()) {
                    throw new IllegalStateException("Expected direct biome removals in installed BOP dimension tag " + id);
                }
                ids.remove(entry.getId());
            }
            return ids;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read installed BOP dimension tag " + id, exception);
        }
    }
}
