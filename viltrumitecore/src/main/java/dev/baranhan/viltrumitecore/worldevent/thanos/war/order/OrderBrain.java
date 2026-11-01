package dev.baranhan.viltrumitecore.worldevent.thanos.war.order;

import java.util.SplittableRandom;
import javax.annotation.Nullable;

/**
 * Move choice of a Black Order member (pure, deterministic for a seed), the same shape as Thanos's
 * brain: hard rules (cooldowns, never the same move twice in a row, a global gap between moves),
 * gates (range, air, cover, the control ledger) and weights (the member's own situational rules,
 * variety over the last five moves). Null means "move into position".
 */
public final class OrderBrain<M extends Enum<M> & OrderMove> {
   /**
    * What the member sees: distance to its target, the target in the air / behind cover / hurting the
    * war from range / a Hulkbuster, heroes close to the member, incoming projectiles, Thanos under
    * heavy fire, a hold may land on the target, the target already held by an ally, own health 0..1.
    */
   public record Situation(boolean hasTarget, double distance, boolean airborne, boolean visible, boolean ranged, boolean hulkbuster, int heroesClose,
      int projectiles, boolean thanosThreatened, boolean controllable, boolean pinned, float health, boolean structuresNear) {
   }

   private final M[] moves;
   private final int[] cooldowns;
   private final Object[] history = new Object[5];
   private final SplittableRandom random;
   private int gap;

   public OrderBrain(Class<M> type, long seed) {
      this.moves = type.getEnumConstants();
      this.cooldowns = new int[this.moves.length];
      this.random = new SplittableRandom(seed);
   }

   public void tick() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }

      if (this.gap > 0) {
         this.gap--;
      }
   }

   public int cooldown(M move) {
      return this.cooldowns[move.ordinal()];
   }

   @SuppressWarnings("unchecked")
   @Nullable
   public M last() {
      return (M)this.history[0];
   }

   public boolean allowed(M move) {
      return this.cooldowns[move.ordinal()] <= 0 && this.history[0] != move;
   }

   public void commit(M move) {
      this.cooldowns[move.ordinal()] = move.cooldown() + move.length();
      this.gap = 6;
      System.arraycopy(this.history, 0, this.history, 1, this.history.length - 1);
      this.history[0] = move;
   }

   public double weight(M move, Situation s) {
      if (!s.hasTarget() || !this.allowed(move)) {
         return 0.0;
      }

      if (!move.throughCover() && !s.visible()) {
         return 0.0;
      }

      if (s.distance() < move.minRange() || s.distance() > move.maxRange()) {
         return 0.0;
      }

      if (s.airborne() && !move.air()) {
         return 0.0;
      }

      if (move.control() && (!s.controllable() || s.pinned())) {
         return 0.0;
      }

      double w = move.weight() * move.situational(s);
      if (w <= 0.0) {
         return 0.0;
      }

      for (int i = 1; i < this.history.length; i++) {
         if (this.history[i] == move) {
            w *= i == 1 ? 0.4 : 0.75;
         }
      }

      return w;
   }

   @Nullable
   public M choose(Situation s) {
      if (!s.hasTarget() || this.gap > 0) {
         return null;
      }

      double total = 0.0;
      double[] w = new double[this.moves.length];
      for (M m : this.moves) {
         w[m.ordinal()] = this.weight(m, s);
         total += w[m.ordinal()];
      }

      if (total <= 0.0) {
         return null;
      }

      double pick = this.random.nextDouble() * total;
      M fallback = null;
      for (M m : this.moves) {
         if (w[m.ordinal()] <= 0.0) {
            continue;
         }

         fallback = m;
         pick -= w[m.ordinal()];
         if (pick < 0.0) {
            return m;
         }
      }

      return fallback;
   }
}
