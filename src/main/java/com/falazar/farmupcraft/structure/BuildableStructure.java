package com.falazar.farmupcraft.structure;

import com.falazar.farmupcraft.entity.CampForeman;
import com.falazar.farmupcraft.entity.StructureTemplateAccess;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.BlockUtil;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public class BuildableStructure {
    //todo account for rotation
    public static final Logger LOGGER = LogUtils.getLogger();

    public final ResourceLocation structureLocation;
    public List<StructureBuildTask> blockTasks = new ArrayList<>();
    public List<StructureEntityTask> entityTasks = new ArrayList<>();
    public Vec3i structureSize = Vec3i.ZERO;
    private BoundingBox boundingBox = null;
    private StructureTemplate template = null;

    public BuildableStructure(ResourceLocation location) {
        this.structureLocation = Objects.requireNonNull(location);
    }

    public void gatherAndPopulateStructureData(ServerLevel level) {
        this.blockTasks.clear();
        this.entityTasks.clear();
        Optional<StructureTemplate> structureTemplate = getStructureTemplate(level);
        if (structureTemplate.isEmpty()) {
            LOGGER.warn("Structure not found: {}", structureLocation);
            return;
        }

        StructureTemplate template = structureTemplate.get();
        this.template = template;
        this.structureSize = template.getSize();

        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccess) template).getStructurePalette();
        for (StructureTemplate.Palette palette : palettes) {
            List<StructureTemplate.StructureBlockInfo> structureBlockInfos = palette.blocks();
            for (StructureTemplate.StructureBlockInfo structureBlockInfo : structureBlockInfos) {
                if(structureBlockInfo.state().is(Blocks.JIGSAW) && structureBlockInfo.nbt() != null) {

                    CompoundTag tag = structureBlockInfo.nbt();
                    String s = tag.getString("final_state");
                    BlockState blockstate = Blocks.AIR.defaultBlockState();

                    try {
                        blockstate = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), s, true).blockState();
                    } catch (CommandSyntaxException commandsyntaxexception) {
                        LOGGER.error("Error while parsing blockstate {} in jigsaw block @ {}", s, structureBlockInfo.pos());
                    }
                    blockTasks.add(new StructureBuildTask(structureBlockInfo.pos(), blockstate, structureBlockInfo.nbt()));
                } else {
                    blockTasks.add(new StructureBuildTask(structureBlockInfo.pos(), structureBlockInfo.state(), structureBlockInfo.nbt()));
                }
            }
        }

        List<StructureTemplate.StructureEntityInfo> entityInfos = ((StructureTemplateAccess) template).getStructureEntityInfo();
        for (StructureTemplate.StructureEntityInfo info : entityInfos) {
            entityTasks.add(new StructureEntityTask(info.pos, info.blockPos, info.nbt));
        }

        sortTasks();
    }

    public Optional<StructureTemplate> getStructureTemplate(ServerLevel level) {
        Optional<StructureTemplate> templateOptional = level.getStructureManager().get(structureLocation);
        if (templateOptional.isEmpty()) {
            LOGGER.error("Failed to find specified structure for nbt: {}", structureLocation);
            return Optional.empty();
        }
        return templateOptional;
    }

    public StructureTemplate getTemplate() {
        if (template == null) throw new IllegalStateException("Structure for template " + structureLocation + " was not initialized");
        return template;
    }

    private void sortTasks() {
        blockTasks.sort(Comparator.comparingInt(t -> t.relativePos.getY()));
    }

    public boolean isPopulated() {
        return !blockTasks.isEmpty();
    }

    public int getTaskCount() {
        return blockTasks.size();
    }

    public BoundingBox getBoundingBox(BlockPos origin) {
        if (boundingBox != null) return boundingBox;
        if (template == null) {
            LOGGER.warn("Template not yet initialized for bounding box computation: {}", structureLocation);
            return new BoundingBox(origin);
        }

        BlockPos size = new BlockPos(template.getSize());
        BlockPos max = origin.offset(size.getX(), size.getY(), size.getZ());
        boundingBox = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), max.getX(), max.getY(), max.getZ());
        return boundingBox;
    }

    public void ensurePopulated(ServerLevel level) {
        if (!isPopulated()) gatherAndPopulateStructureData(level);
    }

    public static class StructureBuildTask {
        public final BlockPos relativePos;
        public final BlockState state;
        @Nullable
        public final CompoundTag tag;

        public StructureBuildTask(BlockPos relPos, BlockState state, @Nullable CompoundTag tag) {
            this.relativePos = relPos;
            this.state = state;
            this.tag = tag;
        }
    }

    public static class StructureEntityTask {
        public final Vec3 pos;
        public final BlockPos blockAnchor;
        public final CompoundTag entityData;

        public StructureEntityTask(Vec3 pos, BlockPos blockAnchor, CompoundTag entityData) {
            this.pos = pos;
            this.blockAnchor = blockAnchor;
            this.entityData = entityData;
        }
    }
}


