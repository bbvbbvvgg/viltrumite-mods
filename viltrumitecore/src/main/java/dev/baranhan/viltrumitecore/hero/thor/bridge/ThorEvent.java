package dev.baranhan.viltrumitecore.hero.thor.bridge;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;

/**
 * One bridge event, immutable. {@code eventId} comes from
 * {@link ThorEvents#nextId()} and is the dedupe key: the same event posted
 * twice reaches listeners once. {@code attackId} identifies the swing / combo
 * step (0 when not an attack); {@code hammerInHand} is captured when the
 * event happens (the hit), never after a death. {@code amount} is damage
 * only (0 when the event carries none); {@code success} is used by COMBO_END.
 * {@code where} names the entities by their network id, the dimension and
 * the position at the event, so a listener never searches every level for a
 * UUID ({@link Where#UNKNOWN} from pure code). {@code detail} is a
 * type-specific integer documented on each {@link ThorEventType}.
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
   boolean success,
   Where where,
   int detail
) {
   /** Entity ids (-1 = none / unknown), dimension id (null = unknown) and position (NaN = unknown) at the event. */
   public record Where(int attackerEntityId, int targetEntityId, @Nullable String dimension, double x, double y, double z) {
      public static final Where UNKNOWN = new Where(-1, -1, null, Double.NaN, Double.NaN, Double.NaN);

      public static Where of(Entity attacker, @Nullable Entity target) {
         Entity at = target != null ? target : attacker;
         return new Where(attacker.getId(), target == null ? -1 : target.getId(), attacker.level().dimension().location().toString(), at.getX(), at.getY(),
            at.getZ());
      }

      public boolean known() {
         return this.dimension != null;
      }
   }

   public ThorEvent {
      if (type == null || attackerId == null) {
         throw new IllegalArgumentException("type and attackerId are required");
      }

      if (where == null) {
         where = Where.UNKNOWN;
      }
   }

   /** Without location or detail (pure code, tests). */
   public ThorEvent(long eventId, ThorEventType type, UUID attackerId, @Nullable UUID targetId, @Nullable ThorMove move, int attackId, boolean hammerInHand,
      float amount, boolean success) {
      this(eventId, type, attackerId, targetId, move, attackId, hammerInHand, amount, success, Where.UNKNOWN, 0);
   }

   public ThorEvent at(Where where) {
      return new ThorEvent(this.eventId, this.type, this.attackerId, this.targetId, this.move, this.attackId, this.hammerInHand, this.amount, this.success,
         where, this.detail);
   }

   public ThorEvent withDetail(int detail) {
      return new ThorEvent(this.eventId, this.type, this.attackerId, this.targetId, this.move, this.attackId, this.hammerInHand, this.amount, this.success,
         this.where, detail);
   }
}
