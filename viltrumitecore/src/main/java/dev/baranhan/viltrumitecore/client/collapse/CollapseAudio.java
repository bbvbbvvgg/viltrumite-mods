package dev.baranhan.viltrumitecore.client.collapse;

import dev.baranhan.viltrumitecore.client.meteor.MeteorRainAudio;
import dev.baranhan.viltrumitecore.world.collapse.CollapseSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Layered collapse sound: each cue is a one-shot placed at its source (no distance attenuation by the
 * engine; our own curve), delayed by the speed of sound (shared with Meteor Rain) and mixed by distance:
 * near layers (cracks, groans, debris) fade out between 40 and 150 blocks while the distant boom fades in;
 * the low rumble carries furthest. Several collapses at once duck each other. Pure mix functions are tested.
 */
public final class CollapseAudio {
   /** Overall headroom: collapses sit under hero impacts and voices. */
   public static final float HEADROOM = 0.85F;

   public enum Layer {
      NEAR,
      MID,
      RUMBLE,
      DISTANT
   }

   private CollapseAudio() {
   }

   /** Pure: layer gain at a distance (blocks). */
   public static float gain(Layer layer, double d) {
      return switch (layer) {
         case NEAR -> d < 40.0 ? 1.0F : d < 150.0 ? (float)(1.0 - (d - 40.0) / 110.0) : 0.0F;
         case MID -> d < 70.0 ? 1.0F : d < 220.0 ? (float)(1.0 - (d - 70.0) / 150.0) : 0.0F;
         case RUMBLE -> (float)Mth.clamp(1.0 - d / 340.0, 0.0, 1.0);
         case DISTANT -> d < 60.0 ? 0.0F : d < 180.0 ? (float)((d - 60.0) / 120.0 * 0.9) : (float)Mth.clamp(0.9 - (d - 180.0) / 500.0, 0.25, 0.9);
      };
   }

   /** Pure: ducking when {@code running} collapses play at once. */
   public static float duck(int running) {
      return running <= 1 ? 1.0F : running == 2 ? 0.78F : 0.62F;
   }

   /** Pure: bigger sections sound lower and louder. */
   public static float pitch(float strength, double random) {
      return (float)Mth.clamp(1.06 - 0.14 * strength + (random - 0.5) * 0.1, 0.7, 1.2);
   }

   public static float loudness(float strength) {
      return 0.62F + 0.38F * Mth.clamp(strength, 0.0F, 1.0F);
   }

   /** Plays one cue from {@code at} for the camera; {@code extraDelay} is added to the sound travel time. */
   static void cue(RegistryObject<SoundEvent> event, Layer layer, Vec3 at, float volume, float pitch, int extraDelay, int running) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      double d = ear.distanceTo(at);
      float v = volume * gain(layer, d) * duck(running) * HEADROOM;
      if (v <= 0.01F) {
         return;
      }
      SimpleSoundInstance sound = new SimpleSoundInstance(event.get().getLocation(), SoundSource.BLOCKS, Mth.clamp(v, 0.0F, 1.0F), pitch,
         SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
      int delay = MeteorRainAudio.soundDelay(d) + Math.max(0, extraDelay);
      if (delay > 0) {
         mc.getSoundManager().playDelayed(sound, delay);
      } else {
         mc.getSoundManager().play(sound);
      }
   }

   static void impact(Vec3 at, float strength, double rnd, int running) {
      float p = pitch(strength, rnd), l = loudness(strength);
      cue(CollapseSounds.IMPACT, Layer.NEAR, at, l, p, 0, running);
      cue(CollapseSounds.CRUMBLE_BURST, Layer.NEAR, at, 0.8F * l, p, 3, running);
      cue(CollapseSounds.RUMBLE, Layer.RUMBLE, at, 0.45F * l, p, 0, running);
   }

   static void groan(Vec3 at, float strength, double rnd, int running) {
      cue(CollapseSounds.METAL_GROAN, Layer.MID, at, 0.85F * loudness(strength), pitch(strength, rnd), 0, running);
   }

   static void crack(Vec3 at, float strength, double rnd, int running) {
      cue(CollapseSounds.CRACK, Layer.MID, at, 0.8F * loudness(strength), pitch(strength, rnd) + 0.05F, 0, running);
   }

   static void shed(Vec3 at, float strength, double rnd, int running) {
      cue(CollapseSounds.FACADE_SHED, Layer.NEAR, at, 0.6F * loudness(strength), pitch(strength, rnd) + 0.08F, 0, running);
   }

   static void collapse(Vec3 at, float strength, double rnd, int running) {
      cue(CollapseSounds.ROAR, Layer.MID, at, loudness(strength), pitch(strength, rnd), 0, running);
   }

   static void ground(Vec3 at, float strength, double rnd, int running) {
      float p = pitch(strength, rnd), l = loudness(strength);
      cue(CollapseSounds.GROUND_IMPACT, Layer.MID, at, l, p, 0, running);
      cue(CollapseSounds.RUMBLE, Layer.RUMBLE, at, 0.9F * l, p * 0.95F, 0, running);
      cue(CollapseSounds.DEBRIS_SCATTER, Layer.NEAR, at, 0.85F * l, p, 4, running);
      cue(CollapseSounds.DISTANT, Layer.DISTANT, at, l, p, 0, running);
      cue(CollapseSounds.TAIL, Layer.NEAR, at, 0.7F * l, p, 22, running);
      cue(CollapseSounds.DUST, Layer.NEAR, at, 0.55F * l, p, 46, running);
   }

   /** A fragment landing on its own (small, rate limited by the scene). */
   static void piece(Vec3 at, float size, double rnd, int running) {
      cue(CollapseSounds.FACADE_SHED, Layer.NEAR, at, Mth.clamp(0.25F + 0.12F * size, 0.25F, 0.6F), (float)(0.85 + rnd * 0.25), 0, running);
   }
}
