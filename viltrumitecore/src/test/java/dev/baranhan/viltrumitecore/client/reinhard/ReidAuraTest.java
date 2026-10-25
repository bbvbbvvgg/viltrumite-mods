package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.reid.ReidAura;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardFlags;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import org.junit.jupiter.api.Test;

class ReidAuraTest {
   @Test
   void auraOnlyWhenDrawn() {
      assertFalse(ReidAura.active(0));
      assertFalse(ReidAura.active(1 << ReinhardFlags.REID_DRAWING));
      assertFalse(ReidAura.active(1 << ReinhardFlags.REID_SHEATHING));
      assertTrue(ReidAura.active(1 << ReinhardFlags.REID_DRAWN));
   }

   @Test
   void tintWeightFallsWithDistance() {
      assertEquals(1.0F, ReidAura.tintWeight(0.0), 1.0E-6F);
      float near = ReidAura.tintWeight(10.0);
      float far = ReidAura.tintWeight(50.0);
      assertTrue(near > far && far > 0.0F);
      assertEquals(0.0F, ReidAura.tintWeight(ReidAura.TINT_RANGE), 1.0E-6F);
      assertEquals(0.0F, ReidAura.tintWeight(200.0), 1.0E-6F);
   }

   @Test
   void barMatchesResource() {
      assertFalse(ReinhardHud.showsReidBar(0));
      assertTrue(ReinhardHud.showsReidBar(1));
      assertEquals(40, ReinhardHud.reidFillWidth(ReinhardRules.REID_IDLE_TICKS, 40));
      assertEquals(20, ReinhardHud.reidFillWidth(ReinhardRules.REID_IDLE_TICKS / 2, 40));
      assertEquals(0, ReinhardHud.reidFillWidth(0, 40));
   }

   @Test
   void drawnIdleClipLoops() {
      assertEquals(ReinhardClips.IDLE_HOLY_DRAWN, ReinhardPoses.reidClip(ReinhardPoses.ReidPhase.DRAWN, 30.0).animation());
   }

   @Test
   void iconShowsDrawnBlade() {
      String id = dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.REID.id();
      assertTrue(dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbilities.icon(id, 0).getPath().endsWith("/reid.png"));
      assertTrue(dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbilities.icon(id, 1 << ReinhardFlags.REID_DRAWN).getPath().endsWith("/reid_drawn.png"));
   }
}
