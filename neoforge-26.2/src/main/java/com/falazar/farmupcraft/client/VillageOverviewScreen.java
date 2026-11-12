package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageOverview;
import com.falazar.farmupcraft.util.VillageView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VillageOverviewScreen extends Screen {
    private final VillageView view;
    private OverviewList list;
    private Button upgrade;
    private int left, top, panelWidth, panelHeight;
    private String levelUpMessage;
    private boolean levelUpSucceeded;
    private boolean levelUpPending;

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
                    button -> confirmLevelUp()).bounds(0, 0, 136, 20).build());
            upgrade.active = level < 10
                    && view.entries().stream().anyMatch(entry -> entry.label().equals("Level-up action"))
                    && minecraft.getConnection() != null;
            upgrade.visible = false;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + panelWidth - 68, top + panelHeight - 24, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        positionUpgrade();
        graphics.fill(0, 0, width, height, 0xB0000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF14252D);
        graphics.outline(left, top, panelWidth, panelHeight, 0xFF637F7A);
        graphics.text(font, title, left + 10, top + 8, 0xFFEAC879);
        if (levelUpMessage != null) {
            int bannerTop = top + 22;
            int color = levelUpPending ? 0xFF66512B : levelUpSucceeded ? 0xFF27563E : 0xFF6D302A;
            graphics.fill(left + 8, bannerTop, left + panelWidth - 8, bannerTop + 16, color);
            graphics.text(font, font.plainSubstrByWidth(levelUpMessage, panelWidth - 32), left + 14,
                    bannerTop + 4, 0xFFFFFFFF);
        }
        super.extractRenderState(graphics, mouseX, mouseY, tick);
    }

    public static void showLevelUpResult(String message, boolean success) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof VillageOverviewScreen screen) {
            screen.levelUpMessage = message;
            screen.levelUpSucceeded = success;
            screen.levelUpPending = false;
            screen.rebuildWidgets();
        }
    }

    private void positionUpgrade() {
        if (upgrade == null || list.children().isEmpty())
            return;
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
                levelUpMessage = "Not connected to a server.";
                levelUpSucceeded = false;
                levelUpPending = false;
                minecraft.gui.setScreen(this);
                return;
            }
            levelUpMessage = "Checking level-up requirements...";
            levelUpPending = true;
            minecraft.gui.setScreen(this);
            minecraft.getConnection().sendCommand(command);
        }, Component.literal("Level up " + view.name() + "?"),
                Component.literal("Level " + level + " -> " + (level + 1) + " costs " + level * 300
                        + " coins. Village coins are used first, then your coins for any remainder. Requires "
                        + (level + 1) * 2 + " villagers and "
                        + ((level + 1) * 2 - 2) + " claimed structures.")));
    }

    private final class OverviewList extends ObjectSelectionList<OverviewRow> {
        OverviewList() {
            super(VillageOverviewScreen.this.minecraft, panelWidth - 12, listHeight(), listTop(),
                    VillageOverview.height(view, panelWidth - 26));
            centerListVertically = false;
            updateSizeAndPosition(panelWidth - 12, listHeight(), left + 6, listTop());
            addEntry(new OverviewRow());
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 14;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }

        @Override
        protected double scrollRate() {
            return 24;
        }

        @Override
        public void setSelected(OverviewRow row) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return false;
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        }
    }

    private int listTop() {
        return top + (levelUpMessage == null ? 24 : 42);
    }

    private int listHeight() {
        return Math.max(24, panelHeight - (levelUpMessage == null ? 54 : 72));
    }

    private final class OverviewRow extends ObjectSelectionList.Entry<OverviewRow> {
        @Override
        public Component getNarration() {
            return Component.literal(view.name() + ". " + String.join(". ",
                    view.entries().stream().filter(entry -> !entry.label().equals("Level-up action"))
                            .map(entry -> entry.label() + ": " + entry.value()).toList()));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tick) {
            VillageOverview.render(new VillageOverview.Canvas() {
                @Override
                public void fill(int x, int y, int w, int h, int color) {
                    graphics.fill(getX() + x, getY() + y, getX() + x + w, getY() + y + h, 0xFF000000 | color);
                }

                @Override
                public void text(String text, int x, int y, int w, int color) {
                    graphics.text(font, font.plainSubstrByWidth(text, w), getX() + x, getY() + y,
                            0xFF000000 | color);
                    if (font.width(text) > w && list.isMouseOver(mouseX, mouseY)
                            && mouseX >= getX() + x && mouseX < getX() + x + w
                            && mouseY >= getY() + y && mouseY < getY() + y + 9) {
                        graphics.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
                    }
                }
            }, view, getWidth());
            positionUpgrade();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}