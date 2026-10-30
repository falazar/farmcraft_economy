package com.falazar.farmupcraft.client.overlay;

import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.visualtest.VisualAcceptance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.text.NumberFormat;
import java.util.List;

/** Source assertions, not a replacement GUI layer or a pixel/text recognition assertion. */
public final class VisualHudProbe {
    private VisualHudProbe() {}

    public static List<String> lines(Minecraft minecraft) {
        var level = minecraft.level;
        var player = minecraft.player;
        var data = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        var claim = ModEvents.getChunkDataDatabase(level).getData(ChunkPos.pack(player.blockPosition()));
        var village = ModEvents.getVillageDatabase(level).getData(VisualAcceptance.VILLAGE);
        var numbers = NumberFormat.getInstance();
        var lines = PlayerDataOverlay.lines(data, claim, village, village, "VisualProbe", numbers)
                .stream().map(PlayerDataOverlay.Line::text).toList();
        VisualAcceptance.require(lines.equals(List.of(
                "Location: " + VisualAcceptance.VILLAGE_NAME,
                "Plot: farm level 3",
                "Home: " + VisualAcceptance.VILLAGE_NAME,
                "Coins: " + numbers.format(VisualAcceptance.COINS))), "Unexpected production HUD source: " + lines);
        return lines;
    }
}
