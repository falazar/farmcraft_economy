package com.falazar.farmupcraft.datagen.custom;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.rules.crop.ItemListCropRule;
import com.falazar.farmupcraft.data.rules.crop.TagBasedRandomCropRule;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BiomeTags;
import net.minecraftforge.common.Tags;

import java.util.function.Consumer;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

public class BiomeRulesDataDataGenerator extends BiomeRulesDataDataProvider {
    public BiomeRulesDataDataGenerator(PackOutput pOutput, String modid) {
        super(pOutput, modid);
    }

    @Override
    protected void buildBiomeRuleData(Consumer<BiomeRulesDataConsumer> pWriter) {
        //pWriter.accept(new BiomeRulesDataConsumer(prefix("desert_rules"), new BiomeRulesData(
        //        Tags.Biomes.IS_DESERT,
        //        ItemListCropRule.EMPTY
        //)));
        pWriter.accept(new BiomeRulesDataConsumer(prefix("overworld_rules"), new BiomeRulesData(
                BiomeTags.IS_OVERWORLD,
                ItemListCropRule.EMPTY
        )));
        pWriter.accept(new BiomeRulesDataConsumer(prefix("bop_rules"), new BiomeRulesData(
                FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG,
                new TagBasedRandomCropRule(
                        FUCTags.VANILLA_AND_MODDED_CROPS,
                        15
                )
        )));

        //pWriter.accept(new BiomeRulesDataConsumer(prefix("plains_rules"), new BiomeRulesData(
        //        Tags.Biomes.IS_PLAINS,
        //        new TagBasedRandomCropRule(
        //                FUCTags.VANILLA_AND_MODDED_CROPS,
        //                //ImmutableList.of(
        //                //        ItemRegistration.amaranthseeditem.get(),
        //                //        ItemRegistration.artichokeseeditem.get(),
        //                //        ItemRegistration.brusselsproutseeditem.get(),
        //                //        ItemRegistration.chiaseeditem.get(),
        //                //        ItemRegistration.kaleseeditem.get(),
        //                //        ItemRegistration.leekseeditem.get(),
        //                //        ItemRegistration.lettuceseeditem.get(),
        //                //        ItemRegistration.mulberryseeditem.get(),
        //                //        ItemRegistration.nettlesseeditem.get(),
        //                //        ItemRegistration.onionseeditem.get(),
        //                //        ItemRegistration.peasseeditem.get(),
        //                //        ItemRegistration.raspberryseeditem.get(),
        //                //        ItemRegistration.ryeseeditem.get(),
        //                //        ItemRegistration.spiceleafseeditem.get(),
        //                //        ItemRegistration.taroseeditem.get()
        //                //        ),
        //                15
//
        //        )
        //)));

        // Define the Forest Biome Rules
       // pWriter.accept(new BiomeRulesDataConsumer(prefix("forest_rules"), new BiomeRulesData(
       //         BiomeTags.IS_FOREST,
       //         new TagBasedRandomCropRule(
       //                 FUCTags.VANILLA_AND_MODDED_CROPS,
       //                 //ImmutableList.of(
       //                 //        Items.CARROT,
       //                 //        ItemRegistration.barleyseeditem.get(),
       //                 //        ItemRegistration.bellpepperseeditem.get(),
       //                 //        ItemRegistration.broccoliseeditem.get(),
       //                 //        ItemRegistration.cauliflowerseeditem.get(),
       //                 //        ItemRegistration.chiaseeditem.get(),
       //                 //        ItemRegistration.cloudberryseeditem.get(),
       //                 //        ItemRegistration.eggplantseeditem.get(),
       //                 //        ItemRegistration.huckleberryseeditem.get(),
       //                 //        ItemRegistration.kaleseeditem.get(),
       //                 //        ItemRegistration.kenafseeditem.get(),
       //                 //        ItemRegistration.lentilseeditem.get(),
       //                 //        ItemRegistration.nopalesseeditem.get(),
       //                 //        ItemRegistration.onionseeditem.get(),
       //                 //        ItemRegistration.radishseeditem.get()
       //                 //),
       //                 15
       //         )
       // )));

        // Define the Beach Biome Rules
        //pWriter.accept(new BiomeRulesDataConsumer(prefix("beach_rules"), new BiomeRulesData(
        //        BiomeTags.IS_BEACH,
        //        new TagBasedRandomCropRule(
        //                FUCTags.VANILLA_AND_MODDED_CROPS,
        //                //ImmutableList.of(
        //                //        Items.CARROT,
        //                //        Items.POTATO,
        //                //        ItemRegistration.arrowrootseeditem.get(),
        //                //        ItemRegistration.calabashseeditem.get(),
        //                //        ItemRegistration.cottonseeditem.get(),
        //                //        ItemRegistration.eggplantseeditem.get(),
        //                //        ItemRegistration.garlicseeditem.get(),
        //                //        ItemRegistration.grapeseeditem.get(),
        //                //        ItemRegistration.guaranaseeditem.get(),
        //                //        ItemRegistration.lentilseeditem.get(),
        //                //        ItemRegistration.lotusseeditem.get(),
        //                //        ItemRegistration.mulberryseeditem.get(),
        //                //        ItemRegistration.nopalesseeditem.get(),
        //                //        ItemRegistration.pineappleseeditem.get(),
        //                //        ItemRegistration.ryeseeditem.get()
        //                //),
        //                15
        //        )
        //)));
    }
}
