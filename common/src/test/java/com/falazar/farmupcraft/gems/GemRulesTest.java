package com.falazar.farmupcraft.gems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GemRulesTest {
    @Test void looseHeightHintsStayWithinWorldBoundsIncludingEdgeBands() {
        for (int center : new int[]{-49, 0, 304}) {
            var band = new GemRules.Band("minecraft:plains", center, center - 15, center + 15);
            var rules = new GemRules.Rules(1, 1, -64, 319, 1, 2, 6, 15,
                    Map.of("opal", List.of(band)), List.of("minecraft:plains"), Map.of());
            for (int y : new int[]{band.minY(), band.maxY()}) {
                var knowledge = new GemRules.Knowledge(GemRules.Kind.ORDINARY, "opal",
                        band.id(), "", y, false);
                String hint = GemRules.hint(rules, knowledge, "", false);
                var number = java.util.regex.Pattern.compile("-?\\d+").matcher(hint);
                assertTrue(number.find(), hint);
                int height = Integer.parseInt(number.group());
                assertTrue(height >= -64 && height <= 319, hint);
                if (center == -49 && y == band.maxY())
                    assertEquals("You could search for opals at or above level -64.", hint);
                if (center == 304 && y == band.minY())
                    assertEquals("You could search for opals at or below level 319.", hint);
                if (center == 0)
                    assertTrue(hint.endsWith(y == band.minY() ? "below level 30." : "above level -30."), hint);
            }
        }
    }
    @Test void newWorldTerrainReferencesAndBands() {
        var expected = Map.of("biomesoplenty:crag", 70, "minecraft:stony_peaks", 120,
                "minecraft:snowy_slopes", 100, "biomesoplenty:highland", 100, "biomesoplenty:jade_cliffs", 120);
        expected.forEach((id, elevation) -> assertEquals(elevation, GemRules.referenceElevation(id)));
        assertEquals(64, GemRules.referenceElevation("newmod:new_biome"));
        assertEquals(64, GemRules.referenceElevation("minecraft:jagged_peaks"));
        var biomes = expected.keySet().stream()
                .map(id -> new GemRules.Biome(id, false, false, false, GemRules.referenceElevation(id))).toList();
        for (long seed = 0; seed < 100; seed++) {
            var rules = GemRules.create(seed, biomes, List.copyOf(expected.keySet()), -64, 319);
            assertEquals(expected, rules.referenceElevations());
            for (var bands : rules.gems().values()) for (var band : bands) {
                int reference = expected.get(band.id());
                int ceiling = reference >= 85 ? Math.min(100, reference - 20) : 50;
                assertTrue(band.centerY() <= ceiling);
            }
        }
    }
    @Test void exposedTargetsHaveBalancedStableRollsAndOwnedAlternatives() {
        int hidden = 0;
        for (int i = 0; i < 10000; i++) {
            var target = new GemRules.Target("opal", "minecraft:plains", i, 34, -i);
            boolean hide = GemRules.hideExposedTarget(12345, target);
            assertEquals(hide, GemRules.hideExposedTarget(12345, target));
            if (hide) hidden++;
            var alternatives = GemRules.buriedAlternatives(target);
            assertTrue(alternatives.size() <= 12);
            for (var p : alternatives) {
                assertEquals(target.x() >> 4, p.x() >> 4);
                assertEquals(target.z() >> 4, p.z() >> 4);
                assertTrue(Math.abs(p.y() - target.y()) <= 2);
                assertEquals(target.gem(), p.gem());
            }
        }
        assertTrue(hidden > 4800 && hidden < 5200, "Observed half-hide distribution: " + hidden);
    }
    @Test void gemTriggersUseWholeWordsAndSingularPlural() {
        for (String word : List.of("gem", "gems", "ruby", "rubies", "opal", "opals", "sapphire", "sapphires",
                "topaz", "topazes", "peridot", "peridots", "diamond", "diamonds"))
            assertTrue(GemRules.gemQuestion("Tell me about " + word + "?"), word);
        for (String word : List.of("gemstone", "gemini", "rubying", "opalescent", "peridotite", "mining", "digging"))
            assertFalse(GemRules.gemQuestion(word), word);
        assertEquals("", GemRules.requestedGem("opalescent gemstone"));
        assertEquals("ruby", GemRules.requestedGem("RUBIES!"));
        assertEquals("diamond", GemRules.requestedGem("diamonds"));
    }

    @Test void unsolicitedGemTopicsHaveExactFivePercentChanceOnlyWithKnowledge() {
        for (var kind : GemRules.Kind.values()) {
            long topics = java.util.stream.IntStream.range(0, 20)
                    .filter(roll -> GemRules.unsolicitedGemTopic(kind, roll)).count();
            assertEquals(kind == GemRules.Kind.NONE ? 0 : 1, topics);
        }
        assertThrows(IllegalArgumentException.class, () -> GemRules.unsolicitedGemTopic(GemRules.Kind.ORDINARY, -1));
        assertThrows(IllegalArgumentException.class, () -> GemRules.unsolicitedGemTopic(GemRules.Kind.ORDINARY, 20));
    }

    @Test void knowledgeableRepliesKeepOnlyBasicChecks() {
        var biomes = List.of("minecraft:cold_ocean", "minecraft:flower_forest", "minecraft:forest",
                "minecraft:desert", "minecraft:plains");
        String rumor = "The rumor mentions rubies in Cold Ocean or Flower Forest; I cannot vouch for which.";
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.RUMOR, rumor,
                "My aunt whispered about rubies in Cold Ocean or Flower Forest. I can't say which, dear!", biomes));
        for (String allowed : List.of("Rubies are in Cold Ocean.",
                "I heard rubies are in Cold Ocean or Desert; I can't say which.",
                "I heard rubies are in Cold Ocean or Flower Forest."))
            assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.RUMOR, rumor, allowed, biomes), allowed);
        assertFalse(GemRules.validKnowledgeReply(GemRules.Kind.RUMOR, rumor, "No clue.", biomes));
        assertFalse(GemRules.validKnowledgeReply(GemRules.Kind.RUMOR, rumor, "", biomes));
        assertFalse(GemRules.validKnowledgeReply(GemRules.Kind.RUMOR, rumor, "Rubies " + "x".repeat(600), biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.RUMOR, rumor,
                "Rubies might be in Cold Ocean or Flower Forest; I'm not sure which. Ask my neighbor!", biomes));
        String depth = "You could search for opals below level 60.";
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, depth,
                "You might find opals under level 60, friend. My knees aren't keen on such adventures!", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, depth, "Opals above level 60!", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, depth, "Opals at level 60!", biomes));
        assertFalse(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, depth, "Opals below level 61!", biomes));
        String direction = "You might search for sapphires northeast of here.";
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, direction,
                "Sapphires might be northeast of here, friend!", biomes));
        assertFalse(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, direction, "Sapphires lie north!", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, direction,
                "I heard about rubies, and sapphires northeast of here. Ask around!", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, depth,
                "I might know a thing or two, but I'm not one to gossip. Could you search for opals below level 60?", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY, depth,
                "Opals below level 60. I've lived here for thirty years.", biomes));
        String level = "You could try looking for topaz around level -49.";
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.LEVEL, level,
                "Topaz? Perhaps around level -49. I'd rather tend my flowers!", biomes));
        assertFalse(GemRules.validKnowledgeReply(GemRules.Kind.LEVEL, level, "Topaz around level 49!", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.LEVEL, level, "Topaz at level -49.", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY,
                "In the Overworld: " + direction, "Sapphires northeast.", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.ORDINARY,
                "In the Overworld: " + direction, "Sapphires northeast in the Overworld.", biomes));
        assertTrue(GemRules.validKnowledgeReply(GemRules.Kind.LEVEL, level,
                "I've heard that topaz can be found around level -49, roughly speaking.", biomes));
    }
    private static GemRules.Rules rules(long seed) {
        return GemRules.create(seed, List.of(new GemRules.Biome("a:one", false, false, false, 64),
                new GemRules.Biome("a:two", false, false, false, 64),
                new GemRules.Biome("a:ocean", false, false, true, 16),
                new GemRules.Biome("a:highland", false, false, false, 120)),
                List.of("a:one", "a:two", "a:ocean", "a:highland", "a:rare"), -64, 320);
    }
    @Test void stableRulesAndValidBands() {
        assertEquals(rules(43), rules(43));
        assertNotEquals(rules(43), rules(44));
        for (long seed = 0; seed < 100; seed++) for (var bands : rules(seed).gems().values()) {
            assertEquals(3, bands.stream().map(GemRules.Band::id).distinct().count());
            for (var band : bands) {
                assertEquals(15, band.centerY() - band.minY());
                assertTrue(band.minY() >= -64);
                if (band.id().equals("a:ocean")) assertTrue(band.maxY() <= 11);
            }
        }
    }
    @Test void positiveTriangularEndpointsAndExactRates() {
        var band = new GemRules.Band("a:one", 0, -15, 15);
        assertEquals(1, GemRules.weight(band, -15));
        assertEquals(1, GemRules.weight(band, 15));
        assertEquals(16, GemRules.weight(band, 0));
        assertEquals(256, java.util.stream.IntStream.rangeClosed(-15, 15).map(y -> GemRules.weight(band, y)).sum());
        Map<GemRules.Kind, Integer> counts = new EnumMap<>(GemRules.Kind.class);
        for (int roll = 0; roll < 60; roll++) for (int ordinaryRoll = 0; ordinaryRoll < 10; ordinaryRoll++)
            counts.merge(GemRules.outcome(roll, ordinaryRoll), 1, Integer::sum);
        assertEquals(Map.of(GemRules.Kind.ORDINARY, 42, GemRules.Kind.RUMOR, 10,
                GemRules.Kind.LEVEL, 10, GemRules.Kind.NONE, 538), counts);
        assertThrows(IllegalArgumentException.class, () -> GemRules.outcome(60, 0));
        assertThrows(IllegalArgumentException.class, () -> GemRules.outcome(0, 10));
    }
    @Test void chunksOwnTheirTargetsAndAreOrderIndependent() {
        var rules = rules(33);
        var first = GemRules.candidates(rules, 0, 0);
        var next = GemRules.candidates(rules, 1, 0);
        assertEquals(first, GemRules.candidates(rules, 0, 0));
        assertEquals(next, GemRules.candidates(rules, 1, 0));
        first.forEach(p -> { assertEquals(0, p.x() >> 4); assertEquals(0, p.z() >> 4); });
        next.forEach(p -> { assertEquals(1, p.x() >> 4); assertEquals(0, p.z() >> 4); });
        var aThenB = new HashSet<>(first); aThenB.addAll(next);
        var bThenA = new HashSet<>(next); bThenA.addAll(first);
        assertEquals(aThenB, bThenA);
        var crossing = GemRules.originCandidates(rules, 0, 0).stream().filter(p -> (p.x() >> 4) != 0 || (p.z() >> 4) != 0).toList();
        assertFalse(crossing.isEmpty(), "Fixture contains real cross-border deposits");
        for (var point : crossing) assertTrue(GemRules.candidates(rules, point.x() >> 4, point.z() >> 4).contains(point),
                "Destination must reproduce every crossed origin target without loading that origin");
    }
    @Test void stableLimitedKnowledgeAndRumorDecoy() {
        var rules = rules(77);
        for (int i = 0; i < 10000; i++) {
            UUID id = new UUID(0, i);
            var k = GemRules.knowledge(rules, id);
            assertEquals(k, GemRules.knowledge(rules, id));
            if (k.kind() == GemRules.Kind.RUMOR) {
                assertTrue(rules.gems().get(k.gem()).stream().noneMatch(b -> b.id().equals(k.decoy())));
                String one = GemRules.hint(rules, k, "", false), two = GemRules.hint(rules, k, "", true);
                assertTrue(one.contains(GemRules.biomeName(k.biome())));
                assertTrue(two.contains(GemRules.biomeName(k.decoy())));
                assertNotEquals(one, two);
            }
            if (k.kind() == GemRules.Kind.ORDINARY) {
                String fallback = GemRules.hint(rules, k, "", false);
                assertTrue(fallback.contains("level"));
                assertFalse(fallback.contains(GemRules.biomeName(k.biome())));
            }
        }
    }
    @Test void preservesRulesAndRejectsMissingExistingAndCorruption(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gem_biomes.json");
        GemFiles.write(file, rules(10));
        assertEquals(rules(10), GemFiles.load(file, 10, List.of(), rules(10).overworldBiomes(), -64, 320, true));
        assertThrows(IllegalStateException.class, () -> GemFiles.load(file, 11, List.of(), rules(10).overworldBiomes(), -64, 320, true));
        Files.delete(file);
        assertThrows(IllegalStateException.class, () -> GemFiles.load(file, 10, List.of(), List.of(), -64, 320, true));
        Files.writeString(file, "{broken");
        assertThrows(RuntimeException.class, () -> GemFiles.load(file, 10, List.of(), List.of(), -64, 320, true));
        assertEquals("{broken", Files.readString(file), "Corruption must never cause a reroll/write");
    }
    @Test void profileMigrationBacksUpAndPreservesUnknownFields(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("npc.json");
        String json = "{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Lina\",\"personality\":\"shy\",\"history\":{\"secret\":\"old\"}}";
        Files.writeString(file, json);
        GemFiles.migrateProfiles(dir);
        assertEquals(json, Files.readString(dir.resolve("gem-migration-v1-backups").resolve("npc.json")));
        String migrated = Files.readString(file);
        assertTrue(migrated.contains("\"history\""));
        assertTrue(migrated.contains("\"gems_migration_version\": 1"));
        GemFiles.migrateProfiles(dir);
        assertEquals(migrated, Files.readString(file));
    }
    @Test void optionalModPolicyAndExceptionsRemainNarrow(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gem_biome_config.json");
        var policy = GemFiles.policy(file);
        assertTrue(policy.eligible(new GemRules.Biome("anothermod:ordinary", false, false, false, 64)));
        assertTrue(policy.eligible(new GemRules.Biome("biomesoplenty:jade_cliffs", false, true, false, 120)));
        assertFalse(policy.eligible(new GemRules.Biome("biomesoplenty:jade_cliffs", true, true, false, 120)));
        assertFalse(policy.eligible(new GemRules.Biome("biomesoplenty:auroral_garden", false, false, false, 64)));
        assertFalse(policy.eligible(new GemRules.Biome("minecraft:lush_caves", false, false, false, 64)));
        assertFalse(policy.eligible(new GemRules.Biome("anothermod:rare", false, true, false, 64)));
        Files.writeString(file, "{\"schema_version\":1}");
        assertThrows(IllegalStateException.class, () -> GemFiles.policy(file));
        assertEquals("{\"schema_version\":1}", Files.readString(file));
    }
}
