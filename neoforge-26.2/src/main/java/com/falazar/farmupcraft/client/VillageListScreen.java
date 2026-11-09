package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageListView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class VillageListScreen extends Screen {
    private final VillageListView view;
    private int left, top, panelWidth, panelHeight;

    private VillageListScreen(VillageListView view) {
        super(Component.literal("FarmUpcraft Village List"));
        this.view = view;
    }

    public static void open(VillageListView view) {
        Minecraft.getInstance().gui.setScreen(new VillageListScreen(view));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 12);
        panelHeight = Math.max(110, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        addRenderableWidget(new VillageList(panelWidth - 16, panelHeight - 62, top + 34));
        int footer = top + panelHeight - 24;
        addRenderableWidget(Button.builder(Component.literal("Previous"),
                b -> sendCommand(VillageListView.pageCommand(view.page() - 1)))
                .bounds(left + 8, footer, 66, 20).build()).active = view.page() > 1;
        addRenderableWidget(Button.builder(Component.literal("Next"),
                b -> sendCommand(VillageListView.pageCommand(view.page() + 1)))
                .bounds(left + 78, footer, 60, 20).build()).active = view.page() < view.pages();
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + panelWidth - 72, footer, 60, 20).build());
    }

    private void sendCommand(String command) {
        if (minecraft.getConnection() == null) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
            return;
        }
        minecraft.getConnection().sendCommand(command);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF19252C);
        g.outline(left, top, panelWidth, panelHeight, 0xFF718A91);
        g.text(font, font.plainSubstrByWidth(title.getString(), panelWidth - 16), left + 8, top + 6, 0xFFFFD879);
        g.text(font, font.plainSubstrByWidth(view.total() + " villages | Page " + view.page() + " / "
                + view.pages() + " | A-Z", panelWidth - 16), left + 8, top + 19, 0xFFFFFFFF);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private final class VillageList extends ContainerObjectSelectionList<VillageRow> {
        VillageList(int width, int height, int y) {
            super(VillageListScreen.this.minecraft, width, height, y, 26);
            centerListVertically = false;
            updateSizeAndPosition(width, height, left + 8, y);
            for (var entry : view.entries()) addEntry(new VillageRow(entry));
        }

        @Override
        public int getRowWidth() { return getWidth() - 14; }

        @Override
        protected int scrollBarX() { return getRight() - 6; }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor g) {
            g.fill(getX(), getY(), getRight(), getBottom(), 0xFF19252C);
        }

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor g) {}
    }

    private final class VillageRow extends ContainerObjectSelectionList.Entry<VillageRow> {
        private final VillageListView.Entry entry;
        private final Button info;

        VillageRow(VillageListView.Entry entry) {
            this.entry = entry;
            info = Button.builder(Component.literal("Info"), b -> sendCommand(entry.infoCommand()))
                    .bounds(0, 0, 42, 20)
                    .tooltip(Tooltip.create(Component.literal(entry.name() + "\n" + entry.details()))).build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getX(), y = getY(), rowWidth = getWidth();
            int color = entry.home() ? 0xFFFFD879 : 0xFFFFFFFF;
            g.fill(x, y, x + rowWidth, y + 24, hovered ? 0xFF344D59 : 0xFF283942);
            g.fill(x, y, x + 2, y + 24, color);
            int textWidth = rowWidth - 56;
            g.text(font, font.plainSubstrByWidth((entry.home() ? "[Home] " : "") + entry.name(), textWidth),
                    x + 6, y + 3, color);
            g.text(font, font.plainSubstrByWidth(entry.details(), textWidth), x + 6, y + 13, 0xFFBAC9D0);
            info.setPosition(x + rowWidth - 46, y + 2);
            info.active = minecraft.getConnection() != null;
            info.extractRenderState(g, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() { return List.of(info); }

        @Override
        public List<? extends NarratableEntry> narratables() { return List.of(info); }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
