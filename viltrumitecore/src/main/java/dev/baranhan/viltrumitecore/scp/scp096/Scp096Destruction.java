package dev.baranhan.viltrumitecore.scp.scp096;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.power.api.CityInfrastructure;
import dev.baranhan.viltrumitecore.world.collapse.CollapseCause;
import dev.baranhan.viltrumitecore.world.collapse.StructuralDamage;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathCause;
import dev.baranhan.viltrumitecore.worldevent.aftermath.AftermathEvent;
import dev.baranhan.viltrumitecore.worldevent.aftermath.CityAftermath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * SCP-096 tearing through the city (only on its way to a target). Every removal runs inside the structural
 * damage scope {@link #CAUSE}, so the existing collapse, aftermath and infrastructure links see it like any other
 * disaster; heavy breaches add an explicit impact, a ruin zone and a light infrastructure hit (severity below the
 * city alarm). mobGriefing and Forge's block-destroy events are honoured; unbreakable blocks stay.
 */
final class Scp096Destruction {
   static final CollapseCause CAUSE = CollapseCause.custom("scp096", 0.9F);
   private static final float CHEAP_HARDNESS = 0.8F;

   private Scp096Destruction() {
   }

   static boolean griefing(ServerLevel level, Scp096Entity mob) {
      return ForgeEventFactory.getMobGriefingEvent(level, mob);
   }

   /** Blocks it goes through without slowing down: doors, glass, panes, bars, leaves, light stuff. */
   static boolean cheap(BlockState state, float hardness) {
      return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
         || state.getBlock() instanceof AbstractGlassBlock || state.getBlock() instanceof IronBarsBlock || state.is(BlockTags.LEAVES)
         || hardness <= CHEAP_HARDNESS;
   }

   private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state, Scp096Entity mob, boolean cheapOnly) {
      if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
         return false;
      }
      float h = state.getDestroySpeed(level, pos);
      if (h < 0.0F || h > Scp096Rules.SMASH_MAX_HARDNESS) {
         return false;
      }
      if (cheapOnly && !cheap(state, h)) {
         return false;
      }
      return state.canEntityDestroy(level, pos, mob) && ForgeEventFactory.onEntityDestroyBlock(mob, pos, state);
   }

   /**
    * Breaks up to {@code max} blocks in the body box pushed ahead along {@code dir} (floor kept unless digging
    * down, ceiling only when climbing through). Returns the removed positions.
    */
   static List<BlockPos> carve(ServerLevel level, Scp096Entity mob, Vec3 dir, int max, boolean cheapOnly) {
      List<BlockPos> out = new ArrayList<>();
      if (max <= 0 || !griefing(level, mob)) {
         return out;
      }
      AABB bb = mob.getBoundingBox();
      AABB ahead = bb.move(dir.x * 0.9, dir.y * 0.9, dir.z * 0.9).inflate(0.2, 0.0, 0.2).minmax(bb.inflate(0.05, 0.0, 0.05));
      double minY = dir.y < -0.5 ? ahead.minY - 0.6 : bb.minY + 0.1;
      double maxY = dir.y > 0.5 ? ahead.maxY + 0.6 : ahead.maxY + 0.2;
      int x0 = (int)Math.floor(ahead.minX);
      int x1 = (int)Math.floor(ahead.maxX);
      int y0 = (int)Math.floor(minY);
      int y1 = (int)Math.floor(maxY);
      int z0 = (int)Math.floor(ahead.minZ);
      int z1 = (int)Math.floor(ahead.maxZ);
      List<BlockPos> candidates = new ArrayList<>();
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
               BlockPos p = new BlockPos(x, y, z);
               if (!level.isLoaded(p)) {
                  continue;
               }
               BlockState s = level.getBlockState(p);
               if (breakable(level, p, s, mob, cheapOnly)) {
                  candidates.add(p);
               }
            }
         }
      }
      if (candidates.isEmpty()) {
         return out;
      }
      Vec3 c = bb.getCenter();
      candidates.sort((a, b) -> Double.compare(c.distanceToSqr(Vec3.atCenterOf(a)), c.distanceToSqr(Vec3.atCenterOf(b))));
      StructuralDamage.run(CAUSE, () -> {
         for (BlockPos p : candidates) {
            if (out.size() >= max) {
               break;
            }
            if (level.destroyBlock(p, false, mob)) {
               out.add(p);
            }
         }
      });
      return out;
   }

   /** Consequences of a breach: the smash clip, a thud, the collapse impact, citizens knocked, ruins, power. */
   static void breach(ServerLevel level, Scp096Entity mob, List<BlockPos> broken, Scp096Chase memory) {
      if (broken.isEmpty()) {
         return;
      }
      double x = 0.0;
      double y = 0.0;
      double z = 0.0;
      for (BlockPos p : broken) {
         x += p.getX() + 0.5;
         y += p.getY() + 0.5;
         z += p.getZ() + 0.5;
      }
      int n = broken.size();
      Vec3 c = new Vec3(x / n, y / n, z / n);
      long now = level.getGameTime();
      if (n >= 3 && now - memory.lastSmashMark >= 8) {
         memory.lastSmashMark = now;
         mob.markSmash();
         level.playSound(null, c.x, c.y, c.z, Scp096Sounds.SMASH.get(), SoundSource.HOSTILE, 1.4F, 0.55F + level.random.nextFloat() * 0.2F);
      }
      if (n < 4) {
         return;
      }
      StructuralDamage.impact(level, c, CAUSE);
      CityManager city = CityManager.of(level);
      if (city != null) {
         city.blast(c.x, c.y, c.z, 3.5F);
      }
      if (now - memory.lastRuin >= 60) {
         memory.lastRuin = now;
         float radius = (float)Math.min(8.0, 2.5 + Math.sqrt(n));
         CityAftermath.registerDamage(level, AftermathEvent.of(level, "scp096:" + mob.getUUID() + ":" + now / 100L, BlockPos.containing(c), radius, 0.28F,
            AftermathCause.DESTRUCTION, broken));
      }
      if (now - memory.lastGrid >= 100) {
         memory.lastGrid = now;
         CityInfrastructure.damage(level, c, 3.0F, 0.2F, "scp096", "scp096:" + mob.getUUID());
      }
   }
}
