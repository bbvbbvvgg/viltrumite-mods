package dev.baranhan.viltrumitecore.client.scp096;

import dev.baranhan.viltrumitecore.scp.scp096.Scp096Entity;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * One SCP-096 loop: positional on the entity (the sobbing, the wailing, the screaming; the streamed originals are
 * folded to mono so they really come from it) or relative to the listener (the chase music, kept stereo). The
 * volume fades toward {@link #target}, re-set by {@link Scp096ClientSounds} every tick; the loop stops itself
 * once the target stays at zero and the fade is done, or when its entity is gone. Client only.
 */
final class Scp096LoopSound extends AbstractTickableSoundInstance {
   @Nullable
   private final Scp096Entity entity;
   private final boolean mono;
   private final float fadeUp;
   private final float fadeDown;
   private float level;
   private int silent;
   /** Wanted loudness 0..1, written by the client tick. */
   float target;

   Scp096LoopSound(SoundEvent event, SoundSource source, @Nullable Scp096Entity entity, boolean loop, float fadeUp, float fadeDown) {
      super(event, source, SoundInstance.createUnseededRandom());
      this.entity = entity;
      this.mono = entity != null;
      this.fadeUp = fadeUp;
      this.fadeDown = fadeDown;
      this.looping = loop;
      this.delay = 0;
      this.volume = 0.001F;
      if (entity == null) {
         this.relative = true;
         this.attenuation = SoundInstance.Attenuation.NONE;
      } else {
         this.attenuation = SoundInstance.Attenuation.LINEAR;
         this.x = entity.getX();
         this.y = entity.getY() + 2.0;
         this.z = entity.getZ();
      }
   }

   @Nullable
   Scp096Entity entity() {
      return this.entity;
   }

   @Override
   public boolean canStartSilent() {
      return true;
   }

   @Override
   public boolean canPlaySound() {
      return this.entity == null || !this.entity.isSilent();
   }

   @Override
   public void tick() {
      float goal = Mth.clamp(this.target, 0.0F, 1.0F);
      if (this.entity != null && (this.entity.isRemoved() || !this.entity.isAlive())) {
         goal = 0.0F;
         this.level = Math.min(this.level, 0.3F);
      }
      this.level = goal > this.level ? Math.min(goal, this.level + this.fadeUp) : Math.max(goal, this.level - this.fadeDown);
      this.silent = goal <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
      if (this.silent > 2) {
         this.stop();
         return;
      }
      this.volume = Math.max(0.001F, this.level);
      if (this.entity != null) {
         this.x = this.entity.getX();
         this.y = this.entity.getY() + this.entity.getBbHeight() * 0.85;
         this.z = this.entity.getZ();
      }
   }

   void halt() {
      this.stop();
   }

   /** Streamed originals are stereo: positional loops get a mono view so OpenAL attenuates and pans them. */
   @Override
   public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
      CompletableFuture<AudioStream> stream = buffers.getStream(sound.getPath(), looping);
      return this.mono ? stream.thenApply(MonoDownmixStream::wrap) : stream;
   }
}
