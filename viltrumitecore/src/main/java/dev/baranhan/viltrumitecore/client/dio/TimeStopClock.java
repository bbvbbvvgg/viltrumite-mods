package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.dio.DioSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The clock heard inside stopped time: dio/time_tick is a seamless one-second
 * tick-tock, looped at the listener while the camera is inside a running field
 * (DIO and the frozen alike) and faded out when time resumes or the camera
 * leaves. Before this the loop asset was only played once, as the field ended.
 */
public final class TimeStopClock extends AbstractTickableSoundInstance {
   static final float VOLUME = 0.45F;
   private static TimeStopClock playing;
   private float target = VOLUME;
   private float level;

   private TimeStopClock() {
      super(DioSounds.TIME_TICK.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
      this.looping = true;
      this.delay = 0;
      this.relative = true;
      this.attenuation = SoundInstance.Attenuation.NONE;
      this.volume = 0.0F;
   }

   @Override
   public boolean canStartSilent() {
      return true;
   }

   @Override
   public void tick() {
      this.level = fade(this.level, this.target);
      this.volume = this.level;
      if (this.target <= 0.0F && this.level < 0.01F) {
         this.stop();
      }
   }

   /** One fade step: about a third of a second to settle either way. */
   static float fade(float level, float target) {
      float next = level + (target - level) * 0.18F;
      return Math.abs(next - target) < 0.005F ? target : next;
   }

   /** Called every client tick with whether the camera sits inside a running field. */
   static void update(boolean inside) {
      Minecraft client = Minecraft.getInstance();
      if (inside) {
         if (playing == null || playing.isStopped()) {
            playing = new TimeStopClock();
            client.getSoundManager().play(playing);
         }

         playing.target = VOLUME;
      } else if (playing != null) {
         playing.target = 0.0F;
         if (playing.isStopped()) {
            playing = null;
         }
      }
   }

   static void clear() {
      if (playing != null) {
         Minecraft.getInstance().getSoundManager().stop(playing);
         playing = null;
      }
   }
}
