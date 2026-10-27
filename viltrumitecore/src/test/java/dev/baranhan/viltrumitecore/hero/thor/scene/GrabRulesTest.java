package dev.baranhan.viltrumitecore.hero.thor.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorInput;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import org.junit.jupiter.api.Test;

/** Grab window chance (decisions 117-120), the hold / resist flow (121-126) and the action mapping (60-64). */
class GrabRulesTest {
   @Test
   void windowOpensAtMostOncePerComboAndOnlyForNormalCombos() {
      GrabWindowRule.Combo combo = new GrabWindowRule.Combo();
      assertFalse(GrabWindowRule.onHit(combo, ThorMove.HAMMER_COMBO, 0, 0.5F, 0.3F, 40, true), "roll above the chance");
      assertTrue(GrabWindowRule.onHit(combo, ThorMove.HAMMER_COMBO, 5, 0.1F, 0.3F, 40, true));
      assertFalse(GrabWindowRule.onHit(combo, ThorMove.FIST_COMBO, 10, 0.0F, 0.3F, 40, true), "one window per combo");
      assertTrue(combo.used());
      GrabWindowRule.onComboEnd(combo);
      assertTrue(GrabWindowRule.onHit(combo, ThorMove.FIST_COMBO, 12, 0.0F, 0.3F, 40, true), "a new combo may open it again");
   }

   @Test
   void idleGapStartsANewCombo() {
      GrabWindowRule.Combo combo = new GrabWindowRule.Combo();
      assertTrue(GrabWindowRule.onHit(combo, ThorMove.HAMMER_COMBO, 0, 0.0F, 0.3F, 40, true));
      assertFalse(GrabWindowRule.onHit(combo, ThorMove.HAMMER_COMBO, 30, 0.0F, 0.3F, 40, true));
      assertTrue(GrabWindowRule.onHit(combo, ThorMove.HAMMER_COMBO, 80, 0.0F, 0.3F, 40, true));
   }

   @Test
   void otherMovesAndInvalidTargetsNeverOpenIt() {
      GrabWindowRule.Combo combo = new GrabWindowRule.Combo();
      for (ThorMove move : new ThorMove[]{ThorMove.ELECTRIC_COMBO, ThorMove.HAMMER_STRIKE, ThorMove.HAMMER_THROW, ThorMove.WRATH, null}) {
         assertFalse(GrabWindowRule.onHit(combo, move, 0, 0.0F, 1.0F, 40, true), String.valueOf(move));
      }
      assertFalse(GrabWindowRule.onHit(combo, ThorMove.HAMMER_COMBO, 1, 0.0F, 1.0F, 40, false));
      assertFalse(combo.used());
   }

   @Test
   void onlyTheFixedKeyResistsAndPressesAreRateCapped() {
      GrabFlow flow = new GrabFlow(ResistKey.A);
      assertTrue(flow.resistible());
      assertFalse(flow.resist(ResistKey.W, 0.1F, 14), "wrong key");
      assertEquals(0.0F, flow.meter());
      int accepted = 0;
      for (int i = 0; i < 30; i++) {
         if (flow.resist(ResistKey.A, 0.01F, 14)) {
            accepted++;
         }
      }
      assertEquals(14, accepted, "at most maxPerSecond presses in one second");
      for (int i = 0; i < 20; i++) {
         flow.tick(0.0F, 8);
      }
      assertTrue(flow.resist(ResistKey.A, 0.01F, 14), "the cap resets after a second");
   }

   @Test
   void fullMeterEscapesAndDecayPullsItBack() {
      GrabFlow flow = new GrabFlow(ResistKey.SPACE);
      flow.resist(ResistKey.SPACE, 0.5F, 14);
      flow.tick(0.1F, 8);
      assertEquals(0.4F, flow.meter(), 1.0E-6);
      flow.resist(ResistKey.SPACE, 0.7F, 14);
      assertSame(GrabFlow.Outcome.ESCAPED, flow.tick(0.1F, 8));
      assertSame(GrabFlow.Stage.DONE, flow.stage());
   }

   @Test
   void mobsCannotResistAndTheHoldNeverRunsOut() {
      GrabFlow flow = new GrabFlow(null);
      assertFalse(flow.resistible());
      assertFalse(flow.resist(ResistKey.W, 1.0F, 14));
      for (int i = 0; i < 20 * 60 * 10; i++) {
         assertSame(GrabFlow.Outcome.NONE, flow.tick(0.0F, 8), "no hold time limit: only an escape or Thor's action ends a grab");
      }
      assertSame(GrabFlow.Stage.HOLD, flow.stage());
   }

   @Test
   void unresistedPlayerStaysHeldUntilThorActs() {
      GrabFlow flow = new GrabFlow(ResistKey.W);
      for (int i = 0; i < 20 * 60 * 10; i++) {
         assertSame(GrabFlow.Outcome.NONE, flow.tick(0.006F, 8));
      }
      assertSame(GrabFlow.Stage.HOLD, flow.stage());
      assertTrue(flow.commit(GrabAction.BARRAGE));
   }

   @Test
   void oneCommitPerGrabAndNoResistAfterIt() {
      GrabFlow flow = new GrabFlow(ResistKey.D);
      assertTrue(flow.commit(GrabAction.SLAM));
      assertFalse(flow.commit(GrabAction.THROW), "the choice is final");
      assertSame(GrabAction.SLAM, flow.action());
      assertFalse(flow.resistible());
      assertFalse(flow.resist(ResistKey.D, 1.0F, 14));
   }

   @Test
   void actionsReleaseThenRecoverThenEnd() {
      GrabFlow flow = new GrabFlow(null);
      flow.commit(GrabAction.THROW);
      int release = -1;
      for (int i = 1; i <= 100; i++) {
         if (flow.tick(0, 8) == GrabFlow.Outcome.RELEASE) {
            release = i;
            break;
         }
      }
      assertEquals(GrabAction.THROW.releaseTick(), release);
      assertSame(GrabFlow.Stage.RECOVER, flow.stage());
      GrabFlow.Outcome last = GrabFlow.Outcome.NONE;
      for (int i = 0; i < 8; i++) {
         last = flow.tick(0, 8);
      }
      assertSame(GrabFlow.Outcome.END, last);
   }

   @Test
   void barragePunchesEveryIntervalBeforeTheFinisher() {
      GrabFlow flow = new GrabFlow(null);
      flow.commit(GrabAction.BARRAGE);
      int punches = 0;
      GrabFlow.Outcome outcome;
      do {
         outcome = flow.tick(0, 8);
         if (outcome == GrabFlow.Outcome.PUNCH) {
            assertEquals(punches, flow.punch());
            punches++;
         }
      } while (outcome != GrabFlow.Outcome.RELEASE);
      assertEquals(ThorSceneConfig.barrageHits, punches);
   }

   @Test
   void mouseButtonsMapToTheThreeActions() {
      assertSame(GrabAction.SLAM, GrabAction.of(ThorInput.STRIKE));
      assertSame(GrabAction.THROW, GrabAction.of(ThorInput.STANCE));
      assertSame(GrabAction.BARRAGE, GrabAction.of(ThorInput.HAMMER));
      assertNull(GrabAction.of(ThorInput.POWER));
      assertNull(GrabAction.of(ThorInput.TELEPORT));
      for (GrabAction action : GrabAction.values()) {
         assertSame(action, GrabAction.byId(action.ordinal()));
      }
      assertNull(GrabAction.byId(7));
   }

   @Test
   void resistKeyPickCoversAllKeys() {
      for (int roll = -10; roll < 10; roll++) {
         ResistKey key = ResistKey.pick(roll);
         assertSame(key, ResistKey.byId(key.ordinal()));
      }
   }

   @Test
   void holdPoseKeepsTheBodyInFrontAtNeckHeight() {
      SceneFrame.Offset hold = GrabPose.hold(0.6F, 1.8F);
      assertTrue(hold.forward() > 0.6, "in front of Thor");
      assertTrue(hold.up() > 0.0 && hold.up() < 1.2);
      assertEquals(hold, GrabPose.at(GrabFlow.Stage.HOLD, null, 50, 0.6F, 1.8F));
   }
}
