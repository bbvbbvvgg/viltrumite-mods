package dev.baranhan.viltrumitecore.client.city;

import com.mojang.blaze3d.platform.NativeImage;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenPainter;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Client cache of painted citizen textures (render thread). One small 128x64 texture per
 * distinct look, shared by every citizen that looks the same; least recently used ones are
 * released past {@link #MAX} (a crowd of hundreds never holds hundreds of stale textures).
 * At most {@link #UPLOADS_PER_TICK} new textures are painted per client tick so a crowd
 * walking into view never stalls a frame; the others borrow an already painted look for
 * that moment.
 */
public final class CitizenSkins {
   static final int MAX = 320;
   static final int UPLOADS_PER_TICK = 10;
   private static final Map<Integer, ResourceLocation> CACHE = new LinkedHashMap<>(256, 0.75F, true);
   private static int next;
   private static long budgetTick = Long.MIN_VALUE;
   private static int budget;

   private CitizenSkins() {
   }

   public static ResourceLocation texture(CitizenLook look) {
      int key = look.textureKey();
      ResourceLocation rl = CACHE.get(key);
      if (rl != null) {
         return rl;
      }

      Minecraft mc = Minecraft.getInstance();
      long tick = mc.level == null ? 0 : mc.level.getGameTime();
      if (tick != budgetTick) {
         budgetTick = tick;
         budget = UPLOADS_PER_TICK;
      }

      if (budget <= 0 && !CACHE.isEmpty()) {
         return CACHE.values().iterator().next();
      }

      budget--;
      int[] argb = CitizenPainter.paint(look);
      NativeImage img = new NativeImage(CitizenPainter.W, CitizenPainter.H, true);
      for (int y = 0; y < CitizenPainter.H; y++) {
         for (int x = 0; x < CitizenPainter.W; x++) {
            int c = argb[y * CitizenPainter.W + x];
            // NativeImage stores ABGR
            img.setPixelRGBA(x, y, c & 0xFF00FF00 | (c & 0xFF) << 16 | c >> 16 & 0xFF);
         }
      }

      rl = new ResourceLocation("viltrumitecore", "citizen/look_" + next++);
      mc.getTextureManager().register(rl, new DynamicTexture(img));
      CACHE.put(key, rl);
      if (CACHE.size() > MAX) {
         var it = CACHE.entrySet().iterator();
         ResourceLocation old = it.next().getValue();
         it.remove();
         mc.getTextureManager().release(old);
      }

      return rl;
   }

   /** Number of painted looks held right now (debug, performance checks). */
   public static int cached() {
      return CACHE.size();
   }
}
