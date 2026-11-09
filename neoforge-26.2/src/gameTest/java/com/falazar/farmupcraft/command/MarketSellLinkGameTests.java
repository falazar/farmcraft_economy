package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class MarketSellLinkGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("market_sell_link"), MarketSellLinkGameTests::verify));
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("market_wood_import"), MarketSellLinkGameTests::woodImport));
    }

    private static void woodImport(GameTestHelper helper) {
        var level = helper.getLevel();
        var goods = ModEvents.getGoodsDataDatabase(level);
        var previous = new java.util.HashMap<String, GoodsData>();
        for (String key : goods.getKeys()) previous.put(key, goods.getData(key));
        List<String> additions = List.of("minecraft:bamboo_planks", "minecraft:cherry_boat",
                "minecraft:cherry_chest_boat", "minecraft:mangrove_boat", "minecraft:mangrove_chest_boat",
                "minecraft:pale_oak_door", "minecraft:pale_oak_fence", "minecraft:pale_oak_fence_gate",
                "minecraft:pale_oak_leaves", "minecraft:pale_oak_log", "minecraft:pale_oak_planks",
                "minecraft:pale_oak_sapling", "minecraft:pale_oak_stairs", "minecraft:pale_oak_trapdoor",
                "minecraft:pale_oak_wood", "minecraft:stripped_pale_oak_log", "minecraft:stripped_pale_oak_wood");
        try {
            goods.removeDataBatch(additions);
            String sentinelId = "minecraft:oak_log";
            var sentinel = new GoodsData(sentinelId, 37, 81, "common", true, "wood", "");
            goods.putData(sentinelId, sentinel);
            var source = level.getServer().createCommandSourceStack();
            helper.assertTrue(MarketCommand.importMarketItemsTXT(source, "wood"), "Wood catalog import succeeds");
            for (String id : additions) {
                helper.assertTrue(MarketCommand.validMarketItem(id), "New wood item is registered: " + id);
                var entry = goods.getData(id);
                helper.assertTrue(entry != null, "New wood item is imported: " + id);
                helper.assertValueEqual(entry.getCost(), 5, "Imported wood uses existing default price");
                helper.assertValueEqual(entry.getMarketType(), "wood", "Imported item uses wood category");
                helper.assertTrue(!entry.isActive(), "New entries start inactive for normal rotation");
            }
            helper.assertTrue(goods.getData(sentinelId) == sentinel, "Import preserves existing goods object");
            helper.assertValueEqual(sentinel.getCost(), 37, "Import preserves existing price");
            helper.assertTrue(sentinel.isActive(), "Import preserves active status");
            int count = goods.getValues().size();
            helper.assertTrue(MarketCommand.importMarketItemsTXT(source, "wood"), "Repeated import succeeds");
            helper.assertValueEqual(goods.getValues().size(), count, "Repeated imports do not duplicate entries");
            helper.assertTrue(goods.getValues().stream().allMatch(good -> MarketCommand.validMarketItem(good.getItemId())),
                    "Missing optional mod items do not become invalid market entries");
        } finally {
            goods.removeDataBatch(new java.util.ArrayList<>(goods.getKeys()));
            previous.forEach(goods::putData);
        }
        helper.succeed();
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "market-link-test"));
        var goods = ModEvents.getGoodsDataDatabase(level);
        var players = ModEvents.getPlayerDatabase(level);
        String id = "minecraft:apple";
        var previous = goods.getData(id);
        var previousPlayer = players.getData(player.getUUID());
        try {
            goods.putData(id, new GoodsData(id, 5, 31, "common", true, "food", ""));
            players.putData(player.getUUID(), new PlayerData(player.getId(), player.getUUID(), null, 100));
            var source = player.createCommandSourceStack();
            var emptyLine = MarketCommand.buildItemLine(source, Items.APPLE, id, 5, player);
            helper.assertTrue(!emptyLine.getString().contains(" - sell"), "No sell link without inventory items");
            player.getInventory().setItem(0, new ItemStack(Items.APPLE, 1));
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.APPLE, 1));
            player.getInventory().setItem(1, new ItemStack(Items.CARROT, 3));
            verifyView(helper, source, player, 100, 5, 2);
            var line = MarketCommand.buildItemLine(source, Items.APPLE, id, 5, player);
            helper.assertTrue(line.getString().contains("(2 cnt) - sell"), "Link count includes offhand");
            var button = line.getSiblings().stream().filter(c -> c.getString().equals(" - sell")).findFirst().orElseThrow();
            helper.assertTrue(button.getStyle().getClickEvent() instanceof ClickEvent.RunCommand
                    && button.getStyle().isUnderlined() && button.getStyle().getHoverEvent() != null,
                    "Sell button must have its own click and tooltip");
            String command = ((ClickEvent.RunCommand) button.getStyle().getClickEvent()).command();
            helper.assertTrue(command.equals("/market sell item \"minecraft:apple\""), "Namespaced item is quoted");
            var dispatcher = level.getServer().getCommands().getDispatcher();
            for (var entry : com.falazar.farmupcraft.util.GeneralMenu.ENTRIES) {
                var parsed = dispatcher.parse(entry.command(), source);
                helper.assertTrue(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead()
                                && parsed.getContext().getCommand() != null,
                        "General-menu shortcut resolves to executable command: " + entry.command());
            }
            var preferences = players.getData(player.getUUID());
            helper.assertValueEqual(preferences.getMarketMinimumPrice(), 10, "New player high-price default");
            helper.assertValueEqual(dispatcher.execute("market show high 23", source), 1, "Set high-price limit");
            helper.assertValueEqual(preferences.getMarketMinimumPrice(), 23, "Explicit high limit remembered");
            var encodedPlayer = PlayerData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, preferences)
                    .getOrThrow();
            var restoredPlayer = PlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encodedPlayer)
                    .getOrThrow();
            players.putData(player.getUUID(), restoredPlayer);
            helper.assertValueEqual(dispatcher.execute("market show high", source), 1, "Reopen with saved limit");
            helper.assertValueEqual(restoredPlayer.getMarketMinimumPrice(), 23, "Saved limit survives reopen and reload");
            helper.assertValueEqual(dispatcher.execute("market show", source), 1, "Open general market");
            helper.assertValueEqual(dispatcher.execute("market show high", source), 1, "Return to high tab");
            helper.assertValueEqual(restoredPlayer.getMarketMinimumPrice(), 23, "General tab does not reset high limit");
            helper.assertValueEqual(dispatcher.execute("market favorite \"minecraft:apple\" true", source), 1,
                    "Favorite add command succeeds");
            helper.assertValueEqual(dispatcher.execute("market favorite \"minecraft:apple\" true", source), 1,
                    "Repeated add is idempotent");
            helper.assertValueEqual(restoredPlayer.getMarketFavorites().size(), 1, "Favorites have no duplicates");
            var favoriteView = MarketCommand.buildMarketView(source, player, "favorites", 100, 100);
            helper.assertTrue(favoriteView.entries().size() == 1 && favoriteView.entries().getFirst().favorite(),
                    "Favorites include saved active items regardless of high-price threshold");
            verifyView(helper, source, player, 100, 5, 2);
            var savedFavorites = PlayerData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, restoredPlayer)
                    .getOrThrow();
            var loadedFavorites = PlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, savedFavorites)
                    .getOrThrow();
            helper.assertTrue(loadedFavorites.isMarketFavorite(id), "Favorites survive save and reload");
            var otherPlayer = new PlayerData(99, UUID.randomUUID(), null, 0);
            helper.assertTrue(!otherPlayer.isMarketFavorite(id), "Favorites are private per player");
            goods.getData(id).setActive(false);
            helper.assertTrue(MarketCommand.buildMarketView(source, player, "favorites", 1, 100).entries().isEmpty(),
                    "Inactive favorites are hidden");
            goods.getData(id).setActive(true);
            helper.assertValueEqual(dispatcher.execute("market favorite \"minecraft:apple\" false", source), 1,
                    "Favorite remove command succeeds");
            helper.assertTrue(MarketCommand.buildMarketView(source, player, "favorites", 1, 100).entries().isEmpty(),
                    "Removed favorite disappears");
            helper.assertValueEqual(dispatcher.execute("market show favorites", source), 1,
                    "Empty Favorites opens without trying to seed a market category");
            helper.assertValueEqual(dispatcher.execute("market favorite \"minecraft:not_a_real_item\" true", source), 0,
                    "Unknown favorite rejected");
            encodedPlayer.getAsJsonObject().remove("market_minimum_price");
            helper.assertValueEqual(PlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encodedPlayer)
                    .getOrThrow().getMarketMinimumPrice(), 10, "Legacy saves default to 10");
            helper.assertTrue(PlayerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encodedPlayer)
                    .getOrThrow().getMarketFavorites().isEmpty(), "Legacy saves default to no favorites");
            helper.assertValueEqual(dispatcher.execute(command.substring(1), source), 1, "Click executes ordinary command");
            helper.assertValueEqual(players.getData(player.getUUID()).getCoins(), 109, "Sale credits exactly once");
            var receiptBuffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                for (int coins : new int[]{0, 9, Integer.MAX_VALUE}) {
                    receiptBuffer.clear();
                    var receipt = new com.falazar.farmupcraft.database.message.MarketSaleReceiptPacket(coins);
                    com.falazar.farmupcraft.database.message.MarketSaleReceiptPacket.STREAM_CODEC.encode(receiptBuffer, receipt);
                    helper.assertValueEqual(
                            com.falazar.farmupcraft.database.message.MarketSaleReceiptPacket.STREAM_CODEC.decode(receiptBuffer).coins(),
                            coins, "Receipt preserves exact server payout including zero and large sales");
                }
                receiptBuffer.clear();
                receiptBuffer.writeVarInt(-1);
                boolean rejected = false;
                try {
                    com.falazar.farmupcraft.database.message.MarketSaleReceiptPacket.STREAM_CODEC.decode(receiptBuffer);
                } catch (io.netty.handler.codec.DecoderException error) {
                    rejected = true;
                }
                helper.assertTrue(rejected, "Negative receipt rejected");
            } finally {
                receiptBuffer.release();
            }
            helper.assertValueEqual(MarketCommand.countSaleItems(player.getInventory(), Items.APPLE), 0, "All apples sold");
            helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 3, "Other items remain");
            helper.assertValueEqual(goods.getData(id).getCost(), 4, "Existing price-drop behavior preserved");
            helper.assertValueEqual(goods.getData(id).getAmountSold(), 33, "Existing sold counter preserved");
            verifyView(helper, source, player, 109, 4, 0);
            for (String category : com.falazar.farmupcraft.util.MarketView.CATEGORIES) {
                var parsed = dispatcher.parse("market show " + category, source);
                helper.assertTrue(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead(),
                        "show category registered: " + category);
            }
            helper.assertValueEqual(dispatcher.execute("market show", level.getServer().createCommandSourceStack()),
                    0, "Graphical market rejects console");
            helper.assertValueEqual(dispatcher.execute(command.substring(1), source), 0, "Stale empty-inventory click rejected");
            player.getInventory().setItem(0, new ItemStack(Items.APPLE));
            goods.getData(id).setActive(false);
            helper.assertTrue(MarketCommand.buildMarketView(source, player, "food", 10, 109).entries().stream()
                    .noneMatch(entry -> entry.itemId().equals(id)), "GUI excludes inactive goods");
            helper.assertValueEqual(dispatcher.execute(command.substring(1), source), 0, "Inactive goods rejected");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "Rejected sale keeps inventory");
            goods.getData(id).setActive(true);
            players.getData(player.getUUID()).setCoins(Integer.MAX_VALUE);
            helper.assertValueEqual(dispatcher.execute(command.substring(1), source), 0, "Wallet overflow rejected");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 1, "Overflow preserves inventory");
            helper.assertValueEqual(goods.getData(id).getAmountSold(), 33, "Overflow preserves market");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalStateException("Market sell link failed", error);
        } finally {
            if (previous == null) goods.removeDataBatch(List.of(id));
            else goods.putData(id, previous);
            if (previousPlayer == null) players.removeDataBatch(List.of(player.getUUID()));
            else players.putData(player.getUUID(), previousPlayer);
            player.getInventory().clearContent();
            player.discard();
        }
        helper.succeed();
    }

    private static void verifyView(GameTestHelper helper, net.minecraft.commands.CommandSourceStack source,
            net.minecraft.server.level.ServerPlayer player, int coins, int price, int count) {
        var view = MarketCommand.buildMarketView(source, player, "food", 10, coins);
        var apple = view.entries().stream().filter(entry -> entry.itemId().equals("minecraft:apple"))
                .findFirst().orElseThrow();
        helper.assertValueEqual(apple.price(), price, "GUI reflects current server price");
        helper.assertValueEqual(apple.count(), count, "GUI reflects inventory including offhand after sales");
        helper.assertValueEqual(view.coins(), coins, "GUI reflects server wallet");
        for (int i = 1; i < view.entries().size(); i++) {
            helper.assertTrue(view.entries().get(i - 1).price() >= view.entries().get(i).price(),
                    "GUI prices strictly ordered descending");
        }
        var high = MarketCommand.buildMarketView(source, player, "high", price + 1, coins);
        helper.assertTrue(high.entries().stream().allMatch(entry -> entry.price() >= price + 1),
                "High category honors minimum price");
        var packet = new com.falazar.farmupcraft.database.message.MarketViewPacket(view);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            helper.assertTrue(new com.falazar.farmupcraft.database.message.MarketViewPacket(buffer).view().equals(view),
                    "Market payload round-trips exactly");
            buffer.clear();
            buffer.writeUtf("food", 16);
            buffer.writeVarInt(10);
            buffer.writeInt(coins);
            buffer.writeVarInt(com.falazar.farmupcraft.util.MarketView.MAX_ENTRIES + 1);
            boolean rejected = false;
            try {
                new com.falazar.farmupcraft.database.message.MarketViewPacket(buffer);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Market payload rejects oversized lists before allocation");
        } finally {
            buffer.release();
        }
    }
}
