package com.falazar.farmupcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class MarketCommandTest {
    @Test
    void recipeLinksUseTextTooltipsWithoutChangingCommandsOrInventoryHighlight(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        for (boolean inInventory : new boolean[]{false, true}) {
            for (String itemId : List.of("minecraft:lantern", "biomesoplenty:test_item")) {
                var style = MarketCommand.itemLineStyle(Items.LANTERN, itemId, inInventory, true);
                var click = assertInstanceOf(ClickEvent.RunCommand.class, style.getClickEvent());
                assertEquals("/frecipe " + itemId, click.command());
                var hover = assertInstanceOf(HoverEvent.ShowText.class, style.getHoverEvent());
                assertEquals("Click to view recipe in JEI\n" + itemId, hover.value().getString());
                assertTrue(style.isUnderlined());
                assertEquals(TextColor.fromLegacyFormat(inInventory ? ChatFormatting.GREEN : ChatFormatting.AQUA),
                        style.getColor());
            }
        }
    }

    @Test
    void itemsWithoutRecipesKeepItemTooltipsAndHaveNoRecipeClick(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        for (boolean inInventory : new boolean[]{false, true}) {
            var style = MarketCommand.itemLineStyle(Items.DIRT, "minecraft:dirt", inInventory, false);
            assertInstanceOf(HoverEvent.ShowItem.class, style.getHoverEvent());
            assertNull(style.getClickEvent());
            assertFalse(style.isUnderlined());
            assertEquals(TextColor.fromLegacyFormat(inInventory ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                    style.getColor());
        }
    }

    @Test
    void quotesRespectLifetimeBoundariesAndZeroFloor() {
        assertEquals(new MarketCommand.SaleQuote(0, 5), MarketCommand.quoteSale(5, 31, 0));
        assertEquals(new MarketCommand.SaleQuote(155, 5), MarketCommand.quoteSale(5, 0, 31));
        assertEquals(new MarketCommand.SaleQuote(160, 4), MarketCommand.quoteSale(5, 0, 32));
        assertEquals(new MarketCommand.SaleQuote(9, 4), MarketCommand.quoteSale(5, 31, 2));
        assertEquals(new MarketCommand.SaleQuote(288, 3), MarketCommand.quoteSale(5, 0, 64));
        assertEquals(new MarketCommand.SaleQuote(32, 0), MarketCommand.quoteSale(1, 0, Integer.MAX_VALUE));
        assertEquals(new MarketCommand.SaleQuote(0, 0), MarketCommand.quoteSale(0, 31, Integer.MAX_VALUE));
        assertEquals(new MarketCommand.SaleQuote(Integer.MAX_VALUE, Integer.MAX_VALUE),
                MarketCommand.quoteSale(Integer.MAX_VALUE, 0, 1));
        assertThrows(ArithmeticException.class, () -> MarketCommand.quoteSale(Integer.MAX_VALUE, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> MarketCommand.quoteSale(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> MarketCommand.quoteSale(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> MarketCommand.quoteSale(1, 0, -1));
    }

    @Test
    void closedFormMatchesPerItemEconomyForEveryBoundaryOffset() {
        for (int initialCost = 0; initialCost <= 12; initialCost++) {
            for (int amountSold = 0; amountSold < 96; amountSold++) {
                for (int count : new int[]{0, 1, 2, 31, 32, 33, 63, 64, 65, 512}) {
                    int cost = initialCost;
                    int coins = 0;
                    for (int i = 0; i < count; i++) {
                        coins += cost;
                        if ((amountSold + i + 1) % 32 == 0) cost = Math.max(0, cost - 1);
                    }
                    assertEquals(new MarketCommand.SaleQuote(coins, cost),
                            MarketCommand.quoteSale(initialCost, amountSold, count),
                            "cost=" + initialCost + ", sold=" + amountSold + ", count=" + count);
                }
            }
        }
    }

    @Test
    void villagerCostsAreCheckedInsteadOfWrapping() {
        assertEquals(0, MarketCommand.villagerEggCost(0));
        assertEquals(200, MarketCommand.villagerEggCost(1));
        assertEquals(2000, MarketCommand.villagerEggCost(10));
        assertThrows(IllegalArgumentException.class, () -> MarketCommand.villagerEggCost(-1));
        assertThrows(ArithmeticException.class, () -> MarketCommand.villagerEggCost(Integer.MAX_VALUE));
    }

    @Test
    void activeStatusRetainsEveryLegacyAliasAndRejectsUnknownInput() {
        for (String value : List.of("true", "1", "YES", "Active"))
            assertEquals(Boolean.TRUE, MarketCommand.parseActiveStatus(value));
        for (String value : List.of("false", "0", "NO", "Inactive"))
            assertEquals(Boolean.FALSE, MarketCommand.parseActiveStatus(value));
        assertNull(MarketCommand.parseActiveStatus("maybe"));
    }

    @Test
    void dailyRotationRequiresEnoughReplacementsBeforeRemovingAnyActiveItem() {
        assertEquals(0, MarketCommand.rotationCount(0, 0));
        assertEquals(0, MarketCommand.rotationCount(4, 0));
        assertEquals(1, MarketCommand.rotationCount(5, 1));
        assertEquals(3, MarketCommand.rotationCount(19, 3));
        assertThrows(IllegalStateException.class, () -> MarketCommand.rotationCount(5, 0));
        assertThrows(IllegalStateException.class, () -> MarketCommand.rotationCount(20, 3));
        assertThrows(IllegalArgumentException.class, () -> MarketCommand.rotationCount(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> MarketCommand.rotationCount(0, -1));
    }

    @Test
    void saleRemovalHonorsCountOffhandAndItemComponentsWithoutTouchingArmor(MinecraftServer server) {
        assertNull(server.overworld());
        EntityEquipment equipment = new EntityEquipment();
        Inventory inventory = new Inventory(null, equipment);
        ItemStack named = new ItemStack(Items.STONE, 7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named stone"));
        named.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.UNBREAKING), 2);
        inventory.setItem(0, named);
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.STONE, 4));
        equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.STONE, 3));
        inventory.setItem(1, new ItemStack(Items.DIRT, 6));
        assertEquals(11, MarketCommand.countSaleItems(inventory, Items.STONE));

        MarketCommand.removeItem(inventory, new ItemStack(Items.STONE), 3);
        assertEquals(4, named.getCount());
        assertEquals("Named stone", named.get(DataComponents.CUSTOM_NAME).getString());
        assertTrue(named.isEnchanted());
        assertEquals(8, MarketCommand.countSaleItems(inventory, Items.STONE));
        assertThrows(IllegalArgumentException.class,
                () -> MarketCommand.removeItem(inventory, new ItemStack(Items.STONE), 9));
        assertEquals(8, MarketCommand.countSaleItems(inventory, Items.STONE));

        MarketCommand.removeItem(inventory, new ItemStack(Items.STONE), 6);
        assertEquals(2, inventory.getItem(Inventory.SLOT_OFFHAND).getCount());
        assertEquals(3, equipment.get(EquipmentSlot.HEAD).getCount());
        assertEquals(6, inventory.getItem(1).getCount());
        assertEquals(2, inventory.getTimesChanged());
    }

    @Test
    void itemIdentifiersDoNotSilentlyResolveToAir(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        assertTrue(MarketCommand.validMarketItem("minecraft:stone"));
        assertFalse(MarketCommand.validMarketItem("minecraft:air"));
        assertFalse(MarketCommand.validMarketItem("farmupcraft:missing_item"));
        assertFalse(MarketCommand.validMarketItem("Not an identifier!"));
    }

    @Test
    void everyMarketBranchRegistersWithModernGamemasterPermissions(MinecraftServer server) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        MarketCommand.register(dispatcher);
        Feedback feedback = new Feedback();
        CommandSourceStack ordinary = source(server, PermissionSet.NO_PERMISSIONS, feedback);
        CommandSourceStack moderator = source(server, LevelBasedPermissionSet.MODERATOR, feedback);
        CommandSourceStack admin = source(server, LevelBasedPermissionSet.GAMEMASTER, feedback);
        var root = dispatcher.getRoot().getChild("market");
        assertFalse(root.getChild("admin").canUse(ordinary));
        assertFalse(root.getChild("admin").canUse(moderator));
        assertTrue(root.getChild("admin").canUse(admin));
        assertTrue(root.getChild("show").canUse(ordinary));
        for (String command : List.of(
                "market info", "market show general", "market show food", "market show wood", "market show stone",
                "market show high", "market show high 10", "market sell general", "market sell food",
                "market sell wood", "market sell stone", "market buy villager", "market admin rundaily",
                "market admin raiseprices food", "market admin importtxt stone", "market admin importall",
                "market admin setactive \"minecraft:stone\" active", "market admin setcost \"minecraft:stone\" 10",
                "market admin clearall forreal", "market admin addrandom food", "market admin find oak",
                "market admin findfoods", "market admin findwood", "market admin clearair",
                "market admin restoreremoved")) {
            var parsed = dispatcher.parse(command, admin);
            assertTrue(parsed.getExceptions().isEmpty(), command);
            assertEquals("", parsed.getReader().getRemaining(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        assertFalse(dispatcher.parse("market admin setcost \"minecraft:stone\" -1", admin)
                .getExceptions().isEmpty());
        assertFalse(dispatcher.parse("market show high 0", ordinary).getExceptions().isEmpty());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("market admin importall", ordinary));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("market admin importall", moderator));
        assertEquals(0, dispatcher.execute("market buy villager", ordinary));
        assertTrue(feedback.messages.contains("Only players can use this command."));
    }

    @Test
    void allStartupMarketListsArePackagedAndContainVanillaItems(MinecraftServer server) throws Exception {
        assertNotNull(server.registryAccess());
        for (String type : List.of("food", "wood", "stone", "general")) {
            String path = "data/farmupcraft/farmupcraft/market/market_" + type + "_items.txt";
            try (var stream = MarketCommand.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                String contents = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(contents.lines().map(String::trim).anyMatch(MarketCommand::validMarketItem), path);
            }
        }
    }

    static CommandSourceStack source(MinecraftServer server, PermissionSet permissions, Feedback feedback) {
        return new CommandSourceStack(feedback, Vec3.ZERO, Vec2.ZERO, null, permissions,
                "test", Component.literal("test"), server, null);
    }

    static final class Feedback implements CommandSource {
        final List<String> messages = new ArrayList<>();

        @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
