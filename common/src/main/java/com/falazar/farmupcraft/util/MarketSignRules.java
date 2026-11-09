package com.falazar.farmupcraft.util;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class MarketSignRules {
    private MarketSignRules() {}

    public static boolean isMarketSignSide(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (String line : lines) {
            if (line == null) continue;
            String cleaned = clean(line);
            if (!cleaned.isEmpty()) {
                joiner.add(cleaned);
            }
        }
        String combined = joiner.toString().replaceAll("\\s+", " ").trim();
        return matchesPhrase(combined);
    }

    static String clean(String raw) {
        // Strip Minecraft formatting codes, brackets/punctuation at ends, and collapse whitespace.
        String withoutFormatting = raw.replaceAll("(?i)\u00A7[0-9A-FK-OR]", "");
        String normalized = withoutFormatting.toLowerCase(Locale.ROOT)
                .replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private static boolean matchesPhrase(String normalized) {
        return "market".equals(normalized) || "click for market".equals(normalized);
    }
}
