package dev.baranhan.viltrumitecore.client.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What a ridden host sees (spec §8.4): never black, glimpses, flashes, veins, the racing heartbeat. */
class VenomHostFxTest {
   @Test
   void theSlitNeverClosesAndGlimpsesTearItOpen() {
      for (int seed = 0; seed < 32; seed++) {
         for (float resist : new float[]{0.0F, 0.5F, 1.0F}) {
            float max = 0;
            for (double s = 0; s < 15; s += 0.05) {
               float open = VenomHostFx.slit(s, resist, seed);
               assertTrue(open >= VenomHostFx.MIN_OPEN && open <= 1.0F, "slit " + open + " at " + s);
               max = Math.max(max, open);
            }
            assertTrue(max > 0.8F, "a glimpse within the control phase, seed " + seed + " resist " + resist);
         }
      }
   }

   @Test
   void fightingBackOpensTheEyesWider() {
      double calm = 0;
      double fight = 0;
      for (double s = 0; s < 30; s += 0.05) {
         calm += VenomHostFx.slit(s, 0.0F, 7);
         fight += VenomHostFx.slit(s, 1.0F, 7);
      }
      assertTrue(fight > calm * 1.15, "resistance widens the view: " + calm + " vs " + fight);
   }

   @Test
   void flashesAreShortSpikesNotAConstantGlare() {
      for (int seed = 0; seed < 16; seed++) {
         int lit = 0;
         int total = 0;
         float max = 0;
         for (double s = 0; s < 20; s += 0.05) {
            float f = VenomHostFx.flash(s, seed);
            assertTrue(f >= 0.0F && f <= 1.0F);
            max = Math.max(max, f);
            if (f > 0.05F) {
               lit++;
            }
            total++;
         }
         assertTrue(max > 0.6F, "there are scary flashes, seed " + seed);
         assertTrue(lit < total * 0.15, "but they are rare, seed " + seed + ": " + lit + "/" + total);
      }
   }

   @Test
   void veinsCrawlInHoldAndDrawBack() {
      assertEquals(0.0F, VenomHostFx.veins(0.0F, false, 0.0F, 0.0));
      assertTrue(VenomHostFx.veins(0.5F, false, 0.0F, 0.0) < VenomHostFx.veins(1.0F, false, 0.0F, 0.0));
      for (double s = 0; s < 5; s += 0.1) {
         float v = VenomHostFx.veins(1.0F, true, 0.0F, s);
         assertTrue(v >= 0.8F && v <= 1.0F, "full cover in control: " + v);
         assertEquals(0.0F, VenomHostFx.veins(1.0F, true, 1.0F, s), "released: nothing stays on the screen");
      }
   }

   @Test
   void theHeartRacesAsTheHostFights() {
      assertEquals(20, VenomHostFx.heartbeatGap(0.0F));
      assertEquals(10, VenomHostFx.heartbeatGap(1.0F));
      assertTrue(VenomHostFx.heartbeatGap(5.0F) >= 9, "never faster than every 9 ticks");
      assertTrue(VenomHostFx.MIN_LIGHT > 0.0F, "the view is never black");
   }

   @Test
   void aLostEndNeverLeavesTheHostBlind() {
      // the control phase hears a state every 5 server ticks, the engulf is 30 ticks long: lag is not a lost END
      assertFalse(VenomHostFx.stale(false, 5 * 4));
      assertFalse(VenomHostFx.stale(true, 30 * 3));
      // but a session that heard nothing for seconds ends on its own
      assertTrue(VenomHostFx.stale(false, VenomHostFx.STALE_CONTROL + 1));
      assertTrue(VenomHostFx.stale(true, VenomHostFx.STALE_ENGULF + 1));
      assertTrue(VenomHostFx.STALE_ENGULF > VenomHostFx.STALE_CONTROL, "the engulf is silent by design");
   }

   @Test
   void theCountdownClimbsOverTheVoicePanel() {
      // no panel, or one far below: the bar stays under the crosshair
      assertEquals(240 / 2 + 34, VenomHostFx.resistY(240, Float.NaN));
      assertEquals(480 / 2 + 34, VenomHostFx.resistY(480, 400.0F));
      // the playtest case (GUI height 240, the inner voice's panel top at 126): lifted clear, over the crosshair
      int y = VenomHostFx.resistY(240, 126.0F);
      assertTrue(y + VenomHostFx.RESIST_BELOW < 126, "the hint ends above the panel");
      assertTrue(y + VenomHostFx.RESIST_BELOW <= 120 - 10, "and does not sit on the crosshair");
      assertTrue(y - VenomHostFx.RESIST_ABOVE >= 0, "the countdown stays on screen");
      // a panel a little lower: lifted but still under the crosshair
      int z = VenomHostFx.resistY(300, 200.0F);
      assertTrue(z + VenomHostFx.RESIST_BELOW < 200 && z - VenomHostFx.RESIST_ABOVE >= 150 + 10);
   }
}
