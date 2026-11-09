package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.MarketView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;

public final class MarketScreen extends Screen {
    private MarketView view;
    private int page;
    private int rows;
    private int left;
    private int top;
    private int panelWidth;
    private boolean waiting;
    private int waitTicks;
    private int receiptTicks;
    private int receiptCoins;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance();

    private MarketScreen(MarketView view) {
        super(Component.literal("FarmUpcraft Village Market"));
        this.view = view;
    }

    public static void open(MarketView view) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MarketScreen screen) {
            if (!screen.view.category().equals(view.category())
                    || screen.view.minimumPrice() != view.minimumPrice()) screen.page = 0;
            screen.view = view;
            screen.waiting = false;
            screen.rebuildWidgets();
        } else {
            minecraft.setScreen(new MarketScreen(view));
        }

    }

    public static void showSaleReceipt(int coins) {
        if (Minecraft.getInstance().screen instanceof MarketScreen screen) {
            screen.receiptCoins = coins;
            screen.receiptTicks = 80;
            screen.minecraft.getNarrator().sayNow(Component.literal("Sale complete. Received "
                    + screen.numbers.format(coins) + " coins."));
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, width - 12);
        rows = Math.max(1, (height - 90) / 22);
        left = (width - panelWidth) / 2;
        top = (height - (78 + rows * 22)) / 2;
        page = Math.min(page, pages() - 1);
        int tabWidth = (panelWidth - 16) / MarketView.CATEGORIES.size();
        for (int i = 0; i < MarketView.CATEGORIES.size(); i++) {
            String category = MarketView.CATEGORIES.get(i);
            if (category.equals("high") && view.category().equals("high")) {
                int x = left + 8 + i * tabWidth;
                int arrowWidth = 16;
                addRenderableWidget(Button.builder(Component.literal("v"),
                        ignored -> request(view.minimumPriceCommand(-1)))
                        .bounds(x, top + 26, arrowWidth, 20)
                        .tooltip(Tooltip.create(Component.literal("Lower minimum price by 1 coin and refresh.")))
                        .build()).active = !waiting && view.minimumPrice() > 1;
                addRenderableWidget(Button.builder(Component.literal("" + view.minimumPrice()), ignored -> {})
                        .bounds(x + arrowWidth, top + 26, tabWidth - 2 - arrowWidth * 2, 20)
                        .tooltip(Tooltip.create(Component.literal("Minimum price: " + numbers.format(view.minimumPrice())
                                + " coins in High. Use the down/up arrows to change it."))).build()).active = false;
                addRenderableWidget(Button.builder(Component.literal("^"),
                        ignored -> request(view.minimumPriceCommand(1)))
                        .bounds(x + tabWidth - 2 - arrowWidth, top + 26, arrowWidth, 20)
                        .tooltip(Tooltip.create(Component.literal("Raise minimum price by 1 coin and refresh.")))
                        .build()).active = !waiting && view.minimumPrice() < Integer.MAX_VALUE;
                continue;
            }
            var button = addRenderableWidget(Button.builder(Component.literal(category.equals("favorites") ? "Favorites" : category),
                    ignored -> request("market show " + category)).bounds(left + 8 + i * tabWidth,
                    top + 26, tabWidth - 2, 20).build());
            button.active = !waiting && !view.category().equals(category);
        }
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= view.entries().size()) break;
            var entry = view.entries().get(index);
            addRenderableWidget(Button.builder(Component.literal(entry.favorite() ? "-" : "+"), ignored -> {
                if (waiting) return;
                request(entry.favoriteCommand());
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(view.refreshCommand());
            }).bounds(left + panelWidth - 100, top + 51 + row * 22, 20, 20)
                    .tooltip(Tooltip.create(Component.literal(entry.favorite()
                            ? "Remove from favorites" : "Add to favorites"))).build()).active = !waiting;
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(entry.itemId())));
            var button = addRenderableWidget(Button.builder(Component.literal("Sell all"), ignored -> sell(entry))
                    .bounds(left + panelWidth - 76, top + 51 + row * 22, 66, 20)
                    .tooltip(Tooltip.create(Component.literal("Sell " + entry.count() + " ")
                            .append(stack.getHoverName()))).build());
            button.active = !waiting && entry.count() > 0;
        }
        int footer = top + 54 + rows * 22;
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(left + 8, footer, 25, 20).build());
        previous.active = page > 0 && !waiting;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(left + 38, footer, 25, 20).build());
        next.active = page + 1 < pages() && !waiting;
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(left + panelWidth - 68, footer, 60, 20).build());
    }

    private int pages() {
        return Math.max(1, (view.entries().size() + Math.max(1, rows) - 1) / Math.max(1, rows));
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        rebuildWidgets();
    }

    private void request(String command) {
        if (minecraft.getConnection() == null) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
            onClose();
            return;
        }
        waiting = true;
        waitTicks = 0;
        minecraft.getConnection().sendCommand(command);
        rebuildWidgets();
    }

    private void sell(MarketView.Entry entry) {
        if (waiting) return;
        request(entry.sellCommand());
        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(view.refreshCommand());
    }

    @Override
    public void tick() {
        if (receiptTicks > 0) receiptTicks--;
        if (waiting && ++waitTicks >= 100) {
            waiting = false;
            if (minecraft.player != null) minecraft.player.sendSystemMessage(
                    Component.literal("Market refresh timed out. Check chat for errors and reopen /market show."));
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0000000);
        graphics.fill(left, top, left + panelWidth, top + 78 + rows * 22, 0xFF19252C);
        graphics.renderOutline(left, top, panelWidth, 78 + rows * 22, 0xFF718A91);
        String wallet = numbers.format(view.coins()) + " coins";
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), panelWidth - font.width(wallet) - 30),
                left + 10, top + 10, 0xFFFFD879);
        graphics.drawString(font, wallet, left + panelWidth - font.width(wallet) - 10, top + 10, 0xFF9EE6A4);
        ItemStack hovered = ItemStack.EMPTY;
        String hoveredDetails = "";
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= view.entries().size()) break;
            var entry = view.entries().get(index);
            int y = top + 50 + row * 22;
            graphics.fill(left + 8, y, left + panelWidth - 8, y + 21, 0xFF283942);
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(entry.itemId())));
            graphics.renderItem(stack, left + 13, y + 3);
            String price = numbers.format(entry.price()) + " coins";
            String have = entry.count() > 0 ? "Have " + numbers.format(entry.count()) : "";
            int priceX = left + panelWidth - 108 - font.width(price);
            int haveX = priceX - (have.isEmpty() ? 0 : font.width(have) + 12);
            graphics.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(),
                    Math.max(0, haveX - left - 44)), left + 36, y + 7, 0xFFFFFFFF);
            graphics.drawString(font, price, priceX, y + 7, 0xFFFFD879);
            if (!have.isEmpty()) graphics.drawString(font, have, haveX, y + 7, 0xFF9EE6A4);
            if (mouseX >= left + 10 && mouseX < left + 34 && mouseY >= y && mouseY < y + 22) {
                hovered = stack;
                hoveredDetails = price + (have.isEmpty() ? "" : " | " + have);
            }
        }
        if (view.entries().isEmpty()) graphics.drawString(font, view.category().equals("favorites")
                ? "No active favorites. Use + beside an item to add it." : "No active items in this category.",
                left + 12, top + 57, 0xFFBAC9D0);
        graphics.drawString(font, (page + 1) + " / " + pages() + (waiting ? "  Waiting..." : ""),
                left + 72, top + 60 + rows * 22, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= left + 10 && mouseX < left + panelWidth - font.width(wallet) - 20
                && mouseY >= top + 7 && mouseY < top + 22)
            graphics.renderTooltip(font, title, mouseX, mouseY);
        if (!hovered.isEmpty()) graphics.renderComponentTooltip(font, java.util.List.of(
                hovered.getHoverName(), Component.literal(hoveredDetails),
                Component.literal("Right-click to view recipe in JEI."),
                Component.literal("Sell all uses current prices; bulk sales can lower the price.")), mouseX, mouseY);
        if (receiptTicks > 0) {
            String amount = "Received " + numbers.format(receiptCoins) + " coins";
            int popupWidth = Math.max(180, font.width(amount) + 24);
            int x = (width - popupWidth) / 2;
            int y = Math.max(6, height / 2 - 25);
            graphics.fill(x, y, x + popupWidth, y + 50, 0xFF203D35);
            graphics.renderOutline(x, y, popupWidth, 50, 0xFFEAC879);
            graphics.drawString(font, "Sale complete", width / 2 - font.width("Sale complete") / 2, y + 10, 0xFFEAC879);
            graphics.drawString(font, amount, width / 2 - font.width(amount) / 2, y + 29, 0xFF9EE6A4);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && !waiting && mouseX >= left + 10 && mouseX < left + 34) {
            int row = (int) ((mouseY - top - 50) / 22);
            int index = page * rows + row;
            if (mouseY >= top + 50 && row >= 0 && row < rows && index < view.entries().size()) {
                if (!net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                    if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Recipe viewing requires JEI."));
                    return true;
                }
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(view.entries().get(index).recipeCommand());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
