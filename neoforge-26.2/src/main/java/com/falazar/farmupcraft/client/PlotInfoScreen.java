package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.PlotView;
import com.falazar.farmupcraft.util.PlotOverview;
import com.falazar.farmupcraft.util.VillageOverview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PlotInfoScreen extends Screen {
    private final PlotView plot;
    private PlotList list;
    private PlotOverview.Layout layout;
    private int left, panelWidth, panelHeight;

    private PlotInfoScreen(PlotView plot) {
        super(Component.literal("FarmUpcraft Plot Information"));
        this.plot = plot;
    }

    public static void open(PlotView plot) {
        Minecraft.getInstance().gui.setScreen(new PlotInfoScreen(plot));
    }

    @Override
    protected void init() {
        double scroll = list == null ? 0 : list.scrollAmount();
        panelWidth = Math.min(640, width - 12);
        panelHeight = height - 12;
        left = (width - panelWidth) / 2;
        layout = PlotOverview.layout(plot, panelWidth - 26, (text, available) ->
                font.getSplitter().splitLines(text, available, net.minecraft.network.chat.Style.EMPTY)
                        .stream().map(net.minecraft.network.chat.FormattedText::getString).toList());
        list = addRenderableWidget(new PlotList());
        list.setScrollAmount(scroll);
        int y = panelHeight - 18;
        addRenderableWidget(Button.builder(Component.literal("Buy"), b -> chooseType(true))
                .bounds(left + 8, y, 70, 20).build()).active = plot.canBuy();
        addRenderableWidget(Button.builder(Component.literal("Change type"), b -> chooseType(false))
                .bounds(left + 82, y, 100, 20).build()).active = plot.canChange();
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + panelWidth - 68, y, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(left, 6, left + panelWidth, panelHeight + 6, 0xFF14252D);
        g.outline(left, 6, panelWidth, panelHeight, 0xFF637F7A);
        g.text(font, title, left + 10, 14, 0xFFEAC879);
        super.extractRenderState(g, x, y, tick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private final class PlotList extends ObjectSelectionList<PlotRow> {
        PlotList() {
            super(PlotInfoScreen.this.minecraft, panelWidth - 12, Math.max(24, panelHeight - 54), 30, layout.height());
            centerListVertically = false;
            updateSizeAndPosition(panelWidth - 12, Math.max(24, panelHeight - 54), left + 6, 30);
            addEntry(new PlotRow());
        }

        @Override
        public int getRowWidth() { return getWidth() - 14; }
        @Override
        protected int scrollBarX() { return getRight() - 6; }
        @Override
        protected double scrollRate() { return 24; }
        @Override
        public void setSelected(PlotRow row) {}
        @Override
        protected boolean entriesCanBeSelected() { return false; }
        @Override
        protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override
        protected void extractListSeparators(GuiGraphicsExtractor g) {}
    }

    private final class PlotRow extends ObjectSelectionList.Entry<PlotRow> {
        @Override
        public Component getNarration() {
            return Component.literal(String.join(". ", layout.texts().stream().map(PlotOverview.Text::value).toList()));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tick) {
            layout.render(new VillageOverview.Canvas() {
                @Override
                public void fill(int x, int y, int w, int h, int color) {
                    g.fill(getX() + x, getY() + y, getX() + x + w, getY() + y + h, 0xFF000000 | color);
                }

                @Override
                public void text(String text, int x, int y, int w, int color) {
                    g.text(font, text, getX() + x, getY() + y, 0xFF000000 | color);
                }
            });
        }
    }

    private void chooseType(boolean buy) {
        minecraft.gui.setScreen(new TypeScreen(buy));
    }

    private final class TypeScreen extends Screen {
        private final boolean buy;
        private int page;

        private TypeScreen(boolean buy) {
            super(Component.literal(buy ? "Buy a plot" : "Change plot type"));
            this.buy = buy;
        }

        @Override
        protected void init() {
            int rows = Math.max(1, (height - 76) / 24);
            int capacity = rows * 2;
            int pages = Math.max(1, (plot.types().size() + capacity - 1) / capacity);
            page = Math.min(page, pages - 1);
            int buttonWidth = Math.min(180, (width - 24) / 2);
            int startX = (width - buttonWidth * 2 - 6) / 2;
            for (int slot = 0; slot < capacity; slot++) {
                int index = page * capacity + slot;
                if (index >= plot.types().size()) break;
                String type = plot.types().get(index);
                int cost = buy ? plot.buyCost() : type.equals(plot.type()) ? 0 : plot.changeCost();
                addRenderableWidget(Button.builder(Component.literal(type + " for " + cost + " coins"), b -> confirm(type, cost))
                        .bounds(startX + slot % 2 * (buttonWidth + 6), 34 + slot / 2 * 24, buttonWidth, 20).build());
            }
            int footer = height - 28;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                    .bounds(width / 2 - 100, footer, 25, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                    .bounds(width / 2 - 70, footer, 25, 20).build()).active = page + 1 < pages;
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                    .bounds(width / 2 + 30, footer, 70, 20).build());
        }

        private void confirm(String type, int cost) {
            minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
                if (!accepted) {
                    minecraft.gui.setScreen(this);
                    return;
                }
                if (minecraft.getConnection() == null) {
                    if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
                    minecraft.gui.setScreen(PlotInfoScreen.this);
                    return;
                }
                minecraft.getConnection().sendCommand(plot.actionCommand(buy, type));
                minecraft.gui.setScreen(null);
                minecraft.getConnection().sendCommand("plot info2");
            }, Component.literal(buy ? "Confirm plot purchase" : "Confirm type change"),
                    Component.literal("Chunk " + plot.x() + ", " + plot.z() + " in " + plot.dimension()
                            + ": " + (buy ? "buy " : plot.type() + " -> ") + type + " for " + cost
                            + " coins from your wallet. Server rules still apply.")));
        }

        @Override
        public void onClose() { minecraft.gui.setScreen(PlotInfoScreen.this); }
        @Override
        public boolean isPauseScreen() { return false; }

        @Override
        public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float tick) {
            g.fill(0, 0, width, height, 0xFF19252C);
            g.text(font, title, width / 2 - font.width(title) / 2, 12, 0xFFFFD879);
            super.extractRenderState(g, x, y, tick);
        }
    }
}
