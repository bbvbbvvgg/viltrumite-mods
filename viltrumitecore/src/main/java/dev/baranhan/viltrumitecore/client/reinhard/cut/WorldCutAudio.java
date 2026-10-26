package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Normal World Cut audio only: one looping hum per live cast plus one
 * short pulse voice. The diagonal cut keeps its own {@link CutFinishSound}.
 *
 * The BLAST is the owner-confirmed strike on Theresia (TEMPORARY test asset,
 * REINHARD OVERHAUL item 12): one stereo voice per cast, started on the elapsed
 * tick of the server BLAST, replacing the old world_cut_impact hit. The same call
 * starts the heavy explosion under it (mono, at the nearest point of the cut, see
 * {@link #EXPLOSION_GAIN}); {@link WorldCutJolt} kicks the camera on that tick.
 * While the voice's loud body plays the hum and the pulse voice step back
 * ({@link #strikeDuck}).
 *
 * The hum is a 1.99 s seamless loop derived from the same recording as the
 * old tail (forward + time reverse, see tools/sfx/reinhard_worldcut.py).
 * It starts at BLAST, follows the scene fade after DONE and stops itself.
 * At most one hum exists per cast and at most one pulse exists overall.
 */
final class WorldCutAudio {
   private static final Map<UUID, Hum> HUM = new HashMap<>();
   /** Elapsed scene tick at which each cast's strike voice started. */
   private static final Map<UUID, Double> STRIKE = new HashMap<>();
   private static Pulse pulse;
   private static SoundInstance strike;
   private static SoundInstance explosion;
   private static long lastPulse = Long.MIN_VALUE;
   private static long lastBlade = Long.MIN_VALUE;
   private static long lastImpact = Long.MIN_VALUE;
   /** No second pulse within this many client ticks. */
   private static final int PULSE_COOLDOWN = 20;
   /**
    * Hum gain at the crest of a pulse swell. The sound engine clamps a voice
    * to 1.0, so the crest sits there and the resting level is
    * {@code HUM_PEAK / (1 + SWELL)}: the full +45 % swell stays audible
    * instead of being clipped away.
    */
   static final float HUM_PEAK = 1.0F;
   static final float SWELL = .45F;
   private static final float WOBBLE = .10F;
   private static final float PULSE_GAIN = 1.0F;
   /**
    * Ticks of the 1.35 s {@code world_cut_pulse} asset. Its release is baked
    * into the PCM by tools/sfx/reinhard_worldcut.py, so the voice keeps a
    * constant gain and only a safety stop at the asset end.
    */
   static final int PULSE_TICKS = 27;
   /** Hum and pulse level under the loud body of the strike voice. */
   static final float STRIKE_DUCK = .45F;
   /** The strike voice: about 4.5 s of loud body, then an even decay to its end at 8.76 s. */
   static final int STRIKE_BODY_TICKS = 90;
   static final int STRIKE_RECOVER_TICKS = 150;
   /** Strike volume: full within this distance of the cut, then down to {@link #STRIKE_FAR_GAIN} at the view range. */
   static final double STRIKE_NEAR = 32;
   static final float STRIKE_FAR_GAIN = .25F;
   /**
    * Explosion volume. Positional mono with the linear 320-block attenuation of its sounds.json entry, so a
    * listener at the cut hears it at this gain and it fades out at the view range. A step under the voice:
    * the boom sits below 300 Hz and the voice above 1 kHz, and the step keeps headroom where the two first
    * hits overlap.
    */
   static final float EXPLOSION_GAIN = .85F;

   private WorldCutAudio() {
   }

   /** {@code pulse} is the scene's admitted envelope (0 between beats and for a beat joined too late). */
   static void update(UUID cast, Vec3 at, double elapsed, float fade, boolean inRange, float pulse) {
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
      hum.aim(at, humGain(fade, pulse) * duck(cast, elapsed));
   }

   /** Hum / pulse multiplier for a cast at elapsed tick t: 1 without a strike voice. */
   static float duck(UUID cast, double elapsed) {
      Double at = STRIKE.get(cast);
      return at == null ? 1.0F : strikeDuck(elapsed - at);
   }

   /** Pure: the duck under the strike voice, {@code since} ticks after it started (negative = not started). */
   static float strikeDuck(double since) {
      if (!(since >= 0) || since >= STRIKE_RECOVER_TICKS) {
         return 1.0F;
      }
      if (since <= STRIKE_BODY_TICKS) {
         return STRIKE_DUCK;
      }
      return STRIKE_DUCK + (1.0F - STRIKE_DUCK) * (float)((since - STRIKE_BODY_TICKS) / (STRIKE_RECOVER_TICKS - STRIKE_BODY_TICKS));
   }

   /** Pure: strike voice volume for a listener {@code distance} blocks from the cut (stereo, so not positional). */
   static float strikeVolume(double distance) {
      if (!(distance > STRIKE_NEAR)) {
         return 1.0F;
      }
      double far = Math.min(1.0, (distance - STRIKE_NEAR) / (ReinhardRules.WORLD_CUT_VIEW_RANGE - STRIKE_NEAR));
      return (float)(1.0 - (1.0 - STRIKE_FAR_GAIN) * far);
   }

   /** Hum target: rests at HUM_PEAK / (1 + SWELL), reaches HUM_PEAK on a full pulse, never above it. */
   static float humGain(float fade, float pulse) {
      float swell = 1.0F + SWELL * Mth.clamp(pulse, 0.0F, 1.0F);
      return HUM_PEAK * Mth.clamp(fade, 0.0F, 1.0F) * swell / (1.0F + SWELL);
   }

   /** One hum tick: slow wobble around the target, eased, capped at the engine ceiling. */
   static float humStep(float volume, float target, int age) {
      // A slow wobble at a period that does not divide the 2 s loop, so the
      // repeat pattern is longer than one cast.
      float wobble = 1.0F + WOBBLE * Mth.sin(age * .13F);
      return volume + (Math.min(HUM_PEAK, target * wobble) - volume) * .22F;
   }

   /** Pulse voice gain: the baked release is the only decay; no second runtime envelope. */
   static float pulseVolume(int age) {
      return age < PULSE_TICKS ? PULSE_GAIN : 0.0F;
   }

   /** One pulse voice total; a new pulse stops the previous one instead of layering. */
   static void pulse(Vec3 at, long now) {
      pulse(at, now, 1.0F);
   }

   /** {@code gain} is the strike duck of the cast that admitted this beat. */
   static void pulse(Vec3 at, long now, float gain) {
      if (lastPulse != Long.MIN_VALUE && now - lastPulse < PULSE_COOLDOWN) {
         return;
      }
      lastPulse = now;
      if (pulse != null) {
         pulse.stopNow();
      }
      pulse = new Pulse(ReinhardSounds.WORLD_CUT_PULSE.get(), at, gain);
      Minecraft.getInstance().getSoundManager().play(pulse);
   }

   /** One blade-presentation ring at the swing; bounded globally like the pulse. */
   static void blade(Vec3 at, long now) {
      if (lastBlade != Long.MIN_VALUE && now - lastBlade < PULSE_COOLDOWN) {
         return;
      }
      lastBlade = now;
      play(ReinhardSounds.WORLD_CUT_BLADE.get(), at, .5F, 1.15F);
   }

   /**
    * The strike voice and the explosion on the first BLAST, once per cast. The voice file is stereo, which the
    * sound engine never positions, so it plays head-relative and the distance to the cut sets its volume; the
    * explosion is mono and positioned at {@code at}, the nearest point of the cut. The scene's admitImpact and
    * this cooldown stop a second start from a repeated packet or a late join; world_cut_impact stays registered
    * as the licensed fallback. True when the pair started.
    */
   static boolean strike(UUID cast, Vec3 at, double distance, double elapsed, long now) {
      if (lastImpact != Long.MIN_VALUE && now - lastImpact < PULSE_COOLDOWN) {
         return false;
      }
      lastImpact = now;
      var sounds = Minecraft.getInstance().getSoundManager();
      if (strike != null) {
         sounds.stop(strike);
      }
      if (explosion != null) {
         sounds.stop(explosion);
      }
      STRIKE.put(cast, elapsed);
      strike = new SimpleSoundInstance(ReinhardSounds.WORLD_CUT_STRIKE_VOICE.get().getLocation(), SoundSource.PLAYERS,
         strikeVolume(distance), 1.0F, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
      explosion = new SimpleSoundInstance(ReinhardSounds.WORLD_CUT_EXPLOSION.get().getLocation(), SoundSource.PLAYERS,
         EXPLOSION_GAIN, 1.0F, RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
      sounds.play(strike);
      sounds.play(explosion);
      return true;
   }

   private static void play(SoundEvent event, Vec3 at, float volume, float pitch) {
      Minecraft.getInstance().level.playLocalSound(at.x, at.y, at.z, event, SoundSource.PLAYERS, volume, pitch, false);
   }

   static void stop(UUID cast) {
      Hum hum = HUM.get(cast);
      if (hum != null) {
         hum.release();
      }
      STRIKE.remove(cast);
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
      STRIKE.clear();
      if (pulse != null) {
         pulse.stopNow();
         pulse = null;
      }
      if (strike != null) {
         Minecraft.getInstance().getSoundManager().stop(strike);
         strike = null;
      }
      if (explosion != null) {
         Minecraft.getInstance().getSoundManager().stop(explosion);
         explosion = null;
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
         volume = humStep(volume, target, age);
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
      private final float gain;
      private int age;

      Pulse(SoundEvent event, Vec3 at, float gain) {
         super(event, SoundSource.PLAYERS, RandomSource.create());
         attenuation = Attenuation.LINEAR;
         x = at.x;
         y = at.y;
         z = at.z;
         this.gain = Mth.clamp(gain, 0.0F, 1.0F);
         volume = pulseVolume(0) * this.gain;
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
         volume = pulseVolume(age) * gain;
         if (age >= PULSE_TICKS) {
            stop();
         }
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
