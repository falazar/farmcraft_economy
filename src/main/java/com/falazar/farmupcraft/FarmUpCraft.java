package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.ShowBiomesCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Locale;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(FarmUpCraft.MODID)
public class FarmUpCraft {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "farmupcraft";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();


    public FarmUpCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.addListener(this::commands);

    }

    public static ResourceLocation prefix(String name) {
        return new ResourceLocation(MODID, name.toLowerCase(Locale.ROOT));
    }


    public void commands(RegisterCommandsEvent e) {
        ShowBiomesCommand.register(e.getDispatcher());
    }
}


/*

TODO:
Command to show nearby biomes?
Command to show all biomes in this chuck?
Chunk Biome setting or command?
Command to show /biomecrop strawberry
    /biomecrop garlic onion chilepepper  (combo list of up to 3) shows where can plant both.
    /cropbiome swamp

 */
