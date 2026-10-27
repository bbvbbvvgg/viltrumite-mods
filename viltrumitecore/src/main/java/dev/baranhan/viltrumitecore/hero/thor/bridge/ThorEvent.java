package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One bridge event. {@code id} comes from {@link ThorEvents#nextId()} and is
 * the dedupe key: the same event posted twice reaches listeners once.
 */
public record ThorEvent(long id, ThorEventType type, UUID source, @Nullable UUID target, @Nullable ThorMove move, float amount) {
   public ThorEvent {
      if (type == null || source == null) {
         throw new IllegalArgumentException("type and source are required");
      }
   }
}
