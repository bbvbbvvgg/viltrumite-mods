package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One bridge event, immutable. {@code eventId} comes from
 * {@link ThorEvents#nextId()} and is the dedupe key: the same event posted
 * twice reaches listeners once. {@code attackId} identifies the swing / combo
 * step (0 when not an attack); {@code hammerInHand} is captured when the
 * event happens (the hit), never after a death. {@code success} is used by
 * COMBO_END.
 */
public record ThorEvent(
   long eventId,
   ThorEventType type,
   UUID attackerId,
   @Nullable UUID targetId,
   @Nullable ThorMove move,
   int attackId,
   boolean hammerInHand,
   float amount,
   boolean success
) {
   public ThorEvent {
      if (type == null || attackerId == null) {
         throw new IllegalArgumentException("type and attackerId are required");
      }
   }
}
