package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Adaptation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Divine Protection of Protection Acquisition (spec §4.8). */
class AdaptationTest {
   private static final ResourceLocation A = new ResourceLocation("viltrumitecore", "divine");
   private static final ResourceLocation B = new ResourceLocation("othermod", "smite");

   @Test
   void thirdHitGrantsImmunity() {
      Adaptation a = Adaptation.NONE.onDivineHit(A).onDivineHit(A);
      assertFalse(a.immuneTo(A));
      a = a.onDivineHit(A);
      assertEquals(ReinhardRules.ADAPT_HITS, 3);
      assertTrue(a.immuneTo(A));
      assertFalse(a.immuneTo(B));
   }

   @Test
   void thirdHitStillLands() {
      Adaptation a = Adaptation.NONE;
      for (int hit = 1; hit <= 3; hit++) {
         ReinhardHero.DivineStep step = ReinhardHero.divineStep(a, A);
         assertFalse(step.result().absorbed(), "hit " + hit + " lands");
         a = step.next();
      }
      assertTrue(ReinhardHero.divineStep(a, A).result().absorbed(), "the 4th does nothing");
   }

   @Test
   void otherTypeResetsStreak() {
      Adaptation a = Adaptation.NONE.onDivineHit(A).onDivineHit(A).onDivineHit(B);
      assertEquals(B, a.streakType());
      assertEquals(1, a.streak());
      a = a.onDivineHit(A).onDivineHit(A);
      assertFalse(a.immuneTo(A), "the streak restarted");
   }

   @Test
   void ordinaryDamageDoesNotTouch() {
      Adaptation a = Adaptation.NONE.onDivineHit(A);
      assertSame(a, ReinhardHero.ordinaryStep(a));
   }

   @Test
   void clearedOnDeath() {
      ReinhardState state = new ReinhardState();
      state.mode = ReinhardMode.KNIGHT;
      state.adaptation = Adaptation.NONE.onDivineHit(A).onDivineHit(A).onDivineHit(A);
      ReinhardState clone = state.respawnCopy();
      assertSame(ReinhardMode.KNIGHT, clone.mode, "mode carries over");
      assertFalse(clone.adaptation.immuneTo(A), "immunity is lost with the body");
   }

   @Test
   void savedWithTheHero() {
      ReinhardState state = new ReinhardState();
      state.adaptation = Adaptation.NONE.onDivineHit(B).onDivineHit(B).onDivineHit(B).onDivineHit(A);
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      ReinhardState loaded = new ReinhardState();
      loaded.load(nbt);
      assertTrue(loaded.adaptation.immuneTo(B));
      assertEquals(A, loaded.adaptation.streakType());
      assertEquals(1, loaded.adaptation.streak());
   }
}
