package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.entity.StructureTemplateAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(StructureTemplate.class)
public class StructureTemplateMixin implements StructureTemplateAccess {
    @Shadow @Final private List<StructureTemplate.Palette> palettes;

    @Shadow @Final private List<StructureTemplate.StructureEntityInfo> entityInfoList;

    @Override
    public List<StructureTemplate.Palette> getStructurePalette() {
        return palettes;
    }

    @Override
    public List<StructureTemplate.StructureEntityInfo> getStructureEntityInfo() {
        return entityInfoList;
    }
}
