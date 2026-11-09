package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.GeneralMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GeneralMenuScreen extends Screen {
    private int top;
    public GeneralMenuScreen() {
        super(Component.literal("FarmUpcraft"));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(260, width - 24);
        int spacing = Math.max(16, Math.min(28, (height - 48) / (GeneralMenu.ENTRIES.size() + 1)));
        top = Math.max(24, (height - GeneralMenu.ENTRIES.size() * spacing - 24) / 2);
        for (int i = 0; i < GeneralMenu.ENTRIES.size(); i++) {
            var entry = GeneralMenu.ENTRIES.get(i);
            addRenderableWidget(Button.builder(Component.literal(entry.label()), ignored -> {
                if (minecraft.getConnection() == null) {
                    if (minecraft.player != null) {
                        minecraft.player.sendSystemMessage(Component.literal("Not connected to a server."));
                    }
                    onClose();
                    return;
                }
                onClose();
                minecraft.getConnection().sendCommand(entry.command());
            }).bounds((width - buttonWidth) / 2, top + i * spacing, buttonWidth, spacing - 4)
                    .tooltip(Tooltip.create(Component.literal(entry.description()))).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds((width - buttonWidth) / 2, top + GeneralMenu.ENTRIES.size() * spacing + 4, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top - 22, 0xFFFFD978);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
