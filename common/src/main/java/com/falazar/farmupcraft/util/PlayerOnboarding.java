package com.falazar.farmupcraft.util;

import java.time.LocalDate;
import java.util.List;

public final class PlayerOnboarding {
    public static final long UNSET_DAY = Long.MIN_VALUE;
    public static final String WELCOME = "Welcome to FarmUpCraft! Use /farmupcraft help for player commands.";
    public static final String MENU_REMINDER = "Use /menu or press Alt+F to see all the options!";
    public static final long MARKET_REMINDER_DELAY_TICKS = 15L * 60L * 20L; // 15 minutes at 20 TPS (18,000 ticks)
    public static final long MARKET_REMINDER_DELAY_MS = 15L * 60L * 1000L; // 15 minutes in milliseconds
    public static final List<String> MARKET_REMINDERS = List.of(
            "Be sure and check the marketplace today with /market show!",
            "Don't forget to check the marketplace! Run /market show to see today's prices.",
            "Hey! Be sure and check the marketplace — type /market show to browse what's buying.",
            "Got extra goods on hand? Be sure and check the marketplace using /market show!",
            "Friendly reminder: be sure and check the marketplace with /market show!",
            "Curious what's in demand today? Be sure and check the marketplace via /market show!",
            "Your pockets could use some extra coins — be sure and check the marketplace with /market show!",
            "Take a moment to check the marketplace! Use /market show to view active deals.",
            "Have you visited the market yet? Be sure and check the marketplace with /market show!",
            "Merchants are buying! Be sure and check the marketplace by running /market show.",
            "Be sure and check the marketplace (/market show) to see which crops, wood, and stone pay best today!",
            "Looking to earn some coins for your village? Be sure and check the marketplace with /market show!",
            "Before your chests fill up, be sure and check the marketplace using /market show!",
            "Today's market rotation is open — be sure and check the marketplace with /market show!",
            "Need coins for plots or upkeep? Be sure and check the marketplace with /market show!",
            "Quick tip: be sure and check the marketplace (/market show) to spot high-value items!",
            "Traders have fresh offers waiting — be sure and check the marketplace with /market show!",
            "See what your harvest is worth today! Be sure and check the marketplace with /market show.",
            "Time for a market check! Be sure and check the marketplace by typing /market show.",
            "Don't miss out on good prices — be sure and check the marketplace with /market show!");
    public static final List<String> HELP = List.of(
            "FarmUpCraft player commands",
            "/player info - View your coins and village.",
            "/market show <food|wood|stone|general> - See what the market buys.",
            "/market sell <food|wood|stone|general> - Sell matching items from your inventory.",
            "/market buy villager - Buy a villager.",
            "/village list - Browse villages.",
            "/village info - View village details.",
            "/village buy <name> - Create a village.",
            "/village invite <player> - Invite a village member.",
            "/village accept - Accept a village invitation.",
            "/player leavevillage - Leave your village.",
            "/player givevillagecoins <amount> - Donate coins to your village.",
            "/village farms - Inspect village farms.",
            "/village animals - Inspect village animals.",
            "/village chores - Get suggestions for things to do.",
            "/plot info - Inspect the current plot.",
            "/plot buy <type> - Buy a plot (for example farm, nursery, or pasture).",
            "/player border show - Toggle village borders.",
            "/frecipe <itemId> - Look up an item's recipe (requires JEI).",
            "/show biomecrops - List crops you can plant in your current biome.",
            "/show biomecrops <biome> - List crops you can plant in a named biome (for example plains).",
            "/show cropbiomes <crop> - List biomes where you can plant a crop (for example wheat).",
            "/build pull_wall <distance> - Preview pulling a 15-block-high wall 1-10 blocks toward you.",
            "/build push_building <distance> - Preview pushing a building 1-10 blocks away from you.",
            "/build confirm - Confirm the previewed move and pay its gold cost.",
            "/build cancel - Cancel build selection or preview without paying.",
            "Use command suggestions for arguments and additional village/member actions.");

    private PlayerOnboarding() {}

    public static boolean shouldWelcome(long firstDay, LocalDate today) {
        long day = today.toEpochDay();
        return firstDay != UNSET_DAY && firstDay <= day && firstDay > day - 5;
    }

    public static String marketReminder(int index) {
        return MARKET_REMINDERS.get(Math.floorMod(index, MARKET_REMINDERS.size()));
    }
}
