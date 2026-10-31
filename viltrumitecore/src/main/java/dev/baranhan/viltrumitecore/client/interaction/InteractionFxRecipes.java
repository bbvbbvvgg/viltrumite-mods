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
      return List.of(shieldShatter(), stormShield(), meteorBurst(), meteorSplit(), overcharge(), divineClash(), legionMark(), titanFling());
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
}
