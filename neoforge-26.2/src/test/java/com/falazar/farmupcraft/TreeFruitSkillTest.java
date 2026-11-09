package com.falazar.farmupcraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreeFruitSkillTest {
    @Test void targetsResolveRegisteredFruitAndNutsButExcludeOtherTreeProducts() {
        for (String fruit : new String[]{"apple", "lemon", "peach", "plum", "almond", "walnut", "nutmeg"}) {
            Identifier id = TreeManager.fruitItemId(Identifier.parse("pamhc2trees:pam" + fruit));
            assertNotSame(Items.AIR, BuiltInRegistries.ITEM.getValue(id));
            assertTrue(FruitHarvestSkill.isFruitOrNut(id.toString()), fruit);
        }
        for (String product : new String[]{"cinnamon", "maple", "paperbark", "spiderweb"}) {
            Identifier id = TreeManager.fruitItemId(Identifier.parse("pamhc2trees:pam" + product));
            assertFalse(FruitHarvestSkill.isFruitOrNut(id.toString()), product);
        }
    }

    @Test void onlyCrossingFullHundredsReducesTheSuccessBonus() {
        Identifier orchard = Identifier.parse("biomesoplenty:orchard");
        assertEquals(92, TreeManager.fruitSuccessRate(FruitHarvestSkill.effectiveCount(202, 0), 28, orchard));
        assertEquals(92, TreeManager.fruitSuccessRate(FruitHarvestSkill.effectiveCount(202, 2), 28, orchard));
        assertEquals(90, TreeManager.fruitSuccessRate(FruitHarvestSkill.effectiveCount(202, 3), 28, orchard));
        assertEquals(88, TreeManager.fruitSuccessRate(FruitHarvestSkill.effectiveCount(49, 49), 28, orchard));
        assertEquals(98, Math.min(98, TreeManager.fruitSuccessRate(500, 28, orchard)));
    }
}
