package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** B-M10: every electric attack has its own first-person arm, and no vanilla swing rides on an authored strike. */
class ThorFirstPersonTest {
   @Test
   void electricHandsPickTheirRows() {
      assertSame(ThorFirstPerson.DISCHARGE, ThorFirstPerson.rows(ThorMove.ELECTRIC_COMBO, 1));
      assertSame(ThorFirstPerson.GUARD, ThorFirstPerson.rows(ThorMove.ELECTRIC_COMBO, ThorCombatClips.LEFT_HAND));
   }

   @Test
   void rowsStartAndEndAtRest() {
      for (float[][] rows : new float[][][]{ThorFirstPerson.STRIKE, ThorFirstPerson.GUARD, ThorFirstPerson.DISCHARGE, ThorFirstPerson.WAVE,
         ThorFirstPerson.WRATH}) {
         float[] last = rows[rows.length - 1];
         assertTrue(rows[0][0] == 0.0F && last[1] == 0.0F && last[2] == 0.0F && last[3] == 0.0F, "no pop at either end");
      }
   }

   @Test
   void noVanillaSwingOnAuthoredAttacks() throws Exception {
      String client = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/thor/ThorCombatClient.java"));
      assertFalse(client.contains("player.swing("), "animation-system §6: no second vanilla swing on an authored attack");
      String mixin = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/mixin/FirstPersonThorMixin.java"));
      assertTrue(mixin.contains("ThorFirstPerson.apply("));
      String fp = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/client/thor/ThorFirstPerson.java"));
      for (String move : new String[]{"ELECTRIC_WAVE", "ELECTRIC_STORM", "WRATH"}) {
         assertTrue(fp.contains("ThorMove." + move), move + " has a first-person motion");
      }
   }
}
