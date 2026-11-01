package dev.baranhan.viltrumitecore.client.city.power;

import dev.baranhan.viltrumitecore.city.power.CityPowerSounds;
import dev.baranhan.viltrumitecore.city.power.PowerRules;
import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * The sound of the city emergency and the failing grid, all client side. Sirens: the {@link #MAX_SIRENS} nearest
 * masts play the stage's siren (warning, attack, critical), each as a near and a far (filtered, echoing) layer
 * crossfaded by distance with {@link PowerRules#sirenLevel} / {@link PowerRules#sirenNear}; a stage change fades the
 * old siren out under the new one, the all-clear plays once. Klaxons around critical foci, fire-panel beeps
 * indoors during a blackout, a low disaster bed while the emergency lasts, sparks hissing at blown transformers
 * and the hum stuttering under flickering lights. One-shots (transformer blows, cascades, arcs, breakers) are
 * picked by distance, delayed by the speed of sound and rate limited, so a big disaster never stacks dozens of
 * voices. Attenuation is manual (the source still pans). No vanilla sounds.
 */
final class CityPowerAudio {
   static final int MAX_SIRENS = 3;
   static final double SIREN_RANGE = 900.0;
   static final double KLAXON_RANGE = 110.0;
   static final double SPARK_RANGE = 36.0;
   static final float FADE_UP = 0.04F;
   static final float FADE_DOWN = 0.05F;

   record Target(float volume, float pitch, Vec3 at) {
   }

   /** One siren mast playing one stage: near and far layer. */
   static final class Siren {
      final int site;
      final AlarmStage stage;
      final float detune;
      Vec3 at;
      float near;
      float far;
      boolean wanted = true;
      Loop nearLoop;
      Loop farLoop;

      Siren(int site, AlarmStage stage, Vec3 at) {
         this.site = site;
         this.stage = stage;
         this.at = at;
         this.detune = 0.975F + (float)PowerRules.unit(site * 7919L + stage.ordinal()) * 0.05F;
      }
   }

   private static final List<Siren> SIRENS = new ArrayList<>();
   private static Loop klaxon;
   private static Vec3 klaxonAt = Vec3.ZERO;
   private static float klaxonVolume;
   private static Loop beeps;
   private static float beepsVolume;
   private static Loop bed;
   private static float bedVolume;
   private static final Loop[] SPARKS = new Loop[2];
   private static final Vec3[] SPARK_AT = {Vec3.ZERO, Vec3.ZERO};
   private static final float[] SPARK_VOLUME = new float[2];
   private static Loop hum;
   private static float humVolume;
   private static Vec3 me = Vec3.ZERO;
   private static int clock;
   private static int lastBlow = -100;
   private static int blowsInWindow;
   private static int lastArc = -100;
   private static int arcsInWindow;
   private static int lastCascade = -100;
   private static int lastBreaker = -100;
   private static boolean indoors;

   private CityPowerAudio() {
   }

   static void clear() {
      for (Siren s : SIRENS) {
         stop(s.nearLoop);
         stop(s.farLoop);
      }
      SIRENS.clear();
      stop(klaxon);
      stop(beeps);
      stop(bed);
      stop(hum);
      for (int i = 0; i < SPARKS.length; i++) {
         stop(SPARKS[i]);
         SPARKS[i] = null;
      }
      klaxon = beeps = bed = hum = null;
      klaxonVolume = beepsVolume = bedVolume = humVolume = 0.0F;
   }

   private static void stop(Loop l) {
      if (l != null) {
         Minecraft.getInstance().getSoundManager().stop(l);
      }
   }

   // ------------------------------------------------------------------ state changes

   static void onStage(AlarmStage prev, AlarmStage next) {
      if (next == AlarmStage.ALL_CLEAR) {
         // the long steady tone winding down, from the two nearest masts
         List<Vec3> near = nearestSites(2);
         for (Vec3 s : near) {
            double d = s.distanceTo(me);
            once(CityPowerSounds.SIREN_ALL_CLEAR, s, PowerRules.sirenLevel(d) * 0.9F, 1.0F, PowerRules.soundDelay(d));
         }
      }
   }

   static void onOwnCell(int before, int after) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      Vec3 at = mc.player.position().add(0, 2.5, 0);
      if (!CityPowerClient.dark(before) && CityPowerClient.dark(after)) {
         once(CityPowerSounds.POWER_DOWN, at, 0.75F, 1.0F, 0);
         lastBreaker = clock;
      } else if (CityPowerClient.dark(before) && !CityPowerClient.dark(after)) {
         once(CityPowerSounds.POWER_UP, at, 0.75F, 1.0F, 0);
         lastBreaker = clock;
      } else if (after == 1 || after == 2) {
         once(CityPowerSounds.SURGE, at, 0.6F, 0.95F + (float)Math.random() * 0.1F, 0);
      }
   }

   /** Cells around the listener going dark or coming back: the nearest of each, if in earshot (at most every second). */
   static void onCells(Vec3 listener, List<Vec3> down, List<Vec3> up) {
      if (clock - lastBreaker < 20) {
         return;
      }
      for (int pass = 0; pass < 2; pass++) {
         List<Vec3> list = pass == 0 ? down : up;
         Vec3 best = null;
         double bestD = 90.0;
         for (Vec3 v : list) {
            double d = Math.hypot(v.x - listener.x, v.z - listener.z);
            if (d > 20.0 && d < bestD) {
               bestD = d;
               best = v;
            }
         }
         if (best != null) {
            once(pass == 0 ? CityPowerSounds.POWER_DOWN : CityPowerSounds.POWER_UP, best, 0.55F * (float)(1.0 - bestD / 90.0), 0.9F,
               PowerRules.soundDelay(bestD));
            lastBreaker = clock;
         }
      }
   }

   // ------------------------------------------------------------------ cues

   static void impact(Vec3 at, double d, float power, long seed) {
      if (d < 80.0) {
         arc(at, d * 0.6, seed);
      }
      if (d > 140.0 && d < 700.0 && power > 0.5F) {
         once(CityPowerSounds.DISTANT_DESTRUCTION, at, curve(d, 260.0) * 0.8F, 1.0F, PowerRules.soundDelay(d));
      }
   }

   static void blow(Vec3 at, double d, long seed) {
      if (clock - lastBlow > 10) {
         blowsInWindow = 0;
      }
      if (blowsInWindow >= 2 || d > 320.0) {
         return;
      }
      blowsInWindow++;
      lastBlow = clock;
      int delay = PowerRules.soundDelay(d);
      float pitch = 0.94F + (float)PowerRules.unit(seed) * 0.12F;
      if (d < 60.0) {
         once(CityPowerSounds.TRANSFORMER_BLOW, at, curve(d, 26.0), pitch, delay);
         once(CityPowerSounds.LOW_IMPACT, at, curve(d, 30.0) * 0.6F, pitch, delay);
      } else {
         once(CityPowerSounds.TRANSFORMER_BLOW_FAR, at, curve(d, 80.0), pitch, delay);
      }
   }

   static void cascade(Vec3 at, double d, long seed) {
      if (clock - lastCascade < 20 || d > 800.0) {
         return;
      }
      lastCascade = clock;
      int delay = PowerRules.soundDelay(d);
      float pitch = 0.95F + (float)PowerRules.unit(seed) * 0.1F;
      if (d < 130.0) {
         once(CityPowerSounds.CASCADE, at, curve(d, 60.0), pitch, delay);
         once(CityPowerSounds.METAL_GROAN, at, curve(d, 40.0) * 0.8F, pitch, delay + 30);
      } else if (d < 420.0) {
         once(CityPowerSounds.TRANSFORMER_BLOW_FAR, at, curve(d, 120.0), pitch * 0.9F, delay);
         once(CityPowerSounds.LOW_IMPACT, at, curve(d, 140.0) * 0.8F, pitch * 0.9F, delay);
      } else {
         once(CityPowerSounds.DISTANT_DESTRUCTION, at, curve(d, 260.0) * 0.8F, pitch, delay);
      }
   }

   static void arc(Vec3 at, double d, long seed) {
      if (clock - lastArc > 20) {
         arcsInWindow = 0;
      }
      if (arcsInWindow >= 3 || d > 56.0) {
         return;
      }
      arcsInWindow++;
      lastArc = clock;
      once(CityPowerSounds.ARC, at, (float)(1.0 - d / 56.0) * 0.85F, 0.9F + (float)PowerRules.unit(seed) * 0.25F, PowerRules.soundDelay(d));
   }

   /** Loudness of a one-shot {@code d} blocks away for a reference distance {@code ref} (half loudness there). */
   static float curve(double d, double ref) {
      return (float)Mth.clamp(1.0 / (1.0 + Math.pow(Math.max(0.0, d) / ref, 1.4)), 0.0, 1.0);
   }

   private static void once(RegistryObject<SoundEvent> event, Vec3 at, float volume, float pitch, int delay) {
      if (volume < 0.02F) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      SimpleSoundInstance sound = new SimpleSoundInstance(event.get().getLocation(), SoundSource.AMBIENT, Mth.clamp(volume, 0.0F, 1.0F), pitch,
         SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
      if (delay > 0) {
         mc.getSoundManager().playDelayed(sound, delay);
      } else {
         mc.getSoundManager().play(sound);
      }
   }

   // ------------------------------------------------------------------ loops

   private static List<Vec3> nearestSites(int n) {
      List<Vec3> all = new ArrayList<>(CityPowerClient.sites);
      all.sort(Comparator.comparingDouble(v -> v.distanceToSqr(me)));
      return all.size() > n ? all.subList(0, n) : all;
   }

   static void tick(Minecraft mc, Vec3 listener) {
      clock++;
      me = listener;
      AlarmStage stage = CityPowerClient.stage;
      boolean sounding = stage == AlarmStage.WARNING || stage == AlarmStage.ACTIVE || stage == AlarmStage.CRITICAL;
      // sirens: the nearest masts play this stage; everything else fades out and stops itself
      for (Siren s : SIRENS) {
         s.wanted = false;
      }
      if (sounding) {
         List<Vec3> sites = CityPowerClient.sites;
         Integer[] order = new Integer[sites.size()];
         for (int i = 0; i < order.length; i++) {
            order[i] = i;
         }
         java.util.Arrays.sort(order, Comparator.comparingDouble(i -> sites.get(i).distanceToSqr(listener)));
         float stageGain = stage == AlarmStage.WARNING ? 0.8F : 1.0F;
         for (int k = 0; k < Math.min(MAX_SIRENS, order.length); k++) {
            int site = order[k];
            Vec3 at = sites.get(site);
            double d = at.distanceTo(listener);
            if (d > SIREN_RANGE) {
               continue;
            }
            Siren voice = null;
            for (Siren s : SIRENS) {
               if (s.site == site && s.stage == stage) {
                  voice = s;
               }
            }
            if (voice == null) {
               voice = new Siren(site, stage, at);
               SIRENS.add(voice);
            }
            voice.wanted = true;
            voice.at = at;
            // later masts a little quieter: the nearest one leads, the others fill the city
            float rank = k == 0 ? 1.0F : k == 1 ? 0.7F : 0.5F;
            float level = PowerRules.sirenLevel(d) * stageGain * rank;
            float nearShare = PowerRules.sirenNear(d);
            voice.near = level * nearShare;
            voice.far = level * (1.0F - nearShare) * 1.15F;
         }
      }
      for (Iterator<Siren> it = SIRENS.iterator(); it.hasNext();) {
         Siren s = it.next();
         if (!s.wanted) {
            s.near = 0.0F;
            s.far = 0.0F;
         }
         if (s.near > 0.01F && dead(s.nearLoop)) {
            s.nearLoop = start(mc, sirenEvent(s.stage, false), SoundSource.AMBIENT, () -> new Target(s.near, s.detune, s.at));
         }
         if (s.far > 0.01F && dead(s.farLoop)) {
            s.farLoop = start(mc, sirenEvent(s.stage, true), SoundSource.AMBIENT, () -> new Target(s.far, s.detune, s.at));
         }
         if (!s.wanted && dead(s.nearLoop) && dead(s.farLoop)) {
            it.remove();
         }
      }
      // klaxon at the strongest critical focus in earshot
      klaxonVolume = 0.0F;
      if (stage == AlarmStage.CRITICAL) {
         for (int i = 0; i < CityPowerClient.foci.size() && i < CityPowerClient.fociStage.length; i++) {
            Vec3 f = CityPowerClient.foci.get(i);
            double d = f.distanceTo(listener);
            if (CityPowerClient.fociStage[i] == AlarmStage.CRITICAL.ordinal() && d < KLAXON_RANGE) {
               float v = (float)(1.0 - d / KLAXON_RANGE) * 0.75F;
               if (v > klaxonVolume) {
                  klaxonVolume = v;
                  klaxonAt = f.add(0, 6, 0);
               }
            }
         }
      }
      if (klaxonVolume > 0.01F && dead(klaxon)) {
         klaxon = start(mc, CityPowerSounds.KLAXON, SoundSource.AMBIENT, () -> new Target(klaxonVolume, 1.0F, klaxonAt));
      }
      // indoors in a blackout: the fire panel of the building beeps on its battery
      int own = CityPowerClient.stateAt(listener.x, listener.z);
      if (clock % 10 == 0 && mc.level != null) {
         indoors = !mc.level.canSeeSky(BlockPos.containing(listener.x, listener.y + 1.6, listener.z));
      }
      beepsVolume = CityPowerClient.dark(own) && indoors ? 0.4F : 0.0F;
      if (beepsVolume > 0.01F && dead(beeps)) {
         beeps = start(mc, CityPowerSounds.ALARM_BEEPS, SoundSource.AMBIENT, () -> new Target(beepsVolume, 1.0F, me.add(2.0, 1.5, 1.0)));
      }
      // the low bed of a city in trouble
      bedVolume = stage == AlarmStage.CRITICAL ? 0.55F : stage == AlarmStage.ACTIVE ? 0.4F : 0.0F;
      if (bedVolume > 0.01F && dead(bed)) {
         bed = start(mc, CityPowerSounds.DISASTER_BED, SoundSource.AMBIENT, () -> new Target(bedVolume, 1.0F, me.add(0, 8, 0)));
      }
      // blown transformers hiss: the two nearest in earshot
      List<Vec3> ts = new ArrayList<>(CityPowerClient.transformers);
      ts.sort(Comparator.comparingDouble(v -> v.distanceToSqr(listener)));
      for (int i = 0; i < SPARKS.length; i++) {
         final int slot = i;
         if (i < ts.size() && ts.get(i).distanceTo(listener) < SPARK_RANGE) {
            SPARK_AT[i] = ts.get(i);
            SPARK_VOLUME[i] = (float)Math.pow(1.0 - ts.get(i).distanceTo(listener) / SPARK_RANGE, 1.5) * 0.85F;
         } else {
            SPARK_VOLUME[i] = 0.0F;
         }
         if (SPARK_VOLUME[i] > 0.01F && dead(SPARKS[i])) {
            SPARKS[i] = start(mc, CityPowerSounds.SPARK_LOOP, SoundSource.BLOCKS, () -> new Target(SPARK_VOLUME[slot], 1.0F + slot * 0.06F, SPARK_AT[slot]));
         }
      }
      // flickering lights: the mains hum stutters with them (a voltage dip)
      humVolume = CityPowerClient.flickering(own) ? (Math.random() < 0.35 ? 0.05F : 0.42F) : 0.0F;
      if (humVolume > 0.01F && dead(hum)) {
         hum = start(mc, CityPowerSounds.LINE_HUM, SoundSource.BLOCKS, () -> new Target(humVolume, 0.9F + (float)Math.random() * 0.05F, me.add(0, 3, 0)));
      }
   }

   private static RegistryObject<SoundEvent> sirenEvent(AlarmStage stage, boolean far) {
      return switch (stage) {
         case WARNING -> far ? CityPowerSounds.SIREN_WARNING_FAR : CityPowerSounds.SIREN_WARNING;
         case CRITICAL -> far ? CityPowerSounds.SIREN_CRITICAL_FAR : CityPowerSounds.SIREN_CRITICAL;
         default -> far ? CityPowerSounds.SIREN_ATTACK_FAR : CityPowerSounds.SIREN_ATTACK;
      };
   }

   private static boolean dead(Loop l) {
      return l == null || l.isStopped() || !Minecraft.getInstance().getSoundManager().isActive(l) && clock - l.born > 40;
   }

   private static Loop start(Minecraft mc, RegistryObject<SoundEvent> event, SoundSource source, java.util.function.Supplier<Target> target) {
      Loop l = new Loop(event.get(), source, target);
      mc.getSoundManager().play(l);
      return l;
   }

   /** A loop that follows a target volume, pitch and position each tick and stops itself after fading to zero. */
   static final class Loop extends AbstractTickableSoundInstance {
      private final java.util.function.Supplier<Target> target;
      private float level;
      private int silent;
      final int born = clock;

      Loop(SoundEvent event, SoundSource source, java.util.function.Supplier<Target> target) {
         super(event, source, SoundInstance.createUnseededRandom());
         this.target = target;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.attenuation = SoundInstance.Attenuation.NONE;
         Target t = target.get();
         this.pitch = t.pitch();
         this.x = t.at().x;
         this.y = t.at().y;
         this.z = t.at().z;
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         Target t = this.target.get();
         float goal = Mth.clamp(t.volume(), 0.0F, 1.0F);
         this.level = goal > this.level ? Math.min(goal, this.level + FADE_UP) : Math.max(goal, this.level - FADE_DOWN);
         this.silent = goal <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 2) {
            this.stop();
            return;
         }
         this.volume = Math.max(0.001F, this.level);
         this.pitch += (t.pitch() - this.pitch) * 0.25F;
         this.x = t.at().x;
         this.y = t.at().y;
         this.z = t.at().z;
      }
   }
}
