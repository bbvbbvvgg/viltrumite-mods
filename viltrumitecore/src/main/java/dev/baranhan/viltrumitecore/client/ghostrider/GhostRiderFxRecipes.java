package dev.baranhan.viltrumitecore.client.ghostrider;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFx;
import java.util.List;

/**
 * Neutral layers of the Ghost Rider presets (ids in {@link GhostRiderFx}): small flashes, ground rings, dust, scorch
 * marks, screen flashes and shakes. The hellfire itself (flame fields, chain fire, the stare) is GhostRiderVfx's preset
 * listener. Flashes stay small: hellfire is orange, never a white glare. Electric layers are not used. Pure data
 * (GhostRiderFxRecipesTest).
 */
public final class GhostRiderFxRecipes {
   private GhostRiderFxRecipes() {
   }

   public static void init() {
      all().forEach(FxRecipe::register);
   }

   public static List<FxRecipe> all() {
      return List.of(transform(), lash(), hook(), slam(), wave(), verdict(), ram(), bikeForm(), dash());
   }

   /** The skull bursts into hellfire: a low ring of heat, a wide shake, scorched ground under his boots (no white flash). */
   static FxRecipe transform() {
      return FxRecipe.builder(GhostRiderFx.TRANSFORM)
         .screenFlash(0, 5, 0.14F, 18.0F)
         .shake(0, 0.9F, 24.0F)
         .shockwave(0, 16, 3.2F, 0.25F)
         .dust(0, 1.4F, 12)
         .scorch(0, 160, 1.6F, 7)
         .build();
   }

   /** A chain lash lands: a crack at the contact, a short shake. */
   static FxRecipe lash() {
      return FxRecipe.builder(GhostRiderFx.LASH)
         .flash(0, 3, 0.6F)
         .shake(0, 0.3F, 12.0F)
         .dust(0, 0.6F, 6)
         .build();
   }

   /** The chain bites a body. */
   static FxRecipe hook() {
      return FxRecipe.builder(GhostRiderFx.HOOK)
         .flash(0, 3, 0.7F)
         .shake(0, 0.4F, 14.0F)
         .build();
   }

   /** Chain slam into the ground: a ring at the radius, debris and dust, a scorch. */
   static FxRecipe slam() {
      return FxRecipe.builder(GhostRiderFx.SLAM)
         .flash(0, 5, 1.2F)
         .screenFlash(0, 3, 0.1F, 16.0F)
         .shake(0, 1.1F, 22.0F)
         .shockwave(0, 14, 3.5F, 1.0F)
         .debris(0, 8, 2.2F, 0.7F)
         .dust(1, 3.0F, 34)
         .scorch(0, 160, 2.2F, 8)
         .build();
   }

   /** Hellfire Wave: the ground heaves and scorches (the fire cone is the listener's; no ring round his own feet). */
   static FxRecipe wave() {
      return FxRecipe.builder(GhostRiderFx.WAVE)
         .shake(0, 0.8F, 26.0F)
         .scorch(0, 180, 1.8F, 7)
         .build();
   }

   /** Penance verdict: no ground layers, only a dull pulse (the streams and eye flames are the listener's). */
   static FxRecipe verdict() {
      return FxRecipe.builder(GhostRiderFx.VERDICT)
         .screenFlash(0, 6, 0.14F, 10.0F)
         .shake(0, 0.5F, 10.0F)
         .build();
   }

   /** The bike rams a body during Hell Dash. */
   static FxRecipe ram() {
      return FxRecipe.builder(GhostRiderFx.RAM)
         .flash(0, 4, 0.9F)
         .shake(0, 0.9F, 18.0F)
         .dust(0, 1.2F, 14)
         .build();
   }

   /** The Hell Cycle forms out of the fire: a ring and a scorched circle. */
   static FxRecipe bikeForm() {
      return FxRecipe.builder(GhostRiderFx.BIKE_FORM)
         .shake(0, 0.7F, 20.0F)
         .shockwave(0, 14, 2.6F, 0.6F)
         .dust(0, 2.0F, 22)
         .scorch(0, 200, 1.8F, 8)
         .build();
   }

   /** Hell Dash: a scorched push-off at the start (no ring or dust: the camera starts inside it). */
   static FxRecipe dash() {
      return FxRecipe.builder(GhostRiderFx.DASH)
         .shake(0, 0.5F, 16.0F)
         .scorch(0, 120, 1.0F, 5)
         .build();
   }
}
