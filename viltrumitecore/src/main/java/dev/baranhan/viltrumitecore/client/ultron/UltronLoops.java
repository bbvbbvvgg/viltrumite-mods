package dev.baranhan.viltrumitecore.client.ultron;

import dev.baranhan.viltrumitecore.entity.ultron.UltronBrute;
import dev.baranhan.viltrumitecore.entity.ultron.UltronDrone;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEngineer;
import dev.baranhan.viltrumitecore.entity.ultron.UltronInterceptor;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import dev.baranhan.viltrumitecore.entity.ultron.UltronSentry;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronSounds;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The machines' continuous sounds, all client side (the server only sends one-shots): the rotor hum of the
 * nearest drones, the jet roar of the nearest interceptors and of Ultron Prime in flight, the power hum of the
 * nearest nodes (lower while unpowered or under construction) and the upload tether's drone during the finale,
 * plus every walker's footsteps on its gait (two footfalls per stride). Each loop fades toward a target re-read
 * every tick and stops itself once that stays at zero, so the end of the event, a level change or a logout
 * never leaves a hum behind; a loop the sound engine dropped is replaced after a pause while still wanted
 * (the ChitauriLoops pattern). Loudness ranges come from the sounds' attenuation distances.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class UltronLoops {
   public static final float FADE_UP = 0.07F;
   public static final float FADE_DOWN = 0.1F;
   static final int STALE_TICKS = 10;
   static final int RETRY_TICKS = 20;
   static final double STEP_RANGE = 36.0;

   /** A loop family: how many play at once, start range, the extra range a running loop keeps. */
   enum Kind {
      DRONE(4, 24.0),
      JET(3, 56.0),
      PRIME(1, 96.0),
      NODE(3, 24.0),
      TETHER(1, 160.0);

      final int max;
      final double range;

      Kind(int max, double range) {
         this.max = max;
         this.range = range;
      }

      double keep() {
         return this.range + 8.0;
      }
   }

   private static final Map<Long, Loop> LOOPS = new HashMap<>();
   private static final Map<Long, Integer> RETRY_AT = new HashMap<>();
   private static final Map<Kind, Map<Integer, Integer>> RANK = new EnumMap<>(Kind.class);
   private static ClientLevel lastLevel;
   private static int clock;

   private UltronLoops() {
   }

   /** A unit started a timed move on this client (nothing loops on moves yet: the server sends their one-shots). */
   static void moveStarted(UltronUnit unit) {
   }

   // ------------------------------------------------------------------ pure targets (tested)

   /** Pure: one fade step. */
   public static float approach(float current, float target) {
      return target > current ? Math.min(target, current + FADE_UP) : Math.max(target, current - FADE_DOWN);
   }

   /** Pure: drone rotor loudness and pitch from speed (blocks / tick); a falling drone whines down. */
   public static float[] droneHum(double speed, boolean dying, int deathTime) {
      float s = (float)Mth.clamp(speed / 0.4, 0.0, 1.0);
      if (dying) {
         return new float[]{Math.max(0.0F, 0.5F - deathTime * 0.02F), Math.max(0.5F, 0.95F - deathTime * 0.02F)};
      }
      return new float[]{0.3F + 0.35F * s, 0.92F + 0.3F * s};
   }

   /** Pure: jet loudness and pitch from speed; louder for Ultron Prime. */
   public static float[] jet(double speed, boolean prime) {
      float s = (float)Mth.clamp(speed / 1.0, 0.0, 1.0);
      return prime ? new float[]{0.75F + 0.25F * s, 0.85F + 0.25F * s} : new float[]{0.45F + 0.5F * s, 0.8F + 0.4F * s};
   }

   /** Pure: node hum by state (online powered, online cut off, under construction); silent otherwise. */
   public static float[] nodeHum(UltronNodeState state, boolean powered, float build) {
      return switch (state) {
         case ONLINE -> powered ? new float[]{0.6F, 1.0F} : new float[]{0.28F, 0.78F};
         case BUILDING -> new float[]{0.18F + 0.2F * build, 0.65F + 0.3F * build};
         default -> new float[]{0.0F, 1.0F};
      };
   }

   /** Pure: a footfall happens when the gait crosses a half stride. */
   public static boolean footfall(float gaitO, float gait) {
      return Math.floor(gait * 2.0F) != Math.floor(gaitO * 2.0F) && gait > gaitO;
   }

   // ------------------------------------------------------------------ tick

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
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      prune(mc);
      RETRY_AT.values().removeIf(at -> at <= clock);
      AABB box = new AABB(eye, eye).inflate(Kind.TETHER.keep());
      List<UltronUnit> units = mc.level.getEntitiesOfClass(UltronUnit.class, box.deflate(Kind.TETHER.keep() - Kind.PRIME.keep()));
      List<Entity> drones = new ArrayList<>();
      List<Entity> jets = new ArrayList<>();
      List<Entity> primes = new ArrayList<>();
      for (UltronUnit u : units) {
         if (u instanceof UltronDrone && (u.isAlive() || u.deathTime > 0)) {
            drones.add(u);
         } else if (u instanceof UltronInterceptor && u.isAlive()) {
            jets.add(u);
         } else if (u instanceof UltronPrime p && p.isAlive() && p.flier()) {
            primes.add(p);
         }
         steps(mc, u, eye);
      }
      group(mc, eye, Kind.DRONE, drones, UltronSounds.DRONE_HOVER.get(), e -> {
         UltronUnit u = (UltronUnit)e;
         float[] t = droneHum(u.getDeltaMovement().length(), !u.isAlive(), u.deathTime);
         return new Target(t[0], t[1]);
      });
      group(mc, eye, Kind.JET, jets, UltronSounds.INTERCEPTOR_JET.get(), e -> {
         float[] t = jet(e.getDeltaMovement().length(), false);
         return e.isAlive() ? new Target(t[0], t[1]) : Target.OFF;
      });
      group(mc, eye, Kind.PRIME, primes, UltronSounds.PRIME_JET.get(), e -> {
         float[] t = jet(e.getDeltaMovement().length(), true);
         return e.isAlive() && ((UltronPrime)e).flier() ? new Target(t[0], t[1]) : Target.OFF;
      });
      List<Entity> nodes = new ArrayList<>(mc.level.getEntitiesOfClass(UltronNodeEntity.class, new AABB(eye, eye).inflate(Kind.NODE.keep()),
         n -> n.isAlive() && n.nodeState().standing() && n.nodeState() != UltronNodeState.DISABLED));
      group(mc, eye, Kind.NODE, nodes, UltronSounds.NODE_HUM.get(), e -> {
         UltronNodeEntity n = (UltronNodeEntity)e;
         float[] t = nodeHum(n.nodeState(), n.nodeFlag(UltronNodeEntity.F_POWERED), n.buildShare());
         return new Target(t[0], t[1]);
      });
      UltronStateS2CPacket s = UltronClientState.get();
      List<Entity> tether = new ArrayList<>();
      if (s != null && s.uploading() && s.bossId() >= 0) {
         Entity boss = mc.level.getEntity(s.bossId());
         if (boss != null && boss.isAlive()) {
            tether.add(boss);
         }
      }
      group(mc, eye, Kind.TETHER, tether, UltronSounds.NODE_TETHER.get(), e -> {
         UltronStateS2CPacket now = UltronClientState.get();
         return now != null && now.uploading() && e.isAlive() ? new Target(0.9F, 0.9F + 0.2F * now.upload()) : Target.OFF;
      });
   }

   private static void group(Minecraft mc, Vec3 eye, Kind kind, List<Entity> candidates, SoundEvent sound, Function<Entity, Target> target) {
      Map<Integer, Integer> rank = RANK.computeIfAbsent(kind, k -> new HashMap<>());
      rank.clear();
      candidates.removeIf(e -> e.distanceToSqr(eye) > kind.keep() * kind.keep());
      candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
      for (int i = 0; i < candidates.size(); i++) {
         rank.put(candidates.get(i).getId(), i);
      }
      int tracked = 0;
      for (Loop l : LOOPS.values()) {
         if (l.kind == kind) {
            tracked++;
         }
      }
      for (int i = 0; i < candidates.size() && i < kind.max; i++) {
         Entity e = candidates.get(i);
         long key = key(kind, e.getId());
         if (LOOPS.containsKey(key) || RETRY_AT.containsKey(key) || tracked >= kind.max + 1 || e.distanceToSqr(eye) > kind.range * kind.range) {
            continue;
         }
         Loop loop = new Loop(kind, sound, e, target);
         LOOPS.put(key, loop);
         tracked++;
         mc.getSoundManager().play(loop);
      }
   }

   /** Still wanted: in its family's keep range and within one rank of hysteresis. */
   static boolean wanted(Kind kind, Entity e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || e.isRemoved()) {
         return false;
      }
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      Integer r = RANK.getOrDefault(kind, Map.of()).get(e.getId());
      return r != null && r < kind.max + 1 && e.distanceToSqr(eye) <= kind.keep() * kind.keep();
   }

   private static void steps(Minecraft mc, UltronUnit u, Vec3 eye) {
      if (!u.isAlive() || u.flier() || !u.onGround() || u.clientSpeed < 0.015F || !footfall(u.clientGaitO, u.clientGait)
         || u.distanceToSqr(eye) > STEP_RANGE * STEP_RANGE) {
         return;
      }
      SoundEvent step;
      float volume;
      float pitch;
      if (u instanceof UltronPrime) {
         step = UltronSounds.PRIME_STEP.get();
         volume = 0.9F;
         pitch = 0.95F;
      } else if (u instanceof UltronBrute) {
         step = UltronSounds.BRUTE_STEP.get();
         volume = 1.0F;
         pitch = 0.85F + Math.min(0.2F, u.clientSpeed);
      } else if (u instanceof UltronEngineer) {
         step = UltronSounds.ENGINEER_STEP.get();
         volume = 0.3F;
         pitch = 1.1F;
      } else if (u instanceof UltronSentry) {
         step = UltronSounds.SENTRY_STEP.get();
         volume = 0.55F;
         pitch = 0.95F + Math.min(0.15F, u.clientSpeed * 0.5F);
      } else {
         return;
      }
      float jitter = (u.getRandom().nextFloat() - 0.5F) * 0.08F;
      mc.getSoundManager().play(new SimpleSoundInstance(step, SoundSource.HOSTILE, volume, pitch + jitter, SoundInstance.createUnseededRandom(), u.getX(),
         u.getY() + 0.1, u.getZ()));
   }

   private static void prune(Minecraft mc) {
      Iterator<Map.Entry<Long, Loop>> it = LOOPS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Long, Loop> e = it.next();
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

   private static long key(Kind kind, int id) {
      return (long)kind.ordinal() << 32 | id & 0xFFFFFFFFL;
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   static void clear() {
      Minecraft mc = Minecraft.getInstance();
      for (Loop loop : new ArrayList<>(LOOPS.values())) {
         loop.end();
         if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(loop);
         }
      }
      LOOPS.clear();
      RETRY_AT.clear();
      RANK.clear();
   }

   /** Loops currently tracked (tests and the debug HUD). */
   public static int playing() {
      return LOOPS.size();
   }

   record Target(float volume, float pitch) {
      static final Target OFF = new Target(0.0F, 1.0F);
   }

   static final class Loop extends AbstractTickableSoundInstance {
      final Kind kind;
      final Entity entity;
      private final Function<Entity, Target> target;
      private float level;
      private int silent;
      int heartbeat;

      Loop(Kind kind, SoundEvent event, Entity entity, Function<Entity, Target> target) {
         super(event, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
         this.kind = kind;
         this.entity = entity;
         this.target = target;
         this.heartbeat = clock;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.follow();
      }

      void end() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      private void follow() {
         double up = this.entity instanceof UltronNodeEntity ? 2.6 : this.entity.getBbHeight() * 0.5;
         this.x = this.entity.getX();
         this.y = this.entity.getY() + up;
         this.z = this.entity.getZ();
      }

      @Override
      public void tick() {
         this.heartbeat = clock;
         Target t = wanted(this.kind, this.entity) ? this.target.apply(this.entity) : Target.OFF;
         this.level = approach(this.level, t.volume());
         this.silent = t.volume() <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 2) {
            this.stop();
            return;
         }
         // never exactly 0: the engine drops a channel whose volume reaches 0
         this.volume = Math.max(0.001F, this.level);
         this.pitch += (t.pitch() - this.pitch) * 0.25F;
         if (!this.entity.isRemoved()) {
            this.follow();
         }
      }

      @Nullable
      Entity entity() {
         return this.entity;
      }
   }
}
