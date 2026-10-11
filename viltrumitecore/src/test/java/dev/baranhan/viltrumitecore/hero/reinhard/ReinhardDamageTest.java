package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroDamageLayers;
import org.junit.jupiter.api.Test;

/** Reinhard chassis through the shared damage pipeline (spec §3.1, §3.2). */
class ReinhardDamageTest {
   private static final float EPS = 1.0E-5F;

   /** Fake target wired to ReinhardHero's two damage hooks. */
   private static HeroDamageLayers.Target reinhard(float health, boolean divine) {
      return new HeroDamageLayers.Target() {
         @Override
         public DamageAbsorb absorbIncoming(float raw) {
            return ReinhardHero.absorb(divine, raw);
         }

         @Override
         public float clampFinalDamage(float afterArmor) {
            return ReinhardHero.clamp(divine, health, afterArmor);
         }
      };
   }

   @Test
   void swordHitLosesOneThousandth() {
      DamageAbsorb result = HeroDamageLayers.attack(reinhard(20.0F, false), false, false, 10.0F);
      assertFalse(result.absorbed());
      assertEquals(0.01F, HeroDamageLayers.passOn(result, 10.0F), EPS);
   }

   @Test
   void percentDamageAlsoResisted() {
      float half = 0.5F * ReinhardRules.MAX_HEALTH;
      DamageAbsorb result = HeroDamageLayers.attack(reinhard(20.0F, false), false, false, half);
      assertEquals(0.01F, HeroDamageLayers.passOn(result, half), EPS);
   }

   @Test
   void cleanPathResistedAndFloored() {
      assertEquals(0.5F, HeroDamageLayers.clean(reinhard(1.5F, false), HeroDamageLayers.NO_ATTACKER, false, 1.0E6F), EPS);
   }

   @Test
   void floorNeverBelowOne() {
      HeroDamageLayers.Target target = reinhard(20.0F, false);
      assertEquals(19.0F, HeroDamageLayers.finalDamage(target, false, 500.0F), EPS);
      assertEquals(0.0F, HeroDamageLayers.finalDamage(reinhard(1.0F, false), false, 3.0F), EPS);
      assertEquals(2.0F, HeroDamageLayers.finalDamage(target, false, 2.0F), EPS);
   }

   @Test
   void divinePassesUnchanged() {
      HeroDamageLayers.Target target = reinhard(20.0F, true);
      DamageAbsorb result = HeroDamageLayers.attack(target, false, false, 30.0F);
      assertEquals(30.0F, HeroDamageLayers.passOn(result, 30.0F), EPS);
      assertEquals(30.0F, HeroDamageLayers.finalDamage(target, false, 30.0F), EPS);
      assertTrue(HeroDamageLayers.clean(target, HeroDamageLayers.NO_ATTACKER, false, 25.0F) >= 25.0F - EPS);
   }

   /** Review focus 3: a huge unblockable hit (generic_kill from another mod) leaves exactly 1 HP, never NaN. */
   @Test
   void floorHandlesMaxValue() {
      HeroDamageLayers.Target target = new HeroDamageLayers.Target() {
         @Override
         public DamageAbsorb absorbIncoming(float raw) {
            return ReinhardHero.absorb(false, raw);
         }

         @Override
         public float clampFinalDamage(float afterArmor) {
            return ReinhardHero.clamp(false, 20.0F, afterArmor);
         }

         @Override
         public float clampUnblockable(float amount) {
            return ReinhardRules.floor(20.0F, amount);
         }
      };
      assertEquals(19.0F, HeroDamageLayers.finalDamage(target, true, false, Float.MAX_VALUE), EPS);
      assertEquals(19.0F, HeroDamageLayers.clean(target, HeroDamageLayers.NO_ATTACKER, true, false, Float.MAX_VALUE), EPS);
      assertEquals(Float.MAX_VALUE, HeroDamageLayers.finalDamage(target, true, true, Float.MAX_VALUE), EPS);
      assertEquals(0.0F, HeroDamageLayers.finalDamage(target, false, false, Float.NaN), EPS);
   }
}
