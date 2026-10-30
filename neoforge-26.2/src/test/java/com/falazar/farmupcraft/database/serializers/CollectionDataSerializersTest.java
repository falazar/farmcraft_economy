package com.falazar.farmupcraft.database.serializers;

import com.falazar.farmupcraft.database.DataSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CollectionDataSerializersTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @Test
    void listsRetainLegacyNestedCompoundShapeAndOrder() {
        var serializer = new ListDataSerializer<>(new IntDataSerializer());
        CompoundTag legacy = list("list", integer(-1), integer(Integer.MIN_VALUE), integer(Integer.MAX_VALUE), integer(-1));
        List<Integer> values = List.of(-1, Integer.MIN_VALUE, Integer.MAX_VALUE, -1);
        assertEquals(legacy, serializer.serialize(values, REGISTRIES));
        assertEquals(values, serializer.deserialize(legacy, REGISTRIES));

        var nested = new ListDataSerializer<>(serializer);
        CompoundTag nestedLegacy = list("list", legacy, list("list"));
        assertEquals(nestedLegacy, nested.serialize(List.of(values, List.of()), REGISTRIES));
        assertEquals(List.of(values, List.of()), nested.deserialize(nestedLegacy, REGISTRIES));
    }

    @Test
    void mapsRetainLegacyEntryListKeyAndValueCompoundsAcrossComposition() {
        var maps = new MapDataSerializer<>(new StringDataSerializer(), new ListDataSerializer<>(new IntDataSerializer()));
        CompoundTag legacy = list("map_entry", entry(string("negative"), list("list", integer(-1), integer(0))));
        Map<String, List<Integer>> value = Map.of("negative", List.of(-1, 0));
        assertEquals(legacy, maps.serialize(value, REGISTRIES));
        assertEquals(value, maps.deserialize(legacy, REGISTRIES));

        var lists = new ListDataSerializer<>(maps);
        CompoundTag nested = list("list", legacy);
        assertEquals(nested, lists.serialize(List.of(value), REGISTRIES));
        assertEquals(List.of(value), lists.deserialize(nested, REGISTRIES));

        var mapOfMaps = new MapDataSerializer<>(new StringDataSerializer(), maps);
        CompoundTag twiceNested = list("map_entry", entry(string("outer"), legacy));
        assertEquals(twiceNested, mapOfMaps.serialize(Map.of("outer", value), REGISTRIES));
        assertEquals(Map.of("outer", value), mapOfMaps.deserialize(twiceNested, REGISTRIES));
    }

    @Test
    void emptyCollectionsStillHaveRequiredLegacyListFields() {
        var lists = new ListDataSerializer<>(new IntDataSerializer());
        var maps = new MapDataSerializer<>(new StringDataSerializer(), new IntDataSerializer());
        assertEquals(list("list"), lists.serialize(List.of(), REGISTRIES));
        assertEquals(List.of(), lists.deserialize(list("list"), REGISTRIES));
        assertEquals(list("map_entry"), maps.serialize(Map.of(), REGISTRIES));
        assertEquals(Map.of(), maps.deserialize(list("map_entry"), REGISTRIES));
    }

    @Test
    void malformedListFieldsAndElementsFailClosed() {
        var serializer = new ListDataSerializer<>(new IntDataSerializer());
        for (CompoundTag malformed : List.of(new CompoundTag(), field("list", new CompoundTag()),
                list("list", IntTag.valueOf(1)), list("list", integer(1), new CompoundTag()),
                list("list", field("int_value", StringTag.valueOf("1"))))) {
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(malformed, REGISTRIES));
        }
        ListTag mixed = new ListTag();
        mixed.add(integer(1));
        mixed.add(StringTag.valueOf("not a compound"));
        assertThrows(IllegalArgumentException.class,
                () -> serializer.deserialize(field("list", mixed), REGISTRIES));
    }

    @Test
    void malformedMapFieldsEntriesAndDuplicateDecodedKeysFailClosed() {
        var serializer = new MapDataSerializer<>(new StringDataSerializer(), new IntDataSerializer());
        CompoundTag valid = entry(string("a"), integer(1));
        for (CompoundTag malformed : List.of(new CompoundTag(), field("map_entry", new CompoundTag()),
                list("map_entry", IntTag.valueOf(1)), list("map_entry", valid, new CompoundTag()),
                list("map_entry", field("key", string("a"))),
                list("map_entry", field("value", integer(1))),
                list("map_entry", entry(IntTag.valueOf(1), integer(1))),
                list("map_entry", entry(string("a"), IntTag.valueOf(1))),
                list("map_entry", entry(new CompoundTag(), integer(1))),
                list("map_entry", entry(string("a"), new CompoundTag())),
                list("map_entry", valid, entry(string("a"), integer(2))))) {
            assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(malformed, REGISTRIES));
        }
    }

    @Test
    void nestedFailuresPropagateUnchangedInBothDirectionsAndBothMapDelegates() {
        RuntimeException failure = new IllegalStateException("delegate failure");
        DataSerializer<Integer> failing = new DataSerializer<>() {
            @Override
            public CompoundTag serialize(Integer value, HolderLookup.Provider registries) {
                throw failure;
            }

            @Override
            public Integer deserialize(CompoundTag tag, HolderLookup.Provider registries) {
                throw failure;
            }
        };
        var nestedList = new ListDataSerializer<>(new ListDataSerializer<>(failing));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> nestedList.serialize(List.of(List.of(1)), REGISTRIES)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> nestedList.deserialize(list("list", list("list", integer(1))), REGISTRIES)));
        for (var map : List.of(new MapDataSerializer<>(failing, new IntDataSerializer()),
                new MapDataSerializer<>(new IntDataSerializer(), failing))) {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> map.serialize(Map.of(1, 2), REGISTRIES)));
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> map.deserialize(list("map_entry", entry(integer(1), integer(2))), REGISTRIES)));
        }
    }

    @Test
    void nullDelegateResultsAreErrorsNotDroppedEntries() {
        DataSerializer<Integer> nulls = new DataSerializer<>() {
            @Override
            public CompoundTag serialize(Integer value, HolderLookup.Provider registries) {
                return null;
            }

            @Override
            public Integer deserialize(CompoundTag tag, HolderLookup.Provider registries) {
                return null;
            }
        };
        var lists = new ListDataSerializer<>(nulls);
        assertThrows(IllegalArgumentException.class, () -> lists.serialize(List.of(1), REGISTRIES));
        assertThrows(IllegalArgumentException.class, () -> lists.deserialize(list("list", integer(1)), REGISTRIES));
        for (var map : List.of(new MapDataSerializer<>(nulls, new IntDataSerializer()),
                new MapDataSerializer<>(new IntDataSerializer(), nulls))) {
            assertThrows(IllegalArgumentException.class, () -> map.serialize(Map.of(1, 2), REGISTRIES));
            assertThrows(IllegalArgumentException.class,
                    () -> map.deserialize(list("map_entry", entry(integer(1), integer(2))), REGISTRIES));
        }
    }

    @Test
    void registryProviderIsForwardedThroughListsAndBothMapDelegates() {
        int[] calls = new int[2];
        DataSerializer<Integer> checking = new DataSerializer<>() {
            private final IntDataSerializer delegate = new IntDataSerializer();

            @Override
            public CompoundTag serialize(Integer value, HolderLookup.Provider registries) {
                assertSame(REGISTRIES, registries);
                calls[0]++;
                return delegate.serialize(value, registries);
            }

            @Override
            public Integer deserialize(CompoundTag tag, HolderLookup.Provider registries) {
                assertSame(REGISTRIES, registries);
                calls[1]++;
                return delegate.deserialize(tag, registries);
            }
        };
        var serializer = new ListDataSerializer<>(new MapDataSerializer<>(checking, new ListDataSerializer<>(checking)));
        var value = List.of(Map.of(-1, List.of(2, 3)));
        CompoundTag encoded = serializer.serialize(value, REGISTRIES);
        assertEquals(value, serializer.deserialize(encoded, REGISTRIES));
        assertArrayEquals(new int[] {3, 3}, calls);
    }

    private static CompoundTag integer(int value) {
        return field("int_value", IntTag.valueOf(value));
    }

    private static CompoundTag string(String value) {
        return field("string_value", StringTag.valueOf(value));
    }

    private static CompoundTag entry(Tag key, Tag value) {
        CompoundTag entry = field("key", key);
        entry.put("value", value);
        return entry;
    }

    private static CompoundTag list(String name, Tag... values) {
        ListTag list = new ListTag();
        for (Tag value : values) {
            list.add(value);
        }
        return field(name, list);
    }

    private static CompoundTag field(String name, Tag value) {
        CompoundTag tag = new CompoundTag();
        tag.put(name, value);
        return tag;
    }
}
