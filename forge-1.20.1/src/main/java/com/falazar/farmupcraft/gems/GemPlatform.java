package com.falazar.farmupcraft.gems;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

public final class GemPlatform {
    private GemPlatform() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static Path gameDir() { return FMLPaths.GAMEDIR.get(); }
    public static boolean gameTestServer(ServerLevel level) {
        return level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
    }
    public static int minY(ServerLevel level) { return level.getMinBuildHeight(); }
    public static int maxY(ServerLevel level) { return level.getMaxBuildHeight(); }
    public static String biomeId(Holder<Biome> holder) { return holder.unwrapKey().orElseThrow().location().toString(); }
    public static java.util.List<String> biomeIds(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).toList();
    }
    public static TagKey<Biome> biomeTag(String id) { return TagKey.create(Registries.BIOME, new ResourceLocation(id)); }
    public static TagKey<Block> blockTag(String id) { return TagKey.create(Registries.BLOCK, new ResourceLocation(id)); }
    public static void unsaved(LevelChunk chunk) { chunk.setUnsaved(true); }
    public static void place(net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.core.BlockPos pos,
                             net.minecraft.world.level.block.state.BlockState state) { chunk.setBlockState(pos, state, false); }
    public static int chunkX(LevelChunk chunk) { return chunk.getPos().x; }
    public static int chunkZ(LevelChunk chunk) { return chunk.getPos().z; }
    public static long chunkKey(LevelChunk chunk) { return chunk.getPos().toLong(); }
    public static void nameNote(ItemStack note, String text) {
        note.setHoverName(Component.literal("Miner's rumor"));
        var lore = new net.minecraft.nbt.ListTag();
        lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(Component.literal(text))));
        note.getOrCreateTagElement("display").put("Lore", lore);
    }
}
