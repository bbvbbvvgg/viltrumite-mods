package dev.baranhan.viltrumitecore.city.citizen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regressions for the review of PR #145 (falling citizens). Server-bound wiring is pinned by source checks. */
class CitizenPerilReviewTest {
   private static final Path CITIZEN = Path.of("src/main/java/dev/baranhan/viltrumitecore/city/citizen");
   private static final Path CLIENT = Path.of("src/main/java/dev/baranhan/viltrumitecore/client/city");

   private static String src(Path dir, String file) throws IOException {
      return Files.readString(dir.resolve(file));
   }

   // ---- P2: peril statistics must not outlive their server level

   @Test
   void statisticsAreForgottenWhenTheLevelUnloads() throws IOException {
      String peril = src(CITIZEN, "CitizenPeril.java");
      assertTrue(peril.contains("STATS.remove(dimension);") && peril.contains("STATS.clear();"));
      String events = src(CITIZEN, "CitizenPerilEvents.java");
      assertTrue(events.contains("public static void onLevelUnload(LevelEvent.Unload event)"));
      assertTrue(events.contains("CitizenPeril.forget(level.dimension());"));
      assertTrue(events.contains("public static void onServerStopped(ServerStoppedEvent event)"));
      assertTrue(events.contains("CitizenPeril.forgetAll();"));
   }

   // ---- P2: a catch stops only the rescued citizen's scream

   @Test
   void aCatchStopsOnlyThatCitizensScream() throws IOException {
      String peril = src(CITIZEN, "CitizenPeril.java");
      assertFalse(peril.contains("ClientboundStopSoundPacket"), "an event-wide stop packet silences every falling citizen of the sex");
      assertTrue(peril.contains("level.broadcastEntityEvent(c, CitizenEntity.EVENT_STOP_FALL_VOICE);"));
      String entity = src(CITIZEN, "CitizenEntity.java");
      assertTrue(entity.contains("if (id == EVENT_STOP_FALL_VOICE) {\n         CitizenPeril.clientStopFallVoice.accept(this.getId());"));
      String client = src(CLIENT, "CitizenVoiceOcclusion.java");
      assertTrue(client.contains("Occluded v = FALLING.remove(entityId);"), "the client stops the voice by its citizen id");
      assertTrue(client.contains("return this.killed || this.inner instanceof TickableSoundInstance t && t.isStopped();"));
      assertTrue(src(CLIENT, "power/CityPowerClientSetup.java").contains("CitizenPeril.clientStopFallVoice = "));
   }

   // ---- P2: a transfer (captor, containment) invalidates the hanging anchor

   @Test
   void aHeldOrMovedBodyNeverSnapsBackToTheOldLedge() throws IOException {
      assertTrue(PerilRules.ANCHOR_SLACK > 0.0 && PerilRules.ANCHOR_SLACK <= 1.5);
      String entity = src(CITIZEN, "CitizenEntity.java");
      int held = entity.indexOf("if (CitizenCaptivity.isHeld(this)) {");
      assertTrue(held > 0 && entity.indexOf("CitizenPeril.onHeld(this);", held) > held);
      String peril = src(CITIZEN, "CitizenPeril.java");
      int hang = peril.indexOf("private static void hang(CitizenEntity c, CityManager city, long now) {");
      int anchor = peril.indexOf("PerilRules.ANCHOR_SLACK", hang);
      int snap = peril.indexOf("c.setPos(c.hangX, c.hangY, c.hangZ);", hang);
      assertTrue(hang > 0 && anchor > hang && snap > anchor, "the anchor is checked before the body is put back on it");
      int onHeld = peril.indexOf("static void onHeld(CitizenEntity c) {");
      assertTrue(peril.indexOf("c.hangLedge = null;", onHeld) > onHeld);
   }
}
