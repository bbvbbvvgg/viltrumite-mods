package dev.baranhan.viltrumitecore.client.wish;

import javax.annotation.Nullable;

/**
 * The look and sound of each wish scene ({@code Wish.style()}): the hero's colour, the motif the scene
 * renderer draws in the FULFIL phase, the sky tint while the light gathers, and the two hero sound cues
 * (one while the answer builds, one on the swap). Sound ids are existing viltrumitecore events, so every
 * hero sounds like itself; the shared Od Lagna sounds come from {@link dev.baranhan.viltrumitecore.hero.wish.WishSounds}.
 */
public enum WishStyle {
   TIME("time", 0xFFD34D, 0xBFEFFF, 0.92F, 0.9F, 0.7F, 0.35F, "dio.stand_summon", "dio.time_stop"),
   SHADOW("shadow", 0x8A4DFF, 0x1A0A33, 0.62F, 0.5F, 0.95F, 0.55F, "jinwoo.monarch_charge", "jinwoo.ult_rise"),
   THUNDER("thunder", 0x7FD8FF, 0xFFFFFF, 0.6F, 0.7F, 0.95F, 0.45F, "thor.storm_charge", "thor.thunder_crack"),
   CARDS("cards", 0xFF3B5C, 0xFFF2D8, 1.0F, 0.85F, 0.9F, 0.15F, "sinclair.act_drumroll", "sinclair.tada"),
   TECH("tech", 0x5FE6FF, 0xFF3A2A, 0.8F, 0.92F, 1.0F, 0.2F, "ironman_nano_deploy", "ironman_helmet_close"),
   CRIMSON("crimson", 0xFF2A2A, 0x3A7BFF, 1.0F, 0.55F, 0.5F, 0.35F, "homelander_laser_charge", "homelander_roar"),
   HOLY("holy", 0xFFF1B8, 0xFF9A3A, 1.0F, 0.95F, 0.8F, 0.0F, "reinhard.reid_choir", "reinhard.mode_holy_sword"),
   SHIELD("shield", 0x3A78FF, 0xFFFFFF, 0.78F, 0.85F, 1.0F, 0.25F, "captain.charged", "captain.parry"),
   STILL("still", 0xE8EEF5, 0x9FB2C8, 0.8F, 0.82F, 0.88F, 0.3F, null, "regulus_impact_heavy"),
   MORTAL("mortal", 0xFFE3A3, 0xFFFFFF, 1.0F, 0.95F, 0.85F, 0.0F, null, null);

   /** Od Lagna's own gold: the shared ring, motes and title. */
   public static final int GOLD = 0xFFD98A;

   public final String key;
   public final int primary;
   public final int secondary;
   public final float tintR;
   public final float tintG;
   public final float tintB;
   public final float darken;
   @Nullable
   public final String buildSound;
   @Nullable
   public final String climaxSound;

   WishStyle(String key, int primary, int secondary, float tintR, float tintG, float tintB, float darken, @Nullable String buildSound,
      @Nullable String climaxSound) {
      this.key = key;
      this.primary = primary;
      this.secondary = secondary;
      this.tintR = tintR;
      this.tintG = tintG;
      this.tintB = tintB;
      this.darken = darken;
      this.buildSound = buildSound;
      this.climaxSound = climaxSound;
   }

   public static WishStyle of(@Nullable String key) {
      for (WishStyle style : values()) {
         if (style.key.equals(key)) {
            return style;
         }
      }
      return MORTAL;
   }

   public static int r(int rgb) {
      return rgb >> 16 & 0xFF;
   }

   public static int g(int rgb) {
      return rgb >> 8 & 0xFF;
   }

   public static int b(int rgb) {
      return rgb & 0xFF;
   }
}
