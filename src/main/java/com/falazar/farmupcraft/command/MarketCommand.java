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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
                    return showMarketList(context, "general");
                }))
                .then(Commands.literal("food").executes(context -> {
                    return showMarketList(context, "food");
                }))
                .then(Commands.literal("wood").executes(context -> {
                    return showMarketList(context, "wood");
                }))
                .then(Commands.literal("stone").executes(context -> {
                    return showMarketList(context, "stone");
                }));
        builder.then(showBuilder);

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


        // Register the main "market" command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showMarketInfo(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Build a response message
            MutableComponent response = Component.literal("Market info options: \n");
            response = response.append(Component.literal("  /market show general \n"));
            response = response.append(Component.literal("  /market show food\n"));
            response = response.append(Component.literal("  /market show wood"));
            response = response.append(Component.literal("  /market show stone \n"));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Show market info Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int showMarketList(CommandContext<CommandSourceStack> context, String type) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // TODO loop over all items and prices in text.

            MutableComponent response = Component.literal("Market " + type + " items: \n");
            response = response.append(Component.literal("item1: 10 coins\n"));
            response = response.append(Component.literal("item2: 10 coins\n"));
            response = response.append(Component.literal("item3: 10 coins\n"));
            response = response.append(Component.literal("item4: 10 coins\n"));
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Show Market List Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // General searchability method to find items.
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

            // Search for items containing the keyword
            List<String> matchingItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains(keyword.toLowerCase()))
                    .filter(item -> !ForgeRegistries.ITEMS.getKey(item).toString().toLowerCase().contains("_slab")) // Exclude slabs
                    .filter(item -> item.getMaxStackSize() > 1) // Exclude unstackable items
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


PAMS HARVESTCRAFT
edible only.
from 2 or 4 mods?  some extra jellies and things...
"pams" 1420 items.

4 mods
pamhc2crops - none - seeds and crops, has 20 baked, roasted, and tea items, grab those.
pamhc2foodcore - most - 171 has flour, dough, water, milk, salt, stock, vinegar, yogurt, and cookware items (these dont stack so will filter out)
pamhc2foodextended - most - 871 has cornmeal, pepper, and rest recipes, grab all for now.
pamhc2trees - some?  sandwiches jelly? - none - no recipes.
regular minecraft foods - dont forget.
any other mods - dont forget.
Filter by edible instead?
TODO remove word "item" off of each
Get game friendly name off each? or use code?
total = 871 + 171 + 20 = 1062
TODO add egg


Stone Types:
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


General Type:
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


TODO maybe make a marketadmin command, list all those in a diff file....
May have a few helpers.






 */