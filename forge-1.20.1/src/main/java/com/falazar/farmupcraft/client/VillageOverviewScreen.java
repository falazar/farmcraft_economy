package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageOverview;
import com.falazar.farmupcraft.util.VillageView;
import net.minecraft.client.gui.GuiGraphics;
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
    private Component clippedDetails;

    public VillageOverviewScreen(VillageView view) {
        super(Component.literal("FarmUpcraft Village Overview"));
        VillageOverview.level(view);
        this.view = view;
    }

    @Override
    protected void init() {
        double scroll = list == null ? 0 : list.getScrollAmount();
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        positionUpgrade();
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF14252D);
        g.renderOutline(left, top, panelWidth, panelHeight, 0xFF637F7A);
        g.drawString(font, title, left + 10, top + 8, 0xFFEAC879);
        clippedDetails = null;
        super.render(g, mouseX, mouseY, tick);
        if (clippedDetails != null) g.renderTooltip(font, clippedDetails, mouseX, mouseY);
    }

    private void positionUpgrade() {
        if (upgrade == null) return;
        var bounds = VillageOverview.levelUpBounds(list.getRowWidth());
        int y = list.contentTop() + bounds.y();
        upgrade.setPosition(list.contentLeft() + bounds.x(), y);
        upgrade.setWidth(bounds.width());
        upgrade.visible = y >= top + 24 && y + bounds.height() <= top + panelHeight - 30;
    }

    private void confirmLevelUp() {
        int level = VillageOverview.level(view);
        String command = VillageOverview.field(view, "Level-up action").value();
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            if (!accepted) {
                minecraft.setScreen(this);
                return;
            }
            if (minecraft.getConnection() == null) {
                if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
                minecraft.setScreen(this);
                return;
            }
            minecraft.getConnection().sendCommand(command);
            minecraft.setScreen(null);
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
                    top + 24, top + panelHeight - 30, VillageOverview.height(view, panelWidth - 26));
            setLeftPos(left + 6);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            setRenderSelection(false);
            addEntry(new OverviewRow());
        }

        @Override
        public int getRowWidth() { return width - 14; }
        int contentTop() { return getRowTop(0); }
        int contentLeft() { return getRowLeft(); }
        @Override
        protected int getScrollbarPosition() { return x1 - 6; }
        @Override
        public void setSelected(OverviewRow row) {}
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            setScrollAmount(getScrollAmount() - delta * 24);
            return true;
        }
    }

    private final class OverviewRow extends ObjectSelectionList.Entry<OverviewRow> {
        @Override
        public Component getNarration() {
            return Component.literal(view.name() + ". " + String.join(". ",
                    view.entries().stream().filter(e -> !e.label().equals("Level-up action"))
                            .map(e -> e.label() + ": " + e.value()).toList()));
        }

        @Override
        public void render(GuiGraphics g, int index, int rowY, int rowX, int rowWidth, int rowHeight,
                int mouseX, int mouseY, boolean hovered, float tick) {
            VillageOverview.render(new VillageOverview.Canvas() {
                @Override
                public void fill(int x, int y, int w, int h, int color) {
                    g.fill(rowX + x, rowY + y, rowX + x + w, rowY + y + h, 0xFF000000 | color);
                }

                @Override
                public void text(String text, int x, int y, int w, int color) {
                    g.drawString(font, font.plainSubstrByWidth(text, w), rowX + x, rowY + y, 0xFF000000 | color);
                    if (font.width(text) > w && list.isMouseOver(mouseX, mouseY)
                            && mouseX >= rowX + x && mouseX < rowX + x + w
                            && mouseY >= rowY + y && mouseY < rowY + y + 9)
                        clippedDetails = Component.literal(text);
                }
            }, view, rowWidth);
            positionUpgrade();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
