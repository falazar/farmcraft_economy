package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public class GoodsData {
    public static final CustomLogger LOGGER = new CustomLogger(GoodsData.class.getSimpleName());

    public static final Codec<GoodsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(GoodsData::getItem),
            Codec.STRING.fieldOf("itemId").forGetter(GoodsData::getItemId),
            Codec.INT.fieldOf("cost").forGetter(GoodsData::getCost),
            Codec.INT.fieldOf("amountSold").forGetter(GoodsData::getAmountSold),
            Codec.STRING.fieldOf("rarity").forGetter(GoodsData::getRarity),
            Codec.BOOL.fieldOf("active").forGetter(GoodsData::isActive),
            Codec.STRING.fieldOf("marketType").forGetter(GoodsData::getMarketType),
            Codec.STRING.fieldOf("dateAddedToMarket").forGetter(GoodsData::getDateAddedToMarket))
            .apply(instance, GoodsData::new));

    // TODO deprecate this one.
    // private final Item item; // Saves as a registry name, so it can be
    // serialized, but allows use to use as Item.
    // TODO add this one
    private final String itemId; // Saves as a registry key.
    private String marketType; // One of 4 or more types, "wood", "stone", "food", "general" etc.
    private boolean active; // Is the item currently active in a market.
    private String rarity; // common, uncommon, rare. TODO UNUSED FOR NOW.
    private String dateAddedToMarket = "";
    private int cost; // current coins to sell item for.
    private int amountSold; // How many items sold so far.

    public GoodsData(String itemId, int cost, int amountSold, String rarity, boolean active, String marketType,
            String dateAddedToMarket) {
        // this.item = item;
        this.itemId = itemId;
        this.cost = cost;
        this.amountSold = amountSold;
        this.rarity = rarity;
        this.active = active;
        this.marketType = marketType;
        this.dateAddedToMarket = dateAddedToMarket;
    }

    public String getItemId() {
        return itemId;
    }

    // Loads the actual item on demand using the registry name.
    public Item getItem() {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        if (cost < 0) {
            LOGGER.error("Cost cannot be negative. Setting to 0.");
            cost = 0; // Prevent negative cost.
        }
        this.cost = cost;
    }

    public int getAmountSold() {
        return amountSold;
    }

    public void setAmountSold(int amountSold) {
        this.amountSold = amountSold;
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // TODO how to do these with optional command sources????
    public static void setActiveStatusOfItem(CommandSourceStack source, String itemName, String active) {
        DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
        GoodsData goodsData = goodsDataDataBase.getData(itemName);
        if (goodsData == null) {
            source.sendFailure(Component.literal("GoodsData not found for item: " + itemName));
            LOGGER.error("GoodsData not found for item: " + itemName);
            return;
        }

        boolean newActiveValue;
        if (active.equalsIgnoreCase("true") || active.equals("1") || active.equalsIgnoreCase("yes")
                || active.equalsIgnoreCase("active")) {
            newActiveValue = true;
        } else if (active.equalsIgnoreCase("false") || active.equals("0") || active.equalsIgnoreCase("no")
                || active.equalsIgnoreCase("inactive")) {
            newActiveValue = false;
        } else {
            LOGGER.error("Invalid active status: " + active + ". Use true/false, active/inactive, yes/no, or 1/0.");
            source.sendFailure(Component.literal(
                    "Invalid active status: '" + active + "'. Use true/false, active/inactive, yes/no, or 1/0."));
            return;
        }
        goodsData.setActive(newActiveValue);

        if (goodsData.isActive()) {
            // Save date string as system date in YYYY-MM-DD format.
            goodsData.setDateAddedToMarket(java.time.LocalDate.now().toString());
        } else {
            goodsData.setDateAddedToMarket("");
        }
        goodsDataDataBase.putData(itemName, goodsData);
        LOGGER.info("Set active status of item: " + itemName + " to " + active);
        source.sendSuccess(() -> Component.literal("Set active status of item: " + itemName + " to " + active), true);

        // get the active setting and show her for logging debug.
        if (goodsData.isActive()) {
            LOGGER.info("Item " + itemName + " is now active in the market.");
        } else {
            LOGGER.info("Item " + itemName + " is now inactive in the market.");
        }
    }

    public static void setCostOfItem(CommandSourceStack source, String itemName, int cost) {
        DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
        GoodsData goodsData = goodsDataDataBase.getData(itemName);
        if (goodsData == null) {
            LOGGER.error("GoodsData not found for item: " + itemName);
            if (source != null) {
                source.sendFailure(Component.literal("GoodsData not found for item: " + itemName));
            }
            return;
        }
        goodsData.setCost(cost);
        goodsDataDataBase.putData(itemName, goodsData);
        LOGGER.info("Set cost of item: " + itemName + " to " + cost);
        // If source output to chat now
        if (source != null) {
            source.sendSuccess(() -> Component.literal("Set cost of item: " + itemName + " to " + cost), true);
        }
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public String getDateAddedToMarket() {
        return dateAddedToMarket;
    }

    public void setDateAddedToMarket(String dateAddedToMarket) {
        this.dateAddedToMarket = dateAddedToMarket;
    }
}
