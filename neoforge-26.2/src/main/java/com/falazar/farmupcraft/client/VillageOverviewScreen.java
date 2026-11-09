package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageOverview;
import com.falazar.farmupcraft.util.VillageView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public final class VillageOverviewScreen extends Screen {
    private final VillageView view;
    private OverviewList list;
    private Button upgrade;
    private int left, top, panelWidth, panelHeight;

    public VillageOverviewScreen(VillageView view) {
        super(Component.literal("FarmUpcraft Village Overview"));
        VillageOverview.level(view);
        this.view = view;
    }

    @Override
    protected void init() {
        double scroll = list == null ? 0 : list.scrollAmount();
        panelWidth = Math.min(640, width - 12);
        panelHeight = height - 12;
        left = (width - panelWidth) / 2;
        top = 6;
        list = addRenderableWidget(new OverviewList());
        list.setScrollAmount(scroll);
        int level = VillageOverview.level(view);
        if (view.hasManagementPanels()) {
            upgrade = addRenderableWidget(Button.builder(
                    Component.literal(level == 10 ? "Level 10 - Max" : "Level " + level + " - Level Up"),
                    b -> confirmLevelUp()).bounds(0, 0, 136, 20).build());
            upgrade.active = level < 10 && view.entries().stream().anyMatch(e -> e.label().equals("Level-up action"))
                    && minecraft.getConnection() != null;
            upgrade.visible = false;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + panelWidth - 68, top + panelHeight - 24, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        positionUpgrade();
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF14252D);
        g.outline(left, top, panelWidth, panelHeight, 0xFF637F7A);
        g.text(font, title, left + 10, top + 8, 0xFFEAC879);
        super.extractRenderState(g, mouseX, mouseY, tick);
    }

    private void positionUpgrade() {
        if (upgrade == null) return;
        var bounds = VillageOverview.levelUpBounds(list.getRowWidth());
        var row = list.children().getFirst();
        int y = row.getY() + bounds.y();
        upgrade.setPosition(row.getX() + bounds.x(), y);
        upgrade.setWidth(bounds.width());
        upgrade.visible = y >= list.getY() && y + bounds.height() <= list.getBottom();
    }

    private void confirmLevelUp() {
        int level = VillageOverview.level(view);
        String command = VillageOverview.field(view, "Level-up action").value();
        minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
            if (!accepted) {
                minecraft.gui.setScreen(this);
                return;
            }
            if (minecraft.getConnection() == null) {
                if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
                minecraft.gui.setScreen(this);
                return;
            }
            minecraft.getConnection().sendCommand(command);
            minecraft.gui.setScreen(null);
            minecraft.getConnection().sendCommand("village info2");
        }, Component.literal("Level up " + view.name() + "?"),
                Component.literal("Level " + level + " -> " + (level + 1) + " costs " + level * 300
                        + " coins. Village coins are used first, then your coins for any remainder. Requires "
                        + (level + 1) * 2 + " villagers and "
                        + ((level + 1) * 2 - 2) + " claimed structures.")));
    }

    private final class OverviewList extends ObjectSelectionList<OverviewRow> {
        OverviewList() {
            super(VillageOverviewScreen.this.minecraft, panelWidth - 12, Math.max(24, panelHeight - 54),
                    top + 24, VillageOverview.height(view, panelWidth - 26));
            centerListVertically = false;
            updateSizeAndPosition(panelWidth - 12, Math.max(24, panelHeight - 54), left + 6, top + 24);
            addEntry(new OverviewRow());
        }

        @Override
        public int getRowWidth() { return getWidth() - 14; }
        @Override
        protected int scrollBarX() { return getRight() - 6; }
        @Override
        protected double scrollRate() { return 24; }
        @Override
        public void setSelected(OverviewRow row) {}
        @Override
        protected boolean entriesCanBeSelected() { return false; }
        @Override
        protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override
        protected void extractListSeparators(GuiGraphicsExtractor g) {}
    }

    private final class OverviewRow extends ObjectSelectionList.Entry<OverviewRow> {
        @Override
        public Component getNarration() {
            return Component.literal(view.name() + ". " + String.join(". ",
                    view.entries().stream().filter(e -> !e.label().equals("Level-up action"))
                            .map(e -> e.label() + ": " + e.value()).toList()));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tick) {
            VillageOverview.render(new VillageOverview.Canvas() {
                @Override
                public void fill(int x, int y, int w, int h, int color) {
                    g.fill(getX() + x, getY() + y, getX() + x + w, getY() + y + h, 0xFF000000 | color);
                }

                @Override
                public void text(String text, int x, int y, int w, int color) {
                    g.text(font, font.plainSubstrByWidth(text, w), getX() + x, getY() + y, 0xFF000000 | color);
                    if (font.width(text) > w && list.isMouseOver(mouseX, mouseY)
                            && mouseX >= getX() + x && mouseX < getX() + x + w
                            && mouseY >= getY() + y && mouseY < getY() + y + 9)
                        g.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
                }
            }, view, getWidth());
            positionUpgrade();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
