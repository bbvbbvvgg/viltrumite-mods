package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.CleanupReason;

/**
 * Pure ownership and limbo decisions of the one logical Mjölnir, over its
 * saved {@link MjolnirRecord} only (no world, no body, no events): who loses
 * it and how, what an owner change does to a flight, how a first fall far
 * from every player ends, how the void recovery stops looping.
 * {@link MjolnirWorld} stays the single writer: it asks these rules, applies
 * the record change, then does the world side (body, sounds, bridge events).
 * Unit-tested as a table in {@code MjolnirOwnershipTest}.
 */
final class MjolnirOwnership {
   /** What a cleanup (or the disappearance) of the active Thor does to the hammer. */
   enum Cleanup {
      /** Nothing changes (it lies somewhere, or the slot stays his and the hammer waits for his recall). */
      NONE,
      /** Attached: it leaves the hand and falls from where he was. */
      DROP,
      /** №169: died during the recall: on to the fixed death point, then the strike. */
      DEATH_STRIKE,
      /** Flying for him (thrown, homing): it falls first (№3.2 / №213), nobody inherits the flight. */
      LOOSEN
   }

   /** Last-resort ladder of the void recovery (A-m17; №217 is open, PLACEHOLDER). */
   enum VoidStep {
      /** Back to the last resting point. */
      SAFE,
      /** The last resting point keeps failing (or there is none): the world spawn. */
      SPAWN,
      /** The spawn fails too (a void world): parked at the spawn, resting without support until recalled. */
      PARK
   }

   private MjolnirOwnership() {
   }

   /** Phases that belong to a holder's flight or hand: they fall when the holder goes (A-M6: THROWN included). */
   static boolean loosens(MjolnirPhase phase) {
      return phase == MjolnirPhase.IN_HAND || phase == MjolnirPhase.THROWN || phase.homing();
   }

   /** Death / logout / hero change of the active Thor (§3.5). */
   static Cleanup onCleanup(MjolnirPhase phase, CleanupReason reason, boolean deathInHandDrops) {
      return switch (reason) {
         case DEATH, ERASED -> phase == MjolnirPhase.IN_HAND ? deathInHandDrops ? Cleanup.DROP : Cleanup.NONE
            : phase == MjolnirPhase.RETURNING ? Cleanup.DEATH_STRIKE : Cleanup.NONE;
         case DISCONNECT, HERO_CHANGE -> onOwnerGone(phase);
      };
   }

   /** Logout and hero change free the slot; a dead or erased Thor comes back as Thor and keeps it (№208). */
   static boolean freesSlot(CleanupReason reason) {
      return reason == CleanupReason.DISCONNECT || reason == CleanupReason.HERO_CHANGE;
   }

   /** The active owner is not there any more (offline without a logout event, not Thor). */
   static Cleanup onOwnerGone(MjolnirPhase phase) {
      if (phase == MjolnirPhase.IN_HAND) {
         return Cleanup.DROP;
      }

      return loosens(phase) ? Cleanup.LOOSEN : Cleanup.NONE;
   }

   /**
    * The previous holder is gone: a thrown or homing hammer falls first
    * (FALLING; a throw keeps its ballistic velocity, a homing one drops),
    * its flight facts and a pending recall are forgotten. True when the
    * record changed. IN_HAND is dropped by the caller from the holder's
    * position instead ({@link Cleanup#DROP}); here it falls where the record is.
    */
   static boolean loosen(MjolnirRecord record) {
      MjolnirPhase before = record.phase;
      if (!loosens(before)) {
         return false;
      }

      if (before == MjolnirPhase.DIMENSION_TRANSFER && !record.go(MjolnirPhase.RETURNING)) {
         return false;
      }

      if (!record.go(MjolnirPhase.FALLING)) {
         return false;
      }

      if (before != MjolnirPhase.THROWN) {
         record.velocity(0.0, -0.4, 0.0);
      }

      record.lying = false;
      record.charged = false;
      record.recallPending = false;
      record.clearFlight();
      return true;
   }

   /**
    * Somebody else becomes the holder: whatever flew for the previous one
    * falls first, and the previous holder's pending recall never fires for
    * the new one (A-NEW1). True when the record changed.
    */
   static boolean changeOwner(MjolnirRecord record) {
      boolean changed = loosen(record);
      if (record.recallPending) {
         record.recallPending = false;
         changed = true;
      }

      return changed;
   }

   /**
    * A-M1: one server step of a first fall that has no body (its chunk does
    * not tick entities): straight down at the fall speed to {@code ground}.
    * {@code land} ends it at once (a recall is waiting). True when it is on
    * the ground; the caller then settles it (RESTING, fall done).
    */
   static boolean remoteFallStep(MjolnirRecord record, double ground, boolean land) {
      if (record.phase != MjolnirPhase.WORLD_FALLING) {
         return false;
      }

      double next = record.y - MjolnirTuning.FALL_SPEED;
      if (land || next <= ground) {
         record.y = ground;
         record.velocity(0.0, 0.0, 0.0);
         return true;
      }

      record.y = next;
      record.velocity(0.0, -MjolnirTuning.FALL_SPEED, 0.0);
      return false;
   }

   /** A recall cannot wait for a first fall nobody simulates: it is put on the ground at once (A-M1). */
   static boolean landsOnRecall(MjolnirPhase phase, boolean bodyPresent) {
      return phase == MjolnirPhase.WORLD_FALLING && !bodyPresent;
   }

   /** The {@code attempt}-th recovery (1-based) since the last real landing. */
   static VoidStep voidStep(int attempt, boolean hasSafe) {
      if (attempt <= MjolnirTuning.VOID_RETRIES && hasSafe) {
         return VoidStep.SAFE;
      }

      return attempt <= 2 * MjolnirTuning.VOID_RETRIES ? VoidStep.SPAWN : VoidStep.PARK;
   }
}
