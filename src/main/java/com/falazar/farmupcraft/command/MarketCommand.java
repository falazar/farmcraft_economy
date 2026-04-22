package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import java.util.stream.Collectors;
import java.text.NumberFormat;

public class MarketCommand {
    public static final CustomLogger LOGGER = new CustomLogger(MarketCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "market"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("market");

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(MarketCommand::showMarketInfo);
        builder.then(infoBuilder);

        // Define the "show" sub-command
        // with four options: misc, stone, food, wood
        LiteralArgumentBuilder<CommandSourceStack> showBuilder = Commands.literal("show")
                .then(Commands.literal("general").executes(context -> {
                    return showMarketList(context.getSource(), "general");
                }))
                .then(Commands.literal("food").executes(context -> {
                    return showMarketList(context.getSource(), "food");
                }))
                .then(Commands.literal("wood").executes(context -> {
                    return showMarketList(context.getSource(), "wood");
                }))
                .then(Commands.literal("stone").executes(context -> {
                    return showMarketList(context.getSource(), "stone");
                }))
                .then(Commands.literal("high").executes(context -> {
                    return showHighValueItems(context.getSource(), 10);
                }))
                .then(Commands.literal("high").then(Commands.argument("minCoins", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int minCoins = IntegerArgumentType.getInteger(context, "minCoins");
                            return showHighValueItems(context.getSource(), minCoins);
                        })));
        builder.then(showBuilder);

        // Define a "sell" sub-command, to sell all items of that type
        LiteralArgumentBuilder<CommandSourceStack> sellBuilder = Commands.literal("sell")
                .then(Commands.literal("general").executes(context -> {
                    sellMarketItems(context.getSource(), "general");
                    return 0;
                }))
                .then(Commands.literal("food").executes(context -> {
                    sellMarketItems(context.getSource(), "food");
                    return 0;
                }))
                .then(Commands.literal("wood").executes(context -> {
                    sellMarketItems(context.getSource(), "wood");
                    return 0;
                }))
                .then(Commands.literal("stone").executes(context -> {
                    sellMarketItems(context.getSource(), "stone");
                    return 0;
                }));
        builder.then(sellBuilder);

        // Admin-only sub-commands grouped under "/market admin ..."
        // Permission level 2 required for the entire "admin" branch.
        LiteralArgumentBuilder<CommandSourceStack> adminBuilder = Commands.literal("admin")
                .requires(source -> source.hasPermission(2))
                // rundaily - rotate market items and raise prices
                .then(Commands.literal("rundaily")
                        .executes(context -> {
                            runDailyTask(context.getSource());
                            return 0;
                        }))
                // raiseprices <type>
                .then(Commands.literal("raiseprices")
                        .then(Commands.argument("type", StringArgumentType.string())
                                .executes(context -> {
                                    String type = StringArgumentType.getString(context, "type");
                                    raiseMarketPrices(context.getSource(), type);
                                    return 0;
                                })))
                // importtxt <type> - import items from txt file
                .then(Commands.literal("importtxt")
                        .then(Commands.argument("type", StringArgumentType.string())
                                .executes(context -> {
                                    String type = StringArgumentType.getString(context, "type");
                                    importMarketItemsTXT(context.getSource(), type);
                                    return 0;
                                })))
                // setactive <itemId> <active|inactive>
                .then(Commands.literal("setactive")
                        .then(Commands.argument("itemId", StringArgumentType.string())
                                .then(Commands.argument("status", StringArgumentType.string())
                                        .executes(context -> {
                                            String itemId = StringArgumentType.getString(context, "itemId");
                                            String status = StringArgumentType.getString(context, "status");
                                            GoodsData.setActiveStatusOfItem(context.getSource(), itemId, status);
                                            return 0;
                                        }))))
                // setcost <itemId> <cost>
                .then(Commands.literal("setcost")
                        .then(Commands.argument("itemId", StringArgumentType.string())
                                .then(Commands.argument("cost", StringArgumentType.string())
                                        .executes(context -> {
                                            String itemId = StringArgumentType.getString(context, "itemId");
                                            int cost = Integer.parseInt(StringArgumentType.getString(context, "cost"));
                                            GoodsData.setCostOfItem(context.getSource(), itemId, cost);
                                            return 0;
                                        }))))
                // clearall <forreal> - clears all market items
                .then(Commands.literal("clearall")
                        .then(Commands.argument("confirm", StringArgumentType.string())
                                .executes(context -> {
                                    String confirm = StringArgumentType.getString(context, "confirm");
                                    if ("forreal".equals(confirm)) {
                                        clearAllMarketItems(context.getSource());
                                    } else {
                                        context.getSource().sendFailure(Component.literal(
                                                "WARNING: This will clear ALL market items! If you are sure, type: /market admin clearall forreal"));
                                    }
                                    return 0;
                                }))
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal(
                                    "WARNING: This will clear ALL market items! If you are sure, type: /market admin clearall forreal"));
                            return 0;
                        }))
                // addrandom <type>
                .then(Commands.literal("addrandom")
                        .then(Commands.argument("type", StringArgumentType.string())
                                .executes(context -> {
                                    String type = StringArgumentType.getString(context, "type");
                                    addRandomItemToMarket(context.getSource(), type);
                                    return 0;
                                })))
                // find <keyword>
                .then(Commands.literal("find")
                        .then(Commands.argument("keyword", StringArgumentType.string())
                                .executes(context -> {
                                    String keyword = StringArgumentType.getString(context, "keyword");
                                    return findMarketItems(context.getSource(), keyword);
                                })))
                // findfoods - lists pam's food items
                .then(Commands.literal("findfoods")
                        .executes(context -> findFoodMarketItems(context.getSource())))
                // findwood - lists wood-type items
                .then(Commands.literal("findwood")
                        .executes(context -> findWoodMarketItems(context.getSource())));
        builder.then(adminBuilder);

        // TODO MAKE AN ADD, and COMMAND REMOVE ITEM COMMAND

        // Register the main "market" command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showMarketInfo(CommandContext<CommandSourceStack> context) {
        try {
            MutableComponent response = Component.literal("Market info options: \n");
            response = response.append(Component.literal("  /market show general \n"));
            response = response.append(Component.literal("  /market show food \n"));
            response = response.append(Component.literal("  /market show wood \n"));
            response = response.append(Component.literal("  /market show stone \n"));
            response = response.append(Component.literal("  /market show high [minCoins] \n"));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Show market info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int showMarketList(CommandSourceStack source, String type) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // STEP 1: Get filtered sorted list of GoodsData by type.
            Collection<GoodsData> goodsDataList = getFilteredActiveGoods(type);
            if (goodsDataList == null || goodsDataList.isEmpty()) {
                source.sendFailure(Component.literal("No items found for market type: " + type));
                return 0;
            }
            MutableComponent response = Component
                    .literal("Market " + type + " items (" + goodsDataList.size() + "): \n")
                    .withStyle(ChatFormatting.YELLOW);

            // Missing an item in here.

            // STEP 2: Show the final list, highlight if in our inventory.
            NumberFormat numberFormat = NumberFormat.getInstance();
            for (GoodsData good : goodsDataList) {
                LOGGER.info("DEBUG: Showing market item: " + good.getItemId() + ", cost = " + good.getCost()
                        + ", amountSold = " + good.getAmountSold());

                // Highlight ones in your inventory now.
                Item item = good.getItem();
                if (item != null) {
                    boolean inInventory = playerSource.getInventory().contains(item.getDefaultInstance());
                    String itemName = item.getDescription().getString();
                    if (inInventory) {
                        int count = playerSource.getInventory().countItem(item);
                        response = response
                                .append(Component.literal(" -" + itemName + ": " + numberFormat.format(good.getCost())
                                        + " coins (" + count + " cnt)\n").withStyle(ChatFormatting.GREEN));
                    } else {
                        response = response.append(Component
                                .literal(" -" + itemName + ": " + numberFormat.format(good.getCost()) + " coins\n")
                                .withStyle(ChatFormatting.WHITE));
                    }
                } else {
                    response = response.append(
                            Component.literal(" -Unknown Item: " + numberFormat.format(good.getCost()) + " coins\n")
                                    .withStyle(ChatFormatting.WHITE));
                }
            }
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Show Market List Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Grab all items from the DB, filter by type.
    private static @Nullable Collection<GoodsData> getFilteredGoods(String type) {
        // STEP 1: Grab all items from the DB.
        DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
        Collection<GoodsData> goodsDataList = goodsDataDataBase.getValues();
        if (goodsDataList.isEmpty()) {
            return null;
        }

        // STEP 2: Filter on type.
        List<GoodsData> goodsData = new ArrayList<>();
        for (GoodsData goods : goodsDataList) {
            if (goods.getMarketType().equals(type)) {
                goodsData.add(goods);
            }
        }

        return goodsData;
    }

    // Grab all items from the DB, filter by type and active status.
    private static @Nullable Collection<GoodsData> getFilteredActiveGoods(String type) {
        // STEP 1: Grab all items from the DB.
        DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
        Collection<GoodsData> goodsDataList = goodsDataDataBase.getValues();
        if (goodsDataList.isEmpty()) {
            LOGGER.info("DEBUG: No goods data found in database for type: " + type);
            return null;
        }

        // STEP 2: Filter on type and active status.
        List<GoodsData> goodsData = new ArrayList<>();
        for (GoodsData goods : goodsDataList) {
            if (goods.getMarketType().equals(type) && goods.isActive()) {
                goodsData.add(goods);
            }
        }

        // STEP 3: Sort by dateAddedToMarket newest to oldest.
        // goodsData.sort(Comparator.comparing(GoodsData::getDateAddedToMarket).reversed());
        // STEP 3: Sort by dateAddedToMarket newest to oldest, then by cost ascending.
        goodsData.sort(
                Comparator.comparing(GoodsData::getDateAddedToMarket).reversed()
                        .thenComparingInt(GoodsData::getCost));
        return goodsData;
    }

    // Given a market type sell all items sellable from inventory.
    public static void sellMarketItems(CommandSourceStack source, String type) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Map<String, Integer> items = getMarketBuyItems(type);
            Collection<GoodsData> goods = getFilteredActiveGoods(type);

            int totalCoins = 0;
            // Loop over all items and sell all we have.
            MutableComponent response = Component.literal("Selling Market items: \n").withStyle(ChatFormatting.YELLOW); // TODO
                                                                                                                        // TEST
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
            for (GoodsData good : goods) {
                Item item = good.getItem();
                boolean inInventory = playerSource.getInventory().contains(item.getDefaultInstance());
                if (!inInventory) {
                    continue;
                }

                if (item != null) {
                    int coins = sellAllItemInInventory(source, playerSource, item);
                    totalCoins += coins;
                } else {
                    MutableComponent response2 = Component.literal(" -Unknown Item: " + good.getCost() + " coins\n")
                            .withStyle(ChatFormatting.WHITE);
                    MutableComponent finalResponse2 = response2;
                    source.sendSuccess(() -> finalResponse2, false);
                }
            }

            // TODO TEST playerData and coins. - failing on save
            PlayerCommand.givePlayerCoins(source, totalCoins);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Sell Market List Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    // Sell all items of a given type in the player's inventory.
    // Lower the price by 1 for each set of 32 sold.
    // Randomly increase the price of another item by 1 for each set of 64 sold.
    public static int sellAllItemInInventory(CommandSourceStack source, Player playerSource, Item item) {
        // Find all items matching in inventory and sell them.

        // STEP 1: Look over player inventory now and count matching items.
        int count = playerSource.getInventory().countItem(item);
        LOGGER.info("DEBUG: Selling " + count + " of " + item.getDescriptionId());

        // STEP 2: Remove all items.
        ItemStack itemStack = item.getDefaultInstance();
        // LOGGER.info("DEBUG: Removing " + count + " of " + item.getDescriptionId() + "
        // from inventory.");
        removeItem(playerSource.getInventory(), itemStack, count);

        // STEP 3: Calculate coins earned and decrease price.
        String itemKey = ForgeRegistries.ITEMS.getKey(item).toString();
        GoodsData good = ModEvents.getGoodsDataDatabase().getData(itemKey);
        LOGGER.info("DEBUG: Found GoodsData for item: " + itemKey + ", cost = " + good.getCost() + ", amountSold = "
                + good.getAmountSold());
        int coinsTotal = getCoinsTotalForItem(good, count);

        // STEP 4: Randomly update other items.
        increaseOtherGoods(item, good, count);

        // Update the amount sold for this item.
        good.setAmountSold(good.getAmountSold() + count);
        ModEvents.getGoodsDataDatabase().putData(itemKey, good);

        // STEP 5: Send final chat to player.
        String itemName = item.getDescription().getString();
        NumberFormat numberFormat = NumberFormat.getInstance();
        MutableComponent response = Component
                .literal(
                        " - Sold " + count + " of " + itemName + " for " + numberFormat.format(coinsTotal) + " coins\n")
                .withStyle(ChatFormatting.GREEN);
        MutableComponent finalResponse = response;
        source.sendSuccess(() -> finalResponse, false);

        // NOTE: Will sell 0 coin values and just take for free, but that is ok for now.

        return coinsTotal;
    }

    // Increase the cost of another item randomly by 1 for each set of 64 sold.
    private static void increaseOtherGoods(Item item, GoodsData good, int count) {
        // If greater than 64 increments crossed, raise 1 other cost randomly by 1.
        int startCount64 = good.getAmountSold() % 64; // Get the current sold count modulo 64.
        int newCount64 = startCount64 + count; // Add the new count to it.
        int sets64 = newCount64 / 64; // Calculate how many sets of 64 we have.
        // TODO test
        LOGGER.info("DEBUG: sets64 = " + sets64);

        // TODO test can increase cost of SAME item, probably dont want that.

        for (int i = 0; i < sets64; i++) {
            // Get a random item from the active filtered list.
            Collection<GoodsData> allGoods = getFilteredActiveGoods(good.getMarketType());
            Random random = new Random();
            GoodsData randomGood = allGoods.stream()
                    .skip(random.nextInt(allGoods.size())) // Skip a random number of items
                    .findFirst() // Get the first item after skipping
                    .orElse(null); // If no item found, return null
            // Dont raise same good cost, find another good, without increasing counter.
            if (randomGood == null || randomGood.getItemId().equals(good.getItemId())) {
                // If we got the same good or null, skip this iteration.
                LOGGER.info("DEBUG: Skipping cost increase for " + good.getItem().getDescriptionId()
                        + " as it is the same or null.");
                i--; // Decrement i to retry this iteration.
                continue;
            }

            randomGood.setCost(randomGood.getCost() + 1); // Increase the cost by 1.
            ModEvents.getGoodsDataDatabase().putData(randomGood.getItemId(), randomGood);
            LOGGER.info("DEBUG: Raising cost of " + randomGood.getItem().getDescriptionId() + " to "
                    + randomGood.getCost());
        }
    }

    // Get coins earned for selling X count of an item.
    // Side effect: Lowers the price of the item by 1 for each set of 32 sold.
    private static int getCoinsTotalForItem(GoodsData good, int count) {
        int coinsTotal = 0;
        int cost = good.getCost();
        int sold = good.getAmountSold() % 32;

        LOGGER.info("DEBUG: Starting getCoinsTotalForItem for " + good.getItemId() + " with count=" + count + ", cost="
                + cost + ", sold=" + sold);

        while (count > 0) {
            int toNextDrop = 32 - sold;
            int sellNow = Math.min(count, toNextDrop);

            coinsTotal += cost * sellNow;
            LOGGER.info("DEBUG: Sold " + sellNow + " items at cost " + cost + ", coinsTotal now " + coinsTotal);

            sold += sellNow;
            count -= sellNow;

            if (sold == 32) {
                cost = Math.max(0, cost - 1);
                LOGGER.info("DEBUG: Lowered cost to " + cost);
                sold = 0;
            }
        }

        good.setCost(cost);
        ModEvents.getGoodsDataDatabase().putData(good.getItemId(), good);
        LOGGER.info("DEBUG: Finished getCoinsTotalForItem for " + good.getItemId() + ", final coinsTotal=" + coinsTotal
                + ", final cost=" + cost);

        return coinsTotal;
    }

    // TODO remove out to proper home. player maybe.
    // Remove all items from inventory that match item.
    public static void removeItem(Inventory inventory, ItemStack pStack, int count) {
        // Regular inventory
        for (ItemStack itemStack : inventory.items) { // 36 items here.
            if (itemStack.isEmpty()) {
                continue;
            }
            // Check if the itemStack matches the pStack
            if (itemStack.getItem() == pStack.getItem()) {
                // LOGGER.info("FOUND, removing now!");
                itemStack.setCount(0); // Set to 0 to remove it
                continue;
            }
        }

        // Check offhand also.
        ItemStack itemStack = inventory.offhand.get(0);
        if (itemStack.isEmpty()) {
            return;
        }
        // Check if the itemStack matches the pStack
        if (itemStack.getItem() == pStack.getItem()) {
            // Remove the item from the inventory
            itemStack.setCount(0); // Set to 0 to remove it
        }
    }

    // // TODO: notice put all new ones at TOP of the list - not sure how we do
    // that?
    // // We will hard code a list here now to play with.
    // // Fields needed: itemId, cost, amountSold
    // // TODO start using GoodsData objects instead.
    // // Scan db of items, find all of this type, and active.
    // // TODO delete unused method.
    // public static Map<String, Integer> getMarketBuyItems(String type) {
    // if (type.equals("food")) {
    // String foodString = """
    // pamhc2foodextended:gooseberryjellysandwichitem\t5
    // pamhc2foodcore:caramelappleitem\t5
    // pamhc2foodextended:cashewbutteritem\t5
    // pamhc2foodextended:heartybreakfastitem\t6
    // pamhc2foodextended:peanutchocolatebaritem\t6
    // pamhc2foodextended:breadedporkchopitem\t7
    // pamhc2foodextended:cactusfruitpieitem\t8
    // pamhc2foodextended:bbqsauceitem\t9
    // pamhc2foodextended:pineapplesmoothieitem\t9
    // pamhc2foodcore:epicbaconitem\t9
    // pamhc2foodextended:raspberryjellysandwichitem\t9
    // pamhc2foodextended:strawberrypieitem\t8
    // pamhc2foodextended:imitationcrabsticksitem\t12
    // pamhc2foodextended:soursopjellytoastitem\t13
    // pamhc2foodextended:gardensoupitem\t9
    // """;
    // return parseItemsFromString(foodString);
    // } else if (type.equals("wood")) {
    // // Use a text block string here:
    // String woodString = """
    // minecraft:birch_door\t5
    // valhelsia_structures:stripped_mangrove_post\t9
    // biomesoplenty:mahogany_fence_gate\t13
    // biomesoplenty:stripped_palm_wood\t15
    // cfm:mangrove_kitchen_drawer 23
    // """;
    // // Parse that into our items now.
    // return parseItemsFromString(woodString);
    // } else if (type.equals("stone")) {
    // String stoneString = """
    // biomesoplenty:orange_sandstone\t6
    // minecraft:red_sandstone_wall\t7
    // philipsruins:red_sand_stone_brick\t9
    // minecraft:stone_brick_stairs\t9
    // valhelsia_structures:cyan_metal_framed_glass\t8
    // """;
    // return parseItemsFromString(stoneString);
    // } else if (type.equals("general")) {
    // String generalString = """
    // minecraft:prismarine_brick_stairs\t5
    // minecraft:lime_wool\t5
    // valhelsia_structures:purple_sleeping_bag\t6
    // minecraft:yellow_banner\t6
    // minecraft:cyan_wool\t7
    // minecraft:sculk\t8
    // valhelsia_structures:white_sleeping_bag\t9
    // cfm:cyan_grill\t9
    // minecraft:lily_of_the_valley\t9
    // cfm:cyan_cooler\t15
    // """;
    // return parseItemsFromString(generalString);
    // } else {
    // LOGGER.info("DEBUG unknown market type: " + type);
    // return new HashMap<>(); // Return an empty map if the type is unknown
    // }
    // two item snot showing, fireandice:crackeld stone somethign and prismarine
    // steps.
    /*
     * 
     * 09:02:11.284
     * game
     * Item iceandfire:crackled_stone is now active in the market.
     * 
     */

    // }

    // General searchability method to find items, test one to play around with.
    public static int findMarketItems(CommandSourceStack source, String keyword) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            // Ensure the keyword is not null or empty
            if (keyword == null || keyword.isEmpty()) {
                source.sendFailure(Component.literal("Keyword cannot be empty."));
                return 0;
            }

            // Call into minecraft items now to search by partial keyword for things.
            // Test first with like "jungle" to look for wood items.
            // Use this to export a list of items for our market json and spreadsheet.
            // is there any categories or other ways to filter this?
            // for foods we need edible recipes and check which MOD id they are from.
            // mostly foods and extended food items. and edible? no plain crops.
            // maybe save some full queries here so we can reuse them later.
            // IE all woods but no buttons or some such.

            // todo change keyword to a keyword list, then it can grab oak, jungle etc. all
            // in one go.
            // TODO check for full word boundary for strings like oak, or it will overmach.

            // Search for items containing the keyword
            List<String> matchingItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase()
                            .contains(keyword.toLowerCase()))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude
                                                                                                                    // slabs
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("potted_")) // Exclude
                                                                                                                      // potted
                                                                                                                      // cant
                                                                                                                      // get
                                                                                                                      // items
                    .filter(item -> item.getMaxStackSize() > 1) // Exclude unstackable items
                    .map(item -> ForgeRegistries.ITEMS.getKey(item).toString())
                    .toList();
            // If no items are found, notify the player
            if (matchingItems.isEmpty()) {
                source.sendFailure(Component.literal("No items found matching keyword: " + keyword));
                return 0;
            }

            // Build a response message with the matching items
            // Get a count of all the items found.
            int itemCount = matchingItems.size();
            MutableComponent response = Component.literal("Items matching \"" + keyword + "\":\n");
            for (String itemName : matchingItems) {
                response = response.append(Component.literal("- " + itemName + "\n"));
            }
            // Add the total count of items found
            response = response.append(Component.literal("Total items found: " + itemCount + "\n"));

            // Send the response to the player
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Find Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Special searchability method to find items by MOD.
    public static int findFoodMarketItems(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Call into minecraft items now to search by partial keyword for things.
            // Test first with like "jungle" to look for wood items.
            // Use this to export a list of items for our market json and spreadsheet.
            // is there any categories or other ways to filter this?
            // for foods we need edible recipes and check which MOD id they are from.
            // mostly foods and extended food items. and edible? no plain crops.
            // maybe save some full queries here so we can reuse them later.
            // IE all woods but no buttons or some such.

            // todo change keyword to a keyword list, then it can grab oak, jungle etc. all
            // in one go.
            // TODO check for full word boundary for strings like oak, or it will overmatch.

            // NOTICE keyword like pam and such will match also, might be easy enough!!!
            String keyword = "pams"; // TODO TESTING.
            // pamhc2foodcore or pamhc2foodextended
            // Search for items containing the keyword

            // Search for items belonging to either "pamhc2foodcore" or "pamhc2foodextended"
            List<String> matchingItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> {
                        String namespace = ForgeRegistries.ITEMS.getKey(item).getNamespace();
                        return namespace.equalsIgnoreCase("pamhc2foodcore")
                                || namespace.equalsIgnoreCase("pamhc2foodextended");
                    })
                    .filter(item -> item.getMaxStackSize() > 1) // Exclude unstackable items
                    .map(item -> ForgeRegistries.ITEMS.getKey(item).toString())
                    .toList(); // If no items are found, notify the player
            if (matchingItems.isEmpty()) {
                source.sendFailure(Component.literal("No items found matching. "));
                return 0;
            }

            // Build a response message with the matching items
            // Get a count of all the items found.
            int itemCount = matchingItems.size();
            MutableComponent response = Component.literal("Items matching:\n");
            for (String itemName : matchingItems) {
                response = response.append(Component.literal("- " + itemName + "\n"));
            }
            // Add the total count of items found
            response = response.append(Component.literal("Total items found: " + itemCount + "\n"));

            // Send the response to the player
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Find Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int findWoodMarketItems(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Call into minecraft items now to search by partial keyword for things.
            // Test first with like "jungle" to look for wood items.
            // Use this to export a list of items for our market json and spreadsheet.

            // Search for items containing the keyword
            String logNames = "dead_,fir_,hellbark_,jacaranda_,magic_,mahogany_,palm_,redwood_,umbran_,willow_,acacia_,birch_,cherry_,dark_oak_,jungle_,mangrove_,oak_,spruce_";
            List<String> keywords = Arrays.asList(logNames.split(","));

            List<String> matchingItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> keywords.stream()
                            .anyMatch(keyword -> ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase()
                                    .contains(keyword.toLowerCase())))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude
                                                                                                                    // slabs
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("potted_")) // Exclude
                                                                                                                      // potted
                                                                                                                      // cant
                                                                                                                      // get
                                                                                                                      // items
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_button")) // cut
                                                                                                                      // down
                                                                                                                      // number
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_sign"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_boat"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_plate"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_counter"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_sink"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("iceandfire"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase()
                            .contains("_bridge_stair")) // just to cut down # of these
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase()
                            .contains("_lapidified")) // hard item?
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase()
                            .contains("villagersplus")) // bogus matches.
                    .filter(item -> item.getMaxStackSize() > 1) // Exclude unstackable items
                    // probably remove a few more types...
                    .map(item -> ForgeRegistries.ITEMS.getKey(item).toString())
                    .toList();

            // If no items are found, notify the player
            if (matchingItems.isEmpty()) {
                source.sendFailure(Component.literal("No items found matching. "));
                return 0;
            }

            // Build a response message with the matching items
            // Get a count of all the items found.
            int itemCount = matchingItems.size();
            MutableComponent response = Component.literal("Items matching:\n");
            for (String itemName : matchingItems) {
                response = response.append(Component.literal("- " + itemName + "\n"));
            }
            // Add the total count of items found
            response = response.append(Component.literal("Total items found: " + itemCount + "\n"));

            // Send the response to the player
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Find Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // TODO Move to new home.
    // Run daily task for all markets, removing old items, and adding new items.
    public static int runDailyTask(CommandSourceStack source) {
        try {
            // Run the daily task for each market.
            getNewMarketItems(source, "food");
            getNewMarketItems(source, "wood");
            getNewMarketItems(source, "stone");
            getNewMarketItems(source, "general");

            // Raise market prices now.
            raiseMarketPrices(source, "food");
            raiseMarketPrices(source, "wood");
            raiseMarketPrices(source, "stone");
            raiseMarketPrices(source, "general");

            // Broadcast to all online players that the market has refreshed.
            Component broadcastMsg = Component.literal("[Market] The market has been refreshed for today!")
                    .withStyle(ChatFormatting.GOLD);
            source.getServer().getPlayerList().broadcastSystemMessage(broadcastMsg, false);

            // Also confirm to the command source (e.g. admin or scheduler log).
            source.sendSuccess(() -> Component.literal("Daily task completed for all markets."), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Run Daily Task Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // TODO failing in a couple categories hitting an AIR item... report hit and
    // pick a new item instead
    // then i can fix those individual items.
    // Second half of rundaily task.
    // This will remove old items, and add new items.
    public static int getNewMarketItems(CommandSourceStack source, String type) {
        try {
            // STEP 1: Remove a few old items.
            // Get market list
            Collection<GoodsData> activeGoods = getFilteredActiveGoods(type);
            // Shuffle and remove 1 item per 5 existing.
            ArrayList<GoodsData> shuffledItems = new ArrayList<>(activeGoods);
            Collections.shuffle(shuffledItems);

            // Print out the ones we are removing.
            MutableComponent response = Component
                    .literal("Removing " + type + " Market items(" + shuffledItems.size() + "): \n")
                    .withStyle(ChatFormatting.YELLOW);
            int count = shuffledItems.size() / 5; // Remove 1 item per 5 existing items.
            for (int i = 0; i < count; i++) { // mod 5
                // String itemName = shuffledItems.get(i);
                GoodsData good = shuffledItems.get(i);
                String itemName = good.getItem().getDescription().getString();
                response = response.append(Component.literal("- " + itemName + "\n"));
                good.setActive(Boolean.valueOf(false)); // Set active status to false

                ModEvents.getGoodsDataDatabase().putData(good.getItemId(), good);
            }

            // STEP 3: Add in new items.
            Collection<GoodsData> newItems = getFilteredGoods(type);
            // Shuffle the new items and take the first count.
            ArrayList<GoodsData> newItemsList = new ArrayList<>(newItems);
            Collections.shuffle(newItemsList);
            for (int i = 0; i < count; i++) {
                GoodsData good = newItemsList.get(i);
                // TODO MAKE METHOD on goodsData
                String itemName = good.getItem().getDescription().getString();

                // If air item, pick another one, and send error notice to me.
                if (good.getItem() == null) {
                    LOGGER.error("DEBUG: Air item found, picking another one.");
                    // Pick another one, and send error notice to me.
                    // TODO test
                    i--;
                    continue;
                }

                // Add the new item to the response.
                response = response.append(Component.literal("+ " + itemName + "\n"));
                // Add to market, set active and cost.
                good.setActive(Boolean.valueOf(true)); // Set active status
                // Set at 4, cause we add one to all afterwards.
                good.setCost(4); // Set a default cost, can be changed later.
                good.setDateAddedToMarket(java.time.LocalDate.now().toString());
                ModEvents.getGoodsDataDatabase().putData(good.getItemId(), good);
            }

            // Broadcast item changes to all online players.
            MutableComponent finalResponse = response;
            source.getServer().getPlayerList().broadcastSystemMessage(finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Get New Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Increment all market prices by 1 for a given type.
    public static int raiseMarketPrices(CommandSourceStack source, String type) {
        try {
            // STEP 1: Get market list
            Collection<GoodsData> activeGoods = getFilteredActiveGoods(type);

            // TODO TEST
            // STEP 2: Increase cost of each item by 1.
            for (GoodsData good : activeGoods) {
                good.setCost(good.getCost() + 1);
                ModEvents.getGoodsDataDatabase().putData(good.getItemId(), good);
                LOGGER.info("DEBUG: Raising cost of " + good.getItem().getDescriptionId() + " to " + good.getCost());
            }

        } catch (Exception ex) {
            source.sendFailure(Component.literal("Get New Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static void importMarketItemsTXT(CommandSourceStack source, String type) {
        try {
            InputStream inputStream = MarketCommand.class.getClassLoader()
                    .getResourceAsStream("data/farmupcraft/farmupcraft/market/market_" + type + "_items.txt");
            if (inputStream == null) {
                throw new FileNotFoundException(
                        "Resource not found: data/farmupcraft/farmupcraft/market/market_" + type + "_items.txt");
            }
            String fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            // Parse the file content into a list of item names, unordered array of strings.
            String[] itemsArray = fileContent.split("\n");

            // NOTE: for now an item can be in only ONE market type. may update later to
            // allow multiple types.
            DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();

            // DEBUG output results to logger
            LOGGER.info("DEBUG Importing Market Items for type: " + type);
            int importedCount = 0;
            for (String itemStr : itemsArray) {
                // Trim whitespace and check if the line is not empty
                String trimmedItem = itemStr.trim();
                if (!trimmedItem.isEmpty()) {
                    LOGGER.info("DEBUG Item: " + trimmedItem);
                    // Step 2: Create an object to hold as a GoodsData object.
                    GoodsData goodsData = goodsDataDataBase.getData(trimmedItem);
                    if (goodsData == null) {
                        // STEP 3: Insert into DB, ignore if an old one exists.
                        // Dont create dupes, just insert new ones.
                        goodsData = new GoodsData(trimmedItem, 5, 0, "common", false, type, "");
                        goodsDataDataBase.putData(trimmedItem, goodsData);
                        importedCount++;
                    } else {
                        // ignore for now.
                    }
                }
            }

            int finalImportedCount = importedCount;
            source.sendSuccess(
                    () -> Component.literal("Imported " + finalImportedCount + " items for market type: " + type),
                    false);

            // NOTE: This wont remove any older ones, manually do that.

        } catch (Exception ex) {
            source.sendFailure(Component.literal("Import Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    public static void clearAllMarketItems(CommandSourceStack source) {
        try {
            // Clear all market items from the database.
            DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
            goodsDataDataBase.clearDataBase(true);
            source.sendSuccess(() -> Component.literal("All market items cleared."), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Clear All Market Items Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    public static void addRandomItemToMarket(CommandSourceStack source, String type) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return;
            }

            // Get a random item from the filtered goods.
            Collection<GoodsData> goods = getFilteredGoods(type);
            if (goods.isEmpty()) {
                source.sendFailure(Component.literal("No goods found for type: " + type));
                return;
            }

            Random random = new Random();
            GoodsData randomGood = goods.stream()
                    .skip(random.nextInt(goods.size())) // Skip a random number of items
                    .findFirst() // Get the first item after skipping
                    .orElse(null); // If no item found, return null

            if (randomGood != null) {
                randomGood.setActive(true); // Set it active
                randomGood.setCost(5); // Set a default cost
                ModEvents.getGoodsDataDatabase().putData(randomGood.getItemId(), randomGood);
                source.sendSuccess(
                        () -> Component.literal(
                                "Added random item to market: " + randomGood.getItem().getDescription().getString()),
                        false);
            } else {
                source.sendFailure(Component.literal("No valid item found to add to market."));
            }
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Add Random Item to Market Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    public static int showHighValueItems(CommandSourceStack source, int minCoins) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Get all active goods data from the database
            DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase();
            Collection<GoodsData> allGoodsData = goodsDataDataBase.getValues();

            if (allGoodsData.isEmpty()) {
                source.sendFailure(Component.literal("No items found in the market."));
                return 0;
            }

            // Filter active goods with cost greater than or equal to minCoins
            List<GoodsData> filteredGoods = allGoodsData.stream()
                    .filter(goods -> goods.isActive() && goods.getCost() >= minCoins)
                    .sorted(Comparator.comparingInt(GoodsData::getCost)) // Sort by cost ascending
                    .collect(Collectors.toList());

            if (filteredGoods.isEmpty()) {
                source.sendFailure(
                        Component.literal("No items found with cost greater than or equal to " + minCoins + " coins."));
                return 0;
            }

            MutableComponent response = Component
                    .literal("High-value market items (≥" + minCoins + " coins) (" + filteredGoods.size() + "): \n")
                    .withStyle(ChatFormatting.YELLOW);

            // Show the final list, highlight if in player's inventory
            for (GoodsData good : filteredGoods) {
                LOGGER.info(
                        "DEBUG: Showing high-value market item: " + good.getItemId() + ", cost = " + good.getCost());

                Item item = good.getItem();
                if (item != null) {
                    boolean inInventory = playerSource != null
                            && playerSource.getInventory().contains(item.getDefaultInstance());
                    String itemName = item.getDescription().getString();
                    if (inInventory) {
                        int count = playerSource.getInventory().countItem(item);
                        response = response.append(Component
                                .literal(" -" + itemName + ": " + good.getCost() + " coins (" + count + " cnt)\n")
                                .withStyle(ChatFormatting.GREEN));
                    } else {
                        response = response
                                .append(Component.literal(" -" + itemName + ": " + good.getCost() + " coins\n")
                                        .withStyle(ChatFormatting.WHITE));
                    }
                } else {
                    response = response.append(Component.literal(" -Unknown Item: " + good.getCost() + " coins\n")
                            .withStyle(ChatFormatting.WHITE));
                }
            }

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Show High Value Items Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }
}

/*
 * 
 * #############################################################################
 * ########
 * 
 * TODO add egg?
 * other easy stuff from netherworld.
 * 
 * 
 */