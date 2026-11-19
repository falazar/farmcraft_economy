package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.util.VillageView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class VillageOverviewScreenTest {
    @Test
    void inlineUpgradeReceivesClickBeforeOverlappingScrollableContent(MinecraftServer server) throws Exception {
        var fixture = fixture();

        assertTrue(fixture.screen.mouseClicked(click(30, 50), false));

        assertEquals(1, fixture.presses.get());
        assertEquals(0, fixture.content.clicks);
        assertSame(fixture.button, fixture.screen.getFocused());
    }

    @Test
    void hiddenDisabledAndOutsideClicksDoNotUpgradeAndWheelStillReachesContent(MinecraftServer server)
            throws Exception {
        var fixture = fixture();
        fixture.button.visible = false;
        fixture.screen.mouseClicked(click(30, 50), false);
        fixture.button.visible = true;
        fixture.button.active = false;
        fixture.screen.mouseClicked(click(30, 50), false);
        fixture.button.active = true;
        fixture.screen.mouseClicked(click(200, 100), false);

        assertEquals(0, fixture.presses.get());
        assertEquals(3, fixture.content.clicks);
        assertTrue(fixture.screen.mouseScrolled(30, 50, 0, -1));
        assertEquals(1, fixture.content.scrolls);
    }

    private static MouseButtonEvent click(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
    }

    private static Fixture fixture() throws Exception {
        var view = new VillageView("Mykos",
                List.of(new VillageView.Entry("Village level", "3", VillageView.NORMAL)));
        var screen = new VillageOverviewScreen(view, null, null);
        var content = new Content();
        var presses = new AtomicInteger();
        var button = new SilentButton(presses);
        var addWidget = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
        addWidget.setAccessible(true);
        addWidget.invoke(screen, content);
        addWidget.invoke(screen, button);
        var upgrade = VillageOverviewScreen.class.getDeclaredField("upgrade");
        upgrade.setAccessible(true);
        upgrade.set(screen, button);
        return new Fixture(screen, content, button, presses);
    }

    private record Fixture(VillageOverviewScreen screen, Content content, Button button, AtomicInteger presses) {}

    private static final class Content extends AbstractWidget {
        private int clicks;
        private int scrolls;

        Content() {
            super(0, 0, 320, 240, Component.empty());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            clicks++;
            return false;
        }

        @Override
        public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            scrolls++;
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    private static final class SilentButton extends Button {
        SilentButton(AtomicInteger presses) {
            super(20, 40, 136, 20, Component.literal("Level 3 - Level Up"),
                    button -> presses.incrementAndGet(), Button.DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int x, int y, float tick) {}

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            // Exercise the real button bounds and callback without a running client sound engine.
            if (!isActive() || !isMouseOver(event.x(), event.y()) || event.button() != 0)
                return false;
            onClick(event, doubleClick);
            return true;
        }
    }
}
