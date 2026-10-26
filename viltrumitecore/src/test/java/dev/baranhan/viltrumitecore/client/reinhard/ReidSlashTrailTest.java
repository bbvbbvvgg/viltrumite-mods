package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.*;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeArc;
import dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves;
import org.junit.jupiter.api.Test;

class ReidSlashTrailTest {
   @Test
   void everyOrdinaryStrikeHasItsOwnTrailAndNothingElseDoes() {
      for (int phase = 0; phase < MeleeMoves.REID.length; phase++) {
         ReidSlashTrail.Trail trail = ReidSlashTrail.trail(ReinhardAbility.REID_STRIKE.ordinal(), phase);
         assertSame(MeleeMoves.reid(phase).swing(), trail.swing(), "the trail draws the server hit path");
         assertSame(MeleeMoves.reid(phase).heavy() ? ReidSlashTrail.REID_HEAVY : ReidSlashTrail.REID, trail.style());
      }
      for (int phase = 0; phase < MeleeMoves.FIST.length; phase++) {
         ReidSlashTrail.Trail trail = ReidSlashTrail.trail(ReinhardAbility.LIGHT_PUNCH.ordinal(), phase);
         assertSame(MeleeMoves.fist(phase).swing(), trail.swing());
         assertSame(ReidSlashTrail.FIST, trail.style());
      }
      assertSame(MeleeMoves.KNIGHT[2], ReidSlashTrail.trail(ReinhardAbility.FIST_COMBO.ordinal(), 2).swing());
      assertNull(ReidSlashTrail.trail(-1, 0));
      assertNull(ReidSlashTrail.trail(ReinhardAbility.WORLD_CUT.ordinal(), 0));
   }

   @Test
   void stylesStaySubtle() {
      for (ReidSlashTrail.Style s : new ReidSlashTrail.Style[]{ReidSlashTrail.REID, ReidSlashTrail.REID_HEAVY, ReidSlashTrail.FIST}) {
         assertTrue(s.inner() > 0.3 && s.inner() < s.outer(), "never on the camera, hilt side inside the tip");
         assertTrue(s.alpha() <= 170, "a light accent, not a screen-filling flash");
         assertTrue(s.trailTicks() > 1 && s.trailTicks() < 4);
      }
      assertTrue(ReidSlashTrail.FIST.alpha() < ReidSlashTrail.REID.alpha());
      assertTrue(ReidSlashTrail.REID_HEAVY.outer() > ReidSlashTrail.REID.outer());
      assertTrue(ReidSlashTrail.REID.outer() < 2.5, "about hand + blade length, not the generous hit reach");
   }

   @Test
   void windowFollowsTheSweepThenFades() {
      MeleeArc.Swing swing = MeleeMoves.reid(MeleeMoves.SLASH_SWEEP).swing();
      double trail = ReidSlashTrail.REID.trailTicks();
      assertNull(ReidSlashTrail.window(swing, swing.contactStart() - 1.5, trail), "nothing before the blade moves");
      double lastHead = 0;
      for (double t = swing.contactStart() - 0.9; t <= swing.contactEnd() + 1; t += 0.2) {
         double[] w = ReidSlashTrail.window(swing, t, trail);
         assertNotNull(w, "visible at " + t);
         assertTrue(w[0] >= lastHead, "head never runs backwards");
         assertTrue(w[1] < w[0] && w[1] >= 0);
         assertEquals(1.0, w[2], 1.0E-9, "full strength during contact");
         lastHead = w[0];
      }
      double[] fading = ReidSlashTrail.window(swing, swing.contactEnd() + 2, trail);
      assertNotNull(fading);
      assertEquals(1.0, fading[0], 1.0E-9);
      assertTrue(fading[2] < 1.0 && fading[2] > 0);
      assertNull(ReidSlashTrail.window(swing, swing.contactEnd() + 1 + ReidSlashTrail.FADE_TICKS, trail), "gone after the fade");
      assertNull(ReidSlashTrail.window(swing, swing.contactEnd() + 1 + trail + 0.01, trail), "or once the tail caught up");
   }
}
