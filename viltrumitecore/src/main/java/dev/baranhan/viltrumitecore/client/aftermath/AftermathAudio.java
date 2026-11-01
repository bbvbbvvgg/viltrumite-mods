package dev.baranhan.viltrumitecore.client.aftermath;

import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathRules;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathSounds;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathStage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * The soundscape of the ruins, all client side and all our own CC0 sounds (no vanilla ones, no voices).
 * <p><b>Beds</b> (loops that fade toward a target re-read every tick): the wind moaning through broken walls
 * (stronger as the ruin gets older and quieter), the low rumble of a fresh ruin settling, and the residual
 * fire of the {@link #FIRE_LOOPS} nearest burning spots.
 * <p><b>Scene director</b> (one-shots at the real places the scanner found): crumbles and pebbles from broken
 * ceilings, rocks bouncing down rubble, heavy debris impacts, now and then a part of the ruin giving way,
 * steel creaking (sometimes followed by something falling), torn wires arcing (only with power), crackles of
 * smouldering debris, explosions far off while the disaster is fresh and sirens across the city while help
 * is on the way. Every kind has a chance from the zone's channels ({@link AftermathMixer#chance}), a cooldown
 * per ruin, a range with a manual distance curve (attenuation off, the position still pans), the speed of
 * sound as delay, random pitch, and one shared voice limit so a big ruin never turns into noise.
 */
final class AftermathAudio {
   static final int FIRE_LOOPS = 2;
   static final double DIRECTOR_RANGE = 320.0;
   static final float FADE_UP = 0.03F;
   static final float FADE_DOWN = 0.05F;
   private static final int RETRY = 20;

   private static final Map<Integer, int[]> COOLDOWNS = new HashMap<>();
   private static final ArrayDeque<Integer> VOICES = new ArrayDeque<>();
   private static final List<Pending> PENDING = new ArrayList<>();
   private static Loop wind;
   private static Loop rumble;
   private static final Loop[] FIRE = new Loop[FIRE_LOOPS];
   private static float windTarget;
   private static float rumbleTarget;
   private static Vec3 windAt = Vec3.ZERO;
   private static Vec3 rumbleAt = Vec3.ZERO;
   private static final float[] FIRE_TARGET = new float[FIRE_LOOPS];
   private static final Vec3[] FIRE_AT = {Vec3.ZERO, Vec3.ZERO};
   private static int windRetry;
   private static int rumbleRetry;
   private static final int[] FIRE_RETRY = new int[FIRE_LOOPS];
   private static int clock;
   private static int played;

   private AftermathAudio() {
   }

   private record Pending(int at, int kind, ClientZone zone, ClientZone.Emitter emitter) {
   }

   static void tick(Minecraft mc, List<ClientZone> nearestFirst, Vec3 ear, RandomSource random, int now) {
      clock = now;
      beds(mc, nearestFirst, ear);
      while (!VOICES.isEmpty() && clock - VOICES.peekFirst() > AftermathMixer.VOICE_WINDOW) {
         VOICES.removeFirst();
      }

      Iterator<Pending> it = PENDING.iterator();
      while (it.hasNext()) {
         Pending p = it.next();
         if (p.at() <= clock) {
            it.remove();
            play(mc, p.kind(), p.zone(), p.emitter(), ear, random, false);
         }
      }

      for (ClientZone zn : nearestFirst) {
         if (zn.distance - zn.radius > DIRECTOR_RANGE) {
            break;
         }

         if (zn.hold > 0) {
            zn.hold--;
            continue;
         }

         int[] cd = COOLDOWNS.computeIfAbsent(zn.id, k -> new int[AftermathMixer.KINDS]);
         boolean focus = zn.focus != null;
         for (int kind = 0; kind < AftermathMixer.KINDS; kind++) {
            if (cd[kind] > 0) {
               cd[kind]--;
               continue;
            }

            float p = AftermathMixer.chance(kind, zn.mix, zn.severity, focus) * AftermathMixer.crowd(VOICES.size(), kind);
            if (p > 0.0F && random.nextFloat() < p) {
               ClientZone.Emitter e = pick(zn, kind, ear, random);
               if ((kind == AftermathMixer.SPARKS || kind == AftermathMixer.CRACKLE) && e == null) {
                  continue; // no torn wire / burning debris found here
               }

               if (play(mc, kind, zn, e, ear, random, true)) {
                  cd[kind] = Math.round(AftermathMixer.cooldown(kind) * (0.7F + random.nextFloat() * 0.6F));
               }
            }
         }
      }
   }

   static void forget(int zoneId) {
      COOLDOWNS.remove(zoneId);
      PENDING.removeIf(p -> p.zone().id == zoneId);
   }

   static void clear() {
      COOLDOWNS.clear();
      PENDING.clear();
      VOICES.clear();
      windTarget = 0.0F;
      rumbleTarget = 0.0F;
      for (int i = 0; i < FIRE_LOOPS; i++) {
         FIRE_TARGET[i] = 0.0F;
      }
   }

   /** One-shots started in this session (QA). */
   static int played() {
      return played;
   }

   // ---------------------------------------------------------------- director

   private static ClientZone.Emitter pick(ClientZone zn, int kind, Vec3 ear, RandomSource random) {
      if (zn.emitters.isEmpty()) {
         return null;
      }

      if (kind == AftermathMixer.CRACKLE || kind == AftermathMixer.SPARKS) {
         // the nearest burning (or wired) spot within reach, so the close ones crackle
         ClientZone.Emitter best = null;
         double bestD = AftermathMixer.range(kind) * AftermathMixer.range(kind);
         for (ClientZone.Emitter e : zn.emitters) {
            boolean ok = kind == AftermathMixer.CRACKLE ? e.burnable || e.kind == ClientZone.Emitter.HOT : e.wire;
            double d = e.distanceSq(ear.x, ear.y, ear.z) * (0.6 + random.nextDouble() * 0.8);
            if (ok && d < bestD) {
               bestD = d;
               best = e;
            }
         }

         return best;
      }

      ClientZone.Emitter any = zn.emitters.get(random.nextInt(zn.emitters.size()));
      for (int tries = 0; tries < 6; tries++) {
         ClientZone.Emitter e = zn.emitters.get(random.nextInt(zn.emitters.size()));
         boolean fits = switch (kind) {
            case AftermathMixer.CRUMBLE -> e.kind == ClientZone.Emitter.OVERHANG || e.kind == ClientZone.Emitter.WINDOW;
            case AftermathMixer.ROCKS -> e.kind == ClientZone.Emitter.RUBBLE || e.kind == ClientZone.Emitter.BREACH || e.kind == ClientZone.Emitter.OVERHANG;
            case AftermathMixer.CREAK -> e.metal;
            case AftermathMixer.COLLAPSE -> e.kind != ClientZone.Emitter.HOT;
            default -> true;
         };
         if (fits) {
            return e;
         }
      }

      return any;
   }

   private static boolean play(Minecraft mc, int kind, ClientZone zn, ClientZone.Emitter e, Vec3 ear, RandomSource random, boolean chain) {
      Vec3 at;
      float volume;
      if (kind == AftermathMixer.EXPLOSION || kind == AftermathMixer.SIREN) {
         // far away, roughly behind the ruin as seen from here (the disaster goes on elsewhere, help comes from the city)
         double base = Math.atan2(zn.z - ear.z, zn.x - ear.x);
         double a = base + (random.nextDouble() - 0.5) * (kind == AftermathMixer.SIREN ? Math.PI * 1.6 : Math.PI * 0.8);
         double r = kind == AftermathMixer.SIREN ? 110.0 + random.nextDouble() * 110.0 : 150.0 + random.nextDouble() * 140.0;
         at = new Vec3(ear.x + Math.cos(a) * r, ear.y + 8.0 + random.nextDouble() * 20.0, ear.z + Math.sin(a) * r);
         float near = AftermathMixer.bed(Math.sqrt(zn.distanceSq(ear.x, ear.y, ear.z)), zn.radius, 260.0);
         volume = near * (kind == AftermathMixer.SIREN ? 0.22F + 0.18F * zn.channel(AftermathRules.SIREN) : 0.35F + 0.45F * zn.severity);
      } else {
         at = e != null ? new Vec3(e.x, e.y, e.z)
            : new Vec3(zn.x + (random.nextDouble() - 0.5) * zn.radius, zn.y + random.nextDouble() * 4.0, zn.z + (random.nextDouble() - 0.5) * zn.radius);
         double d = at.distanceTo(ear);
         volume = AftermathMixer.falloff(d, AftermathMixer.range(kind)) * gain(kind) * (0.8F + random.nextFloat() * 0.2F);
      }

      if (volume < 0.02F) {
         return false;
      }

      double d = at.distanceTo(ear);
      int delay = AftermathMixer.delay(d);
      float pitch = switch (kind) {
         case AftermathMixer.CREAK -> 0.78F + random.nextFloat() * 0.36F;
         case AftermathMixer.SIREN -> 0.94F + random.nextFloat() * 0.1F;
         case AftermathMixer.COLLAPSE, AftermathMixer.EXPLOSION -> 0.82F + random.nextFloat() * 0.25F;
         default -> 0.86F + random.nextFloat() * 0.3F;
      };
      once(mc, event(kind), at, volume, pitch, delay);
      VOICES.addLast(clock);
      played++;
      boolean visible = e != null && d < 110.0;
      switch (kind) {
         case AftermathMixer.CRUMBLE -> {
            if (visible) {
               AftermathFx.shower(e, 3 + random.nextInt(4), 0.5F);
            }
         }
         case AftermathMixer.ROCKS -> {
            if (visible) {
               AftermathFx.shower(e, 6 + random.nextInt(5), 1.0F);
            }
         }
         case AftermathMixer.DEBRIS -> {
            if (visible) {
               AftermathFx.shower(e, 4 + random.nextInt(4), 1.6F);
            }
         }
         case AftermathMixer.COLLAPSE -> {
            if (visible) {
               AftermathFx.shower(e, 14 + random.nextInt(8), 3.0F);
            }

            if (chain) {
               later(clock + 12 + random.nextInt(20), AftermathMixer.DEBRIS, zn, e);
               later(clock + 30 + random.nextInt(30), AftermathMixer.ROCKS, zn, e);
            }
         }
         case AftermathMixer.CREAK -> {
            if (chain && random.nextFloat() < 0.35F) {
               // the groan of steel, then something lets go
               later(clock + 15 + random.nextInt(30), random.nextBoolean() ? AftermathMixer.DEBRIS : AftermathMixer.ROCKS, zn, e);
            }
         }
         case AftermathMixer.SPARKS -> {
            if (visible) {
               AftermathFx.arc(e, 6 + random.nextInt(9));
            }
         }
         default -> {
         }
      }

      if (chain && kind == AftermathMixer.CRUMBLE && random.nextFloat() < 0.18F) {
         later(clock + 6 + random.nextInt(14), AftermathMixer.ROCKS, zn, e);
      }

      return true;
   }

   private static void later(int at, int kind, ClientZone zn, ClientZone.Emitter e) {
      if (PENDING.size() < 16) {
         PENDING.add(new Pending(at, kind, zn, e));
      }
   }

   private static float gain(int kind) {
      return switch (kind) {
         case AftermathMixer.CRUMBLE -> 0.55F;
         case AftermathMixer.ROCKS -> 0.7F;
         case AftermathMixer.DEBRIS -> 0.9F;
         case AftermathMixer.COLLAPSE -> 1.0F;
         case AftermathMixer.CREAK -> 0.7F;
         case AftermathMixer.SPARKS -> 0.6F;
         case AftermathMixer.CRACKLE -> 0.45F;
         default -> 0.6F;
      };
   }

   private static RegistryObject<SoundEvent> event(int kind) {
      return switch (kind) {
         case AftermathMixer.CRUMBLE -> AftermathSounds.CRUMBLE;
         case AftermathMixer.ROCKS -> AftermathSounds.ROCKS;
         case AftermathMixer.DEBRIS -> AftermathSounds.DEBRIS_IMPACT;
         case AftermathMixer.COLLAPSE -> AftermathSounds.COLLAPSE;
         case AftermathMixer.CREAK -> AftermathSounds.METAL_CREAK;
         case AftermathMixer.SPARKS -> AftermathSounds.SPARKS;
         case AftermathMixer.CRACKLE -> AftermathSounds.FIRE_CRACKLE;
         case AftermathMixer.EXPLOSION -> AftermathSounds.EXPLOSION_FAR;
         default -> AftermathSounds.SIREN_FAR;
      };
   }

   private static void once(Minecraft mc, RegistryObject<SoundEvent> event, Vec3 at, float volume, float pitch, int delay) {
      SimpleSoundInstance sound = new SimpleSoundInstance(event.get().getLocation(), SoundSource.AMBIENT, Mth.clamp(volume, 0.0F, 1.0F), pitch,
         SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
      if (delay > 0) {
         mc.getSoundManager().playDelayed(sound, delay);
      } else {
         mc.getSoundManager().play(sound);
      }
   }

   // ---------------------------------------------------------------- beds

   private static Vec3 toward(Vec3 ear, double x, double y, double z, double d, double keep) {
      if (d < 1.0E-3) {
         return ear;
      }

      double k = Math.min(d, keep) / d;
      return new Vec3(ear.x + (x - ear.x) * k, ear.y + (y - ear.y) * k, ear.z + (z - ear.z) * k);
   }

   private static void beds(Minecraft mc, List<ClientZone> zones, Vec3 ear) {
      float w = 0.0F;
      float r = 0.0F;
      for (ClientZone zn : zones) {
         double d = Math.sqrt(zn.distanceSq(ear.x, ear.y, ear.z));
         float bw = zn.channel(AftermathRules.WIND) * AftermathMixer.bed(d, zn.radius, 64.0) * 0.5F;
         if (bw > w) {
            w = bw;
            // inside the ruin the wind is all around; from outside it comes from the ruin
            windAt = toward(ear, zn.x, zn.y + 6.0, zn.z, d, d < zn.radius ? 3.0 : 10.0);
         }

         float br = zn.channel(AftermathRules.RUMBLE) * AftermathMixer.bed(d, zn.radius, 120.0) * 0.7F;
         if (br > r) {
            r = br;
            rumbleAt = toward(ear, zn.x, zn.y, zn.z, d, 12.0);
         }
      }

      windTarget = w;
      rumbleTarget = r;
      // residual fire: the nearest burning spots of the nearest burning ruins
      ClientZone.Emitter[] best = new ClientZone.Emitter[FIRE_LOOPS];
      float[] level = new float[FIRE_LOOPS];
      for (ClientZone zn : zones) {
         float fire = zn.channel(AftermathRules.FIRE) * 0.9F + zn.channel(AftermathRules.SMOLDER) * 0.3F;
         if (fire < 0.03F || zn.distance - zn.radius > 40.0) {
            continue;
         }

         for (ClientZone.Emitter e : zn.emitters) {
            if (!e.burnable && e.kind != ClientZone.Emitter.HOT) {
               continue;
            }

            float v = fire * e.strength * AftermathMixer.falloff(Math.sqrt(e.distanceSq(ear.x, ear.y, ear.z)), 32.0);
            for (int i = 0; i < FIRE_LOOPS; i++) {
               if (v > level[i] && (best[i] == null || best[i].distanceSq(e.x, e.y, e.z) > 36.0)) {
                  for (int j = FIRE_LOOPS - 1; j > i; j--) {
                     level[j] = level[j - 1];
                     best[j] = best[j - 1];
                  }

                  level[i] = v;
                  best[i] = e;
                  break;
               }
            }
         }
      }

      for (int i = 0; i < FIRE_LOOPS; i++) {
         FIRE_TARGET[i] = level[i];
         if (best[i] != null) {
            FIRE_AT[i] = new Vec3(best[i].x, best[i].y + 0.5, best[i].z);
         }
      }

      if (mc.getSoundManager() == null) {
         return;
      }

      if (windTarget > 0.02F && (wind == null || wind.isStopped()) && --windRetry <= 0) {
         wind = start(mc, AftermathSounds.WIND.get(), () -> new Target(windAt, windTarget, 1.0F));
         windRetry = RETRY;
      }

      if (rumbleTarget > 0.02F && (rumble == null || rumble.isStopped()) && --rumbleRetry <= 0) {
         rumble = start(mc, AftermathSounds.RUMBLE.get(), () -> new Target(rumbleAt, rumbleTarget, 1.0F));
         rumbleRetry = RETRY;
      }

      for (int i = 0; i < FIRE_LOOPS; i++) {
         final int k = i;
         if (FIRE_TARGET[i] > 0.02F && (FIRE[i] == null || FIRE[i].isStopped()) && --FIRE_RETRY[i] <= 0) {
            FIRE[i] = start(mc, AftermathSounds.FIRE_BED.get(), () -> new Target(FIRE_AT[k], FIRE_TARGET[k], 0.92F + 0.08F * k));
            FIRE_RETRY[i] = RETRY;
         }
      }
   }

   private static Loop start(Minecraft mc, SoundEvent event, Supplier<Target> target) {
      Loop loop = new Loop(event, target);
      mc.getSoundManager().play(loop);
      return loop;
   }

   /** Loudness, pitch and place a loop fades toward. */
   record Target(Vec3 at, float volume, float pitch) {
   }

   /** Beds currently playing (QA). */
   static int beds() {
      int n = 0;
      for (Loop l : new Loop[]{wind, rumble, FIRE[0], FIRE[1]}) {
         if (l != null && !l.isStopped()) {
            n++;
         }
      }

      return n;
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final Supplier<Target> target;
      private float level;
      private int silent;

      Loop(SoundEvent event, Supplier<Target> target) {
         super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
         this.target = target;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.attenuation = SoundInstance.Attenuation.NONE;
         Target t = target.get();
         this.x = t.at().x;
         this.y = t.at().y;
         this.z = t.at().z;
         this.pitch = t.pitch();
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
         this.pitch += (t.pitch() - this.pitch) * 0.2F;
         this.x = t.at().x;
         this.y = t.at().y;
         this.z = t.at().z;
      }
   }
}
