package com.falazar.farmupcraft.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameModeCommand.class)
public abstract class GameModeCommandMixin {
    @Redirect(method = {
            "setMode",
            "setGameMode(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/GameType;)Z"
    }, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;setDefaultGameType(Lnet/minecraft/world/level/GameType;)V"),
            require = 2, allow = 2)
    private static void farmupcraft$keepPersonalModeSeparate(MinecraftServer server, GameType type) {
        // Personal mode changes must not alter LAN join defaults; /defaultgamemode remains explicit.
    }
}
