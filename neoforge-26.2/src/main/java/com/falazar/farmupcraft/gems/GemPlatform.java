package com.falazar.farmupcraft.gems;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;

public final class GemPlatform {
    private GemPlatform() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static Path gameDir() { return FMLPaths.GAMEDIR.get(); }
    public static boolean gameTestServer(ServerLevel level) {
        return level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
    }
    public static int minY(ServerLevel level) { return level.getMinY(); }
    public static int maxY(ServerLevel level) { return level.getMaxY(); }
    public static String biomeId(Holder<Biome> holder) { return holder.unwrapKey().orElseThrow().identifier().toString(); }
    public static java.util.List<String> biomeIds(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).toList();
    }
    public static TagKey<Biome> biomeTag(String id) { return TagKey.create(Registries.BIOME, Identifier.parse(id)); }
    public static TagKey<Block> blockTag(String id) { return TagKey.create(Registries.BLOCK, Identifier.parse(id)); }
    public static void unsaved(LevelChunk chunk) { chunk.markUnsaved(); }
    public static void place(net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.core.BlockPos pos,
                             net.minecraft.world.level.block.state.BlockState state) { chunk.setBlockState(pos, state, 0); }
    public static int chunkX(LevelChunk chunk) { return chunk.getPos().x(); }
    public static int chunkZ(LevelChunk chunk) { return chunk.getPos().z(); }
    public static long chunkKey(LevelChunk chunk) {
        return Integer.toUnsignedLong(chunkX(chunk)) | ((long) chunkZ(chunk) << 32);
    }
    public static void nameNote(ItemStack note, String text) {
        note.set(DataComponents.CUSTOM_NAME, Component.literal("Miner's rumor"));
        note.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(Component.literal(text))));
    }
}
