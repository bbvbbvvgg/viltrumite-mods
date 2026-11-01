package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaEntity;
import dev.baranhan.viltrumitecore.entity.godzilla.GodzillaPart;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriTerrain;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The atomic breath, server side. CHARGE: the body stops and braces, the head turns to the aim at its normal rate
 * (the plates light from the tail up the spine and the mouth fills: drawn by the clients from the move time).
 * FIRE: a ray from the mouth along the head; the head follows the target at the limited beam rate, the body only
 * when the head runs out of neck. Each tick the ray marches to the first solid block (loaded chunks only, never
 * loads one) and drills a sphere there (block budget); living things inside the beam's capsule burn, the impact
 * blasts around the hit, the hit leaves hot ruin zones, grid damage and structural impacts. BREATH_END: the beam
 * dies down and the plates cool.
 */
final class GodzillaBreath {
   /** Hardest block the beam melts (obsidian and the like go, bedrock never). */
   static final float MELT_HARDNESS = 80.0F;
   static final int ENTITY_EVERY = 5;
   static final int HOT_EVERY = 8;

   @Nullable
   private UUID target;
   @Nullable
   private Vec3 point;
   private Vec3 aim = Vec3.ZERO;
   private float sweepSide = 1.0F;
   private int drilled;
   private int fired;

   /** A new breath: at a target (tracked) or at a fixed point (raked across). */
   void begin(GodzillaSession s, GodzillaEntity e, @Nullable LivingEntity target, @Nullable Vec3 point) {
      this.target = target == null ? null : target.getUUID();
      this.point = point;
      this.aim = point != null ? point : target != null ? centre(target) : e.position().add(GodzillaEntity.forward(e.getYRot()).scale(100.0));
      this.sweepSide = s.random.nextBoolean() ? 1.0F : -1.0F;
      this.drilled = 0;
      this.fired = 0;
   }

   int drilled() {
      return this.drilled;
   }

   private static Vec3 centre(Entity t) {
      return t.position().add(0.0, t.getBbHeight() * 0.5, 0.0);
   }

   /** Not at a hero standing in its mouth (the claws are for that). */
   static boolean worthIt(GodzillaEntity e, Vec3 at) {
      return e.mouth().distanceTo(at) > 20.0;
   }

   /** Where the beam wants to point this tick. */
   private Vec3 aimPoint(ServerLevel level, GodzillaEntity e, float t, float length) {
      Entity who = this.target == null ? null : level.getEntity(this.target);
      if (who instanceof LivingEntity living && living.isAlive() && who.distanceToSqr(e) < GodzillaRules.BEAM_RANGE * GodzillaRules.BEAM_RANGE) {
         // lead a moving target a little
         this.aim = centre(who).add(who.getDeltaMovement().scale(6.0));
         return this.aim;
      }
      if (this.point != null && length > 0.0F) {
         // rake across the building: from one side to the other over the fire
         Vec3 lf = GodzillaEntity.left(e.getYRot());
         double u = Mth.clamp(t / length, 0.0F, 1.0F) - 0.5;
         return this.point.add(lf.scale(this.sweepSide * u * 44.0)).add(0.0, -u * 10.0, 0.0);
      }
      return this.point != null ? this.point : this.aim;
   }

   void charge(ServerLevel level, GodzillaSession s, GodzillaEntity e, int t) {
      Vec3 a = this.aimPoint(level, e, 0.0F, 0.0F);
      e.lookAt(a, false);
      e.faceTowards(a);
      if (t == GodzillaRules.CHARGE_SPINE) {
         // the streets in the line of fire empty
         CityManager city = CityManager.of(level);
         if (city != null) {
            city.danger(a.x, a.y, a.z, 40.0F, 200);
         }
      }
   }

   void fire(ServerLevel level, GodzillaSession s, GodzillaEntity e, int t) {
      int length = e.fireLength();
      Vec3 a = this.aimPoint(level, e, t, length);
      e.lookAt(a, true);
      e.faceTowards(a);
      boolean rage = s.rage();
      boolean finale = s.phase == GodzillaPhase.FINALE;
      Vec3 from = e.mouth();
      Vec3 dir = e.headDir();
      Hit hit = march(level, from, dir, GodzillaRules.BEAM_RANGE);
      e.setBeam(hit.end);
      this.fired++;
      if (hit.block) {
         float r = rage ? GodzillaRules.DRILL_RADIUS_RAGE : GodzillaRules.DRILL_RADIUS;
         int budget = rage ? GodzillaRules.DRILL_BLOCKS_RAGE : GodzillaRules.DRILL_BLOCKS;
         if (finale) {
            r += 0.8F;
         }
         Vec3 c = hit.end.add(dir.scale(r * 0.4));
         this.drilled += s.destruction.smashNow(level, c, r, 1.0, MELT_HARDNESS, dir.scale(0.5), 0.012F, budget, "beam");
         if (t % HOT_EVERY == 0) {
            GodzillaNet.fx(level, GodzillaFxS2CPacket.HOT, hit.end, dir, r + 2.0F, GodzillaRules.HOT_TICKS);
            s.destruction.report(level, hit.end, r * 2.5F, Math.max(60, this.drilled), "beam", java.util.List.of(BlockPos.containing(hit.end)), false);
            CityManager city = CityManager.of(level);
            if (city != null) {
               city.blast(hit.end.x, hit.end.y, hit.end.z, 14.0F);
            }
         }
      }
      if (t % ENTITY_EVERY == 0) {
         float dmg = (rage ? GodzillaRules.BEAM_DAMAGE_RAGE : GodzillaRules.BEAM_DAMAGE) * ENTITY_EVERY * (finale ? 1.3F : 1.0F);
         float radius = rage ? GodzillaRules.BEAM_RADIUS_RAGE : GodzillaRules.BEAM_RADIUS;
         this.burnCapsule(level, s, e, from, hit.end, radius, dmg);
         if (hit.block) {
            GodzillaActions.hurtSphere(level, s, e, hit.end, GodzillaRules.IMPACT_RADIUS, GodzillaRules.IMPACT_DAMAGE * ENTITY_EVERY, 0.6, true,
               GodzillaDamage.breath(level, e), null);
         }
         if (t % (ENTITY_EVERY * 4) == 0) {
            GodzillaNet.fx(level, GodzillaFxS2CPacket.BEAM_BLAST, hit.end, dir, rage ? 1.3F : 1.0F, 0);
         }
      }
   }

   void end(GodzillaEntity e) {
      e.setBeam(null);
   }

   /** Living things inside the beam (a capsule from the mouth to the hit) burn. */
   private void burnCapsule(ServerLevel level, GodzillaSession s, GodzillaEntity e, Vec3 from, Vec3 to, float radius, float damage) {
      AABB box = new AABB(from, to).inflate(radius + 2.0);
      Vec3 seg = to.subtract(from);
      double len2 = Math.max(1.0E-6, seg.lengthSqr());
      for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != e && x.isAlive() && !(x instanceof GodzillaEntity))) {
         Vec3 c = centre(living);
         double u = Mth.clamp(c.subtract(from).dot(seg) / len2, 0.0, 1.0);
         Vec3 near = from.add(seg.scale(u));
         double reach = radius + living.getBbWidth() * 0.5 + living.getBbHeight() * 0.25;
         if (near.distanceToSqr(c) > reach * reach) {
            continue;
         }
         living.invulnerableTime = 0;
         if (living.hurt(GodzillaDamage.breath(level, e), damage)) {
            living.setSecondsOnFire(5);
            s.noteHit(living);
            if (HeroRegistry.allowsImpulse(living)) {
               Vec3 push = seg.normalize().scale(0.6);
               living.push(push.x, 0.15, push.z);
               living.hurtMarked = true;
            }
         }
      }
   }

   /** Result of a beam march: where it stops and whether a block stopped it. */
   record Hit(Vec3 end, boolean block) {
   }

   /**
    * Voxel march from {@code from} along {@code dir} (unit) up to {@code range}: the first solid block (not air, not
    * a fluid). Stops at the first unloaded chunk (never loads one) and outside the build height.
    */
   static Hit march(ServerLevel level, Vec3 from, Vec3 dir, double range) {
      int x = Mth.floor(from.x);
      int y = Mth.floor(from.y);
      int z = Mth.floor(from.z);
      int stepX = dir.x > 0 ? 1 : -1;
      int stepY = dir.y > 0 ? 1 : -1;
      int stepZ = dir.z > 0 ? 1 : -1;
      double tDeltaX = Math.abs(dir.x) < 1.0E-9 ? Double.MAX_VALUE : Math.abs(1.0 / dir.x);
      double tDeltaY = Math.abs(dir.y) < 1.0E-9 ? Double.MAX_VALUE : Math.abs(1.0 / dir.y);
      double tDeltaZ = Math.abs(dir.z) < 1.0E-9 ? Double.MAX_VALUE : Math.abs(1.0 / dir.z);
      double tMaxX = Math.abs(dir.x) < 1.0E-9 ? Double.MAX_VALUE : ((stepX > 0 ? x + 1 - from.x : from.x - x) * tDeltaX);
      double tMaxY = Math.abs(dir.y) < 1.0E-9 ? Double.MAX_VALUE : ((stepY > 0 ? y + 1 - from.y : from.y - y) * tDeltaY);
      double tMaxZ = Math.abs(dir.z) < 1.0E-9 ? Double.MAX_VALUE : ((stepZ > 0 ? z + 1 - from.z : from.z - z) * tDeltaZ);
      double t = 0.0;
      LevelChunk chunk = null;
      int ccx = Integer.MIN_VALUE;
      int ccz = Integer.MIN_VALUE;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int minY = level.getMinBuildHeight();
      int maxY = level.getMaxBuildHeight();
      // skip the first few blocks: the mouth sits inside the kaiju's own (block-free) volume
      while (t <= range) {
         if (y < minY && stepY < 0 || y >= maxY && stepY > 0) {
            break;
         }
         if (y >= minY && y < maxY && t > 2.0) {
            if (x >> 4 != ccx || z >> 4 != ccz) {
               ccx = x >> 4;
               ccz = z >> 4;
               chunk = level.getChunkSource().getChunkNow(ccx, ccz);
            }
            if (chunk == null) {
               return new Hit(from.add(dir.scale(t)), false);
            }
            pos.set(x, y, z);
            BlockState state = chunk.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
               return new Hit(from.add(dir.scale(t)), true);
            }
         }
         if (tMaxX < tMaxY && tMaxX < tMaxZ) {
            t = tMaxX;
            tMaxX += tDeltaX;
            x += stepX;
         } else if (tMaxY < tMaxZ) {
            t = tMaxY;
            tMaxY += tDeltaY;
            y += stepY;
         } else {
            t = tMaxZ;
            tMaxZ += tDeltaZ;
            z += stepZ;
         }
      }
      return new Hit(from.add(dir.scale(Math.min(t, range))), false);
   }

   /** A tall building in front (for a breath while exploring): a point on it at 60 % of its height, or null. */
   @Nullable
   static Vec3 towerAhead(ServerLevel level, GodzillaEntity e, RandomSource random) {
      Vec3 best = null;
      double bestH = 0.35 * GodzillaRules.HEIGHT;
      float yaw = e.getYRot();
      for (int i = 0; i < 24; i++) {
         float a = yaw + (random.nextFloat() - 0.5F) * 80.0F;
         double d = 50.0 + random.nextDouble() * 100.0;
         Vec3 fw = GodzillaEntity.forward(a);
         double x = e.getX() + fw.x * d;
         double z = e.getZ() + fw.z * d;
         int top = ChitauriTerrain.surface(level, x, z);
         if (top == ChitauriTerrain.UNKNOWN) {
            continue;
         }
         double h = top - e.getY();
         if (h > bestH) {
            bestH = h;
            best = new Vec3(x, e.getY() + h * 0.6, z);
         }
      }
      return best;
   }

   static boolean isPart(Entity x) {
      return x instanceof GodzillaPart;
   }
}
