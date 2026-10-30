package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

public class GoodsData {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<GoodsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("itemId").forGetter(GoodsData::getItemId),
            Codec.INT.fieldOf("cost").forGetter(GoodsData::getCost),
            Codec.INT.fieldOf("amountSold").forGetter(GoodsData::getAmountSold),
            Codec.STRING.fieldOf("rarity").forGetter(GoodsData::getRarity),
            Codec.BOOL.fieldOf("active").forGetter(GoodsData::isActive),
            Codec.STRING.fieldOf("marketType").forGetter(GoodsData::getMarketType),
            Codec.STRING.fieldOf("dateAddedToMarket").forGetter(GoodsData::getDateAddedToMarket))
            .apply(instance, GoodsData::new));

    private final String itemId;
    private String marketType;
    private boolean active;
    private String rarity;
    private String dateAddedToMarket;
    private int cost;
    private int amountSold;

    public GoodsData(String itemId, int cost, int amountSold, String rarity, boolean active, String marketType,
                     String dateAddedToMarket) {
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

    public Item getItem() {
        Identifier id = Identifier.parse(itemId);
        // The defaulted item registry would otherwise turn unknown IDs into air.
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        if (cost < 0) {
            LOGGER.error("Cost cannot be negative. Setting to 0.");
            cost = 0;
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

    public static void setActiveStatusOfItem(CommandSourceStack source, String itemName, String active) {
        setActiveStatusOfItem(source, itemName, active, ModEvents.getGoodsDataDatabase());
    }

    static void setActiveStatusOfItem(CommandSourceStack source, String itemName, String active,
                                     DataBase<String, GoodsData> database) {
        GoodsData goods = database.getData(itemName);
        if (goods == null) {
            source.sendFailure(Component.literal("GoodsData not found for item: " + itemName));
            LOGGER.error("GoodsData not found for item: {}", itemName);
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
            LOGGER.error("Invalid active status: {}. Use true/false, active/inactive, yes/no, or 1/0.", active);
            source.sendFailure(Component.literal(
                    "Invalid active status: '" + active + "'. Use true/false, active/inactive, yes/no, or 1/0."));
            return;
        }
        goods.setActive(newActiveValue);
        goods.setDateAddedToMarket(newActiveValue ? java.time.LocalDate.now().toString() : "");
        database.putData(itemName, goods);
        LOGGER.info("Set active status of item: {} to {}", itemName, active);
        source.sendSuccess(() -> Component.literal("Set active status of item: " + itemName + " to " + active), true);
        LOGGER.info("Item {} is now {} in the market.", itemName, newActiveValue ? "active" : "inactive");
    }

    public static void setCostOfItem(CommandSourceStack source, String itemName, int cost) {
        setCostOfItem(source, itemName, cost, ModEvents.getGoodsDataDatabase());
    }

    static void setCostOfItem(CommandSourceStack source, String itemName, int cost, DataBase<String, GoodsData> database) {
        GoodsData goods = database.getData(itemName);
        if (goods == null) {
            LOGGER.error("GoodsData not found for item: {}", itemName);
            if (source != null) source.sendFailure(Component.literal("GoodsData not found for item: " + itemName));
            return;
        }
        goods.setCost(cost);
        database.putData(itemName, goods);
        LOGGER.info("Set cost of item: {} to {}", itemName, cost);
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
