package dev.baranhan.viltrumitecore.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Ticks the {@link SoundDuck} fades and refreshes the playing channels: a
 * non-MASTER {@code updateSourceVolume} makes the engine re-run
 * {@code calculateVolume(instance)} for every active instance. Cleared (and
 * refreshed) on a level change.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class SoundDuckEvents {
   private static ClientLevel lastLevel;

   private SoundDuckEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      boolean changed = false;
      if (client.level != lastLevel) {
         lastLevel = client.level;
         changed = SoundDuck.INSTANCE.active();
         SoundDuck.INSTANCE.clear();
      }

      if (!client.isPaused() && SoundDuck.INSTANCE.active()) {
         changed |= SoundDuck.INSTANCE.tick();
      }

      if (changed) {
         client.getSoundManager().updateSourceVolume(SoundSource.PLAYERS, client.options.getSoundSourceVolume(SoundSource.PLAYERS));
      }
   }
}
