package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Wall crawling on a facade (spec §4.3): climb, descend, hold, slide, crab-walk and the wall run. */
class VenomCrawlTest {
   /** A wall to the north: its face points south (+z), Venom faces the wall (-z). */
   private static final Vec3 NORMAL = new Vec3(0, 0, 1);
   private static final Vec3 INTO = new Vec3(0, 0, -1);
   private static final Vec3 STILL = Vec3.ZERO;

   @Test
   void forwardClimbsBackDescendsSneakHolds() {
      Vec3 up = VenomCrawlClient.motion(NORMAL, INTO, 1.0F, 0.0F, false, false, STILL);
      assertEquals(VenomRules.WALL_CLIMB_SPEED, up.y, 1.0E-9);
      assertTrue(up.z < 0, "keeps hugging the wall");
      Vec3 down = VenomCrawlClient.motion(NORMAL, INTO, -1.0F, 0.0F, false, false, STILL);
      assertEquals(-VenomRules.WALL_CLIMB_SPEED, down.y, 1.0E-9);
      Vec3 hold = VenomCrawlClient.motion(NORMAL, INTO, 0.0F, 0.0F, false, true, STILL);
      assertEquals(0.0, hold.y, 1.0E-9, "sneak hangs on the wall");
   }

   @Test
   void noInputSlidesSlowlyInsteadOfFalling() {
      Vec3 fall = new Vec3(0, -1.2, 0);
      Vec3 v = VenomCrawlClient.motion(NORMAL, INTO, 0.0F, 0.0F, false, false, fall);
      assertEquals(VenomCrawlClient.SLIDE, v.y, 1.0E-9);
      Vec3 rising = VenomCrawlClient.motion(NORMAL, INTO, 0.0F, 0.0F, false, false, new Vec3(0, 0.3, 0));
      assertEquals(0.3, rising.y, 1.0E-9, "a jump off the wall is not cut");
   }

   @Test
   void strafingCrabWalksAlongTheFacade() {
      Vec3 left = VenomCrawlClient.motion(NORMAL, INTO, 0.0F, 1.0F, false, true, STILL);
      Vec3 right = VenomCrawlClient.motion(NORMAL, INTO, 0.0F, -1.0F, false, true, STILL);
      assertTrue(Math.abs(left.x) > 0.05, "moves along the wall");
      assertEquals(-left.x, right.x, 1.0E-9, "mirrored");
      // facing -z, the left is -x in Minecraft (yaw 180)
      assertTrue(left.x < 0, "A goes to the player's left");
   }

   @Test
   void sprintingAlongTheWallRunsWithoutGravity() {
      Vec3 diagonal = new Vec3(1, 0, -0.2).normalize();
      Vec3 run = VenomCrawlClient.motion(NORMAL, diagonal, 1.0F, 0.0F, true, false, new Vec3(0, -0.5, 0));
      assertEquals(0.0, run.y, 1.0E-9);
      assertEquals(VenomRules.WALL_RUN_SPEED, Math.abs(run.x), 1.0E-9);
      assertTrue(run.x > 0, "in the facing's direction");
      assertTrue(run.z < 0, "pressed to the wall");
   }

   @Test
   void facingAwayWalksOffTheWall() {
      Vec3 away = new Vec3(0, 0, 1);
      Vec3 v0 = new Vec3(0.1, -0.3, 0.2);
      Vec3 v = VenomCrawlClient.motion(NORMAL, away, 1.0F, 0.0F, false, false, v0);
      assertEquals(v0.x, v.x, 1.0E-9);
      assertEquals(v0.z, v.z, 1.0E-9);
      assertEquals(VenomCrawlClient.SLIDE, v.y, 1.0E-9);
   }
}
