package dev.baranhan.viltrumitecore.client.scp106;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** One ambience bed of the pocket dimension, relative to the listener, fading toward {@link #target}. Client only. */
final class PocketLoopSound extends AbstractTickableSoundInstance {
   private float level;
   private int silent;
   float target;

   PocketLoopSound(SoundEvent event) {
      super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
      this.looping = true;
      this.delay = 0;
      this.volume = 0.001F;
      this.relative = true;
      this.attenuation = SoundInstance.Attenuation.NONE;
   }

   @Override
   public boolean canStartSilent() {
      return true;
   }

   @Override
   public void tick() {
      float goal = Mth.clamp(this.target, 0.0F, 1.0F);
      this.level = goal > this.level ? Math.min(goal, this.level + 0.02F) : Math.max(goal, this.level - 0.03F);
      this.silent = goal <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
      if (this.silent > 40) {
         this.stop();
         return;
      }
      this.volume = Math.max(0.001F, this.level);
   }

   void halt() {
      this.stop();
   }
}
