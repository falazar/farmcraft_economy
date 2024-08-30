package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.FarmUpCraft;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Manages biome-specific rules for the mod.
 * <p>
 * This class is responsible for loading, saving, and managing biome rules within the game.
 * It uses {@link SavedData} to persist biome rules across world saves.
 */
public class BiomeRulesManager extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = FarmUpCraft.MODID + "_biome_rules_manager";

    private static BiomeRulesManager clientMangerCache = new BiomeRulesManager();
    private static ClientLevel levelCache = null;

    private static ServerLevel level;
    private static final Map<Holder<Biome>, BiomeRulesInstance> biomeRules = new ConcurrentHashMap<>();

    //public static SavedData.Factory<BiomeRulesManager> factory() {
    //    return new SavedData.Factory<>(WorldEventManager::new, WorldEventManager::new);
    //}

    /**
     * Retrieves the {@link BiomeRulesManager} instance for the given level.
     * <p>
     * For client-side, it returns a cached instance. For server-side, it retrieves or creates
     * the instance from the world data storage.
     *
     * @param level The level for which to retrieve the manager.
     * @return The {@link BiomeRulesManager} instance.
     */
    public static BiomeRulesManager get(Level level){
        if (!(level instanceof ServerLevel)) {
            if (levelCache != level) {
                levelCache = (ClientLevel) level;
                clientMangerCache = new BiomeRulesManager();
            }
            return clientMangerCache;
        }

        //make sure we are always in overworld storage!
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = ((ServerLevel)overworld).getDataStorage();
        return storage.computeIfAbsent(e ->  new BiomeRulesManager(e, overworld), BiomeRulesManager::new,  DATA_NAME);
    }

    /**
     * Creates a new {@link BiomeRulesManager} instance.
     */
    public BiomeRulesManager(){
    }

    /**
     * Creates a {@link BiomeRulesManager} instance from the given NBT data.
     *
     * @param nbt   The NBT data to load.
     * @param level The server level where the manager is used.
     */
    public BiomeRulesManager(CompoundTag nbt, ServerLevel level) {
        load(nbt, level);
    }

    /**
     * Saves the current biome rules to NBT.
     *
     * @param compoundTag The NBT tag to save the data to.
     * @return The updated NBT tag.
     */
    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        ListTag listTag = new ListTag();

        for (Map.Entry<Holder<Biome>, BiomeRulesInstance> entry : biomeRules.entrySet()) {
            Holder<Biome> biome = entry.getKey();
            BiomeRulesInstance rulesInstance = entry.getValue();
            if(!rulesInstance.shouldSave()) continue;
            CompoundTag biomeTag = new CompoundTag();

            // Save the biome key
            biomeTag.putString("biome", biome.unwrapKey().orElseThrow().location().toString());

            // Save the BiomeRulesInstance using Codec
            CompoundTag rulesTag = new CompoundTag();
            Codec<BiomeRulesInstance> codec = BiomeRulesInstance.CODEC;
            codec.encodeStart(NbtOps.INSTANCE, rulesInstance)
                    .get()
                    .ifLeft(tag -> rulesTag.put("rules_instance", tag))
                    .ifRight(partial -> LOGGER.error("Failed to save rules instance {} for biome {}", rulesInstance, biome.unwrapKey().orElseThrow()));

            biomeTag.put("rules", rulesTag);
            listTag.add(biomeTag);
        }

        compoundTag.put("biome_rules", listTag);
        return compoundTag;
    }

    /**
     * Loads biome rules from the given NBT data.
     *
     * @param nbt   The NBT data to load.
     * @param level The server level where the manager is used.
     */
    private void load(CompoundTag nbt, ServerLevel level) {
        ListTag listTag = nbt.getList("biome_rules", Tag.TAG_COMPOUND);

        for (Tag tag : listTag) {
            CompoundTag biomeTag = (CompoundTag) tag;
            String biomeKey = biomeTag.getString("biome");

            // Handle biome retrieval with optional and proper error logging

            ResourceKey<Biome> biomeRK = ResourceKey.create(Registries.BIOME, new ResourceLocation(biomeKey));

            // Retrieve biome holder
            Optional<Holder<Biome>> biomeHolderOptional = level.registryAccess().registry(Registries.BIOME)
                    .flatMap(biomes -> biomes.getHolder(biomeRK));

            if (biomeHolderOptional.isEmpty()) {
                LOGGER.error("Failed to load rules for biome '{}', biome not found.", biomeKey);
                continue; // Skip processing for this biome
            }

            Holder<Biome> biomeHolder = biomeHolderOptional.get();


            CompoundTag rulesTag = biomeTag.getCompound("rules");
            Codec<BiomeRulesInstance> codec = BiomeRulesInstance.CODEC;
            DataResult<BiomeRulesInstance> rulesInstance = codec.parse(NbtOps.INSTANCE, rulesTag);
            rulesInstance.resultOrPartial(err -> LOGGER.error("Failed to load rules for {} due to {}", biomeHolder, err))
                    .ifPresent(rulesInstancer    -> biomeRules.put(biomeHolder, rulesInstancer));
        }
    }

    /**
     * Sets the server level for this manager.
     *
     * @param level The server level to set.
     */
    public void setLevel(ServerLevel level) {
        this.level = level;
    }

    /**
     * Checks if there are any rules currently loaded.
     *
     * @return True if there are rules, false otherwise.
     */
    public boolean hasRules() {
        return !biomeRules.isEmpty();
    }

    /**
     * Clears all biome rules.
     */
    public void clearRules() {
        biomeRules.clear();
    }

    /**
     * Removes the rules for a specific biome.
     *
     * @param biome The biome to remove rules for.
     */
    public void removeBiomeRule(Holder<Biome> biome) {
        biomeRules.remove(biome);
    }

    /**
     * Checks if there are rules for a specific biome.
     *
     * @param biomeHolder The biome to check.
     * @return True if there are rules for the biome, false otherwise.
     */
    public boolean containsBiome(Holder<Biome> biomeHolder) {
        return biomeRules.containsKey(biomeHolder);
    }

    /**
     * Retrieves the {@link BiomeRulesInstance} for a specific biome.
     * If no rules exist, an empty instance with `shouldSave` set to false is created.
     *
     * @param biome The biome to get rules for.
     * @return The {@link BiomeRulesInstance} for the biome.
     */
    public BiomeRulesInstance getBiomeRules(Holder<Biome> biome) {
        return biomeRules.computeIfAbsent(biome, e -> {
            BiomeRulesInstance instance = BiomeRulesInstance.EMPTY;
            instance.setShouldSave(false);
            return instance;
        });
    }

    /**
     * Sets the {@link BiomeRulesInstance} for a specific biome.
     *
     * @param biome        The biome to set rules for.
     * @param rulesInstance The {@link BiomeRulesInstance} to set.
     */
    public void setBiomeRules(Holder<Biome> biome, BiomeRulesInstance rulesInstance) {
        biomeRules.put(biome, rulesInstance);
        setDirty(); // Mark the data as dirty to be saved
    }
}
