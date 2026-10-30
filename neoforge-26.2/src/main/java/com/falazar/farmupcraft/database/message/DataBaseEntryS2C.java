package com.falazar.farmupcraft.database.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;

public record DataBaseEntryS2C(CompoundTag key, CompoundTag value, String databaseName) implements CustomPacketPayload {
    public static final Type<DataBaseEntryS2C> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "database_entry"));
    public static final StreamCodec<ByteBuf, DataBaseEntryS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, DataBaseEntryS2C::key,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, DataBaseEntryS2C::value,
            ByteBufCodecs.STRING_UTF8, DataBaseEntryS2C::databaseName,
            DataBaseEntryS2C::new);

    public DataBaseEntryS2C {
        key = Objects.requireNonNull(key).copy();
        value = Objects.requireNonNull(value).copy();
        databaseName = Identifier.parse(databaseName).toString();
    }

    @Override
    public CompoundTag key() {
        return key.copy();
    }

    @Override
    public CompoundTag value() {
        return value.copy();
    }

    @Override
    public Type<DataBaseEntryS2C> type() {
        return TYPE;
    }
}
