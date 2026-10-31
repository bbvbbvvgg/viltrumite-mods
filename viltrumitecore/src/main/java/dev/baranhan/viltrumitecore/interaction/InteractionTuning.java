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
}
