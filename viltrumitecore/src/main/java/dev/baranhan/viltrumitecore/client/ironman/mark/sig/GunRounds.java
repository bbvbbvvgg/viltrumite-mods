package dev.baranhan.viltrumitecore.client.ironman.mark.sig;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * War Machine rounds on the client (PR 19 iteration 2). The server hit-scans
 * every shot and syncs the end point; here each shot becomes a visible round
 * that travels {@link #SPEED} blocks per tick from the turret muzzle: a hot
 * white head with a fading orange streak, a muzzle flash and smoke. Near
 * another player's camera it plays a whiz at the closest approach (louder the
 * closer it passes). At the end point it lands: on a block, sparks thrown off
 * the face, chips of that block, dust and a flash with an impact sound (one
 * variant rings off as a ricochet); in a body, crit sparks and a hit sound.
 * Purely cosmetic and local; damage stays the server's hit-scan.
 */
public final class GunRounds {
   /** Round speed (blocks per tick): fast, but visible for a few frames. */
   static final double SPEED = 7.5;
   /** Streak length behind the head (blocks). */
   private static final double STREAK = 2.6;
   /** A round passing closer than this (blocks) to the camera whizzes. */
   private static final double WHIZ_RADIUS = 4.0;
   private static final int SPARK_MAX = 240;
   private static final int ROUND_MAX = 96;
   private static final List<Round> ROUNDS = new ArrayList<>();
   private static final List<Spark> SPARKS = new ArrayList<>();
   private static final List<Flash> FLASHES = new ArrayList<>();

   private GunRounds() {
   }

   private static final class Round {
      final Vec3 from;
      final Vec3 dir;
      final double length;
      final int shooterId;
      int age;
      boolean whizzed;

      Round(Vec3 from, Vec3 to, int shooterId) {
         Vec3 d = to.subtract(from);
         this.length = d.length();
         this.dir = this.length < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : d.scale(1.0 / this.length);
         this.from = from;
         this.shooterId = shooterId;
      }

      Vec3 at(double distance) {
         return this.from.add(this.dir.scale(Math.min(distance, this.length)));
      }
   }

   private static final class Spark {
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      int age;
      final int life;
      final boolean ember;

      Spark(Vec3 pos, Vec3 vel, int life, boolean ember) {
         this.pos = pos;
         this.prev = pos;
         this.vel = vel;
         this.life = life;
         this.ember = ember;
      }
   }

   private record Flash(Vec3 pos, float size, int life, int[] age) {
   }

   static void clear() {
      ROUNDS.clear();
      SPARKS.clear();
      FLASHES.clear();
   }

   static boolean busy() {
      return !ROUNDS.isEmpty() || !SPARKS.isEmpty() || !FLASHES.isEmpty();
   }

   /** One shot from the muzzle to the synced end point. */
   public static void fire(ClientLevel level, Entity shooter, Vec3 muzzle, Vec3 point) {
      if (ROUNDS.size() < ROUND_MAX) {
         ROUNDS.add(new Round(muzzle, point, shooter.getId()));
      }

      FLASHES.add(new Flash(muzzle, 0.42F, 2, new int[1]));
      RandomSource random = level.random;
      Vec3 dir = point.subtract(muzzle).normalize();
      level.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, dir.x * 0.05 + (random.nextDouble() - 0.5) * 0.02, 0.02,
         dir.z * 0.05 + (random.nextDouble() - 0.5) * 0.02);
      for (int i = 0; i < 2; i++) {
         spark(muzzle, dir.scale(0.25 + random.nextDouble() * 0.2).add(jitter(random, 0.08)), 3 + random.nextInt(2), false);
      }
   }

   static void tick(ClientLevel level) {
      Minecraft client = Minecraft.getInstance();
      Entity cameraEntity = client.getCameraEntity();
      Vec3 ear = cameraEntity == null ? null : cameraEntity.getEyePosition();
      Iterator<Round> rounds = ROUNDS.iterator();
      while (rounds.hasNext()) {
         Round round = rounds.next();
         double before = round.age * SPEED;
         round.age++;
         double after = round.age * SPEED;
         if (ear != null && !round.whizzed && cameraEntity.getId() != round.shooterId) {
            whiz(level, round, before, Math.min(after, round.length), ear);
         }

         if (after >= round.length) {
            land(level, round);
            rounds.remove();
         }
      }

      Iterator<Spark> sparks = SPARKS.iterator();
      while (sparks.hasNext()) {
         Spark spark = sparks.next();
         spark.prev = spark.pos;
         spark.pos = spark.pos.add(spark.vel);
         spark.vel = new Vec3(spark.vel.x * 0.86, spark.vel.y * 0.86 - (spark.ember ? 0.012 : 0.045), spark.vel.z * 0.86);
         spark.age++;
         if (spark.age > spark.life) {
            sparks.remove();
         }
      }

      FLASHES.removeIf(flash -> ++flash.age()[0] > flash.life());
   }

   /** Closest approach of this tick's flight segment to the listener. */
   private static void whiz(ClientLevel level, Round round, double from, double to, Vec3 ear) {
      double along = ear.subtract(round.from).dot(round.dir);
      if (along < from - 0.5 || along > to + 0.5 || along > round.length - 1.0) {
         return;
      }

      Vec3 closest = round.at(Mth.clamp(along, from, to));
      double distance = closest.distanceTo(ear);
      if (distance > WHIZ_RADIUS) {
         return;
      }

      round.whizzed = true;
      float near = 1.0F - (float)(distance / WHIZ_RADIUS);
      level.playLocalSound(closest.x, closest.y, closest.z, IronManMarkSounds.BULLET_WHIZ.get(), SoundSource.PLAYERS, 0.35F + 0.75F * near,
         0.9F + level.random.nextFloat() * 0.25F, false);
   }

   private static void land(ClientLevel level, Round round) {
      RandomSource random = level.random;
      Vec3 point = round.at(round.length);
      Vec3 start = point.subtract(round.dir.scale(0.6));
      Vec3 end = point.add(round.dir.scale(0.6));
      Entity shooter = level.getEntity(round.shooterId);
      LivingEntity body = null;
      AABB probe = new AABB(point, point).inflate(0.6);
      for (Entity entity : level.getEntities(shooter, probe, e -> e instanceof LivingEntity && e.isAlive())) {
         if (entity.getBoundingBox().inflate(0.3).contains(point)) {
            body = (LivingEntity)entity;
            break;
         }
      }

      if (body != null) {
         for (int i = 0; i < 6; i++) {
            Vec3 v = round.dir.scale(-0.1).add(jitter(random, 0.25));
            level.addParticle(ParticleTypes.CRIT, point.x, point.y, point.z, v.x, v.y + 0.1, v.z);
         }

         for (int i = 0; i < 4; i++) {
            spark(point, round.dir.scale(-0.15).add(jitter(random, 0.18)), 4 + random.nextInt(3), false);
         }

         FLASHES.add(new Flash(point, 0.22F, 1, new int[1]));
         level.playLocalSound(point.x, point.y, point.z, IronManMarkSounds.BULLET_HIT.get(), SoundSource.PLAYERS, 0.8F, 0.9F + random.nextFloat() * 0.2F, false);
         return;
      }

      BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return;
      }

      BlockPos pos = hit.getBlockPos();
      BlockState state = level.getBlockState(pos);
      Vec3 at = hit.getLocation();
      Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
      // Reflected direction: the sparks glance off the face.
      Vec3 reflect = round.dir.subtract(normal.scale(2.0 * round.dir.dot(normal)));
      for (int i = 0; i < 7; i++) {
         Vec3 v = reflect.scale(0.25 + random.nextDouble() * 0.3).add(normal.scale(0.1)).add(jitter(random, 0.16));
         spark(at.add(normal.scale(0.02)), v, 4 + random.nextInt(5), false);
      }

      for (int i = 0; i < 3; i++) {
         spark(at.add(normal.scale(0.03)), normal.scale(0.05).add(jitter(random, 0.05)), 10 + random.nextInt(8), true);
      }

      if (!state.isAir()) {
         BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, state);
         for (int i = 0; i < 6; i++) {
            Vec3 v = normal.scale(0.12 + random.nextDouble() * 0.12).add(jitter(random, 0.12));
            level.addParticle(chips, at.x + normal.x * 0.05, at.y + normal.y * 0.05, at.z + normal.z * 0.05, v.x, v.y + 0.05, v.z);
         }
      }

      level.addParticle(ParticleTypes.SMOKE, at.x + normal.x * 0.1, at.y + normal.y * 0.1, at.z + normal.z * 0.1, normal.x * 0.02, 0.02, normal.z * 0.02);
      if (random.nextInt(3) == 0) {
         level.addParticle(ParticleTypes.POOF, at.x + normal.x * 0.15, at.y + normal.y * 0.15, at.z + normal.z * 0.15, normal.x * 0.03, 0.01, normal.z * 0.03);
      }

      FLASHES.add(new Flash(at.add(normal.scale(0.03)), 0.28F, 1, new int[1]));
      level.playLocalSound(at.x, at.y, at.z, IronManMarkSounds.BULLET_IMPACT.get(), SoundSource.PLAYERS, 0.75F, 0.88F + random.nextFloat() * 0.24F, false);
   }

   private static void spark(Vec3 pos, Vec3 vel, int life, boolean ember) {
      if (SPARKS.size() < SPARK_MAX) {
         SPARKS.add(new Spark(pos, vel, life, ember));
      }
   }

   private static Vec3 jitter(RandomSource random, double size) {
      return new Vec3((random.nextDouble() - 0.5) * 2.0 * size, (random.nextDouble() - 0.5) * 2.0 * size, (random.nextDouble() - 0.5) * 2.0 * size);
   }

   /** Additive pass inside SignatureVfx (POSITION_COLOR quads, camera-relative). */
   static void draw(BufferBuilder buffer, Vec3 cameraPos, Camera camera, float partialTick) {
      for (Round round : ROUNDS) {
         double head = Math.min(round.length, (round.age + partialTick) * SPEED);
         double tail = Math.max(0.0, head - STREAK);
         Vec3 h = round.at(head);
         // Streak: wide dim orange, then a hot thin core over the last block, a white head.
         PixelVfx.beamDots(buffer, cameraPos, camera, round.at(tail), h, 0.14F, 0.03F, 255, 150, 60, 110);
         PixelVfx.beamDots(buffer, cameraPos, camera, round.at(Math.max(tail, head - 1.0)), h, 0.08F, 0.022F, 255, 225, 150, 220);
         PixelVfx.billboardPixel(buffer, cameraPos, camera, h, 0.06F, 255, 250, 225, 255);
      }

      for (Spark spark : SPARKS) {
         Vec3 at = spark.prev.lerp(spark.pos, partialTick);
         float k = 1.0F - spark.age / (float)(spark.life + 1);
         if (spark.ember) {
            PixelVfx.billboardPixel(buffer, cameraPos, camera, at, 0.03F, 255, 120, 40, (int)(200 * k));
         } else {
            PixelVfx.beamDots(buffer, cameraPos, camera, spark.prev, at, 0.05F, 0.02F, 255, 200, 90, (int)(230 * k));
            PixelVfx.billboardPixel(buffer, cameraPos, camera, at, 0.028F, 255, 240, 170, (int)(255 * k));
         }
      }

      for (Flash flash : FLASHES) {
         float k = 1.0F - flash.age()[0] / (float)(flash.life() + 1);
         PixelVfx.crossGlow(buffer, cameraPos, camera, flash.pos(), flash.size() * (0.7F + 0.3F * k), 255, 215, 130, (int)(235 * k));
      }
   }
}
