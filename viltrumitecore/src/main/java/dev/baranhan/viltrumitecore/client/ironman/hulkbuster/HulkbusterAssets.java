package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import net.minecraft.resources.ResourceLocation;

/**
 * Mark 48 art (rework spec §2, plan H1): one rigged body and its first-person arm chains, both written by
 * tools/assets/rig_hulkbuster.py from the unchanged Sind source. One atlas (body + jackhammer arm), one
 * emissive mask built only from the emitting faces. Thruster flames are bones of the body ({@link #FLAME_PREFIX})
 * drawn in their own pass with the repulsor flame texture; the armour passes hide them.
 */
public final class HulkbusterAssets {
   public static final ResourceLocation BODY = loc("geo/ironman/hulkbuster/mark48.geo.json");
   /** The two arm chains on the vanilla arm pivots (same bones and clips as the body). */
   public static final ResourceLocation FP_ARMS = loc("geo/ironman/hulkbuster/mark48_fp.geo.json");
   public static final ResourceLocation ANIMATIONS = loc("animations/ironman/hulkbuster/mark48.animation.json");
   public static final ResourceLocation TEXTURE = loc("textures/entity/ironman/hulkbuster.png");
   public static final ResourceLocation GLOW = loc("textures/entity/ironman/hulkbuster_glow.png");
   /** Bones whose name starts here are thruster flames. */
   public static final String FLAME_PREFIX = "flame_";
   public static final String ROOT = "root";
   public static final String HEAD = "head";
   public static final String CHEST = "chest";
   public static final String TORSO = "torso";
   public static final String REACTOR = "reactor";
   public static final String SHOULDER_R = "shoulder_r";
   public static final String SHOULDER_L = "shoulder_l";
   public static final String FIST_R = "fist_r";
   public static final String HAND_L = "hand_l";
   public static final String FOREARM_R = "forearm_r";
   public static final String FOREARM_L = "forearm_l";
   public static final String PISTON_R = "piston_r";
   public static final String FOOT_THRUSTER_R = "foot_thruster_r";
   public static final String FOOT_THRUSTER_L = "foot_thruster_l";
   public static final String BACK_THRUSTERS = "back_thrusters";
   /** Plates hidden by the damage stages (spec §2): stage 1 drops the first set, stage 2 both. */
   public static final String[][] DAMAGE_PLATES = {{"shoulder_cap_l", "chest_flap_r"}, {"helmet_l", "shoulder_cap_r", "chest_flap_l"}};
   /** Sind model units: 68 px tall. */
   public static final float SIND_UNITS = 32.0F / 68.0F;

   private HulkbusterAssets() {
   }

   private static ResourceLocation loc(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }
}
