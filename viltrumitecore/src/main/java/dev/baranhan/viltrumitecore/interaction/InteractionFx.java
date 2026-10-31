package dev.baranhan.viltrumitecore.interaction;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;

/**
 * FX presets of the contextual interactions (shared {@link FxPreset} seam: no own packet, no protocol change).
 * Net ids 600-649 belong to the interactions; never reuse or renumber one. Layers: client
 * {@code client.interaction.InteractionFxRecipes}.
 */
public final class InteractionFx {
   /** Reid cuts through a raised vibranium guard: hard white flash, thrown sparks, ring, heavy shake. */
   public static final FxPreset SHIELD_SHATTER = FxPreset.register("viltrumitecore:interaction_shield_shatter", 600);
   /** Mjolnir on a raised shield: blinding flash, lightning shell, double ground ring, crawlers. */
   public static final FxPreset STORM_SHIELD = FxPreset.register("viltrumitecore:interaction_storm_shield", 601);
   /** A meteor bursts on a raised guard: fire-white flash, debris, blast ring. */
   public static final FxPreset METEOR_BURST = FxPreset.register("viltrumitecore:interaction_meteor_burst", 602);
   /** Lightning from Mjolnir splits a meteor mid-air. */
   public static final FxPreset METEOR_SPLIT = FxPreset.register("viltrumitecore:interaction_meteor_split", 603);
   /** Lightning soaked by powered armour: crackle shell, sparks, reactor glow. */
   public static final FxPreset OVERCHARGE = FxPreset.register("viltrumitecore:interaction_overcharge", 604);
   /** Reid meets Mjolnir mid-strike: flash, arcs, sparks, short ring. */
   public static final FxPreset DIVINE_CLASH = FxPreset.register("viltrumitecore:interaction_divine_clash", 605);
   /** An Iron Legion hit on a Chitauri during the invasion: targeting arc and sparks. */
   public static final FxPreset LEGION_MARK = FxPreset.register("viltrumitecore:interaction_legion_mark", 606);
   /** Thanos swats a hero summon away: dust burst, ring, shake. */
   public static final FxPreset TITAN_FLING = FxPreset.register("viltrumitecore:interaction_titan_fling", 607);

   /** Thrown Mjolnir rebounds off a raised shield: flash, crackle shell, ring, sparks. */
   public static final FxPreset STORM_REBOUND = FxPreset.register("viltrumitecore:interaction_storm_rebound", 608);
   /** A Viltrumite fist on vibranium: hard flash, shockwave ring, dust, heavy shake. */
   public static final FxPreset VIBRANIUM_RING = FxPreset.register("viltrumitecore:interaction_vibranium_ring", 609);
   /** A repulsor bolt splits on the shield: flash, sparks, a small ring. */
   public static final FxPreset REPULSOR_SPLIT = FxPreset.register("viltrumitecore:interaction_repulsor_split", 610);
   /** One split shard: an arc from the shield to its target, sparks at the end. */
   public static final FxPreset REPULSOR_SHARD = FxPreset.register("viltrumitecore:interaction_repulsor_shard", 611);
   /** The Time Stone rewinds the time-stop ledger: light pool, rings, screen flash. */
   public static final FxPreset TIME_REWIND = FxPreset.register("viltrumitecore:interaction_time_rewind", 612);
   /** Reid on the stopped body: white flash, sparks, a tight ring, shake. */
   public static final FxPreset LION_STRAIN = FxPreset.register("viltrumitecore:interaction_lion_strain", 613);
   /** Thanos crushes the guard: flash, shockwave, debris, dust, heavy shake. */
   public static final FxPreset SHIELD_CRUSH = FxPreset.register("viltrumitecore:interaction_shield_crush", 614);
   /** Reid cleaves a meteor: flash, screen flash, scorch rays, debris, shake. */
   public static final FxPreset METEOR_CLEAVE = FxPreset.register("viltrumitecore:interaction_meteor_cleave", 615);
   /** A Viltrumite catches a meteor: fire flash, shockwave, dust, shake. */
   public static final FxPreset METEOR_HURL = FxPreset.register("viltrumitecore:interaction_meteor_hurl", 616);
   /** Shadow exchange: dust and a ring where the two swap places. */
   public static final FxPreset SHADOW_EXCHANGE = FxPreset.register("viltrumitecore:interaction_shadow_exchange", 617);
   /** A meteor slams into a Leviathan: big flash, shockwave, debris, long shake. */
   public static final FxPreset LEVIATHAN_IMPACT = FxPreset.register("viltrumitecore:interaction_leviathan_impact", 618);
   /** Thunder discharged from a Legion suit: an arc to one target, crackle at the end. */
   public static final FxPreset LEGION_DISCHARGE = FxPreset.register("viltrumitecore:interaction_legion_discharge", 619);
   /** Lightning down a Leviathan: one arc between two body segments. */
   public static final FxPreset SPINE_ARC = FxPreset.register("viltrumitecore:interaction_spine_arc", 620);
   /** A heavy fist halts a Leviathan: flash, shockwave, debris, shake. */
   public static final FxPreset LEVIATHAN_HALT = FxPreset.register("viltrumitecore:interaction_leviathan_halt", 621);

   /** Every preset of the interactions, in net id order. */
   public static final java.util.List<FxPreset> PRESETS = java.util.List.of(SHIELD_SHATTER, STORM_SHIELD, METEOR_BURST, METEOR_SPLIT, OVERCHARGE,
      DIVINE_CLASH, LEGION_MARK, TITAN_FLING,
      STORM_REBOUND, VIBRANIUM_RING, REPULSOR_SPLIT, REPULSOR_SHARD, TIME_REWIND, LION_STRAIN, SHIELD_CRUSH, METEOR_CLEAVE, METEOR_HURL, SHADOW_EXCHANGE, LEVIATHAN_IMPACT, LEGION_DISCHARGE, SPINE_ARC, LEVIATHAN_HALT);

   private InteractionFx() {
   }

   /** Forces class init (presets register before the first packet on both sides). */
   public static void init() {
   }
}
