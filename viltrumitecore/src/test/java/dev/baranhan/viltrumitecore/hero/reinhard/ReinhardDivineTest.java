package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroDamageLayers;
import org.junit.jupiter.api.Test;

/** Reinhard is divine; divine damage skips his resist and HP floor (spec §3.2, §3.3). */
class ReinhardDivineTest {
   @Test
   void reinhardIsDivine() {
      assertTrue(ReinhardHero.INSTANCE.isDivine(null));
      assertFalse(ReinhardHero.INSTANCE.isAbsolutelyInvulnerable(null));
   }

   @Test
   void divineSkipsResistAndFloor() {
      HeroDamageLayers.Target target = new HeroDamageLayers.Target() {
         @Override
         public DamageAbsorb absorbIncoming(float raw) {
            return ReinhardHero.absorb(true, raw);
         }

         @Override
         public float clampFinalDamage(float afterArmor) {
            return ReinhardHero.clamp(true, 20.0F, afterArmor);
         }
      };
      assertEquals(100.0F, HeroDamageLayers.clean(target, HeroDamageLayers.NO_ATTACKER, false, 100.0F), 1.0E-5F);
      assertEquals(5.0F, HeroDamageLayers.clean(target, HeroDamageLayers.NO_ATTACKER, false, 5.0F), 1.0E-5F);
   }
}
