package dev.baranhan.viltrumitecore.client.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Keyframe blending of a cinematic scene path and the focus-frame transform (wave1-22, T4). */
class CameraPathTest {
   private static CameraPath two() {
      return new CameraPath(List.of(
         new CameraPath.Key(new Vec3(2.0, 1.0, 0.0), CameraPath.DEFAULT_LOOK, 60.0F),
         new CameraPath.Key(new Vec3(-2.0, 3.0, 4.0), new Vec3(0.0, 2.0, 0.0), 80.0F)));
   }

   @Test
   void theEndsAreTheKeysThemselves() {
      CameraPath.Key first = two().sample(0.0F, 1.0F);
      assertEquals(2.0, first.offset().x, 1.0E-9);
      assertEquals(1.0, first.offset().y, 1.0E-9);
      assertEquals(60.0F, first.fov(), 1.0E-6F);

      CameraPath.Key last = two().sample(1.0F, 1.0F);
      assertEquals(-2.0, last.offset().x, 1.0E-9);
      assertEquals(4.0, last.offset().z, 1.0E-9);
      assertEquals(80.0F, last.fov(), 1.0E-6F);
   }

   @Test
   void theMiddleIsTheAverageOfTheNeighbours() {
      CameraPath.Key middle = two().sample(0.5F, 1.0F);
      assertEquals(0.0, middle.offset().x, 1.0E-9);
      assertEquals(2.0, middle.offset().y, 1.0E-9);
      assertEquals(2.0, middle.offset().z, 1.0E-9);
      assertEquals(1.6, middle.look().y, 1.0E-9);
      assertEquals(70.0F, middle.fov(), 1.0E-6F);
   }

   @Test
   void progressIsClampedAndDegeneratePathsAreSafe() {
      assertEquals(4.0, two().sample(9.0F, 1.0F).offset().z, 1.0E-9);
      CameraPath single = new CameraPath(List.of(new CameraPath.Key(new Vec3(0.0, 1.0, 0.0), CameraPath.DEFAULT_LOOK, 0.0F)));
      assertEquals(1.0, single.sample(0.7F, 1.0F).offset().y, 1.0E-9);
      assertEquals(Vec3.ZERO, new CameraPath(List.of()).sample(0.5F, 1.0F).offset());
   }

   @Test
   void reducedAmplitudeShrinksThePositionAndKeepsTheAim() {
      CameraPath.Key middle = two().sample(0.5F, 0.5F);
      assertEquals(1.0, middle.offset().y, 1.0E-9);
      assertEquals(1.6, middle.look().y, 1.0E-9);
   }

   @Test
   void aLensNamedOnceOnEitherSideIsInterpolatedAcrossTheGap() {
      // The demo scene's shape: 66, none, 72. The old per-segment borrow held 66 for the whole first
      // segment and then jumped to 72 at the cut; the lens must ramp instead.
      CameraPath path = new CameraPath(List.of(
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 66.0F),
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 0.0F),
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 72.0F)));

      assertEquals(69.0F, path.sample(0.5F, 1.0F).fov(), 1.0E-4F);
      assertEquals(68.94F, path.sample(0.49F, 1.0F).fov(), 1.0E-3F);
      assertEquals(69.06F, path.sample(0.51F, 1.0F).fov(), 1.0E-3F);
      assertTrue(Math.abs(path.sample(0.51F, 1.0F).fov() - path.sample(0.49F, 1.0F).fov()) < 0.2F,
         "the lens must be continuous across the keyframe");
      assertEquals(66.0F, path.sample(0.0F, 1.0F).fov(), 1.0E-4F);
      assertEquals(72.0F, path.sample(1.0F, 1.0F).fov(), 1.0E-4F);
   }

   @Test
   void aLensAtTheEndsIsCarriedOutwardsAndAbsenceStaysThePlayersOwn() {
      CameraPath held = new CameraPath(List.of(
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 0.0F),
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 40.0F),
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 0.0F)));
      assertEquals(40.0F, held.key(0).fov(), 1.0E-4F);
      assertEquals(40.0F, held.key(2).fov(), 1.0E-4F);

      CameraPath none = new CameraPath(List.of(
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 0.0F),
         new CameraPath.Key(Vec3.ZERO, CameraPath.DEFAULT_LOOK, 0.0F)));
      assertEquals(0.0F, none.key(0).fov(), 1.0E-4F);
      assertEquals(0.0F, none.sample(0.5F, 1.0F).fov(), 1.0E-4F);
   }

   @Test
   void theFocusFrameTurnsWithYaw() {
      assertEquals(2.0, CameraPath.toWorld(new Vec3(0.0, 0.0, 2.0), Vec3.ZERO, 0.0F).z, 1.0E-9);
      assertEquals(-1.0, CameraPath.toWorld(new Vec3(1.0, 0.0, 0.0), Vec3.ZERO, 0.0F).x, 1.0E-9);
      assertEquals(-2.0, CameraPath.toWorld(new Vec3(0.0, 0.0, 2.0), Vec3.ZERO, 90.0F).x, 1.0E-6);
      assertEquals(67.0, CameraPath.toWorld(new Vec3(0.0, 3.0, 0.0), new Vec3(5.0, 64.0, 5.0), 0.0F).y, 1.0E-9);
   }
}
