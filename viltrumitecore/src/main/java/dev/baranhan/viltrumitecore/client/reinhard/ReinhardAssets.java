package dev.baranhan.viltrumitecore.client.reinhard;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Reinhard art paths and bone names (user archive, converted by tools/assets/convert_reinhard.py).
 * Body: 32 px tall, vanilla pivots. Sword: block units x16, origin at the hilt guard, one bone {@link #SWORD_BONE}.
 */
public final class ReinhardAssets {
   public static final ResourceLocation BODY = loc("geo/reinhard/reinhard.geo.json");
   public static final ResourceLocation ARMS = loc("geo/reinhard/reinhard_arms.geo.json");
   public static final ResourceLocation SHEATH = loc("geo/reinhard/sheath.geo.json");
   public static final ResourceLocation BLADE = loc("geo/reinhard/blade.geo.json");
   public static final ResourceLocation TEXTURE = loc("textures/entity/reinhard/reinhard.png");
   public static final ResourceLocation GLOW = loc("textures/entity/reinhard/reinhard_glow.png");
   public static final ResourceLocation SWORD_TEXTURE = loc("textures/entity/reinhard/dragon_sword.png");
   public static final ResourceLocation SWORD_GLOW = loc("textures/entity/reinhard/dragon_sword_glow.png");
   public static final ResourceLocation ANIMATIONS = loc("animations/reinhard/reinhard.animation.json");

   public static final String HEAD = "head";
   public static final String BODY_BONE = "body";
   public static final String RIGHT_ARM = "right_arm";
   public static final String LEFT_ARM = "left_arm";
   public static final String RIGHT_LEG = "right_leg";
   public static final String LEFT_LEG = "left_leg";
   public static final String SWORD_BONE = "sword";
   /** Empty bone of blade.geo.json whose pivot is {@link #SHEATHED_OFFSET} (Bedrock px). */
   public static final String SHEATHED_OFFSET_BONE = "sheathed_offset";

   /** Translation (Bedrock px) from the drawn blade to its place in the sheath; printed by convert_reinhard.py. */
   public static final Vector3f SHEATHED_OFFSET = new Vector3f(0.0F, -14.0F, 0.0F);

   private ReinhardAssets() {
   }

   private static ResourceLocation loc(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }
}
