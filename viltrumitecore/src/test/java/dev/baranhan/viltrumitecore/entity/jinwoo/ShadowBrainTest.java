package dev.baranhan.viltrumitecore.entity.jinwoo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.jinwoo.JinwooRules;
import org.junit.jupiter.api.Test;

/** Playtest: shadows keep one target, follow Jinwoo's fight and give up on unreachable enemies. */
class ShadowBrainTest {
   private static final int ORDER = 0;
   private static final int OWNER_FIGHT = 1;
   private static final int LOCAL = 2;

   @Test
   void targetIsKeptInsideTheLeashOnly() {
      double inside = JinwooRules.LEASH * JinwooRules.LEASH;
      double far = (JinwooRules.LEASH + 9.0) * (JinwooRules.LEASH + 9.0);
      assertTrue(ShadowBrain.keepsTarget(true, inside, true));
      assertFalse(ShadowBrain.keepsTarget(true, far, true));
      assertFalse(ShadowBrain.keepsTarget(false, inside, true), "dead / removed / other level");
      assertFalse(ShadowBrain.keepsTarget(true, inside, false), "ignored as unreachable");
   }

   @Test
   void localPicksNeverFlapBetweenEnemies() {
      assertFalse(ShadowBrain.switches(true, false, LOCAL, LOCAL, true));
      assertTrue(ShadowBrain.switches(false, false, LOCAL, LOCAL, true), "no target yet");
      assertTrue(ShadowBrain.switches(true, true, LOCAL, LOCAL, true), "same target");
   }

   @Test
   void ownerFightAndOrdersOutrankLocalTargets() {
      assertTrue(ShadowBrain.switches(true, false, OWNER_FIGHT, LOCAL, false));
      assertTrue(ShadowBrain.switches(true, false, ORDER, OWNER_FIGHT, false));
      assertFalse(ShadowBrain.switches(true, false, LOCAL, OWNER_FIGHT, true), "a stray mob does not pull them off Jinwoo's fight");
      assertTrue(ShadowBrain.switches(true, false, OWNER_FIGHT, OWNER_FIGHT, false), "Jinwoo switched targets");
      assertFalse(ShadowBrain.switches(true, false, OWNER_FIGHT, ORDER, false), "an explicit order wins");
   }

   @Test
   void stuckLimitsAreSane() {
      assertTrue(ShadowBrain.STUCK_LIMIT >= 60 && ShadowBrain.IGNORE_TICKS > 0);
   }

   @Test
   void ownerFightIsNotDroppedWhenUnreachable() {
      assertTrue(ShadowBrain.dropsWhenStuck(true), "a local pick that cannot be reached is given up");
      assertFalse(ShadowBrain.dropsWhenStuck(false), "the order / Jinwoo's fight is chased, not abandoned");
   }
}
