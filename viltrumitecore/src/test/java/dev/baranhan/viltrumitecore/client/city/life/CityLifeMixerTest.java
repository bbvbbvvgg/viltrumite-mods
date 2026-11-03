package dev.baranhan.viltrumitecore.client.city.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The client mix of the living city (issue #136): voice kinds and limits, occlusion, distance and the beds. */
class CityLifeMixerTest {
   private static final int CROWD = 0;
   private static final int CROWD_FAR = 1;
   private static final int STEPS = 2;
   private static final int RUNNING = 3;
   private static final int DAY = 4;
   private static final int NIGHT = 5;
   private static final int WIND = 6;
   private static final int PANIC = 7;
   private static final int HUSH = 8;

   @Test
   void kindsOfVoiceEvents() {
      assertEquals(CityLifeMixer.TALK, CityLifeMixer.kindOf("city_life.voice.m1.greet_1"));
      assertEquals(CityLifeMixer.CALL, CityLifeMixer.kindOf("city_life.voice.f2.flee_3"));
      assertEquals(CityLifeMixer.CALL, CityLifeMixer.kindOf("city_life.voice.m2.help_1"));
      assertEquals(CityLifeMixer.SCREAM, CityLifeMixer.kindOf("city_life.scream.female"));
      assertEquals(CityLifeMixer.QUIET, CityLifeMixer.kindOf("city_life.cough"));
      assertEquals(CityLifeMixer.QUIET, CityLifeMixer.kindOf("city_life.sob.male"));
      assertEquals(-1, CityLifeMixer.kindOf("city_life.bed.crowd"), "beds are not limited voices");
      assertEquals(-1, CityLifeMixer.kindOf("scp096.cry"));
      assertEquals("greet_1", CityLifeMixer.lineOf("city_life.voice.m1.greet_1"));
      for (int k = 0; k < CityLifeMixer.KINDS; k++) {
         assertTrue(CityLifeMixer.cap(k) >= 2 && CityLifeMixer.cap(k) <= 4, "a few voices per kind at once");
      }
   }

   @Test
   void distanceAndOcclusion() {
      assertEquals(1.0F, CityLifeMixer.linear(0, 14));
      assertEquals(0.5F, CityLifeMixer.linear(7, 14), 1e-6F);
      assertEquals(0.0F, CityLifeMixer.linear(20, 14));
      assertEquals(CityLifeMixer.OPEN, CityLifeMixer.occlusion(false, 5));
      assertEquals(CityLifeMixer.THIN, CityLifeMixer.occlusion(true, 1.0));
      assertEquals(CityLifeMixer.THICK, CityLifeMixer.occlusion(true, 6.0), "a building between muffles more than a window");
      assertTrue(CityLifeMixer.weight(2, 40) > CityLifeMixer.weight(20, 40));
      assertEquals(0.0F, CityLifeMixer.weight(41, 40));
      assertEquals(1.0F, CityLifeMixer.amount(10, 5));
      assertTrue(CityLifeMixer.amount(1, 6) < CityLifeMixer.amount(3, 6));
   }

   @Test
   void dayNightAndTrauma() {
      assertEquals(1.0F, CityLifeMixer.daylight(6000));
      assertEquals(0.0F, CityLifeMixer.daylight(18000));
      float dusk = CityLifeMixer.daylight(12500);
      assertTrue(dusk > 0.0F && dusk < 1.0F, "dusk fades");
      float t = CityLifeMixer.trauma(1.0F, (int)CityLifeMixer.TRAUMA_HALF_LIFE, 0.0F);
      assertEquals(0.5F, t, 1e-3F);
      assertEquals(0.8F, CityLifeMixer.trauma(0.1F, 0, 0.8F), "raised by what the street shows");
   }

   @Test
   void bedsFollowTheStreet() {
      // a busy calm street by day
      float[] calm = CityLifeMixer.beds(8, 20, 6, 0, 0, 0, 1, false, false, 0, 0);
      assertTrue(calm[CROWD] > 0.6F && calm[STEPS] > 0.6F && calm[DAY] > 0.3F, "murmur, steps and the day street");
      assertEquals(0.0F, calm[PANIC]);
      assertEquals(0.0F, calm[HUSH]);
      assertEquals(0.0F, calm[NIGHT]);
      assertTrue(calm[WIND] > 0.0F && calm[WIND] < 0.3F, "a little wind between the buildings");
      // panic: screams and running take over, the murmur drops
      float[] panic = CityLifeMixer.beds(1, 5, 0, 6, 6, 0.6F, 1, false, false, 0, 0.3F);
      assertTrue(panic[PANIC] > 0.9F && panic[RUNNING] > 0.9F);
      assertTrue(panic[CROWD] < calm[CROWD] * 0.3F);
      // aftermath: nobody panics any more, the hush and the wind of the ruins
      float[] after = CityLifeMixer.beds(2, 4, 1, 0, 0, 0.7F, 1, false, false, 0, 0.8F);
      assertTrue(after[HUSH] > 0.3F && after[PANIC] == 0.0F);
      assertTrue(after[WIND] > calm[WIND], "ruins are windy");
      assertTrue(after[CROWD] < calm[CROWD] * 0.5F, "people are quieter");
      // night, indoors, high above the street
      float[] night = CityLifeMixer.beds(8, 20, 6, 0, 0, 0, 0, false, false, 0, 0);
      assertTrue(night[NIGHT] > 0.3F && night[DAY] == 0.0F);
      float[] indoor = CityLifeMixer.beds(8, 20, 6, 0, 0, 0, 1, false, true, 0, 0);
      assertTrue(indoor[CROWD] < calm[CROWD] * 0.5F, "walls muffle the street");
      float[] high = CityLifeMixer.beds(8, 20, 6, 0, 0, 0, 1, false, false, 60, 0);
      assertEquals(0.0F, high[CROWD], "no murmur 60 blocks above the people");
      assertEquals(0.0F, high[STEPS]);
      assertTrue(high[WIND] > calm[WIND] * 2.0F, "wind up high");
      float[] rain = CityLifeMixer.beds(8, 20, 6, 0, 0, 0, 1, true, false, 0, 0);
      assertTrue(rain[CROWD] < calm[CROWD]);
      for (float[] b : new float[][]{calm, panic, after, night, indoor, high, rain}) {
         assertEquals(9, b.length);
         for (float v : b) {
            assertTrue(v >= 0.0F && v <= 1.0F, "volume in range: " + v);
         }
      }
   }
}
