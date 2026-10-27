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
   @Nullable Vec3 position
) {
   public HammerView {
      if (phase == null) {
         throw new IllegalArgumentException("phase is required");
      }
   }

   /** Flying after a throw (Y pulls to it). */
   public boolean flying() {
      return this.phase == HammerState.THROWN;
   }
}
