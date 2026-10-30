package dev.baranhan.viltrumitecore.client.city;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.viltrumitecore.city.citizen.CitizenSkinPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Uploads the generated citizen skins once, on first use (render thread). */
public final class CitizenSkins {
   private static final ResourceLocation[] TEXTURES = new ResourceLocation[CitizenSkinPainter.COUNT];

   private CitizenSkins() {
   }

   public static ResourceLocation texture(int skin) {
      int i = Math.floorMod(skin, CitizenSkinPainter.COUNT);
      ResourceLocation rl = TEXTURES[i];
      if (rl == null) {
         int[] argb = CitizenSkinPainter.paint(i);
         NativeImage img = new NativeImage(CitizenSkinPainter.SIZE, CitizenSkinPainter.SIZE, true);
         for (int y = 0; y < CitizenSkinPainter.SIZE; y++) {
            for (int x = 0; x < CitizenSkinPainter.SIZE; x++) {
               int c = argb[y * CitizenSkinPainter.SIZE + x];
               // NativeImage stores ABGR
               int abgr = c & 0xFF00FF00 | (c & 0xFF) << 16 | c >> 16 & 0xFF;
               img.setPixelRGBA(x, y, abgr);
            }
         }

         rl = new ResourceLocation("viltrumitecore", "citizen/skin_" + i);
         Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
         TEXTURES[i] = rl;
      }

      return rl;
   }
}
