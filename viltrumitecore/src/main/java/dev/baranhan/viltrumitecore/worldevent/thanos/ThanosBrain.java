package dev.baranhan.viltrumitecore.worldevent.thanos;

import java.util.SplittableRandom;

/**
 * Server-side move choice (pure, deterministic for a seed). The event feeds a {@link Situation}
 * once Thanos is free (no move running, breather over) and executes the returned move; null means
 * "stalk the target". Weights are the move's base weight multiplied by the rules below; a move
 * with weight 0 is never chosen.
 * <ul>
 * <li>hard rules ({@link #allowed}): cooldown, the stone gap between two stone moves, never the
 * same move twice in a row, never one stone in three moves in a row (combos count every stone
 * they use), at least {@link ThanosRules#teleportGap} ticks between two teleports;</li>
 * <li>gates: phase, range band (defensive moves work at any range), a target behind cover only
 * gets moves that reach it there ({@link ThanosMove#throughCover});</li>
 * <li>situation: flying targets favour moves that reach the air, a crowd close by favours area
 * moves, incoming projectiles raise the Space ward, a hero's time stop or a strong hit favour
 * defensive answers (rewind, step, illusions), Time Rewind only after real recent damage, a
 * hidden target favours the Space step to it;</li>
 * <li>follow-ups: a pull, a step or a dome make the next close strike likelier (effective
 * combinations); combos grow likelier with the phase;</li>
 * <li>variety: the move before last is damped, and so is any move used twice in the last six;</li>
 * <li>THANOS 2.0 ({@link War}, neutral by default): the Ship Call only while the Sanctuary II can
 * fire, flyers get the moves that bring them down, a hero who hurts him from range gets pulled in
 * or stepped to, heavy damage switches him to a defensive tactic for a while, a target held by an
 * ally gets the heavy follow-up, control moves avoid a hero the control ledger protects, and the
 * moves a phase unlocks are shown in it.</li>
 * </ul>
 */
public final class ThanosBrain {
   /** What the event sees this tick. Fractions are 0..1 of the max health. */
   public record Situation(ThanosPhase phase, boolean hasTarget, double distance, boolean targetAirborne, boolean targetVisible, int heroes,
      int heroesClose, int incomingProjectiles, boolean recentlyTimeStopped, boolean threatened, float healthFraction,
      float recentDamageFraction, War war) {
      /** Without the war's extras (neutral). */
      public Situation(ThanosPhase phase, boolean hasTarget, double distance, boolean targetAirborne, boolean targetVisible, int heroes,
         int heroesClose, int incomingProjectiles, boolean recentlyTimeStopped, boolean threatened, float healthFraction,
         float recentDamageFraction) {
         this(phase, hasTarget, distance, targetAirborne, targetVisible, heroes, heroesClose, incomingProjectiles, recentlyTimeStopped, threatened,
            healthFraction, recentDamageFraction, War.NONE);
      }

      public Situation withWar(War next) {
         return new Situation(this.phase, this.hasTarget, this.distance, this.targetAirborne, this.targetVisible, this.heroes, this.heroesClose,
            this.incomingProjectiles, this.recentlyTimeStopped, this.threatened, this.healthFraction, this.recentDamageFraction, next);
      }
   }

   /**
    * The war around him: {@code shipReady} the Sanctuary II hovers and may fire, {@code targetRanged}
    * the target hurts him mostly from range, {@code flyers} heroes in the air near him,
    * {@code defensive} his heavy-damage tactic runs, {@code pinned} an ally holds the target,
    * {@code alliesEngaged} allies fighting next to the target, {@code targetControllable} the control
    * ledger lets a new hold land on the target.
    */
   public record War(boolean shipReady, boolean targetRanged, int flyers, boolean defensive, boolean pinned, int alliesEngaged,
      boolean targetControllable) {
      public static final War NONE = new War(false, false, 0, false, false, 0, true);
   }

   private static final ThanosMove[] MOVES = ThanosMove.values();
   private final int[] cooldowns = new int[MOVES.length];
   /** Last six moves, [0] the most recent. */
   private final ThanosMove[] history = new ThanosMove[6];
   private final SplittableRandom random;
   private int stoneGap;
   /** Ticks since the last teleport started. */
   private int sinceTeleport = 1 << 20;

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

      if (this.sinceTeleport < 1 << 20) {
         this.sinceTeleport++;
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

   /**
    * The hard rules, whatever the situation: off cooldown and past the stone gap, not the last
    * move again, no stone in a third move in a row, no teleport within the gap of the last one.
    * Forced moves (unstuck, recovery) obey them too.
    */
   public boolean allowed(ThanosMove move, ThanosPhase phase) {
      if (!this.ready(move) || move == this.history[0]) {
         return false;
      }

      if (this.history[0] != null && this.history[1] != null && (move.stones & this.history[0].stones & this.history[1].stones) != 0) {
         return false;
      }

      return !move.teleports() || this.sinceTeleport >= ThanosRules.teleportGap(phase);
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

      if (move.teleports()) {
         this.sinceTeleport = 0;
      }

      System.arraycopy(this.history, 0, this.history, 1, this.history.length - 1);
      this.history[0] = move;
   }

   /** Weight of one move in a situation (0 = never). Public for the tests. */
   public double weight(ThanosMove move, Situation s) {
      if (move.scripted() || !this.allowed(move, s.phase()) || !s.phase().atLeast(move.minPhase)) {
         return 0.0;
      }

      War war = s.war();
      if (move == ThanosMove.SHIP_CALL && !war.shipReady()) {
         return 0.0;
      }

      boolean defensive = move.kind == ThanosMove.Kind.DEFENSE;
      if (!defensive && !move.inRange(s.distance())) {
         return 0.0;
      }

      if (!s.targetVisible() && !move.throughCover()) {
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
         boolean reaction = s.recentlyTimeStopped() || s.threatened();
         if (s.recentDamageFraction() < (reaction ? ThanosRules.REWIND_MIN_DAMAGE_REACTION : ThanosRules.REWIND_MIN_DAMAGE)) {
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

      if (!s.targetVisible() && move == ThanosMove.SPACE_STEP) {
         // behind a wall: go to him through space rather than wait
         w *= 4.0;
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
         // Someone stopped his time: rewind or step away first, everything else waits.
         w *= switch (move) {
            case TIME_REWIND -> 14.0;
            case SPACE_STEP -> 5.0;
            case TIME_DOME -> 2.0;
            default -> 0.3;
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
            case ARRIVAL, BLACK_ORDER -> 1.0;
            case INVASION -> 1.4;
            case GAUNTLET -> 1.8;
         };
         if (move == ThanosMove.COMBO_ETERNITY && s.phase() != ThanosPhase.GAUNTLET) {
            w *= 0.5;
         }

         if (move == ThanosMove.COMBO_INFINITY && s.healthFraction() < 0.15F) {
            // the last stand: everything the Gauntlet has
            w *= 2.0;
         }
      }

      w *= this.warWeight(move, s, war);
      w *= this.followUp(move);
      if (move == this.history[1]) {
         w *= 0.35;
      }

      int recent = 0;
      for (int i = 2; i < this.history.length; i++) {
         if (this.history[i] == move) {
            recent++;
         }
      }

      if (recent >= 1) {
         w *= recent >= 2 ? 0.4 : 0.7;
      }

      return Math.max(0.0, w);
   }

   /** The war's multipliers (1 for {@link War#NONE}). */
   double warWeight(ThanosMove move, Situation s, War war) {
      double w = 1.0;
      if (war == War.NONE) {
         return w;
      }

      if (war.flyers() > 0 && s.targetAirborne()) {
         // bring the flyer down: drag, snap to the ground mark, portals in the air, the ship's lance
         w *= switch (move) {
            case MIND_COMPULSION, SPACE_BARRAGE -> 2.0;
            case TIME_ANOMALY, SPACE_PULL -> 1.7;
            case SHIP_CALL, COMBO_TIME_VOLLEY -> 1.4;
            default -> 1.0;
         };
      }

      if (war.targetRanged() && s.distance() > ThanosRules.CLOSE_RANGE) {
         // the sniper: pull him in or go to him
         w *= switch (move) {
            case MIND_COMPULSION -> 2.5;
            case SPACE_PULL -> 2.0;
            case SPACE_STEP, COMBO_MIND_STEP -> 1.8;
            case SPACE_BARRAGE, COMBO_PORTAL_PUNCH -> 1.5;
            default -> 1.0;
         };
      }

      if (war.defensive()) {
         // heavy damage: guard, illusions, distance, let the army and the ship work
         if (move.kind == ThanosMove.Kind.DEFENSE) {
            w *= 3.0;
         } else if (move.kind == ThanosMove.Kind.MOBILITY) {
            w *= 2.0;
         } else if (move.kind == ThanosMove.Kind.MELEE) {
            w *= 0.6;
         }

         if (move == ThanosMove.SHIP_CALL || move == ThanosMove.TIME_ANOMALY) {
            w *= 1.5;
         }
      }

      if (war.pinned()) {
         // an ally holds the target: the heavy follow-up
         w *= switch (move) {
            case POWER_PUNCH -> 3.0;
            case HAMMER, COMBO_PORTAL_PUNCH, POWER_RUPTURE -> 2.0;
            case SPACE_PULL, MIND_COMPULSION, TIME_ANOMALY -> 0.3;
            default -> 1.0;
         };
      } else if (war.alliesEngaged() >= 2 && (move.kind == ThanosMove.Kind.RANGED || move.kind == ThanosMove.Kind.AREA)) {
         // the army is on the target: support from range
         w *= 1.3;
      }

      if (!war.targetControllable() && control(move)) {
         w *= 0.15;
      }

      if (move.war() && !move.combo() && move.minPhase == s.phase()) {
         // the phase shows what it brought
         w *= 1.4;
      }

      return w;
   }

   /** Moves that hold a hero (the control ledger decides how long). */
   public static boolean control(ThanosMove move) {
      return move == ThanosMove.SPACE_PULL || move == ThanosMove.MIND_COMPULSION || move == ThanosMove.TIME_ANOMALY || move == ThanosMove.REALITY_REWRITE
         || move == ThanosMove.COMBO_TIME_VOLLEY;
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
         case MIND_COMPULSION -> move == ThanosMove.CLEAVE || move == ThanosMove.POWER_PUNCH ? 2.5 : move == ThanosMove.HAMMER ? 2.0 : 1.0;
         case TIME_ANOMALY -> move == ThanosMove.POWER_RUPTURE || move == ThanosMove.SHIP_CALL ? 1.8 : 1.0;
         case REALITY_REWRITE -> move == ThanosMove.POWER_WAVE || move == ThanosMove.SPACE_BARRAGE ? 1.6 : 1.0;
         case SPACE_BARRAGE -> move == ThanosMove.SPACE_STEP || move == ThanosMove.COMBO_PORTAL_PUNCH ? 1.6 : 1.0;
         default -> 1.0;
      };
   }
}
