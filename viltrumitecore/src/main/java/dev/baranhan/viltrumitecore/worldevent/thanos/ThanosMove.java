package dev.baranhan.viltrumitecore.worldevent.thanos;

import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.ARRIVAL;
import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.BLACK_ORDER;
import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.GAUNTLET;
import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.INVASION;

/**
 * Thanos's moves (pure data). Ticks: {@code telegraph} (readable wind-up: pose, stone colour,
 * sound), {@code active} (the strike or the channel), {@code recovery} (open to punishment). The
 * ordinal is the network id of the move (append only). Damage is the base for one hero hit,
 * before {@link ThanosRules#damageScale}.
 */
public enum ThanosMove {
   //                 stones                                   tel act rec   cd   phase       min   max  kind            weight dmg   air
   CLEAVE(0, 12, 6, 14, 36, ARRIVAL, 0, 5.5, Kind.MELEE, 1.4F, 16, false),
   HAMMER(0, 16, 6, 18, 70, ARRIVAL, 0, 6.0, Kind.MELEE, 1.0F, 22, false),
   POWER_PUNCH(bits(ThanosStone.POWER), 20, 8, 20, 150, ARRIVAL, 0, 9.0, Kind.MELEE, 1.0F, 34, false),
   POWER_WAVE(bits(ThanosStone.POWER), 24, 30, 16, 220, ARRIVAL, 0, 18.0, Kind.AREA, 0.9F, 20, false),
   SPACE_STEP(bits(ThanosStone.SPACE), 14, 4, 8, 140, ARRIVAL, 7.0, 64.0, Kind.MOBILITY, 1.1F, 0, true),
   SPACE_PULL(bits(ThanosStone.SPACE), 18, 16, 12, 230, ARRIVAL, 6.0, 40.0, Kind.RANGED, 0.9F, 8, true),
   SPACE_WARD(bits(ThanosStone.SPACE), 6, 30, 6, 200, ARRIVAL, 0, 64.0, Kind.DEFENSE, 0.2F, 0, true),
   REALITY_ZONE(bits(ThanosStone.REALITY), 20, 10, 14, 260, ARRIVAL, 0, 30.0, Kind.AREA, 0.8F, 3, false),
   REALITY_MIRAGE(bits(ThanosStone.REALITY), 16, 50, 12, 320, BLACK_ORDER, 0, 24.0, Kind.DEFENSE, 0.6F, 18, false),
   TIME_DOME(bits(ThanosStone.TIME), 22, 10, 12, 300, ARRIVAL, 0, 14.0, Kind.AREA, 0.8F, 0, true),
   TIME_REWIND(bits(ThanosStone.TIME), 10, 20, 10, 400, BLACK_ORDER, 0, 64.0, Kind.DEFENSE, 0.15F, 0, true),
   MIND_GAZE(bits(ThanosStone.MIND), 24, 6, 14, 280, ARRIVAL, 0, 28.0, Kind.RANGED, 0.8F, 6, true),
   MIND_PULSE(bits(ThanosStone.MIND), 18, 40, 12, 240, ARRIVAL, 0, 22.0, Kind.AREA, 0.8F, 12, false),
   SOUL_TETHER(bits(ThanosStone.SOUL), 18, 80, 14, 320, ARRIVAL, 0, 20.0, Kind.RANGED, 0.7F, 2, true),
   SOUL_NOVA(bits(ThanosStone.SOUL), 30, 8, 20, 300, BLACK_ORDER, 0, 10.0, Kind.AREA, 0.8F, 24, true),
   COMBO_PORTAL_PUNCH(bits(ThanosStone.SPACE, ThanosStone.POWER), 22, 14, 22, 360, BLACK_ORDER, 4.0, 48.0, Kind.RANGED, 0.9F, 38, true),
   COMBO_TIME_CRUSH(bits(ThanosStone.TIME, ThanosStone.POWER), 26, 30, 20, 420, BLACK_ORDER, 0, 14.0, Kind.AREA, 0.8F, 30, false),
   COMBO_SOUL_MIRAGE(bits(ThanosStone.REALITY, ThanosStone.SOUL), 22, 70, 16, 460, INVASION, 0, 22.0, Kind.RANGED, 0.7F, 3, true),
   COMBO_MIND_STEP(bits(ThanosStone.MIND, ThanosStone.SPACE), 24, 16, 14, 380, BLACK_ORDER, 4.0, 40.0, Kind.MOBILITY, 0.8F, 26, true),
   COMBO_TRINITY(bits(ThanosStone.POWER, ThanosStone.SPACE, ThanosStone.REALITY), 34, 50, 24, 600, INVASION, 0, 30.0, Kind.AREA, 0.7F, 28, true),
   COMBO_ETERNITY(bits(ThanosStone.TIME, ThanosStone.MIND, ThanosStone.SOUL), 36, 60, 24, 640, INVASION, 0, 18.0, Kind.AREA, 0.6F, 26, true),
   // ---- THANOS 2.0 (append only: the ordinal is the network id) ----
   /** He points at the target: the Sanctuary II fires a lance there after its own warning ring. */
   SHIP_CALL(0, 16, 10, 10, 380, BLACK_ORDER, 0, 96.0, Kind.RANGED, 0.9F, 26, true),
   /** A line of violet eruptions runs along the ground from him to the target. */
   POWER_RUPTURE(bits(ThanosStone.POWER), 22, 24, 16, 300, ARRIVAL, 3.0, 26.0, Kind.AREA, 0.9F, 22, false),
   /** Four small portals around the target (also in the air) fire shards in turn. */
   SPACE_BARRAGE(bits(ThanosStone.SPACE), 20, 30, 14, 340, BLACK_ORDER, 6.0, 48.0, Kind.RANGED, 0.9F, 9, true),
   /** A red sphere at the target lifts everything, then slams it to the ground. */
   REALITY_REWRITE(bits(ThanosStone.REALITY), 24, 26, 14, 320, BLACK_ORDER, 0, 32.0, Kind.AREA, 0.8F, 20, true),
   /** A green mark at the target's feet: 2.5 s later the target is snapped back to it. */
   TIME_ANOMALY(bits(ThanosStone.TIME), 14, 60, 10, 380, BLACK_ORDER, 0, 40.0, Kind.RANGED, 0.7F, 14, true),
   /** A yellow thread drags the target towards him (anti-ranged), ending in his reach. */
   MIND_COMPULSION(bits(ThanosStone.MIND), 20, 40, 12, 360, BLACK_ORDER, 8.0, 40.0, Kind.RANGED, 0.8F, 0, true),
   /** Soul phantoms rise and home in on the heroes; each hit heals him a little. */
   SOUL_HARVEST(bits(ThanosStone.SOUL), 26, 70, 14, 420, INVASION, 0, 36.0, Kind.AREA, 0.7F, 10, true),
   /** Time stops around the targets, six portals open in a ring, a charged volley fires as time resumes. */
   COMBO_TIME_VOLLEY(bits(ThanosStone.TIME, ThanosStone.SPACE, ThanosStone.POWER), 30, 60, 24, 700, GAUNTLET, 0, 36.0, Kind.RANGED, 0.9F, 14, true),
   /** A portal in the sky drops rocks on warned spots near the heroes. */
   COMBO_MOON_FALL(bits(ThanosStone.POWER, ThanosStone.SPACE), 34, 80, 24, 760, GAUNTLET, 0, 60.0, Kind.AREA, 0.8F, 24, false),
   /** All six stones in six stages (pull, slow, fracture, daze, drain, blast); heavy damage cancels it. */
   COMBO_INFINITY(bits(ThanosStone.values()), 40, 90, 30, 1200, GAUNTLET, 0, 40.0, Kind.AREA, 0.6F, 36, true),
   /** The phase beat (never chosen by the brain): deploy beams, the ship's horn, the full Gauntlet. */
   COMMAND(0, 20, 30, 10, 0, ARRIVAL, 0, 999.0, Kind.DEFENSE, 0.0F, 0, true);

   public enum Kind {
      MELEE,
      RANGED,
      AREA,
      MOBILITY,
      DEFENSE
   }

   private static final ThanosMove[] VALUES = values();
   /** Stones used (mask of {@link ThanosStone#bit()}); 0 for plain melee. */
   public final int stones;
   public final int telegraph;
   public final int active;
   public final int recovery;
   public final int cooldown;
   public final ThanosPhase minPhase;
   public final double minRange;
   public final double maxRange;
   public final Kind kind;
   public final float weight;
   public final float damage;
   /** Reaches a flying target. */
   public final boolean air;

   ThanosMove(int stones, int telegraph, int active, int recovery, int cooldown, ThanosPhase minPhase, double minRange, double maxRange, Kind kind,
      float weight, float damage, boolean air) {
      this.stones = stones;
      this.telegraph = telegraph;
      this.active = active;
      this.recovery = recovery;
      this.cooldown = cooldown;
      this.minPhase = minPhase;
      this.minRange = minRange;
      this.maxRange = maxRange;
      this.kind = kind;
      this.weight = weight;
      this.damage = damage;
      this.air = air;
   }

   private static int bits(ThanosStone... stones) {
      int mask = 0;
      for (ThanosStone stone : stones) {
         mask |= stone.bit();
      }

      return mask;
   }

   public boolean combo() {
      return ThanosStone.count(this.stones) >= 2;
   }

   public boolean usesStones() {
      return this.stones != 0;
   }

   public boolean uses(ThanosStone stone) {
      return stone.in(this.stones);
   }

   /** Main stone (the one whose colour leads the telegraph), null for melee. */
   public ThanosStone lead() {
      return switch (this) {
         case COMBO_PORTAL_PUNCH -> ThanosStone.SPACE;
         case COMBO_TIME_CRUSH, COMBO_ETERNITY -> ThanosStone.TIME;
         case COMBO_SOUL_MIRAGE -> ThanosStone.REALITY;
         case COMBO_MIND_STEP -> ThanosStone.MIND;
         case COMBO_TRINITY, COMBO_INFINITY -> ThanosStone.POWER;
         case COMBO_TIME_VOLLEY -> ThanosStone.TIME;
         case COMBO_MOON_FALL -> ThanosStone.SPACE;
         default -> ThanosStone.first(this.stones);
      };
   }

   /** Total busy ticks (telegraph in the given phase + active + recovery). */
   public int length(ThanosPhase phase) {
      return ThanosRules.telegraph(this.telegraph, phase) + this.active + this.recovery;
   }

   /** Carries Thanos himself through space (the rules keep a gap between two of these). */
   public boolean teleports() {
      return this == SPACE_STEP || this == COMBO_MIND_STEP;
   }

   /**
    * Usable on a target he cannot see: a portal or a step through space, reality warped where the
    * target stands, a psychic wave through matter, or no attack at all. Everything else needs a
    * clear line (no attacks through walls).
    */
   public boolean throughCover() {
      return this.kind == Kind.DEFENSE || this == SPACE_STEP || this == COMBO_PORTAL_PUNCH || this == REALITY_ZONE || this == MIND_PULSE
         || this == SHIP_CALL || this == TIME_ANOMALY || this == SPACE_BARRAGE || this == COMBO_MOON_FALL || this == COMBO_INFINITY;
   }

   /** Added by THANOS 2.0 (its own clip file, sounds and effects). */
   public boolean war() {
      return this.ordinal() >= SHIP_CALL.ordinal();
   }

   /** Never chosen by the brain (forced by the event). */
   public boolean scripted() {
      return this == COMMAND;
   }

   public boolean inRange(double distance) {
      return distance >= this.minRange && distance <= this.maxRange;
   }

   public static ThanosMove byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }
}
