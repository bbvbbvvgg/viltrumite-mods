package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The Reid strike contract (Reinhard spec §1.3, §3.3): every verdict and the unfloored scope. */
class AnnihilationTest {
   private static final DivineTargets.Verdict DIVINE = DivineTargets.Verdict.DIVINE;
   private static final DivineTargets.Verdict ABSOLUTE = DivineTargets.Verdict.ABSOLUTE;
   private static final DivineTargets.Verdict MORTAL = DivineTargets.Verdict.MORTAL;

   @Test
   void divineIsImmuneAlways() {
      for (boolean creative : new boolean[]{false, true}) {
         for (boolean alive : new boolean[]{false, true}) {
            assertSame(Annihilation.Outcome.IMMUNE_DIVINE, Annihilation.decide(DIVINE, creative, alive));
         }
      }
   }

   @Test
   void absoluteBlocksAlways() {
      for (boolean creative : new boolean[]{false, true}) {
         for (boolean alive : new boolean[]{false, true}) {
            assertSame(Annihilation.Outcome.BLOCKED_ABSOLUTE, Annihilation.decide(ABSOLUTE, creative, alive));
         }
      }
   }

   @Test
   void creativeOrDeadSkipped() {
      assertSame(Annihilation.Outcome.SKIPPED, Annihilation.decide(MORTAL, true, true));
      assertSame(Annihilation.Outcome.SKIPPED, Annihilation.decide(MORTAL, false, false));
      assertSame(Annihilation.Outcome.SKIPPED, Annihilation.decide(MORTAL, true, false));
   }

   @Test
   void mortalKilled() {
      assertSame(Annihilation.Outcome.KILLED, Annihilation.decide(MORTAL, false, true));
   }

   @Test
   void scopeNestsAndCloses() {
      assertFalse(Annihilation.active());
      Annihilation.run(() -> {
         assertTrue(Annihilation.active());
         Annihilation.run(() -> assertTrue(Annihilation.active()));
         assertTrue(Annihilation.active());
      });
      assertFalse(Annihilation.active());
   }

   /** A flooring hero (Reinhard's 1 HP floor) keeps 1 HP outside the scope and takes the full amount inside it. */
   @Test
   void scopeSkipsUnblockableClamp() {
      HeroDamageLayers.Target floor = new HeroDamageLayers.Target() {
         @Override
         public DamageAbsorb absorbIncoming(float raw) {
            return DamageAbsorb.ABSORBED;
         }

         @Override
         public float clampFinalDamage(float afterArmor) {
            return 0.0F;
         }

         @Override
         public float clampUnblockable(float amount) {
            return Math.min(amount, 19.0F);
         }
      };
      assertEquals(19.0F, HeroDamageLayers.finalDamageInScope(floor, true, 1000.0F));
      Annihilation.run(() -> assertEquals(1000.0F, HeroDamageLayers.finalDamageInScope(floor, true, 1000.0F)));
      AdminKill.run(() -> assertEquals(1000.0F, HeroDamageLayers.finalDamageInScope(floor, true, 1000.0F)));
      // A layered (non-bypassing) source still meets the hero's clamp inside the scope.
      Annihilation.run(() -> assertEquals(0.0F, HeroDamageLayers.finalDamageInScope(floor, false, 1000.0F)));
   }

   @Test
   void damageTypeWiredInEveryBypassTag() throws java.io.IOException {
      Path data = Path.of("src/main/resources/data");
      assertTrue(Files.readString(data.resolve("viltrumitecore/damage_type/annihilation.json")).contains("\"viltrumitecore.annihilation\""));
      for (String tag : new String[]{"bypasses_invulnerability", "bypasses_armor", "bypasses_shield", "bypasses_enchantments", "bypasses_effects",
         "bypasses_resistance"}) {
         assertTrue(Files.readString(data.resolve("minecraft/tags/damage_type/" + tag + ".json")).contains("\"viltrumitecore:annihilation\""), tag);
      }
   }
}
