package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.GameZone;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.LanBotLauncher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

public final class CommandSuggestions {
    private CommandSuggestions() {
    }

    public static CompletableFuture<Suggestions> onlinePlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder);
    }

    public static CompletableFuture<Suggestions> runningBots(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(LanBotLauncher.getRunningBotNames(), builder);
    }

    public static CompletableFuture<Suggestions> villageNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ModEvents.getVillageDatabase(context.getSource().getLevel())
                .getValues().stream().map(village -> village.getName()), builder);
    }

    public static CompletableFuture<Suggestions> villageIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ModEvents.getVillageDatabase(context.getSource().getLevel())
                .getValues().stream().map(village -> village.getUUID().toString()), builder);
    }

    public static CompletableFuture<Suggestions> npcNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ModEvents.getNpcDatabase(context.getSource().getLevel())
                .getValues().stream().map(npc -> npc.getName()), builder);
    }

    public static CompletableFuture<Suggestions> zoneNames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ModEvents.getGameZoneDatabase(context.getSource().getLevel())
                .getValues().stream().map(zone -> zone.getName()), builder);
    }

    public static CompletableFuture<Suggestions> zoneTypes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(java.util.Arrays.stream(GameZone.ZoneType.values())
                .map(type -> type.name().toLowerCase(Locale.ROOT)), builder);
    }

    public static CompletableFuture<Suggestions> biomes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ShowBiomesCommand.biomeAliases(context.getSource()).keySet(), builder);
    }

    public static CompletableFuture<Suggestions> marketTypes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("food", "general", "wood", "stone"), builder);
    }

    public static CompletableFuture<Suggestions> marketItems(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ModEvents.getGoodsDataDatabase(context.getSource().getLevel())
                .getKeys(), builder);
    }

    public static CompletableFuture<Suggestions> itemIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.entrySet().stream()
                .map(entry -> entry.getKey().identifier().toString()), builder);
    }

    public static CompletableFuture<Suggestions> dimensions(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Iterable<ServerLevel> levels = context.getSource().getServer().getAllLevels();
        return SharedSuggestionProvider.suggest(StreamSupport.stream(levels.spliterator(), false)
                .map(level -> level.dimension().identifier().toString()), builder);
    }

    public static CompletableFuture<Suggestions> npcBehaviors(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("nonhostile", "wary", "hostile"), builder);
    }

    public static CompletableFuture<Suggestions> activeStatuses(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("active", "inactive", "true", "false"), builder);
    }

    public static CompletableFuture<Suggestions> clearAllConfirmation(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("forreal"), builder);
    }
}