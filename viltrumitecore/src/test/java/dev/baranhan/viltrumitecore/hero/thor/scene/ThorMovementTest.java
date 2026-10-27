package dev.baranhan.viltrumitecore.hero.thor.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumiteflight.util.FlightState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Flight / hover / dash rules (decisions 5, 13, 20-22, 34, 48, 110, 135), the V teleport (14, 29, 41-47, 71, 72, 120) and the scene wire. */
class ThorMovementTest {
   @Test
   void fullFlightOnlyWithMjolnirInHand() {
      assertNull(ThorMovementRules.profile(true), "the original flight");
      assertSame(ThorMovementRules.HOVER_ONLY, ThorMovementRules.profile(false));
      assertEquals(0.0F, ThorMovementRules.HOVER_ONLY.throttleUpPerTick(), "hover-only can never build throttle");
      assertTrue(ThorMovementRules.allowsFlight(true, false));
      assertFalse(ThorMovementRules.allowsFlight(true, true), "no flight while a scene holds Thor");
      assertTrue(ThorMovementRules.fullFlight(FlightState.CRUISE));
      assertTrue(ThorMovementRules.fullFlight(FlightState.SONIC));
      assertFalse(ThorMovementRules.fullFlight(FlightState.HOVER));
      assertFalse(ThorMovementRules.fullFlight(null));
   }

   @Test
   void theServerUndoesARefusedPredictedDash() throws java.io.IOException {
      assertTrue(ThorDash.accepted(0, true, 100L, null));
      assertTrue(ThorDash.accepted(3, true, 100L, 100L), "the cooldown is over at its tick");
      assertFalse(ThorDash.accepted(1, false, 100L, null), "not allowed now (full flight, scene, held, anchored)");
      assertFalse(ThorDash.accepted(2, true, 99L, 100L), "on cooldown");
      assertFalse(ThorDash.accepted(-1, true, 100L, null));
      assertFalse(ThorDash.accepted(4, true, 100L, null));
      String dash = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/scene/ThorDash.java"));
      assertTrue(dash.contains("player.connection.teleport("), "a refused dash snaps the player back to the server position");
      assertTrue(dash.contains("new ThorDashS2CPacket()"), "and the client stops the burst");
      String messages = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/dev/baranhan/viltrumitecore/network/CoreMessages.java"));
      assertTrue(messages.contains("ThorDashS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT"));
   }

   @Test
   void noAttackInFullFlightOrScene() {
      assertTrue(ThorMovementRules.attacksAllowed(false, false));
      assertFalse(ThorMovementRules.attacksAllowed(true, false), "decisions 22 / 34: full flight is movement only");
      assertFalse(ThorMovementRules.attacksAllowed(false, true), "a scene owns the mouse");
   }

   @Test
   void poseFadeIsSmoothAndTakesTheShortWayRound() {
      assertEquals(0.0F, PoseBlend.weight(0.0F, 6.0F), 1.0E-6);
      assertEquals(0.5F, PoseBlend.weight(3.0F, 6.0F), 1.0E-6);
      assertEquals(1.0F, PoseBlend.weight(6.0F, 6.0F), 1.0E-6);
      assertEquals(1.0F, PoseBlend.weight(1.0F, 0.0F), 1.0E-6, "no fade length = the new pose at once");
      float[] from = new float[PoseBlend.STRIDE];
      float[] to = new float[PoseBlend.STRIDE];
      from[0] = (float)Math.toRadians(170);
      to[0] = (float)Math.toRadians(-170);
      from[3] = 0.0F;
      to[3] = 2.0F;
      float[] half = PoseBlend.blend(from, to, 0.5F);
      assertEquals(180.0, Math.abs(Math.toDegrees(half[0])), 1.0E-3, "through 180, not through 0");
      assertEquals(1.0F, half[3], 1.0E-6, "pivots blend linearly");
      float[] done = PoseBlend.blend(from, to, 0.0F);
      assertEquals(from[0], done[0], 1.0E-6);
   }

   @Test
   void dashesOnTheGroundAndInHoverOnly() {
      assertTrue(ThorMovementRules.dashAllowed(false, false, FlightState.NONE, true));
      assertTrue(ThorMovementRules.dashAllowed(false, false, null, true));
      assertTrue(ThorMovementRules.dashAllowed(false, false, FlightState.HOVER, false));
      assertFalse(ThorMovementRules.dashAllowed(false, false, FlightState.NONE, false), "not mid-jump");
      assertFalse(ThorMovementRules.dashAllowed(false, false, FlightState.CRUISE, false));
      assertFalse(ThorMovementRules.dashAllowed(false, false, FlightState.SONIC, false));
      assertFalse(ThorMovementRules.dashAllowed(true, false, FlightState.NONE, true), "scene");
      assertFalse(ThorMovementRules.dashAllowed(false, true, FlightState.NONE, true), "held / anchored");
      assertEquals(0.0F, ThorMovementRules.dashYawOffset(0));
      assertEquals(-90.0F, ThorMovementRules.dashYawOffset(1));
      assertEquals(180.0F, ThorMovementRules.dashYawOffset(2));
      assertEquals(90.0F, ThorMovementRules.dashYawOffset(3));
   }

   @Test
   void doubleTapFiresOnTheSecondPressWithinTheWindow() {
      DoubleTap taps = new DoubleTap();
      boolean[] w = {true, false, false, false};
      boolean[] none = {false, false, false, false};
      assertEquals(-1, taps.tick(w, 0, 7));
      assertEquals(-1, taps.tick(none, 1, 7));
      assertEquals(0, taps.tick(w, 3, 7));
      assertEquals(-1, taps.tick(none, 4, 7));
      assertEquals(-1, taps.tick(w, 5, 7), "a fired tap is consumed");
      assertEquals(-1, taps.tick(none, 6, 7));
      assertEquals(-1, taps.tick(w, 30, 7), "too slow");
   }

   @Test
   void holdingAKeyIsNotATapAndAnotherKeyBreaksTheChain() {
      DoubleTap taps = new DoubleTap();
      boolean[] d = {false, false, false, true};
      boolean[] a = {false, true, false, false};
      boolean[] none = {false, false, false, false};
      for (int t = 0; t < 5; t++) {
         assertEquals(-1, taps.tick(d, t, 7));
      }
      taps.tick(none, 5, 7);
      taps.tick(a, 6, 7);
      taps.tick(none, 7, 7);
      assertEquals(-1, taps.tick(d, 8, 7), "A in between resets D");
   }

   @Test
   void teleportGateAndTargetRules() {
      assertSame(TeleportRules.Refusal.NONE, TeleportRules.gate(true, false, false));
      assertSame(TeleportRules.Refusal.NO_HAMMER, TeleportRules.gate(false, false, false));
      assertSame(TeleportRules.Refusal.FULL_FLIGHT, TeleportRules.gate(true, true, false));
      assertSame(TeleportRules.Refusal.BUSY, TeleportRules.gate(true, false, true));
      assertSame(TeleportRules.Target.OK, TeleportRules.judge(true, true, true, true));
      assertSame(TeleportRules.Target.NOT_SOLID, TeleportRules.judge(false, true, true, true));
      assertSame(TeleportRules.Target.UNSAFE, TeleportRules.judge(true, false, true, true));
      assertSame(TeleportRules.Target.OUT_OF_VIEW, TeleportRules.judge(true, true, false, true));
      assertSame(TeleportRules.Target.OUT_OF_VIEW, TeleportRules.judge(true, true, true, false));
      assertTrue(TeleportRules.expired(200, 200));
      assertFalse(TeleportRules.expired(199, 200));
   }

   @Test
   void teleportCameraRisesAboveThorUnderTheBuildLimit() {
      Vec3 eye = new Vec3(10, 70, -4);
      Vec3 camera = TeleportRules.camera(eye, 80, 320);
      assertEquals(150.0, camera.y, 1.0E-9);
      assertEquals(319.0, TeleportRules.camera(new Vec3(0, 300, 0), 80, 320).y, 1.0E-9);
      assertTrue(TeleportRules.rising(eye, camera, 0, 16).distanceTo(eye) < 1.0E-9);
      assertTrue(TeleportRules.rising(eye, camera, 16, 16).distanceTo(camera) < 1.0E-9);
      assertTrue(TeleportRules.inView(camera, new Vec3(60, 64, -4), 96));
      assertFalse(TeleportRules.inView(camera, new Vec3(200, 64, -4), 96));
      assertFalse(TeleportRules.inView(camera, new Vec3(10, 160, -4), 96), "above the camera");
   }

   @Test
   void configAcceptsKnownKeysAndRejectsBadValues() {
      int before = ThorSceneConfig.grabWindowTicks;
      try {
         assertTrue(ThorSceneConfig.values().containsKey("grab_window_ticks"));
         assertTrue(ThorSceneConfig.set("grab_window_ticks", "25"));
         assertEquals(25, ThorSceneConfig.grabWindowTicks);
         assertFalse(ThorSceneConfig.set("grab_window_ticks", "-3"));
         assertFalse(ThorSceneConfig.set("grab_window_ticks", "abc"));
         assertFalse(ThorSceneConfig.set("no_such_key", "1"));
         assertEquals(25, ThorSceneConfig.grabWindowTicks);
      } finally {
         ThorSceneConfig.grabWindowTicks = before;
      }
   }

   @Test
   void grabAndTeleportSyncsRoundTrip() {
      GrabFlow flow = new GrabFlow(ResistKey.S);
      flow.resist(ResistKey.S, 0.25F, 14);
      for (SceneSync sync : new SceneSync[]{SceneSync.grab(3, 4, flow), SceneSync.teleport(3, new Vec3(1, 2, 3), new Vec3(1, 82, 3), 5, 16, 200),
         SceneSync.end(SceneSync.Kind.GRAB, 3, 4)}) {
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         sync.write(buf);
         assertEquals(sync, SceneSync.read(buf));
      }
      SceneSync grab = SceneSync.grab(3, 4, flow);
      assertSame(GrabFlow.Stage.HOLD, grab.grabStage());
      assertEquals(ResistKey.S.ordinal(), grab.resistKey());
      assertTrue(ThorSceneView.holds(grab));
      flow.commit(GrabAction.SLAM);
      assertEquals(-1, SceneSync.grab(3, 4, flow).resistKey(), "no key once Thor chose");
   }

   @Test
   void clientInputPacketRoundTrips() {
      var packet = new dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket(
         dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket.Op.TELEPORT_CONFIRM, 2, new BlockPos(5, 60, -7));
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      packet.toBytes(buf);
      FriendlyByteBuf original = new FriendlyByteBuf(buf.copy());
      var read = new dev.baranhan.viltrumitecore.hero.thor.scene.net.ThorSceneC2SPacket(buf);
      FriendlyByteBuf again = new FriendlyByteBuf(Unpooled.buffer());
      read.toBytes(again);
      assertEquals(original.readableBytes(), again.readableBytes());
      assertTrue(io.netty.buffer.ByteBufUtil.equals(original, again));
   }
}
