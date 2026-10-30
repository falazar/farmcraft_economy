package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataBase;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

public class ItemDataSerializer implements DataBase.DataSerializer<Item> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ITEM_KEY_TAG = "item_key";
    private static final String DATABASE_NAME_TAG = "database_name";

    @Override
    public CompoundTag serialize(Item value) {

        CompoundTag tag = new CompoundTag();
        ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(value);
        tag.putString(ITEM_KEY_TAG, itemLocation.toString());

        LOGGER.debug("Serialization complete for Item: {}", itemLocation);
        return tag;
    }

    @Override
    public Item deserialize(CompoundTag tag, Level level) {
        String itemKey = tag.getString(ITEM_KEY_TAG);
        String databaseName = tag.getString(DATABASE_NAME_TAG);

        //LOGGER.debug("Deserializing Item with key: {}", itemKey);

        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemKey));

        if (item == null) {
            LOGGER.error("[{}] Failed to deserialize Item: No item found with key {}", databaseName,itemKey);
            throw new IllegalArgumentException("["+databaseName+"]No item found with key: " + itemKey);
        }

        LOGGER.debug("[{}] Deserialization complete for Item: {}", databaseName,item.getDescriptionId());
        return item;
    }

}
