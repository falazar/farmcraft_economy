package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.BiomeCropRulesData;
import com.falazar.farmupcraft.data.BiomeRulesData;
import com.google.common.collect.ImmutableList;
import com.pam.pamhc2crops.setup.ItemRegistration;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

import java.util.function.Consumer;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class BiomeRulesDataDataGenerator extends BiomeRulesDataDataProvider {
    public BiomeRulesDataDataGenerator(PackOutput pOutput, String modid) {
        super(pOutput, modid);
    }

    @Override
    protected void buildBiomeRuleData(Consumer<BiomeRulesDataConsumer> pWriter) {
        pWriter.accept(new BiomeRulesDataConsumer(prefix("desert_rules"), new BiomeRulesData(
                Tags.Biomes.IS_DESERT,
                BiomeCropRulesData.EMPTY
        )));

        pWriter.accept(new BiomeRulesDataConsumer(prefix("plains_rules"), new BiomeRulesData(
                Tags.Biomes.IS_PLAINS,
                new BiomeCropRulesData(
                        ImmutableList.of(
                                ItemRegistration.amaranthseeditem.get(),
                                ItemRegistration.artichokeseeditem.get(),
                                ItemRegistration.brusselsproutseeditem.get(),
                                ItemRegistration.chiaseeditem.get(),
                                ItemRegistration.kaleseeditem.get(),
                                ItemRegistration.leekseeditem.get(),
                                ItemRegistration.lettuceseeditem.get(),
                                ItemRegistration.mulberryseeditem.get(),
                                ItemRegistration.nettlesseeditem.get(),
                                ItemRegistration.onionseeditem.get(),
                                ItemRegistration.peasseeditem.get(),
                                ItemRegistration.raspberryseeditem.get(),
                                ItemRegistration.ryeseeditem.get(),
                                ItemRegistration.spiceleafseeditem.get(),
                                ItemRegistration.taroseeditem.get()
                                ),
                        15

                )
        )));

        // Define the Forest Biome Rules
        pWriter.accept(new BiomeRulesDataConsumer(prefix("forest_rules"), new BiomeRulesData(
                BiomeTags.IS_FOREST,
                new BiomeCropRulesData(
                        ImmutableList.of(
                                Items.CARROT,
                                ItemRegistration.barleyseeditem.get(),
                                ItemRegistration.bellpepperseeditem.get(),
                                ItemRegistration.broccoliseeditem.get(),
                                ItemRegistration.cauliflowerseeditem.get(),
                                ItemRegistration.chiaseeditem.get(),
                                ItemRegistration.cloudberryseeditem.get(),
                                ItemRegistration.eggplantseeditem.get(),
                                ItemRegistration.huckleberryseeditem.get(),
                                ItemRegistration.kaleseeditem.get(),
                                ItemRegistration.kenafseeditem.get(),
                                ItemRegistration.lentilseeditem.get(),
                                ItemRegistration.nopalesseeditem.get(),
                                ItemRegistration.onionseeditem.get(),
                                ItemRegistration.radishseeditem.get()
                        ),
                        15
                )
        )));

        // Define the Beach Biome Rules
        pWriter.accept(new BiomeRulesDataConsumer(prefix("beach_rules"), new BiomeRulesData(
                BiomeTags.IS_BEACH,
                new BiomeCropRulesData(
                        ImmutableList.of(
                                Items.CARROT,
                                Items.POTATO,
                                ItemRegistration.arrowrootseeditem.get(),
                                ItemRegistration.calabashseeditem.get(),
                                ItemRegistration.cottonseeditem.get(),
                                ItemRegistration.eggplantseeditem.get(),
                                ItemRegistration.garlicseeditem.get(),
                                ItemRegistration.grapeseeditem.get(),
                                ItemRegistration.guaranaseeditem.get(),
                                ItemRegistration.lentilseeditem.get(),
                                ItemRegistration.lotusseeditem.get(),
                                ItemRegistration.mulberryseeditem.get(),
                                ItemRegistration.nopalesseeditem.get(),
                                ItemRegistration.pineappleseeditem.get(),
                                ItemRegistration.ryeseeditem.get()
                        ),
                        10
                )
        )));
    }
}
