package dev.baranhan.viltrumitecore.hero.captain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contextual interaction reid_shatters_vibranium, Captain's side: the broken guard is timed, synced and animated. */
class CaptainShieldShatterTest {
   static final Path ANIMS = Path.of("src/main/resources/assets/viltrumitecore/animations/captain");

   @Test
   void brokenGuardTicksDownAndIsNotKeptByRespawn() {
      CaptainState state = new CaptainState();
      state.shieldBroken = InteractionTuning.SHATTER_TICKS;
      for (int i = 0; i < InteractionTuning.SHATTER_TICKS - 1; i++) {
         state.tickTimers();
      }
      assertEquals(1, state.shieldBroken);
      state.tickTimers();
      assertEquals(0, state.shieldBroken);
      state.tickTimers();
      assertEquals(0, state.shieldBroken, "never negative");
      state.shieldBroken = 50;
      assertEquals(0, state.respawnCopy().shieldBroken);
   }

   @Test
   void snapshotCarriesTheBrokenFlagAndTheReelMove() {
      CaptainState state = new CaptainState();
      assertFalse(CaptainFlags.has(CaptainHero.compose(state).heroFlags(), CaptainFlags.SHIELD_BROKEN));
      state.shieldBroken = 10;
      state.start(CaptainMove.SHIELD_SHATTERED);
      var snapshot = CaptainHero.compose(state);
      assertTrue(CaptainFlags.has(snapshot.heroFlags(), CaptainFlags.SHIELD_BROKEN));
      assertEquals(CaptainMove.SHIELD_SHATTERED.ordinal(), snapshot.actionId());
      assertEquals(CaptainMove.SHIELD_SHATTERED, CaptainMove.byId(snapshot.actionId()));
   }

   @Test
   void reelMoveIsAppendedAndHasBothClips() throws Exception {
      CaptainMove[] all = CaptainMove.values();
      assertEquals(CaptainMove.SHIELD_SHATTERED, all[all.length - 1], "moves are append only");
      assertEquals(CaptainMove.TEAM_BRACE.ordinal() + 1, CaptainMove.SHIELD_SHATTERED.ordinal());
      assertTrue(CaptainMove.SHIELD_SHATTERED.roots());
      assertFalse(CaptainMove.SHIELD_SHATTERED.usesShield());
      for (String file : new String[] {"captain_skills.animation.json", "captain_fp.animation.json"}) {
         JsonObject anims = JsonParser.parseString(Files.readString(ANIMS.resolve(file))).getAsJsonObject().getAsJsonObject("animations");
         assertTrue(anims.has(CaptainMove.SHIELD_SHATTERED.clip()), file);
      }
   }

   @Test
   void lineIsRegistered() {
      assertTrue(CaptainLines.LINES.byId("captain:shield_shattered").isPresent());
      assertTrue(CaptainLines.LINES.byId("captain:storm_shield").isPresent());
   }
}
