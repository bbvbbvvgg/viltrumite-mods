package dev.baranhan.viltrumitecore.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** Drives {@link SkyTint}: real-time weight easing once per frame and the fog colour. Cleared on a level change. */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class SkyTintEvents {
   private static long lastNanos;
   private static ClientLevel lastLevel;

   private SkyTintEvents() {
   }

   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent event) {
      if (event.phase != Phase.START) {
         return;
      }

      ClientLevel level = Minecraft.getInstance().level;
      if (level != lastLevel) {
         lastLevel = level;
         SkyTint.INSTANCE.clear();
      }

      long now = System.nanoTime();
      float dt = lastNanos == 0L ? 0.0F : Math.min(0.1F, (float)(now - lastNanos) / 1.0E9F);
      lastNanos = now;
      if (SkyTint.INSTANCE.active()) {
         SkyTint.INSTANCE.advance(dt);
      }
   }

   @SubscribeEvent
   public static void onFogColor(ViewportEvent.ComputeFogColor event) {
      if (!SkyTint.INSTANCE.active()) {
         return;
      }

      double[] rgb = SkyTint.apply(event.getRed(), event.getGreen(), event.getBlue(), SkyTint.INSTANCE.current());
      event.setRed((float)rgb[0]);
      event.setGreen((float)rgb[1]);
      event.setBlue((float)rgb[2]);
   }
}
