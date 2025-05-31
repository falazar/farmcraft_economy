package com.falazar.farmupcraft.entity;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

public interface StructureTemplateAccess {

    List<StructureTemplate.Palette> getStructurePalette();
    List<StructureTemplate.StructureEntityInfo> getStructureEntityInfo();
}
