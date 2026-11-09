package com.falazar.farmupcraft.util;

import java.util.random.RandomGenerator;

public final class StructureNoteFlavor {
    private static final String[] STYLES = {
            "a weary explorer's journal", "a worried caretaker's warning", "a smuggler's coded letter",
            "a scholar's strange discovery", "a guard's unfinished report", "a playful but ominous riddle",
            "an apprentice's failed experiment", "a traveler's farewell", "a quartermaster's suspicious inventory",
            "a local legend told by an unreliable witness", "a secret society's instructions", "a lost messenger's confession"
    };
    private static final String[] DETAILS = {
            "a bell that rings without wind", "fresh footprints that end at a wall", "a key made of blue glass",
            "a map with one room scratched out", "a lantern burning beneath water", "a missing shipment of salt",
            "a locked door that hums at dawn", "a compass pointing downward", "a coin that returns to its owner",
            "a clock that counts backward", "a familiar voice behind an empty door", "an unsigned letter in green ink"
    };
    private static final String[] OPENINGS = {
            "I came here to catalogue the rooms, but the list changes every morning.",
            "The caretaker insisted that everything was ordinary. His hands would not stop shaking.",
            "Our supplies arrived a day before we ordered them.",
            "Someone has been correcting my journal while I sleep.",
            "The last patrol left its boots neatly beside the doorway.",
            "We agreed never to mention what happened during the festival.",
            "The apprentice says the experiment succeeded. Nobody remembers his teacher.",
            "If this letter reaches home, tell them I found the place on the old map.",
            "The smugglers paid in coins from a kingdom that never existed.",
            "I counted the windows twice. The second count included one beneath the floor.",
            "We found a table set for exactly our number, with one extra chair.",
            "The guard asked for my name, then checked a ledger written a century ago."
    };
    private static final String[] ENDINGS = {
            "Do not answer if someone calls from the room you just left.",
            "Leave the last candle burning; it is not for us.",
            "Whatever you hear at dawn, wait until the second bell.",
            "The return route is marked in green ink, unless the ink has changed.",
            "Please return the key before the keeper notices its absence.",
            "I have hidden the truth between the lines, where it cannot see it.",
            "We should have listened when the horses refused to enter.",
            "The empty chair must stay empty.",
            "If the walls begin to hum, stop counting.",
            "The map is accurate only while nobody is looking at it.",
            "I cannot tell whether this is a warning or an invitation.",
            "Promise me you will not open anything bearing my seal."
    };

    private StructureNoteFlavor() {}

    public static String prompt(String structure, RandomGenerator random) {
        return "Write a found note for " + structure + " in a medieval fantasy game. "
                + "Use " + STYLES[random.nextInt(STYLES.length)] + " and a detail about "
                + DETAILS[random.nextInt(DETAILS.length)] + ". "
                + "Vary narrator, motive, mood, vocabulary and opening. It may be mysterious, wistful, humorous, "
                + "suspicious or ominous; do not always use a villain threatening trespassers. "
                + "This is atmospheric fiction, not a real quest: do not promise rewards, give exact coordinates, "
                + "or claim a hidden passage actually exists. Write 1 to 3 sentences, at most 600 characters. "
                + "Return ONLY the note text, no title, quotes, labels or numbering.";
    }

    public static String batchPrompt(String structure, int count, RandomGenerator random) {
        return prompt(structure, random) + " Produce " + count + " DISTINCT notes with different narrators, "
                + "situations and moods. Draw from journals, letters, riddles, reports, folklore, confessions and warnings. "
                + "No repeated openings or paraphrases. Separate each note with exactly ---NEXT---.";
    }

    public static String[] fallback(RandomGenerator random) {
        int style = random.nextInt(STYLES.length);
        String[] titles = {"Explorer's Journal", "Caretaker's Warning", "Coded Letter", "Research Fragment",
                "Unfinished Report", "Curious Riddle", "Apprentice's Scrap", "Farewell Letter",
                "Inventory Margin", "Local Legend", "Sealed Instructions", "Messenger's Confession"};
        return new String[]{titles[style], OPENINGS[random.nextInt(OPENINGS.length)] + " "
                + ENDINGS[random.nextInt(ENDINGS.length)]};
    }
}
