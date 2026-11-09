package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class VillageInfoScreen extends Screen {
    private final VillageView view;
    private int page;
    private int rows;
    private int columns;
    protected int left;
    protected int top;
    protected int panelWidth;
    private int cardWidth;

    private VillageInfoScreen(VillageView view) {
        this(view, "FarmUpcraft Village Overview");
    }

    protected VillageInfoScreen(VillageView view, String title) {
        super(Component.literal(title));
        this.view = view;
    }

    public static void open(VillageView view) {
        Minecraft.getInstance().gui.setScreen(new VillageOverviewScreen(view));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 12);
        columns = panelWidth >= 510 ? 3 : panelWidth >= 340 ? 2 : 1;
        int neededRows = Math.max(1, (view.entries().size() + columns - 1) / columns);
        rows = Math.max(1, Math.min(neededRows, (height - 88 - extraFooterHeight()) / 28));
        cardWidth = (panelWidth - 20 - (columns - 1) * 4) / columns;
        left = (width - panelWidth) / 2;
        top = (height - (76 + rows * 28 + extraFooterHeight())) / 2;
        page = Math.min(page, pages() - 1);
        int footer = top + 50 + rows * 28;
        if (pages() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(left + 12, footer, 25, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(left + 42, footer, 25, 20).build()).active = page + 1 < pages();
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + panelWidth - 72, footer, 60, 20).build());
        addFooterActions(footer);
    }

    protected void addFooterActions(int y) {}
    protected int extraFooterHeight() { return 0; }

    private int pages() {
        int capacity = Math.max(1, rows * columns);
        return Math.max(1, (view.entries().size() + capacity - 1) / capacity);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pages() - 1, page + delta));
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0000000);
        int panelHeight = 76 + rows * 28 + extraFooterHeight();
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF15232C);
        graphics.outline(left, top, panelWidth, panelHeight, 0xFF526D79);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 40, 0xFF203541);
        graphics.fill(left + 10, top + 39, left + panelWidth - 10, top + 40, 0xFFBD9B57);
        graphics.text(font, font.plainSubstrByWidth(title.getString(), panelWidth - 20), left + 10, top + 7, 0xFFE8C77D);
        graphics.text(font, font.plainSubstrByWidth(view.name(), panelWidth - 20), left + 10, top + 23, 0xFFFFFFFF);
        for (int slot = 0; slot < rows * columns; slot++) {
            int index = page * rows * columns + slot;
            if (index >= view.entries().size()) break;
            var entry = view.entries().get(index);
            int x = left + 10 + slot % columns * (cardWidth + 4);
            int y = top + 46 + slot / columns * 28;
            boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + 25;
            graphics.fill(x, y, x + cardWidth, y + 25,
                    hovered ? 0xFF36515F : slot / columns % 2 == 0 ? 0xFF243945 : 0xFF20333E);
            graphics.fill(x, y, x + 2, y + 25, 0xFF000000 | entry.color());
            graphics.text(font, font.plainSubstrByWidth(entry.label(), cardWidth - 14), x + 7, y + 3, 0xFFA6BAC5);
            graphics.text(font, font.plainSubstrByWidth(entry.value(), cardWidth - 14),
                    x + 7, y + 14, 0xFF000000 | entry.color());
            if (hovered) {
                graphics.setTooltipForNextFrame(Component.literal(entry.label() + ": " + entry.value()), mouseX, mouseY);
            }
        }
        graphics.text(font, pages() > 1 ? (page + 1) + " / " + pages() : "Hover for details",
                left + (pages() > 1 ? 78 : 10), top + 56 + rows * 28, 0xFFA6BAC5);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
