package com.falazar.farmupcraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

public final class NpcPrompts {
    private static final Pattern SLOT = Pattern.compile("\\{([a-z_]+)\\}");

    private NpcPrompts() {}

    public static String base(Path config, String name, String description, String personality,
                              String player, String message) {
        return render(load(config, "npc-conversation.txt"), Map.of(
                "npc_name", name, "description", description, "personality", personality,
                "player_name", player, "player_message", message));
    }

    public static String gems(Path config, String mode, String fact) {
        String file = load(config, "npc-gems.txt");
        var headers = Pattern.compile("(?m)^\\[(COMMON|IGNORANCE|RUMOR|ORDINARY|LEVEL)\\]\\s*$").matcher(file);
        Map<String, String> sections = new java.util.LinkedHashMap<>();
        String previous = null;
        int start = 0;
        while (headers.find()) {
            if (previous != null) sections.put(previous, file.substring(start, headers.start()).trim());
            previous = headers.group(1);
            if (sections.containsKey(previous)) throw new IllegalArgumentException("Duplicate NPC gem prompt section " + previous);
            start = headers.end();
        }
        if (previous != null) sections.put(previous, file.substring(start).trim());
        for (String section : java.util.List.of("COMMON", "IGNORANCE", "RUMOR", "ORDINARY", "LEVEL"))
            if (!sections.containsKey(section) || sections.get(section).isBlank())
                throw new IllegalArgumentException("Missing NPC gem prompt section " + section);
        if (!sections.containsKey(mode) || mode.equals("COMMON"))
            throw new IllegalArgumentException("Unknown NPC gem prompt mode " + mode);
        return "\n" + render(sections.get("COMMON") + "\n" + sections.get(mode),
                Map.of("gem_mode", mode, "gem_fact", fact));
    }

    private static synchronized String load(Path config, String filename) {
        Path path = config.resolve("farmupcraft").resolve("prompts").resolve(filename);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                try (var stream = NpcPrompts.class.getResourceAsStream("/prompts/" + filename)) {
                    if (stream == null) throw new IOException("Missing bundled NPC prompt " + filename);
                    Files.copy(stream, path);
                }
            }
            String text = Files.readString(path);
            if (text.isBlank()) throw new IOException("NPC prompt is empty: " + path);
            return text;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load NPC prompt " + path, error);
        }
    }

    static String render(String template, Map<String, String> values) {
        for (String key : values.keySet())
            if (!template.contains("{" + key + "}"))
                throw new IllegalArgumentException("NPC prompt is missing placeholder {" + key + "}");
        return SLOT.matcher(template).replaceAll(match -> {
            String value = values.get(match.group(1));
            if (value == null) throw new IllegalArgumentException("Unknown NPC prompt placeholder " + match.group());
            return java.util.regex.Matcher.quoteReplacement(value);
        });
    }
}
