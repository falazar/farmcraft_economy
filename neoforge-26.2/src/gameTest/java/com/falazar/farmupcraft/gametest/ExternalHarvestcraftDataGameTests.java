package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.CookwareManager;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class ExternalHarvestcraftDataGameTests {
    private ExternalHarvestcraftDataGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("external_pack_oil"),
                    ExternalHarvestcraftDataGameTests::oil);
            functions.register(FarmUpCraftNeoForge.prefix("external_pack_fish_tags"),
                    ExternalHarvestcraftDataGameTests::fishTags);
            functions.register(FarmUpCraftNeoForge.prefix("external_pack_optional_pipes"),
                    ExternalHarvestcraftDataGameTests::pipes);
        });
    }

    private static void oil(GameTestHelper helper) {
        var roller = stack("pamhc2foodcore:rolleritem");
        int uses = CookwareManager.getRemainingUses(roller);
        var ingredients = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        ingredients.set(0, roller);
        ingredients.set(1, stack("pamhc2crops:canolaitem"));
        ingredients.set(2, stack("pamhc2crops:canolaitem"));
        var menu = craft(helper, "pamhc2crops:cookingoil_x4_canola_x2", ingredients,
                "pamhc2foodcore:cookingoilitem", 8);
        var returned = menu.getSlot(1).getItem();
        helper.assertTrue(returned.is(roller.getItem()) && returned.getCount() == 1,
                "Oil crafting returns exactly one rolling pin");
        helper.assertValueEqual(CookwareManager.getRemainingUses(returned), uses - 1,
                "Oil crafting uses the real cookware remainder and consumes exactly one use");
        for (int slot = 2; slot <= 9; slot++) {
            helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "Oil ingredients fully consumed");
        }
        helper.succeed();
    }

    private static void fishTags(GameTestHelper helper) {
        for (String name : List.of("fishes", "rawfish", "rawmeats")) {
            var tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", name));
            helper.assertTrue(BuiltInRegistries.ITEM.get(tag).isPresent(),
                    "Converted HarvestCraft fish/meat tag binds without optional companions: " + name);
            var members = new ArrayList<>(List.of("aquaculture:fish_fillet_raw", "alexsmobs:raw_catfish"));
            if (name.equals("rawfish")) {
                members.add("aquaculture:fish_fillet_cooked");
            }
            for (String id : members) {
                var item = BuiltInRegistries.ITEM.get(Identifier.parse(id));
                if (item.isPresent()) {
                    helper.assertTrue(new ItemStack(item.orElseThrow().value()).is(tag),
                            "Installed optional item must belong to converted tag: " + id + " in " + name);
                }
            }
        }
        helper.assertTrue(new ItemStack(Items.COD).is(
                        TagKey.create(Registries.ITEM, Identifier.parse("c:rawfish"))),
                "Non-replacing overrides preserve HarvestCraft's vanilla fish entries");
        helper.succeed();
    }

    private static void pipes(GameTestHelper helper) {
        for (String name : List.of("fluid_pipe", "item_pipe")) {
            var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("pipez", name));
            if (!ModList.get().isLoaded("pipez")) {
                helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isEmpty(),
                        "Pipe override must be skipped when Pipez is absent: " + name);
                continue;
            }
            var side = name.equals("fluid_pipe") ? Items.BUCKET : Items.DROPPER;
            var ingredients = new ArrayList<ItemStack>();
            for (int slot = 0; slot < 9; slot++) {
                ingredients.add(new ItemStack(slot < 3 || slot > 5 ? Items.IRON_INGOT
                        : slot == 4 ? Items.REDSTONE : side));
            }
            var menu = craft(helper, "pipez:" + name, ingredients, "pipez:" + name, 1);
            for (int slot = 1; slot <= 9; slot++) {
                helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(),
                        "Pipe crafting consumes the complete original input pattern");
            }
        }
        helper.succeed();
    }

    private static CraftingMenu craft(GameTestHelper helper, String id, List<ItemStack> ingredients,
                                      String outputId, int count) {
        var level = helper.getLevel();
        var key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
        var input = CraftingInput.of(3, 3, ingredients);
        var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElseThrow();
        helper.assertValueEqual(recipe.id(), key, "Actual manager selects the external override: " + id);
        var table = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "external_pack"));
        player.setPos(table.getX() + 0.5, table.getY() + 1, table.getZ() + 0.5);
        var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, table));
        for (int slot = 0; slot < 9; slot++) {
            menu.getSlot(slot + 1).set(ingredients.get(slot).copy());
        }
        var result = menu.getSlot(0);
        helper.assertTrue(result.getItem().is(stack(outputId).getItem()) && result.getItem().getCount() == count,
                "Actual crafting-menu output matches external datapack: " + id);
        var taken = result.remove(count);
        result.onTake(player, taken);
        helper.assertTrue(taken.is(stack(outputId).getItem()) && taken.getCount() == count,
                "Taking the crafted output preserves the exact item/count");
        helper.assertTrue(result.getItem().isEmpty(), "Cannot obtain a duplicate output from consumed inputs");
        return menu;
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow().value());
    }
}
