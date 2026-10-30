package com.falazar.farmupcraft.util;

import java.time.LocalDate;
import java.util.List;

public final class PlayerOnboarding {
    public static final long UNSET_DAY = Long.MIN_VALUE;
    public static final String WELCOME = "Welcome to FarmUpCraft! Use /farmupcraft help for player commands.";
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
}
