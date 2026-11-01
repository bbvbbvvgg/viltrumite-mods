package dev.baranhan.viltrumitecore.client.redhulk;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFx;
import java.util.List;

/**
 * Neutral layers of the Red Hulk presets (ids in {@link RedHulkFx}): flashes, ground rings, dust, debris, scorch marks,
 * screen flashes and shakes. The heat-coloured part (embers, fire rings, lava, steam, the beam) is RedHulkVfx's preset
 * listener. Scale 1 is the cold body; the server raises the scale with the heat tier. Pure data (RedHulkFxRecipesTest).
 * Electric layers (sparks, rings, crackle, light pools) are deliberately not used: his energy is heat, not lightning.
 */
public final class RedHulkFxRecipes {
   private RedHulkFxRecipes() {
   }

   public static void init() {
      all().forEach(FxRecipe::register);
   }

   public static List<FxRecipe> all() {
      return List.of(punch(), hammer(), clap(), seismic(), leapLaunch(), leapLand(), throwImpact(), blast(), fury(), vent(), ultCharge(),
         ultRelease(), landing());
   }

   /** A landed fist: a small hot flash, a puff of dust, a short shake. */
   static FxRecipe punch() {
      return FxRecipe.builder(RedHulkFx.PUNCH)
         .flash(0, 4, 0.9F)
         .shake(0, 0.35F, 12.0F)
         .dust(0, 0.8F, 8)
         .build();
   }

   /** The two-hand hammer into the ground: flash, heavy ring, debris and dust, a scorch. */
   static FxRecipe hammer() {
      return FxRecipe.builder(RedHulkFx.HAMMER)
         .flash(0, 6, 1.6F)
         .screenFlash(0, 3, 0.12F, 16.0F)
         .shake(0, 0.9F, 20.0F)
         .shockwave(0, 14, 2.6F, 1.2F)
         .debris(0, 10, 2.0F, 0.7F)
         .dust(1, 2.4F, 30)
         .scorch(0, 120, 1.2F, 5)
         .build();
   }

   /** Thermal Clap: a blinding thunderclap between the palms, a cone of shock (the fire cone is the listener's). */
   static FxRecipe clap() {
      return FxRecipe.builder(RedHulkFx.CLAP)
         .flash(0, 8, 2.4F)
         .screenFlash(0, 5, 0.35F, 20.0F)
         .shake(0, 1.2F, 24.0F)
         .shockwave(1, 12, 3.0F, 0.6F)
         .dust(1, 3.0F, 26)
         .build();
   }

   /** Seismic Slam: the ground ring at the radius, a second late ring, debris thrown out, wide scorch. */
   static FxRecipe seismic() {
      return FxRecipe.builder(RedHulkFx.SEISMIC)
         .flash(0, 8, 2.2F)
         .screenFlash(0, 4, 0.22F, 28.0F)
         .shake(0, 1.8F, 36.0F)
         .shockwave(0, 20, 6.0F, 1.6F)
         .shockwave(4, 16, 4.0F, 0.8F)
         .debris(0, 26, 5.0F, 1.0F)
         .dust(1, 5.5F, 90)
         .scorch(0, 200, 3.0F, 9)
         .build();
   }

   /** Destruction Leap take-off: the ground cracks under his feet. */
   static FxRecipe leapLaunch() {
      return FxRecipe.builder(RedHulkFx.LEAP_LAUNCH)
         .shake(0, 0.9F, 24.0F)
         .shockwave(0, 10, 2.4F, 0.9F)
         .debris(0, 12, 2.0F, 1.3F)
         .dust(0, 2.4F, 30)
         .build();
   }

   /** Destruction Leap landing: the crater. */
   static FxRecipe leapLand() {
      return FxRecipe.builder(RedHulkFx.LEAP_LAND)
         .flash(0, 10, 2.8F)
         .screenFlash(0, 6, 0.4F, 32.0F)
         .shake(0, 2.2F, 40.0F)
         .shockwave(0, 22, 5.0F, 1.8F)
         .debris(0, 30, 4.5F, 1.4F)
         .dust(1, 5.0F, 100)
         .scorch(0, 240, 2.6F, 10)
         .build();
   }

   /** A thrown body hits: dust, debris, a thump. */
   static FxRecipe throwImpact() {
      return FxRecipe.builder(RedHulkFx.THROW_IMPACT)
         .shake(0, 0.8F, 20.0F)
         .shockwave(0, 10, 2.0F, 0.7F)
         .debris(0, 10, 1.6F, 0.8F)
         .dust(0, 2.0F, 26)
         .build();
   }

   /** One Heat Blast pulse: the muzzle flash at the chest (the beam and its scorched end are the listener's). */
   static FxRecipe blast() {
      return FxRecipe.builder(RedHulkFx.BLAST)
         .flash(0, 3, 1.2F)
         .shake(0, 0.25F, 14.0F)
         .build();
   }

   /** Thermal Fury: the roar ring, a red flash, a long shake. */
   static FxRecipe fury() {
      return FxRecipe.builder(RedHulkFx.FURY)
         .flash(0, 10, 2.0F)
         .screenFlash(0, 8, 0.3F, 24.0F)
         .shake(0, 1.4F, 30.0F)
         .shockwave(0, 18, 4.5F, 0.6F)
         .dust(1, 3.5F, 40)
         .build();
   }

   /** Overheat vent: a pressure burst of steam (the steam itself is the listener's). */
   static FxRecipe vent() {
      return FxRecipe.builder(RedHulkFx.VENT)
         .flash(0, 6, 1.4F)
         .shake(0, 0.7F, 18.0F)
         .shockwave(0, 12, 2.6F, 0.4F)
         .build();
   }

   /** Critical Overheat charge: the ground begins to glow and tremble. */
   static FxRecipe ultCharge() {
      return FxRecipe.builder(RedHulkFx.ULT_CHARGE)
         .shake(0, 0.6F, 28.0F)
         .shake(20, 0.9F, 28.0F)
         .shake(40, 1.3F, 28.0F)
         .dust(0, 3.0F, 40)
         .scorch(0, 100, 2.0F, 8)
         .build();
   }

   /** Critical Overheat: a white-hot nova, rings to the radius, debris, a city-block scorch. */
   static FxRecipe ultRelease() {
      return FxRecipe.builder(RedHulkFx.ULT_RELEASE)
         .flash(0, 20, 6.0F)
         .screenFlash(0, 16, 0.85F, 64.0F)
         .shake(0, 2.8F, 64.0F)
         .shockwave(0, 30, 12.0F, 2.0F)
         .shockwave(5, 26, 8.0F, 1.4F)
         .shockwave(10, 20, 4.5F, 1.0F)
         .debris(0, 48, 9.0F, 1.6F)
         .dust(2, 10.0F, 180)
         .scorch(0, 400, 7.0F, 16)
         .build();
   }

   /** A heavy landing from a fall: dust and a thud. */
   static FxRecipe landing() {
      return FxRecipe.builder(RedHulkFx.LANDING)
         .shake(0, 0.7F, 18.0F)
         .shockwave(0, 10, 2.2F, 0.8F)
         .dust(0, 2.2F, 30)
         .build();
   }
}
