package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.command.EnumArgument;

public class ManagersCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("managers")
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Define the "clear" sub-command
        LiteralArgumentBuilder<CommandSourceStack> clearBuilder = Commands.literal("clear")
                .then(Commands.argument("managerType", EnumArgument.enumArgument(ManagerType.class))
                        .executes(context -> clearManager(context, context.getArgument("managerType", ManagerType.class))))
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Add the "clear" sub-command to the "managers" command
        builder.then(clearBuilder);

        // Register the main "managers" command with the dispatcher
        dispatcher.register(builder);
    }

    private static int clearManager(CommandContext<CommandSourceStack> context, ManagerType managerType) {
        CommandSourceStack source = context.getSource();
        ServerLevel serverLevel = source.getLevel();

        // Use the managerType to get the appropriate manager
        switch (managerType) {
            case BIOME_RULES:
                BiomeRulesManager biomeRulesManager = BiomeRulesManager.get(serverLevel);
                biomeRulesManager.clear();  // Make sure you have a clearRules method in BiomeRulesManager
                break;
            // Add cases for other managers here
            case NONE:
            default:
                source.sendFailure(Component.literal("Unknown manager type."));
                return 0;
        }

        source.sendSuccess(() -> Component.literal(managerType.name() + " manager cleared."), true);
        return 1;
    }

    public enum ManagerType {
        BIOME_RULES("biome_rules"),
        NONE("none");

        private final String name;

        ManagerType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static ManagerType getByName(String name) {
            for (ManagerType type : values()) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return ManagerType.NONE;  // Or throw an exception
        }
    }
}

