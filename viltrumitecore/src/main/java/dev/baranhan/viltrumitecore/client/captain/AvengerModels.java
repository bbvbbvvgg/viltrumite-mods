package dev.baranhan.viltrumitecore.client.captain;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerKind;
import javax.annotation.Nullable;

/**
 * Which files draw each Avenger (resource paths inside the {@code viltrumitecore} namespace). Thor, Black Widow,
 * Hawkeye and Hulk have their own shaded body meshes (UV 128x256 / 256x512, the size of their skins); Iron Man is
 * the hero's own Mark 42: the shared player rig wears the Mark 42 skin (with Tony's face under the faceplate) and
 * his gear mesh is the hero's Sind Mark 42 plates re-parented onto the rig (tools/assets/avengers_ironman_mk42.py),
 * drawn with the hero's 160 px plate atlas. Pure, so
 * {@code AvengerModelsTest} can pin the choice: a dedicated body must never fall back to the shared rig again.
 */
public final class AvengerModels {
   /** The shared player-shaped rig (64x64 UV = the player skin layout); Iron Man's Mark 42 skin is a player skin. */
   public static final String SHARED_RIG = "geo/avengers/avenger.geo.json";

   private AvengerModels() {
   }

   /** Body mesh of a kind. */
   public static String body(AvengerKind kind) {
      return switch (kind) {
         case IRON_MAN -> SHARED_RIG;
         case THOR, HULK, BLACK_WIDOW, HAWKEYE -> "geo/avengers/" + kind.key() + ".geo.json";
      };
   }

   /** Skin of the body mesh (same UV size as {@link #body}). */
   public static String skin(AvengerKind kind) {
      return "textures/entity/avengers/" + kind.key() + ".png";
   }

   /** Gear mesh (armour, hair, cape, weapons) with its own bones; Hulk has none. */
   @Nullable
   public static String gear(AvengerKind kind) {
      return kind == AvengerKind.HULK ? null : "geo/avengers/" + kind.key() + "_gear.geo.json";
   }

   /** The hero's Mark 42 plate atlas and its emissive mask (eyes, arc reactor, palms). */
   public static final String MARK_42_PLATES = "textures/entity/ironman/mark_42/pieces.png";
   public static final String MARK_42_GLOW = "textures/entity/ironman/mark_42/pieces_glow.png";

   @Nullable
   public static String gearTexture(AvengerKind kind) {
      return switch (kind) {
         case HULK -> null;
         case IRON_MAN -> MARK_42_PLATES;
         default -> "textures/entity/avengers/" + kind.key() + "_gear.png";
      };
   }

   /** Emissive mask drawn over the gear mesh (over the body for Hulk: his eyes). */
   public static String glow(AvengerKind kind) {
      return switch (kind) {
         case HULK -> "textures/entity/avengers/hulk_glow.png";
         case IRON_MAN -> MARK_42_GLOW;
         default -> "textures/entity/avengers/" + kind.key() + "_gear_glow.png";
      };
   }

   /**
    * Render scale of the body and gear meshes. Model height x scale matches the hitbox ({@link AvengerKind#height}):
    * Hulk's 3.0-block mesh at 1.1 stands 3.3 blocks, about 15 % taller than the first Hulk (2.86 blocks).
    */
   public static float scale(AvengerKind kind) {
      return switch (kind) {
         case HULK -> 1.1F;
         case THOR -> 1.0F;
         case IRON_MAN -> 0.97F;
         case BLACK_WIDOW, HAWKEYE -> 0.9375F;
      };
   }

   /** Clip file played on the body and on the gear mesh. */
   public static String clips(AvengerKind kind) {
      return "animations/avengers/" + kind.key() + ".animation.json";
   }
}
