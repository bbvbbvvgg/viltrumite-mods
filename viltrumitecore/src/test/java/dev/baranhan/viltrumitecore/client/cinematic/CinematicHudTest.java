package dev.baranhan.viltrumitecore.client.cinematic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The one pure rule of the cinematic HUD: when the slowed sound bed is let go (wave1-22, T4). */
class CinematicHudTest {
   @Test
   void theSoundBedIsReleasedOnlyOnTheWayOutOfTheDuck() {
      assertTrue(CinematicHud.releaseNow(true, false), "the duck just ended");
      assertFalse(CinematicHud.releaseNow(true, true), "still slowed: the duck keeps its target");
      assertFalse(CinematicHud.releaseNow(false, false), "already released: no second fade to restart");
      assertFalse(CinematicHud.releaseNow(false, true));
   }
}
