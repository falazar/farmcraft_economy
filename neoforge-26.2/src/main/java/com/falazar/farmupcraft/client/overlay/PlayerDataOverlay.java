package com.falazar.farmupcraft.client.overlay;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerDataOverlay {
    private PlayerDataOverlay() {}

    public static final GuiLayer HUD_PLAYER_DATA = (graphics, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        PlayerData player = ModEvents.getPlayerDatabase(mc.level).getData(mc.player.getUUID());
        if (player == null) return;

        ChunkData chunk = ModEvents.getChunkDataDatabase(mc.level)
                .getData(ChunkPos.pack(mc.player.blockPosition()));
        var villages = ModEvents.getVillageDatabase(mc.level);
        VillageData standing = chunk == null || chunk.getVillageId() == null
                ? null : villages.getData(chunk.getVillageId());
        VillageData home = player.getHomeVillageUUID() == null ? null : villages.getData(player.getHomeVillageUUID());
        String owner = chunk == null || chunk.getOwnerUUID() == null ? null : mc.level.players().stream()
                .filter(online -> chunk.getOwnerUUID().equals(online.getUUID()))
                .map(online -> online.getName().getString()).findFirst().orElse(null);
        List<Line> lines = lines(player, chunk, standing, home, owner, NumberFormat.getInstance());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            graphics.text(mc.font, line.text(), 6, 6 + i * 10, line.color(), true);
        }
    };

    static List<Line> lines(PlayerData player, ChunkData chunk, VillageData standing, VillageData home,
                            String ownerName, NumberFormat numbers) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Location: " + (standing == null ? "wilderness" : standing.getName()), 0xFFAAAAAA));
        String plot = chunk == null || chunk.getType() == null ? "None" : chunk.getType();
        if ("house".equalsIgnoreCase(plot) && ownerName != null && !ownerName.isBlank()) {
            plot = ownerName + " house";
        } else if ("village".equals(plot)) {
            plot = "village unclaimed";
        } else if (isLeveledPlotType(plot)) {
            plot += " level " + Math.max(1, chunk.getPlotLevel());
        }
        if (!"None".equals(plot)) lines.add(new Line("Plot: " + plot, 0xFFCCCCCC));
        lines.add(new Line(home == null ? "No Village" : "Home: " + home.getName(), 0xFFFFD700));
        lines.add(new Line("Coins: " + numbers.format(player.getCoins()), 0xFFFFD700));
        return List.copyOf(lines);
    }

    private static boolean isLeveledPlotType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "farm", "nursery", "pasture" -> true;
            default -> false;
        };
    }

    record Line(String text, int color) {}
}
