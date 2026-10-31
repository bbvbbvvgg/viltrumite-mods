package dev.baranhan.viltrumitecore.client.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The citizen body bakes for both arm widths and every look switches its parts; idle gestures vary. */
class CitizenModelTest {
   @Test
   void bothBodiesBakeAndLooksSwitchTheirParts() {
      for (boolean slim : new boolean[]{false, true}) {
         CitizenModel m = new CitizenModel(CitizenModel.create(slim).bakeRoot(), slim);
         Random rnd = new Random(3);
         int skirts = 0;
         int caps = 0;
         int ponytails = 0;
         for (int i = 0; i < 300; i++) {
            CitizenLook l = CitizenLook.of(rnd.nextInt());
            m.prepare(l);
            assertTrue(m.shoeR.visible && m.shoeL.visible && m.nose.visible);
            assertEquals(l.skirtPart(), m.skirt.visible);
            assertEquals(l.hat() == CitizenLook.Hat.CAP, m.visor.visible);
            assertEquals(l.hat() != CitizenLook.Hat.NONE, m.crown.visible);
            assertFalse(m.backpack.visible && m.bag.visible, "one bag at a time");
            skirts += m.skirt.visible ? 1 : 0;
            caps += m.visor.visible ? 1 : 0;
            ponytails += m.ponytail.visible ? 1 : 0;
         }
         assertTrue(skirts > 0 && caps > 0 && ponytails > 0);
      }
   }

   @Test
   void idleGesturesVaryAndWalkersOnlyKeepThePhone() {
      Set<Integer> seen = new HashSet<>();
      Random rnd = new Random(5);
      for (int i = 0; i < 2000; i++) {
         int seed = rnd.nextInt();
         int w = rnd.nextInt(1000);
         seen.add(CitizenPoser.gestureFor(seed, w, 10, false));
         int walking = CitizenPoser.gestureFor(seed, w, 10, true);
         assertTrue(walking == CitizenPoser.NONE || walking == CitizenPoser.PHONE);
         // a look at the watch / a head scratch is short: late in the window it is over
         int late = CitizenPoser.gestureFor(seed, w, 120, false);
         assertTrue(late != CitizenPoser.WATCH && late != CitizenPoser.SCRATCH);
      }
      assertEquals(6, seen.size());
      assertTrue(CitizenPoser.approach(1.0F, 0.3F) > 0.2F && CitizenPoser.approach(0.0F, 0.3F) == 0.0F);
   }
}
