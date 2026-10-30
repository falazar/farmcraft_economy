package com.falazar.farmupcraft.datagen.custom;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public abstract class CodecDataProvider<T> implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final Codec<T> codec;
    private final String name;

    protected CodecDataProvider(PackOutput output, String modid, String directory, String name, Codec<T> codec) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "farmupcraft/" + directory);
        this.name = modid + " " + name;
        this.codec = codec;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Map<Identifier, T> entries = new LinkedHashMap<>();
        buildEntries((id, value) -> {
            if (entries.putIfAbsent(id, value) != null) {
                throw new IllegalStateException("Duplicate " + name + ": " + id);
            }
        });
        Map<Identifier, JsonElement> encoded = new LinkedHashMap<>();
        entries.forEach((id, value) -> encoded.put(id, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()));
        return CompletableFuture.allOf(encoded.entrySet().stream()
                .map(entry -> DataProvider.saveStable(output, entry.getValue(), pathProvider.json(entry.getKey())))
                .toArray(CompletableFuture[]::new));
    }

    protected abstract void buildEntries(BiConsumer<Identifier, T> writer);

    @Override
    public String getName() {
        return name;
    }
}
