package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumiteflight.util.FlightProfile;
import dev.baranhan.viltrumitecore.hero.HumanHero;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import org.junit.jupiter.api.Test;

class IronManFlightTest {

   @Test
   void glideProfileValues() {
      FlightProfile p = IronManRules.glideProfile();
      assertTrue(p.glide());
      assertEquals(0.12F, p.glideSink(), 1.0E-6F);
   }

   @Test
   void normalFlightIsTheOriginalFlight() {
      // same flight as Homelander: no profile while the suit has energy
      assertNull(IronManHero.profileFor(true, false));
   }

   @Test
   void profileNullForOtherHeroes() {
      assertNull(new HumanHero().flightProfile(null));
      assertNull(new HomelanderHero().flightProfile(null));
      assertNull(RegulusHero.INSTANCE.flightProfile(null));
   }

   @Test
   void profileNullWithoutSuit() {
      assertNull(new IronManHero().flightProfile(null));
      assertNull(IronManHero.profileFor(false, false));
      assertNull(IronManHero.profileFor(false, true));
   }

   @Test
   void glideProfileWhenEmpty() {
      assertTrue(IronManHero.profileFor(true, true).glide());
      assertEquals(IronManRules.glideProfile(), IronManHero.profileFor(true, true));
   }
}
