package dev.baranhan.viltrumitecore.scp.scp096;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regressions for the review of PR #129. The rules are tested as pure functions; the entity is wired to them, which the
 * source checks pin (the entity needs a running server and cannot be built in a unit test).
 */
class Scp096ReviewRegressionTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore/scp/scp096");

   private static String src(String file) throws IOException {
      return Files.readString(SRC.resolve(file));
   }

   // ---- P2: ordinary damage must not kill

   @Test
   void ordinaryHitsCanNeverTakeTheLastHealthPoint() {
      float hp = Scp096Rules.MAX_HEALTH;
      // a player spamming capped hits far faster than it regenerates (4 HP/s): 1000 hits, no regen at all
      for (int i = 0; i < 1000; i++) {
         hp -= Scp096Rules.nonLethal(hp, Scp096Rules.cappedHit(1.0E9F));
      }
      assertEquals(Scp096Rules.HEALTH_FLOOR, hp, 1.0E-4F);
      assertEquals(0.0F, Scp096Rules.nonLethal(1.0F, 12.0F));
      assertEquals(5.0F, Scp096Rules.nonLethal(100.0F, 5.0F));
      assertEquals(0.0F, Scp096Rules.nonLethal(0.5F, 12.0F), "below the floor (a /kill in progress) ordinary damage adds nothing");
      assertEquals(0.0F, Scp096Rules.nonLethal(100.0F, Float.NaN));
   }

   @Test
   void theEntityClampsInTheLastDamageStepAndLetsKillAndVoidThrough() throws IOException {
      String e = src("Scp096Entity.java");
      assertTrue(e.contains("protected void actuallyHurt(DamageSource source, float amount)"));
      assertTrue(e.contains("amount = Scp096Rules.nonLethal(this.getHealth(), amount);"));
      assertTrue(e.contains("if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {\n         amount = Scp096Rules.nonLethal"));
   }

   // ---- P2: the sight streak must be consecutive and die with the face

   @Test
   void onlyBackToBackChecksBuildTheStreak() {
      int iv = Scp096Rules.PLAYER_SCAN_INTERVAL;
      assertEquals(1, Scp096Rules.sightStreak(0, Long.MIN_VALUE / 2, 100, iv));
      assertEquals(2, Scp096Rules.sightStreak(1, 100, 102, iv));
      // one check before the face closed, one at the next peek seconds later: a fresh streak, not a trigger
      assertEquals(1, Scp096Rules.sightStreak(1, 100, 260, iv));
      assertTrue(Scp096Rules.sightStreak(1, 100, 260, iv) < Scp096Rules.SEEN_CHECKS);
   }

   @Test
   void aHiddenFaceClearsPendingSightings() throws IOException {
      String e = src("Scp096Entity.java");
      assertTrue(e.contains("if (peaceful || !this.faceOpen()) {"));
      assertTrue(e.contains("this.seen.clear();"));
      assertTrue(e.contains("Scp096Rules.sightStreak("));
   }

   // ---- P2: citizen cap per admission

   @Test
   void theCitizenBudgetIsCheckedOnEveryAdmission() throws IOException {
      // simulate a scan that starts with 7 admissions and finds 4 more visible citizens
      int admitted = 7;
      int visible = 4;
      for (int i = 0; i < visible; i++) {
         if (!Scp096Rules.citizenMayJoin(true, admitted)) {
            break;
         }
         admitted++;
      }
      assertEquals(Scp096Rules.CITIZEN_TARGETS_PER_RAMPAGE, admitted);
      String e = src("Scp096Entity.java");
      assertTrue(e.contains("if (!player && !Scp096Rules.citizenMayJoin(s.enraged(), this.rampageCitizens)) {"),
         "onFaceSeen refuses a citizen past the cap");
      assertTrue(e.contains("this.onFaceSeen(level, c, false);\n            if (!Scp096Rules.citizenMayJoin("),
         "the scan stops as soon as the cap is reached");
   }

   // ---- P2: a saved hunt never attaches to a respawned player

   @Test
   void aHuntBelongsToOneLifeOfThePlayer() throws IOException {
      assertTrue(Scp096Rules.sameLife(3, 3));
      assertFalse(Scp096Rules.sameLife(3, 4), "died elsewhere while the creature was unloaded");
      assertTrue(Scp096Rules.sameLife(-1, 9), "hunts saved before the life was recorded keep working");
      String e = src("Scp096Entity.java");
      assertTrue(e.contains("e.putInt(\"Life\", t.life);"));
      assertTrue(e.contains("t.life = e.contains(\"Life\") ? e.getInt(\"Life\") : -1;"));
      assertTrue(e.contains("&& !diedSince(p, t) ? p : null"));
   }

   // ---- P2: unload retires the runtime id on the client

   @Test
   void unloadingTellsHuntedPlayersToDropTheOldId() throws IOException {
      String e = src("Scp096Entity.java");
      assertTrue(e.contains("!reason.shouldDestroy() && this.level() instanceof ServerLevel sl) {\n            this.retireClientIds(sl);"));
      assertTrue(e.contains("new Scp096S2CPacket(Scp096S2CPacket.RELEASED, this.getId()), p);\n         }\n      }\n      this.notified.clear();"),
         "notified is cleared so the reloaded creature's resync announces its new id");
   }

   // ---- P2: repath cooldown

   @Test
   void everyPathSearchWaitsTheCooldown() {
      int min = Scp096Rules.REPATH_MIN_TICKS;
      for (int age = 0; age < min; age++) {
         assertFalse(Scp096Rules.shouldRepath(false, false, age, true), "failed search retried at age " + age);
         assertFalse(Scp096Rules.shouldRepath(true, true, age, true), "completed path retried at age " + age);
      }
      assertTrue(Scp096Rules.shouldRepath(false, false, min, false));
      assertTrue(Scp096Rules.shouldRepath(true, true, min, false));
      assertTrue(Scp096Rules.shouldRepath(true, false, min, true));
      assertFalse(Scp096Rules.shouldRepath(true, false, min, false));
      assertTrue(Scp096Rules.shouldRepath(true, false, Scp096Rules.REPATH_MAX_TICKS + 1, false));
      // the first search of a chase is immediate (reset puts the age past the cooldown)
      assertTrue(Scp096Rules.shouldRepath(false, false, 999, false));
   }

   @Test
   void theChaseUsesTheCooldownRule() throws IOException {
      String c = src("Scp096Chase.java");
      assertTrue(c.contains("Scp096Rules.shouldRepath("));
      assertTrue(c.contains("this.pathAge = 999;"));
   }
}
