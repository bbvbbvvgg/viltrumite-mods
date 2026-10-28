package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MjolnirHandGuardTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void aPickedUpBlockLeavesTheHammerSlotForAFreeSlot() {
      NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
      items.set(0, new ItemStack(Items.DIRT, 3));
      assertTrue(MjolnirHandGuard.moveOutOfSlot(items, 0, 64));
      assertTrue(items.get(0).isEmpty());
      assertEquals(3, items.get(1).getCount());
   }

   @Test
   void itMergesIntoAnEqualStackFirst() {
      NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
      items.set(0, new ItemStack(Items.DIRT, 5));
      items.set(7, new ItemStack(Items.DIRT, 62));
      assertTrue(MjolnirHandGuard.moveOutOfSlot(items, 0, 64));
      assertEquals(64, items.get(7).getCount());
      assertEquals(3, items.get(1).getCount());
   }

   @Test
   void aFullInventoryKeepsTheItemInTheHand() {
      NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
      for (int i = 0; i < 36; i++) {
         items.set(i, new ItemStack(Items.STONE, 64));
      }
      items.set(4, new ItemStack(Items.DIRT, 2));
      assertFalse(MjolnirHandGuard.moveOutOfSlot(items, 4, 64));
      assertEquals(2, items.get(4).getCount());
   }
}
