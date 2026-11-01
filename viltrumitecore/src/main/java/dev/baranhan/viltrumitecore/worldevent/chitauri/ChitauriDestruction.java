package dev.baranhan.viltrumitecore.worldevent.chitauri;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.danger.DangerMap;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriMob;
import dev.baranhan.viltrumitecore.hero.CarveJobs;
import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every block the Chitauri break goes through here: mobGriefing (HeroDestruction.canDestroy),
 * loaded chunks only, a per-invasion cap ({@link ChitauriRules#MAX_DESTROYED_BLOCKS}) and a cap on
 * flying debris. Blocks change through Level.setBlock, so the city journal records them and
 * {@code /city reset} restores them; nothing is restored when the invasion ends.
 */
public final class ChitauriDestruction {
   private static int destroyed;
   private static final ArrayDeque<FallingBlockEntity> FLYING = new ArrayDeque<>();

   private ChitauriDestruction() {
   }

   public static void reset() {
      destroyed = 0;
      FLYING.clear();
   }

   public static int destroyed() {
      return destroyed;
   }

   public static int left() {
      return Math.max(0, ChitauriRules.MAX_DESTROYED_BLOCKS - destroyed);
   }

   public static boolean exhausted() {
      return destroyed >= ChitauriRules.MAX_DESTROYED_BLOCKS;
   }

   /** Pure: may a block of this hardness go, given the smash strength (negative = unbreakable). */
   public static boolean breakable(float hardness, float maxHardness) {
      return hardness >= 0.0F && hardness <= maxHardness;
   }

   private static boolean glassy(BlockState state) {
      SoundType sound = state.getSoundType();
      return sound == SoundType.GLASS;
   }

   /** A rifle bolt on a window: the pane shatters (vanilla break particles and sound). */
   public static boolean shatterGlass(ServerLevel level, BlockPos pos) {
      if (exhausted() || !level.hasChunkAt(pos)) {
         return false;
      }
      BlockState state = level.getBlockState(pos);
      if (!glassy(state) || !HeroDestruction.canDestroy(level, pos)) {
         return false;
      }
      if (level.destroyBlock(pos, false)) {
         destroyed++;
         return true;
      }
      return false;
   }

   /** Cannon impact: glass, leaves and other weak blocks in a small sphere. */
   public static int crack(ServerLevel level, BlockPos center, double radius, Vec3 push) {
      dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.hint(level, dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause.CHITAURI, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, (float)radius);
      int done = 0;
      int r = Mth.ceil(radius);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               if (dx * dx + dy * dy + dz * dz > radius * radius || exhausted()) {
                  continue;
               }
               pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               if (!level.hasChunkAt(pos)) {
                  continue;
               }
               BlockState state = level.getBlockState(pos);
               if (state.isAir() || !breakable(state.getDestroySpeed(level, pos), 0.8F) || !state.getFluidState().isEmpty()) {
                  continue;
               }
               if (glassy(state)) {
                  if (shatterGlass(level, pos.immutable())) {
                     done++;
                  }
               } else if (HeroDestruction.clearBlock(level, pos.immutable())) {
                  destroyed++;
                  done++;
               }
            }
         }
      }
      return done;
   }

   /**
    * Sphere smash (Leviathan body, chariot crash, crater): removes blocks up to {@code maxHardness},
    * throws up to {@code debrisMax} of them as falling debris along {@code push}. Returns blocks removed.
    */
   public static int smash(ServerLevel level, Vec3 center, double radius, float maxHardness, int maxBlocks, Vec3 push, int debrisMax) {
      if (exhausted() || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
         return 0;
      }
      dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath.hint(level, dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause.CHITAURI, center.x, center.y, center.z, (float)radius);
      pruneFlying();
      RandomSource random = level.getRandom();
      int r = Mth.ceil(radius);
      int cx = Mth.floor(center.x), cy = Mth.floor(center.y), cz = Mth.floor(center.z);
      int done = 0;
      int debris = 0;
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      for (int dy = r; dy >= -r && done < maxBlocks; dy--) {
         int y = cy + dy;
         if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            continue;
         }
         for (int dx = -r; dx <= r && done < maxBlocks; dx++) {
            for (int dz = -r; dz <= r && done < maxBlocks; dz++) {
               double fx = cx + dx + 0.5 - center.x, fy = y + 0.5 - center.y, fz = cz + dz + 0.5 - center.z;
               if (fx * fx + fy * fy + fz * fz > radius * radius) {
                  continue;
               }
               if (exhausted()) {
                  return done;
               }
               pos.set(cx + dx, y, cz + dz);
               if (!level.hasChunkAt(pos)) {
                  continue;
               }
               BlockState state = level.getBlockState(pos);
               if (state.isAir() || !state.getFluidState().isEmpty() || !breakable(state.getDestroySpeed(level, pos), maxHardness)) {
                  continue;
               }
               BlockPos at = pos.immutable();
               if (debris < debrisMax && FLYING.size() < ChitauriRules.MAX_FLYING_DEBRIS && random.nextFloat() < 0.35F
                  && HeroDestruction.canDestroy(level, at) && HeroDebris.canLaunch(level, at, state)) {
                  Vec3 out = new Vec3(fx, Math.abs(fy) + 0.6, fz).normalize().scale(0.35 + random.nextDouble() * 0.35).add(push.scale(0.6));
                  FallingBlockEntity block = HeroDebris.launchBlock(level, at, state, out, 1.0F, 6, HeroDestruction.CLEAR_FLAGS);
                  FLYING.add(block);
                  debris++;
                  destroyed++;
                  done++;
               } else if (HeroDestruction.clearBlock(level, at)) {
                  destroyed++;
                  done++;
               }
            }
         }
      }
      return done;
   }

   /** A big smash spread over ticks through CarveJobs (shares the hero carve budget). */
   public static void smashLater(ServerLevel level, Vec3 center, double radius, float maxHardness, Vec3 push) {
      List<Vec3> shells = new ArrayList<>();
      for (double s = 1.5; s <= radius + 0.01; s += 1.5) {
         shells.add(new Vec3(s, 0, 0));
      }
      CarveJobs.submit(level, new CarveJobs.Job() {
         int shell;

         @Override
         public boolean step(@Nullable ServerLevel lvl, int budget) {
            if (lvl == null || this.shell >= shells.size()) {
               return true;
            }
            double rr = shells.get(this.shell++).x;
            smash(lvl, center, rr, maxHardness, Math.min(budget, 900), push, this.shell == shells.size() ? 10 : 2);
            return this.shell >= shells.size();
         }

         @Override
         public int priority() {
            return 1;
         }
      });
   }

   /** Area damage around an explosion (never Chitauri), with knockback where hero policy allows it. */
   public static void blastEntities(ServerLevel level, Vec3 at, double radius, float damage, @Nullable Entity owner, @Nullable Entity direct) {
      AABB box = new AABB(at, at).inflate(radius);
      for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ChitauriMob.is(e))) {
         double d = living.position().add(0, living.getBbHeight() * 0.5, 0).distanceTo(at);
         if (d > radius) {
            continue;
         }
         float f = (float)(1.0 - d / radius);
         living.hurt(level.damageSources().explosion(direct, owner), damage * (0.4F + 0.6F * f));
         if (HeroRegistry.allowsImpulse(living)) {
            Vec3 away = living.position().subtract(at).normalize();
            living.knockback(0.6 * f, -away.x, -away.z);
         }
      }
      danger(level, at, Math.max(12.0F, (float)radius * 4.0F), 120);
   }

   /** City citizens flee from this point (no-op without a city). */
   public static void danger(ServerLevel level, Vec3 at, float radius, int lifetime) {
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.danger(at.x, at.y, at.z, radius, lifetime);
      }
   }

   /** Something to look at, not to run from (the portal): citizens up to the max notice range stop and watch. */
   public static void spectacle(ServerLevel level, Vec3 at, int lifetime) {
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.dangerMap().add(at.x, at.y, at.z, 0.0F, DangerMap.MAX_NOTICE, city.now(), lifetime);
      }
   }

   private static void pruneFlying() {
      Iterator<FallingBlockEntity> it = FLYING.iterator();
      while (it.hasNext()) {
         FallingBlockEntity e = it.next();
         if (e.isRemoved() || e.tickCount > 400) {
            it.remove();
         }
      }
   }
}
