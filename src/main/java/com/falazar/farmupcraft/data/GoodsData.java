package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.currency.CoinStack;
import com.falazar.farmupcraft.currency.CurrencyCost;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.spi.LoggerRegistry;

public class GoodsData {
    public static final CustomLogger LOGGER = new CustomLogger(GoodsData.class.getSimpleName());

    public static final Codec<GoodsData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(GoodsData::getItem),
                    Codec.INT.fieldOf("cost").forGetter(g -> Integer.valueOf(g.getCost())),
                    Codec.INT.optionalFieldOf("amountSold", Integer.valueOf(0)).forGetter(g -> Integer.valueOf(g.getAmountSold())),
                    Codec.STRING.optionalFieldOf("rarity", "common").forGetter(GoodsData::getRarity),
                    Codec.BOOL.optionalFieldOf("active", Boolean.FALSE).forGetter(g -> Boolean.valueOf(g.isActive())),
                    Codec.STRING.optionalFieldOf("marketType", "").forGetter(GoodsData::getMarketType),
                    Codec.STRING.optionalFieldOf("dateAddedToMarket", "").forGetter(GoodsData::getDateAddedToMarket)
            ).apply(instance, GoodsData::new)
    );

    private final Item item; // Saves as a registry name, so it can be serialized, but allows use to use as Item.
    private String marketType; // One of 4 or more types, "wood", "stone", "food", "general" etc.
    private boolean active;  // Is the item currently active in a market.
    private String rarity; // common, uncommon, rare. TODO UNUSED FOR NOW.
    private String dateAddedToMarket = "";
    private int cost; // current coins to sell item for.
    private int amountSold; // How many items sold so far.

    public GoodsData(Item item, int cost, int amountSold, String rarity, boolean active, String marketType, String dateAddedToMarket) {
        this.item = item;
        this.cost = cost;
        this.amountSold = amountSold;
        this.rarity = rarity;
        this.active = active;
        this.marketType = marketType;
        this.dateAddedToMarket = dateAddedToMarket;
    }

    public Item getItem() {
        return item;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
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

    public void setActive(Boolean active) {
        this.active = active;
    }

    // TODO how to do these with optional command sources????
    public static void setActiveStatusOfItem(CommandSourceStack source, String itemName, String active) {
//        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
//        if (item == null) {
////            source.sendFailure(() -> "Item not found: " + itemName);w
//            LOGGER.error("Item not found: " + itemName);
//            return;
//        }

        DataBase <String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
        GoodsData goodsData = goodsDataDataBase.getData(itemName);
        if (goodsData == null) {
//            source.sendFailure(() -> "GoodsData not found for item: " + itemName);
            LOGGER.error("GoodsData not found for item: " + itemName);
            return;
        }

        goodsData.setActive(Boolean.valueOf(Boolean.parseBoolean(active)));
        if (goodsData.isActive()) {
            // Save date string as system date in YYYY-MM-DD format.
            goodsData.setDateAddedToMarket( java.time.LocalDate.now().toString());
        } else {
            goodsData.setDateAddedToMarket("");
        }
        goodsDataDataBase.putData(itemName, goodsData);
        LOGGER.info("Set active status of item: " + itemName + " to " + active);
    }

    public static void setCostOfItem(CommandSourceStack source, String itemName, int cost) {
        DataBase <String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
        GoodsData goodsData = goodsDataDataBase.getData(itemName);
        if (goodsData == null) {
            LOGGER.error("GoodsData not found for item: " + itemName);
            return;
        }
        goodsData.setCost(cost);
        goodsDataDataBase.putData(itemName, goodsData);
        LOGGER.info("Set cost of item: " + itemName + " to " + cost);
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
