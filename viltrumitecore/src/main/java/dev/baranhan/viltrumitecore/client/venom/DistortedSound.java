package dev.baranhan.viltrumitecore.client.venom;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** A ridden host hears the world through the symbiote: quieter and lower (muffled, distorted). Delegates the rest. */
final class DistortedSound implements SoundInstance {
   private final SoundInstance inner;
   private final float factor;
   private final float pitch;

   DistortedSound(SoundInstance inner, float factor, float pitch) {
      this.pitch = pitch;
      this.inner = inner;
      this.factor = factor;
   }

   @Override
   public ResourceLocation getLocation() {
      return this.inner.getLocation();
   }

   @Nullable
   @Override
   public WeighedSoundEvents resolve(SoundManager manager) {
      return this.inner.resolve(manager);
   }

   @Override
   public Sound getSound() {
      return this.inner.getSound();
   }

   @Override
   public SoundSource getSource() {
      return this.inner.getSource();
   }

   @Override
   public boolean isLooping() {
      return this.inner.isLooping();
   }

   @Override
   public boolean isRelative() {
      return this.inner.isRelative();
   }

   @Override
   public int getDelay() {
      return this.inner.getDelay();
   }

   @Override
   public float getVolume() {
      return this.inner.getVolume() * this.factor;
   }

   @Override
   public float getPitch() {
      return this.inner.getPitch() * this.pitch;
   }

   @Override
   public double getX() {
      return this.inner.getX();
   }

   @Override
   public double getY() {
      return this.inner.getY();
   }

   @Override
   public double getZ() {
      return this.inner.getZ();
   }

   @Override
   public Attenuation getAttenuation() {
      return this.inner.getAttenuation();
   }

   @Override
   public boolean canStartSilent() {
      return this.inner.canStartSilent();
   }

   @Override
   public boolean canPlaySound() {
      return this.inner.canPlaySound();
   }

   @Override
   public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
      return this.inner.getStream(library, sound, looping);
   }
}
