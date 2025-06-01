package com.falazar.farmupcraft.entity;

import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class FUCEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FarmUpCraft.MODID);
    public static final RegistryObject<EntityType<FlyingBlockChunkEntity>> FLYING_BLOCK_CHUNK = ENTITY_TYPES.register("flying_block_chunk",
            () -> EntityType.Builder.<FlyingBlockChunkEntity>of(FlyingBlockChunkEntity::new, MobCategory.MISC)
                    .sized(1F, 1F).clientTrackingRange(4)
                    .build(prefix("flying_block_chunk").toString()));
}
