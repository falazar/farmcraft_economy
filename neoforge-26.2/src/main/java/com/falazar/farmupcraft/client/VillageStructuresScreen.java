package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageStructuresView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class VillageStructuresScreen extends Screen {
    private final VillageStructuresView view;
    private List<VillageStructuresView.Entry> entries;
    private String filter = "all";
    private StructureList list;
    private int left, top, panelWidth, panelHeight;
    private final double originX, originZ;

    private VillageStructuresScreen(VillageStructuresView view) {
        super(Component.literal("FarmUpcraft Village Structures"));
        this.view = view;
        entries = view.entries();
        var player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("A player is required to view village structures");
        originX = player.blockPosition().getX();
        originZ = player.blockPosition().getZ();
    }

    public static void open(VillageStructuresView view) {
        Minecraft.getInstance().gui.setScreen(new VillageStructuresScreen(view));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 12);
        panelHeight = Math.max(110, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        double scroll = list == null ? 0 : list.scrollAmount();
        int tabWidth = (panelWidth - 16) / VillageStructuresView.FILTERS.size();
        for (int i = 0; i < VillageStructuresView.FILTERS.size(); i++) {
            String selected = VillageStructuresView.FILTERS.get(i);
            addRenderableWidget(Button.builder(Component.literal(selected), b -> {
                filter = selected;
                entries = view.filtered(filter);
                list.setScrollAmount(0);
                rebuildWidgets();
            }).bounds(left + 8 + i * tabWidth, top + 32, tabWidth - 3, 18).build()).active = !filter.equals(selected);
        }
        list = addRenderableWidget(new StructureList(panelWidth - 16, panelHeight - 82, top + 54));
        list.setScrollAmount(scroll);
        int footer = top + panelHeight - 24;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + panelWidth - 72, footer, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF19252C);
        g.outline(left, top, panelWidth, panelHeight, 0xFF718A91);
        g.text(font, font.plainSubstrByWidth(title.getString(), panelWidth - 16), left + 8, top + 6, 0xFFFFD879);
        g.text(font, font.plainSubstrByWidth(view.name() + " | " + entries.size() + " / " + view.entries().size(),
                panelWidth - 16), left + 8, top + 19, 0xFFFFFFFF);
        g.text(font, "Scroll to browse", left + 8, top + panelHeight - 18, 0xFFBAC9D0);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (entries.isEmpty()) g.text(font, "No matching structures.", left + 14, top + 62, 0xFFBAC9D0);
    }

    private final class StructureList extends ContainerObjectSelectionList<StructureRow> {
        StructureList(int width, int height, int y) {
            super(VillageStructuresScreen.this.minecraft, width, height, y, 24);
            centerListVertically = false;
            updateSizeAndPosition(width, height, left + 8, y);
            for (var entry : entries) addEntry(new StructureRow(entry));
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

    private final class StructureRow extends ContainerObjectSelectionList.Entry<StructureRow> {
        private final VillageStructuresView.Entry entry;
        private final Button waypoint;

        StructureRow(VillageStructuresView.Entry entry) {
            this.entry = entry;
            waypoint = Button.builder(Component.literal("Map"), b -> {
                if (minecraft.getConnection() == null) {
                    if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
                    return;
                }
                if (!net.neoforged.fml.ModList.get().isLoaded("journeymap")) {
                    if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("JourneyMap is not installed."));
                    return;
                }
                minecraft.getConnection().sendCommand(entry.waypointCommand());
            }).bounds(0, 0, 42, 18).build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getX(), y = getY(), rowWidth = getWidth();
            g.fill(x, y, x + rowWidth, y + 22, hovered ? 0xFF344D59 : 0xFF283942);
            g.fill(x, y, x + 2, y + 22, 0xFF000000 | entry.color());
            int textWidth = rowWidth - 54;
            g.text(font, font.plainSubstrByWidth(entry.name(), textWidth), x + 6, y + 2, 0xFFFFFFFF);
            g.text(font, font.plainSubstrByWidth(entry.distance() + " blocks " + entry.bearing(originX, originZ)
                    + " | " + entry.status() + " | " + entry.coordinates(), textWidth),
                    x + 6, y + 12, 0xFF000000 | entry.color());
            waypoint.setPosition(x + rowWidth - 46, y + 2);
            waypoint.active = minecraft.getConnection() != null;
            waypoint.extractRenderState(g, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() { return List.of(waypoint); }

        @Override
        public List<? extends NarratableEntry> narratables() { return List.of(waypoint); }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
