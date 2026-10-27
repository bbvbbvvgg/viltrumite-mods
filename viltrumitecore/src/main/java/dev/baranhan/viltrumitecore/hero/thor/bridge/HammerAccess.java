package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Read access to Mjölnir. One implementation is active: {@link #FOUNDATION}
 * until session A installs the world hammer through
 * {@link ThorBridge#installHammerAccess}. Readers (B / C) never write.
 */
@FunctionalInterface
public interface HammerAccess {
   /** Safe default: the per-player state only, no world hammer. */
   HammerAccess FOUNDATION = (thor, state) -> new HammerView(null, thor,
      state == null ? HammerState.HOLSTERED : state.hammer, state != null && state.hammer == HammerState.IN_HAND, null, null);

   /** Mjölnir as seen for this Thor player; {@code state} may be null when unknown. */
   HammerView view(UUID thor, @Nullable ThorState state);
}
