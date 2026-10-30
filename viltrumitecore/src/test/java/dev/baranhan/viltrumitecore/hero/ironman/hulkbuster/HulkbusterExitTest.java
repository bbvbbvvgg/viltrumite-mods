package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Fix spec 2026-10-28 item 2 (exit, 2.5 s, no vanishing). The old exit translated Tony by +Z before vanilla's body
 * rotation, so he always stepped out towards world +Z; the shell and Tony then snapped back when the phase ended.
 */
class HulkbusterExitTest {
   private static final Vec3 ORIGIN = new Vec3(10.5, 64.0, -3.5);

   @Test
   void phasesMatchTheSpec() {
      assertEquals(50, HulkbusterLayer.EXIT_TICKS, "2.5 s");
      assertEquals(8, HulkbusterExit.VENT_END, "0-0.4 s vent");
      assertEquals(24, HulkbusterExit.STEP_START, "0.4-1.2 s chest opens");
      assertEquals(36, HulkbusterExit.STEP_END, "1.2-1.8 s step out");
      assertTrue(HulkbusterExit.TONY_SHOWN > HulkbusterExit.VENT_END && HulkbusterExit.TONY_SHOWN < HulkbusterExit.STEP_START);
   }

   @Test
   void stepOutFollowsTheFrozenFacingNotWorldZ() {
      for (float yaw : new float[]{0.0F, 90.0F, 180.0F, -90.0F, 45.0F, 135.0F}) {
         float q = HulkbusterExit.quantize(yaw);
         Vec3 spot = HulkbusterExit.spot(ORIGIN, q, true);
         Vec3 look = Vec3.directionFromRotation(0.0F, q);
         Vec3 d = spot.subtract(ORIGIN);
         assertEquals(HulkbusterExit.STEP_OUT, d.length(), 1.0E-6, "distance at yaw " + yaw);
         assertTrue(d.normalize().dot(new Vec3(look.x, 0.0, look.z).normalize()) > 0.999, "forward of the shell at yaw " + yaw);
      }

      // yaw 90 faces world -X: the old code would have sent Tony to +Z.
      Vec3 west = HulkbusterExit.spot(ORIGIN, HulkbusterExit.quantize(90.0F), true).subtract(ORIGIN);
      assertTrue(west.x < -1.5 && Math.abs(west.z) < 0.1, west.toString());
      assertEquals(ORIGIN, HulkbusterExit.spot(ORIGIN, 90.0F, false), "blocked: Tony steps down in place");
   }

   @Test
   void quantizedYawIsWhatTheWireCarries() {
      for (float yaw = -180.0F; yaw < 180.0F; yaw += 7.3F) {
         float q = HulkbusterExit.quantize(yaw);
         int word = HulkbusterWire.exitScene(30, true, q, true);
         assertEquals(q, HulkbusterWire.sceneYaw(word), 1.0E-4F);
         assertEquals(30, HulkbusterWire.sceneTick(word));
         assertTrue(HulkbusterWire.exitVeronica(word));
         assertTrue(HulkbusterWire.exitStepOut(word));
         int other = HulkbusterWire.exitScene(5, false, q, false);
         assertFalse(HulkbusterWire.exitVeronica(other));
         assertFalse(HulkbusterWire.exitStepOut(other));
      }
   }

   @Test
   void tonyMovesContinuouslyAndEndsOnTheSpot() {
      Vec3 spot = HulkbusterExit.spot(ORIGIN, 33.0F, true);
      Vec3 last = HulkbusterExit.tony(ORIGIN, spot, 0.0);
      for (double t = 0.0; t <= HulkbusterLayer.EXIT_TICKS; t += 0.25) {
         Vec3 p = HulkbusterExit.tony(ORIGIN, spot, t);
         assertTrue(p.distanceTo(last) < 0.2, "no jump at t " + t);
         last = p;
      }

      assertEquals(0.0, HulkbusterExit.tony(ORIGIN, spot, HulkbusterExit.STEP_END).distanceTo(spot), 1.0E-9);
      assertEquals(0.0, HulkbusterExit.path(ORIGIN, spot, HulkbusterLayer.EXIT_TICKS).distanceTo(spot), 1.0E-9,
         "the exit ends where the player already stands: nothing snaps when the phase ends");
      // The real player keeps the origin while the big body stands; from the step it is where Tony is drawn.
      assertEquals(ORIGIN, HulkbusterExit.path(ORIGIN, spot, HulkbusterExit.STEP_START - 1));
      for (int t = HulkbusterExit.STEP_START; t <= HulkbusterLayer.EXIT_TICKS; t++) {
         assertEquals(0.0, HulkbusterExit.path(ORIGIN, spot, t).distanceTo(HulkbusterExit.tony(ORIGIN, spot, t)), 1.0E-9);
      }
   }

   @Test
   void hitboxShrinksWhenTonyStepsOutOnBothSides() {
      HulkbusterLayer layer = new HulkbusterLayer();
      layer.start(false);
      while (layer.phase() != HulkbusterLayer.Phase.ACTIVE) {
         if (layer.tick() == HulkbusterLayer.Event.READY) {
            layer.activate();
         }
      }

      assertTrue(layer.exit());
      while (layer.phase() == HulkbusterLayer.Phase.EXITING) {
         int word = HulkbusterWire.exitScene(layer.sceneTick(), true, 0.0F, true);
         assertEquals(layer.ticks() < HulkbusterExit.STEP_START, layer.big(), "t " + layer.ticks());
         assertEquals(layer.big(), HulkbusterLayer.bigOnWire(layer.phase().ordinal(), word), "wire t " + layer.ticks());
         layer.tick();
      }

      assertFalse(layer.big());
   }

   @Test
   void reactorDimsInSteps() {
      float last = 2.0F;
      int steps = 0;
      for (int t = 0; t <= HulkbusterExit.VENT_END + 4; t++) {
         float r = HulkbusterExit.reactor(t);
         assertTrue(r <= last);
         if (r < last) {
            steps++;
         }

         last = r;
      }

      assertEquals(HulkbusterExit.REACTOR_STEPS.length, steps, "one change per step");
      assertEquals(1.0F, HulkbusterExit.reactor(0));
      assertTrue(HulkbusterExit.reactor(HulkbusterExit.VENT_END) <= 0.1F, "dark by 0.4 s");
   }

   @Test
   void emptyShellTimeline() {
      assertEquals(200, HulkbusterExit.CRUMBLE_AT, "no Veronica: stands 10 s, then crumbles");
      int last = 0;
      for (int launch : HulkbusterExit.MODULE_LAUNCH) {
         assertTrue(launch > last, "arms, then legs, then torso, one by one");
         last = launch;
      }

      assertEquals(0.0, HulkbusterExit.moduleRise(0.0));
      assertTrue(HulkbusterExit.moduleRise(HulkbusterExit.MODULE_FLIGHT) > 60.0, "out of sight when it is dropped");
      assertTrue(HulkbusterExit.shellLife(true) < HulkbusterExit.shellLife(false));
   }
}
