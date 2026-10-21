package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Normal World Cut audio only: one looping hum per live cast plus one
 * short pulse voice. The diagonal cut keeps its own {@link CutFinishSound}.
 *
 * The hum is a 1.99 s seamless loop derived from the same recording as the
 * old tail (forward + time reverse, see tools/sfx/reinhard_worldcut.py).
 * It starts at BLAST, follows the scene fade after DONE and stops itself.
 * At most one hum exists per cast and at most one pulse exists overall.
 */
final class WorldCutAudio {
   private static final Map<UUID, Hum> HUM = new HashMap<>();
   private static Pulse pulse;
   private static long lastPulse = Long.MIN_VALUE;
   private static long lastBlade = Long.MIN_VALUE;
   private static long lastImpact = Long.MIN_VALUE;
   /** No second pulse within this many client ticks. */
   private static final int PULSE_COOLDOWN = 20;
   /** Linear gain of the loop before scene fade and pulse swell. */
   private static final float HUM_GAIN = 1.0F;
   private static final float PULSE_GAIN = 1.0F;
   private static final float SWELL = .45F;

   private WorldCutAudio() {
   }

   static void update(UUID cast, Vec3 at, double elapsed, double acceptedUntil, float fade, boolean inRange) {
      Hum hum = HUM.get(cast);
      if (!inRange || fade <= .002F || elapsed < ReinhardRules.WORLD_CUT_BLAST) {
         if (hum != null) {
            hum.release();
         }
         return;
      }
      if (hum == null || hum.done()) {
         hum = new Hum(ReinhardSounds.WORLD_CUT_HUM.get(), at);
         HUM.put(cast, hum);
         Minecraft.getInstance().getSoundManager().play(hum);
      }
      float swell = 1.0F + SWELL * WorldCutPulse.strength(elapsed, acceptedUntil);
      hum.aim(at, HUM_GAIN * fade * swell);
   }

   /** One pulse voice total; a new pulse stops the previous one instead of layering. */
   static void pulse(Vec3 at, long now) {
      if (lastPulse != Long.MIN_VALUE && now - lastPulse < PULSE_COOLDOWN) {
         return;
      }
      lastPulse = now;
      if (pulse != null) {
         pulse.stopNow();
      }
      pulse = new Pulse(ReinhardSounds.WORLD_CUT_PULSE.get(), at, PULSE_GAIN);
      Minecraft.getInstance().getSoundManager().play(pulse);
   }

   /** One blade-presentation ring at the swing; bounded globally like the pulse. */
   static void blade(Vec3 at, long now) {
      if (lastBlade != Long.MIN_VALUE && now - lastBlade < PULSE_COOLDOWN) {
         return;
      }
      lastBlade = now;
      play(ReinhardSounds.SHEATH_STRIKE.get(), at, .5F, 1.15F);
   }

   /** One short explosion on the first BLAST. */
   static void impact(Vec3 at, long now) {
      if (lastImpact != Long.MIN_VALUE && now - lastImpact < PULSE_COOLDOWN) {
         return;
      }
      lastImpact = now;
      play(ReinhardSounds.WORLD_CUT_IMPACT.get(), at, .85F, 1.0F);
   }

   private static void play(SoundEvent event, Vec3 at, float volume, float pitch) {
      Minecraft.getInstance().level.playLocalSound(at.x, at.y, at.z, event, SoundSource.PLAYERS, volume, pitch, false);
   }

   static void stop(UUID cast) {
      Hum hum = HUM.get(cast);
      if (hum != null) {
         hum.release();
      }
   }

   /** Drops finished voices; a released hum needs ~10 ticks to fade out. */
   static void prune() {
      HUM.values().removeIf(Hum::done);
      if (pulse != null && pulse.done()) {
         pulse = null;
      }
   }

   static void clear() {
      for (Hum hum : HUM.values()) {
         hum.stopNow();
      }
      HUM.clear();
      if (pulse != null) {
         pulse.stopNow();
         pulse = null;
      }
      lastPulse = Long.MIN_VALUE;
      lastBlade = Long.MIN_VALUE;
      lastImpact = Long.MIN_VALUE;
   }

   private static final class Hum extends AbstractTickableSoundInstance {
      private float target = .001F;
      private int age;
      private boolean releasing = true;

      Hum(SoundEvent event, Vec3 at) {
         super(event, SoundSource.PLAYERS, RandomSource.create());
         looping = true;
         attenuation = Attenuation.LINEAR;
         x = at.x;
         y = at.y;
         z = at.z;
         volume = .001F;
      }

      void aim(Vec3 at, float gain) {
         x = at.x;
         y = at.y;
         z = at.z;
         target = Math.max(0.0F, Math.min(1.0F, gain));
         releasing = target <= .002F;
      }

      void release() {
         target = 0.0F;
         releasing = true;
      }

      void stopNow() {
         stop();
      }

      boolean done() {
         return isStopped();
      }

      @Override
      public void tick() {
         age++;
         // A slow wobble at a period that does not divide the 2 s loop, so the
         // repeat pattern is longer than one cast.
         float wobble = 1.0F + .10F * Mth.sin(age * .13F);
         volume += (target * wobble - volume) * .22F;
         if (releasing && volume <= .002F) {
            stop();
         }
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }

   private static final class Pulse extends AbstractTickableSoundInstance {
      private static final int LENGTH = WorldCutPulse.LENGTH;
      private final float gain;
      private int age;

      Pulse(SoundEvent event, Vec3 at, float gain) {
         super(event, SoundSource.PLAYERS, RandomSource.create());
         attenuation = Attenuation.LINEAR;
         x = at.x;
         y = at.y;
         z = at.z;
         this.gain = gain;
         volume = gain;
      }

      void stopNow() {
         stop();
      }

      boolean done() {
         return isStopped();
      }

      @Override
      public void tick() {
         age++;
         float left = Math.max(0.0F, 1.0F - age / (float)LENGTH);
         volume = gain * left * left;
         if (age >= LENGTH) {
            stop();
         }
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
