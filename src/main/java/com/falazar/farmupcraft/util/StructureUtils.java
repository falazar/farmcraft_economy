package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.datagen.ItemTagsGenerator;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.List;

public class StructureUtils {
    public static final CustomLogger LOGGER = new CustomLogger(StructureUtils.class.getSimpleName());


    public static boolean isInStructure(ServerLevel serverLevel, BlockPos pos, ResourceKey<Structure> structure) {
        return LocationPredicate.inStructure(structure).matches(serverLevel, pos.getX(), pos.getY(), pos.getZ());
    }

    public static AABB getAABBFromBoundingBox(BoundingBox boundingBox) {
        return new AABB(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ(), boundingBox.maxX(), boundingBox.maxY(), boundingBox.maxZ());
    }

    public static BlockPos getMinBlockPosFromAABB(AABB aabb) {
        return BlockPos.containing( aabb.minX, aabb.minY, aabb.minZ);
    }

    public static BlockPos getMaxBlockPosFromAABB(AABB aabb) {
        return BlockPos.containing( aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    public static boolean checkIfSafeForCheck(StructureStart structure) {
        return structure.getPieces().size() > 0;
    }

    public static boolean checkForEntityInsideStructure(ServerLevel serverLevel, AABB aabb, EntityType entity) {
        List<Entity> entityList = serverLevel.getEntitiesOfClass(Entity.class, aabb);
        for (var e : entityList) {
            if (e.getType() == entity) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkForEntityInsideStructure(ServerLevel serverLevel, AABB aabb, TagKey<EntityType<?>> entity) {
        List<Entity> entityList = serverLevel.getEntitiesOfClass(Entity.class, aabb);
        for (var e : entityList) {
            if (e.getType().is(entity)) {
                return true;
            }
        }
        return false;
    }

    public static String getStructureLanguageKey(ResourceKey<Structure> key) {
        ResourceLocation location = key.location();
        String path = location.getPath();
        String loc = location.getNamespace();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("structure." + loc + ".");
        stringBuilder.append(path);
        return stringBuilder.toString();
    }
}
