package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.hero.thor.ThorFx;
import java.util.List;

/**
 * Layers of Thor's FX presets (ids in {@link ThorFx}, rework spec §7).
 * Scale 1 is sized for the 3.2-block Thor: a sky bolt has a 0.28-block
 * white core inside a 2.2-block glow and lives two seconds (stroke,
 * re-strikes, afterglow); heavy hits keep their dust and rings in the air
 * for a second or more. Pure data, unit tested (ThorFxRecipesTest).
 */
public final class ThorFxRecipes {
   private ThorFxRecipes() {
   }

   public static void init() {
      all().forEach(FxRecipe::register);
   }

   public static List<FxRecipe> all() {
      return List.of(bolt(), boltHeavy(), wrathStrike(), arc(), ring(), crackle(), groundCrawl(), charge(), impactFist(), impactHammer(),
         impactHammerHeavy(), impactGround(), impactElectric(), catchFlash());
   }

   static FxRecipe bolt() {
      return FxRecipe.builder(ThorFx.BOLT)
         .bolt(0, 40, 40.0F, 1.0F, 5, 0.0F)
         .skyFlash(2, 12, 40.0F, 14.0F)
         .lightPool(2, 20, 7.0F, 1.0F)
         .screenFlash(2, 6, 0.3F, 48.0F)
         .flash(2, 10, 2.4F)
         .shake(2, 0.7F, 32.0F)
         .crawl(3, 20, 4.0F, 7)
         .sparks(2, 26, 40, 0.35F)
         .dust(3, 2.5F, 30)
         .scorch(3, 700, 2.0F, 9)
         .crackle(8, 32, 0.9F, 6)
         .build();
   }

   static FxRecipe boltHeavy() {
      return FxRecipe.builder(ThorFx.BOLT_HEAVY)
         .bolt(0, 56, 48.0F, 1.6F, 7, 0.0F)
         .bolt(5, 40, 44.0F, 0.8F, 3, 1.5F)
         .skyFlash(2, 18, 46.0F, 20.0F)
         .lightPool(2, 28, 10.0F, 1.3F)
         .screenFlash(2, 10, 0.5F, 64.0F)
         .flash(2, 14, 3.6F)
         .shake(2, 1.3F, 48.0F)
         .shockwave(3, 26, 6.0F, 1.2F)
         .dust(3, 4.0F, 70)
         .debris(3, 14, 4.0F, 0.8F)
         .crawl(3, 26, 6.5F, 10)
         .sparks(2, 30, 70, 0.5F)
         .scorch(3, 900, 3.2F, 12)
         .crawl(12, 24, 4.5F, 6)
         .crackle(10, 46, 1.2F, 8)
         .build();
   }

   /**
    * The ultimate's contact: one giant column, satellites over the first
    * half second, three crawler waves and aftershocks over the long tail
    * (about four seconds after contact; the storm build-up before it is
    * state-driven, {@link ThorStormVfx}).
    */
   static FxRecipe wrathStrike() {
      return FxRecipe.builder(ThorFx.WRATH_STRIKE)
         .bolt(0, 80, 60.0F, 3.0F, 9, 0.0F)
         .bolt(2, 50, 56.0F, 1.0F, 4, 3.5F)
         .bolt(5, 46, 56.0F, 1.0F, 4, 3.5F)
         .bolt(9, 44, 56.0F, 0.9F, 3, 4.0F)
         .bolt(14, 40, 56.0F, 0.9F, 3, 4.0F)
         .skyFlash(1, 32, 58.0F, 32.0F)
         .lightPool(1, 56, 16.0F, 1.6F)
         .screenFlash(1, 16, 0.85F, 96.0F)
         .flash(1, 22, 6.0F)
         .shake(1, 2.5F, 96.0F)
         .shockwave(2, 30, 14.0F, 1.5F)
         .dust(2, 9.0F, 160)
         .debris(2, 30, 9.0F, 1.2F)
         .crawl(2, 32, 10.0F, 14)
         .sparks(1, 40, 120, 0.7F)
         .scorch(2, 1200, 6.0F, 16)
         .shockwave(8, 40, 22.0F, 1.0F)
         .crawl(12, 34, 8.0F, 10)
         .shake(16, 1.4F, 96.0F)
         .crackle(20, 60, 2.0F, 10)
         .crawl(30, 30, 6.0F, 8)
         .shake(34, 0.8F, 96.0F)
         .bolt(36, 30, 50.0F, 0.7F, 3, 6.0F)
         .bolt(52, 28, 50.0F, 0.6F, 2, 8.0F)
         .crackle(56, 30, 1.6F, 6)
         .build();
   }

   static FxRecipe arc() {
      return FxRecipe.builder(ThorFx.ARC)
         .arc(0, 8, 0.06F, 3)
         .sparks(0, 12, 5, 0.12F)
         .build();
   }

   static FxRecipe ring() {
      return FxRecipe.builder(ThorFx.RING)
         .ring(0, 12, 0.07F, 16)
         .sparks(0, 14, 10, 0.15F)
         .build();
   }

   static FxRecipe crackle() {
      return FxRecipe.builder(ThorFx.CRACKLE)
         .crackle(0, 14, 0.6F, 5)
         .sparks(0, 16, 8, 0.12F)
         .build();
   }

   static FxRecipe groundCrawl() {
      return FxRecipe.builder(ThorFx.GROUND_CRAWL)
         .crawl(0, 22, 5.0F, 8)
         .lightPool(0, 16, 3.0F, 0.6F)
         .build();
   }

   static FxRecipe charge() {
      return FxRecipe.builder(ThorFx.CHARGE)
         .crackle(0, 10, 0.45F, 4)
         .sparks(0, 18, 10, 0.18F)
         .flash(0, 4, 0.6F)
         .build();
   }

   static FxRecipe impactFist() {
      return FxRecipe.builder(ThorFx.IMPACT_FIST)
         .flash(0, 5, 0.9F)
         .shake(0, 0.5F, 16.0F)
         .dust(0, 1.2F, 14)
         .build();
   }

   static FxRecipe impactHammer() {
      return FxRecipe.builder(ThorFx.IMPACT_HAMMER)
         .flash(0, 7, 1.6F)
         .shake(0, 0.9F, 24.0F)
         .shockwave(0, 16, 2.5F, 0.6F)
         .dust(0, 2.0F, 30)
         .debris(0, 6, 2.5F, 0.5F)
         .sparks(0, 12, 14, 0.25F)
         .build();
   }

   static FxRecipe impactHammerHeavy() {
      return FxRecipe.builder(ThorFx.IMPACT_HAMMER_HEAVY)
         .flash(0, 9, 2.4F)
         .shake(0, 1.5F, 32.0F)
         .shockwave(0, 22, 4.0F, 1.2F)
         .dust(0, 3.5F, 60)
         .debris(0, 12, 4.0F, 0.8F)
         .sparks(0, 16, 24, 0.35F)
         .shockwave(3, 26, 6.0F, 0.8F)
         .build();
   }

   static FxRecipe impactGround() {
      return FxRecipe.builder(ThorFx.IMPACT_GROUND)
         .flash(0, 10, 3.0F)
         .shake(0, 2.0F, 40.0F)
         .shockwave(0, 26, 6.0F, 1.5F)
         .dust(0, 5.0F, 110)
         .debris(0, 22, 6.0F, 1.1F)
         .scorch(0, 800, 2.6F, 10)
         .shockwave(4, 32, 9.0F, 1.0F)
         .shake(8, 0.8F, 40.0F)
         .build();
   }

   static FxRecipe impactElectric() {
      return FxRecipe.builder(ThorFx.IMPACT_ELECTRIC)
         .flash(0, 7, 1.8F)
         .shake(0, 1.0F, 24.0F)
         .shockwave(0, 16, 2.5F, 0.6F)
         .dust(0, 2.0F, 30)
         .debris(0, 6, 2.5F, 0.5F)
         .crackle(0, 16, 0.8F, 5)
         .crawl(0, 18, 3.5F, 6)
         .lightPool(0, 12, 3.0F, 0.7F)
         .sparks(0, 14, 20, 0.3F)
         .build();
   }

   static FxRecipe catchFlash() {
      return FxRecipe.builder(ThorFx.CATCH)
         .flash(0, 5, 1.0F)
         .crackle(0, 10, 0.5F, 5)
         .sparks(0, 12, 12, 0.2F)
         .build();
   }
}
