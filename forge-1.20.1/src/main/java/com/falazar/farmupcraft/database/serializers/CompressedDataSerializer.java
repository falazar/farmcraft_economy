/*package com.falazar.farmupcraft.test.serializers;

import com.falazar.farmupcraft.test.DataBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;

import java.io.*;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class CompressedDataSerializer<T> implements DataBase.DataSerializer<T> {
    private final DataBase.DataSerializer<T> delegate;

    public CompressedDataSerializer(DataBase.DataSerializer<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompoundTag serialize(T value) {
        CompoundTag tag = delegate.serialize(value);
        try {
            // Convert the CompoundTag to byte array
            byte[] serializedData = CompoundTagToByteArray(tag);
            // Compress the serialized data
            byte[] compressedData = compress(serializedData);
            CompoundTag compressedTag = new CompoundTag();
            compressedTag.putByteArray("data", compressedData);
            return compressedTag;
        } catch (IOException e) {
            throw new RuntimeException("Error during serialization", e);
        }
    }

    @Override
    public T deserialize(CompoundTag tag, ServerLevel level) {
        try {
            // Decompress the data
            byte[] compressedData = tag.getByteArray("data");
            byte[] serializedData = decompress(compressedData);
            // Convert byte array back to CompoundTag
            CompoundTag decompressedTag = ByteArrayToCompoundTag(serializedData);
            // Deserialize using the delegate
            return delegate.deserialize(decompressedTag, level);
        } catch (IOException e) {
            throw new RuntimeException("Error during deserialization", e);
        }
    }

    private byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length)) {
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        }
    }

    private byte[] decompress(byte[] data) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length)) {
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (DataFormatException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] CompoundTagToByteArray(CompoundTag tag) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, baos);
            return baos.toByteArray();
        }
    }

    private CompoundTag ByteArrayToCompoundTag(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            return NbtIo.readCompressed(bais);
        }
    }
}*/