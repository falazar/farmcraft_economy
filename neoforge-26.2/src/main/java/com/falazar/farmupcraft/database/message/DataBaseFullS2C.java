package com.falazar.farmupcraft.database.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public record DataBaseFullS2C(CompoundTag database, String databaseName) implements CustomPacketPayload {
    public static final Type<DataBaseFullS2C> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "database_full"));
    public static final StreamCodec<ByteBuf, DataBaseFullS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, DataBaseFullS2C::database,
            ByteBufCodecs.STRING_UTF8, DataBaseFullS2C::databaseName,
            DataBaseFullS2C::new);

    public DataBaseFullS2C {
        database = Objects.requireNonNull(database).copy();
        databaseName = Identifier.parse(databaseName).toString();
    }

    @Override
    public CompoundTag database() {
        return database.copy();
    }

    @Override
    public Type<DataBaseFullS2C> type() {
        return TYPE;
    }
}
