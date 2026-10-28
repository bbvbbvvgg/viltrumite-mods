package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Read-only view of Mjölnir for the sessions. A copy: changing it changes
 * nothing. Produced by the installed {@link HammerAccess}: the foundation
 * default knows only the per-player {@code ThorState.hammer} and leaves
 * {@code logicalId}, {@code dimension} and {@code position} null; session A
 * installs the world-hammer access (SavedData / entity), the only writer.
 */
public record HammerView(
   @Nullable UUID logicalId,
   @Nullable UUID activeOwnerId,
   HammerState phase,
   boolean inHand,
   @Nullable ResourceKey<Level> dimension,
   @Nullable Vec3 position,
   boolean detached,
   boolean airborne
) {
   public HammerView {
      if (phase == null) {
         throw new IllegalArgumentException("phase is required");
      }
   }

   /** Per-player view without world facts (detached / airborne derived from the phase only). */
   public HammerView(@Nullable UUID logicalId, @Nullable UUID activeOwnerId, HammerState phase, boolean inHand, @Nullable ResourceKey<Level> dimension,
      @Nullable Vec3 position) {
      this(logicalId, activeOwnerId, phase, inHand, dimension, position, false, phase == HammerState.THROWN || phase == HammerState.RETURNING);
   }

   /**
    * Flying after a throw, the one phase Y pulls to (review m6: not "any
    * flight"; for any flight read {@link #airborne()}).
    */
   public boolean flying() {
      return this.phase == HammerState.THROWN;
   }

   /** On the belt for real: attached to Thor and not in the hand (HOLSTERED and not detached). */
   public boolean onBelt() {
      return this.phase == HammerState.HOLSTERED && !this.detached;
   }
}
