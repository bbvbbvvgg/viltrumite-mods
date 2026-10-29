package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * World phases of the one logical Mjölnir (session A spec §3.2). Saved by
 * key, never by ordinal. These are the rich world phases kept apart from the
 * two-bit {@link HammerState} wire value: {@link #personal()} projects a phase
 * onto the active Thor's synced state. MAGNETIZED_TO_HAMMER is a Thor state
 * ({@link MjolnirPull}), not a hammer phase.
 */
public enum MjolnirPhase {
   /** The first appearance has not happened yet. */
   NOT_CREATED,
   /** The one visible first fall from the sky. */
   WORLD_FALLING,
   /** Attached to the active Thor (drawn on the belt while an item takes the right hand). */
   IN_HAND,
   /** Autonomous flight after a throw; the only phase Y pulls to (№220). */
   THROWN,
   /** Lies / sticks in a block, solid. */
   RESTING,
   /** Falls under gravity (lost support, released from the hand). */
   FALLING,
   /** Flies straight to the active Thor. */
   RETURNING,
   /** Strikes the death point of a Thor who died during the recall. */
   DEATH_IMPACT,
   /** Technical hop to the Thor's dimension (no scene, no second hammer). */
   DIMENSION_TRANSFER,
   /** Emergency recovery out of the void. */
   VOID_RECOVERY;

   private static final MjolnirPhase[] VALUES = values();

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   @Nullable
   public static MjolnirPhase fromKey(@Nullable String key) {
      for (MjolnirPhase phase : VALUES) {
         if (phase.key().equals(key)) {
            return phase;
         }
      }

      return null;
   }

   /** Represented by the physical MjolnirEntity while its chunk is loaded. */
   public boolean physical() {
      return switch (this) {
         case WORLD_FALLING, THROWN, RESTING, FALLING, RETURNING, DEATH_IMPACT, VOID_RECOVERY -> true;
         default -> false;
      };
   }

   /** The hammer exists and is not attached to a Thor: MMB recalls it. */
   public boolean recallable() {
      return this != NOT_CREATED && this != IN_HAND;
   }

   /** Pulled by Y (№220): only flying after a throw. */
   public boolean pullable() {
      return this == THROWN;
   }

   /** Already on its way to the Thor. */
   public boolean homing() {
      return this == RETURNING || this == DIMENSION_TRANSFER;
   }

   /** The two-bit per-player state of the active Thor for this phase. */
   public HammerState personal() {
      return switch (this) {
         case IN_HAND -> HammerState.IN_HAND;
         case THROWN -> HammerState.THROWN;
         case RETURNING, DIMENSION_TRANSFER -> HammerState.RETURNING;
         default -> HammerState.HOLSTERED;
      };
   }

   /** Exists in the world, away from Thor and not flying to or from him (resting, falling, ...). */
   public boolean detached() {
      return this != NOT_CREATED && this.personal() == HammerState.HOLSTERED;
   }

   /** Allowed transitions (spec table; FALLING covers "released from the hand"). */
   public boolean canGo(MjolnirPhase next) {
      return next != this && next(this).contains(next);
   }

   private static Set<MjolnirPhase> next(MjolnirPhase phase) {
      return switch (phase) {
         case NOT_CREATED -> EnumSet.of(WORLD_FALLING, IN_HAND);
         case WORLD_FALLING -> EnumSet.of(RESTING, VOID_RECOVERY);
         case IN_HAND -> EnumSet.of(THROWN, RESTING, FALLING);
         case THROWN -> EnumSet.of(RESTING, FALLING, RETURNING, DIMENSION_TRANSFER, VOID_RECOVERY);
         case RESTING -> EnumSet.of(FALLING, RETURNING, IN_HAND, DIMENSION_TRANSFER, VOID_RECOVERY);
         case FALLING -> EnumSet.of(RESTING, RETURNING, DIMENSION_TRANSFER, VOID_RECOVERY);
         case RETURNING -> EnumSet.of(IN_HAND, DEATH_IMPACT, RESTING, FALLING, DIMENSION_TRANSFER, VOID_RECOVERY);
         case DEATH_IMPACT -> EnumSet.of(RESTING);
         case DIMENSION_TRANSFER -> EnumSet.of(RETURNING);
         case VOID_RECOVERY -> EnumSet.of(FALLING, RESTING);
      };
   }
}
