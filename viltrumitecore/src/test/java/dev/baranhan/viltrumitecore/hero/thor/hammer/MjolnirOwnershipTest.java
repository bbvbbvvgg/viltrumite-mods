package dev.baranhan.viltrumitecore.hero.thor.hammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A-M8: the ownership and phase rules of the one logical hammer as a table
 * ({@link MjolnirOwnership}, the pure rules MjolnirWorld applies), plus the
 * limbo cases that soft-locked it (A-M1 first fall far away, A-M6 a flight
 * inherited by a new Thor, A-m17 the endless void recovery).
 */
class MjolnirOwnershipTest {
   private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
   private static final UUID OLD = UUID.fromString("00000000-0000-0000-0000-00000000b001");

   /** A record standing in {@code phase}, reached through the real state machine. */
   static MjolnirRecord at(MjolnirPhase phase) {
      MjolnirRecord record = new MjolnirRecord();
      switch (phase) {
         case NOT_CREATED -> {
         }
         case WORLD_FALLING -> record.create(ID, MjolnirPhase.WORLD_FALLING);
         case IN_HAND -> record.create(ID, MjolnirPhase.IN_HAND);
         case DIMENSION_TRANSFER -> {
            record.create(ID, MjolnirPhase.IN_HAND);
            assertTrue(record.go(MjolnirPhase.THROWN));
            assertTrue(record.go(MjolnirPhase.DIMENSION_TRANSFER));
         }
         case DEATH_IMPACT -> {
            record.create(ID, MjolnirPhase.IN_HAND);
            assertTrue(record.go(MjolnirPhase.THROWN));
            assertTrue(record.go(MjolnirPhase.RETURNING));
            assertTrue(record.go(MjolnirPhase.DEATH_IMPACT));
         }
         default -> {
            record.create(ID, MjolnirPhase.IN_HAND);
            assertTrue(record.go(MjolnirPhase.THROWN));
            if (phase != MjolnirPhase.THROWN) {
               assertTrue(record.go(phase), "reach " + phase);
            }
         }
      }

      assertSame(phase, record.phase());
      record.activeOwner = record.exists() ? OLD : null;
      record.lastOwner = record.activeOwner;
      return record;
   }

   @Test
   void cleanupTableCoversEveryReasonAndPhase() {
      for (CleanupReason reason : CleanupReason.values()) {
         for (MjolnirPhase phase : MjolnirPhase.values()) {
            MjolnirOwnership.Cleanup expected = switch (reason) {
               case DEATH, ERASED -> phase == MjolnirPhase.IN_HAND ? MjolnirOwnership.Cleanup.DROP
                  : phase == MjolnirPhase.RETURNING ? MjolnirOwnership.Cleanup.DEATH_STRIKE : MjolnirOwnership.Cleanup.NONE;
               case DISCONNECT, HERO_CHANGE -> switch (phase) {
                  case IN_HAND -> MjolnirOwnership.Cleanup.DROP;
                  case THROWN, RETURNING, DIMENSION_TRANSFER -> MjolnirOwnership.Cleanup.LOOSEN;
                  default -> MjolnirOwnership.Cleanup.NONE;
               };
            };
            assertSame(expected, MjolnirOwnership.onCleanup(phase, reason, true), reason + " in " + phase);
         }
      }

      assertSame(MjolnirOwnership.Cleanup.NONE, MjolnirOwnership.onCleanup(MjolnirPhase.IN_HAND, CleanupReason.DEATH, false),
         "without the approved death drop the hammer stays with the dead Thor");
   }

   @Test
   void onlyLeavingTheGameOrTheHeroFreesTheSlot() {
      assertTrue(MjolnirOwnership.freesSlot(CleanupReason.DISCONNECT));
      assertTrue(MjolnirOwnership.freesSlot(CleanupReason.HERO_CHANGE));
      assertFalse(MjolnirOwnership.freesSlot(CleanupReason.DEATH), "№208: the dead Thor comes back as Thor");
      assertFalse(MjolnirOwnership.freesSlot(CleanupReason.ERASED));
   }

   /** A-M6 / №213: a throw is never inherited: the holder going (or a new holder) makes it fall, keeping its ballistic velocity. */
   @Test
   void aThrowFallsWhenItsThrowerGoes() {
      MjolnirRecord record = at(MjolnirPhase.THROWN);
      record.velocity(2.0, 0.5, -1.0);
      record.charged = true;
      record.recallPending = true;
      record.flightHits.add(7);
      record.flightTicks = 30;
      assertSame(MjolnirOwnership.Cleanup.LOOSEN, MjolnirOwnership.onOwnerGone(MjolnirPhase.THROWN));
      assertTrue(MjolnirOwnership.loosen(record));
      assertSame(MjolnirPhase.FALLING, record.phase());
      assertEquals(2.0, record.vx);
      assertEquals(0.5, record.vy);
      assertFalse(record.charged(), "a fall is not a charged throw");
      assertFalse(record.recallPending);
      assertTrue(record.flightHits.isEmpty());
      assertEquals(0, record.flightTicks);
      assertFalse(MjolnirPull.allowed(record.phase()), "Y cannot ride a throw somebody else made");
   }

   @Test
   void homingAndAttachedHammersDropStraightDown() {
      for (MjolnirPhase phase : new MjolnirPhase[]{MjolnirPhase.IN_HAND, MjolnirPhase.RETURNING, MjolnirPhase.DIMENSION_TRANSFER}) {
         MjolnirRecord record = at(phase);
         record.velocity(3.0, 3.0, 3.0);
         assertTrue(MjolnirOwnership.loosen(record), phase.name());
         assertSame(MjolnirPhase.FALLING, record.phase(), phase.name());
         assertEquals(0.0, record.vx);
         assertEquals(-0.4, record.vy);
      }
   }

   @Test
   void lyingFallingAndLimboHammersStayWhereTheyAre() {
      for (MjolnirPhase phase : new MjolnirPhase[]{MjolnirPhase.NOT_CREATED, MjolnirPhase.WORLD_FALLING, MjolnirPhase.RESTING, MjolnirPhase.FALLING,
         MjolnirPhase.DEATH_IMPACT, MjolnirPhase.VOID_RECOVERY}) {
         MjolnirRecord record = at(phase);
         assertFalse(MjolnirOwnership.loosens(phase), phase.name());
         assertFalse(MjolnirOwnership.loosen(record), phase.name());
         assertSame(phase, record.phase());
      }
   }

   /** A-NEW1: the previous Thor's waiting recall never fires for the next one. */
   @Test
   void aNewHolderForgetsThePreviousHoldersRecall() {
      MjolnirRecord record = at(MjolnirPhase.WORLD_FALLING);
      record.recallPending = true;
      assertTrue(MjolnirOwnership.changeOwner(record));
      assertSame(MjolnirPhase.WORLD_FALLING, record.phase(), "the first fall itself goes on");
      assertFalse(record.recallPending);
      assertFalse(MjolnirOwnership.changeOwner(at(MjolnirPhase.RESTING)), "nothing to change");
   }

   /** A-M1: a first fall nobody simulates still ends on the ground, in a bounded number of steps, and lands at once for a recall. */
   @Test
   void aFirstFallFarFromEveryPlayerAlwaysLands() {
      MjolnirRecord record = at(MjolnirPhase.WORLD_FALLING);
      record.place("minecraft:overworld", 0.5, 224.0, 0.5);
      double ground = 64.0;
      int steps = 0;
      while (!MjolnirOwnership.remoteFallStep(record, ground, false)) {
         steps++;
         assertTrue(steps <= (int)Math.ceil((224.0 - ground) / MjolnirTuning.FALL_SPEED), "bounded");
         assertEquals(-MjolnirTuning.FALL_SPEED, record.vy);
      }

      assertEquals(ground, record.y);
      assertEquals(0.0, record.vy);

      MjolnirRecord recalled = at(MjolnirPhase.WORLD_FALLING);
      recalled.place("minecraft:overworld", 0.5, 224.0, 0.5);
      assertTrue(MjolnirOwnership.remoteFallStep(recalled, ground, true), "a waiting recall lands it now");
      assertEquals(ground, recalled.y);
      assertFalse(MjolnirOwnership.remoteFallStep(at(MjolnirPhase.RESTING), ground, true), "only a first fall");
   }

   /** A-M1, logical: WORLD_FALLING without a body + recall => on the ground (RESTING, fall done) => called back (RETURNING). */
   @Test
   void aRecallResolvesAFirstFallWithoutABody() {
      assertTrue(MjolnirOwnership.landsOnRecall(MjolnirPhase.WORLD_FALLING, false));
      assertFalse(MjolnirOwnership.landsOnRecall(MjolnirPhase.WORLD_FALLING, true), "a visible fall finishes on its own");
      assertFalse(MjolnirOwnership.landsOnRecall(MjolnirPhase.DEATH_IMPACT, false), "the death strike has its own remote flight");
      MjolnirRecord record = at(MjolnirPhase.WORLD_FALLING);
      assertTrue(MjolnirOwnership.remoteFallStep(record, 70.0, true));
      assertTrue(record.go(MjolnirPhase.RESTING), "settle");
      assertTrue(MjolnirPhase.RESTING.recallable());
      assertTrue(record.go(MjolnirPhase.RETURNING), "the recall can start");
   }

   /** A-m17: the void recovery climbs a bounded ladder and ends parked; a parked hammer stops being parked when it moves. */
   @Test
   void theVoidRecoveryNeverLoopsForever() {
      int n = MjolnirTuning.VOID_RETRIES;
      for (int attempt = 1; attempt <= n; attempt++) {
         assertSame(MjolnirOwnership.VoidStep.SAFE, MjolnirOwnership.voidStep(attempt, true));
         assertSame(MjolnirOwnership.VoidStep.SPAWN, MjolnirOwnership.voidStep(attempt, false));
      }

      for (int attempt = n + 1; attempt <= 2 * n; attempt++) {
         assertSame(MjolnirOwnership.VoidStep.SPAWN, MjolnirOwnership.voidStep(attempt, true));
      }

      assertSame(MjolnirOwnership.VoidStep.PARK, MjolnirOwnership.voidStep(2 * n + 1, true));
      assertSame(MjolnirOwnership.VoidStep.PARK, MjolnirOwnership.voidStep(1000, false));

      MjolnirRecord record = at(MjolnirPhase.VOID_RECOVERY);
      assertTrue(record.go(MjolnirPhase.RESTING), "the park is a transition of the table");
      record.parked = true;
      assertTrue(record.go(MjolnirPhase.RETURNING));
      assertFalse(record.parked, "leaving the rest ends the park");
   }

   /** A-m13: the flight facts live in the record (a re-spawned body keeps them) but are never saved. */
   @Test
   void flightFactsSurviveABodyButNotASave() {
      MjolnirRecord record = at(MjolnirPhase.RETURNING);
      record.flightHits.add(42);
      record.flightTicks = 12;
      record.ripPending = true;
      record.voidAttempts = 2;
      MjolnirRecord loaded = MjolnirRecord.load(record.save());
      assertTrue(loaded.flightHits.isEmpty());
      assertEquals(0, loaded.flightTicks);
      assertFalse(loaded.ripPending);
      assertEquals(0, loaded.voidAttempts);
      assertNotEquals(0, record.flightHits.size(), "the live record keeps them");
      record.clearFlight();
      assertTrue(record.flightHits.isEmpty());
      assertFalse(record.ripPending);
   }
}
