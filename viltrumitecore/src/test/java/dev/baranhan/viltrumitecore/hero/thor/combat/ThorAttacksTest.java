package dev.baranhan.viltrumitecore.hero.thor.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorRules;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The attack table (rework spec §3 one row per attack, §4 heavy timings). */
class ThorAttacksTest {
   private static final Pattern CLIP_ID = Pattern.compile("[a-z][a-z0-9_]*");

   @Test
   void everyStrikeHasExactlyOneRowOfItsMove() {
      for (ThorStrike strike : ThorStrike.values()) {
         ThorAttacks.Attack row = ThorAttacks.of(strike);
         assertNotNull(row, strike + " has a row");
         assertSame(strike, row.strike());
         assertSame(strike.move(), row.move());
         assertEquals(strike.pose(), row.pose());
         assertSame(row, ThorAttacks.of(strike.move(), strike.pose()));
      }
   }

   @Test
   void reworkTimings() {
      // §4: DRAW / HOLSTER 30–40 ticks.
      for (ThorMove move : new ThorMove[]{ThorMove.DRAW, ThorMove.HOLSTER}) {
         assertTrue(move.length() >= 30 && move.length() <= 40, move + " " + move.length());
         assertEquals(move.length(), ThorAttacks.envelope(move).length());
      }

      // §4: Mjölnir strikes 24–30 ticks, fists 18–22, HAMMER_STRIKE ~30.
      for (ThorStrike strike : ThorStrike.values()) {
         int length = ThorAttacks.of(strike).length();
         switch (strike.move()) {
            case HAMMER_COMBO -> assertTrue(length >= 24 && length <= 30, strike + " " + length);
            case FIST_COMBO -> assertTrue(length >= 18 && length <= 22, strike + " " + length);
            case HAMMER_STRIKE -> assertTrue(length >= 28 && length <= 32, strike + " " + length);
            default -> throw new AssertionError(strike.move());
         }
      }

      // §4: WRATH 100–140 ticks, contact not before tick 60 (§3: 5–7 s, the strike not before ~3 s).
      ThorAttacks.Phases wrath = ThorAttacks.envelope(ThorMove.WRATH).phases();
      assertTrue(wrath.length() >= 100 && wrath.length() <= 140, "wrath " + wrath.length());
      assertTrue(wrath.contactTick() >= 60, "wrath contact " + wrath.contactTick());
      assertTrue(wrath.recovery() >= 30, "a long tail after the strike");
   }

   @Test
   void heavyShapeLongWindupShortContactLongRecovery() {
      for (ThorStrike strike : ThorStrike.values()) {
         ThorAttacks.Phases p = ThorAttacks.of(strike).phases();
         assertTrue(p.windup() >= 7, strike + ": a visible, heavy wind-up");
         assertTrue(p.contact() <= 3 && p.contact() >= 1, strike + ": a short contact");
         assertTrue(p.recovery() >= p.windup(), strike + ": the recovery is at least as long as the wind-up");
         assertTrue(p.windup() > p.contact() * 2, strike + ": wind-up dominates the contact");
      }

      for (ThorStrike strike : ThorStrike.values()) {
         if (strike.heavy() && strike.move() == ThorMove.HAMMER_COMBO) {
            assertTrue(ThorAttacks.of(strike).length() >= ThorAttacks.of(ThorStrike.SWING_RIGHT).length(), strike + ": heavy is not quicker");
         }
      }
   }

   @Test
   void unchangedElectricTimings() {
      assertEquals(new ThorMoveDefinition(0, 4, 0, MovePhase.MAIN, 1), ThorCombatTimings.of(ThorMove.ELECTRIC_MODE));
      assertEquals(new ThorMoveDefinition(3, 2, 7, MovePhase.FINISH, ThorCombatTimings.COMBO_STEPS), ThorCombatTimings.of(ThorMove.ELECTRIC_COMBO));
      assertEquals(new ThorMoveDefinition(7, 3, 8, MovePhase.FINISH, ThorCombatTimings.COMBO_STEPS), ThorCombatTimings.of(ThorMove.ELECTRIC_WAVE));
      assertEquals(new ThorMoveDefinition(0, ThorCombatTimings.STORM_MAX_TICKS, 12, null, 1), ThorCombatTimings.of(ThorMove.ELECTRIC_STORM));
   }

   @Test
   void moveDefinitionsAreTheEnvelopesAndKeepTheirMechanics() {
      assertEquals(ThorAttacks.of(ThorStrike.SWING_RIGHT).phases().definition(MovePhase.FINISH, ThorCombatTimings.COMBO_STEPS),
         ThorCombatTimings.of(ThorMove.HAMMER_COMBO));
      assertEquals(ThorAttacks.of(ThorStrike.JAB).phases().definition(MovePhase.FINISH, ThorCombatTimings.COMBO_STEPS),
         ThorCombatTimings.of(ThorMove.FIST_COMBO));
      assertEquals(ThorAttacks.of(ThorStrike.MMB_BLOW).phases().definition(MovePhase.FINISH, 1), ThorCombatTimings.of(ThorMove.HAMMER_STRIKE));
      assertEquals(new ThorMoveDefinition(72, 10, 46, null, 1), ThorCombatTimings.of(ThorMove.WRATH));
      for (ThorMove move : ThorCombatTimings.DEFINITIONS.keySet()) {
         for (ThorAttacks.Attack row : ThorAttacks.rows(move)) {
            ThorMoveDefinition d = ThorCombatTimings.of(row);
            assertEquals(ThorCombatTimings.of(move).cancellableFrom(), d.cancellableFrom(), row.clip());
            assertEquals(ThorCombatTimings.of(move).maxSteps(), d.maxSteps(), row.clip());
         }
      }

      for (ThorAttacks.Attack row : ThorAttacks.rows(ThorMove.ELECTRIC_COMBO)) {
         assertEquals(ThorAttacks.envelope(ThorMove.ELECTRIC_COMBO).phases(), row.phases(), "both hands share one timeline");
      }
   }

   @Test
   void drawAndHolsterRowsAreTheFoundationTimings() {
      ThorAttacks.Phases draw = ThorAttacks.envelope(ThorMove.DRAW).phases();
      assertEquals(new ThorAttacks.Phases(ThorRules.DRAW_INTRO, ThorRules.DRAW_MAIN, ThorRules.DRAW_FINISH), draw);
      assertEquals(ThorMove.DRAW.ticks(MovePhase.INTRO), draw.windup());
      ThorAttacks.Phases holster = ThorAttacks.envelope(ThorMove.HOLSTER).phases();
      assertEquals(ThorMove.HOLSTER.length(), holster.length());
   }

   @Test
   void comboWindowsScaleWithTheSteps() {
      // Old: a 6-tick grace after a 16-tick hammer step and an 11-tick fist step.
      double hammer = average(ThorMove.HAMMER_COMBO) / 16.0;
      double fist = average(ThorMove.FIST_COMBO) / 11.0;
      assertEquals(6.0 * hammer, ThorCombatTimings.HAMMER_COMBO_GRACE, 1.0, "hammer grace ~ proportional");
      assertEquals(6.0 * fist, ThorCombatTimings.FIST_COMBO_GRACE, 1.0, "fist grace ~ proportional");
      assertEquals(ThorCombatTimings.HAMMER_COMBO_GRACE, ThorCombatTimings.comboGrace(ThorMove.HAMMER_COMBO));
      assertEquals(ThorCombatTimings.FIST_COMBO_GRACE, ThorCombatTimings.comboGrace(ThorMove.FIST_COMBO));
      assertEquals(0, ThorCombatTimings.comboGrace(ThorMove.WRATH));
      for (ThorStrike strike : ThorStrike.values()) {
         ThorAttacks.Phases p = ThorAttacks.of(strike).phases();
         // The buffer opens on the recovery (cancellableFrom FINISH): the same share of the step as before (8/16, 5/11, 9/18).
         double share = p.chainFrom() / (double)p.length();
         assertTrue(share >= 0.42 && share <= 0.56, strike + " chain share " + share);
      }
   }

   private static double average(ThorMove move) {
      return ThorAttacks.rows(move).stream().mapToInt(ThorAttacks.Attack::length).average().orElseThrow();
   }

   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorDevCommands.resetForTests();
      ThorCombatModule.resetForTests();
   }

   @Test
   void aRetimedStepHitsOnItsRow() {
      ThorCombatModule.install();
      for (ThorStrike strike : ThorStrike.values()) {
         ThorAttacks.Phases p = ThorAttacks.of(strike).phases();
         StagedTimeline<ThorMove> timeline = new StagedTimeline<>();
         assertSame(StagedTimeline.Admission.START, timeline.request(strike.move(), -1), strike.name());
         assertTrue(timeline.retime(p.windup(), p.contact(), p.recovery()));
         assertEquals(p.length(), timeline.length(), strike.name());
         int contactAt = -1;
         for (int t = 1; t <= p.length(); t++) {
            if (timeline.tick() == StagedTimeline.Event.ENTER_MAIN) {
               contactAt = t;
            }
         }

         assertEquals(p.contactTick(), contactAt, strike.name());
         assertFalse(timeline.running(), strike.name());
      }
   }

   @Test
   void clipNamesFollowTheScheme() {
      Set<String> clips = new HashSet<>();
      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         assertTrue(CLIP_ID.matcher(row.clip()).matches(), row.clip());
         assertEquals(row.clip() + ThorAttacks.FP_SUFFIX, row.fpClip());
         assertTrue(clips.add(row.clip()) && clips.add(row.fpClip()), "unique: " + row.clip());
         assertSame(row, ThorAttacks.byClip(row.clip()));
         assertFalse(row.cue().isBlank(), row.clip() + " cue");
         assertTrue(row.cue().startsWith("thor."), row.clip() + " cue id");
         assertFalse(row.vfx().isBlank(), row.clip() + " vfx");
         ThorStrike strike = row.strike();
         if (strike != null) {
            assertEquals(expectedClip(strike), row.clip(), "naming scheme");
         }
      }
   }

   static String expectedClip(ThorStrike strike) {
      String name = strike.name().toLowerCase(Locale.ROOT);
      return switch (strike.move()) {
         case HAMMER_COMBO -> "hammer_" + name;
         case FIST_COMBO -> "fist_" + (name.startsWith("fist_") ? name.substring(5) : name);
         case HAMMER_STRIKE -> "hammer_strike_" + (name.startsWith("mmb_") ? name.substring(4) : name);
         default -> throw new AssertionError(strike);
      };
   }

   @Test
   void trailsStayInsideTheHitbox() {
      for (ThorStrike strike : ThorStrike.values()) {
         ThorAttacks.Trail trail = ThorAttacks.of(strike).trail();
         if (trail.none()) {
            continue;
         }

         switch (strike.shape()) {
            case ARC, THRUST -> {
               assertTrue(Math.abs(trail.yawFrom()) <= strike.arc() && Math.abs(trail.yawTo()) <= strike.arc(), strike + ": trail inside the cone");
            }
            case RING -> assertTrue(Math.abs(trail.yawTo() - trail.yawFrom()) >= 270.0F, strike + ": a spin goes around");
            case SLAM -> assertTrue(trail.pitchFrom() > 45.0F && trail.pitchTo() < -45.0F, strike + ": from above into the ground");
         }

         assertTrue(Math.abs(trail.pitchFrom()) <= 90.0F && Math.abs(trail.pitchTo()) <= 90.0F, strike + " pitch");
      }

      // Rework spec §3 trajectories of the main chain.
      ThorAttacks.Trail horizontal = ThorAttacks.of(ThorStrike.SWING_RIGHT).trail();
      assertTrue(Math.abs(horizontal.pitchFrom() - horizontal.pitchTo()) < 15.0F && Math.abs(horizontal.yawFrom() - horizontal.yawTo()) > 90.0F);
      ThorAttacks.Trail diagonal = ThorAttacks.of(ThorStrike.SWING_BACKHAND).trail();
      assertTrue(diagonal.pitchFrom() > 30.0F && diagonal.pitchTo() < 0.0F && Math.abs(diagonal.yawFrom() - diagonal.yawTo()) > 60.0F);
      ThorAttacks.Trail uppercut = ThorAttacks.of(ThorStrike.UPPERCUT).trail();
      assertTrue(uppercut.pitchFrom() < -30.0F && uppercut.pitchTo() > 45.0F);
      ThorAttacks.Trail slam = ThorAttacks.of(ThorStrike.GROUND_SLAM).trail();
      assertTrue(slam.pitchFrom() > 60.0F && slam.pitchTo() < -45.0F);
   }
}
