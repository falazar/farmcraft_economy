package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.*;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

import net.minecraftforge.registries.ForgeRegistries;


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
                }));
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

        // Define a "find" sub-command for Admin only, to look for items by keyword
        LiteralArgumentBuilder<CommandSourceStack> findBuilder = Commands.literal("find")
                .requires(source -> source.hasPermission(2)) // Restrict to admins (permission level 2 or higher)
                .then(Commands.argument("keyword", StringArgumentType.string())
                        .executes(context -> {
                            String keyword = StringArgumentType.getString(context, "keyword");
                            return findMarketItems(context.getSource(), keyword);
                        }));
        builder.then(findBuilder);

        // Define a "findfoods" sub-command for Admin only, to look for items by keyword
        LiteralArgumentBuilder<CommandSourceStack> findFoodsBuilder = Commands.literal("findfoods")
                .requires(source -> source.hasPermission(2)) // Restrict to admins (permission level 2 or higher)
                .executes(context -> {
                    return findFoodMarketItems(context.getSource());
                });
        builder.then(findFoodsBuilder);

        // Define a "findwood" sub-command for Admin only, to look for items by keyword
        LiteralArgumentBuilder<CommandSourceStack> findWoodBuilder = Commands.literal("findwood")
                .requires(source -> source.hasPermission(2)) // Restrict to admins (permission level 2 or higher)
                .executes(context -> {
                    return findWoodMarketItems(context.getSource());
                });
        builder.then(findWoodBuilder);

        // TODO make a special function to call each of the four market types to help define them.

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

            Map<String, Integer> items = getMarketBuyItems(type);

            // Loop over all items and prices to chat.
            MutableComponent response = Component.literal("Market " + type + " items: \n").withStyle(ChatFormatting.YELLOW);
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey()));
                // Highlight ones in your inventory now.
                boolean inInventory = playerSource.getInventory().contains(item.getDefaultInstance());

                if (item != null) {
                    // Get the display name of the item
                    String itemName = item.getDescription().getString();
                    if (!inInventory) {
                        response = response.append(Component.literal(" -" + itemName + ": " + entry.getValue() + " coins\n").withStyle(ChatFormatting.WHITE));
                    } else {
                        response = response.append(Component.literal(" -" + itemName + ": " + entry.getValue() + " coins\n").withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    response = response.append(Component.literal(" -Unknown Item: " + entry.getValue() + " coins\n").withStyle(ChatFormatting.WHITE));
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

    // Given a market type sell all items sellable from inventory.
    public static void sellMarketItems(CommandSourceStack source, String type) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            Map<String, Integer> items = getMarketBuyItems(type);

            int totalCoins = 0;
            // Loop over all items and sell all we have.
            MutableComponent response = Component.literal("Selling Market items: \n").withStyle(ChatFormatting.YELLOW); // TODO TEST
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey()));
                boolean inInventory = playerSource.getInventory().contains(item.getDefaultInstance());
                if (!inInventory) {
                    continue;
                }

                if (item != null) {
                    int coins = sellAllItemInInventory(source, playerSource, item, entry.getValue());
                    totalCoins += coins;
                } else {
                    response = response.append(Component.literal(" -Unknown Item: " + entry.getValue() + " coins\n").withStyle(ChatFormatting.WHITE));
                }
            }

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);

            // TODO TEST playerData and coins. - failing on save
            PlayerCommand.givePlayerCoins(source, totalCoins);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Sell Market List Exception thrown - see log"));
            ex.printStackTrace();
        }
    }

    public static int sellAllItemInInventory(CommandSourceStack source, Player playerSource, Item item, int coins) {
        // Find all items matching in inventory and sell them.

        // Look over player inventory now and count items.
        int count = playerSource.getInventory().countItem(item);
        int coinsTotal = count * coins;

        // Remove all items.
        ItemStack itemStack = item.getDefaultInstance();
        removeItem(playerSource.getInventory(), itemStack, count);

        // Send chat to player.
        String itemName = item.getDescription().getString();
        MutableComponent response = Component.literal(" - Sold " + count + " of " + itemName + " for " + coinsTotal + " coins\n").withStyle(ChatFormatting.GREEN);
        MutableComponent finalResponse = response;
        source.sendSuccess(() -> finalResponse, false);

        // Return coins earned.
        return coinsTotal;
    }

    // TODO remove out to proper home. player maybe.
    // Remove all items from inventory that match item.
    public static void removeItem(Inventory inventory, ItemStack pStack, Integer count) {
        // Regular inventory
        for (ItemStack itemStack : inventory.items) { // 36 items here.
            if (itemStack.isEmpty()) {
                continue;
            }
            // Check if the itemStack matches the pStack
            LOGGER.info("DEBUG comparing items: " + itemStack.getItem() + " == " + pStack.getItem());
            if (itemStack.getItem() == pStack.getItem()) {
                LOGGER.info("FOUND, removing now!");
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

    // TODO notice put all new ones at TOP of the list.
    // We will hard code a list here now to play with.
    // Fields needed: itemId, price, numSold
    public static Map<String, Integer> getMarketBuyItems(String type) {
        if (type.equals("food")) {
            String foodString = """
                    pamhc2foodextended:energydrinkitem\t5
                    pamhc2foodextended:rawtofishitem\t5
                    pamhc2foodextended:cornedbeefandcabbageitem\t5
                    pamhc2foodcore:baconandeggsitem\t9
                    pamhc2foodextended:cookedtofeakitem\t8
                    pamhc2foodcore:vinegaritem\t5
                    pamhc2foodextended:passionfruityogurtitem\t8
                    pamhc2foodextended:honeysoyribsitem\t6
                    pamhc2foodextended:frosteddonutitem\t7
                    pamhc2foodextended:gardensoupitem\t4
                    pamhc2foodextended:spaghettidinneritem\t6
                    pamhc2foodextended:bibimbapitem\t13
                    pamhc2foodextended:charsiuitem\t12
                    pamhc2foodextended:sweetpickleitem\t8
                    pamhc2foodextended:potatoesobrienitem\t10
                    """;
            return parseItemsFromString(foodString);
        } else if (type.equals("wood")) {
            // Use a text block string here:
            String woodString = """
                    biomesoplenty:mahogany_stairs	8
                    cfm:stripped_acacia_mail_box	5
                    cfm:jungle_chair	7
                    cfm:mangrove_kitchen_drawer	6
                    valhelsia_structures:bundled_mangrove_posts	13
                    """;
            // Parse that into our items now.
            return parseItemsFromString(woodString);
        } else if (type.equals("stone")) {
            String stoneString = """
                    minecraft:obsidian\t5
                    minecraft:stone_bricks\t8
                    minecraft:redstone_torch\t5
                    mcwbridges:deepslate_brick_bridge_stair\t7
                    minecraft:polished_andesite_stairs\t4
                    """;
            return parseItemsFromString(stoneString);
        } else if (type.equals("general")) {
            String generalString = """
                    biomesoplenty:spanish_moss	5
                    cfm:light_gray_grill	5
                    minecraft:mycelium	9
                    cfm:magenta_grill	7
                    minecraft:prismarine_shard	6
                    minecraft:flowering_azalea_leaves	10
                    cfm:black_kitchen_drawer	13
                    untamedwilds:material_fat	6
                    minecraft:magenta_candle	9
                    minecraft:amethyst_block	8
                    """;
            // maybe no concrete, only powder? too annoying?  maybe no stained glass panes, or make harder?
            // copper one is broken, odd.
            return parseItemsFromString(generalString);
        } else {
            LOGGER.info("DEBUG unknown market type: " + type);
            return new HashMap<>(); // Return an empty map if the type is unknown
        }
    }

    // Temp helper method.
    private static Map<String, Integer> parseItemsFromString(String input) {
        Map<String, Integer> items = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        String[] lines = input.split("\n");
        for (String line : lines) {
            String[] parts = line.split("\t");
            if (parts.length == 2) {
                String item = parts[0].trim();
                int price = Integer.parseInt(parts[1].trim());
                items.put(item, price);
            }
        }
        LOGGER.info("DEBUG items = " + items);
        return items;
    }

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
            // mostly foods and extended food items.  and edible?  no plain crops.
            // maybe save some full queries here so we can reuse them later.
            // IE all woods but no buttons or some such.

            // todo change keyword to a keyword list, then it can grab oak, jungle etc. all in one go.
            // TODO check for full word boundary for strings like oak, or it will overmach.

            // Search for items containing the keyword
            List<String> matchingItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains(keyword.toLowerCase()))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude slabs
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("potted_")) // Exclude potted cant get items
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
            // mostly foods and extended food items.  and edible?  no plain crops.
            // maybe save some full queries here so we can reuse them later.
            // IE all woods but no buttons or some such.

            // todo change keyword to a keyword list, then it can grab oak, jungle etc. all in one go.
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
                    .toList();            // If no items are found, notify the player
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
                    .filter(item -> keywords.stream().anyMatch(keyword -> ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains(keyword.toLowerCase())))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude slabs
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("potted_")) // Exclude potted cant get items
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_button")) // cut down number
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_sign"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_boat"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_plate"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_counter"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_sink"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("iceandfire"))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_bridge_stair")) // just to cut down # of these
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_lapidified")) // hard item?
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("villagersplus")) // bogus matches.
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


}

/* sample data found


#####################################################################################
Wood Market Type

jungle:
[12:53:31] [Render thread/INFO] [minecraft/ChatComponent]: [System] [CHAT] Items matching "jungle":\n- minecraft:jungle_boat\n- minecraft:jungle_button\n- minecraft:jungle_chest_boat\n- minecraft:jungle_door\n- minecraft:jungle_fence\n- minecraft:jungle_fence_gate\n- minecraft:jungle_hanging_sign\n- minecraft:jungle_leaves\n- minecraft:jungle_log\n- minecraft:jungle_planks\n- minecraft:jungle_pressure_plate\n- minecraft:jungle_sapling\n- minecraft:jungle_sign\n- minecraft:jungle_slab\n- minecraft:jungle_stairs\n- minecraft:jungle_trapdoor\n- minecraft:jungle_wood\n- minecraft:stripped_jungle_log\n- minecraft:stripped_jungle_wood\n

[12:53:31] [Render thread/INFO] [minecraft/ChatComponent]: [System] [CHAT] Items matching "jungle":
minecraft:jungle_boat
minecraft:jungle_button
minecraft:jungle_chest_boat
minecraft:jungle_door
minecraft:jungle_fence
minecraft:jungle_fence_gate
minecraft:jungle_hanging_sign
minecraft:jungle_leaves
minecraft:jungle_log
minecraft:jungle_planks
minecraft:jungle_pressure_plate
minecraft:jungle_sapling
minecraft:jungle_sign
minecraft:jungle_slab
minecraft:jungle_stairs
minecraft:jungle_trapdoor
minecraft:jungle_wood
minecraft:stripped_jungle_log
minecraft:stripped_jungle_wood
19 items with boat. and chest boat both gone now.


TODO NO unstackable items... like boats, filter out all!
no simple items, hmmm.....
fences and doors all ok?
no button?
no pressure plate, or ok? or boring?  I think these are all ok cost wise actually.
log and stripped are most expensive, and there are some viking ones later and furniture ones?

Oak has dark oak and oak, but that is ok, on PROD game may have more though?
38 oak_ found, but should be 36 with 4 boats removed, hmmm what is extra?
potted flowering oak sapling  hmmm whats that cant find that item, weird... manually remove I guess.
and petrified_oak slab, unobtainable, how to filter out those kinds?
dark_oak_ looks good, potted one again
birch_ looks good.

_log will give us all our base types:
"stripped" 41 log types,
TODO search with big or string on all those and we will have full list.
Should have approx 17*41 items for this marketType "wood"
total = 697
No slabs, too easy, doubles planks.
TODO furniture
With mods
dark_oak - 65 valhall, bridge, furniture
birch, spruce
65*41 = 2665 items, hmmm maybe too many?

List 41 log types:

Items matching "_log":
- biomesoplenty:dead_log
- biomesoplenty:fir_log
- biomesoplenty:hellbark_log
- biomesoplenty:jacaranda_log
- biomesoplenty:magic_log
- biomesoplenty:mahogany_log
- biomesoplenty:palm_log
- biomesoplenty:redwood_log
- biomesoplenty:umbran_log
- biomesoplenty:willow_log
- minecraft:acacia_log
- minecraft:birch_log
- minecraft:cherry_log
- minecraft:dark_oak_log
- minecraft:jungle_log
- minecraft:mangrove_log
- minecraft:oak_log
- minecraft:spruce_log

String:
biomesoplenty:dead_log,biomesoplenty:fir_log,biomesoplenty:hellbark_log,biomesoplenty:jacaranda_log,biomesoplenty:magic_log,biomesoplenty:mahogany_log,biomesoplenty:palm_log,biomesoplenty:redwood_log,biomesoplenty:umbran_log,biomesoplenty:willow_log,minecraft:acacia_log,minecraft:birch_log,minecraft:cherry_log,minecraft:dark_oak_log,minecraft:jungle_log,minecraft:mangrove_log,minecraft:oak_log,minecraft:spruce_log


// TODO burnable items only?? cherry0
dead_ r fir_ might overmatch??? fir is ok  dead catches coral. 
dead_log,fir_log,hellbark_log,jacaranda_log,magic_log,mahogany_log,palm_log,redwood_log,umbran_log,willow_log,acacia_log,birch_log,cherry_log,dark_oak_log,jungle_log,mangrove_log,oak_log,spruce_log

dead_,fir_,hellbark_,jacaranda_,magic_,mahogany_,palm_,redwood_,umbran_,willow_,acacia_,birch_,cherry_,dark_oak_,jungle_,mangrove_,oak_,spruce_

Now the Items:
[System] [CHAT] Items matching:
- alexsmobs:acacia_blossom
- aquaculture:acacia_fish_mount
- aquaculture:birch_fish_mount
- aquaculture:dark_oak_fish_mount
- aquaculture:jungle_fish_mount
- aquaculture:oak_fish_mount
- aquaculture:spruce_fish_mount
- biomesoplenty:dead_branch
- biomesoplenty:dead_button
- biomesoplenty:dead_door
- biomesoplenty:dead_fence
- biomesoplenty:dead_fence_gate
- biomesoplenty:dead_grass
- biomesoplenty:dead_hanging_sign
- biomesoplenty:dead_leaves
- biomesoplenty:dead_log
- biomesoplenty:dead_planks
- biomesoplenty:dead_pressure_plate
- biomesoplenty:dead_sapling
- biomesoplenty:dead_sign
- biomesoplenty:dead_stairs
- biomesoplenty:dead_trapdoor
- biomesoplenty:dead_wood
- biomesoplenty:fir_button
- biomesoplenty:fir_door
- biomesoplenty:fir_fence
- biomesoplenty:fir_fence_gate
- biomesoplenty:fir_hanging_sign
- biomesoplenty:fir_leaves
- biomesoplenty:fir_log
- biomesoplenty:fir_planks
- biomesoplenty:fir_pressure_plate
- biomesoplenty:fir_sapling
- biomesoplenty:fir_sign
- biomesoplenty:fir_stairs
- biomesoplenty:fir_trapdoor
- biomesoplenty:fir_wood
- biomesoplenty:flowering_oak_leaves
- biomesoplenty:flowering_oak_sapling
- biomesoplenty:hellbark_button
- biomesoplenty:hellbark_door
- biomesoplenty:hellbark_fence
- biomesoplenty:hellbark_fence_gate
- biomesoplenty:hellbark_hanging_sign
- biomesoplenty:hellbark_leaves
- biomesoplenty:hellbark_log
- biomesoplenty:hellbark_planks
- biomesoplenty:hellbark_pressure_plate
- biomesoplenty:hellbark_sapling
- biomesoplenty:hellbark_sign
- biomesoplenty:hellbark_stairs
- biomesoplenty:hellbark_trapdoor
- biomesoplenty:hellbark_wood
- biomesoplenty:jacaranda_button
- biomesoplenty:jacaranda_door
- biomesoplenty:jacaranda_fence
- biomesoplenty:jacaranda_fence_gate
- biomesoplenty:jacaranda_hanging_sign
- biomesoplenty:jacaranda_leaves
- biomesoplenty:jacaranda_log
- biomesoplenty:jacaranda_planks
- biomesoplenty:jacaranda_pressure_plate
- biomesoplenty:jacaranda_sapling
- biomesoplenty:jacaranda_sign
- biomesoplenty:jacaranda_stairs
- biomesoplenty:jacaranda_trapdoor
- biomesoplenty:jacaranda_wood
- biomesoplenty:magic_button
- biomesoplenty:magic_door
- biomesoplenty:magic_fence
- biomesoplenty:magic_fence_gate
- biomesoplenty:magic_hanging_sign
- biomesoplenty:magic_leaves
- biomesoplenty:magic_log
- biomesoplenty:magic_planks
- biomesoplenty:magic_pressure_plate
- biomesoplenty:magic_sapling
- biomesoplenty:magic_sign
- biomesoplenty:magic_stairs
- biomesoplenty:magic_trapdoor
- biomesoplenty:magic_wood
- biomesoplenty:mahogany_button
- biomesoplenty:mahogany_door
- biomesoplenty:mahogany_fence
- biomesoplenty:mahogany_fence_gate
- biomesoplenty:mahogany_hanging_sign
- biomesoplenty:mahogany_leaves
- biomesoplenty:mahogany_log
- biomesoplenty:mahogany_planks
- biomesoplenty:mahogany_pressure_plate
- biomesoplenty:mahogany_sapling
- biomesoplenty:mahogany_sign
- biomesoplenty:mahogany_stairs
- biomesoplenty:mahogany_trapdoor
- biomesoplenty:mahogany_wood
- biomesoplenty:palm_button
- biomesoplenty:palm_door
- biomesoplenty:palm_fence
- biomesoplenty:palm_fence_gate
- biomesoplenty:palm_hanging_sign
- biomesoplenty:palm_leaves
- biomesoplenty:palm_log
- biomesoplenty:palm_planks
- biomesoplenty:palm_pressure_plate
- biomesoplenty:palm_sapling
- biomesoplenty:palm_sign
- biomesoplenty:palm_stairs
- biomesoplenty:palm_trapdoor
- biomesoplenty:palm_wood
- biomesoplenty:rainbow_birch_leaves
- biomesoplenty:rainbow_birch_sapling
- biomesoplenty:redwood_button
- biomesoplenty:redwood_door
- biomesoplenty:redwood_fence
- biomesoplenty:redwood_fence_gate
- biomesoplenty:redwood_hanging_sign
- biomesoplenty:redwood_leaves
- biomesoplenty:redwood_log
- biomesoplenty:redwood_planks
- biomesoplenty:redwood_pressure_plate
- biomesoplenty:redwood_sapling
- biomesoplenty:redwood_sign
- biomesoplenty:redwood_stairs
- biomesoplenty:redwood_trapdoor
- biomesoplenty:redwood_wood
- biomesoplenty:stripped_dead_log
- biomesoplenty:stripped_dead_wood
- biomesoplenty:stripped_fir_log
- biomesoplenty:stripped_fir_wood
- biomesoplenty:stripped_hellbark_log
- biomesoplenty:stripped_hellbark_wood
- biomesoplenty:stripped_jacaranda_log
- biomesoplenty:stripped_jacaranda_wood
- biomesoplenty:stripped_magic_log
- biomesoplenty:stripped_magic_wood
- biomesoplenty:stripped_mahogany_log
- biomesoplenty:stripped_mahogany_wood
- biomesoplenty:stripped_palm_log
- biomesoplenty:stripped_palm_wood
- biomesoplenty:stripped_redwood_log
- biomesoplenty:stripped_redwood_wood
- biomesoplenty:stripped_umbran_log
- biomesoplenty:stripped_umbran_wood
- biomesoplenty:stripped_willow_log
- biomesoplenty:stripped_willow_wood
- biomesoplenty:umbran_button
- biomesoplenty:umbran_door
- biomesoplenty:umbran_fence
- biomesoplenty:umbran_fence_gate
- biomesoplenty:umbran_hanging_sign
- biomesoplenty:umbran_leaves
- biomesoplenty:umbran_log
- biomesoplenty:umbran_planks
- biomesoplenty:umbran_pressure_plate
- biomesoplenty:umbran_sapling
- biomesoplenty:umbran_sign
- biomesoplenty:umbran_stairs
- biomesoplenty:umbran_trapdoor
- biomesoplenty:umbran_wood
- biomesoplenty:willow_button
- biomesoplenty:willow_door
- biomesoplenty:willow_fence
- biomesoplenty:willow_fence_gate
- biomesoplenty:willow_hanging_sign
- biomesoplenty:willow_leaves
- biomesoplenty:willow_log
- biomesoplenty:willow_planks
- biomesoplenty:willow_pressure_plate
- biomesoplenty:willow_sapling
- biomesoplenty:willow_sign
- biomesoplenty:willow_stairs
- biomesoplenty:willow_trapdoor
- biomesoplenty:willow_vine
- biomesoplenty:willow_wood
- cfm:acacia_bedside_cabinet
- cfm:acacia_blinds
- cfm:acacia_cabinet
- cfm:acacia_chair
- cfm:acacia_coffee_table
- cfm:acacia_crate
- cfm:acacia_desk
- cfm:acacia_desk_cabinet
- cfm:acacia_hedge
- cfm:acacia_kitchen_counter
- cfm:acacia_kitchen_drawer
- cfm:acacia_kitchen_sink_dark
- cfm:acacia_kitchen_sink_light
- cfm:acacia_mail_box
- cfm:acacia_park_bench
- cfm:acacia_table
- cfm:acacia_upgraded_fence
- cfm:acacia_upgraded_gate
- cfm:birch_bedside_cabinet
- cfm:birch_blinds
- cfm:birch_cabinet
- cfm:birch_chair
- cfm:birch_coffee_table
- cfm:birch_crate
- cfm:birch_desk
- cfm:birch_desk_cabinet
- cfm:birch_hedge
- cfm:birch_kitchen_counter
- cfm:birch_kitchen_drawer
- cfm:birch_kitchen_sink_dark
- cfm:birch_kitchen_sink_light
- cfm:birch_mail_box
- cfm:birch_park_bench
- cfm:birch_table
- cfm:birch_upgraded_fence
- cfm:birch_upgraded_gate
- cfm:dark_oak_bedside_cabinet
- cfm:dark_oak_blinds
- cfm:dark_oak_cabinet
- cfm:dark_oak_chair
- cfm:dark_oak_coffee_table
- cfm:dark_oak_crate
- cfm:dark_oak_desk
- cfm:dark_oak_desk_cabinet
- cfm:dark_oak_hedge
- cfm:dark_oak_kitchen_counter
- cfm:dark_oak_kitchen_drawer
- cfm:dark_oak_kitchen_sink_dark
- cfm:dark_oak_kitchen_sink_light
- cfm:dark_oak_mail_box
- cfm:dark_oak_park_bench
- cfm:dark_oak_table
- cfm:dark_oak_upgraded_fence
- cfm:dark_oak_upgraded_gate
- cfm:jungle_bedside_cabinet
- cfm:jungle_blinds
- cfm:jungle_cabinet
- cfm:jungle_chair
- cfm:jungle_coffee_table
- cfm:jungle_crate
- cfm:jungle_desk
- cfm:jungle_desk_cabinet
- cfm:jungle_hedge
- cfm:jungle_kitchen_counter
- cfm:jungle_kitchen_drawer
- cfm:jungle_kitchen_sink_dark
- cfm:jungle_kitchen_sink_light
- cfm:jungle_mail_box
- cfm:jungle_park_bench
- cfm:jungle_table
- cfm:jungle_upgraded_fence
- cfm:jungle_upgraded_gate
- cfm:mangrove_bedside_cabinet
- cfm:mangrove_blinds
- cfm:mangrove_cabinet
- cfm:mangrove_chair
- cfm:mangrove_coffee_table
- cfm:mangrove_crate
- cfm:mangrove_desk
- cfm:mangrove_desk_cabinet
- cfm:mangrove_hedge
- cfm:mangrove_kitchen_counter
- cfm:mangrove_kitchen_drawer
- cfm:mangrove_kitchen_sink_dark
- cfm:mangrove_kitchen_sink_light
- cfm:mangrove_mail_box
- cfm:mangrove_park_bench
- cfm:mangrove_table
- cfm:mangrove_upgraded_fence
- cfm:mangrove_upgraded_gate
- cfm:oak_bedside_cabinet
- cfm:oak_blinds
- cfm:oak_cabinet
- cfm:oak_chair
- cfm:oak_coffee_table
- cfm:oak_crate
- cfm:oak_desk
- cfm:oak_desk_cabinet
- cfm:oak_hedge
- cfm:oak_kitchen_counter
- cfm:oak_kitchen_drawer
- cfm:oak_kitchen_sink_dark
- cfm:oak_kitchen_sink_light
- cfm:oak_mail_box
- cfm:oak_park_bench
- cfm:oak_table
- cfm:oak_upgraded_fence
- cfm:oak_upgraded_gate
- cfm:spruce_bedside_cabinet
- cfm:spruce_blinds
- cfm:spruce_cabinet
- cfm:spruce_chair
- cfm:spruce_coffee_table
- cfm:spruce_crate
- cfm:spruce_desk
- cfm:spruce_desk_cabinet
- cfm:spruce_hedge
- cfm:spruce_kitchen_counter
- cfm:spruce_kitchen_drawer
- cfm:spruce_kitchen_sink_dark
- cfm:spruce_kitchen_sink_light
- cfm:spruce_mail_box
- cfm:spruce_park_bench
- cfm:spruce_table
- cfm:spruce_upgraded_fence
- cfm:spruce_upgraded_gate
- cfm:stripped_acacia_bedside_cabinet
- cfm:stripped_acacia_blinds
- cfm:stripped_acacia_cabinet
- cfm:stripped_acacia_chair
- cfm:stripped_acacia_coffee_table
- cfm:stripped_acacia_crate
- cfm:stripped_acacia_desk
- cfm:stripped_acacia_desk_cabinet
- cfm:stripped_acacia_kitchen_counter
- cfm:stripped_acacia_kitchen_drawer
- cfm:stripped_acacia_kitchen_sink_dark
- cfm:stripped_acacia_kitchen_sink_light
- cfm:stripped_acacia_mail_box
- cfm:stripped_acacia_park_bench
- cfm:stripped_acacia_table
- cfm:stripped_acacia_upgraded_fence
- cfm:stripped_acacia_upgraded_gate
- cfm:stripped_birch_bedside_cabinet
- cfm:stripped_birch_blinds
- cfm:stripped_birch_cabinet
- cfm:stripped_birch_chair
- cfm:stripped_birch_coffee_table
- cfm:stripped_birch_crate
- cfm:stripped_birch_desk
- cfm:stripped_birch_desk_cabinet
- cfm:stripped_birch_kitchen_counter
- cfm:stripped_birch_kitchen_drawer
- cfm:stripped_birch_kitchen_sink_dark
- cfm:stripped_birch_kitchen_sink_light
- cfm:stripped_birch_mail_box
- cfm:stripped_birch_park_bench
- cfm:stripped_birch_table
- cfm:stripped_birch_upgraded_fence
- cfm:stripped_birch_upgraded_gate
- cfm:stripped_dark_oak_bedside_cabinet
- cfm:stripped_dark_oak_blinds
- cfm:stripped_dark_oak_cabinet
- cfm:stripped_dark_oak_chair
- cfm:stripped_dark_oak_coffee_table
- cfm:stripped_dark_oak_crate
- cfm:stripped_dark_oak_desk
- cfm:stripped_dark_oak_desk_cabinet
- cfm:stripped_dark_oak_kitchen_counter
- cfm:stripped_dark_oak_kitchen_drawer
- cfm:stripped_dark_oak_kitchen_sink_dark
- cfm:stripped_dark_oak_kitchen_sink_light
- cfm:stripped_dark_oak_mail_box
- cfm:stripped_dark_oak_park_bench
- cfm:stripped_dark_oak_table
- cfm:stripped_dark_oak_upgraded_fence
- cfm:stripped_dark_oak_upgraded_gate
- cfm:stripped_jungle_bedside_cabinet
- cfm:stripped_jungle_blinds
- cfm:stripped_jungle_cabinet
- cfm:stripped_jungle_chair
- cfm:stripped_jungle_coffee_table
- cfm:stripped_jungle_crate
- cfm:stripped_jungle_desk
- cfm:stripped_jungle_desk_cabinet
- cfm:stripped_jungle_kitchen_counter
- cfm:stripped_jungle_kitchen_drawer
- cfm:stripped_jungle_kitchen_sink_dark
- cfm:stripped_jungle_kitchen_sink_light
- cfm:stripped_jungle_mail_box
- cfm:stripped_jungle_park_bench
- cfm:stripped_jungle_table
- cfm:stripped_jungle_upgraded_fence
- cfm:stripped_jungle_upgraded_gate
- cfm:stripped_mangrove_bedside_cabinet
- cfm:stripped_mangrove_blinds
- cfm:stripped_mangrove_cabinet
- cfm:stripped_mangrove_chair
- cfm:stripped_mangrove_coffee_table
- cfm:stripped_mangrove_crate
- cfm:stripped_mangrove_desk
- cfm:stripped_mangrove_desk_cabinet
- cfm:stripped_mangrove_kitchen_counter
- cfm:stripped_mangrove_kitchen_drawer
- cfm:stripped_mangrove_kitchen_sink_dark
- cfm:stripped_mangrove_kitchen_sink_light
- cfm:stripped_mangrove_mail_box
- cfm:stripped_mangrove_park_bench
- cfm:stripped_mangrove_table
- cfm:stripped_mangrove_upgraded_fence
- cfm:stripped_mangrove_upgraded_gate
- cfm:stripped_oak_bedside_cabinet
- cfm:stripped_oak_blinds
- cfm:stripped_oak_cabinet
- cfm:stripped_oak_chair
- cfm:stripped_oak_coffee_table
- cfm:stripped_oak_crate
- cfm:stripped_oak_desk
- cfm:stripped_oak_desk_cabinet
- cfm:stripped_oak_kitchen_counter
- cfm:stripped_oak_kitchen_drawer
- cfm:stripped_oak_kitchen_sink_dark
- cfm:stripped_oak_kitchen_sink_light
- cfm:stripped_oak_mail_box
- cfm:stripped_oak_park_bench
- cfm:stripped_oak_table
- cfm:stripped_oak_upgraded_fence
- cfm:stripped_oak_upgraded_gate
- cfm:stripped_spruce_bedside_cabinet
- cfm:stripped_spruce_blinds
- cfm:stripped_spruce_cabinet
- cfm:stripped_spruce_chair
- cfm:stripped_spruce_coffee_table
- cfm:stripped_spruce_crate
- cfm:stripped_spruce_desk
- cfm:stripped_spruce_desk_cabinet
- cfm:stripped_spruce_kitchen_counter
- cfm:stripped_spruce_kitchen_drawer
- cfm:stripped_spruce_kitchen_sink_dark
- cfm:stripped_spruce_kitchen_sink_light
- cfm:stripped_spruce_mail_box
- cfm:stripped_spruce_park_bench
- cfm:stripped_spruce_table
- cfm:stripped_spruce_upgraded_fence
- cfm:stripped_spruce_upgraded_gate
- iceandfire:jungle_myrmex_cocoon
- iceandfire:myrmex_jungle_biolight
- iceandfire:myrmex_jungle_chitin
- iceandfire:myrmex_jungle_resin
- iceandfire:myrmex_jungle_resin_block
- iceandfire:myrmex_jungle_resin_glass
- macawsbridgesbop:dead_bridge_pier
- macawsbridgesbop:dead_log_bridge_middle
- macawsbridgesbop:dead_log_bridge_stair
- macawsbridgesbop:dead_rail_bridge
- macawsbridgesbop:dead_rope_bridge_stair
- macawsbridgesbop:fir_bridge_pier
- macawsbridgesbop:fir_log_bridge_middle
- macawsbridgesbop:fir_log_bridge_stair
- macawsbridgesbop:fir_rail_bridge
- macawsbridgesbop:fir_rope_bridge_stair
- macawsbridgesbop:hellbark_bridge_pier
- macawsbridgesbop:hellbark_log_bridge_middle
- macawsbridgesbop:hellbark_log_bridge_stair
- macawsbridgesbop:hellbark_rail_bridge
- macawsbridgesbop:hellbark_rope_bridge_stair
- macawsbridgesbop:jacaranda_bridge_pier
- macawsbridgesbop:jacaranda_log_bridge_middle
- macawsbridgesbop:jacaranda_log_bridge_stair
- macawsbridgesbop:jacaranda_rail_bridge
- macawsbridgesbop:jacaranda_rope_bridge_stair
- macawsbridgesbop:magic_bridge_pier
- macawsbridgesbop:magic_log_bridge_middle
- macawsbridgesbop:magic_log_bridge_stair
- macawsbridgesbop:magic_rail_bridge
- macawsbridgesbop:magic_rope_bridge_stair
- macawsbridgesbop:mahogany_bridge_pier
- macawsbridgesbop:mahogany_log_bridge_middle
- macawsbridgesbop:mahogany_log_bridge_stair
- macawsbridgesbop:mahogany_rail_bridge
- macawsbridgesbop:mahogany_rope_bridge_stair
- macawsbridgesbop:palm_bridge_pier
- macawsbridgesbop:palm_log_bridge_middle
- macawsbridgesbop:palm_log_bridge_stair
- macawsbridgesbop:palm_rail_bridge
- macawsbridgesbop:palm_rope_bridge_stair
- macawsbridgesbop:redwood_bridge_pier
- macawsbridgesbop:redwood_log_bridge_middle
- macawsbridgesbop:redwood_log_bridge_stair
- macawsbridgesbop:redwood_rail_bridge
- macawsbridgesbop:redwood_rope_bridge_stair
- macawsbridgesbop:rope_dead_bridge
- macawsbridgesbop:rope_fir_bridge
- macawsbridgesbop:rope_hellbark_bridge
- macawsbridgesbop:rope_jacaranda_bridge
- macawsbridgesbop:rope_magic_bridge
- macawsbridgesbop:rope_mahogany_bridge
- macawsbridgesbop:rope_palm_bridge
- macawsbridgesbop:rope_redwood_bridge
- macawsbridgesbop:rope_umbran_bridge
- macawsbridgesbop:rope_willow_bridge
- macawsbridgesbop:umbran_bridge_pier
- macawsbridgesbop:umbran_log_bridge_middle
- macawsbridgesbop:umbran_log_bridge_stair
- macawsbridgesbop:umbran_rail_bridge
- macawsbridgesbop:umbran_rope_bridge_stair
- macawsbridgesbop:willow_bridge_pier
- macawsbridgesbop:willow_log_bridge_middle
- macawsbridgesbop:willow_log_bridge_stair
- macawsbridgesbop:willow_rail_bridge
- macawsbridgesbop:willow_rope_bridge_stair
- mcwbridges:acacia_bridge_pier
- mcwbridges:acacia_log_bridge_middle
- mcwbridges:acacia_log_bridge_stair
- mcwbridges:acacia_rail_bridge
- mcwbridges:acacia_rope_bridge_stair
- mcwbridges:birch_bridge_pier
- mcwbridges:birch_log_bridge_middle
- mcwbridges:birch_log_bridge_stair
- mcwbridges:birch_rail_bridge
- mcwbridges:birch_rope_bridge_stair
- mcwbridges:cherry_bridge_pier
- mcwbridges:cherry_log_bridge_middle
- mcwbridges:cherry_log_bridge_stair
- mcwbridges:cherry_rail_bridge
- mcwbridges:cherry_rope_bridge_stair
- mcwbridges:dark_oak_bridge_pier
- mcwbridges:dark_oak_log_bridge_middle
- mcwbridges:dark_oak_log_bridge_stair
- mcwbridges:dark_oak_rail_bridge
- mcwbridges:dark_oak_rope_bridge_stair
- mcwbridges:jungle_bridge_pier
- mcwbridges:jungle_log_bridge_middle
- mcwbridges:jungle_log_bridge_stair
- mcwbridges:jungle_rail_bridge
- mcwbridges:jungle_rope_bridge_stair
- mcwbridges:mangrove_bridge_pier
- mcwbridges:mangrove_log_bridge_middle
- mcwbridges:mangrove_log_bridge_stair
- mcwbridges:mangrove_rail_bridge
- mcwbridges:mangrove_rope_bridge_stair
- mcwbridges:oak_bridge_pier
- mcwbridges:oak_log_bridge_middle
- mcwbridges:oak_log_bridge_stair
- mcwbridges:oak_rail_bridge
- mcwbridges:oak_rope_bridge_stair
- mcwbridges:rope_acacia_bridge
- mcwbridges:rope_birch_bridge
- mcwbridges:rope_cherry_bridge
- mcwbridges:rope_dark_oak_bridge
- mcwbridges:rope_jungle_bridge
- mcwbridges:rope_mangrove_bridge
- mcwbridges:rope_oak_bridge
- mcwbridges:rope_spruce_bridge
- mcwbridges:spruce_bridge_pier
- mcwbridges:spruce_log_bridge_middle
- mcwbridges:spruce_log_bridge_stair
- mcwbridges:spruce_rail_bridge
- mcwbridges:spruce_rope_bridge_stair
- minecraft:acacia_button
- minecraft:acacia_door
- minecraft:acacia_fence
- minecraft:acacia_fence_gate
- minecraft:acacia_hanging_sign
- minecraft:acacia_leaves
- minecraft:acacia_log
- minecraft:acacia_planks
- minecraft:acacia_pressure_plate
- minecraft:acacia_sapling
- minecraft:acacia_sign
- minecraft:acacia_stairs
- minecraft:acacia_trapdoor
- minecraft:acacia_wood
- minecraft:birch_button
- minecraft:birch_door
- minecraft:birch_fence
- minecraft:birch_fence_gate
- minecraft:birch_hanging_sign
- minecraft:birch_leaves
- minecraft:birch_log
- minecraft:birch_planks
- minecraft:birch_pressure_plate
- minecraft:birch_sapling
- minecraft:birch_sign
- minecraft:birch_stairs
- minecraft:birch_trapdoor
- minecraft:birch_wood
- minecraft:cherry_button
- minecraft:cherry_door
- minecraft:cherry_fence
- minecraft:cherry_fence_gate
- minecraft:cherry_hanging_sign
- minecraft:cherry_leaves
- minecraft:cherry_log
- minecraft:cherry_planks
- minecraft:cherry_pressure_plate
- minecraft:cherry_sapling
- minecraft:cherry_sign
- minecraft:cherry_stairs
- minecraft:cherry_trapdoor
- minecraft:cherry_wood
- minecraft:dark_oak_button
- minecraft:dark_oak_door
- minecraft:dark_oak_fence
- minecraft:dark_oak_fence_gate
- minecraft:dark_oak_hanging_sign
- minecraft:dark_oak_leaves
- minecraft:dark_oak_log
- minecraft:dark_oak_planks
- minecraft:dark_oak_pressure_plate
- minecraft:dark_oak_sapling
- minecraft:dark_oak_sign
- minecraft:dark_oak_stairs
- minecraft:dark_oak_trapdoor
- minecraft:dark_oak_wood
- minecraft:dead_brain_coral
- minecraft:dead_brain_coral_block
- minecraft:dead_brain_coral_fan
- minecraft:dead_bubble_coral
- minecraft:dead_bubble_coral_block
- minecraft:dead_bubble_coral_fan
- minecraft:dead_bush
- minecraft:dead_fire_coral
- minecraft:dead_fire_coral_block
- minecraft:dead_fire_coral_fan
- minecraft:dead_horn_coral
- minecraft:dead_horn_coral_block
- minecraft:dead_horn_coral_fan
- minecraft:dead_tube_coral
- minecraft:dead_tube_coral_block
- minecraft:dead_tube_coral_fan
- minecraft:jungle_button
- minecraft:jungle_door
- minecraft:jungle_fence
- minecraft:jungle_fence_gate
- minecraft:jungle_hanging_sign
- minecraft:jungle_leaves
- minecraft:jungle_log
- minecraft:jungle_planks
- minecraft:jungle_pressure_plate
- minecraft:jungle_sapling
- minecraft:jungle_sign
- minecraft:jungle_stairs
- minecraft:jungle_trapdoor
- minecraft:jungle_wood
- minecraft:mangrove_button
- minecraft:mangrove_door
- minecraft:mangrove_fence
- minecraft:mangrove_fence_gate
- minecraft:mangrove_hanging_sign
- minecraft:mangrove_leaves
- minecraft:mangrove_log
- minecraft:mangrove_planks
- minecraft:mangrove_pressure_plate
- minecraft:mangrove_propagule
- minecraft:mangrove_roots
- minecraft:mangrove_sign
- minecraft:mangrove_stairs
- minecraft:mangrove_trapdoor
- minecraft:mangrove_wood
- minecraft:muddy_mangrove_roots
- minecraft:oak_button
- minecraft:oak_door
- minecraft:oak_fence
- minecraft:oak_fence_gate
- minecraft:oak_hanging_sign
- minecraft:oak_leaves
- minecraft:oak_log
- minecraft:oak_planks
- minecraft:oak_pressure_plate
- minecraft:oak_sapling
- minecraft:oak_sign
- minecraft:oak_stairs
- minecraft:oak_trapdoor
- minecraft:oak_wood
- minecraft:spruce_button
- minecraft:spruce_door
- minecraft:spruce_fence
- minecraft:spruce_fence_gate
- minecraft:spruce_hanging_sign
- minecraft:spruce_leaves
- minecraft:spruce_log
- minecraft:spruce_planks
- minecraft:spruce_pressure_plate
- minecraft:spruce_sapling
- minecraft:spruce_sign
- minecraft:spruce_stairs
- minecraft:spruce_trapdoor
- minecraft:spruce_wood
- minecraft:stripped_acacia_log
- minecraft:stripped_acacia_wood
- minecraft:stripped_birch_log
- minecraft:stripped_birch_wood
- minecraft:stripped_cherry_log
- minecraft:stripped_cherry_wood
- minecraft:stripped_dark_oak_log
- minecraft:stripped_dark_oak_wood
- minecraft:stripped_jungle_log
- minecraft:stripped_jungle_wood
- minecraft:stripped_mangrove_log
- minecraft:stripped_mangrove_wood
- minecraft:stripped_oak_log
- minecraft:stripped_oak_wood
- minecraft:stripped_spruce_log
- minecraft:stripped_spruce_wood
- pamhc2trees:cherry_sapling
- valhelsia_structures:acacia_post
- valhelsia_structures:birch_post
- valhelsia_structures:bundled_acacia_posts
- valhelsia_structures:bundled_birch_posts
- valhelsia_structures:bundled_dark_oak_posts
- valhelsia_structures:bundled_jungle_posts
- valhelsia_structures:bundled_lapidified_jungle_posts
- valhelsia_structures:bundled_mangrove_posts
- valhelsia_structures:bundled_oak_posts
- valhelsia_structures:bundled_spruce_posts
- valhelsia_structures:bundled_stripped_acacia_posts
- valhelsia_structures:bundled_stripped_birch_posts
- valhelsia_structures:bundled_stripped_dark_oak_posts
- valhelsia_structures:bundled_stripped_jungle_posts
- valhelsia_structures:bundled_stripped_lapidified_jungle_posts
- valhelsia_structures:bundled_stripped_mangrove_posts
- valhelsia_structures:bundled_stripped_oak_posts
- valhelsia_structures:bundled_stripped_spruce_posts
- valhelsia_structures:cut_acacia_post
- valhelsia_structures:cut_birch_post
- valhelsia_structures:cut_dark_oak_post
- valhelsia_structures:cut_jungle_post
- valhelsia_structures:cut_lapidified_jungle_post
- valhelsia_structures:cut_mangrove_post
- valhelsia_structures:cut_oak_post
- valhelsia_structures:cut_spruce_post
- valhelsia_structures:cut_stripped_acacia_post
- valhelsia_structures:cut_stripped_birch_post
- valhelsia_structures:cut_stripped_dark_oak_post
- valhelsia_structures:cut_stripped_jungle_post
- valhelsia_structures:cut_stripped_lapidified_jungle_post
- valhelsia_structures:cut_stripped_mangrove_post
- valhelsia_structures:cut_stripped_oak_post
- valhelsia_structures:cut_stripped_spruce_post
- valhelsia_structures:dark_oak_post
- valhelsia_structures:jungle_post
- valhelsia_structures:lapidified_jungle_button
- valhelsia_structures:lapidified_jungle_fence
- valhelsia_structures:lapidified_jungle_fence_gate
- valhelsia_structures:lapidified_jungle_log
- valhelsia_structures:lapidified_jungle_planks
- valhelsia_structures:lapidified_jungle_post
- valhelsia_structures:lapidified_jungle_pressure_plate
- valhelsia_structures:lapidified_jungle_stairs
- valhelsia_structures:lapidified_jungle_wood
- valhelsia_structures:mangrove_post
- valhelsia_structures:oak_post
- valhelsia_structures:spruce_post
- valhelsia_structures:stripped_acacia_post
- valhelsia_structures:stripped_birch_post
- valhelsia_structures:stripped_dark_oak_post
- valhelsia_structures:stripped_jungle_post
- valhelsia_structures:stripped_lapidified_jungle_post
- valhelsia_structures:stripped_mangrove_post
- valhelsia_structures:stripped_oak_post
- valhelsia_structures:stripped_spruce_post
- villagersplus:acacia_horticulturist_table
- villagersplus:birch_horticulturist_table
- villagersplus:cherry_horticulturist_table
- villagersplus:dark_oak_horticulturist_table
- villagersplus:jungle_horticulturist_table
- villagersplus:mangrove_horticulturist_table
- villagersplus:oak_horticulturist_table
- villagersplus:spruce_horticulturist_table
Total items found: 744

550 items after that





#####################################################################################
Food Market Type

PAMS HARVESTCRAFT
edible only.
from 2 or 4 mods?  some extra jellies and things...
"pams" 1420 items.

4 mods
pamhc2crops - some - seeds and crops, has 20 baked, roasted, and tea items, grab those.
pamhc2foodcore - most - 171 has flour, dough, water, milk, salt, stock, vinegar, yogurt, and cookware items (these dont stack so will filter out)
pamhc2foodextended - most - 871 has cornmeal, pepper, and rest recipes, grab all for now.
pamhc2trees - some?  sandwiches jelly? - none - no recipes.
regular minecraft foods - dont forget.
any other mods - dont forget.
Filter by edible instead?
TODO remove word "item" off of each
Get game friendly name off each? or use code?
total = 871 + 171 + 20 = 1062
TODO add egg?



#####################################################################################
Stone Market Types:

1-5	search stone, plus?	 (blackstone, sandstone)
"stone" 101 items (no slabs), has stonecutter too, thats ok.
Shows: * for stone, sandstone, redstone, blackstone, mossy, glowstone, end_stone, brimstone.
(minus infested ones)
6-7	bricks? all types?  47
8	diorite, granite, andesite 5, 5,
10	nether - 21 ok
11	redstone? - done above
12	deepslate - 26
13	silver?  for now? - hmmm maybe not
14	clay: blocks only - 1 only
15	terracotta - 33
16	tuff (only one) "minecraft:tuff"	- 1 only
	(skip lapis)	half chance only
	Any weird special list?
17	quartz - 13
18	brimstone - done above
19	calcite - 1 only
20	basalt - 3
21	sands - a bunch -sandwich
22	glass - 36
23	amethyst - 2 only - _bud minus a couple blocks,
24	magma_block - 1
25	obsidian - 2
26 - mud - minus mud cake.
500ish?


#####################################################################################
General Market Type:
all flowers, dyes
all concretes, concrete powders
all non wooden furniture
all copper items
all candles
all banners
iron items (how)
NO wood items, stone items, or fooditems.
NO weapons, armor
MOB drops - some - easier ones.
    all wools
    arrows
    feathers
    slime balls
    bones
    string
    gunpowder
Prismarine items - #
snow block, ice block, packed ice block.
red mushroom, brown mushroom
cactus
skulk block
soul torch, soul lantern
book, paper, bookshelf
flower pot
TODO check other mods too.
sleeping bags
glazed jars


#####################################################################################
TODO maybe make a marketadmin command, list all those in a diff file....
May have a few helpers.






 */