package com.falazar.farmupcraft.gems;

import java.util.*;

/** Versioned, Minecraft-independent world-coordinate rules. Never consume placement-dependent randomness. */
public final class GemRules {
    public static final int VERSION = 1;
    public static final int REACH = 2;
    public static final List<String> GEMS = List.of("ruby", "opal", "sapphire", "topaz", "peridot");
    public static final Map<String, Integer> MARKET_PRICES = Map.of(
            "ruby", 100, "opal", 80, "sapphire", 100, "topaz", 60, "peridot", 60);
    public record Biome(String id, boolean cave, boolean rare, boolean ocean, int referenceElevation) {}
    public record Band(String id, int centerY, int minY, int maxY) {
        public boolean contains(int y) { return y >= minY && y <= maxY; }
    }
    public record Rules(int generationVersion, long seed, int minY, int maxY,
                        int attemptsMin, int attemptsMax, int targetsMin, int targetsMax,
                        Map<String, List<Band>> gems, List<String> overworldBiomes,
                        Map<String, Integer> referenceElevations) {}
    public record Target(String gem, String biome, int x, int y, int z) {}
    public static int referenceElevation(String biome) {
        return switch (biome) {
            case "biomesoplenty:crag" -> 70;
            case "minecraft:stony_peaks" -> 120;
            case "minecraft:snowy_slopes", "biomesoplenty:highland" -> 100;
            case "biomesoplenty:jade_cliffs" -> 120;
            default -> 64;
        };
    }

    public static boolean hideExposedTarget(long seed, Target target) {
        long roll = mix(seed ^ mix(target.x) ^ mix(target.y) ^ mix(target.z)
                ^ mix(target.gem.hashCode()) ^ 0x43617665416972L);
        return (roll & 1) == 0;
    }

    public static List<Target> buriedAlternatives(Target target) {
        var result = new ArrayList<Target>();
        for (int distance = 1; distance <= 2; distance++) {
            for (int[] direction : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                    {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int x = target.x + direction[0] * distance;
                int y = target.y + direction[1] * distance;
                int z = target.z + direction[2] * distance;
                if ((x >> 4) == (target.x >> 4) && (z >> 4) == (target.z >> 4)) {
                    result.add(new Target(target.gem, target.biome, x, y, z));
                }
            }
        }
        return result;
    }
    public enum Kind { NONE, ORDINARY, RUMOR, LEVEL }
    public record Knowledge(Kind kind, String gem, String biome, String decoy, int y, boolean direction) {}
    private GemRules() {}

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static Rules create(long seed, List<Biome> eligible, List<String> overworld, int minY, int maxY) {
        var pool = eligible.stream().sorted(Comparator.comparing(Biome::id)).toList();
        if (pool.size() < 3) throw new IllegalStateException("Gems require at least three eligible overworld biomes");
        if (overworld.stream().distinct().count() < 4)
            throw new IllegalStateException("Gem rumors also require an installed ineligible overworld biome");
        var random = new Random(mix(seed ^ VERSION));
        Map<String, List<Band>> gems = new LinkedHashMap<>();
        for (String gem : GEMS) {
            var shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, random);
            List<Band> bands = new ArrayList<>();
            for (Biome biome : shuffled.subList(0, 3)) {
                int low = Math.max(-50, minY + 15);
                int high = Math.min(biome.referenceElevation >= 85 ? Math.min(100, biome.referenceElevation - 20) : 50, maxY - 16);
                if (biome.ocean) high = Math.min(high, biome.referenceElevation - 20);
                if (low > high) throw new IllegalStateException("No fully valid gem band for " + biome.id);
                int center = low + random.nextInt(high - low + 1);
                bands.add(new Band(biome.id, center, center - 15, center + 15));
            }
            gems.put(gem, List.copyOf(bands));
        }
        Map<String, Integer> references = new TreeMap<>();
        pool.forEach(b -> references.put(b.id, b.referenceElevation));
        return new Rules(VERSION, seed, minY, maxY, 1, 2, 6, 15, gems,
                overworld.stream().distinct().sorted().toList(), references);
    }

    public static void validate(Rules rules, long seed, Set<String> installed, int minY, int maxY) {
        if (rules == null || rules.generationVersion != VERSION || rules.seed != seed
                || rules.minY != minY || rules.maxY != maxY || rules.gems == null
                || !rules.gems.keySet().equals(new HashSet<>(GEMS))
                || rules.attemptsMin != 1 || rules.attemptsMax != 2
                || rules.targetsMin != 6 || rules.targetsMax != 15
                || rules.overworldBiomes == null || rules.overworldBiomes.stream().distinct().count() < 4
                || !installed.containsAll(rules.overworldBiomes)
                || rules.referenceElevations == null)
            throw new IllegalStateException("Invalid/incompatible gem_biomes.json; restore backup, do not reroll");
        for (var bands : rules.gems.values()) {
            if (bands == null || bands.size() != 3 || bands.stream().map(Band::id).distinct().count() != 3)
                throw new IllegalStateException("Every gem requires three distinct biomes");
            for (Band b : bands) if (!installed.contains(b.id) || b.minY != b.centerY - 15
                    || b.maxY != b.centerY + 15 || b.minY < minY || b.maxY >= maxY
                    || b.centerY < -50 || b.centerY > 100 || !rules.referenceElevations.containsKey(b.id)
                    || (rules.referenceElevations.get(b.id) < 64 && b.maxY > rules.referenceElevations.get(b.id) - 5)
                    || (rules.referenceElevations.get(b.id) == 64 && b.centerY > 50))
                throw new IllegalStateException("Invalid gem band: " + b);
        }
    }

    /** Positive weights 1..16..1, including both advertised endpoints. */
    public static int weight(Band band, int y) {
        return band.contains(y) ? 16 - Math.abs(y - band.centerY) : 0;
    }

    public static int sampleY(Random random, Band band) {
        int roll = random.nextInt(256);
        for (int y = band.minY; y <= band.maxY; y++) {
            roll -= weight(band, y);
            if (roll < 0) return y;
        }
        throw new AssertionError("Triangular weights");
    }

    public static List<Target> candidates(Rules rules, int destinationX, int destinationZ) {
        List<Target> result = new ArrayList<>();
        for (int ox = destinationX - 1; ox <= destinationX + 1; ox++)
            for (int oz = destinationZ - 1; oz <= destinationZ + 1; oz++)
                for (Target target : originCandidates(rules, ox, oz))
                    if ((target.x >> 4) == destinationX && (target.z >> 4) == destinationZ) result.add(target);
        return result;
    }

    public static List<Target> originCandidates(Rules rules, int ox, int oz) {
        List<Target> result = new ArrayList<>();
        for (String gem : GEMS) for (Band band : rules.gems.get(gem)) {
                    Random random = new Random(mix(rules.seed ^ ((long) ox << 32)
                            ^ Integer.toUnsignedLong(oz) ^ mix(gem.hashCode()) ^ mix(band.id.hashCode())));
                    int attempts = 1 + random.nextInt(2);
                    for (int attempt = 0; attempt < attempts; attempt++) {
                        int x = ox * 16 + random.nextInt(16), z = oz * 16 + random.nextInt(16);
                        int y = sampleY(random, band);
                        int count = 6 + random.nextInt(10);
                        var points = new ArrayList<int[]>();
                        for (int dx = -REACH; dx <= REACH; dx++) for (int dy = -REACH; dy <= REACH; dy++)
                            for (int dz = -REACH; dz <= REACH; dz++) if (dx * dx + dy * dy + dz * dz <= 6)
                                points.add(new int[]{dx, dy, dz});
                        Collections.shuffle(points, random);
                        for (int[] p : points.subList(0, count)) {
                            int px = x + p[0], py = y + p[1], pz = z + p[2];
                            if (band.contains(py))
                                result.add(new Target(gem, band.id, px, py, pz));
                        }
                    }
                }
        return result;
    }

    public static Kind outcome(int roll, int ordinaryRoll) {
        if (roll < 0 || roll >= 60) throw new IllegalArgumentException("Roll must be 0..59");
        if (ordinaryRoll < 0 || ordinaryRoll >= 10) throw new IllegalArgumentException("Ordinary roll must be 0..9");
        return roll < 6 ? ordinaryRoll < 7 ? Kind.ORDINARY : Kind.NONE
                : roll == 6 ? Kind.RUMOR : roll == 7 ? Kind.LEVEL : Kind.NONE;
    }

    public static Knowledge knowledge(Rules rules, UUID identity) {
        long knowledgeSeed = rules.seed ^ identity.getMostSignificantBits()
                ^ mix(identity.getLeastSignificantBits()) ^ 0x47656d4b6e6f77L;
        Random random = new Random(mix(knowledgeSeed));
        // An independent roll preserves existing rare assignments and hint facts.
        Kind kind = outcome(random.nextInt(60), new Random(mix(knowledgeSeed ^ 0x4f7264696e617279L)).nextInt(10));
        String gem = GEMS.get(random.nextInt(GEMS.size()));
        Band band = rules.gems.get(gem).get(random.nextInt(3));
        var decoys = rules.overworldBiomes.stream().filter(id -> rules.gems.get(gem).stream()
                .noneMatch(b -> b.id.equals(id))).toList();
        String decoy = decoys.isEmpty() ? "" : decoys.get(random.nextInt(decoys.size()));
        if (kind == Kind.RUMOR && decoy.isEmpty())
            throw new IllegalStateException("No installed ineligible overworld biome for rumor");
        return new Knowledge(kind, gem, band.id, decoy,
                band.minY + random.nextInt(31), random.nextBoolean());
    }

    public static boolean gemQuestion(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.matches("(?s).*\\b(gems?|rub(?:y|ies)|opals?|sapphires?|topaz(?:es)?|peridots?|diamonds?|emeralds?|amethysts?)\\b.*");
    }

    public static String requestedGem(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String gem : List.of("ruby", "opal", "sapphire", "topaz", "peridot", "diamond", "emerald", "amethyst")) {
            if (mentionsGem(lower, gem)) return gem;
        }
        return "";
    }

    public static String plural(String gem) { return gem.equals("ruby") ? "rubies" : gem + (gem.equals("topaz") ? "" : "s"); }
    private static boolean mentionsGem(String text, String gem) {
        String pattern = gem.equals("ruby") ? "rub(?:y|ies)" : gem.equals("topaz") ? "topaz(?:es)?" : gem + "s?";
        return text.matches("(?s).*\\b" + pattern + "\\b.*");
    }
    public static boolean unsolicitedGemTopic(Kind knowledge, int roll) {
        if (roll < 0 || roll >= 20) throw new IllegalArgumentException("Gem topic roll must be 0..19");
        return knowledge != Kind.NONE && roll == 0;
    }

    public static boolean validKnowledgeReply(Kind kind, String fact, String reply, Collection<String> biomes) {
        String lower = reply.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        String permitted = fact.toLowerCase(Locale.ROOT);
        if (reply.isBlank() || reply.length() > 600 || kind == Kind.NONE) return false;
        String gem = requestedGem(fact);
        if (gem.isEmpty() || !mentionsGem(lower, gem)) return false;
        var numbers = java.util.regex.Pattern.compile("-?\\d+").matcher(permitted);
        Set<String> allowedNumbers = new HashSet<>();
        while (numbers.find()) allowedNumbers.add(numbers.group());
        numbers = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(lower);
        Set<String> actualNumbers = new HashSet<>();
        while (numbers.find()) actualNumbers.add(numbers.group());
        if (!actualNumbers.containsAll(allowedNumbers)) return false;
        for (String direction : List.of("north", "south", "east", "west", "northeast", "northwest", "southeast", "southwest"))
            if (word(permitted, direction) && !word(lower, direction)) return false;
        return true;
    }

    private static boolean word(String text, String word) {
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word) + "\\b").matcher(text).find();
    }

    public static String biomeName(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder name = new StringBuilder();
        for (String word : path.split(" ")) {
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    public static String hint(Rules rules, Knowledge knowledge, String direction, boolean reverseRumor) {
        if (knowledge.kind == Kind.NONE) return "I don't know where to find gems.";
        String gems = plural(knowledge.gem);
        Band band = rules.gems.get(knowledge.gem).stream().filter(b -> b.id.equals(knowledge.biome)).findFirst().orElseThrow();
        return switch (knowledge.kind) {
            case NONE -> "I don't know where to find gems.";
            case ORDINARY -> knowledge.direction && !direction.isBlank()
                    ? "You might search for " + gems + " " + direction + " of here."
                    : knowledge.y <= band.centerY
                        ? heightHint(rules, gems, (Math.floorDiv(band.maxY, 10) + 2) * 10, false)
                        : heightHint(rules, gems, (Math.floorDiv(band.minY, 10) - 1) * 10, true);
            case RUMOR -> "The rumor mentions " + gems + " in "
                    + biomeName(reverseRumor ? knowledge.decoy : knowledge.biome) + " or "
                    + biomeName(reverseRumor ? knowledge.biome : knowledge.decoy) + "; I cannot vouch for which.";
            case LEVEL -> "You could try looking for " + gems + " around level " + knowledge.y + ".";
        };
    }

    private static String heightHint(Rules rules, String gems, int suggested, boolean above) {
        int height = Math.max(rules.minY, Math.min(rules.maxY, suggested));
        String relation = above ? "above" : "below";
        if (above && height == rules.minY || !above && height == rules.maxY)
            relation = "at or " + relation;
        return "You could search for " + gems + " " + relation + " level " + height + ".";
    }
}
