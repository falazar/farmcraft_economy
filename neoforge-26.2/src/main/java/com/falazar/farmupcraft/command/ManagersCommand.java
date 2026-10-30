package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.BiomeRulesDataJsonManager;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.server.command.EnumArgument;

public class ManagersCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("managers")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("clear")
                        .then(Commands.argument("managerType", EnumArgument.enumArgument(ManagerType.class))
                                .executes(context -> update(context, false))))
                .then(Commands.literal("repopulate")
                        .then(Commands.argument("managerType", EnumArgument.enumArgument(ManagerType.class))
                                .executes(context -> update(context, true)))));
    }

    private static int update(CommandContext<CommandSourceStack> context, boolean repopulate) {
        var source = context.getSource();
        ManagerType type = context.getArgument("managerType", ManagerType.class);
        if (type != ManagerType.BIOME_RULES) {
            source.sendFailure(Component.literal("Unknown manager type."));
            return 0;
        }
        if (repopulate) {
            BiomeRulesDataJsonManager.populateBiomeRulesInstances(source.getLevel());
        } else {
            BiomeRulesManager.get(source.getLevel()).clear();
        }
        source.sendSuccess(() -> Component.literal(type.name() + " manager "
                + (repopulate ? "repopulated." : "cleared.")), true);
        return 1;
    }

    public enum ManagerType {
        BIOME_RULES("biome_rules"), NONE("none");

        private final String name;

        ManagerType(String name) { this.name = name; }

        public String getName() { return name; }

        public static ManagerType getByName(String name) {
            for (ManagerType type : values()) {
                if (type.name.equalsIgnoreCase(name)) return type;
            }
            return NONE;
        }
    }
}
