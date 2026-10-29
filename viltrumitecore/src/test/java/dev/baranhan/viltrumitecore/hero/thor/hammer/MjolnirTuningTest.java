package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** PLACEHOLDER numbers stay inside the approved canon where the canon gives a figure. */
class MjolnirTuningTest {
   @Test
   void approvedFiguresAreKept() {
      assertEquals(128.0, MjolnirTuning.THROW_RANGE, "№33: autonomy 128 blocks");
      assertEquals(60 * 20, MjolnirTuning.FIRST_FALL_DELAY, "№190: ~60 s after the first join");
      assertTrue(MjolnirTuning.CRATER_RADIUS >= 4 && MjolnirTuning.CRATER_RADIUS <= 6, "№174: crater radius 4–6");
      assertTrue(MjolnirTuning.LIFT_TICKS >= 30 && MjolnirTuning.LIFT_TICKS <= 40, "№195: lift attempt 1.5–2 s");
      assertTrue(MjolnirTuning.TRANSFORM_TICKS >= 20 && MjolnirTuning.TRANSFORM_TICKS <= 40, "№215: transformation ~1–2 s");
      assertTrue(MjolnirTuning.TRANSFORM_RECALL_AT < MjolnirTuning.TRANSFORM_TICKS, "№186: the arm goes out inside the scene");
      assertTrue(MjolnirTuning.LIFT_COOLDOWN > 0, "№196: a short pause between attempts");
   }

   @Test
   void theDeathStrikeOutdoesTheFirstFall() {
      // №169 / №209: more destructive than the first world fall.
      assertTrue(MjolnirTuning.DEATH_IMPACT_RADIUS > MjolnirTuning.CRATER_RADIUS);
      assertTrue(MjolnirTuning.DEATH_WAVE_RADIUS > MjolnirTuning.FALL_WAVE_RADIUS);
      assertTrue(MjolnirTuning.DEATH_IMPACT_DAMAGE > MjolnirTuning.FALL_DIRECT_DAMAGE);
      assertTrue(MjolnirImpact.bowlDepth(MjolnirTuning.DEATH_IMPACT_RADIUS, 0, 0) > MjolnirImpact.bowlDepth(MjolnirTuning.CRATER_RADIUS, 0, 0));
   }
}
