package dev.baranhan.viltrumitecore.worldevent.aftermath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract ids and channel weights of the causes. */
class AftermathCauseTest {
   @Test
   void contractIdsAreStableAndUnique() {
      Set<String> ids = new HashSet<>();
      for (AftermathCause c : AftermathCause.values()) {
         assertTrue(ids.add(c.id), c.id);
         assertEquals(c, AftermathCause.of(c.id));
         assertEquals(c, AftermathCause.of(" " + c.id.toUpperCase() + " "));
         for (int ch = 0; ch < AftermathCause.CHANNELS; ch++) {
            assertTrue(c.weight(ch) >= 0.0F && c.weight(ch) <= 1.0F, c + " " + ch);
         }
      }
      // the ids the parallel branches send (docs/city-aftermath-integration.md)
      for (String id : new String[]{"meteor", "chitauri", "leviathan", "hero", "explosion", "destruction", "collapse", "ultron", "infrastructure", "other", "godzilla"}) {
         assertEquals(id, AftermathCause.of(id).id);
      }
      assertEquals(AftermathCause.OTHER, AftermathCause.of("tornado"));
      assertEquals(AftermathCause.OTHER, AftermathCause.of(null));
      assertTrue(AftermathCause.values().length < 31, "fits the int mask");
   }

   @Test
   void maskTakesTheStrongestWeightAndSpecificCausesWin() {
      int mask = AftermathCause.DESTRUCTION.bit() | AftermathCause.METEOR.bit();
      assertEquals(Math.max(AftermathCause.DESTRUCTION.weight(AftermathCause.FIRE), AftermathCause.METEOR.weight(AftermathCause.FIRE)),
         AftermathCause.weight(mask, AftermathCause.FIRE), 1.0E-6);
      assertEquals(AftermathCause.OTHER.weight(AftermathCause.SMOKE), AftermathCause.weight(0, AftermathCause.SMOKE), 1.0E-6);
      assertEquals(AftermathCause.METEOR, AftermathCause.dominant(mask));
      assertEquals(AftermathCause.CHITAURI, AftermathCause.dominant(AftermathCause.EXPLOSION.bit() | AftermathCause.CHITAURI.bit()));
      assertEquals(AftermathCause.OTHER, AftermathCause.dominant(0));
      assertTrue(AftermathCause.METEOR.weight(AftermathCause.FIRE) > AftermathCause.COLLAPSE.weight(AftermathCause.FIRE), "meteors burn more than collapses");
      assertTrue(AftermathCause.COLLAPSE.weight(AftermathCause.DUST) >= 0.9F, "a collapse is mostly dust");
   }
}
