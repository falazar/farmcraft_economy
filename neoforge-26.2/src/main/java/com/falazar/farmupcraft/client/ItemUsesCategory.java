package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ItemUsesCategory implements IRecipeCategory<ItemUseRecipe> {
    private static final int MAX_INPUTS = 9;
    private static final int MAX_OUTPUTS = 3;
    private static final int INPUTS_END = MAX_INPUTS * 18;
    private static final int OUTPUTS_START = INPUTS_END + 20;
    static final IRecipeType<ItemUseRecipe> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, "item_uses"), ItemUseRecipe.class);

    private final IDrawable icon;

    ItemUsesCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(Items.CARROT));
    }

    @Override
    public IRecipeType<ItemUseRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.literal("Item Uses");
    }

    @Override
    public int getWidth() {
        return OUTPUTS_START + MAX_OUTPUTS * 18;
    }

    @Override
    public int getHeight() {
        return 40;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ItemUseRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < Math.min(MAX_INPUTS, recipe.inputSlots().size()); i++) {
            builder.addInputSlot(i * 18, 18).addItemStacks(recipe.inputSlots().get(i));
        }
        for (int i = 0; i < Math.min(MAX_OUTPUTS, recipe.outputSlots().size()); i++) {
            builder.addOutputSlot(OUTPUTS_START + i * 18, 18).addItemStacks(recipe.outputSlots().get(i));
        }
    }

    @Override
    public void draw(ItemUseRecipe recipe, IRecipeSlotsView recipeSlots, GuiGraphicsExtractor graphics,
            double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        String label = (recipe.exactMatch() ? "Exact - " : "Group - ") + recipe.sourceCategory();
        int omittedInputs = Math.max(0, recipe.inputSlots().size() - MAX_INPUTS);
        int omittedOutputs = Math.max(0, recipe.outputSlots().size() - MAX_OUTPUTS);
        if (omittedInputs > 0 || omittedOutputs > 0) {
            label += " (+" + (omittedInputs + omittedOutputs) + " more)";
        }
        graphics.text(font, font.plainSubstrByWidth(label, getWidth()), 0, 2, 0xFF404040);
        graphics.text(font, "->", INPUTS_END + 2, 24, 0xFF404040);
    }
}
