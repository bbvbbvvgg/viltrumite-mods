package dev.baranhan.viltrumitecore.client.meteor;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.worldevent.meteor.MeteorRainSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.RegistryObject;

/**
 * The sound of Meteor Rain 2.0, all client side. Meteors in the air: one far rumble that follows the nearest
 * meteor and the burning roar of the {@link #MAX_METEOR_LOOPS} nearest ones, with a Doppler pitch from the
 * radial speed; a whoosh about a second before a meteor's predicted closest approach to the listener.
 * Impacts: layers picked by distance (crack and body near, a dull boom with a long tail far, the shockwave
 * thump, falling debris and settling rubble close by), delayed by the speed of sound and thinned when many
 * land at once. The burning city: the roar of the {@link #MAX_FIRE_LOOPS} nearest fire zones and rate-limited
 * crackles of single burning blocks. Distance curves are manual (attenuation off, the position still pans),
 * because vanilla linear roll-off would cut a meteor at 250 blocks height. Every loop fades toward a target
 * re-read each tick and stops itself at zero; stale or dropped loops are replaced after a pause.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class MeteorRainAudio {
   public static final int MAX_METEOR_LOOPS = 3;
   public static final double METEOR_RANGE = 320.0;
   public static final double ROAR_RANGE = 190.0;
   public static final int MAX_FIRE_LOOPS = 2;
   public static final double FIRE_RANGE = 70.0;
   /** Impacts within this many ticks count as simultaneous for the voice limit. */
   public static final int IMPACT_WINDOW = 30;
   /** Crackles of single burning blocks per second, all fires together. */
   public static final int CRACKLES_PER_SECOND = 8;
   /** Blocks per tick of sound: 343 m/s at 20 ticks per second. */
   public static final double SOUND_SPEED = 343.0 / 20.0;
   static final float FADE_UP = 0.05F;
   static final float FADE_DOWN = 0.08F;
   static final int STALE_TICKS = 10;
   static final int RETRY_TICKS = 20;

   private static final Map<Integer, Loop> ROARS = new HashMap<>();
   private static final Map<Integer, Integer> RETRY_AT = new HashMap<>();
   private static final Map<Integer, Integer> RANK = new HashMap<>();
   private static final Set<Integer> WHOOSHED = new HashSet<>();
   private static final ArrayDeque<Integer> IMPACTS = new ArrayDeque<>();
   private static final Loop[] FIRE = new Loop[MAX_FIRE_LOOPS];
   private static final int[] FIRE_RETRY = new int[MAX_FIRE_LOOPS];
   private static Loop approach;
   private static int approachRetry;
   private static Vec3 approachAt = Vec3.ZERO;
   private static float approachTarget;
   private static ClientLevel lastLevel;
   private static int clock;
   private static int crackleSecond;
   private static int crackles;

   private MeteorRainAudio() {
   }

   /** Pure: loudness of the far rumble of a meteor at a distance. */
   public static float approachVolume(double distance) {
      if (distance >= METEOR_RANGE) {
         return 0.0F;
      }
      float t = (float)(1.0 - distance / METEOR_RANGE);
      return Mth.clamp(0.15F + 0.7F * t * t, 0.0F, 0.85F);
   }

   /** Pure: loudness of the near roar of a meteor at a distance. */
   public static float roarVolume(double distance) {
      if (distance >= ROAR_RANGE) {
         return 0.0F;
      }
      float t = (float)(1.0 - distance / ROAR_RANGE);
      return Mth.clamp(t * t * 1.1F, 0.0F, 1.0F);
   }

   /** Pure: Doppler pitch from the radial speed toward the listener in blocks per tick (positive = closing). */
   public static float doppler(double closing) {
      double v = Mth.clamp(closing, -6.0, 6.0) / SOUND_SPEED;
      return (float)Mth.clamp(1.0 / (1.0 - v), 0.8, 1.25);
   }

   /** Pure: ticks until a sound from {@code distance} blocks arrives (capped, so a far boom never lags absurdly). */
   public static int soundDelay(double distance) {
      return (int)Math.min(28L, Math.round(Math.max(0.0, distance) / SOUND_SPEED));
   }

   /**
    * Pure: the impact layers at a distance with {@code recent} other impacts in the window, as volumes
    * {near crack/body, far boom, shockwave, debris, rubble}; 0 = skipped. Near layers fade out between 40 and
    * 120 blocks while the far boom fades in; with several impacts at once the tails go first, then everything
    * gets quieter, so a dense rain never stacks a wall of identical booms.
    */
   public static float[] impactMix(double distance, int recent) {
      float near = distance < 40.0 ? 1.0F : distance < 120.0 ? (float)(1.0 - (distance - 40.0) / 80.0) : 0.0F;
      float far = distance < 40.0 ? 0.25F : distance < 120.0 ? 0.25F + 0.65F * (float)((distance - 40.0) / 80.0)
         : (float)Mth.clamp(0.9 - (distance - 120.0) / 520.0, 0.2, 0.9);
      float shock = (float)Mth.clamp(1.0 - distance / 260.0, 0.0, 1.0);
      float debris = distance < 60.0 ? (float)(1.0 - distance / 60.0) : 0.0F;
      float rubble = distance < 45.0 ? (float)(0.8 * (1.0 - distance / 45.0)) : 0.0F;
      if (recent >= 2) {
         debris *= 0.5F;
         rubble = 0.0F;
      }
      if (recent >= 4) {
         float duck = recent >= 7 ? 0.45F : 0.7F;
         near *= duck;
         far *= duck;
         shock *= duck;
         debris = 0.0F;
      }
      return new float[]{near, far, shock, debris, rubble};
   }

   /** Pure: roar of a fire zone by intensity (0..1), radius and the listener's distance to its centre. */
   public static float fireRoarVolume(float intensity, float radius, double distance) {
      double edge = Math.max(0.0, distance - radius);
      if (edge >= FIRE_RANGE || intensity <= 0.0F) {
         return 0.0F;
      }
      float near = (float)(1.0 - edge / FIRE_RANGE);
      float size = Mth.clamp(0.35F + radius / 16.0F, 0.35F, 1.0F);
      return Mth.clamp(intensity * size * near * near, 0.0F, 1.0F);
   }

   /** Pure: the whoosh fires when the predicted closest approach is near and about a second away. */
   public static boolean whooshDue(double closest, double ticksToClosest, double speed) {
      return speed > 0.6 && closest < 36.0 && ticksToClosest >= 12.0 && ticksToClosest <= 22.0;
   }

   // ---------------------------------------------------------------- one-shots

   /** An impact this client saw (entity event), or a far one from the server. */
   public static void impact(Vec3 at, double distance) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      while (!IMPACTS.isEmpty() && clock - IMPACTS.peekFirst() > IMPACT_WINDOW) {
         IMPACTS.pollFirst();
      }
      int recent = IMPACTS.size();
      if (IMPACTS.size() < 32) {
         IMPACTS.addLast(clock);
      }

      float[] mix = impactMix(distance, recent);
      int delay = soundDelay(distance);
      float pitch = 0.92F + mc.level.random.nextFloat() * 0.14F;
      once(mc, MeteorRainSounds.IMPACT_NEAR, at, mix[0], pitch, delay);
      once(mc, MeteorRainSounds.IMPACT_FAR, at, mix[1], pitch * 0.96F, delay);
      once(mc, MeteorRainSounds.SHOCKWAVE, at, mix[2], 0.9F + mc.level.random.nextFloat() * 0.1F, delay);
      once(mc, MeteorRainSounds.DEBRIS, at, mix[3], 0.9F + mc.level.random.nextFloat() * 0.2F, delay + 10 + mc.level.random.nextInt(8));
      once(mc, MeteorRainSounds.RUBBLE, at, mix[4], 0.9F + mc.level.random.nextFloat() * 0.15F, delay + 26 + mc.level.random.nextInt(10));
   }

   /** A crackle of one burning block, rate limited across all fires. */
   public static void crackle(double x, double y, double z) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      int second = clock / 20;
      if (second != crackleSecond) {
         crackleSecond = second;
         crackles = 0;
      }
      if (crackles >= CRACKLES_PER_SECOND) {
         return;
      }
      crackles++;
      mc.getSoundManager().play(new SimpleSoundInstance(MeteorRainSounds.FIRE_CRACKLE.get(), SoundSource.BLOCKS,
         0.6F + mc.level.random.nextFloat() * 0.4F, 0.85F + mc.level.random.nextFloat() * 0.3F, SoundInstance.createUnseededRandom(), x, y, z));
   }

   /** A fire flare (power 0..1) or a brand landing. */
   public static void flare(Vec3 at, float power) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      mc.getSoundManager().play(new SimpleSoundInstance(MeteorRainSounds.FIRE_FLARE.get(), SoundSource.BLOCKS,
         0.5F + 0.5F * Mth.clamp(power, 0.0F, 1.0F), 0.85F + mc.level.random.nextFloat() * 0.3F, SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
   }

   /** A brand thrown into the air. */
   public static void brand(Vec3 at) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      mc.getSoundManager().play(new SimpleSoundInstance(MeteorRainSounds.BRAND.get(), SoundSource.BLOCKS, 0.8F,
         0.9F + mc.level.random.nextFloat() * 0.25F, SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
   }

   private static void once(Minecraft mc, RegistryObject<SoundEvent> event, Vec3 at, float volume, float pitch, int delay) {
      if (volume <= 0.01F) {
         return;
      }
      SimpleSoundInstance sound = new SimpleSoundInstance(event.get().getLocation(), SoundSource.WEATHER, Mth.clamp(volume, 0.0F, 1.0F), pitch,
         SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false);
      if (delay > 0) {
         mc.getSoundManager().playDelayed(sound, delay);
      } else {
         mc.getSoundManager().play(sound);
      }
   }

   // ---------------------------------------------------------------- loops

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         clear();
      }
      if (mc.level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      clock++;
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      prune(mc);
      RETRY_AT.values().removeIf(at -> at <= clock);
      meteors(mc, ear);
      fires(mc, ear);
   }

   private static void prune(Minecraft mc) {
      Iterator<Map.Entry<Integer, Loop>> it = ROARS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Loop> e = it.next();
         Loop loop = e.getValue();
         if (loop.isStopped()) {
            it.remove();
         } else if (clock - loop.heartbeat > STALE_TICKS) {
            loop.end();
            mc.getSoundManager().stop(loop);
            it.remove();
            RETRY_AT.put(e.getKey(), clock + RETRY_TICKS);
         }
      }
   }

   private static void meteors(Minecraft mc, Vec3 ear) {
      List<MeteorEntity> near = new ArrayList<>(mc.level.getEntitiesOfClass(MeteorEntity.class, new AABB(ear, ear).inflate(METEOR_RANGE),
         m -> !m.isRemoved() && m.distanceToSqr(ear) <= METEOR_RANGE * METEOR_RANGE));
      near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(ear)));
      RANK.clear();
      for (int i = 0; i < near.size(); i++) {
         RANK.put(near.get(i).getId(), i);
      }

      // the far rumble follows the nearest meteor
      approachTarget = 0.0F;
      if (!near.isEmpty()) {
         MeteorEntity first = near.get(0);
         approachAt = first.position();
         approachTarget = approachVolume(Math.sqrt(first.distanceToSqr(ear)));
      }
      if (approach != null && (approach.isStopped() || clock - approach.heartbeat > STALE_TICKS)) {
         if (!approach.isStopped()) {
            approach.end();
            mc.getSoundManager().stop(approach);
            approachRetry = clock + RETRY_TICKS;
         }
         approach = null;
      }
      if (approach == null && clock >= approachRetry && approachTarget > 0.0F) {
         approach = new Loop(MeteorRainSounds.APPROACH.get(), SoundSource.WEATHER, () -> {
            Vec3 a = approachAt;
            return new Target(approachTarget, 1.0F, a);
         });
         mc.getSoundManager().play(approach);
      }

      for (MeteorEntity meteor : near) {
         int id = meteor.getId();
         double d = Math.sqrt(meteor.distanceToSqr(ear));
         Vec3 v = meteor.getDeltaMovement();
         double speed = v.length();
         if (speed > 0.01 && !WHOOSHED.contains(id)) {
            Vec3 rel = ear.subtract(meteor.position());
            double t = rel.dot(v) / (speed * speed);
            double closest = rel.subtract(v.scale(t)).length();
            if (whooshDue(closest, t, speed) && WHOOSHED.size() < 64) {
               WHOOSHED.add(id);
               Vec3 at = meteor.position().add(v.scale(t * 0.5));
               once(mc, MeteorRainSounds.WHOOSH, at, (float)Mth.clamp(1.1 - closest / 40.0, 0.25, 1.0), 0.95F + (float)speed * 0.03F, 0);
            }
         }
         Integer rank = RANK.get(id);
         if (rank != null && rank < MAX_METEOR_LOOPS && d < ROAR_RANGE && !ROARS.containsKey(id) && !RETRY_AT.containsKey(id)
            && ROARS.size() < MAX_METEOR_LOOPS + 1) {
            Loop loop = new Loop(MeteorRainSounds.ROAR.get(), SoundSource.WEATHER, () -> roarTarget(meteor));
            ROARS.put(id, loop);
            mc.getSoundManager().play(loop);
         }
      }
      if (WHOOSHED.size() > 48) {
         WHOOSHED.removeIf(id -> mc.level.getEntity(id) == null);
      }
   }

   private static Target roarTarget(MeteorEntity meteor) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || meteor.isRemoved() || RANK.getOrDefault(meteor.getId(), Integer.MAX_VALUE) > MAX_METEOR_LOOPS) {
         return Target.OFF;
      }
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 to = ear.subtract(meteor.position());
      double d = to.length();
      double closing = d < 1.0E-3 ? 0.0 : meteor.getDeltaMovement().dot(to) / d;
      return new Target(roarVolume(d), doppler(closing), meteor.position());
   }

   private static void fires(Minecraft mc, Vec3 ear) {
      List<float[]> zones = CityFireClient.nearestZones(ear, MAX_FIRE_LOOPS);
      for (int i = 0; i < MAX_FIRE_LOOPS; i++) {
         Loop loop = FIRE[i];
         if (loop != null && (loop.isStopped() || clock - loop.heartbeat > STALE_TICKS)) {
            if (!loop.isStopped()) {
               loop.end();
               mc.getSoundManager().stop(loop);
               FIRE_RETRY[i] = clock + RETRY_TICKS;
            }
            FIRE[i] = null;
         }
         if (FIRE[i] == null && clock >= FIRE_RETRY[i] && i < zones.size()) {
            float[] z = zones.get(i);
            if (fireRoarVolume(z[3], z[4], ear.distanceTo(new Vec3(z[0], z[1], z[2]))) > 0.0F) {
               int slot = i;
               FIRE[i] = new Loop(MeteorRainSounds.FIRE_ROAR.get(), SoundSource.BLOCKS, () -> fireTarget(slot));
               mc.getSoundManager().play(FIRE[i]);
            }
         }
      }
   }

   private static Target fireTarget(int slot) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return Target.OFF;
      }
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      List<float[]> zones = CityFireClient.nearestZones(ear, MAX_FIRE_LOOPS);
      if (slot >= zones.size()) {
         return Target.OFF;
      }
      float[] z = zones.get(slot);
      Vec3 centre = new Vec3(z[0], z[1], z[2]);
      double d = ear.distanceTo(centre);
      // the roar comes from the edge of the zone nearest to the listener
      Vec3 at = d <= z[4] || d < 1.0E-3 ? ear : centre.add(ear.subtract(centre).scale(z[4] / d));
      return new Target(fireRoarVolume(z[3], z[4], d), 0.92F + 0.12F * z[3], at);
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   static void clear() {
      Minecraft mc = Minecraft.getInstance();
      List<Loop> all = new ArrayList<>(ROARS.values());
      if (approach != null) {
         all.add(approach);
      }
      for (Loop loop : FIRE) {
         if (loop != null) {
            all.add(loop);
         }
      }
      for (Loop loop : all) {
         loop.end();
         if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(loop);
         }
      }
      ROARS.clear();
      RETRY_AT.clear();
      RANK.clear();
      WHOOSHED.clear();
      IMPACTS.clear();
      java.util.Arrays.fill(FIRE, null);
      java.util.Arrays.fill(FIRE_RETRY, 0);
      approach = null;
      approachRetry = 0;
      approachTarget = 0.0F;
   }

   /** Loops tracked now (debug and QA). */
   public static int playing() {
      int n = ROARS.size() + (approach != null ? 1 : 0);
      for (Loop loop : FIRE) {
         if (loop != null) {
            n++;
         }
      }
      return n;
   }

   record Target(float volume, float pitch, Vec3 at) {
      static final Target OFF = new Target(0.0F, 1.0F, null);
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final Supplier<Target> target;
      private float level;
      private int silent;
      int heartbeat;

      Loop(SoundEvent event, SoundSource source, Supplier<Target> target) {
         super(event, source, SoundInstance.createUnseededRandom());
         this.target = target;
         this.heartbeat = clock;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.attenuation = SoundInstance.Attenuation.NONE;
         Target t = target.get();
         if (t.at() != null) {
            this.x = t.at().x;
            this.y = t.at().y;
            this.z = t.at().z;
         }
      }

      void end() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         this.heartbeat = clock;
         Target t = this.target.get();
         float goal = t.volume();
         this.level = goal > this.level ? Math.min(goal, this.level + FADE_UP) : Math.max(goal, this.level - FADE_DOWN);
         this.silent = goal <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 2) {
            this.stop();
            return;
         }
         // never exactly 0: the engine drops a channel whose volume reaches 0
         this.volume = Math.max(0.001F, this.level);
         this.pitch += (t.pitch() - this.pitch) * 0.25F;
         if (t.at() != null) {
            this.x = t.at().x;
            this.y = t.at().y;
            this.z = t.at().z;
         }
      }
   }
}
