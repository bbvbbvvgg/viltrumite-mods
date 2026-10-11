package dev.baranhan.viltrumitecore.hero.ironman.mark;

import java.util.EnumMap;
import java.util.Map;

/**
 * All numbers of one mark (spec §13, plan stage 4 Task 1). Starting table,
 * tuned after the in-game check. Durability is the only extra life; plating
 * only slows its loss. Balance pass 2026-10-11 (user): armor and plating are
 * per suit, never lower than the 1.24.1 values; heavy suits keep more.
 *
 * @param durability full durability (all damage goes here while it lasts)
 * @param armor armor attribute while worn; also slows durability loss ({@link MarkDamage})
 * @param toughness armor toughness attribute while worn
 * @param knockbackRes knockback resistance attribute while worn
 * @param plating durability loss per point of an absorbed hit ({@link MarkDamage}); lower = tougher plating
 * @param flightSpeedMul flight speed factor (legacy flight, {@code HeroDefinition.flightSpeedScale})
 * @param sonicDrainMul factor on the sonic energy drain
 * @param weaponMul factor on all outgoing damage while worn
 * @param shieldMul shield strength: energy per absorbed hit is divided by it
 * @param missileMarks max missile marks
 * @param missileMul missile damage factor
 * @param unibeamCharge Unibeam charge ticks
 * @param recoilMul factor on the repulsor recoil and self push
 * @param silentFlight no thruster sound and flames (Mark 15)
 * @param stealth hidden from Homelander focus and the Iron Man scan (Mark 15)
 * @param parts parts that fly in from Veronica (legs → arms → chest → back → helmet)
 */
public record MarkSpec(
   MarkId id,
   float durability,
   double armor,
   double toughness,
   double knockbackRes,
   float plating,
   float flightSpeedMul,
   float sonicDrainMul,
   float weaponMul,
   float shieldMul,
   int missileMarks,
   float missileMul,
   int unibeamCharge,
   float recoilMul,
   boolean silentFlight,
   boolean stealth,
   int parts
) {
   private static final Map<MarkId, MarkSpec> TABLE = new EnumMap<>(MarkId.class);

   static {
      put(new MarkSpec(MarkId.MARK_7, 240.0F, 30.0, 8.0, 0.3, 0.40F, 1.0F, 1.0F, 1.0F, 1.0F, 4, 1.0F, 20, 1.0F, false, false, 9));
      put(new MarkSpec(MarkId.MARK_42, 220.0F, 30.0, 7.0, 0.3, 0.40F, 1.0F, 1.0F, 1.0F, 1.0F, 4, 1.0F, 20, 1.0F, false, false, 14));
      put(new MarkSpec(MarkId.MARK_15, 170.0F, 24.0, 5.0, 0.2, 0.52F, 1.0F, 1.0F, 1.0F, 1.0F, 4, 1.0F, 20, 1.0F, true, true, 7));
      put(new MarkSpec(MarkId.MARK_39, 200.0F, 28.0, 7.0, 0.25, 0.44F, 1.35F, 0.6F, 0.85F, 1.0F, 4, 1.0F, 20, 1.0F, false, false, 9));
      put(new MarkSpec(MarkId.MARK_17, 240.0F, 30.0, 8.0, 0.35, 0.38F, 1.0F, 1.0F, 1.0F, 1.0F, 4, 1.0F, 10, 1.0F, false, false, 9));
      put(new MarkSpec(MarkId.WAR_MACHINE_MK2, 320.0F, 30.0, 12.0, 0.6, 0.34F, 0.8F, 1.5F, 1.0F, 1.0F, 8, 1.4F, 20, 0.8F, false, false, 9));
      put(new MarkSpec(MarkId.IRON_HEART_MK3, 360.0F, 30.0, 14.0, 0.9, 0.30F, 0.8F, 1.0F, 1.0F, 1.5F, 4, 1.0F, 20, 0.2F, false, false, 9));
   }

   private static void put(MarkSpec spec) {
      TABLE.put(spec.id(), spec);
   }

   public static MarkSpec of(MarkId id) {
      return TABLE.get(id);
   }
}
