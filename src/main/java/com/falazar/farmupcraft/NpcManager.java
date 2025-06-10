package com.falazar.farmupcraft;

import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NpcManager {
    public static final CustomLogger LOGGER = new CustomLogger(NpcManager.class.getSimpleName());

//    @SubscribeEvent
//    public static void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {
//
//        // Step 1: If in creative mode, skip all rules and allow planting all.
//        Player player = (Player) event.getEntity();
//        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;
//        if (player.isCreative()) {
////            LOGGER.info("DEBUG: Player is in creative mode, skipping all rules.");
//            return;
//        }
//
//        // STEP 2: Test if target block is farmland, if not leave.
//        Level level = event.getLevel();
//        BlockPos clickedPos = event.getPos();
//        BlockState clickedState = level.getBlockState(clickedPos);
//
//        // Return if clicked block is not farmland
//        boolean isFarmBelow = false;
//
//        // Notice allowing some planting in non farm plot areas, if already plowed.
//
//
//        // Return if the placement would be on top of farmland (if face is known)
//        if (event.getFace() != null) {
//            BlockPos placementPos = clickedPos.relative(event.getFace());
//            BlockPos blockBelow = placementPos.below();
//            BlockState stateBelow = level.getBlockState(blockBelow);
//
//            if (stateBelow.is(FUCTags.FARMLAND)) {
//                isFarmBelow = true;
//            }
//        }
//        if (!clickedState.is(FUCTags.FARMLAND) && !isFarmBelow) {
//            return;
//        }
//
//
//        // STEP 3: If sugarcane or sweetberries, make sure they are on a farm plot only.
//        ItemStack stack = event.getItemStack();
//        // TODO TEST sugarcane and sweetberries.
//        if (stack.is(Items.SUGAR_CANE) || stack.is(Items.SWEET_BERRIES)) {
//            LOGGER.info("DEBUG: sugarcane or sweetberries, farm check.");
//            if (!getPlotType(clickedPos, level).equals("farm")) {
//                event.setCanceled(true);
//                return;
//            }
//        }
//
//        // STEP 4: Test if holding a vanilla or harvestcraft item, if not leave.
//        if (!stack.is(FUCTags.MODDED_CROPS) && !stack.is(FUCTags.VANILLA_CROPS) && !stack.is(FUCTags.MODDED_SEEDS)) {
//            return;
//        }
//
//        // Setup all of our biomes and crops rules allowed, saves to cache.
//        // TODO move me, call one time only at start.
//        //not needed anymore, is defined in CropItemDataJsonManager
//        //setupBiomeCrops(event);  // TEMP TESTER AREA.
//
//        // STEP 5: Get current biome the block is in.
//        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());
//
//        // The biome has rules defined for what can happen in it or not!
//        BiomeRulesManager manager = BiomeRulesManager.get(event.getLevel());
//        if (manager == null || !manager.hasRules()) return;
//
//        BiomeRulesInstance instance = manager.getBiomeRules(biome);
//        if (instance == null) return;
//
//        // Check if the crop is allowed in the biome

    /// /        if (!isCropAllowed(manager, instance, stack, biome, event)) {
    /// /            // Cancel event and return now.
    /// /            event.setCanceled(true);
    /// /        }
//    }


    // On right click an npc, show a description and say hello.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickNpc(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return; // server side only.
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        // Check that player is doing this.
        if (event.getEntity() == null || !(event.getEntity() instanceof Player)) {
//            LOGGER.info("DEBUG: not a player, leaving.");
            return;
        }
        Entity target = event.getTarget();
        // Check if the target is an NPC (Minecraft villager for now.)
        if (!(target instanceof Villager)) {
//            LOGGER.info("DEBUG: not a Villager, leaving.");
            return;
        }
        LOGGER.info("DEBUG: right click Villager target: " + target);

        Player player = event.getEntity();

        // Say hello and your name.
        MutableComponent message = Component.literal("Hello, I am ")
                .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("!"))
                .withStyle(ChatFormatting.GREEN);

        // Give a description if available.
        String description = getNpcDescription(target);
        if (description != null && !description.isEmpty()) {
            message.append(Component.literal("\n" + description).withStyle(ChatFormatting.GRAY));
        }
        player.displayClientMessage(message, false);
    }

    public static String getNpcDescription(Entity entity) {
        // For now, we will just return a static description.}
        String desc;
        if (entity instanceof Villager villager) {
            desc = "I am a villager of profession: " + villager.getVillagerData().getProfession().toString();
        } else {
            desc = "I am not a villager.";
        }

        // TODO create an NPC data object.

        return desc;
    }

}