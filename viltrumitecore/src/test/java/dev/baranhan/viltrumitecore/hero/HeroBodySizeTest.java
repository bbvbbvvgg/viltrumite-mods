package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorHero;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Rework R1: Thor's hitbox per pose, the round trip Thor → human → Thor, and the passages it must fit. */
class HeroBodySizeTest {
   private static final float EPS = 1.0E-4F;
   /** Player.POSES / getStandingEyeHeight (1.20.1). */
   private static final Map<Pose, EntityDimensions> VANILLA = new EnumMap<>(Pose.class);
   private static final Map<Pose, Float> EYE = new EnumMap<>(Pose.class);
   private static final HeroBodySize.Scale THOR = HeroBodySize.Scale.of(ThorHero.BODY_WIDTH_SCALE, ThorHero.BODY_HEIGHT_SCALE);

   static {
      put(Pose.STANDING, EntityDimensions.scalable(0.6F, 1.8F), 1.62F);
      put(Pose.CROUCHING, EntityDimensions.scalable(0.6F, 1.5F), 1.27F);
      put(Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F), 0.4F);
      put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6F, 0.6F), 0.4F);
      put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6F, 0.6F), 0.4F);
      put(Pose.SLEEPING, EntityDimensions.fixed(0.2F, 0.2F), 0.2F);
   }

   private static void put(Pose pose, EntityDimensions dims, float eye) {
      VANILLA.put(pose, dims);
      EYE.put(pose, eye);
   }

   private static EntityDimensions size(Pose pose, HeroBodySize.Scale scale) {
      return HeroBodySize.dimensions(VANILLA.get(pose), pose, scale);
   }

   private static float eye(Pose pose, HeroBodySize.Scale scale) {
      return HeroBodySize.eyeHeight(EYE.get(pose), pose, scale);
   }

   @Test
   void thorStandsThreePointOneFiveAndAlmostOneWide() {
      EntityDimensions standing = size(Pose.STANDING, THOR);
      assertEquals(3.15F, standing.height, EPS);
      assertTrue(standing.height >= 3.0F && standing.height <= 3.2F, "spec §1: ~3.0–3.2 tall");
      assertEquals(0.96F, standing.width, EPS);
      assertEquals(2.835F, eye(Pose.STANDING, THOR), EPS);
      assertEquals(2.625F, size(Pose.CROUCHING, THOR).height, EPS);
      assertEquals(2.2225F, eye(Pose.CROUCHING, THOR), EPS);
      for (Pose lying : new Pose[]{Pose.SWIMMING, Pose.FALL_FLYING, Pose.SPIN_ATTACK}) {
         assertEquals(0.9F, size(lying, THOR).height, EPS, lying.name());
         assertEquals(0.96F, size(lying, THOR).width, EPS, lying.name());
         assertTrue(eye(lying, THOR) < size(lying, THOR).height, lying.name());
      }

      assertEquals(0.2F, size(Pose.SLEEPING, THOR).height, EPS, "a bed holds Thor like anyone");
      assertEquals(0.2F, size(Pose.SLEEPING, THOR).width, EPS);
      for (Pose pose : VANILLA.keySet()) {
         assertTrue(eye(pose, THOR) <= size(pose, THOR).height, pose.name() + " eye inside the box");
      }
   }

   @Test
   void roundTripThorHumanThorRestoresEveryPose() {
      HeroBodySize.Scale human = HeroBodySize.Scale.VANILLA;
      assertTrue(HeroBodyTracker.needsRefresh(null, THOR), "login / respawn as Thor");
      assertFalse(HeroBodyTracker.needsRefresh(null, human), "a plain first sight changes nothing");
      assertTrue(HeroBodyTracker.needsRefresh(THOR, human), "leaving Thor");
      assertFalse(HeroBodyTracker.needsRefresh(human, human));
      assertTrue(HeroBodyTracker.needsRefresh(human, THOR), "choosing Thor again");
      assertFalse(HeroBodyTracker.needsRefresh(THOR, HeroBodySize.Scale.of(ThorHero.BODY_WIDTH_SCALE, ThorHero.BODY_HEIGHT_SCALE)));
      for (Pose pose : VANILLA.keySet()) {
         EntityDimensions thor = size(pose, THOR);
         assertSame(VANILLA.get(pose), size(pose, human), pose.name() + " human is vanilla");
         assertEquals(EYE.get(pose), eye(pose, human), EPS);
         EntityDimensions again = size(pose, THOR);
         assertEquals(thor.width, again.width, EPS);
         assertEquals(thor.height, again.height, EPS);
      }
   }

   @Test
   void brokenFactorsFallBackToVanilla() {
      assertTrue(HeroBodySize.Scale.of(Float.NaN, 0.0F).vanilla());
      assertTrue(HeroBodySize.Scale.of(-2.0F, Float.POSITIVE_INFINITY).vanilla());
   }

   /** Air column test: a box at block-aligned feet must stay inside an opening of w × h blocks. */
   private static boolean fits(Pose pose, double openingWidth, double openingHeight) {
      EntityDimensions dims = size(pose, THOR);
      AABB box = HeroBodySize.box(new Vec3(0.5, 0.0, 0.5), dims).deflate(1.0E-7);
      double half = openingWidth / 2.0;
      return box.minX >= 0.5 - half && box.maxX <= 0.5 + half && box.maxY <= openingHeight && box.minY >= 0.0;
   }

   @Test
   void passagesThorCanUse() {
      // 1-wide 3-tall corridor / a door with a block above: walks.
      assertTrue(fits(Pose.STANDING, 1.0, 4.0));
      assertFalse(fits(Pose.STANDING, 1.0, 3.0), "3.15 > 3: a 3-tall opening needs a crouch");
      assertTrue(fits(Pose.CROUCHING, 1.0, 3.0));
      // 2-block passage (doors, a 2-high tunnel): too low to crouch, so the vanilla pose logic crawls.
      assertFalse(fits(Pose.CROUCHING, 1.0, 2.0));
      assertTrue(fits(Pose.SWIMMING, 1.0, 2.0));
      // A 1-block hole: still crawls.
      assertTrue(fits(Pose.SWIMMING, 1.0, 1.0));
   }

   @Test
   void poseFitBoxMatchesTheSizeEventBox() {
      Vec3 feet = new Vec3(10.25, 64.0, -3.75);
      for (Pose pose : VANILLA.keySet()) {
         EntityDimensions dims = size(pose, THOR);
         AABB expected = dims.makeBoundingBox(feet);
         AABB box = HeroBodySize.box(feet, dims);
         assertEquals(expected.minX, box.minX, 1.0E-9, pose.name());
         assertEquals(expected.maxY, box.maxY, 1.0E-9, pose.name());
         assertEquals(expected.maxZ, box.maxZ, 1.0E-9, pose.name());
      }
   }
}
