package dev.baranhan.viltrumitecore.client.interaction;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.interaction.InteractionFx;
import java.util.List;

/**
 * Layers of the contextual interaction FX presets (ids in {@link InteractionFx}). Each recipe is played once at the
 * contact point the server chose; every client in range sees the same layers on the same clock (the shared FX
 * preset packet, no own protocol). Pure data, unit tested (InteractionFxRecipesTest).
 */
public final class InteractionFxRecipes {
   private InteractionFxRecipes() {
   }

   public static void init() {
      all().forEach(FxRecipe::register);
   }

   public static List<FxRecipe> all() {
      return List.of(shieldShatter(), stormShield(), meteorBurst(), meteorSplit(), overcharge(), divineClash(), legionMark(), titanFling(), stormRebound(),
         vibraniumRing(), repulsorSplit(), repulsorShard(), timeRewind(), lionStrain(), shieldCrush(), meteorCleave(), meteorHurl(), shadowExchange(),
         leviathanImpact(), legionDischarge(), spineArc(), leviathanHalt());
   }

   /** Reid tears the guard aside: a hard white flash, the shield's sparks thrown back, a tight ring, a heavy shake. */
   static FxRecipe shieldShatter() {
      return FxRecipe.builder(InteractionFx.SHIELD_SHATTER)
         .flash(0, 8, 2.6F)
         .screenFlash(0, 5, 0.35F, 24.0F)
         .shake(0, 1.1F, 24.0F)
         .sparks(0, 26, 70, 0.6F)
         .ring(1, 14, 2.4F, 6)
         .shockwave(1, 18, 3.5F, 0.8F)
         .debris(1, 10, 2.5F, 0.6F)
         .sparks(4, 30, 30, 0.3F)
         .build();
   }

   /** Mjolnir on vibranium: the iconic shockwave — blinding flash, a lightning shell, two ground rings, crawlers. */
   static FxRecipe stormShield() {
      return FxRecipe.builder(InteractionFx.STORM_SHIELD)
         .flash(0, 16, 4.5F)
         .screenFlash(0, 10, 0.7F, 64.0F)
         .skyFlash(0, 14, 30.0F, 18.0F)
         .lightPool(0, 30, 9.0F, 1.4F)
         .shake(0, 1.8F, 48.0F)
         .crackle(0, 30, 1.6F, 10)
         .sparks(0, 34, 90, 0.7F)
         .shockwave(1, 26, 7.0F, 1.4F)
         .ring(1, 20, 7.0F, 12)
         .crawl(2, 24, 6.5F, 10)
         .dust(2, 5.0F, 80)
         .ring(6, 18, 4.5F, 8)
         .crackle(10, 40, 1.1F, 6)
         .build();
   }

   /** A meteor dies on a raised guard: fire-white flash, debris, blast ring and dust, long shake. */
   static FxRecipe meteorBurst() {
      return FxRecipe.builder(InteractionFx.METEOR_BURST)
         .flash(0, 18, 5.0F)
         .screenFlash(0, 10, 0.6F, 64.0F)
         .lightPool(0, 26, 8.0F, 1.2F)
         .shake(0, 2.0F, 64.0F)
         .shockwave(0, 30, 6.0F, 1.6F)
         .debris(0, 24, 5.0F, 1.0F)
         .dust(1, 6.0F, 110)
         .sparks(1, 36, 80, 0.8F)
         .build();
   }

   /** Lightning from Mjolnir splits a falling meteor: a sky bolt to the rock, a burst, shards and dust. */
   static FxRecipe meteorSplit() {
      return FxRecipe.builder(InteractionFx.METEOR_SPLIT)
         .bolt(0, 30, 36.0F, 1.2F, 5, 0.0F)
         .skyFlash(0, 16, 40.0F, 22.0F)
         .flash(1, 16, 4.0F)
         .screenFlash(1, 8, 0.5F, 64.0F)
         .shake(1, 1.6F, 64.0F)
         .shockwave(2, 26, 6.0F, 1.2F)
         .debris(2, 20, 5.0F, 0.9F)
         .sparks(2, 34, 90, 0.8F)
         .crackle(3, 30, 1.4F, 8)
         .build();
   }

   /** Lightning soaked by powered armour: a crackle shell, sparks, a reactor glow. */
   static FxRecipe overcharge() {
      return FxRecipe.builder(InteractionFx.OVERCHARGE)
         .crackle(0, 34, 1.1F, 9)
         .flash(0, 10, 1.6F)
         .lightPool(0, 24, 3.5F, 1.0F)
         .sparks(0, 24, 40, 0.35F)
         .crackle(12, 26, 0.8F, 5)
         .build();
   }

   /** Reid meets Mjolnir mid-strike: flash, sparks, a short ring and a shake. */
   static FxRecipe divineClash() {
      return FxRecipe.builder(InteractionFx.DIVINE_CLASH)
         .flash(0, 12, 3.2F)
         .screenFlash(0, 6, 0.4F, 32.0F)
         .shake(0, 1.3F, 32.0F)
         .sparks(0, 30, 80, 0.7F)
         .ring(1, 16, 3.5F, 8)
         .shockwave(1, 20, 4.0F, 0.9F)
         .crackle(2, 24, 1.0F, 6)
         .build();
   }

   /** An Iron Legion hit on a Chitauri during the invasion: a small targeting arc and sparks. */
   static FxRecipe legionMark() {
      return FxRecipe.builder(InteractionFx.LEGION_MARK)
         .flash(0, 6, 0.9F)
         .sparks(0, 16, 18, 0.3F)
         .crackle(0, 14, 0.6F, 3)
         .build();
   }

   /** Thanos swats a hero summon away: dust burst, ring, shake. */
   static FxRecipe titanFling() {
      return FxRecipe.builder(InteractionFx.TITAN_FLING)
         .shake(0, 1.0F, 24.0F)
         .shockwave(0, 18, 3.5F, 1.0F)
         .dust(0, 3.0F, 50)
         .debris(0, 8, 2.5F, 0.6F)
         .build();
   }

   /** Thrown Mjolnir rebounds off a raised shield: flash, a crackle shell, a ring, sparks thrown back. */
   static FxRecipe stormRebound() {
      return FxRecipe.builder(InteractionFx.STORM_REBOUND)
         .flash(0, 12, 3.4F)
         .screenFlash(0, 6, 0.45F, 40.0F)
         .shake(0, 1.3F, 36.0F)
         .crackle(0, 24, 1.3F, 8)
         .sparks(0, 28, 70, 0.6F)
         .ring(1, 16, 4.5F, 10)
         .shockwave(1, 20, 4.5F, 0.9F)
         .build();
   }

   /** A Viltrumite fist on vibranium: a hard flash, a shockwave ring, dust and a heavy shake. */
   static FxRecipe vibraniumRing() {
      return FxRecipe.builder(InteractionFx.VIBRANIUM_RING)
         .flash(0, 8, 2.8F)
         .shake(0, 1.5F, 32.0F)
         .shockwave(0, 20, 4.0F, 1.2F)
         .sparks(0, 22, 60, 0.55F)
         .dust(1, 3.5F, 60)
         .build();
   }

   /** A repulsor bolt splits on the shield: flash, sparks, a small ring. */
   static FxRecipe repulsorSplit() {
      return FxRecipe.builder(InteractionFx.REPULSOR_SPLIT)
         .flash(0, 8, 1.8F)
         .lightPool(0, 14, 3.0F, 1.0F)
         .sparks(0, 18, 40, 0.5F)
         .ring(0, 12, 2.0F, 6)
         .build();
   }

   /** One split shard: an arc from the shield to its target, sparks at the end. */
   static FxRecipe repulsorShard() {
      return FxRecipe.builder(InteractionFx.REPULSOR_SHARD)
         .arc(0, 8, 0.25F, 2)
         .sparks(1, 12, 16, 0.3F)
         .build();
   }

   /** The Time Stone rewinds the time-stop ledger: a light pool, two rings, a screen flash. */
   static FxRecipe timeRewind() {
      return FxRecipe.builder(InteractionFx.TIME_REWIND)
         .lightPool(0, 34, 6.0F, 1.3F)
         .screenFlash(0, 10, 0.4F, 32.0F)
         .ring(0, 24, 5.0F, 10)
         .ring(8, 20, 3.0F, 8)
         .flash(10, 10, 2.2F)
         .build();
   }

   /** Reid on the stopped body: a white flash, sparks, a tight ring, a shake. */
   static FxRecipe lionStrain() {
      return FxRecipe.builder(InteractionFx.LION_STRAIN)
         .flash(0, 8, 2.4F)
         .screenFlash(0, 4, 0.3F, 20.0F)
         .shake(0, 1.0F, 20.0F)
         .sparks(0, 24, 60, 0.6F)
         .ring(1, 12, 2.2F, 6)
         .build();
   }

   /** Thanos crushes the guard: flash, shockwave, debris, dust and a heavy shake. */
   static FxRecipe shieldCrush() {
      return FxRecipe.builder(InteractionFx.SHIELD_CRUSH)
         .flash(0, 10, 3.0F)
         .shake(0, 1.8F, 40.0F)
         .shockwave(0, 24, 5.0F, 1.5F)
         .debris(0, 14, 3.5F, 0.8F)
         .dust(1, 4.0F, 80)
         .sparks(0, 26, 60, 0.6F)
         .build();
   }

   /** Reid cleaves a meteor: flash, screen flash, scorch rays, debris and a shake. */
   static FxRecipe meteorCleave() {
      return FxRecipe.builder(InteractionFx.METEOR_CLEAVE)
         .flash(0, 14, 4.5F)
         .screenFlash(0, 8, 0.55F, 64.0F)
         .shake(0, 1.6F, 64.0F)
         .scorch(0, 30, 4.0F, 10)
         .sparks(0, 30, 90, 0.8F)
         .debris(1, 18, 4.0F, 0.9F)
         .ring(1, 18, 5.0F, 10)
         .build();
   }

   /** A Viltrumite catches a meteor: a fire flash, a shockwave, dust, a shake. */
   static FxRecipe meteorHurl() {
      return FxRecipe.builder(InteractionFx.METEOR_HURL)
         .flash(0, 14, 4.0F)
         .screenFlash(0, 8, 0.45F, 48.0F)
         .lightPool(0, 20, 6.0F, 1.2F)
         .shake(0, 1.7F, 56.0F)
         .shockwave(0, 24, 5.0F, 1.3F)
         .dust(1, 5.0F, 90)
         .debris(1, 12, 3.5F, 0.8F)
         .build();
   }

   /** Shadow exchange: dust and a ring where the two swap places. */
   static FxRecipe shadowExchange() {
      return FxRecipe.builder(InteractionFx.SHADOW_EXCHANGE)
         .dust(0, 2.0F, 50)
         .ring(0, 14, 2.5F, 8)
         .flash(0, 6, 1.2F)
         .build();
   }

   /** A meteor slams into a Leviathan: a big flash, shockwave, debris and a long shake. */
   static FxRecipe leviathanImpact() {
      return FxRecipe.builder(InteractionFx.LEVIATHAN_IMPACT)
         .flash(0, 20, 6.0F)
         .screenFlash(0, 12, 0.6F, 96.0F)
         .skyFlash(0, 14, 30.0F, 24.0F)
         .shake(0, 2.2F, 96.0F)
         .shockwave(0, 32, 8.0F, 1.8F)
         .debris(0, 26, 6.0F, 1.0F)
         .sparks(1, 40, 100, 0.9F)
         .dust(2, 7.0F, 120)
         .build();
   }

   /** Thunder discharged from a Legion suit: an arc to one target, crackle at the end. */
   static FxRecipe legionDischarge() {
      return FxRecipe.builder(InteractionFx.LEGION_DISCHARGE)
         .arc(0, 10, 0.35F, 3)
         .crackle(1, 16, 0.8F, 5)
         .sparks(1, 14, 20, 0.35F)
         .build();
   }

   /** Lightning down a Leviathan: one arc between two body segments, crackle on the plates. */
   static FxRecipe spineArc() {
      return FxRecipe.builder(InteractionFx.SPINE_ARC)
         .arc(0, 14, 0.5F, 4)
         .crackle(0, 22, 2.2F, 6)
         .sparks(2, 18, 24, 0.5F)
         .build();
   }

   /** A heavy fist halts a Leviathan: a flash, shockwave, debris and a shake. */
   static FxRecipe leviathanHalt() {
      return FxRecipe.builder(InteractionFx.LEVIATHAN_HALT)
         .flash(0, 12, 3.5F)
         .shake(0, 1.9F, 64.0F)
         .shockwave(0, 26, 6.0F, 1.6F)
         .debris(0, 18, 4.5F, 0.9F)
         .sparks(0, 26, 70, 0.7F)
         .build();
   }
}
