package com.falazar.farmupcraft.util;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongFunction;

public record StructureIdentity(String type, long startChunk) {
    public StructureIdentity {
        Objects.requireNonNull(type, "type");
    }

    public long resolveId(LongFunction<StructureIdentity> storedIdentity) {
        // Old saves keyed structures by start chunk. Keep matching records and their references intact.
        if (equals(storedIdentity.apply(startChunk))) {
            return startChunk;
        }
        long first = UUID.nameUUIDFromBytes((type + "\n" + startChunk)
                .getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        long candidate = first;
        do {
            StructureIdentity stored = storedIdentity.apply(candidate);
            if (stored == null || equals(stored)) {
                return candidate;
            }
            candidate++;
        } while (candidate != first);
        throw new IllegalStateException("No free structure ID for " + this);
    }
}
