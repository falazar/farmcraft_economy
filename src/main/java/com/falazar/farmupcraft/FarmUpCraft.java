package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.ManagersCommand;
import com.falazar.farmupcraft.command.PlotCommand;
import com.falazar.farmupcraft.command.ShowBiomesCommand;
import com.falazar.farmupcraft.data.rules.crop.CropRules;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.setup.Registration;
import com.falazar.farmupcraft.database.DataBaseCommand;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FileInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.DataPackRegistryEvent;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.Locale;


// The value here should match an entry in the META-INF/mods.toml file
@Mod(FarmUpCraft.MODID)
public class FarmUpCraft {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "farmupcraft";
    // Directly reference a slf4j logger
    public static final CustomLogger LOGGER = new CustomLogger(FarmUpCraft.class.getSimpleName());


    public FarmUpCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        modEventBus.addListener(this::commonSetup);
        Registration.init();

        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(FUCRegistries.Keys.CROP_RULES, CropRules.DIRECT_CODEC, CropRules.DIRECT_CODEC);
        });

        // Load the version from gradle.properties
        LOGGER.info("FarmUpCraft initialized successfully!");
        String modVersion = ModList.get().getModContainerById(MODID)
                .map(ModContainer::getModInfo)
                .map(IModInfo::getVersion)
                .map(ArtifactVersion::toString)
                .orElse("[UNKNOWN]");



        String buildTime = FileInfo.getJarModificationTime(FarmUpCraft.class);

        // Create a detailed startup message
        String startupMessage = generateStartupMessage(
                MODID,
                MODID,
                "MIT",
                modVersion,
                "47.1.3",
                buildTime
        );

        // Log the startup message
        LOGGER.info("\n" + startupMessage);
    }

    // Method to generate the startup message
    private String generateStartupMessage(String modId, String modName, String modLicense, String modVersion, String forgeVersion, String buildTime) {
        String border = "#".repeat(35);
        String format = "# %-31s #\n";
        return String.format(
                "%s\n" +
                        String.format(format, "MODID: " + modId) +
                        String.format(format, "Name: " + modName) +
                        String.format(format, "License: " + modLicense) +
                        String.format(format, "Version: " + modVersion) +
                        String.format(format, "Forge Version: " + forgeVersion) +
                        String.format(format, "Build Time: " + buildTime) +
                        "%s",
                border, border
        );
    }

    public static ResourceLocation prefix(String name) {
        return new ResourceLocation(MODID, name.toLowerCase(Locale.ROOT));
    }


    public void commands(RegisterCommandsEvent e) {
        ShowBiomesCommand.register(e.getDispatcher());
        ManagersCommand.register(e.getDispatcher());
        DataBaseCommand.register(e.getDispatcher());
        PlotCommand.register(e.getDispatcher());
    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        EDBMessages.register();

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
