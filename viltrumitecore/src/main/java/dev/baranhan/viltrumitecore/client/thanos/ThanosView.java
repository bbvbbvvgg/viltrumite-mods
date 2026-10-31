package dev.baranhan.viltrumitecore.client.thanos;

import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Asset locations of the Thanos event and the clip lookup across its five animation files. */
public final class ThanosView {
   public static final ResourceLocation GEO = rl("geo/thanos/thanos.geo.json");
   public static final ResourceLocation TEXTURE = rl("textures/entity/thanos/thanos.png");
   public static final ResourceLocation GLOW = rl("textures/entity/thanos/thanos_glow.png");
   /** Od Lagna's own white-gold skin on Reinhard's rig (same UV layout, Reinhard's files untouched). */
   public static final ResourceLocation OD_LAGNA = rl("textures/entity/thanos/od_lagna_scene.png");
   public static final ResourceLocation OD_LAGNA_GLOW = rl("textures/entity/thanos/od_lagna_scene_glow.png");
   private static final ResourceLocation[] CLIPS = {
      rl("animations/thanos/thanos_base.animation.json"),
      rl("animations/thanos/thanos_melee.animation.json"),
      rl("animations/thanos/thanos_stones.animation.json"),
      rl("animations/thanos/thanos_combos.animation.json"),
      rl("animations/thanos/thanos_scene.animation.json")
   };
   /** Gauntlet socket bones in {@link ThanosStone} order. */
   public static final String[] STONE_BONES = bones();

   private ThanosView() {
   }

   public static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }

   private static String[] bones() {
      ThanosStone[] stones = ThanosStone.values();
      String[] out = new String[stones.length];
      for (int i = 0; i < stones.length; i++) {
         out[i] = "stone_" + stones[i].key;
      }

      return out;
   }

   /** A clip by name from any of the Thanos animation files, or null (resources not loaded). */
   @Nullable
   public static Animation clip(String name) {
      for (ResourceLocation file : CLIPS) {
         Animation clip = AnimCache.animation(file, name);
         if (clip != null) {
            return clip;
         }
      }

      return null;
   }

   public static float r(int rgb) {
      return (rgb >> 16 & 255) / 255.0F;
   }

   public static float g(int rgb) {
      return (rgb >> 8 & 255) / 255.0F;
   }

   public static float b(int rgb) {
      return (rgb & 255) / 255.0F;
   }
}
