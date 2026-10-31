package dev.baranhan.viltrumitecore.worldevent.thanos;

import java.util.SplittableRandom;

/**
 * Server-side move choice (pure, deterministic for a seed). The event feeds a {@link Situation}
 * once Thanos is free (no move running, breather over) and executes the returned move; null means
 * "stalk the target". Weights are the move's base weight multiplied by the rules below; a move
 * with weight 0 is never chosen.
 * <ul>
 * <li>gates: cooldown, phase, the stone gap between two stone moves, range band (defensive moves
 * work at any range);</li>
 * <li>situation: flying targets favour moves that reach the air, a crowd close by favours area
 * moves, incoming projectiles raise the Space ward, a hero's time stop or a strong hit favour
 * defensive answers, Time Rewind only after real recent damage;</li>
 * <li>follow-ups: a pull, a step or a dome make the next close strike likelier (effective
 * combinations); combos grow likelier with the phase;</li>
 * <li>anti-repeat: never the same move twice in a row, the move before last is damped, the same
 * lead stone three times in a row is damped hard.</li>
 * </ul>
 */
public final class ThanosBrain {
   /** What the event sees this tick. Fractions are 0..1 of the max health. */
   public record Situation(ThanosPhase phase, boolean hasTarget, double distance, boolean targetAirborne, int heroes, int heroesClose,
      int incomingProjectiles, boolean recentlyTimeStopped, boolean threatened, float healthFraction, float recentDamageFraction) {
   }

   private static final ThanosMove[] MOVES = ThanosMove.values();
   private final int[] cooldowns = new int[MOVES.length];
   /** Last three moves, [0] the most recent. */
   private final ThanosMove[] history = new ThanosMove[3];
   private final SplittableRandom random;
   private int stoneGap;

   public ThanosBrain(long seed) {
      this.random = new SplittableRandom(seed);
   }

   /** One fight tick: cooldowns run down. */
   public void tick() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }

      if (this.stoneGap > 0) {
         this.stoneGap--;
      }
   }

   public int cooldown(ThanosMove move) {
      return this.cooldowns[move.ordinal()];
   }

   public boolean ready(ThanosMove move) {
      return this.cooldowns[move.ordinal()] <= 0 && (!move.usesStones() || this.stoneGap <= 0);
   }

   public ThanosMove last() {
      return this.history[0];
   }

   /** The move chosen now, or null to stalk. Does not commit it. */
   public ThanosMove choose(Situation s) {
      if (!s.hasTarget()) {
         return null;
      }

      double total = 0.0;
      double[] weights = new double[MOVES.length];
      for (ThanosMove move : MOVES) {
         double w = this.weight(move, s);
         weights[move.ordinal()] = w;
         total += w;
      }

      if (total <= 0.0) {
         return null;
      }

      double pick = this.random.nextDouble() * total;
      ThanosMove fallback = null;
      for (ThanosMove move : MOVES) {
         double w = weights[move.ordinal()];
         if (w <= 0.0) {
            continue;
         }

         fallback = move;
         pick -= w;
         if (pick < 0.0) {
            return move;
         }
      }

      return fallback;
   }

   /** The move starts: its cooldown, the stone gap and the history. */
   public void commit(ThanosMove move, ThanosPhase phase) {
      this.cooldowns[move.ordinal()] = ThanosRules.cooldown(move.cooldown, phase) + move.length(phase);
      if (move.usesStones()) {
         this.stoneGap = move.length(phase) + ThanosRules.STONE_GAP_TICKS;
      }

      this.history[2] = this.history[1];
      this.history[1] = this.history[0];
      this.history[0] = move;
   }

   /** Weight of one move in a situation (0 = never). Public for the tests. */
   public double weight(ThanosMove move, Situation s) {
      if (!this.ready(move) || !s.phase().atLeast(move.minPhase)) {
         return 0.0;
      }

      boolean defensive = move.kind == ThanosMove.Kind.DEFENSE;
      if (!defensive && !move.inRange(s.distance())) {
         return 0.0;
      }

      double w = move.weight;
      if (move == ThanosMove.SPACE_WARD) {
         if (s.incomingProjectiles() < ThanosRules.PROJECTILE_THREAT) {
            return 0.0;
         }

         w = 6.0;
      }

      if (move == ThanosMove.TIME_REWIND) {
         if (s.recentDamageFraction() < 0.03F) {
            return 0.0;
         }

         if (s.healthFraction() < 0.6F) {
            w *= 1.0 + 10.0 * s.recentDamageFraction();
         }
      }

      if (move == ThanosMove.REALITY_MIRAGE && !s.threatened() && s.heroes() <= 1) {
         w *= 0.6;
      }

      if (s.targetAirborne()) {
         w *= move.air ? 1.5 : 0.1;
      }

      if (move.kind == ThanosMove.Kind.MELEE && s.distance() <= ThanosRules.CLOSE_RANGE) {
         w *= 2.2;
      }

      if ((move.kind == ThanosMove.Kind.RANGED || move.kind == ThanosMove.Kind.MOBILITY) && s.distance() > ThanosRules.MID_RANGE) {
         w *= 1.6;
      }

      if (move.kind == ThanosMove.Kind.AREA) {
         if (s.heroesClose() >= 3) {
            w *= 2.0;
         } else if (s.heroesClose() >= 2) {
            w *= 1.4;
         }
      }

      if (s.recentlyTimeStopped()) {
         // Someone stopped his time: the Time stone answers first, everything else waits.
         w *= switch (move) {
            case TIME_REWIND -> 10.0;
            case SPACE_STEP -> 4.0;
            case TIME_DOME -> 3.0;
            default -> 0.4;
         };
      }

      if (s.threatened()) {
         if (defensive && move != ThanosMove.SPACE_WARD) {
            w *= 5.0;
         } else if (move == ThanosMove.SPACE_STEP) {
            w *= 2.5;
         }
      }

      if (move.combo()) {
         w *= switch (s.phase()) {
            case OPENING, ESCALATION -> 1.0;
            case ONSLAUGHT -> 1.4;
            case FINAL -> 1.8;
         };
         if (move == ThanosMove.COMBO_ETERNITY && s.phase() != ThanosPhase.FINAL) {
            w *= 0.5;
         }
      }

      w *= this.followUp(move);
      ThanosMove last = this.history[0];
      if (move == last) {
         return 0.0;
      }

      if (move == this.history[1]) {
         w *= 0.35;
      }

      ThanosStone lead = move.lead();
      if (lead != null && last != null && this.history[1] != null && last.lead() == lead && this.history[1].lead() == lead) {
         w *= 0.25;
      }

      return Math.max(0.0, w);
   }

   /** Bonus of a move right after the last one (effective follow-ups). */
   double followUp(ThanosMove move) {
      ThanosMove last = this.history[0];
      if (last == null) {
         return 1.0;
      }

      return switch (last) {
         case SPACE_PULL -> move == ThanosMove.POWER_PUNCH ? 3.0 : move == ThanosMove.HAMMER ? 2.0 : 1.0;
         case SPACE_STEP, COMBO_MIND_STEP -> move == ThanosMove.CLEAVE ? 2.5 : move == ThanosMove.HAMMER || move == ThanosMove.POWER_PUNCH ? 2.0 : 1.0;
         case TIME_DOME -> move == ThanosMove.POWER_WAVE || move == ThanosMove.HAMMER ? 2.0 : 1.0;
         case MIND_GAZE -> move == ThanosMove.CLEAVE || move == ThanosMove.SPACE_STEP || move == ThanosMove.COMBO_PORTAL_PUNCH ? 2.0 : 1.0;
         case REALITY_ZONE -> move == ThanosMove.SOUL_NOVA ? 1.6 : move == ThanosMove.MIND_PULSE ? 1.4 : 1.0;
         default -> 1.0;
      };
   }
}
