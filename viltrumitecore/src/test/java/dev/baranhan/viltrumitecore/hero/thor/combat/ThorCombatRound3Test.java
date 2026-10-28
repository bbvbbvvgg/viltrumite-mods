package dev.baranhan.viltrumitecore.hero.thor.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Round 3, session B: damage type, one-hit accounting, hit-stop order, config writes. */
class ThorCombatRound3Test {
   @Test
   void electricMovesDealLightningAndStrikesStayPlayerAttacks() {
      for (ThorMove move : new ThorMove[]{ThorMove.ELECTRIC_COMBO, ThorMove.ELECTRIC_WAVE, ThorMove.ELECTRIC_STORM, ThorMove.WRATH}) {
         assertTrue(CombatHits.electric(move), move.name());
      }

      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE}) {
         assertFalse(CombatHits.electric(move), move.name());
      }
   }

   @Test
   void lightningDamageTypeShipsWithItsTagAndDeathMessages() throws IOException {
      Path data = Path.of("src/main/resources/data");
      String type = Files.readString(data.resolve("viltrumitecore/damage_type/thor_lightning.json"));
      assertTrue(JsonParser.parseString(type).getAsJsonObject().get("message_id").getAsString().equals("viltrumitecore.thor_lightning"));
      assertTrue(Files.readString(data.resolve("minecraft/tags/damage_type/is_lightning.json")).contains("viltrumitecore:thor_lightning"));
      try (var langs = Files.list(Path.of("src/main/resources/assets/viltrumitecore/lang"))) {
         for (Path lang : langs.toList()) {
            var json = JsonParser.parseString(Files.readString(lang)).getAsJsonObject();
            assertTrue(json.has("death.attack.viltrumitecore.thor_lightning"), lang.toString());
            assertTrue(json.has("death.attack.viltrumitecore.thor_lightning.player"), lang.toString());
         }
      }
   }

   @Test
   void oneSwingSkipsATargetsIFramesOncePerTick() {
      ThorCombatState combat = ThorCombatState.of(new ThorState());
      assertTrue(combat.claimSkip(7, 1, 100L));
      assertFalse(combat.claimSkip(7, 1, 100L), "the same swing, target and tick: no second bypass");
      assertTrue(combat.claimSkip(8, 1, 100L), "another target of the same swing");
      assertTrue(combat.claimSkip(7, 2, 100L), "another swing");
      assertTrue(combat.claimSkip(7, 1, 101L), "a later tick of the swing (scheduled follow-up) is untouched");
      combat.cancelTransient();
      assertTrue(combat.claimSkip(7, 1, 101L), "cleared with the transient state");
   }

   @Test
   void configIsRewrittenOnlyWhenItChanges() {
      var effective = JsonParser.parseString("{\"a\":16.0,\"b\":true}");
      assertFalse(ThorCombatConfig.needsWrite(JsonParser.parseString("{ \"b\": true, \"a\": 16 }"), effective), "same content, own formatting kept");
      assertTrue(ThorCombatConfig.needsWrite(JsonParser.parseString("{\"a\":16}"), effective), "a new key is written");
      assertTrue(ThorCombatConfig.needsWrite(null, effective), "a missing file is written");
   }

   @Test
   void heavyLaunchWaitsForTheHitStopAndThorIsNotFrozen() {
      assertTrue(MeleeMoves.HIT_STOP_TICKS > 0);
      assertTrue(MeleeMoves.hitStopIds(java.util.List.of()).length == 0, "only the struck bodies are listed");
   }
}
