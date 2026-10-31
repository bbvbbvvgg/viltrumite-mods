package dev.baranhan.viltrumitecore.interaction;

/** Gameplay numbers of the contextual interactions (ticks, blocks, damage). Pure constants. */
public final class InteractionTuning {
   /** Reid vs a raised shield: how long the guard cannot be raised again (8 s). */
   public static final int SHATTER_TICKS = 160;
   /** Reid vs shield: slowness on the staggered guard (ticks, amplifier). */
   public static final int SHATTER_SLOW_TICKS = 40;
   public static final int SHATTER_SLOW_LEVEL = 1;
   /** Reid vs shield: push of the defender away from the blade. */
   public static final double SHATTER_PUSH = 1.1;
   /** Mjolnir vs shield: push of each of the two away from the contact, and its lift. */
   public static final double STORM_PUSH = 1.45;
   public static final double STORM_LIFT = 0.42;
   /** Mjolnir vs shield: radius, damage and push of the electric shockwave on everyone else. */
   public static final double STORM_RADIUS = 7.0;
   public static final float STORM_DAMAGE = 6.0F;
   public static final double STORM_WAVE_PUSH = 1.25;
   public static final int STORM_MAX_TARGETS = 24;
   /** Kinetic charge the guard gains from Mjolnir / a meteor (Captain's 0-100 scale). */
   public static final int STORM_KINETIC = 45;
   public static final int METEOR_KINETIC = 100;
   /** Meteor on a guard: push of the defender along the meteor's flight. */
   public static final double METEOR_PUSH = 1.6;
   /** A burst meteor: radius and push of its blast on the creatures around (no blocks change). */
   public static final double METEOR_BLAST_RADIUS = 6.0;
   public static final double METEOR_BLAST_PUSH = 1.1;
   /** Lightning into powered armour: energy gained (0-100 scale). */
   public static final float OVERCHARGE_ENERGY = 40.0F;
   /** Divine clash: push of each striker and hit-stop. */
   public static final double CLASH_PUSH = 1.15;
   public static final int CLASH_HIT_STOP = 5;
   /** Iron Legion vs Chitauri during an invasion: damage multiplier and FX throttle per suit. */
   public static final float LEGION_CHITAURI_FACTOR = 1.5F;
   public static final int LEGION_FX_THROTTLE = 20;
   /** Thanos vs a hero summon: launch speed and lift. */
   public static final double TITAN_FLING = 1.9;
   public static final double TITAN_LIFT = 0.65;

   private InteractionTuning() {
   }

   // ---- stage 2 ----
   /** mjolnir_rebounds_off_shield: shove of the guard and of the ring around it. */
   public static final double REBOUND_PUSH = 0.9;
   public static final double REBOUND_RING_RADIUS = 4.5;
   public static final double REBOUND_RING_PUSH = 0.9;
   /** viltrumite_fist_meets_vibranium: the striker is thrown back and stunned. */
   public static final double FIST_RECOIL_PUSH = 1.5;
   public static final int FIST_RECOIL_STUN = 24;
   /** repulsor_splits_on_vibranium: shards, cone, range, share of the bolt's damage per shard. */
   public static final int SPLIT_SHARDS = 3;
   public static final double SPLIT_CONE_COS = 0.62;
   public static final double SPLIT_RANGE = 18.0;
   public static final float SPLIT_SHARE = 0.6F;
   public static final float SPLIT_MIN_DAMAGE = 3.0F;
   /** time_stone_rewinds_ledger: share of the erased ledger Thanos gets back, push of the caster. */
   public static final float REWIND_HEAL_SHARE = 0.5F;
   public static final double REWIND_PUSH = 1.3;
   public static final double REWIND_RANGE = 24.0;
   /** reid_strains_lions_heart: heart strain a Reid stroke adds (max 1000, seizure at the top). */
   public static final int REID_STRAIN = 420;
   /** titan_breaks_guard: the bearer is thrown back. */
   public static final double CRUSH_PUSH = 1.7;
   public static final double CRUSH_LIFT = 0.35;
   /** reid_cleaves_meteor: halves fly off at this angle (degrees) and keep this share of the speed. */
   public static final double CLEAVE_ANGLE = 34.0;
   public static final double CLEAVE_SPEED = 0.85;
   /** viltrumite_hurls_meteor: thrown speed (blocks/tick, at least the incoming speed), catch recoil. */
   public static final double HURL_SPEED = 2.6;
   public static final double HURL_RECOIL = 0.8;
   /** shadow_exchange_meteor: furthest shadow the exchange reaches. */
   public static final double EXCHANGE_RANGE = 48.0;
   /** meteor_downs_leviathan: hit (capped by the Leviathan) and stagger. */
   public static final float METEOR_LEVIATHAN_DAMAGE = 120.0F;
   public static final int METEOR_LEVIATHAN_STAGGER = 80;
   /** thunder_charges_legion: discharge targets, reach, damage. */
   public static final int DISCHARGE_TARGETS = 3;
   public static final double DISCHARGE_RANGE = 10.0;
   public static final float DISCHARGE_DAMAGE = 7.0F;
   /** lightning_runs_the_leviathan: reach around every segment, damage on riders, stagger. */
   public static final double SPINE_REACH = 4.5;
   public static final float SPINE_DAMAGE = 9.0F;
   public static final int SPINE_MAX_TARGETS = 16;
   public static final int SPINE_STAGGER = 60;
   /** heavy_hitter_halts_leviathan: stagger, rider shake-off radius and push, striker recoil. */
   public static final int HALT_STAGGER = 70;
   public static final double HALT_RADIUS = 6.0;
   public static final double HALT_PUSH = 1.4;
   public static final double HALT_RECOIL = 0.9;
   public static final float HALT_MIN_DAMAGE = 4.0F;
}
