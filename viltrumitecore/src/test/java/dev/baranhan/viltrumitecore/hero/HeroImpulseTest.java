package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Overview impulse rule: only items, XP orbs and falling blocks move among the non-living. */
class HeroImpulseTest {
   @Test
   void itemsYesProjectilesNo() {
      assertTrue(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.item.ItemEntity.class));
      assertTrue(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.ExperienceOrb.class));
      assertTrue(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.item.FallingBlockEntity.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.projectile.Arrow.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.projectile.SmallFireball.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.vehicle.Boat.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.vehicle.Minecart.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.decoration.ItemFrame.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.decoration.ArmorStand.class));
      assertFalse(HeroImpulse.mayMoveNonLiving(net.minecraft.world.entity.monster.Zombie.class), "the living use allowsImpulse");
   }

   @Test
   void armorStandsAreNeverLifted() {
      assertFalse(HeroImpulse.mayLiftLiving(net.minecraft.world.entity.decoration.ArmorStand.class));
      assertTrue(HeroImpulse.mayLiftLiving(net.minecraft.world.entity.animal.Cow.class));
   }
}
