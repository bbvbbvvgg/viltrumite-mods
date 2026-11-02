package dev.baranhan.viltrumitecore.hero.venom.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Attack;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Phase;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Situation;
import dev.baranhan.viltrumitecore.hero.venom.boss.VenomBossBrain.Threat;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Venom boss's phases and adaptive choices (spec §9.2-§9.4). */
class VenomBossBrainTest {
   private static Situation at(double distance) {
      return new Situation(distance, 0, true, true, false, 1, false);
   }

   private static void ticks(VenomBossBrain brain, int n) {
      for (int i = 0; i < n; i++) {
         brain.tick();
      }
   }

   private static VenomBossBrain inPhase(Phase phase) {
      VenomBossBrain brain = new VenomBossBrain();
      brain.restore(phase == Phase.HUNT ? 1.0F : phase == Phase.FRENZY ? 0.5F : 0.2F);
      assertEquals(phase, brain.phase());
      return brain;
   }

   @Test
   void phasesFollowHealthAtTheThirds() {
      assertEquals(Phase.HUNT, VenomBossBrain.phaseFor(1.0F));
      assertEquals(Phase.HUNT, VenomBossBrain.phaseFor(0.67F));
      assertEquals(Phase.FRENZY, VenomBossBrain.phaseFor(200.0F / 300.0F));
      assertEquals(Phase.FRENZY, VenomBossBrain.phaseFor(0.34F));
      assertEquals(Phase.STARVING, VenomBossBrain.phaseFor(100.0F / 300.0F));
      assertEquals(Phase.STARVING, VenomBossBrain.phaseFor(0.0F));
      assertEquals(Phase.STARVING, VenomBossBrain.phaseFor(Float.NaN), "a broken health reads as the last phase, never as fresh");
   }

   @Test
   void everyBorderRoarsOnceAndThePhaseNeverGoesBack() {
      VenomBossBrain brain = new VenomBossBrain();
      assertNull(brain.health(0.9F));
      assertFalse(brain.pendingRoar());
      assertEquals(Phase.FRENZY, brain.health(0.6F));
      assertTrue(brain.pendingRoar());
      assertEquals(Attack.ROAR, brain.choose(at(2.0)), "the roar goes before everything");
      brain.used(Attack.ROAR);
      assertFalse(brain.pendingRoar());
      assertNull(brain.health(0.55F), "the same phase does not roar again");
      assertNull(brain.health(0.95F), "healing back over the border does not undo the frenzy");
      assertEquals(Phase.FRENZY, brain.phase());
      assertEquals(Phase.STARVING, brain.health(0.3F));
      assertTrue(brain.pendingRoar());
   }

   @Test
   void aBigHitAcrossBothBordersRoarsOnceIntoTheLastPhase() {
      VenomBossBrain brain = new VenomBossBrain();
      assertEquals(Phase.STARVING, brain.health(0.1F));
      brain.used(Attack.ROAR);
      assertNull(brain.health(0.05F));
      assertFalse(brain.pendingRoar());
   }

   @Test
   void aReloadTakesThePhaseBackSilently() {
      VenomBossBrain brain = new VenomBossBrain();
      brain.restore(0.25F);
      assertEquals(Phase.STARVING, brain.phase());
      assertFalse(brain.pendingRoar());
      assertNull(brain.health(0.25F));
      assertNotEquals(Attack.ROAR, brain.choose(at(2.0)));
   }

   @Test
   void theHuntHasOnlyTheBaseKit() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      Set<Attack> seen = EnumSet.noneOf(Attack.class);
      for (double d = 0.5; d < 30; d += 0.25) {
         seen.add(brain.choose(at(d)));
      }
      assertTrue(seen.containsAll(EnumSet.of(Attack.CLAW, Attack.TENDRIL, Attack.LEAP)), "base kit " + seen);
      for (Attack locked : EnumSet.of(Attack.IMPALE, Attack.DIVE, Attack.STORM, Attack.SHELL, Attack.POUNCE, Attack.RETREAT)) {
         assertFalse(seen.contains(locked), locked + " is not for the hunt");
      }
   }

   @Test
   void theFrenzyUnlocksTheImpaleAndTheDive() {
      VenomBossBrain brain = inPhase(Phase.FRENZY);
      assertEquals(Attack.IMPALE, brain.choose(at(5.0)));
      assertEquals(Attack.DIVE, brain.choose(at(10.0)));
      brain.used(Attack.DIVE);
      assertEquals(Attack.TENDRIL, brain.choose(at(10.0)), "on the dive's cooldown it yanks instead");
      assertNotEquals(Attack.STORM, brain.choose(at(10.0)));
   }

   @Test
   void theStarvingBossCallsTheStormThenFallsBackOnItsKit() {
      VenomBossBrain brain = inPhase(Phase.STARVING);
      assertEquals(Attack.STORM, brain.choose(at(8.0)));
      Situation blind = new Situation(8.0, 0, false, true, false, 1, false);
      assertNotEquals(Attack.STORM, brain.choose(blind), "the storm needs the target in sight");
      brain.used(Attack.STORM);
      assertNotEquals(Attack.STORM, brain.choose(at(8.0)));
      ticks(brain, brain.cooldown(Attack.STORM));
      assertEquals(Attack.STORM, brain.choose(at(8.0)));
   }

   @Test
   void cooldownsShortenWithEachPhase() {
      VenomBossBrain hunt = inPhase(Phase.HUNT);
      VenomBossBrain frenzy = inPhase(Phase.FRENZY);
      VenomBossBrain starving = inPhase(Phase.STARVING);
      for (VenomBossBrain b : new VenomBossBrain[]{hunt, frenzy, starving}) {
         b.used(Attack.TENDRIL);
      }
      assertEquals(Attack.TENDRIL.cooldown, hunt.cooldown(Attack.TENDRIL));
      assertTrue(frenzy.cooldown(Attack.TENDRIL) < hunt.cooldown(Attack.TENDRIL));
      assertTrue(starving.cooldown(Attack.TENDRIL) < frenzy.cooldown(Attack.TENDRIL));
      assertEquals(Math.round(Attack.TENDRIL.cooldown * 0.6F), starving.cooldown(Attack.TENDRIL));
   }

   @Test
   void shootersGetTheShellAndALongerTendril() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      double before = brain.tendrilRange();
      assertNotEquals(Attack.SHELL, brain.choose(at(12.0)));
      for (int i = 0; i < 3; i++) {
         brain.hurt(Threat.RANGED, 5.0F);
      }
      assertTrue(brain.adapted(Threat.RANGED));
      assertEquals(Threat.RANGED, brain.newlyAdapted());
      assertNull(brain.newlyAdapted(), "one taunt per adaptation");
      assertEquals(Attack.SHELL, brain.choose(at(12.0)));
      assertTrue(brain.tendrilRange() > before);
      brain.used(Attack.SHELL);
      assertEquals(Attack.TENDRIL, brain.choose(at(12.0)));
      brain.used(Attack.TENDRIL);
      assertEquals(Math.round(Attack.TENDRIL.cooldown * 0.6F), brain.cooldown(Attack.TENDRIL), "a chased target is yanked more often");
   }

   @Test
   void aMeleeFlurryIsSweptOff() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      assertEquals(Attack.CLAW, brain.choose(at(2.0)));
      for (int i = 0; i < 3; i++) {
         brain.hurt(Threat.MELEE, 5.0F);
         brain.tick();
      }
      assertTrue(brain.adapted(Threat.MELEE));
      assertEquals(3, brain.meleeStreak());
      assertEquals(Attack.LASH_SWEEP, brain.choose(at(2.0)));
      brain.used(Attack.LASH_SWEEP);
      assertEquals(0, brain.meleeStreak(), "the sweep ends the flurry");
      assertEquals(Math.round(Attack.LASH_SWEEP.cooldown * 0.5F), brain.cooldown(Attack.LASH_SWEEP));
      assertEquals(Attack.CLAW, brain.choose(at(2.0)));
   }

   @Test
   void aSlowStreamOfMeleeHitsIsNoFlurry() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      for (int i = 0; i < 4; i++) {
         brain.hurt(Threat.MELEE, 6.0F);
         ticks(brain, VenomBossBrain.STREAK_WINDOW + 1);
      }
      assertTrue(brain.meleeStreak() <= 1);
      assertEquals(Attack.CLAW, brain.choose(at(2.0)));
   }

   @Test
   void aCrowdIsSweptWhateverItsTactics() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      assertEquals(Attack.LASH_SWEEP, brain.choose(new Situation(2.0, 0, true, true, false, 2, false)));
   }

   @Test
   void fireMakesItJumpBackButTheWeaknessStays() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      brain.hurt(Threat.FIRE, 14.0F);
      assertTrue(brain.adapted(Threat.FIRE));
      assertEquals(Attack.RETREAT, brain.choose(at(2.0)));
      brain.used(Attack.RETREAT);
      assertEquals(Attack.CLAW, brain.choose(at(2.0)), "cornered on the retreat's cooldown it still fights");
      assertEquals(Attack.TENDRIL, brain.choose(at(8.0)), "from range it yanks the burner out instead");
   }

   @Test
   void aBellRingerIsHuntedOnceItLearnedTheNoise() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      assertFalse(brain.provokedByNoise(), "the first bells are only a weakness");
      brain.used(Attack.TENDRIL);
      brain.used(Attack.LEAP);
      brain.hurt(Threat.SOUND, 6.0F);
      brain.hurt(Threat.SOUND, 6.0F);
      assertTrue(brain.adapted(Threat.SOUND));
      assertNotEquals(Attack.TENDRIL, brain.choose(at(8.0)), "on cooldown before the next bell");
      assertTrue(brain.provokedByNoise());
      assertEquals(Attack.TENDRIL, brain.choose(at(8.0)), "the next bell is answered with a yank");
      assertEquals(Attack.LEAP, brain.choose(new Situation(22.0, 0.0, false, true, false, 1, false)), "or a leap when out of reach");
   }

   @Test
   void aPlayerOnARoofIsPouncedOnFromTheWall() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      Situation roof = new Situation(10.0, 9.0, true, false, true, 1, false);
      assertNotEquals(Attack.POUNCE, brain.choose(roof));
      for (int i = 0; i < 100; i++) {
         brain.watch(10.0, 9.0);
         brain.tick();
      }
      assertTrue(brain.adapted(Threat.HEIGHT));
      assertEquals(Attack.POUNCE, brain.choose(roof));
      Situation falling = new Situation(10.0, 9.0, true, false, false, 1, false);
      assertNotEquals(Attack.POUNCE, brain.choose(falling), "no jump from mid-air");
   }

   @Test
   void threatsFadeSoANewTacticIsAnsweredAgain() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      brain.hurt(Threat.RANGED, 20.0F);
      ticks(brain, VenomBossBrain.HALF_LIFE);
      assertEquals(10.0F, brain.threat(Threat.RANGED), 0.05F);
      assertFalse(brain.adapted(Threat.RANGED));
      ticks(brain, VenomBossBrain.HALF_LIFE * 3);
      assertTrue(brain.threat(Threat.RANGED) < VenomBossBrain.ADAPT_AT * 0.25F);
      brain.newlyAdapted();
      brain.hurt(Threat.RANGED, 20.0F);
      assertEquals(Threat.RANGED, brain.newlyAdapted(), "forgotten, then learned again: a new taunt");
   }

   @Test
   void absurdDamageCannotPinAnAdaptationForever() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      brain.hurt(Threat.RANGED, Float.MAX_VALUE);
      brain.hurt(Threat.MELEE, Float.NaN);
      brain.hurt(Threat.FIRE, -5.0F);
      assertEquals(40.0F, brain.threat(Threat.RANGED), 1.0E-3F);
      assertEquals(0.0F, brain.threat(Threat.MELEE));
      assertEquals(0.0F, brain.threat(Threat.FIRE));
      ticks(brain, VenomBossBrain.HALF_LIFE * 2);
      assertFalse(brain.adapted(Threat.RANGED));
   }

   @Test
   void theTakeoverGoesFirstAndTheStarvingBossWantsHealthierPrey() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      assertEquals(VenomRules.TAKEOVER_HEALTH_FRACTION, brain.takeoverFraction());
      Situation prey = new Situation(3.0, 0, true, true, false, 1, true);
      assertEquals(Attack.TAKEOVER, brain.choose(prey));
      brain.delay(Attack.TAKEOVER, 100);
      assertEquals(Attack.CLAW, brain.choose(prey));
      assertEquals(VenomBossBrain.STARVING_TAKEOVER_FRACTION, inPhase(Phase.STARVING).takeoverFraction());
      Situation far = new Situation(VenomRules.TAKEOVER_RANGE + 1, 0, true, true, false, 1, true);
      assertNotEquals(Attack.TAKEOVER, inPhase(Phase.HUNT).choose(far));
   }

   @Test
   void theStarvingBiteComesThreeTimesPerHold() {
      int bites = 0;
      for (int t = 0; t <= 40; t++) {
         if (VenomBossBrain.biteAt(t)) {
            bites++;
         }
      }
      assertEquals(3, bites);
   }

   @Test
   void pouncesGoUpAndTowardsTheTargetWithinLimits() {
      double[] up = VenomBossBrain.pounce(0, 12, 0);
      assertEquals(0.0, up[0]);
      assertEquals(0.0, up[2]);
      assertTrue(up[1] > 1.0);
      double[] roof = VenomBossBrain.pounce(30, 40, 0);
      assertTrue(roof[0] > 0 && roof[0] <= 1.25);
      assertTrue(roof[1] <= 1.7);
      double[] low = VenomBossBrain.pounce(-5, -6, 5);
      assertTrue(low[0] < 0 && low[2] > 0);
      assertEquals(0.55, low[1], 1.0E-9, "a target below still gets a hop, never a downward launch");
   }

   @Test
   void nothingToDoMeansWalking() {
      VenomBossBrain brain = inPhase(Phase.HUNT);
      for (Attack a : Attack.values()) {
         if (a != Attack.NONE && a != Attack.ROAR) {
            brain.used(a);
         }
      }
      assertEquals(Attack.NONE, brain.choose(at(2.0)));
      assertEquals(Attack.NONE, brain.choose(at(12.0)));
   }
}
