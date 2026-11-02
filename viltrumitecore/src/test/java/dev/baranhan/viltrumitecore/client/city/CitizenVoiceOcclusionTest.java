package dev.baranhan.viltrumitecore.client.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #138: citizen voices are quieter behind walls, and the change never pops. */
class CitizenVoiceOcclusionTest {
   @Test
   void open_thin_and_thick() {
      assertEquals(1.0F, CitizenVoiceOcclusion.factor(false, 0.0), 1.0E-6F);
      assertEquals(CitizenVoiceOcclusion.THIN, CitizenVoiceOcclusion.factor(true, 0.3), 1.0E-6F);
      assertEquals(CitizenVoiceOcclusion.THIN, CitizenVoiceOcclusion.factor(true, CitizenVoiceOcclusion.THIN_WALL), 1.0E-6F);
      assertEquals(CitizenVoiceOcclusion.THICK, CitizenVoiceOcclusion.factor(true, 6.0), 1.0E-6F);
      assertTrue(CitizenVoiceOcclusion.THICK < CitizenVoiceOcclusion.THIN && CitizenVoiceOcclusion.THIN < CitizenVoiceOcclusion.OPEN);
      assertTrue(CitizenVoiceOcclusion.THICK > 0.0F, "a muffled scream is still heard");
   }

   @Test
   void easing_moves_part_way_and_converges() {
      float f = 1.0F;
      f = CitizenVoiceOcclusion.ease(f, CitizenVoiceOcclusion.THICK);
      assertTrue(f < 1.0F && f > 0.6F, "one step is not a jump: " + f);
      for (int i = 0; i < 20; i++) {
         f = CitizenVoiceOcclusion.ease(f, CitizenVoiceOcclusion.THICK);
      }

      assertEquals(CitizenVoiceOcclusion.THICK, f, 0.01F);
   }
}
