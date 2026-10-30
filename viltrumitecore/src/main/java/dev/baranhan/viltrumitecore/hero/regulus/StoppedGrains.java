package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The grains of «Застывший песок» (spec §5.3): each is a server-simulated segment (no entity) that
 * sweeps its path every tick against entity boxes and blocks. Clients replay the same pattern from
 * the shot packet ({@link #direction} is shared and deterministic) and are told when a grain stops
 * early, so nothing flies on a client that does not exist on the server.
 */
public final class StoppedGrains {
   /** Grain hit box half-size (blocks). */
   static final double RADIUS = 0.22;

   private StoppedGrains() {
   }

   public static final class Grain {
      final int index;
      Vec3 pos;
      final Vec3 vel;
      double travelled;
      int pierce;
      final float damage;
      final Set<UUID> hit = new HashSet<>();
      boolean dead;

      Grain(int index, Vec3 pos, Vec3 vel, int pierce, float damage) {
         this.index = index;
         this.pos = pos;
         this.vel = vel;
         this.pierce = pierce;
         this.damage = damage;
      }
   }

   /**
    * Direction of grain {@code i} of {@code count} around {@code look}: a filled fan (centre, inner
    * ring, outer ring) at up to {@code spreadDeg}, rotated by the seed. Pure; client and server agree.
    */
   public static Vec3 direction(Vec3 look, int i, int count, double spreadDeg, int seed) {
      Vec3 f = look.normalize();
      Vec3 up = Math.abs(f.y) > 0.98 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      Vec3 r = f.cross(up).normalize();
      Vec3 u = r.cross(f).normalize();
      if (i == 0) {
         return f;
      }

      boolean inner = i <= Math.min(4, count - 1);
      int ringIndex = inner ? i - 1 : i - 5;
      int ringCount = inner ? Math.min(4, count - 1) : Math.max(1, count - 5);
      double spin = (seed % 360) * Math.PI / 180.0 + (inner ? 0.0 : Math.PI / ringCount);
      double a = spin + ringIndex * Math.PI * 2.0 / ringCount;
      double cone = Math.toRadians(spreadDeg) * (inner ? 0.45 : 1.0);
      double t = Math.tan(cone);
      return f.add(r.scale(Math.cos(a) * t)).add(u.scale(Math.sin(a) * t * 0.6)).normalize();
   }

   /** The hand point the grains leave from (both sides use it for the first frame). */
   public static Vec3 muzzle(Vec3 eye, Vec3 look) {
      Vec3 r = look.cross(new Vec3(0.0, 1.0, 0.0));
      r = r.lengthSqr() < 1.0E-6 ? Vec3.ZERO : r.normalize();
      return eye.add(look.scale(0.7)).add(r.scale(0.3)).add(0.0, -0.25, 0.0);
   }

   static void fire(ServerPlayer player, RegulusState state, Vec3 look, int material) {
      ServerLevel level = (ServerLevel) player.level();
      Vec3 from = muzzle(player.getEyePosition(), look);
      float damage = RegulusRules.sandDamage(state.lion);
      for (int i = 0; i < RegulusRules.SAND_GRAINS; i++) {
         Vec3 dir = direction(look, i, RegulusRules.SAND_GRAINS, RegulusRules.SAND_SPREAD_DEGREES, state.seed);
         state.grains.add(new Grain(i, from, dir.scale(RegulusRules.SAND_SPEED), RegulusRules.SAND_PIERCE, damage));
      }

      state.debrisBudget = RegulusRules.SAND_BLOCK_BUDGET;
      RegulusNet.fx(level, RegulusFxS2CPacket.SAND_SHOT, player, -1, from, look, RegulusRules.SAND_GRAINS, (state.seed & 0x7FFF) | (material << 15));
   }

   static void tick(ServerPlayer player, RegulusState state) {
      if (state.grains.isEmpty()) {
         return;
      }

      ServerLevel level = (ServerLevel) player.level();
      Iterator<Grain> it = state.grains.iterator();
      while (it.hasNext()) {
         Grain grain = it.next();
         if (RegulusCombat.timeStoppedAt(level, grain.pos)) {
            continue;
         }

         step(player, state, level, grain);
         if (grain.dead) {
            it.remove();
         }
      }
   }

   private static void step(ServerPlayer player, RegulusState state, ServerLevel level, Grain grain) {
      Vec3 from = grain.pos;
      double left = RegulusRules.SAND_RANGE - grain.travelled;
      Vec3 delta = grain.vel.length() > left ? grain.vel.normalize().scale(left) : grain.vel;
      Vec3 to = from.add(delta);
      BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 end = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : to;
      AABB sweep = new AABB(from, end).inflate(RADIUS + 1.0);
      for (Entity entity : level.getEntities(player, sweep)) {
         if (grain.pierce <= 0 || grain.hit.contains(entity.getUUID()) || !RegulusCombat.hittable(player, entity, state)) {
            continue;
         }

         Optional<Vec3> clip = entity.getBoundingBox().inflate(RADIUS).clip(from, end);
         if (clip.isEmpty() && !entity.getBoundingBox().inflate(RADIUS).contains(from)) {
            continue;
         }

         grain.hit.add(entity.getUUID());
         LivingEntity target = (LivingEntity) entity;
         target.invulnerableTime = 0;
         if (RegulusCombat.hit(player, target, grain.damage)) {
            RegulusCombat.push(target, grain.vel, 0.12, 0.02);
            Vec3 at = clip.orElse(from);
            RegulusSounds.play(level, at, RegulusSounds.GRAIN_HIT, 0.5F, 0.9F + level.random.nextFloat() * 0.3F);
            RegulusNet.fx(level, RegulusFxS2CPacket.GRAIN_HIT, player, target.getId(), at, grain.vel, 1.0F, grain.index);
         }

         grain.pierce--;
      }

      grain.travelled += from.distanceTo(end);
      grain.pos = end;
      if (block.getType() == HitResult.Type.BLOCK) {
         BlockPos pos = block.getBlockPos();
         boolean broke = state.debrisBudget > 0 && RegulusCombat.breakBlock(player, level, pos, RegulusRules.SAND_BLOCK_HARDNESS);
         if (broke) {
            state.debrisBudget--;
            grain.pierce--;
         }

         if (!broke || grain.pierce <= 0) {
            stop(player, level, grain, end);
         }

         return;
      }

      if (grain.pierce <= 0 || grain.travelled >= RegulusRules.SAND_RANGE - 1.0E-3) {
         stop(player, level, grain, end);
      }
   }

   private static void stop(ServerPlayer player, ServerLevel level, Grain grain, Vec3 at) {
      grain.dead = true;
      if (level.random.nextInt(3) == 0) {
         RegulusSounds.play(level, at, RegulusSounds.GRAIN_STOP, 0.35F, 1.1F + level.random.nextFloat() * 0.3F);
      }

      RegulusNet.fx(level, RegulusFxS2CPacket.GRAIN_STOP, player, -1, at, Vec3.ZERO, 0.0F, grain.index);
   }
}
