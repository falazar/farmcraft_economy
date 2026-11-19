package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ItemUsesCategory implements IRecipeCategory<ItemUseRecipe> {
    static final RecipeType<ItemUseRecipe> TYPE = new RecipeType<>(
            new ResourceLocation(FarmUpCraft.MODID, "item_uses"), ItemUseRecipe.class);
    private final IDrawable background;
    private final IDrawable icon;

    ItemUsesCategory(IGuiHelper helper) {
        background = helper.createBlankDrawable(236, 40);
        icon = helper.createDrawableItemStack(new ItemStack(Items.CARROT));
    }

    @Override public RecipeType<ItemUseRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.literal("Item Uses"); }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ItemUseRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < Math.min(9, recipe.inputSlots().size()); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, i * 18, 18).addItemStacks(recipe.inputSlots().get(i));
        }
        for (int i = 0; i < Math.min(3, recipe.outputSlots().size()); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 182 + i * 18, 18).addItemStacks(recipe.outputSlots().get(i));
        }
    }

    @Override
    public void draw(ItemUseRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        String label = (recipe.exactMatch() ? "Exact - " : "Group - ") + recipe.sourceCategory();
        int omitted = Math.max(0, recipe.inputSlots().size() - 9) + Math.max(0, recipe.outputSlots().size() - 3);
        if (omitted > 0) label += " (+" + omitted + " more)";
        graphics.drawString(font, font.plainSubstrByWidth(label, getWidth()), 0, 2, 0xFF404040, false);
        graphics.drawString(font, "->", 164, 24, 0xFF404040, false);
    }
}
