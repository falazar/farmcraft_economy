package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.ShowNameAliases;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ShowBiomesCommand {
    public static final CustomLogger LOGGER = new CustomLogger(ShowBiomesCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("show")
                .then(Commands.literal("plotcrops").executes(c -> showPlotCrops(c.getSource())))
                .then(Commands.literal("biomes")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                                        .executes(c -> showBiomes(c, Vec3Argument.getVec3(c, "pos"),
                                                IntegerArgumentType.getInteger(c, "radius"))))))
                .then(Commands.literal("cropbiomes")
                        .then(Commands.argument("crop", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        cropAliases(context.getSource()).keySet(), builder))
                                .executes(c -> {
                            showCropBiomes(c.getSource(), StringArgumentType.getString(c, "crop"));
                            return 0;
                        })))
                .then(Commands.literal("biomecrops")
                        .executes(c -> showCurrentBiomeCrops(c.getSource()))
                        .then(Commands.argument("biome", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        biomeAliases(context.getSource()).keySet(), builder))
                                .executes(c -> {
                            showBiomeCrops(c.getSource(), StringArgumentType.getString(c, "biome"));
                            return 0;
                        }))));
    }

    public static int showPlotCrops(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /show plotcrops."));
            return 0;
        }
        var manager = BiomeRulesManager.get(source.getLevel());
        if (!manager.hasRules()) {
            source.sendFailure(Component.literal("No biome crop rules are configured."));
            return 0;
        }
        var chunk = ChunkPos.containing(player.blockPosition());
        Map<String, Holder<Biome>> biomes = new TreeMap<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                var biome = source.getLevel().getBiome(new net.minecraft.core.BlockPos(
                        chunk.x() * 16 + x, player.blockPosition().getY() - 1, chunk.z() * 16 + z));
                biome.unwrapKey().ifPresent(key -> biomes.put(key.identifier().toString(), biome));
            }
        }
        Set<Item> crops = new LinkedHashSet<>();
        MutableComponent message = Component.literal("Plot biomes: ").withStyle(ChatFormatting.AQUA);
        for (var entry : biomes.entrySet()) {
            if (!crops.isEmpty() || message.getSiblings().size() > 0) message.append(", ");
            message.append(Component.translatable(CropsManager.getBiomeLangKey(Identifier.parse(entry.getKey())))
                    .withStyle(ChatFormatting.YELLOW));
            var rules = manager.getBiomeRules(entry.getValue());
            if (rules != null) crops.addAll(rules.getCrops(source.getLevel()));
            else message.append(Component.literal(" (no crop rules)").withStyle(ChatFormatting.GRAY));
        }
        message.append(Component.literal("\nCombined crops for this plot: ").withStyle(ChatFormatting.AQUA))
                .append(com.falazar.farmupcraft.util.CropListChat.crops(crops, player));
        message.append(Component.literal("\nBiome-specific planting restrictions still apply; farm level 3+ allows crops from any biome in the chunk.")
                .withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> message, false);
        return 1;
    }

    public static int showCurrentBiomeCrops(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /show biomecrops without a biome."));
            return 0;
        }
        var key = player.level().getBiome(player.blockPosition()).unwrapKey();
        if (key.isEmpty()) {
            source.sendFailure(Component.literal("Your current biome has no registered identifier."));
            return 0;
        }
        showBiomeCrops(source, key.get().identifier().toString());
        return 0;
    }

    static Item resolveCrop(String name) {
        if (name.contains(":")) {
            Identifier id = Identifier.tryParse(name);
            return id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        }

        Identifier pamId = Identifier.tryParse("pamhc2crops:" + name + "item");
        if (pamId == null) return Items.AIR;
        Item item = BuiltInRegistries.ITEM.getValue(pamId);
        if (item != Items.AIR) return item;
        String path = switch (name) {
            case "wheat" -> "wheat_seeds";
            case "beetroot" -> "beetroot_seeds";
            default -> name;
        };
        Identifier vanillaId = Identifier.tryParse("minecraft:" + path);
        return vanillaId == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(vanillaId);
    }

    static Map<String, String> cropAliases(CommandSourceStack source) {
        return ShowNameAliases.create(BiomeRulesManager.get(source.getLevel()).getItems().stream()
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).toList(), true);
    }

    static Map<String, String> biomeAliases(CommandSourceStack source) {
        return ShowNameAliases.create(source.registryAccess().lookupOrThrow(Registries.BIOME)
                .listElementIds().map(key -> key.identifier().toString()).toList(), false);
    }

    public static void showCropBiomes(CommandSourceStack source, String cropName) {
        BiomeRulesManager manager = BiomeRulesManager.get(source.getLevel());
        if (!manager.hasRules() || !manager.hasItems()) {
            source.sendFailure(Component.literal("No biome crop rules are configured."));
            return;
        }
        Item cropItem = resolveCrop(cropAliases(source).getOrDefault(cropName, cropName));
        if (cropItem == Items.AIR) {
            source.sendFailure(Component.literal("Crop not found: " + cropName));
            return;
        }
        Component biomes = getBiomeCropsChat(source.getPlayer(), manager, cropItem);
        source.sendSuccess(() -> Component.literal("§bBiomes you can plant " + cropName + " in: ").append(biomes), false);
    }

    private static Set<String> getPlayerVillageBiomeSet(Player player) {
        if (player == null) return Set.of();
        PlayerData data = ModEvents.getPlayerDatabase(player.level()).getData(player.getUUID());
        if (data == null || data.getHomeVillageUUID() == null) return Set.of();
        VillageData village = ModEvents.getVillageDatabase(player.level()).getData(data.getHomeVillageUUID());
        return village == null ? Set.of()
                : new HashSet<>(VillageCommand.getVillageBiomes(village, player.level()).keySet());
    }

    public static Component getBiomeCropsChat(Player player, BiomeRulesManager manager, Item cropItem) {
        Set<String> villageBiomes = getPlayerVillageBiomeSet(player);
        return manager.getBiomesForItem(cropItem).stream()
                .flatMap(biome -> biome.unwrapKey().stream())
                .distinct()
                .map(key -> Component.translatable(CropsManager.getBiomeLangKey(key.identifier()))
                        .withStyle(villageBiomes.contains(key.identifier().toString())
                                ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                .sorted(Comparator.comparing(Component::getString))
                .reduce((first, second) -> first.append(", ").append(second))
                .orElse(Component.literal("None"));
    }

    public static void showBiomeCrops(CommandSourceStack source, String biomeName) {
        BiomeRulesManager manager = BiomeRulesManager.get(source.getLevel());
        if (!manager.hasRules()) {
            source.sendFailure(Component.literal("No biome crop rules are configured."));
            return;
        }
        var registry = source.registryAccess().lookupOrThrow(Registries.BIOME);
        Identifier id = Identifier.tryParse(biomeAliases(source).getOrDefault(biomeName, biomeName));
        Optional<Holder.Reference<Biome>> biome = id == null ? Optional.empty()
                : registry.get(ResourceKey.create(Registries.BIOME, id));
        if (biome.isEmpty() && !biomeName.contains(":")) {
            Identifier bopId = Identifier.tryParse("biomesoplenty:" + biomeName);
            if (bopId != null) biome = registry.get(ResourceKey.create(Registries.BIOME, bopId));
        }
        if (biome.isEmpty()) {
            source.sendFailure(Component.literal("Biome not found: " + biomeName));
            return;
        }
        BiomeRulesInstance instance = manager.getBiomeRules(biome.get());
        if (instance == null) {
            source.sendFailure(Component.literal("No crop rules for biome: " + biomeName));
            return;
        }
        Component crops = com.falazar.farmupcraft.util.CropListChat.crops(
                instance.getCrops(source.getLevel()), source.getPlayer());
        source.sendSuccess(() -> Component.literal("§bCrops you can plant in " + biomeName + ": ").append(crops), false);
    }

    public static int showBiomes(CommandContext<CommandSourceStack> context, Vec3 pos, int radius) {
        if (radius < 0) {
            context.getSource().sendFailure(Component.literal("Radius must be non-negative."));
            return 0;
        }
        var level = context.getSource().getLevel();
        Set<Identifier> biomes = new TreeSet<>(Comparator.comparing(Identifier::toString));
        ChunkPos center = ChunkPos.containing(net.minecraft.core.BlockPos.containing(pos));
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        double radiusSquared = (double) radius * radius;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunk = new ChunkPos(center.x() + dx, center.z() + dz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        var block = chunk.getBlockAt(x, 0, z);
                        if (block.distToCenterSqr(pos.x(), 0, pos.z()) <= radiusSquared) {
                            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    block.getX(), block.getZ());
                            level.getBiome(block.atY(surfaceY)).unwrapKey()
                                    .ifPresent(key -> biomes.add(key.identifier()));
                        }
                    }
                }
            }
        }
        if (biomes.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No biomes found within the given radius."));
        } else {
            MutableComponent response = Component.literal("Surface biomes within radius " + radius + " blocks: ");
            response.append(String.join(", ", biomes.stream().map(Identifier::getPath).toList()));
            context.getSource().sendSuccess(() -> response, false);
        }
        return 0;
    }
}
