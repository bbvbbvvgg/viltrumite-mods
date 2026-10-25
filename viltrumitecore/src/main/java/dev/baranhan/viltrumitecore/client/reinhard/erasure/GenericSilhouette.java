package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;

/** Small flat paint-pixel fallback, and deterministic disintegration pixels. No model over the wire. */
public final class GenericSilhouette {
   public record Pixel(float x, float y, float drift, float up, float size) {}
   private GenericSilhouette() {}
   public static float ratio(float width, float height) {
      float ratio = width / height;
      return Float.isFinite(ratio) && ratio > 0 ? Math.max(.1F, Math.min(8, ratio)) : .5F;
   }
   public static boolean contains(float x, float y, boolean humanoid) {
      if(x < 0 || x > 1 || y < 0 || y > 1) return false;
      if(!humanoid) {
         float dx = Math.max(0, Math.abs(x-.5F)-.36F), dy = Math.max(0, Math.abs(y-.5F)-.36F);
         return dx*dx + dy*dy <= .14F*.14F;
      }
      float head = (x-.5F)*(x-.5F)/(.135F*.135F)+(y-.12F)*(y-.12F)/(.12F*.12F);
      return head <= 1 || (y>=.26F && y<.6F && x>=.2F && x<=.8F)
         || (y>=.6F && ((x>=.35F && x<=.47F)||(x>=.53F && x<=.65F)));
   }
   public static List<Pixel> pixels(long seed, boolean humanoid) {
      List<Pixel> pixels = new ArrayList<>(); Random random = new Random(seed);
      for(int y=0;y<24;y++) for(int x=0;x<12;x++) {
         float u=(x+.5F)/12, v=(y+.5F)/24;
         if(contains(u,v,humanoid)) pixels.add(new Pixel(u,v,(random.nextFloat()-.5F)*.7F,.25F+random.nextFloat()*.55F,.5F+random.nextFloat()*.5F));
      }
      return List.copyOf(pixels);
   }
   public static void draw(GuiGraphics gui, float cx, float cy, float height, float aspect, boolean humanoid, float alpha) {
      if(alpha <= 0) return;
      float width = height*(humanoid ? .48F : aspect);
      int color=(Math.round(255*alpha)<<24)|0xFFFFFF;
      for(int y=0;y<24;y++) for(int x=0;x<12;x++) {
         float u=(x+.5F)/12, v=(y+.5F)/24;
         if(contains(u,v,humanoid)) gui.fill((int)Math.floor(cx+(u-.5F)*width-width/24),(int)Math.floor(cy+(v-.5F)*height-height/48),
            (int)Math.ceil(cx+(u-.5F)*width+width/24),(int)Math.ceil(cy+(v-.5F)*height+height/48),color);
      }
   }
   public static void dust(GuiGraphics gui, List<Pixel> pixels, float cx, float cy, float height, float aspect, boolean humanoid, float t) {
      float alpha=ErasureScene.dustAlpha(t); if(alpha <= 0) return;
      float progress=Math.max(0,Math.min(1,(t-80)/10)), width=height*(humanoid ? .48F : aspect);
      for(Pixel pixel:pixels) {
         float shrink=1-progress*progress;
         double turn=progress*(1.5+pixel.up()+pixel.drift()*2);
         float dx=(pixel.x()-.5F)*width,dy=(pixel.y()-.5F)*height;
         float x=cx+(float)(dx*Math.cos(turn)-dy*Math.sin(turn))*shrink;
         float y=cy+(float)(dx*Math.sin(turn)+dy*Math.cos(turn))*shrink;
         PixelVfx.guiPixel(gui,x,y,Math.max(1,height/90)*pixel.size(),220,238,255,Math.round(255*alpha));
         if(pixel.size()>.8F) PixelVfx.guiPixel(gui,x+pixel.drift()*height*.05F,y,Math.max(1,height/160),255,220,150,Math.round(180*alpha));
      }
   }
}
