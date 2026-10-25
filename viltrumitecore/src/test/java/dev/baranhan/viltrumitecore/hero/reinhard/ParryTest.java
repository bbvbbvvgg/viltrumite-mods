package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Parry;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

/** Blessing of the Holy Sword (spec §4.1): direct melee from the front only. */
class ParryTest {
   /** Looking along +z (yaw 0). */
   private static final Vec2 LOOK = Parry.lookOf(0.0F);

   private static Vec2 at(double degreesFromLook) {
      double rad = Math.toRadians(degreesFromLook);
      return new Vec2((float) Math.sin(rad) * 3.0F, (float) Math.cos(rad) * 3.0F);
   }

   @Test
   void frontMeleeParried() {
      assertTrue(Parry.isMelee(true, false, false, false));
      assertTrue(Parry.inFront(LOOK, at(0.0)));
      assertTrue(Parry.inFront(LOOK, at(55.0)));
      assertTrue(Parry.inFront(LOOK, at(-55.0)));
   }

   @Test
   void backHitNotParried() {
      assertFalse(Parry.inFront(LOOK, at(180.0)));
      assertFalse(Parry.inFront(LOOK, at(120.0)));
      assertFalse(Parry.inFront(LOOK, at(-150.0)));
   }

   @Test
   void sideAt70DegreesNotParried() {
      assertFalse(Parry.inFront(LOOK, at(70.0)));
      assertFalse(Parry.inFront(LOOK, at(-70.0)));
      assertFalse(Parry.inFront(LOOK, at(90.0)));
   }

   @Test
   void projectileNotParried() {
      assertFalse(Parry.isMelee(false, true, false, false));
      // A projectile whose owner is also its direct entity (a shot that names its shooter) still is no melee.
      assertFalse(Parry.isMelee(true, true, false, false));
      assertFalse(Parry.isMelee(true, false, true, false));
      assertFalse(Parry.isMelee(false, false, false, false));
   }

   @Test
   void divineNotParried() {
      assertFalse(Parry.isMelee(true, false, false, true));
   }

   @Test
   void coneIsSixtyDegrees() {
      assertEquals(0.5F, ReinhardRules.PARRY_CONE_COS);
      assertEquals(8, ReinhardRules.PARRY_POSE_TICKS);
   }

   @Test
   void lookFollowsYaw() {
      // Yaw 90 looks along -x in Minecraft.
      Vec2 west = Parry.lookOf(90.0F);
      assertTrue(Parry.inFront(west, new Vec2(-2.0F, 0.3F)));
      assertFalse(Parry.inFront(west, new Vec2(2.0F, 0.0F)));
   }

   @Test
   void attackerInsideColumnNotParried() {
      assertFalse(Parry.inFront(LOOK, new Vec2(0.0F, 0.0F)));
   }
}
