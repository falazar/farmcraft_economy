package com.falazar.farmupcraft.client.overlay;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class PlayerDataOverlayTest {
    private final UUID playerId = UUID.randomUUID();
    private final UUID villageId = UUID.randomUUID();
    private final PlayerData player = new PlayerData(1, playerId, villageId, 1234);

    @Test
    void wildernessWithoutHomeOmitsThePlotLine() {
        assertEquals(List.of("Location: wilderness", "No Village", "Coins: 1,234"),
                text(null, null));
    }

    @Test
    void ownedHousesAndLeveledPlotsRetainTheirLabels() {
        var village = village();
        var house = new ChunkData("house", 1, villageId, playerId);
        assertEquals(List.of("Location: Test Village", "Plot: Robin house", "Home: Test Village", "Coins: 1,234"),
                text(house, village));
        var farm = new ChunkData("farm", 1, villageId);
        farm.setPlotLevel(3);
        assertEquals("Plot: farm level 3", text(farm, village).get(1));
        farm.setPlotLevel(0);
        assertEquals("Plot: farm level 1", text(farm, village).get(1));
    }

    @Test
    void unclaimedVillagePlotsRemainDistinct() {
        assertEquals("Plot: village unclaimed", text(new ChunkData("village", 1, villageId), village()).get(1));
    }

    private List<String> text(ChunkData chunk, VillageData village) {
        return PlayerDataOverlay.lines(player, chunk, village, village, "Robin", NumberFormat.getInstance(Locale.US))
                .stream().map(PlayerDataOverlay.Line::text).toList();
    }

    private VillageData village() {
        return new VillageData(villageId, "Test Village", new ChunkPos(0, 0), 1, List.of(),
                true, 250, "", "cow", "", playerId.toString(), List.of(playerId));
    }
}
