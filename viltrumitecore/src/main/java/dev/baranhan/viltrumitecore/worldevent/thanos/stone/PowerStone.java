package dev.baranhan.viltrumitecore.worldevent.thanos.stone;

import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterDestruction;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosCombat;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Power;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Purple. Melee (cleave, hammer), the charged punch with its crater, the ground ring (jump or
 * fly over it) and the crush of the Time+Power combo. Blocks only go through the shared
 * destruction boundary (mobGriefing, soft blocks, the carve budget).
 */
public final class PowerStone {
   /** Hardest block the punch crater removes (stone bricks, concrete; never obsidian). */
   private static final float CRATER_HARDNESS = 3.0F;
   private static final int MAX_WAVES = 4;
   private final ThanosCombat c;
   private final List<Wave> waves = new ArrayList<>();

   public PowerStone(ThanosCombat combat) {
      this.c = combat;
   }

   private static final class Wave {
      final Vec3 center;
      final float damage;
      final int shape;
      final Set<UUID> hit = new HashSet<>();
      int age;

      Wave(Vec3 center, float damage, int shape) {
         this.center = center;
         this.damage = damage;
         this.shape = shape;
      }
   }

   public void tick() {
      for (Iterator<Wave> it = this.waves.iterator(); it.hasNext(); ) {
         Wave w = it.next();
         w.age++;
         double r = Power.waveRadius(w.age);
         for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, w.center, r + Power.WAVE_BAND)) {
            if (w.hit.contains(e.getUUID())) {
               continue;
            }

            double dx = e.getX() - w.center.x;
            double dz = e.getZ() - w.center.z;
            double scaled = r * ThanosStoneRules.Reality.shapeScale(Math.atan2(dz, dx), w.shape);
            double distance = Math.sqrt(dx * dx + dz * dz);
            double height = e.getY() - w.center.y;
            if (Power.waveHits(distance * (r / Math.max(0.5, scaled)), height, w.age)) {
               w.hit.add(e.getUUID());
               ThanosTargets.hurt(this.c.thanos, e, w.damage, 1.0F);
               Vec3 out = new Vec3(dx, 0.0, dz).normalize();
               ThanosTargets.push(e, new Vec3(out.x * Power.WAVE_KNOCKBACK, 0.55, out.z * Power.WAVE_KNOCKBACK));
            }
         }

         if (r >= Power.WAVE_MAX) {
            it.remove();
         }
      }
   }

   /** Wide backhand arc in front of him. */
   public void cleave(float damage) {
      Vec3 f = this.c.facing();
      Vec3 chest = this.c.chest();
      int hits = 0;
      for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, chest, 5.5)) {
         Vec3 to = e.position().subtract(this.c.thanos.position());
         Vec3 flat = new Vec3(to.x, 0.0, to.z);
         double d = flat.length();
         if (d < 1.5 || flat.normalize().dot(f) > 0.15) {
            if (ThanosTargets.hurt(this.c.thanos, e, damage, 1.0F)) {
               hits++;
            }

            Vec3 side = d < 1.0E-3 ? f : flat.normalize();
            ThanosTargets.push(e, new Vec3(side.x * 1.1 + f.x * 0.4, 0.35, side.z * 1.1 + f.z * 0.4));
         }
      }

      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.MELEE_HIT, this.c.thanos.getId(), -1, chest.add(f.scale(2.4)), f, hits, 0);
      ThanosSounds.play(this.c.level, this.c.thanos.position(), hits > 0 ? ThanosSounds.HIT : ThanosSounds.SWING, 1.4F, 0.8F, 40.0);
   }

   /** Both fists down in front of him: a short shock ring and a dent in the ground. */
   public void hammer(float damage) {
      Vec3 at = this.ground(this.c.thanos.position().add(this.c.facing().scale(2.6)));
      for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, at, 3.6)) {
         ThanosTargets.hurt(this.c.thanos, e, damage, 1.0F);
         Vec3 out = e.position().subtract(at);
         Vec3 flat = new Vec3(out.x, 0.0, out.z);
         flat = flat.lengthSqr() < 1.0E-4 ? this.c.facing() : flat.normalize();
         ThanosTargets.push(e, new Vec3(flat.x * 0.9, 0.75, flat.z * 0.9));
      }

      HulkbusterDestruction.soft(this.c.level, at.add(0.0, -0.5, 0.0), 1.6, HulkbusterDestruction.STEP_HARDNESS);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SLAM, this.c.thanos.getId(), -1, at, Vec3.ZERO, 3.6F, 0);
      ThanosSounds.play(this.c.level, at, ThanosSounds.SLAM, 2.0F, 0.9F, 64.0);
   }

   /**
    * The charged punch: a cone in front of the fist hurling everything back; with
    * {@code crater} the ground in front of the fist caves in.
    */
   public void punch(Vec3 origin, Vec3 dir, float damage, double reach, boolean crater) {
      Vec3 d = dir.lengthSqr() < 1.0E-6 ? this.c.facing() : dir.normalize();
      for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, origin, reach)) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
         double distance = to.length();
         double dot = distance < 1.0E-3 ? 1.0 : to.scale(1.0 / distance).dot(d);
         if (distance > reach || !Power.inPunchCone(distance * (Power.PUNCH_REACH / Math.max(1.0, reach)), dot)) {
            continue;
         }

         ThanosTargets.hurt(this.c.thanos, e, damage, 1.0F);
         double kb = Power.punchKnockback(distance);
         ThanosTargets.push(e, new Vec3(d.x * kb, 0.45 + Math.max(0.0, d.y) * kb, d.z * kb));
      }

      Vec3 impact = origin.add(d.scale(Math.min(reach, 3.0)));
      if (crater) {
         HitResult hit = this.c.level.clip(new ClipContext(origin, origin.add(d.scale(reach)).add(0.0, -2.5, 0.0), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, this.c.thanos));
         Vec3 ground = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : this.ground(origin.add(d.scale(3.5)));
         impact = ground;
         HulkbusterDestruction.crater(this.c.level, ground, Power.PUNCH_CRATER, CRATER_HARDNESS);
         ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.FISSURES, this.c.thanos.getId(), -1, ground, d, 5.0F, this.c.level.random.nextInt());
      }

      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.POWER_PUNCH, this.c.thanos.getId(), -1, impact, d, (float)reach, crater ? 1 : 0);
      ThanosSounds.play(this.c.level, impact, ThanosSounds.POWER_IMPACT, 2.4F, 0.9F, 96.0);
   }

   /** A ring along the ground from {@code center}, bent into {@code shape} by the Reality stone. */
   public void wave(Vec3 center, float damage, int shape) {
      Vec3 at = this.ground(center);
      if (this.waves.size() >= MAX_WAVES) {
         this.waves.remove(0);
      }

      this.waves.add(new Wave(at, damage, shape));
      HulkbusterDestruction.soft(this.c.level, at.add(0.0, -0.5, 0.0), 2.2, HulkbusterDestruction.STEP_HARDNESS);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.POWER_WAVE, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)Power.WAVE_MAX, shape);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.FISSURES, this.c.thanos.getId(), -1, at, Vec3.ZERO, 9.0F, this.c.level.random.nextInt());
      ThanosSounds.play(this.c.level, at, ThanosSounds.POWER_WAVE, 2.4F, 0.85F, 96.0);
   }

   /** Time+Power: everything held in the dome is crushed down into the ground. */
   public void crush(Vec3 center, double radius, float damage) {
      Vec3 at = this.ground(center);
      for (LivingEntity e : ThanosTargets.victims(this.c.level, this.c.thanos, at, radius)) {
         ThanosTargets.hurt(this.c.thanos, e, damage, 1.0F);
         ThanosTargets.push(e, new Vec3(0.0, -1.2, 0.0));
      }

      HulkbusterDestruction.crater(this.c.level, at, 3.4, CRATER_HARDNESS);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.SLAM, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)radius, 1);
      ThanosTargets.fx(this.c.level, ThanosFxS2CPacket.FISSURES, this.c.thanos.getId(), -1, at, Vec3.ZERO, (float)radius, this.c.level.random.nextInt());
      ThanosSounds.play(this.c.level, at, ThanosSounds.POWER_IMPACT, 2.8F, 0.7F, 96.0);
   }

   /** The ground right below a point (up to 6 blocks down), or the point itself. */
   private Vec3 ground(Vec3 at) {
      HitResult hit = this.c.level.clip(new ClipContext(at.add(0.0, 1.5, 0.0), at.add(0.0, -6.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
         this.c.thanos));
      return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
   }

   public boolean busy() {
      return !this.waves.isEmpty();
   }

   public void clear() {
      this.waves.clear();
   }

   /** For tests and status: rings still running. */
   public int waves() {
      return this.waves.size();
   }
}