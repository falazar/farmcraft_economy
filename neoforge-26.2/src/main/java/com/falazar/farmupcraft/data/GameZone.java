package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.UUID;

/** A named area defined by its sections and identified by UUID. */
public class GameZone {
    public enum ZoneType {
        LAKE, ISLAND, CAVE, DEEP_DARK, COUNTRY, FOREST, MOUNTAIN, RIVER, OCEAN;

        public static final Codec<ZoneType> CODEC = Codec.STRING.xmap(ZoneType::valueOf, ZoneType::name);
    }

    public static final Codec<SectionPos> GAMEZONE_SECTION_POS_CODEC =
            Codec.LONG.xmap(SectionPos::of, SectionPos::asLong);

    private static final Codec<List<SectionPos>> SECTIONS_CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<List<SectionPos>, T>> decode(DynamicOps<T> ops, T input) {
            return GAMEZONE_SECTION_POS_CODEC.listOf().decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(List<SectionPos> input, DynamicOps<T> ops, T prefix) {
            // 1.20 NbtOps promoted nonempty long lists to long arrays; 26.2 no longer does.
            if (input.isEmpty()) return GAMEZONE_SECTION_POS_CODEC.listOf().encode(input, ops, prefix);
            return Codec.LONG_STREAM.encode(input.stream().mapToLong(SectionPos::asLong), ops, prefix);
        }
    };

    public static final Codec<GameZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(GameZone::getUuid),
            Codec.STRING.fieldOf("name").forGetter(GameZone::getName),
            SECTIONS_CODEC.fieldOf("sections").forGetter(GameZone::getSections),
            ZoneType.CODEC.fieldOf("type").forGetter(GameZone::getType),
            BlockPos.CODEC.fieldOf("center").forGetter(GameZone::getCenter))
            .apply(instance, GameZone::new));

    private final UUID uuid;
    private final String name;
    private final List<SectionPos> sections;
    private final ZoneType type;
    private final BlockPos center;

    public GameZone(UUID uuid, String name, List<SectionPos> sections, ZoneType type, BlockPos center) {
        this.uuid = uuid;
        this.name = name;
        this.sections = sections;
        this.type = type;
        this.center = center;
    }

    public GameZone(List<SectionPos> sections, ZoneType type) {
        this.sections = sections;
        this.type = type;
        this.uuid = UUID.randomUUID();
        this.center = calculateCenter();
        this.name = generateName();
    }

    public GameZone(List<SectionPos> sections, ZoneType type, String name) {
        this.sections = sections;
        this.type = type;
        this.uuid = UUID.randomUUID();
        this.center = calculateCenter();
        this.name = name;
    }

    public static GameZone fromChunks(List<ChunkPos> chunks, ZoneType type) {
        return new GameZone(chunks.stream().map(chunk -> SectionPos.of(chunk, 4)).toList(), type);
    }

    private BlockPos calculateCenter() {
        if (sections.isEmpty()) return BlockPos.ZERO;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (SectionPos section : sections) {
            minX = Math.min(minX, section.minBlockX());
            minY = Math.min(minY, section.minBlockY());
            minZ = Math.min(minZ, section.minBlockZ());
            maxX = Math.max(maxX, section.minBlockX());
            maxY = Math.max(maxY, section.minBlockY());
            maxZ = Math.max(maxZ, section.minBlockZ());
        }
        // Legacy center uses the extrema of section origins, not occupied block centers or an average.
        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    private String generateName() { return type.name() + "-" + ((int) (Math.random() * 90) + 10); }
    public List<SectionPos> getSections() { return sections; }
    public ZoneType getType() { return type; }
    public BlockPos getCenter() { return center; }
    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public int getSize() { return sections.size(); }
    public boolean containsSection(SectionPos section) { return sections.contains(section); }
    public boolean containsBlock(BlockPos blockPos) { return sections.contains(SectionPos.of(blockPos)); }
    public List<ChunkPos> getChunkPositions() {
        return sections.stream().map(SectionPos::chunk).distinct().toList();
    }

    @Override
    public String toString() {
        return "GameZone{" + "name='" + name + '\'' + ", type=" + type + ", size=" + sections.size()
                + ", center=" + center + ", uuid=" + uuid + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return uuid.equals(((GameZone) obj).uuid);
    }

    @Override
    public int hashCode() { return uuid.hashCode(); }
}
