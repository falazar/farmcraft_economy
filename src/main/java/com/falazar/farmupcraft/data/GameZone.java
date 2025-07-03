package com.falazar.farmupcraft.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.UUID;

/**
 * Represents a distinct area in the world such as a lake, island, cave, arena, etc.
 * Each GameZone is defined by a collection of sections and has a unique identifier.
 */
public class GameZone {
    
    public enum ZoneType {
        LAKE, ISLAND, CAVE, ARENA, COUNTRY, 
        FOREST, PLAINS, MOUNTAIN, RIVER, OCEAN
    }
    
    private final List<SectionPos> sections;
    private final ZoneType type;
    private final BlockPos center;
    private final String name;
    private final UUID uuid;
    
    /**
     * Creates a new GameZone with auto-generated name and UUID.
     * @param sections The sections that make up this zone
     * @param type The type of zone this represents
     */
    public GameZone(List<SectionPos> sections, ZoneType type) {
        this.sections = sections;
        this.type = type;
        this.uuid = UUID.randomUUID();
        this.center = calculateCenter();
        this.name = generateName();
    }
    
    /**
     * Creates a new GameZone with a custom name.
     * @param sections The sections that make up this zone
     * @param type The type of zone this represents
     * @param name The custom name for this zone
     */
    public GameZone(List<SectionPos> sections, ZoneType type, String name) {
        this.sections = sections;
        this.type = type;
        this.uuid = UUID.randomUUID();
        this.center = calculateCenter();
        this.name = name;
    }
    
    /**
     * Creates a new GameZone from a list of chunks, converting them to sections at Y=64.
     * @param chunks The chunks that make up this zone
     * @param type The type of zone this represents
     * @return A new GameZone instance
     */
    public static GameZone fromChunks(List<ChunkPos> chunks, ZoneType type) {
        List<SectionPos> sections = chunks.stream()
                .map(chunk -> SectionPos.of(chunk, 4)) // Y=64 corresponds to section 4 (64/16=4)
                .toList();
        return new GameZone(sections, type);
    }
    
    /**
     * Calculates the center point of the zone based on the average of all section positions.
     * @return The center BlockPos of the zone
     */
    private BlockPos calculateCenter() {
        if (sections.isEmpty()) {
            return BlockPos.ZERO;
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (SectionPos section : sections) {
            int x = section.minBlockX();
            int y = section.minBlockY();
            int z = section.minBlockZ();
            
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        
        return new BlockPos(
            (minX + maxX) / 2,
            (minY + maxY) / 2,
            (minZ + maxZ) / 2
        );
    }
    
    /**
     * Generates an auto name for the zone using the type and a random 2-digit number.
     * @return The generated name
     */
    private String generateName() {
        int randomNum = (int) (Math.random() * 90) + 10; // 10-99
        return type.name() + "-" + randomNum;
    }
    
    // Getters
    public List<SectionPos> getSections() { return sections; }
    public ZoneType getType() { return type; }
    public BlockPos getCenter() { return center; }
    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public int getSize() { return sections.size(); }
    
    /**
     * Checks if this zone contains a specific section.
     * @param section The section to check
     * @return true if the zone contains this section
     */
    public boolean containsSection(SectionPos section) {
        return sections.contains(section);
    }
    
    /**
     * Checks if this zone contains a specific block position.
     * @param blockPos The block position to check
     * @return true if the zone contains this block
     */
    public boolean containsBlock(BlockPos blockPos) {
        SectionPos section = SectionPos.of(blockPos);
        return sections.contains(section);
    }
    
    /**
     * Gets all chunk positions that this zone overlaps with.
     * @return List of ChunkPos that this zone contains
     */
    public List<ChunkPos> getChunkPositions() {
        return sections.stream()
                .map(SectionPos::chunk)
                .distinct()
                .toList();
    }
    
    @Override
    public String toString() {
        return "GameZone{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", size=" + sections.size() +
                ", center=" + center +
                ", uuid=" + uuid +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GameZone gameZone = (GameZone) obj;
        return uuid.equals(gameZone.uuid);
    }
    
    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
} 