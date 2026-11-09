package com.falazar.farmupcraft.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface SpeechTextAccessor {
    @Invoker("setText")
    void farmupcraft$text(Component text);

    @Invoker("setLineWidth")
    void farmupcraft$lineWidth(int width);

    @Invoker("setTextOpacity")
    void farmupcraft$opacity(byte opacity);

    @Invoker("setBackgroundColor")
    void farmupcraft$background(int color);

    @Invoker("setFlags")
    void farmupcraft$flags(byte flags);
}
