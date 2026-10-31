package dev.baranhan.viltrumitecore.client.budget;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import dev.baranhan.viltrumitecore.hero.budget.BudgetLook;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Paints the expansion skins at runtime from {@link BudgetLook}: a recoloured
 * vanilla 64x64 layout with a few face tokens. No PNG per hero is shipped.
 * Textures are created lazily on the render thread and kept for the session.
 */
public final class BudgetSkins {
   public static final ResourceLocation WHITE = new ResourceLocation("viltrumitecore", "budget_skin/white");
   private static final Map<HeroId, ResourceLocation> SKINS = new EnumMap<>(HeroId.class);
   private static boolean whiteReady;

   private BudgetSkins() {
   }

   public static ResourceLocation skin(BudgetHeroSpec spec) {
      ResourceLocation location = SKINS.get(spec.id());
      if (location == null) {
         location = new ResourceLocation("viltrumitecore", "budget_skin/" + spec.key().toLowerCase(Locale.ROOT));
         Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(paint(spec.look())));
         SKINS.put(spec.id(), location);
      }

      return location;
   }

   public static ResourceLocation skin(HeroId id) {
      BudgetHeroSpec spec = BudgetHeroes.spec(id);
      return spec == null ? net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin() : skin(spec);
   }

   /** Plain white texture tinted by the cube detail layer. */
   public static ResourceLocation white() {
      if (!whiteReady) {
         NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, true);
         image.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
         Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(image));
         whiteReady = true;
      }

      return WHITE;
   }

   // ---------------------------------------------------------------------

   static NativeImage paint(BudgetLook look) {
      NativeImage image = new NativeImage(NativeImage.Format.RGBA, 64, 64, true);
      boolean bald = has(look, "bald");
      int hair = bald ? look.skin() : look.hair();

      // Head: skin, hair on top, back and the upper side rows.
      box(image, 0, 0, 8, 8, 8, look.skin());
      top(image, 0, 0, 8, 8, hair);
      sideRows(image, 0, 0, 8, 8, 8, 0, bald ? -1 : 1, hair);
      back(image, 0, 0, 8, 8, 8, bald ? look.skin() : hair);
      // Body, arms (sleeves down to the wrist), legs with shoes.
      box(image, 16, 16, 8, 12, 4, look.shirt());
      box(image, 40, 16, 4, 12, 4, look.shirt());
      box(image, 32, 48, 4, 12, 4, look.shirt());
      sideRows(image, 40, 16, 4, 12, 4, 10, 11, look.skin());
      sideRows(image, 32, 48, 4, 12, 4, 10, 11, look.skin());
      box(image, 0, 16, 4, 12, 4, look.pants());
      box(image, 16, 48, 4, 12, 4, look.pants());
      sideRows(image, 0, 16, 4, 12, 4, 9, 11, look.shoes());
      sideRows(image, 16, 48, 4, 12, 4, 9, 11, look.shoes());

      for (String token : look.face()) {
         int split = token.indexOf(':');
         String name = split < 0 ? token : token.substring(0, split);
         int color = split < 0 ? 0x111111 : parse(token.substring(split + 1));
         applyToken(image, name, color, look);
      }

      // Eyes last so masks keep them; a simple mouth.
      if (!has(look, "blindfold")) {
         px(image, 9, 12, 0xF4F4F4);
         px(image, 10, 12, look.eyes());
         px(image, 13, 12, look.eyes());
         px(image, 14, 12, 0xF4F4F4);
      }

      if (!has(look, "mask")) {
         px(image, 11, 14, shade(look.skin(), -40));
         px(image, 12, 14, shade(look.skin(), -40));
      }

      roughen(image);
      return image;
   }

   private static void applyToken(NativeImage image, String name, int color, BudgetLook look) {
      switch (name) {
         case "blindfold" -> sideRows(image, 0, 0, 8, 8, 8, 3, 4, color);
         case "mask" -> box(image, 0, 0, 8, 8, 8, color);
         case "cowl" -> {
            box(image, 0, 0, 8, 8, 8, color);
            rect(image, 9, 13, 6, 3, look.skin());
         }
         case "domino" -> rect(image, 8, 11, 8, 2, color);
         case "marks" -> {
            rect(image, 9, 13, 2, 1, color);
            rect(image, 13, 13, 2, 1, color);
            px(image, 9, 11, 0xC01010);
            px(image, 14, 11, 0xC01010);
         }
         case "scar" -> rect(image, 13, 13, 2, 1, color);
         case "stitches" -> {
            for (int y = 9; y < 16; y += 2) {
               px(image, 11, y, color);
            }
            rect(image, 9, 10, 2, 1, color);
         }
         case "blood_line" -> rect(image, 9, 13, 6, 1, color);
         case "whiskers" -> {
            rect(image, 8, 13, 2, 1, color);
            rect(image, 14, 13, 2, 1, color);
         }
         case "patches" -> {
            rect(image, 9, 11, 2, 3, color);
            rect(image, 13, 11, 2, 3, color);
         }
         case "beard" -> rect(image, 9, 14, 6, 2, color);
         case "sideburns" -> {
            rect(image, 8, 11, 1, 4, color);
            rect(image, 15, 11, 1, 4, color);
         }
         case "tattoo" -> {
            rect(image, 10, 8, 1, 8, color);
            rect(image, 21, 20, 2, 12, color);
            rect(image, 8, 0, 1, 8, color);
         }
         case "emblem" -> rect(image, 22, 22, 4, 3, color);
         case "belt" -> sideRows(image, 16, 16, 8, 12, 4, 8, 9, color);
         case "tie" -> rect(image, 23, 20, 2, 8, color);
         case "vest" -> {
            rect(image, 20, 20, 2, 12, color);
            rect(image, 26, 20, 2, 12, color);
            rect(image, 32, 20, 8, 12, color);
         }
         case "armor" -> sideRows(image, 16, 16, 8, 12, 4, 0, 6, color);
         case "trunks" -> {
            sideRows(image, 0, 16, 4, 12, 4, 0, 2, color);
            sideRows(image, 16, 48, 4, 12, 4, 0, 2, color);
            sideRows(image, 16, 16, 8, 12, 4, 10, 11, color);
         }
         case "gloves" -> {
            sideRows(image, 40, 16, 4, 12, 4, 9, 11, color);
            sideRows(image, 32, 48, 4, 12, 4, 9, 11, color);
         }
         case "metal_arms" -> {
            box(image, 40, 16, 4, 12, 4, color);
            box(image, 32, 48, 4, 12, 4, color);
         }
         default -> {
         }
      }
   }

   private static boolean has(BudgetLook look, String name) {
      for (String token : look.face()) {
         if (token.equals(name) || token.startsWith(name + ":")) {
            return true;
         }
      }

      return false;
   }

   static int parse(String hex) {
      try {
         return Integer.parseInt(hex.trim(), 16) & 0xFFFFFF;
      } catch (NumberFormatException exception) {
         return 0xFF00FF;
      }
   }

   /** Whole box region of a cuboid (top/bottom strip and the four sides). */
   private static void box(NativeImage image, int u, int v, int w, int h, int d, int color) {
      rect(image, u + d, v, w * 2, d, color);
      rect(image, u, v + d, d * 2 + w * 2, h, color);
   }

   private static void top(NativeImage image, int u, int v, int w, int d, int color) {
      rect(image, u + d, v, w, d, color);
   }

   private static void back(NativeImage image, int u, int v, int w, int h, int d, int color) {
      rect(image, u + d * 2 + w, v + d, w, h, color);
   }

   /** Rows y0..y1 (box-local) of all four side faces; y1 &lt; y0 paints nothing. */
   private static void sideRows(NativeImage image, int u, int v, int w, int h, int d, int y0, int y1, int color) {
      if (y1 < y0) {
         return;
      }

      rect(image, u, v + d + y0, d * 2 + w * 2, y1 - y0 + 1, color);
   }

   private static void rect(NativeImage image, int x, int y, int w, int h, int color) {
      for (int dy = 0; dy < h; dy++) {
         for (int dx = 0; dx < w; dx++) {
            px(image, x + dx, y + dy, color);
         }
      }
   }

   private static void px(NativeImage image, int x, int y, int rgb) {
      if (x < 0 || y < 0 || x >= 64 || y >= 64) {
         return;
      }

      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      image.setPixelRGBA(x, y, 0xFF000000 | b << 16 | g << 8 | r);
   }

   private static int shade(int rgb, int delta) {
      int r = Math.max(0, Math.min(255, (rgb >> 16 & 255) + delta));
      int g = Math.max(0, Math.min(255, (rgb >> 8 & 255) + delta));
      int b = Math.max(0, Math.min(255, (rgb & 255) + delta));
      return r << 16 | g << 8 | b;
   }

   /** Fixed per-pixel jitter: the flat colours get the usual hand-painted unevenness. */
   private static void roughen(NativeImage image) {
      for (int y = 0; y < 64; y++) {
         for (int x = 0; x < 64; x++) {
            int abgr = image.getPixelRGBA(x, y);
            if ((abgr >>> 24) == 0) {
               continue;
            }

            int jitter = ((x * 73 + y * 151) % 7) * 3 - 9;
            int r = Math.max(0, Math.min(255, (abgr & 255) + jitter));
            int g = Math.max(0, Math.min(255, (abgr >> 8 & 255) + jitter));
            int b = Math.max(0, Math.min(255, (abgr >> 16 & 255) + jitter));
            image.setPixelRGBA(x, y, 0xFF000000 | b << 16 | g << 8 | r);
         }
      }
   }
}
