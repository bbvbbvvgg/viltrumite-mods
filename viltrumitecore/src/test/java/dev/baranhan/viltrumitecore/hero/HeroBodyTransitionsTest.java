package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.*;
import dev.baranhan.viltrumitecore.hero.thor.ThorHero;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;

/** Regression for stale boxes after hero swaps, respawns, pose changes and dimension travel. */
class HeroBodyTransitionsTest {
   private static final EntityDimensions VANILLA = EntityDimensions.scalable(0.6F, 1.8F);
   private static final HeroBodySize.Scale THOR =
      HeroBodySize.Scale.of(ThorHero.BODY_WIDTH_SCALE, ThorHero.BODY_HEIGHT_SCALE);

   @Test void staleBodyIsDetectedEvenWithoutScaleTransition() {
      EntityDimensions human = HeroBodySize.dimensions(VANILLA, Pose.STANDING, HeroBodySize.Scale.VANILLA);
      assertFalse(HeroBodySize.matches(human.width, human.height, 1.62F,
         VANILLA, 1.62F, Pose.STANDING, THOR));
      assertTrue(HeroBodyTracker.shouldRefresh(THOR, THOR, false, false));
      assertFalse(HeroBodyTracker.shouldRefresh(THOR, THOR, false, true));
      assertTrue(HeroBodyTracker.shouldRefresh(THOR, HeroBodySize.Scale.VANILLA, false, true));
   }

   @Test void refreshedBodyAndEyeMatchEveryPose() {
      for (Pose pose : new Pose[]{Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING, Pose.SLEEPING}) {
         EntityDimensions base = pose == Pose.SLEEPING ? EntityDimensions.fixed(0.2F, 0.2F) : VANILLA;
         EntityDimensions expected = HeroBodySize.dimensions(base, pose, THOR);
         float eye = HeroBodySize.eyeHeight(1.62F, pose, THOR);
         assertTrue(HeroBodySize.matches(expected.width, expected.height, eye, base, 1.62F, pose, THOR));
         assertFalse(HeroBodySize.matches(expected.width, expected.height, eye + 0.01F, base, 1.62F, pose, THOR));
         assertEquals(expected.width, HeroBodySize.localBox(expected).getXsize(), 0.0001);
      }
   }
}
