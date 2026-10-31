package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * The client half of the Final Punch audio on the scene clock (cue sheet:
 * tools/sfx/reinhard_final_punch.py, ticks: {@link FinalPunchCues}). Loops (score, wind, hum)
 * are tickable instances with their own volume curves and a real stop; one-shots are scheduled
 * cues. At the silence the score, wind, hum and pressure stop dead and every other sound of the
 * world is ducked to almost nothing (SoundDuck, the scene's own sounds exempt) until the
 * impact, which the server plays (impact + jaw / clang) so it is never doubled here. Every sound
 * is one of the 13 prepared {@code reinhard.final_punch_*} events; no vanilla stand-ins.
 */
public final class FinalPunchAudio {
   /** The world's sounds during the silence (fraction of their volume). */
   static final float SILENCE_LEVEL = 0.03F;
   /** Listeners farther than this from the caster do not get the silence duck. */
   static final double DUCK_RANGE = 48.0;

   /** The scene's sound handles (owned by its Scene). */
   static final class Voices {
      Loop music;
      Loop wind;
      Loop hum;
      final List<SoundInstance> shots = new ArrayList<>();
      SoundInstance crackle;
      boolean ducked;
   }

   private FinalPunchAudio() {
   }

   /** Ids of every Final Punch sound (exempt from the silence duck). */
   static Set<ResourceLocation> own() {
      Set<ResourceLocation> ids = new HashSet<>();
      for (RegistryObject<SoundEvent> sound : List.of(ReinhardSounds.FINAL_PUNCH_MUSIC, ReinhardSounds.FINAL_PUNCH_WIND, ReinhardSounds.FINAL_PUNCH_HUM,
         ReinhardSounds.FINAL_PUNCH_IGNITE, ReinhardSounds.FINAL_PUNCH_SPARK, ReinhardSounds.FINAL_PUNCH_RING, ReinhardSounds.FINAL_PUNCH_PRESSURE,
         ReinhardSounds.FINAL_PUNCH_CRACKLE, ReinhardSounds.FINAL_PUNCH_WHOOSH, ReinhardSounds.FINAL_PUNCH_IMPACT, ReinhardSounds.FINAL_PUNCH_JAW,
         ReinhardSounds.FINAL_PUNCH_CLANG, ReinhardSounds.FINAL_PUNCH_SMOKE)) {
         ids.add(sound.getId());
      }
      return ids;
   }

   /** The cue name of {@link FinalPunchCues.Cue#sound()} to its registered event. */
   static SoundEvent event(String name) {
      return switch (name) {
         case "music" -> ReinhardSounds.FINAL_PUNCH_MUSIC.get();
         case "wind" -> ReinhardSounds.FINAL_PUNCH_WIND.get();
         case "hum" -> ReinhardSounds.FINAL_PUNCH_HUM.get();
         case "ignite" -> ReinhardSounds.FINAL_PUNCH_IGNITE.get();
         case "spark" -> ReinhardSounds.FINAL_PUNCH_SPARK.get();
         case "ring" -> ReinhardSounds.FINAL_PUNCH_RING.get();
         case "pressure" -> ReinhardSounds.FINAL_PUNCH_PRESSURE.get();
         case "crackle" -> ReinhardSounds.FINAL_PUNCH_CRACKLE.get();
         case "whoosh" -> ReinhardSounds.FINAL_PUNCH_WHOOSH.get();
         case "smoke" -> ReinhardSounds.FINAL_PUNCH_SMOKE.get();
         default -> throw new IllegalArgumentException("unknown Final Punch cue " + name);
      };
   }

   static void start(FinalPunchClient.Scene scene) {
      // Nothing sounds on tick 0: the score enters on tick 8 (tick()).
   }

   static void tick(FinalPunchClient.Scene scene) {
      int t = scene.ticks;
      Voices v = scene.audio;
      Minecraft mc = Minecraft.getInstance();
      if (t == 8 && v.music == null) v.music = play(new Loop(ReinhardSounds.FINAL_PUNCH_MUSIC.get(), scene, false, FinalPunchCues::music, x -> 1.0, true));
      if (t == FinalPunchRules.WINDUP_START && v.wind == null) v.wind = play(new Loop(ReinhardSounds.FINAL_PUNCH_WIND.get(), scene, true, FinalPunchCues::wind, FinalPunchCues::windPitch, true));
      if (t == FinalPunchRules.RINGS && v.hum == null) v.hum = play(new Loop(ReinhardSounds.FINAL_PUNCH_HUM.get(), scene, true, FinalPunchCues::hum, FinalPunchCues::humPitch, false));
      if (t == FinalPunchRules.SILENCE) {
         // The silence: the score, wind, hum and pressure stop dead; the world is ducked.
         cut(v);
         if (near(scene)) {
            SoundDuck.INSTANCE.duck(scene, SoundSource.MASTER, SILENCE_LEVEL, 3, own());
            v.ducked = true;
         }
      }

      for (FinalPunchCues.Cue cue : FinalPunchCues.cuesAt(t)) {
         Vec3 at = cue.atFist() ? scene.fist(0.0F) : chest(scene);
         SoundInstance shot = new SimpleSoundInstance(event(cue.sound()), SoundSource.PLAYERS, cue.volume(), cue.pitch(), RandomSource.create(scene.seed + t), at.x, at.y, at.z);
         mc.getSoundManager().play(shot);
         if (cue.sound().equals("crackle")) v.crackle = shot;
         else if (t < FinalPunchRules.SILENCE) v.shots.add(shot);
      }
   }

   /** The impact (server sound): the world comes back at once, the silence's crackle ends. */
   static void impact(FinalPunchClient.Scene scene) {
      Voices v = scene.audio;
      cut(v);
      if (v.crackle != null) Minecraft.getInstance().getSoundManager().stop(v.crackle);
      v.crackle = null;
      if (v.ducked) SoundDuck.INSTANCE.release(scene, 1);
      v.ducked = false;
   }

   /** Every sound of the scene stops, the duck is released (cancel / finish). */
   static void stop(FinalPunchClient.Scene scene) {
      Voices v = scene.audio;
      cut(v);
      if (v.crackle != null) Minecraft.getInstance().getSoundManager().stop(v.crackle);
      v.crackle = null;
      SoundDuck.INSTANCE.release(scene, 2);
      v.ducked = false;
   }

   static void releaseAll() {
      // Each scene releases its own duck in stop(); nothing global is held here.
   }

   private static void cut(Voices v) {
      Minecraft mc = Minecraft.getInstance();
      for (Loop loop : new Loop[]{v.music, v.wind, v.hum}) {
         if (loop != null) {
            loop.cut();
            mc.getSoundManager().stop(loop);
         }
      }
      for (SoundInstance shot : v.shots) mc.getSoundManager().stop(shot);
      v.shots.clear();
   }

   private static Loop play(Loop loop) {
      Minecraft.getInstance().getSoundManager().play(loop);
      return loop;
   }

   private static boolean near(FinalPunchClient.Scene scene) {
      Minecraft mc = Minecraft.getInstance();
      return scene.isLocalCaster() || mc.player != null && mc.player.position().distanceToSqr(scene.feet()) < DUCK_RANGE * DUCK_RANGE
         || mc.player != null && mc.player.getId() == scene.targetId;
   }

   static Vec3 chest(FinalPunchClient.Scene scene) {
      Entity caster = scene.caster();
      return caster != null ? caster.position().add(0, 1.2, 0) : scene.feet().add(0, 1.2, 0);
   }

   /** A scene loop: follows the caster, its volume is a curve of the scene clock, a real stop. */
   static final class Loop extends AbstractTickableSoundInstance {
      private final FinalPunchClient.Scene scene;
      private final DoubleUnaryOperator gain;
      private final DoubleUnaryOperator tone;
      private boolean cut;

      Loop(SoundEvent event, FinalPunchClient.Scene scene, boolean loop, DoubleUnaryOperator gain, DoubleUnaryOperator tone, boolean ambientForCaster) {
         super(event, SoundSource.PLAYERS, RandomSource.create(scene.seed ^ event.getLocation().hashCode()));
         this.scene = scene;
         this.gain = gain;
         this.tone = tone;
         this.looping = loop;
         this.delay = 0;
         if (ambientForCaster && scene.isLocalCaster()) {
            // The score and the wind surround the caster's own view, wherever the scene camera stands.
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
         } else {
            Vec3 at = chest(scene);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
         }
         this.volume = Math.max(0.001F, (float) gain.applyAsDouble(scene.ticks));
         this.pitch = (float) tone.applyAsDouble(scene.ticks);
      }

      void cut() {
         this.cut = true;
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         if (this.cut || this.scene.cancelled() || this.scene.ticks >= FinalPunchRules.SILENCE) {
            this.stop();
            return;
         }
         if (!this.relative) {
            Vec3 at = chest(this.scene);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
         }
         double t = this.scene.ticks;
         this.volume = Math.max(0.0F, Math.min(1.5F, (float) this.gain.applyAsDouble(t)));
         this.pitch = Math.max(0.5F, Math.min(2.0F, (float) this.tone.applyAsDouble(t)));
      }
   }
}
