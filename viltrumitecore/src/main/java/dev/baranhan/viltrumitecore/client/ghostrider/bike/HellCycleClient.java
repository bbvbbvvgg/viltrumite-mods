package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderSounds;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Client side of the Hell Cycle ({@link HellCycleEntity.ClientHooks}): samples the rider's keys (W/S throttle and
 * brake, A/D steering, Space jump, Sprint = drift), runs the engine loops of every bike in earshot, turns the riding
 * events (jump, landing, crash, drift, backfire, gear shift, hard stop) into sounds and hellfire, spawns the wheel
 * flames, sparks and the burning road trail. Installed by GhostRiderClient.
 */
public final class HellCycleClient implements HellCycleEntity.ClientHooks {
   public static final HellCycleClient INSTANCE = new HellCycleClient();
   private static final Int2ObjectOpenHashMap<Fx> FX = new Int2ObjectOpenHashMap<>();
   /** Trail flames are laid at most this far apart along the track (blocks). */
   static final double TRAIL_STEP = 0.32;

   static final class Fx {
      final EnumMap<HellCycleEngineSound.Layer, HellCycleEngineSound> loops = new EnumMap<>(HellCycleEngineSound.Layer.class);
      int backfireTick = -100;
      float backfireStrength;
      Vec3 lastRear;
      Vec3 lastFront;
   }

   private HellCycleClient() {
   }

   @Override
   public HellCyclePhysics.Input sample(HellCycleEntity bike) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || player.getVehicle() != bike || mc.screen != null) {
         return HellCyclePhysics.Input.NONE;
      }
      Input in = player.input;
      double throttle = Mth.clamp(in.forwardImpulse, -1.0F, 1.0F);
      double steer = Mth.clamp(in.leftImpulse, -1.0F, 1.0F);
      return new HellCyclePhysics.Input(throttle, steer, in.jumping, mc.options.keySprint.isDown(), false);
   }

   @Override
   public void tick(HellCycleEntity bike) {
      Fx fx = FX.computeIfAbsent(bike.getId(), k -> new Fx());
      this.loops(bike, fx);
      this.particles(bike, fx);
   }

   @Override
   public void events(HellCycleEntity bike, int events) {
      Fx fx = FX.computeIfAbsent(bike.getId(), k -> new Fx());
      Level level = bike.level();
      RandomSource rnd = bike.level().getRandom();
      if ((events & HellCyclePhysics.EV_JUMP) != 0) {
         play(bike, GhostRiderSounds.BIKE_JUMP, 0.9F, 1.0F);
         burst(bike, ViltrumiteParticles.FIRE_FLARE.get(), rear(bike), 4, 0.05);
      }
      if ((events & HellCyclePhysics.EV_LAND) != 0) {
         play(bike, GhostRiderSounds.BIKE_LAND, 1.0F, 0.95F + rnd.nextFloat() * 0.1F);
         burst(bike, ViltrumiteParticles.METEOR_EMBER.get(), rear(bike), 10, 0.25);
         burst(bike, ViltrumiteParticles.FIRE_FLARE.get(), front(bike), 4, 0.06);
         burst(bike, ViltrumiteParticles.FIRE_FLARE.get(), rear(bike), 4, 0.06);
      }
      if ((events & HellCyclePhysics.EV_CRASH) != 0) {
         play(bike, GhostRiderSounds.BIKE_CRASH, 1.0F, 0.9F + rnd.nextFloat() * 0.15F);
         burst(bike, ViltrumiteParticles.METEOR_EMBER.get(), nose(bike), 18, 0.35);
         burst(bike, ViltrumiteParticles.FIRE_FLARE.get(), nose(bike), 5, 0.08);
      }
      if ((events & HellCyclePhysics.EV_DRIFT_BOOST) != 0) {
         play(bike, GhostRiderSounds.BACKFIRE, 1.0F, 0.85F);
         fx.backfireTick = bike.tickCount;
         fx.backfireStrength = 1.0F;
         burst(bike, ViltrumiteParticles.FIRE_FLARE.get(), exhaust(bike), 6, 0.12);
      } else if ((events & HellCyclePhysics.EV_BACKFIRE) != 0) {
         play(bike, GhostRiderSounds.BACKFIRE, 0.8F, 0.95F + rnd.nextFloat() * 0.15F);
         fx.backfireTick = bike.tickCount;
         fx.backfireStrength = 0.7F;
         burst(bike, ViltrumiteParticles.METEOR_FLAME.get(), exhaust(bike), 5, 0.1);
      }
      if ((events & HellCyclePhysics.EV_SHIFT) != 0) {
         play(bike, GhostRiderSounds.SHIFT, 0.55F, 1.0F);
      }
      if ((events & HellCyclePhysics.EV_STOPPIE) != 0) {
         play(bike, GhostRiderSounds.HARD_STOP, 1.0F, 1.0F);
         burst(bike, ViltrumiteParticles.METEOR_EMBER.get(), front(bike), 14, 0.3);
         burst(bike, ViltrumiteParticles.FIRE_FLARE.get(), front(bike), 5, 0.07);
      }
      if ((events & HellCyclePhysics.EV_DRIFT_START) != 0 && level.isClientSide) {
         burst(bike, ViltrumiteParticles.METEOR_FLAME.get(), rear(bike), 6, 0.12);
      }
   }

   @Override
   public void removed(HellCycleEntity bike) {
      Fx fx = FX.remove(bike.getId());
      if (fx != null) {
         fx.loops.values().forEach(HellCycleClient::stop);
      }
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         FX.values().forEach(fx -> fx.loops.values().forEach(HellCycleClient::stop));
         FX.clear();
         HellCycleTrails.clear();
      }
   }

   /** 0..1 strength of the exhaust flash after a backfire (5 ticks). */
   public static float backfire(HellCycleEntity bike, float partial) {
      Fx fx = FX.get(bike.getId());
      if (fx == null) {
         return 0.0F;
      }
      float t = (bike.tickCount + partial - fx.backfireTick) / 5.0F;
      return t < 0.0F || t > 1.0F ? 0.0F : (1.0F - t) * fx.backfireStrength;
   }

   static double listenerDistanceSqr(HellCycleEntity bike) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
      return bike.distanceToSqr(ear);
   }

   // ------------------------------------------------------------------ loops

   private void loops(HellCycleEntity bike, Fx fx) {
      if (bike.burning() || listenerDistanceSqr(bike) > HellCycleEngineSound.EARSHOT * HellCycleEngineSound.EARSHOT * 0.8) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      for (HellCycleEngineSound.Layer layer : HellCycleEngineSound.Layer.values()) {
         HellCycleEngineSound s = fx.loops.get(layer);
         if (s == null || s.isStopped() || !mc.getSoundManager().isActive(s)) {
            if (s != null && !s.isStopped() && bike.tickCount % 20 != 0) {
               continue; // queued but not yet playing: give the sound engine a moment
            }
            HellCycleEngineSound fresh = new HellCycleEngineSound(bike, layer);
            fx.loops.put(layer, fresh);
            mc.getSoundManager().play(fresh);
         }
      }
   }

   private static void stop(HellCycleEngineSound s) {
      Minecraft.getInstance().getSoundManager().stop(s);
   }

   // ------------------------------------------------------------------ particles and trail

   private void particles(HellCycleEntity bike, Fx fx) {
      Level level = bike.level();
      RandomSource rnd = bike.level().getRandom();
      if (bike.forming()) {
         for (int i = 0; i < 3; i++) {
            Vec3 at = along(bike, rnd.nextDouble() * 2.0 - 1.0, rnd.nextDouble() * 0.4, (rnd.nextDouble() - 0.5) * 0.5);
            level.addParticle(ViltrumiteParticles.FIRE_FLARE.get(), at.x, at.y, at.z, 0.0, 0.04, 0.0);
         }
         return;
      }
      if (bike.burning()) {
         for (int i = 0; i < 4; i++) {
            Vec3 at = along(bike, rnd.nextDouble() * 2.0 - 1.0, rnd.nextDouble() * 0.9, (rnd.nextDouble() - 0.5) * 0.5);
            level.addParticle(ViltrumiteParticles.FIRE_FLARE.get(), at.x, at.y, at.z, 0.0, 0.06, 0.0);
         }
         Vec3 at = along(bike, rnd.nextDouble() * 2.0 - 1.0, 0.5, 0.0);
         level.addParticle(ViltrumiteParticles.METEOR_EMBER.get(), at.x, at.y, at.z, (rnd.nextDouble() - 0.5) * 0.2, 0.15, (rnd.nextDouble() - 0.5) * 0.2);
         return;
      }
      double speed = bike.visualSpeed;
      float speedF = (float)(speed / HellCycleRules.MAX_SPEED);
      boolean ground = !bike.flag(HellCycleEntity.FLAG_AIR) && bike.onGround();
      boolean drift = bike.flag(HellCycleEntity.FLAG_DRIFT);
      boolean wet = bike.isInWaterOrRain() && bike.isInWater();
      // idle: a few embers rising off the tyres
      if (rnd.nextInt(speed > 0.1 ? 2 : 5) == 0) {
         Vec3 at = rnd.nextBoolean() ? rear(bike) : front(bike);
         level.addParticle(ViltrumiteParticles.METEOR_EMBER.get(), at.x, at.y + 0.5, at.z, (rnd.nextDouble() - 0.5) * 0.05, 0.08, (rnd.nextDouble() - 0.5) * 0.05);
      }
      // the burning road trail, laid every TRAIL_STEP between this tick's and last tick's tyre contacts
      long now = level.getGameTime();
      Vec3 rearAt = rear(bike);
      Vec3 frontAt = front(bike);
      if (ground && !wet && speed > 0.12) {
         float size = 0.28F + 0.3F * Mth.clamp(speedF, 0.0F, 1.3F) + (bike.dashing() ? 0.25F : 0.0F);
         fx.lastRear = lay(fx.lastRear, rearAt, size, now);
         if (drift || bike.flag(HellCycleEntity.FLAG_SKID)) {
            fx.lastFront = lay(fx.lastFront, frontAt, size * 0.8F, now);
         } else {
            fx.lastFront = null;
         }
      } else {
         fx.lastRear = null;
         fx.lastFront = null;
      }
      if (ground && speed > 0.3) {
         // flame licks thrown back off the rear tyre, sparks at speed
         Vec3 h = heading(bike);
         int n = 1 + (int)(speedF * 1.5F) + (drift ? 2 : 0);
         for (int i = 0; i < n; i++) {
            level.addParticle(ViltrumiteParticles.METEOR_FLAME.get(), rearAt.x + (rnd.nextDouble() - 0.5) * 0.3, rearAt.y + 0.2 + rnd.nextDouble() * 0.3,
               rearAt.z + (rnd.nextDouble() - 0.5) * 0.3, -h.x * speed * 0.35, 0.03, -h.z * speed * 0.35);
         }
         if (rnd.nextFloat() < speedF * 0.6F || drift) {
            level.addParticle(ViltrumiteParticles.METEOR_EMBER.get(), rearAt.x, rearAt.y + 0.1, rearAt.z, -h.x * speed * 0.6 + (rnd.nextDouble() - 0.5) * 0.2,
               0.12 + rnd.nextDouble() * 0.1, -h.z * speed * 0.6 + (rnd.nextDouble() - 0.5) * 0.2);
         }
      }
      if (bike.dashing()) {
         Vec3 at = exhaust(bike);
         for (int i = 0; i < 2; i++) {
            level.addParticle(ViltrumiteParticles.FIRE_FLARE.get(), at.x, at.y, at.z, 0.0, 0.02, 0.0);
         }
      }
   }

   private static Vec3 lay(Vec3 last, Vec3 now, float size, long time) {
      if (last == null || last.distanceToSqr(now) > 16.0) {
         HellCycleTrails.add(now.x, now.y + 0.02, now.z, size, time);
         return now;
      }
      double d = Math.sqrt(last.distanceToSqr(now));
      if (d < TRAIL_STEP) {
         return last;
      }
      int steps = Math.min(6, (int)(d / TRAIL_STEP));
      Vec3 p = last;
      for (int i = 1; i <= steps; i++) {
         p = last.lerp(now, i * TRAIL_STEP / d);
         HellCycleTrails.add(p.x, p.y + 0.02, p.z, size, time);
      }
      return p;
   }

   // ------------------------------------------------------------------ geometry and helpers

   static Vec3 heading(HellCycleEntity bike) {
      double rad = Math.toRadians(bike.getYRot());
      return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
   }

   /** World point at fore (+) / aft (-) distance, height and left (+) offset in the bike's yaw frame. */
   static Vec3 along(HellCycleEntity bike, double fore, double up, double left) {
      double rad = Math.toRadians(bike.getYRot());
      double hx = -Math.sin(rad);
      double hz = Math.cos(rad);
      double lx = Math.cos(rad);
      double lz = Math.sin(rad);
      return new Vec3(bike.getX() + hx * fore + lx * left, bike.getY() + up, bike.getZ() + hz * fore + lz * left);
   }

   static Vec3 rear(HellCycleEntity bike) {
      return along(bike, -HellCycleRules.WHEELBASE / 2.0, 0.0, 0.0);
   }

   static Vec3 front(HellCycleEntity bike) {
      return along(bike, HellCycleRules.WHEELBASE / 2.0, 0.0, 0.0);
   }

   static Vec3 nose(HellCycleEntity bike) {
      return along(bike, HellCycleRules.NOSE + 0.2, 0.6, 0.0);
   }

   /** The drag pipes end on the right, behind the rear axle. */
   static Vec3 exhaust(HellCycleEntity bike) {
      return along(bike, -1.15, 0.55, -0.27);
   }

   private static void burst(HellCycleEntity bike, ParticleOptions type, Vec3 at, int count, double spread) {
      Level level = bike.level();
      RandomSource rnd = bike.level().getRandom();
      for (int i = 0; i < count; i++) {
         level.addParticle(type, at.x + (rnd.nextDouble() - 0.5) * 0.4, at.y + 0.1 + rnd.nextDouble() * 0.3, at.z + (rnd.nextDouble() - 0.5) * 0.4,
            (rnd.nextDouble() - 0.5) * spread * 2.0, rnd.nextDouble() * spread + 0.02, (rnd.nextDouble() - 0.5) * spread * 2.0);
      }
   }

   private static void play(HellCycleEntity bike, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      bike.level().playLocalSound(bike.getX(), bike.getY() + 0.5, bike.getZ(), sound.get(), SoundSource.NEUTRAL, volume, pitch, false);
   }
}
