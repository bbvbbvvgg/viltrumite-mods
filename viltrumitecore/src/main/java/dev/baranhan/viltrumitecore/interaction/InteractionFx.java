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

   /** Every preset of the interactions, in net id order. */
   public static final java.util.List<FxPreset> PRESETS = java.util.List.of(SHIELD_SHATTER, STORM_SHIELD, METEOR_BURST, METEOR_SPLIT, OVERCHARGE,
      DIVINE_CLASH, LEGION_MARK, TITAN_FLING);

   private InteractionFx() {
   }

   /** Forces class init (presets register before the first packet on both sides). */
   public static void init() {
   }
}
