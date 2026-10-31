package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkSpec;
import java.util.EnumMap;
import java.util.Map;

/**
 * How a mark fights in the Legion (design §5 table). Movement numbers are
 * blocks/tick and blocks/tick²; weapons are periods in ticks.
 *
 * @param role preferred role; the coordinator may change it to spread the squad
 * @param range preferred combat distance to the target
 * @param altitude preferred height above the target
 * @param speed cruise speed
 * @param accel acceleration and braking limit
 * @param repulsorPeriod ticks between repulsor shots (0 = none)
 * @param missiles missiles per volley (0 = none)
 * @param missilePeriod ticks between volleys
 * @param weapon mark signature
 * @param weaponPeriod ticks between signature uses
 * @param dodge chance 0..1 to side-step an incoming projectile
 * @param leash max distance from Tony while guarding (Iron Heart stays close)
 * @param dps rough damage per second the coordinator plans with
 */
public record LegionProfile(
   MarkId mark,
   LegionRole role,
   double range,
   double altitude,
   double speed,
   double accel,
   int repulsorPeriod,
   int missiles,
   int missilePeriod,
   LegionWeapon weapon,
   int weaponPeriod,
   float dodge,
   double leash,
   float dps
) {
   private static final Map<MarkId, LegionProfile> TABLE = new EnumMap<>(MarkId.class);

   static {
      put(new LegionProfile(MarkId.MARK_7, LegionRole.VANGUARD, 9.0, 2.0, 0.85, 0.075, 12, 3, 120, LegionWeapon.MICRO_LASER, 90, 0.35F, 20.0, 7.0F));
      put(new LegionProfile(MarkId.MARK_42, LegionRole.FLANKER, 7.0, 1.0, 0.95, 0.11, 12, 0, 0, LegionWeapon.ROCKET_FIST, 70, 0.75F, 20.0, 6.0F));
      put(new LegionProfile(MarkId.MARK_15, LegionRole.FLANKER, 10.0, 3.0, 1.0, 0.1, 14, 0, 0, LegionWeapon.CAMO, 240, 0.5F, 22.0, 4.5F));
      put(new LegionProfile(MarkId.MARK_39, LegionRole.OVERWATCH, 13.0, 8.0, 1.25, 0.1, 12, 0, 0, LegionWeapon.STARBOOST, 80, 0.45F, 24.0, 5.0F));
      put(new LegionProfile(MarkId.MARK_17, LegionRole.OVERWATCH, 15.0, 5.0, 0.8, 0.065, 16, 0, 0, LegionWeapon.PULSE_UNIBEAM, 150, 0.25F, 22.0, 6.5F));
      put(new LegionProfile(MarkId.WAR_MACHINE_MK2, LegionRole.OVERWATCH, 14.0, 6.0, 0.75, 0.06, 18, 4, 100, LegionWeapon.SHOULDER_GUN, 70, 0.2F, 22.0, 9.0F));
      put(new LegionProfile(MarkId.IRON_HEART_MK3, LegionRole.GUARDIAN, 4.0, 0.0, 0.75, 0.06, 16, 0, 0, LegionWeapon.SLAM, 170, 0.15F, 7.0, 5.5F));
   }

   private static void put(LegionProfile profile) {
      TABLE.put(profile.mark(), profile);
   }

   public static LegionProfile of(MarkId mark) {
      return TABLE.get(mark);
   }

   /** Cruise speed with the mark's own flight factor (Mark 39 is the fastest, War Machine the slowest). */
   public double cruise() {
      return this.speed * Math.max(0.6, Math.min(1.5, MarkSpec.of(this.mark).flightSpeedMul()));
   }

   /** Stealth: no glow, no thruster flames (Mark 15, MarkSpec.silentFlight). */
   public boolean silent() {
      return MarkSpec.of(this.mark).silentFlight();
   }
}
