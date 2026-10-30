package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.WorldData;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraft.core.registries.BuiltInRegistries;
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
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
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
                .then(Commands.literal("importall")
                        .executes(context -> importAllMarketItems(context.getSource())))
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
                                            setActiveStatus(context.getSource(), itemId, status);
                                            return 0;
                                        }))))
                // setcost <itemId> <cost>
                .then(Commands.literal("setcost")
                        .then(Commands.argument("itemId", StringArgumentType.string())
                                .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            String itemId = StringArgumentType.getString(context, "itemId");
                                            int cost = IntegerArgumentType.getInteger(context, "cost");
                                            setCost(context.getSource(), itemId, cost);
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
                        .executes(context -> findWoodMarketItems(context.getSource())))
                // clearair - removes any market entries whose item resolves to Air
                .then(Commands.literal("clearair")
                        .executes(context -> {
                            clearAirMarketItems(context.getSource());
                            return 0;
                        }))
                // restoreremoved - re-adds the items accidentally removed by clearair
                .then(Commands.literal("restoreremoved")
                        .executes(context -> {
                            restoreRemovedItems(context.getSource());
                            return 0;
                        }));
        builder.then(adminBuilder);

        // Define a "buy" sub-command
        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
                .then(Commands.literal("villager")
                        .executes(context -> buyVillagerEgg(context.getSource())));
        builder.then(buyBuilder);

        // TODO MAKE AN ADD, and COMMAND REMOVE ITEM COMMAND

        // Register the main "market" command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showMarketInfo(CommandContext<CommandSourceStack> context) {
        try {
            MutableComponent response = Component.literal("Market Commands:\n").withStyle(ChatFormatting.GOLD);
            response = response.append(Component.literal("  /market show general\n").withStyle(ChatFormatting.WHITE));
            response = response.append(Component.literal("  /market show food\n").withStyle(ChatFormatting.WHITE));
            response = response.append(Component.literal("  /market show wood\n").withStyle(ChatFormatting.WHITE));
            response = response.append(Component.literal("  /market show stone\n").withStyle(ChatFormatting.WHITE));
            response = response
                    .append(Component.literal("  /market show high [minCoins]\n").withStyle(ChatFormatting.WHITE));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            context.getSource().sendFailure(Component.literal("Show market info Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
        return 0;
    }

    /**
     * Buy a villager spawn egg from the market.
     * Cost: village.level * 200 coins (from player wallet).
     * Cap: village must have fewer than max(4, village.level) villagers.
     */
    public static int buyVillagerEgg(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Only players can use this command."));
                return 0;
            }

            // STEP 1: Get player data and village.
            PlayerData player = ModEvents.getPlayerDatabase(source.getLevel()).getData(playerSource.getUUID());
            if (player == null || player.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("You must have a home village to buy a villager egg."));
                return 0;
            }
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase(source.getLevel());
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());
            if (village == null) {
                source.sendFailure(Component.literal("Village not found."));
                return 0;
            }

            // STEP 2: Check villager cap — must have fewer than max(4, level) villagers.
            int villagerCap = Math.max(4, village.getLevel());
            BlockPos villageCenter = village.getPosition().getWorldPosition();
            int currentVillagers = VillageCommand.countVillagers(villageCenter, source.getLevel(), 15);
            if (currentVillagers >= villagerCap) {
                source.sendFailure(Component.literal(
                        "Your village already has " + currentVillagers + " villagers (cap: " + villagerCap
                                + ")."));
                return 0;
            }

            // STEP 2.5: Block villager egg buy if village is in debt.
            if (village.getCoins() < 0) {
                                
                source.sendFailure(Component.literal(
                        "Your village is in debt (" + village.getCoins() + " coins). Pay off the debt before buying villager eggs."));
                return 0;
            }

            // STEP 3: Check cost.
            int cost = villagerEggCost(village.getLevel());
            if (player.getCoins() < cost) {
                source.sendFailure(Component.literal(
                        "Not enough coins. Villager egg costs " + cost + " coins (you have " + player.getCoins()
                                + ")."));
                return 0;
            }

            // STEP 4: Deduct coins and give egg.
            player.removeCoins(cost);
            ModEvents.getPlayerDatabase(source.getLevel()).putData(playerSource.getUUID(), player);

            ItemStack egg = new ItemStack(Items.VILLAGER_SPAWN_EGG, 1);
            playerSource.getInventory().placeItemBackInInventory(egg);

            source.sendSuccess(() -> Component.literal(
                    "Purchased 1 villager egg for " + cost + " coins. (" + currentVillagers + "/" + villagerCap
                            + " villagers)")
                    .withStyle(ChatFormatting.GREEN), false);
            LOGGER.info("Player {} bought villager egg for village {} (cost={}, villagers={}/{})",
                    playerSource.getName().getString(), village.getName(), cost, currentVillagers, villagerCap);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Buy villager egg exception — see log."));
            LOGGER.error(ex.getMessage(), ex);
        }
        return 0;
    }

    /**
     * Seeds a market type with default item counts if empty. Returns 1 on success,
     * 0 if no items could be seeded.
     */
    public static int seedMarketType(CommandSourceStack source, String type) {
        return seedMarketType(source, type, true);
    }

    private static int seedMarketType(CommandSourceStack source, String type, boolean resetPrice) {
        int seedCount = switch (type) {
            case "food" -> 15;
            case "general" -> 10;
            case "wood" -> 5;
            case "stone" -> 5;
            default -> 5;
        };
        source.sendSuccess(
                () -> Component.literal("Market " + type + " is empty — seeding with " + seedCount + " items..."),
                false);
        for (int i = 0; i < seedCount; i++) {
            addRandomItemToMarket(source, type, resetPrice);
        }
        Collection<GoodsData> check = getFilteredActiveGoods(source, type);
        if (check == null || check.isEmpty()) {
            source.sendFailure(Component.literal("Could not seed market for type: " + type
                    + " — no items imported yet? Ask an admin to run /market admin importall."));
            return 0;
        }
        return 1;
    }

    public static int showMarketList(CommandSourceStack source, String type) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // STEP 1: Get filtered sorted list of GoodsData by type.
            Collection<GoodsData> goodsDataList = getFilteredActiveGoods(source, type);
            if (goodsDataList == null || goodsDataList.isEmpty()) {
                if (seedMarketType(source, type) == 0)
                    return 0;
                goodsDataList = getFilteredActiveGoods(source, type);
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
                    response = response
                            .append(buildItemLine(source, item, good.getItemId(), good.getCost(), playerSource));
                } else {
                    response = response.append(
                            Component.literal(" -Unknown Item: " + numberFormat.format(good.getCost()) + " coins\n")
                                    .withStyle(ChatFormatting.WHITE));
                }
            }
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Show Market List Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
        return 0;
    }

    // Grab all items from the DB, filter by type.
    private static Collection<GoodsData> getFilteredGoods(CommandSourceStack source, String type) {
        // STEP 1: Grab all items from the DB.
        DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase(source.getLevel());
        Collection<GoodsData> goodsDataList = goodsDataDataBase.getValues();
        if (goodsDataList.isEmpty()) {
            return List.of();
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
    private static Collection<GoodsData> getFilteredActiveGoods(CommandSourceStack source, String type) {
        // STEP 1: Grab all items from the DB.
        DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase(source.getLevel());
        Collection<GoodsData> goodsDataList = goodsDataDataBase.getValues();
        if (goodsDataList.isEmpty()) {
            LOGGER.info("DEBUG: No goods data found in database for type: " + type);
            return List.of();
        }

        // STEP 2: Filter on type and active status.
        List<GoodsData> goodsData = new ArrayList<>();
        for (GoodsData goods : goodsDataList) {
            if (goods.getMarketType().equals(type) && goods.isActive()) {
                goodsData.add(goods);
            }
        }

        // STEP 3: Sort by cost ascending so market displays cheapest -> most expensive.
        goodsData.sort(
                Comparator.comparingInt(GoodsData::getCost)
                        .thenComparing(GoodsData::getItemId));
        return goodsData;
    }

    // Given a market type sell all items sellable from inventory.
    public static void sellMarketItems(CommandSourceStack source, String type) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null || PlayerCommand.getPlayer(source) == null) {
                if (playerSource == null)
                    source.sendFailure(Component.literal("Player not found."));
                return;
            }

            // Map<String, Integer> items = getMarketBuyItems(type);
            Collection<GoodsData> goods = getFilteredActiveGoods(source, type);

            int totalCoins = 0;
            // Loop over all items and sell all we have.
            MutableComponent response = Component.literal("Selling Market items: \n").withStyle(ChatFormatting.YELLOW); // TODO
                                                                                                                        // TEST
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
            for (GoodsData good : goods) {
                Item item = good.getItem();
                boolean inInventory = item != null && countSaleItems(playerSource.getInventory(), item) > 0;
                if (!inInventory) {
                    continue;
                }

                if (item != null) {
                    int coins = sellAllItemInInventory(source, playerSource, item);
                    totalCoins = Math.addExact(totalCoins, coins);
                    PlayerData wallet = PlayerCommand.getPlayer(source);
                    wallet.setCoins(Math.addExact(wallet.getCoins(), coins));
                    ModEvents.getPlayerDatabase(source.getLevel()).putData(playerSource.getUUID(), wallet);
                } else {
                    MutableComponent response2 = Component.literal(" -Unknown Item: " + good.getCost() + " coins\n")
                            .withStyle(ChatFormatting.WHITE);
                    MutableComponent finalResponse2 = response2;
                    source.sendSuccess(() -> finalResponse2, false);
                }
            }

            // TODO TEST playerData and coins. - failing on save
            int earned = totalCoins;
            int balance = PlayerCommand.getPlayer(source).getCoins();
            NumberFormat format = NumberFormat.getInstance();
            source.sendSuccess(() -> Component.literal("Player: " + playerSource.getScoreboardName()
                    + " given " + format.format(earned) + " coins. Total: " + format.format(balance)), false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Sell Market List Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    // Sell all items of a given type in the player's inventory.
    // Lower the price by 1 for each set of 32 sold.
    // Randomly increase the price of another item by 1 for each set of 64 sold.
    public static int sellAllItemInInventory(CommandSourceStack source, Player playerSource, Item item) {
        // Find all items matching in inventory and sell them.

        // STEP 1: Look over player inventory now and count matching items.
        int count = countSaleItems(playerSource.getInventory(), item);
        if (count == 0)
            return 0;
        LOGGER.info("DEBUG: Selling " + count + " of " + item.getDescriptionId());

        // STEP 3: Calculate coins earned and decrease price.
        String itemKey = BuiltInRegistries.ITEM.getKey(item).toString();
        GoodsData good = ModEvents.getGoodsDataDatabase(source.getLevel()).getData(itemKey);
        if (good == null || !good.isActive()) {
            source.sendFailure(Component.literal("GoodsData not found or inactive for item: " + itemKey));
            return 0;
        }
        SaleQuote quote = quoteSale(good.getCost(), good.getAmountSold(), count);
        PlayerData wallet = PlayerCommand.getPlayer(source);
        if (wallet == null)
            return 0;
        Math.addExact(wallet.getCoins(), quote.coins());
        int amountSold = Math.addExact(good.getAmountSold(), count);
        LOGGER.info("DEBUG: Found GoodsData for item: " + itemKey + ", cost = " + good.getCost() + ", amountSold = "
                + good.getAmountSold());
        int coinsTotal = quote.coins();
        good.setCost(quote.finalCost());

        // STEP 4: Randomly update other items.
        increaseOtherGoods(source, good, count);

        // Update the amount sold for this item.
        good.setAmountSold(amountSold);
        ModEvents.getGoodsDataDatabase(source.getLevel()).putData(itemKey, good);
        removeItem(playerSource.getInventory(), item.getDefaultInstance(), count);

        // STEP 5: Send final chat to player.
        String itemName = item.getDefaultInstance().getHoverName().getString();
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
    private static void increaseOtherGoods(CommandSourceStack source, GoodsData good, int count) {
        // If greater than 64 increments crossed, raise 1 other cost randomly by 1.
        int startCount64 = good.getAmountSold() % 64; // Get the current sold count modulo 64.
        int newCount64 = startCount64 + count; // Add the new count to it.
        int sets64 = newCount64 / 64; // Calculate how many sets of 64 we have.
        // TODO test
        LOGGER.info("DEBUG: sets64 = " + sets64);

        // TODO test can increase cost of SAME item, probably dont want that.

        List<GoodsData> candidates = getFilteredActiveGoods(source, good.getMarketType()).stream()
                .filter(candidate -> !candidate.getItemId().equals(good.getItemId()))
                .filter(candidate -> candidate.getCost() < Integer.MAX_VALUE)
                .toList();
        if (candidates.isEmpty())
            return;
        for (int i = 0; i < sets64; i++) {
            // Get a random item from the active filtered list.
            Random random = new Random();
            GoodsData randomGood = candidates.get(random.nextInt(candidates.size()));
            if (randomGood.getCost() == Integer.MAX_VALUE)
                continue;

            randomGood.setCost(randomGood.getCost() + 1); // Increase the cost by 1.
            ModEvents.getGoodsDataDatabase(source.getLevel()).putData(randomGood.getItemId(), randomGood);
            LOGGER.info("DEBUG: Raising cost of " + randomGood.getItemId() + " to "
                    + randomGood.getCost());
        }
    }

    // TODO remove out to proper home. player maybe.
    // Remove all items from inventory that match item.
    public static void removeItem(Inventory inventory, ItemStack pStack, int count) {
        if (count < 0 || countSaleItems(inventory, pStack.getItem()) < count)
            throw new IllegalArgumentException("Insufficient items to remove: " + count);
        int remaining = count;
        for (int slot = 0; slot <= Inventory.INVENTORY_SIZE; slot++) {
            int inventorySlot = slot == Inventory.INVENTORY_SIZE ? Inventory.SLOT_OFFHAND : slot;
            ItemStack stack = inventory.getItem(inventorySlot);
            if (stack.is(pStack.getItem())) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
            if (remaining == 0)
                break;
        }
        inventory.setChanged();
    }

    static int countSaleItems(Inventory inventory, Item item) {
        int count = 0;
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (stack.is(item))
                count = Math.addExact(count, stack.getCount());
        }
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        return offhand.is(item) ? Math.addExact(count, offhand.getCount()) : count;
    }

    record SaleQuote(int coins, int finalCost) {
    }

    /**
     * Prices drop after each complete group of 32 lifetime sales, never below zero.
     */
    static SaleQuote quoteSale(int initialCost, int amountSold, int count) {
        if (initialCost < 0 || amountSold < 0 || count < 0)
            throw new IllegalArgumentException("Price, amount sold and count must be non-negative");
        int firstBoundary = 32 - amountSold % 32;
        int firstCount = Math.min(count, firstBoundary);
        long coins = (long) initialCost * firstCount;
        int cost = firstCount == firstBoundary ? Math.max(0, initialCost - 1) : initialCost;
        int remaining = count - firstCount;
        int groups = Math.min(remaining / 32, cost);
        coins += 16L * groups * (2L * cost - groups + 1);
        cost -= groups;
        remaining -= groups * 32;
        if (cost > 0)
            coins += (long) cost * remaining;
        return new SaleQuote(Math.toIntExact(coins), cost);
    }

    static int villagerEggCost(int level) {
        if (level < 0)
            throw new IllegalArgumentException("Village level cannot be negative");
        return Math.multiplyExact(level, 200);
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
            List<String> matchingItems = BuiltInRegistries.ITEM.stream()
                    .filter(item -> BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase()
                            .contains(keyword.toLowerCase()))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude
                                                                                                                     // slabs
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("potted_")) // Exclude
                                                                                                                       // potted
                                                                                                                       // cant
                                                                                                                       // get
                                                                                                                       // items
                    .filter(item -> item.getDefaultInstance().getMaxStackSize() > 1) // Exclude unstackable items
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
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
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Find Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
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
            List<String> matchingItems = BuiltInRegistries.ITEM.stream()
                    .filter(item -> {
                        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                        return namespace.equalsIgnoreCase("pamhc2foodcore")
                                || namespace.equalsIgnoreCase("pamhc2foodextended");
                    })
                    .filter(item -> item.getDefaultInstance().getMaxStackSize() > 1) // Exclude unstackable items
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
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
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Find Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
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

            List<String> matchingItems = BuiltInRegistries.ITEM.stream()
                    .filter(item -> keywords.stream()
                            .anyMatch(keyword -> BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase()
                                    .contains(keyword.toLowerCase())))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude
                                                                                                                     // slabs
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("potted_")) // Exclude
                                                                                                                       // potted
                                                                                                                       // cant
                                                                                                                       // get
                                                                                                                       // items
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_button")) // cut
                                                                                                                       // down
                                                                                                                       // number
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_sign"))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_boat"))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_plate"))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_counter"))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase().contains("_sink"))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase()
                            .contains("iceandfire"))
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase()
                            .contains("_bridge_stair")) // just to cut down # of these
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase()
                            .contains("_lapidified")) // hard item?
                    .filter(item -> !BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase()
                            .contains("villagersplus")) // bogus matches.
                    .filter(item -> item.getDefaultInstance().getMaxStackSize() > 1) // Exclude unstackable items
                    // probably remove a few more types...
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
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
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Find Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
        return 0;
    }

    // TODO Move to new home.
    // Run daily task for all markets, removing old items, and adding new items.
    public static int runDailyTask(CommandSourceStack source) {
        try {
            List<String> types = List.of("food", "wood", "stone", "general");
            for (String type : types) {
                if (getFilteredGoods(source, type).isEmpty()) {
                    source.sendFailure(Component.literal("No goods found for type: " + type));
                    return 0;
                }
                for (GoodsData good : getFilteredActiveGoods(source, type)) {
                    Math.addExact(good.getCost(), 1);
                }
                rotationCount(getFilteredActiveGoods(source, type).size(),
                        (int) getFilteredGoods(source, type).stream()
                                .filter(good -> !good.isActive() && validMarketItem(good.getItemId())).count());
            }
            for (String type : types) {
                if (getNewMarketItems(source, type) <= 0)
                    return 0;
            }
            for (String type : types) {
                if (raiseMarketPrices(source, type) <= 0)
                    return 0;
            }

            // Keep scheduler status in sync even when admin runs /market admin rundaily.
            WorldData worldData = ModEvents.getWorldDataDatabase(source.getLevel()).getOrCreate(0, WorldData::new);
            worldData.markMarketDailyRanToday();
            ModEvents.getWorldDataDatabase(source.getLevel()).putData(0, worldData);

            // Broadcast to all online players that the market has refreshed.
            Component broadcastMsg = Component.literal("[Market] The market has been refreshed for today!")
                    .withStyle(ChatFormatting.GOLD);
            source.getServer().getPlayerList().broadcastSystemMessage(broadcastMsg, false);

            // Also confirm to the command source (e.g. admin or scheduler log).
            source.sendSuccess(() -> Component.literal("Daily task completed for all markets."), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Run Daily Task Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
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
            Collection<GoodsData> activeGoods = getFilteredActiveGoods(source, type);
            // Shuffle and remove 1 item per 5 existing.
            ArrayList<GoodsData> shuffledItems = new ArrayList<>(activeGoods);
            Collections.shuffle(shuffledItems);
            ArrayList<GoodsData> newItemsList = new ArrayList<>(getFilteredGoods(source, type).stream()
                    .filter(good -> !good.isActive() && validMarketItem(good.getItemId()))
                    .toList());
            Collections.shuffle(newItemsList);

            // Print out the ones we are removing.
            MutableComponent response = Component
                    .literal("Removing " + type + " Market items(" + shuffledItems.size() + "): \n")
                    .withStyle(ChatFormatting.YELLOW);
            int count = rotationCount(shuffledItems.size(), newItemsList.size());
            for (int i = 0; i < count; i++) { // mod 5
                // String itemName = shuffledItems.get(i);
                GoodsData good = shuffledItems.get(i);
                String itemName = validMarketItem(good.getItemId())
                        ? good.getItem().getDefaultInstance().getHoverName().getString()
                        : good.getItemId();
                response = response.append(Component.literal("- " + itemName + "\n"));
                good.setActive(Boolean.valueOf(false)); // Set active status to false

                ModEvents.getGoodsDataDatabase(source.getLevel()).putData(good.getItemId(), good);
            }

            // STEP 3: Add in new items.
            for (int i = 0; i < count; i++) {
                GoodsData good = newItemsList.get(i);
                // TODO MAKE METHOD on goodsData
                String itemName = good.getItem().getDefaultInstance().getHoverName().getString();

                // Add the new item to the response.
                response = response.append(Component.literal("+ " + itemName + "\n"));
                // Add to market, set active and cost.
                good.setActive(Boolean.valueOf(true)); // Set active status
                // Set at 4, cause we add one to all afterwards.
                good.setCost(4); // Set a default cost, can be changed later.
                good.setDateAddedToMarket(java.time.LocalDate.now().toString());
                ModEvents.getGoodsDataDatabase(source.getLevel()).putData(good.getItemId(), good);
            }

            // Broadcast item changes to all online players.
            MutableComponent finalResponse = response;
            source.getServer().getPlayerList().broadcastSystemMessage(finalResponse, false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Get New Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
        return 0;
    }

    // Increment all market prices by 1 for a given type.
    public static int raiseMarketPrices(CommandSourceStack source, String type) {
        try {
            // STEP 1: Get market list
            Collection<GoodsData> activeGoods = getFilteredActiveGoods(source, type);
            for (GoodsData good : activeGoods)
                Math.addExact(good.getCost(), 1);

            // TODO TEST
            // STEP 2: Increase cost of each item by 1.
            for (GoodsData good : activeGoods) {
                good.setCost(Math.addExact(good.getCost(), 1));
                ModEvents.getGoodsDataDatabase(source.getLevel()).putData(good.getItemId(), good);
                LOGGER.info("DEBUG: Raising cost of " + good.getItemId() + " to " + good.getCost());
            }
            return 1;

        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Get New Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
        return 0;
    }

    public static int importAllMarketItems(CommandSourceStack source) {
        int result = 1;
        for (String type : List.of("food", "wood", "stone", "general")) {
            if (!importMarketItemsTXT(source, type))
                result = 0;
        }
        return result;
    }

    public static void initializeMarket(CommandSourceStack source) {
        for (String type : List.of("food", "wood", "stone", "general")) {
            if (getFilteredGoods(source, type).isEmpty() && !importMarketItemsTXT(source, type)) continue;
            if (getFilteredActiveGoods(source, type).isEmpty()) seedMarketType(source, type, false);
        }
    }

    public static boolean importMarketItemsTXT(CommandSourceStack source, String type) {
        String path = "data/farmupcraft/farmupcraft/market/market_" + type + "_items.txt";
        InputStream resource = MarketCommand.class.getClassLoader().getResourceAsStream(path);
        if (resource == null) resource = MarketCommand.class.getClassLoader()
                .getResourceAsStream("data/farmupcraft/market/market_" + type + "_items.txt");
        try (InputStream inputStream = resource) {
            if (inputStream == null) {
                throw new FileNotFoundException(
                        "Resource not found: data/farmupcraft/farmupcraft/market/market_" + type + "_items.txt");
            }
            String fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            // Parse the file content into a list of item names, unordered array of strings.
            String[] itemsArray = fileContent.split("\n");

            // NOTE: for now an item can be in only ONE market type. may update later to
            // allow multiple types.
            DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase(source.getLevel());

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
                        // Guard: skip if the item resolves to Air (unrecognised registry key).
                        if (!validMarketItem(trimmedItem)) {
                            LOGGER.warn("Market import SKIPPED '{}' — resolves to Air/null, mod may not be loaded.",
                                    trimmedItem);
                            source.sendFailure(Component.literal("Market import skipped invalid/unavailable item: " + trimmedItem));
                            continue;
                        }
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
            return true;

        } catch (IOException | IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Import Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
            return false;
        }
    }

    public static void clearAllMarketItems(CommandSourceStack source) {
        try {
            // Clear all market items from the database.
            DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase(source.getLevel());
            goodsDataDataBase.clearDataBase(true);
            source.sendSuccess(() -> Component.literal("All market items cleared."), false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Clear All Market Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    public static void clearAirMarketItems(CommandSourceStack source) {
        try {
            DataBase<String, GoodsData> goodsDb = ModEvents.getGoodsDataDatabase(source.getLevel());
            List<String> toRemove = new ArrayList<>();
            for (String key : goodsDb.getKeys()) {
                GoodsData gd = goodsDb.getData(key);
                if (gd == null) {
                    toRemove.add(key);
                    continue;
                }
                if (!validMarketItem(gd.getItemId())) {
                    LOGGER.warn("clearAirMarketItems: removing '{}' — resolves to Air/null", gd.getItemId());
                    toRemove.add(key);
                }
            }
            if (!toRemove.isEmpty()) {
                goodsDb.removeDataBatch(toRemove);
            }
            int count = toRemove.size();
            source.sendSuccess(() -> Component.literal("Removed " + count + " Air/invalid market item(s): "
                    + String.join(", ", toRemove)), false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("clearAirMarketItems exception — see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
    }
    /*
     * list here alot of these are ok though and im not sure why exactly???
     * 07:35:59] [Render thread/INFO] [minecraft/ChatComponent]: [System] [CHAT]
     * Removed 46 Air/invalid market item(s): pamhc2foodcore:rabbitpotpieitem,
     * pamhc2foodcore:choruspieitem, pamhc2foodcore:melonmuffinitem,
     * pamhc2foodcore:sweetberrypopsicleitem, pamhc2foodcore:melondonutitem,
     * pamhc2foodcore:gummycreepersitem, pamhc2foodcore:glowberrydonutitem,
     * pamhc2foodcore:beefpotpieitem, pamhc2foodcore:carrotpieitem,
     * pamhc2foodcore:powdereddonutitem, pamhc2foodcore:glowberrymuffinitem,
     * pamhc2foodcore:chorusjellytoastitem, pamhc2foodcore:crackersandcheeseitem,
     * alexmobs:bison_fur, pamhc2foodcore:choruspopsicleitem,
     * pamhc2foodcore:chorusjuiceitem, pamhc2foodcore:chocolatepieitem,
     * pamhc2foodcore:plaindonutitem, pamhc2foodcore:melonpopsicleitem,
     * pamhc2foodcore:fishpotpieitem, pamhc2foodcore:carameldonutitem,
     * minecraft:sea_grass, iceandfire:dragon_bone,
     * pamhc2foodcore:glowberrypopsicleitem, pamhc2foodcore:melonyogurtitem,
     * pamhc2foodcore:fudgesicleitem, pamhc2foodcore:honeypieitem,
     * pamhc2foodcore:carrotdonutitem, pamhc2foodcore:carrotjuiceitem,
     * pamhc2foodcore:muttonpotpieitem, pamhc2foodcore:appledonutitem,
     * pamhc2foodcore:porkpotpieitem, pamhc2foodcore:chocolatemilkitem,
     * pamhc2foodcore:chorusjellyitem, pamhc2foodcore:sweetberrydonutitem,
     * pamhc2foodcore:sweetberrymuffinitem, pamhc2foodcore:caramelpieitem,
     * pamhc2foodcore:chorusmuffinitem, pamhc2foodcore:pumpkindonutitem,
     * pamhc2foodcore:sprinklesdonutitem, pamhc2foodcore:applemuffinitem,
     * pamhc2foodcore:honeyglazeddonutitem, pamhc2foodcore:chorussmoothieitem,
     * pamhc2foodcore:chorusdonutitem, pamhc2foodcore:applepopsicleitem,
     * pamhc2foodcore:chorusyogurtitem
     */

    /** Restores the 46 items accidentally removed by /market admin clearair. */
    public static void restoreRemovedItems(CommandSourceStack source) {
        try {
            DataBase<String, GoodsData> goodsDb = ModEvents.getGoodsDataDatabase(source.getLevel());
            List<String> items = java.util.Arrays.asList(
                    "pamhc2foodcore:rabbitpotpieitem",
                    "pamhc2foodcore:choruspieitem",
                    "pamhc2foodcore:melonmuffinitem",
                    "pamhc2foodcore:sweetberrypopsicleitem",
                    "pamhc2foodcore:melondonutitem",
                    "pamhc2foodcore:gummycreepersitem",
                    "pamhc2foodcore:glowberrydonutitem",
                    "pamhc2foodcore:beefpotpieitem",
                    "pamhc2foodcore:carrotpieitem",
                    "pamhc2foodcore:powdereddonutitem",
                    "pamhc2foodcore:glowberrymuffinitem",
                    "pamhc2foodcore:chorusjellytoastitem",
                    "pamhc2foodcore:crackersandcheeseitem",
                    "alexmobs:bison_fur",
                    "pamhc2foodcore:choruspopsicleitem",
                    "pamhc2foodcore:chorusjuiceitem",
                    "pamhc2foodcore:chocolatepieitem",
                    "pamhc2foodcore:plaindonutitem",
                    "pamhc2foodcore:melonpopsicleitem",
                    "pamhc2foodcore:fishpotpieitem",
                    "pamhc2foodcore:carameldonutitem",
                    "minecraft:sea_grass",
                    "iceandfire:dragon_bone",
                    "pamhc2foodcore:glowberrypopsicleitem",
                    "pamhc2foodcore:melonyogurtitem",
                    "pamhc2foodcore:fudgesicleitem",
                    "pamhc2foodcore:honeypieitem",
                    "pamhc2foodcore:carrotdonutitem",
                    "pamhc2foodcore:carrotjuiceitem",
                    "pamhc2foodcore:muttonpotpieitem",
                    "pamhc2foodcore:appledonutitem",
                    "pamhc2foodcore:porkpotpieitem",
                    "pamhc2foodcore:chocolatemilkitem",
                    "pamhc2foodcore:chorusjellyitem",
                    "pamhc2foodcore:sweetberrydonutitem",
                    "pamhc2foodcore:sweetberrymuffinitem",
                    "pamhc2foodcore:caramelpieitem",
                    "pamhc2foodcore:chorusmuffinitem",
                    "pamhc2foodcore:pumpkindonutitem",
                    "pamhc2foodcore:sprinklesdonutitem",
                    "pamhc2foodcore:applemuffinitem",
                    "pamhc2foodcore:honeyglazeddonutitem",
                    "pamhc2foodcore:chorussmoothieitem",
                    "pamhc2foodcore:chorusdonutitem",
                    "pamhc2foodcore:applepopsicleitem",
                    "pamhc2foodcore:chorusyogurtitem");

            int added = 0;
            int skipped = 0;
            for (String itemId : items) {
                if (goodsDb.getData(itemId) != null) {
                    skipped++;
                    continue;
                }
                // Determine marketType heuristically.
                String marketType = itemId.startsWith("pamhc2foodcore:") ? "food" : "general";
                GoodsData gd = new GoodsData(itemId, 5, 0, "common", false, marketType, "");
                goodsDb.putData(itemId, gd);
                added++;
            }
            goodsDb.setDirty();
            int finalAdded = added;
            int finalSkipped = skipped;
            source.sendSuccess(() -> Component.literal(
                    "[Market] Restored " + finalAdded + " item(s). Skipped " + finalSkipped + " already-present.")
                    .withStyle(ChatFormatting.GREEN), false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("restoreRemovedItems exception — see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    public static void addRandomItemToMarket(CommandSourceStack source, String type) {
        addRandomItemToMarket(source, type, true);
    }

    private static void addRandomItemToMarket(CommandSourceStack source, String type, boolean resetPrice) {
        try {
            // Get a random item from the filtered goods.
            Collection<GoodsData> goods = getFilteredGoods(source, type).stream()
                    .filter(good -> !good.isActive() && validMarketItem(good.getItemId())).toList();
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
                if (resetPrice) randomGood.setCost(5);
                randomGood.setDateAddedToMarket(java.time.LocalDate.now().toString());
                ModEvents.getGoodsDataDatabase(source.getLevel()).putData(randomGood.getItemId(), randomGood);
                source.sendSuccess(
                        () -> Component.literal(
                                "Added random item to market: " + randomGood.getItem().getDefaultInstance().getHoverName().getString()),
                        false);
            } else {
                source.sendFailure(Component.literal("No valid item found to add to market."));
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Add Random Item to Market Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    static boolean validMarketItem(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.getValue(id) != Items.AIR;
    }

    static int rotationCount(int activeItems, int availableItems) {
        if (activeItems < 0 || availableItems < 0)
            throw new IllegalArgumentException("Market item counts cannot be negative");
        int replacements = activeItems / 5;
        if (availableItems < replacements)
            throw new IllegalStateException("Not enough inactive valid market items: need "
                    + replacements + ", available " + availableItems);
        return replacements;
    }

    private static void setActiveStatus(CommandSourceStack source, String itemId, String status) {
        DataBase<String, GoodsData> database = ModEvents.getGoodsDataDatabase(source.getLevel());
        GoodsData goods = database.getData(itemId);
        if (goods == null) {
            source.sendFailure(Component.literal("GoodsData not found for item: " + itemId));
            return;
        }
        Boolean active = parseActiveStatus(status);
        if (active == null) {
            source.sendFailure(Component.literal("Invalid active status: '" + status
                    + "'. Use true/false, active/inactive, yes/no, or 1/0."));
            return;
        }
        goods.setActive(active);
        goods.setDateAddedToMarket(active ? java.time.LocalDate.now().toString() : "");
        database.putData(itemId, goods);
        source.sendSuccess(() -> Component.literal("Set active status of item: " + itemId + " to " + status), true);
    }

    static @Nullable Boolean parseActiveStatus(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "active" -> true;
            case "false", "0", "no", "inactive" -> false;
            default -> null;
        };
    }

    private static void setCost(CommandSourceStack source, String itemId, int cost) {
        DataBase<String, GoodsData> database = ModEvents.getGoodsDataDatabase(source.getLevel());
        GoodsData goods = database.getData(itemId);
        if (goods == null) {
            source.sendFailure(Component.literal("GoodsData not found for item: " + itemId));
            return;
        }
        goods.setCost(cost);
        database.putData(itemId, goods);
        source.sendSuccess(() -> Component.literal("Set cost of item: " + itemId + " to " + cost), true);
    }

    /**
     * Builds a single styled chat line for a market item.
     * If the item has a crafting recipe: teal/green + underlined + JEI click.
     * If no recipe (e.g. oxidized copper): plain white/green + hover only.
     */
    private static MutableComponent buildItemLine(CommandSourceStack source, Item item, String itemId, int cost,
            @Nullable Player playerSource) {
        String itemName = item.getDefaultInstance().getHoverName().getString();
        boolean inInventory = playerSource != null
                && playerSource.getInventory().contains(item.getDefaultInstance());
        int inventoryCount = inInventory ? playerSource.getInventory().countItem(item) : 0;
        NumberFormat numberFormat = NumberFormat.getInstance();
        String costStr = numberFormat.format(cost);
        String label = inInventory
                ? itemName + ": " + costStr + " coins (" + inventoryCount + " cnt)\n"
                : itemName + ": " + costStr + " coins\n";

        // Check if item has any crafting recipe.
        boolean hasRecipe = source.getServer() != null && source.getServer().getRecipeManager()
                .getRecipes().stream()
                .filter(recipe -> recipe.value().getType() == RecipeType.CRAFTING)
                .flatMap(recipe -> recipe.value().display().stream())
                .flatMap(display -> display.result().resolveForStacks(SlotDisplayContext.fromLevel(source.getLevel())).stream())
                .anyMatch(stack -> stack.is(item));

        Style style = itemLineStyle(item, itemId, inInventory, hasRecipe);

        // Strip trailing newline from label so we can append the [x] on the same line.
        String labelNoNewline = label.endsWith("\n") ? label.substring(0, label.length() - 1) : label;
        MutableComponent line = Component.literal(labelNoNewline).withStyle(style);

        // Admin [x] remove button — only shown in creative mode.
        // Wrap itemId in quotes so Brigadier handles the colon in namespaced IDs
        // correctly.
        if (playerSource != null && playerSource.isCreative()) {
            String removeCmd = "/market admin setactive \"" + itemId + "\" inactive";
            MutableComponent removeBtn = Component.literal(" [x]")
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.RED)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand(removeCmd))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Remove " + itemId + " from market\n" + removeCmd))));
            line = line.append(removeBtn);
        }

        return line.append(Component.literal("\n"));
    }

    static Style itemLineStyle(Item item, String itemId, boolean inInventory, boolean hasRecipe) {
        if (hasRecipe) {
            // Item tooltips can consume chat clicks; recipe links use text tooltips instead.
            return Style.EMPTY.withColor(inInventory ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/frecipe " + itemId))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Click to view recipe in JEI\n" + itemId)));
        }
        return Style.EMPTY.withColor(inInventory ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                .withHoverEvent(new HoverEvent.ShowItem(new ItemStackTemplate(item)));
    }

    public static int showHighValueItems(CommandSourceStack source, int minCoins) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Get all active goods data from the database
            DataBase<String, GoodsData> goodsDataDataBase = ModEvents.getGoodsDataDatabase(source.getLevel());
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
                    response = response
                            .append(buildItemLine(source, item, good.getItemId(), good.getCost(), playerSource));
                } else {
                    response = response.append(Component.literal(" -Unknown Item: " + good.getCost() + " coins\n")
                            .withStyle(ChatFormatting.WHITE));
                }
            }

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            source.sendFailure(Component.literal("Show High Value Items Exception thrown - see log"));
            LOGGER.error(ex.getMessage(), ex);
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