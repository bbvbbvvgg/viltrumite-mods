package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Who hurts the war most (pure): a decaying damage score per hero, with the share of it dealt from
 * range. Target choice keeps the current target for at least {@link #HOLD} ticks and only switches
 * to a hero whose score beats it clearly ({@link #SWITCH_RATIO} times plus {@link #SWITCH_MARGIN}),
 * so the attention moves to the most dangerous hero without flickering between two.
 */
public final class ThanosThreat {
   /** Half-life of a score: 5 seconds. */
   public static final int HALF_LIFE = 100;
   public static final int HOLD = 40;
   public static final double SWITCH_RATIO = 1.3;
   public static final double SWITCH_MARGIN = 2.0;
   private static final double DECAY = Math.pow(0.5, 1.0 / HALF_LIFE);
   private static final double FORGET = 0.05;
   private final Map<UUID, double[]> scores = new HashMap<>();

   /** A hit on any of the war's bodies by {@code id}. */
   public void hit(UUID id, float amount, boolean ranged) {
      if (amount <= 0.0F) {
         return;
      }

      double[] s = this.scores.computeIfAbsent(id, k -> new double[2]);
      s[0] += amount;
      if (ranged) {
         s[1] += amount;
      }
   }

   /** One tick: every score decays. */
   public void tick() {
      for (Iterator<double[]> it = this.scores.values().iterator(); it.hasNext(); ) {
         double[] s = it.next();
         s[0] *= DECAY;
         s[1] *= DECAY;
         if (s[0] < FORGET) {
            it.remove();
         }
      }
   }

   public double score(@Nullable UUID id) {
      double[] s = id == null ? null : this.scores.get(id);
      return s == null ? 0.0 : s[0];
   }

   /** Share (0..1) of the hero's score dealt from range. */
   public double rangedShare(@Nullable UUID id) {
      double[] s = id == null ? null : this.scores.get(id);
      return s == null || s[0] <= 1.0E-6 ? 0.0 : Math.min(1.0, s[1] / s[0]);
   }

   /**
    * The target among {@code candidates}: {@code current} while it is a candidate and was held for
    * less than {@link #HOLD} ticks or is not clearly beaten; else the highest score (null when no
    * candidate has one and {@code current} is gone).
    */
   @Nullable
   public UUID pick(@Nullable UUID current, Collection<UUID> candidates, int heldTicks) {
      UUID best = null;
      double bestScore = 0.0;
      for (UUID id : candidates) {
         double s = this.score(id);
         if (s > bestScore) {
            bestScore = s;
            best = id;
         }
      }

      boolean keep = current != null && candidates.contains(current);
      if (!keep) {
         return best;
      }

      if (heldTicks < HOLD || best == null || best.equals(current)) {
         return current;
      }

      return bestScore > this.score(current) * SWITCH_RATIO + SWITCH_MARGIN ? best : current;
   }

   public void clear() {
      this.scores.clear();
   }
}
