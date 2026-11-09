package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.PlotView;
import com.falazar.farmupcraft.util.VillageStructuresView;
import com.falazar.farmupcraft.util.VillageView;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlotStructuresPayloadTest {
    @Test
    void roundTripsPlotQuotesPermissionsAndDetails() {
        var view = new PlotView(new VillageView("Chunk -1, 2", List.of(
                new VillageView.Entry("Wallet", "200 coins", VillageView.POSITIVE))),
                "minecraft:overworld", -1, 2, "road", 215, 215, false, true, List.of("road", "farm"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlotViewPacket.STREAM_CODEC.encode(buffer, new PlotViewPacket(view));
            assertEquals(view, PlotViewPacket.STREAM_CODEC.decode(buffer).view());
        } finally {
            buffer.release();
        }
    }

    @Test
    void maximumStructureSnapshotFitsCustomPayloadLimit() {
        String text = "\u0800".repeat(256);
        var entry = new VillageStructuresView.Entry(-10, text, text, -30000000, -64, 30000000,
                Integer.MAX_VALUE, true, true);
        var view = new VillageStructuresView(text, java.util.Collections.nCopies(VillageStructuresView.MAX_ENTRIES, entry));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            VillageStructuresViewPacket.STREAM_CODEC.encode(buffer, new VillageStructuresViewPacket(view));
            assertTrue(buffer.writerIndex() < 1_048_576, "Worst-case UTF-8 snapshot must fit S2C custom payload limit");
            assertEquals(view, VillageStructuresViewPacket.STREAM_CODEC.decode(buffer).view());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedStructureCountBeforeReadingEntries() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("Village", 256);
            buffer.writeVarInt(VillageStructuresView.MAX_ENTRIES + 1);
            assertThrows(DecoderException.class, () -> VillageStructuresViewPacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedPlotTypesBeforeReadingStrings() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new VillageViewPacket(new VillageView("Chunk", List.of())).toBytes(buffer);
            buffer.writeUtf("minecraft:overworld", 256);
            buffer.writeInt(0); buffer.writeInt(0); buffer.writeUtf("road", 64);
            buffer.writeVarInt(0); buffer.writeVarInt(0);
            buffer.writeBoolean(false); buffer.writeBoolean(false);
            buffer.writeVarInt(33);
            assertThrows(DecoderException.class, () -> PlotViewPacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
