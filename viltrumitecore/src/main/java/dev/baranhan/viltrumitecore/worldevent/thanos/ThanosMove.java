package dev.baranhan.viltrumitecore.worldevent.thanos;

import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.ESCALATION;
import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.ONSLAUGHT;
import static dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase.OPENING;

/**
 * Thanos's moves (pure data). Ticks: {@code telegraph} (readable wind-up: pose, stone colour,
 * sound), {@code active} (the strike or the channel), {@code recovery} (open to punishment). The
 * ordinal is the network id of the move (append only). Damage is the base for one hero hit,
 * before {@link ThanosRules#damageScale}.
 */
public enum ThanosMove {
   //                 stones                                   tel act rec   cd   phase       min   max  kind            weight dmg   air
   CLEAVE(0, 12, 6, 14, 36, OPENING, 0, 5.5, Kind.MELEE, 1.4F, 16, false),
   HAMMER(0, 16, 6, 18, 70, OPENING, 0, 6.0, Kind.MELEE, 1.0F, 22, false),
   POWER_PUNCH(bits(ThanosStone.POWER), 20, 8, 20, 150, OPENING, 0, 9.0, Kind.MELEE, 1.0F, 34, false),
   POWER_WAVE(bits(ThanosStone.POWER), 24, 30, 16, 220, OPENING, 0, 18.0, Kind.AREA, 0.9F, 20, false),
   SPACE_STEP(bits(ThanosStone.SPACE), 14, 4, 8, 140, OPENING, 7.0, 64.0, Kind.MOBILITY, 1.1F, 0, true),
   SPACE_PULL(bits(ThanosStone.SPACE), 18, 16, 12, 230, OPENING, 6.0, 40.0, Kind.RANGED, 0.9F, 8, true),
   SPACE_WARD(bits(ThanosStone.SPACE), 6, 30, 6, 200, OPENING, 0, 64.0, Kind.DEFENSE, 0.2F, 0, true),
   REALITY_ZONE(bits(ThanosStone.REALITY), 20, 10, 14, 260, OPENING, 0, 30.0, Kind.AREA, 0.8F, 3, false),
   REALITY_MIRAGE(bits(ThanosStone.REALITY), 16, 50, 12, 320, ESCALATION, 0, 24.0, Kind.DEFENSE, 0.6F, 18, false),
   TIME_DOME(bits(ThanosStone.TIME), 22, 10, 12, 300, OPENING, 0, 14.0, Kind.AREA, 0.8F, 0, true),
   TIME_REWIND(bits(ThanosStone.TIME), 10, 20, 10, 400, ESCALATION, 0, 64.0, Kind.DEFENSE, 0.15F, 0, true),
   MIND_GAZE(bits(ThanosStone.MIND), 24, 6, 14, 280, OPENING, 0, 28.0, Kind.RANGED, 0.8F, 6, true),
   MIND_PULSE(bits(ThanosStone.MIND), 18, 40, 12, 240, OPENING, 0, 22.0, Kind.AREA, 0.8F, 12, false),
   SOUL_TETHER(bits(ThanosStone.SOUL), 18, 80, 14, 320, OPENING, 0, 20.0, Kind.RANGED, 0.7F, 2, true),
   SOUL_NOVA(bits(ThanosStone.SOUL), 30, 8, 20, 300, ESCALATION, 0, 10.0, Kind.AREA, 0.8F, 24, true),
   COMBO_PORTAL_PUNCH(bits(ThanosStone.SPACE, ThanosStone.POWER), 22, 14, 22, 360, ESCALATION, 4.0, 48.0, Kind.RANGED, 0.9F, 38, true),
   COMBO_TIME_CRUSH(bits(ThanosStone.TIME, ThanosStone.POWER), 26, 30, 20, 420, ESCALATION, 0, 14.0, Kind.AREA, 0.8F, 30, false),
   COMBO_SOUL_MIRAGE(bits(ThanosStone.REALITY, ThanosStone.SOUL), 22, 70, 16, 460, ONSLAUGHT, 0, 22.0, Kind.RANGED, 0.7F, 3, true),
   COMBO_MIND_STEP(bits(ThanosStone.MIND, ThanosStone.SPACE), 24, 16, 14, 380, ESCALATION, 4.0, 40.0, Kind.MOBILITY, 0.8F, 26, true),
   COMBO_TRINITY(bits(ThanosStone.POWER, ThanosStone.SPACE, ThanosStone.REALITY), 34, 50, 24, 600, ONSLAUGHT, 0, 30.0, Kind.AREA, 0.7F, 28, true),
   COMBO_ETERNITY(bits(ThanosStone.TIME, ThanosStone.MIND, ThanosStone.SOUL), 36, 60, 24, 640, ONSLAUGHT, 0, 18.0, Kind.AREA, 0.6F, 26, true);

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
         case COMBO_TRINITY -> ThanosStone.POWER;
         default -> ThanosStone.first(this.stones);
      };
   }

   /** Total busy ticks (telegraph in the given phase + active + recovery). */
   public int length(ThanosPhase phase) {
      return ThanosRules.telegraph(this.telegraph, phase) + this.active + this.recovery;
   }

   public boolean inRange(double distance) {
      return distance >= this.minRange && distance <= this.maxRange;
   }

   public static ThanosMove byId(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : null;
   }
}
