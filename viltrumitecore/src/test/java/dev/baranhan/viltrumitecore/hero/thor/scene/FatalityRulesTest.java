package dev.baranhan.viltrumitecore.hero.thor.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Fatality trigger (decisions 41-48, 150), anti-repeat (86), entries (89-92) and the continuous intro (93-96). */
class FatalityRulesTest {
   @Test
   void onlyALethalHammerHitOnAFreePlayerStartsAScene() {
      assertSame(FatalityRules.Verdict.START, FatalityRules.decide(ThorEventType.LETHAL_HIT, true, true, true, false, false));
      assertSame(FatalityRules.Verdict.NOT_LETHAL, FatalityRules.decide(ThorEventType.MELEE_HIT, true, true, true, false, false));
      assertSame(FatalityRules.Verdict.HAMMER_NOT_IN_HAND, FatalityRules.decide(ThorEventType.LETHAL_HIT, false, true, true, false, false));
      assertSame(FatalityRules.Verdict.NOT_A_PLAYER, FatalityRules.decide(ThorEventType.LETHAL_HIT, true, false, true, false, false));
      assertSame(FatalityRules.Verdict.TARGET_GONE, FatalityRules.decide(ThorEventType.LETHAL_HIT, true, true, false, false, false));
      assertSame(FatalityRules.Verdict.ALREADY_IN_SCENE, FatalityRules.decide(ThorEventType.LETHAL_HIT, true, true, true, true, false));
      assertSame(FatalityRules.Verdict.THOR_BUSY, FatalityRules.decide(ThorEventType.LETHAL_HIT, true, true, true, false, true));
   }

   @Test
   void theSameSceneNeverPlaysTwiceInARow() {
      Set<FatalityScene> seen = EnumSet.noneOf(FatalityScene.class);
      for (FatalityScene previous : FatalityScene.values()) {
         for (int roll = -50; roll < 50; roll++) {
            FatalityScene next = FatalityRules.pick(previous, roll);
            assertNotSame(previous, next);
            seen.add(next);
         }
      }
      assertEquals(EnumSet.allOf(FatalityScene.class), seen);
      for (int roll = 0; roll < 3; roll++) {
         seen.remove(FatalityRules.pick(null, roll));
      }
      assertTrue(seen.isEmpty(), "with no previous scene every scene can come up");
   }

   @Test
   void scenesAreNumberedOneToThree() {
      for (FatalityScene scene : FatalityScene.values()) {
         assertSame(scene, FatalityScene.byNumber(scene.number()));
         assertTrue(scene.beatTicks().length > 0);
         for (int beat : scene.beatTicks()) {
            assertTrue(beat >= 0 && beat < scene.main(), scene + " beat inside the main part");
         }
      }
      assertSame(null, FatalityScene.byNumber(0));
      assertSame(null, FatalityScene.byNumber(4));
   }

   @Test
   void entryIsClassifiedFromMotion() {
      assertSame(SceneEntry.FALLING, SceneEntry.classify(false, 4.0, -0.6, 0.0, 0, 0));
      assertSame(SceneEntry.AIRBORNE, SceneEntry.classify(false, 2.0, 0.1, 0.0, 0, 0));
      assertSame(SceneEntry.RUNNING, SceneEntry.classify(true, 0.0, 0.0, 0.3, 0, 0));
      assertSame(SceneEntry.BEHIND, SceneEntry.classify(true, 0.0, 0.0, 0.0, 0, 180));
      assertSame(SceneEntry.FACING, SceneEntry.classify(true, 0.0, 0.0, 0.0, 0, 10));
      assertSame(SceneEntry.FACING, SceneEntry.classify(true, 0.0, 0.0, 0.0, 0, Float.NaN));
      assertTrue(SceneEntry.FALLING.keepsMomentum());
      assertTrue(!SceneEntry.FACING.keepsMomentum());
   }

   private static FatalityPlan plan(FatalityScene scene, SceneEntry entry, Vec3 thor, float thorYaw, Vec3 victim, float victimYaw, Vec3 velocity,
      Vec3 origin) {
      float frame = SceneFrame.yawTo(origin, victim);
      return FatalityPlan.of(scene, entry, thor, thorYaw, victim, victimYaw, velocity, origin, Float.isNaN(frame) ? thorYaw : frame, 8, 30, 0.45, 20.0F);
   }

   @Test
   void introStartsWhereTheBodiesAreAndJoinsTheSceneWithoutJumps() {
      for (FatalityScene scene : FatalityScene.values()) {
         for (SceneEntry entry : SceneEntry.values()) {
            Vec3 thor = new Vec3(0, 64, 0);
            Vec3 victim = new Vec3(2.5, 64.6, 1.0);
            FatalityPlan plan = plan(scene, entry, thor, 30.0F, victim, -120.0F, new Vec3(0.3, -0.4, 0.1), new Vec3(1.0, 64, -0.5));
            assertTrue(plan.thorAt(0).distanceTo(thor) < 1.0E-6, scene + "/" + entry + " Thor starts in place");
            assertTrue(plan.victimAt(0).distanceTo(victim) < 1.0E-6, scene + "/" + entry + " victim starts in place");
            assertTrue(plan.intro() >= 8 && plan.intro() <= 30);
            assertTrue(plan.maxIntroStep() <= 0.45 + 1.0E-6 || plan.intro() == 30, scene + "/" + entry + " step " + plan.maxIntroStep());
            for (double t = 0; t < plan.length() - 1; t += 1.0) {
               assertTrue(plan.thorAt(t).distanceTo(plan.thorAt(t + 1)) < 2.0, "Thor never teleports (" + scene + " t=" + t + ")");
               assertTrue(Math.abs(SceneFrame.wrap(plan.thorYawAt(t + 1) - plan.thorYawAt(t))) <= 45.0F, "bounded turn");
            }
            double end = plan.intro();
            assertTrue(plan.victimAt(end - 0.001).distanceTo(plan.victimAt(end)) < 0.05, "intro meets the main part");
            assertTrue(plan.thorAt(end - 0.001).distanceTo(plan.thorAt(end)) < 0.05);
         }
      }
   }

   @Test
   void farStartsGetLongerIntros() {
      FatalityPlan near = plan(FatalityScene.HEAD_CRUSH, SceneEntry.FACING, new Vec3(0, 64, 0), 0, new Vec3(0, 64, 1.1), 180, Vec3.ZERO, new Vec3(0, 64, 0));
      FatalityPlan far = plan(FatalityScene.HEAD_CRUSH, SceneEntry.FACING, new Vec3(0, 64, 0), 0, new Vec3(0, 64, 1.1), 180, Vec3.ZERO, new Vec3(5, 64, 0));
      assertTrue(far.intro() > near.intro());
      assertEquals(far.intro() + FatalityScene.HEAD_CRUSH.main() + FatalityScene.HEAD_CRUSH.finish(), far.length());
   }

   @Test
   void syncRoundTripsThePlan() {
      FatalityPlan plan = plan(FatalityScene.AERIAL, SceneEntry.RUNNING, new Vec3(1, 70, 2), 45, new Vec3(3, 70, 2), 10, new Vec3(0.2, 0, 0),
         new Vec3(1, 70, 2));
      SceneSync sync = SceneSync.fatality(7, 9, plan, 12, true);
      net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
      sync.write(buf);
      SceneSync read = SceneSync.read(buf);
      assertEquals(sync, read);
      FatalityPlan back = read.plan();
      assertEquals(plan.intro(), back.intro());
      assertTrue(back.victimAt(20).distanceTo(plan.victimAt(20)) < 1.0E-3);
   }
}
