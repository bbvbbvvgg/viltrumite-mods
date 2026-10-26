package dev.baranhan.viltrumitecore.client.reinhard.saw;

import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The Saw Sphere's bed layer: one looping low hum per scene at the centre, rising with the shell
 * (volume and pitch over phase I), breathing with the spin, released after the final pass and
 * stopped at once by an interrupted or dropped scene.
 */
final class SawHumSound extends AbstractTickableSoundInstance {
   private final SawSphereClient.Scene scene;

   SawHumSound(SoundEvent event, SawSphereClient.Scene scene) {
      super(event, SoundSource.PLAYERS, RandomSource.create());
      this.scene = scene;
      this.looping = true;
      this.delay = 0;
      this.attenuation = Attenuation.LINEAR;
      this.x = scene.cx;
      this.y = scene.y0 + 4.0;
      this.z = scene.cz;
      this.volume = 0.001F;
      this.pitch = 0.6F;
   }

   /** Pure envelope: rising over phase I, full through phase II, released by the shatter. */
   static float volume(double t) {
      double rise = SawChoreography.smooth(t / 30.0);
      double release = 1.0 - SawChoreography.smooth((t - (SawChoreography.SHATTER - 10)) / 12.0);
      return (float) Math.max(0.001, 0.9 * rise * release);
   }

   /** Pure: pitch climbs 0.6 → 1.0 while the shell grows and swells a little with the spin. */
   static float pitch(double t) {
      double rise = 0.6 + 0.4 * SawChoreography.smooth(t / 30.0);
      double spin = SawChoreography.spinRate(t) / SawChoreography.SPIN_MAX;
      return (float) Math.min(2.0, rise + 0.12 * spin);
   }

   void stopNow() {
      this.stop();
   }

   @Override
   public void tick() {
      if (this.scene.ended || this.scene.ageTicks() >= SawChoreography.SHATTER + 4) {
         this.stop();
         return;
      }
      double t = this.scene.ageTicks();
      this.volume = volume(t);
      this.pitch = pitch(t);
   }
}
