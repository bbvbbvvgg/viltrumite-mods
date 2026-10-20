package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.ReinhardClips;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CutRain;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Spec §7 Rain of Cuts (stage 4 Task 8). */
class CutRainTest {
   @Test
   void twelveCutsInCone() {
      assertEquals(12, ReinhardRules.CUT_COUNT);
      assertEquals(30, ReinhardRules.CUT_CONE_DEG);
      assertEquals(40, ReinhardRules.CUT_RANGE);
      List<Vec3> dirs = CutRain.directions(90.0F, 0.0F, 1234L);
      assertEquals(12, dirs.size());
      // Vec3.directionFromRotation uses the Mth sine table: angles are exact to ~0.002°.
      Vec3 look = Vec3.directionFromRotation(0.0F, 90.0F);
      double minYaw = 999;
      double maxYaw = -999;
      for (Vec3 d : dirs) {
         assertEquals(1.0, d.length(), 1.0E-6);
         double yawOff = Math.toDegrees(Math.atan2(d.x * look.z - d.z * look.x, d.x * look.x + d.z * look.z));
         assertTrue(Math.abs(yawOff) <= 15.0 + 0.01, "yaw offset " + yawOff);
         double pitch = Math.toDegrees(Math.asin(-d.y));
         assertTrue(Math.abs(pitch) <= CutRain.PITCH_JITTER_DEG + 0.01, "pitch " + pitch);
         minYaw = Math.min(minYaw, yawOff);
         maxYaw = Math.max(maxYaw, yawOff);
      }
      assertEquals(30.0, maxYaw - minYaw, 0.01, "spread over the whole cone");
      assertEquals(dirs, CutRain.directions(90.0F, 0.0F, 1234L), "deterministic from the seed");
   }

   @Test
   void segmentHitsBox() {
      Vec3 from = new Vec3(0, 1.5, 0);
      Vec3 to = from.add(0, 0, 40);
      assertTrue(CutRain.segmentHits(new AABB(-0.3, 0, 20, 0.3, 1.8, 20.6), from, to));
      assertFalse(CutRain.segmentHits(new AABB(-0.3, 0, 41, 0.3, 1.8, 41.6), from, to), "beyond 40");
      assertFalse(CutRain.segmentHits(new AABB(2, 0, 10, 2.6, 1.8, 10.6), from, to), "beside the cut");
      assertTrue(CutRain.segmentHits(new AABB(-1, 0, -1, 1, 2, 1), from, to), "the segment starts inside");
      Set<BlockPos> groove = CutRain.groove(from, to);
      assertTrue(groove.contains(BlockPos.containing(0, 1.5, 20)));
      assertTrue(groove.contains(BlockPos.containing(0, 0.5, 20)) && groove.contains(BlockPos.containing(0, 2.5, 20)), "3 tall");
      assertFalse(groove.contains(BlockPos.containing(1, 1.5, 20)), "1 wide");
      assertEquals(41 * 3, groove.size(), "40 blocks long, 1 x 3 section");
   }

   @Test
   void cooldown300() {
      ReinhardState state = new ReinhardState();
      CutRain.start(state);
      assertEquals(8, ReinhardRules.CUT_COOLDOWN);
      assertEquals(8, state.cooldowns[ReinhardAbility.CUT_RAIN.ordinal()]);
      assertTrue(state.action.is(ReinhardAbility.CUT_RAIN));
      assertEquals(ReinhardRules.CUT_TICKS, state.action.length());
      int cuts = 0;
      for (int i = 0; i < ReinhardRules.CUT_TICKS; i++) {
         int cut = CutRain.cutAt(state.action);
         if (cut >= 0) {
            assertEquals(cuts, cut, "staggered 1 t, in order");
            cuts++;
         }
         state.action.tick();
      }
      assertEquals(12, cuts);
      assertEquals(List.of(ReinhardClips.CUT_RAIN), ReinhardClips.names(ReinhardAbility.CUT_RAIN));
   }

   @Test
   void onlyWhenDrawn() {
      for (Reid.Phase phase : Reid.Phase.values()) {
         assertEquals(phase == Reid.Phase.DRAWN, Reid.allows(ReinhardAbility.CUT_RAIN, phase), phase.name());
      }
      assertInstanceOf(CutRain.class, ReinhardActions.handler(ReinhardAbility.CUT_RAIN));
      assertTrue(DestructionLevel.of(ReinhardMode.HOLY_SWORD).mayBreak(), "Holy Sword carves");
   }
}
