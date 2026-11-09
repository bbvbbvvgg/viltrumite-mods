package dev.baranhan.viltrumitecore.hero.combat.clash;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;

/**
 * Shared FX presets of the clashes (net ids 2301+, block reserved for session 23; never renumber). The neutral
 * layers are {@code client.clash.ClashFxRecipes}; the colour-aware spray at the point is {@code ClashClient}.
 */
public final class ClashFx {
   /** Two beams lock: flash, ring, short shake. */
   public static final FxPreset BEAM_LOCK = FxPreset.register("viltrumitecore:clash_beam_lock", 2301);
   /** The beam goes through / the draw blast: big flash, shockwave, debris, shake. */
   public static final FxPreset BEAM_BLAST = FxPreset.register("viltrumitecore:clash_beam_blast", 2302);
   /** A struggle called off: a small puff. */
   public static final FxPreset BEAM_FIZZLE = FxPreset.register("viltrumitecore:clash_beam_fizzle", 2303);

   private ClashFx() {
   }

   public static void init() {
   }
}
