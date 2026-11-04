package dev.baranhan.viltrumitecore.scp.scp106;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.CleanDamageSink;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Review of PR #148/#149: fresh pocket hunters live, corrosion obeys mobGriefing, clean hero damage keeps its rules. */
class Scp106ReviewTest {
   private static String src() throws Exception {
      return Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/scp/scp106/Scp106Entity.java"));
   }

   @Test
   void aFreshPocketHunterIsNeverDiscardedAsStale() {
      assertFalse(Scp106Entity.staleOnLoad(false, true, Long.MIN_VALUE, 1_000L), "spawnPocketHunter: not from disk");
      assertFalse(Scp106Entity.staleOnLoad(false, false, 0L, 100_000L), "a fresh natural one either");
      assertTrue(Scp106Entity.staleOnLoad(true, true, 990L, 1_000L), "a saved pocket hunter coming back");
      assertTrue(Scp106Entity.staleOnLoad(true, false, 0L, 1_201L), "a saved one away for more than a minute");
      assertFalse(Scp106Entity.staleOnLoad(true, false, 0L, 1_200L), "a short reload keeps it");
      assertFalse(Scp106Entity.staleOnLoad(true, false, Long.MIN_VALUE, 50_000L), "never seen: kept");
      String s = src();
      int read = s.indexOf("public void readAdditionalSaveData(");
      assertTrue(s.indexOf("this.loadedFromDisk = true;", read) > read, "only deserialization marks it");
   }

   @Test
   void everyCorrosionPathHonoursMobGriefing() throws Exception {
      String s = src();
      int corrode = s.indexOf("void corrode(ServerLevel level, BlockPos pos, Direction face) {");
      int guard = s.indexOf("ForgeEventFactory.getMobGriefingEvent(level, this)", corrode);
      int place = s.indexOf("level.setBlock(", corrode);
      assertTrue(corrode > 0 && guard > corrode && guard < place, "the guard is before the only placement");
      assertEquals(s.indexOf("level.setBlock("), s.lastIndexOf("level.setBlock("), "corrode() is the only block placement");
   }

   @Test
   void cleanHeroDamageKeepsTheFloorAndTheRetreat() throws Exception {
      assertTrue(CleanDamageSink.class.isAssignableFrom(Scp106Entity.class));
      assertEquals(39.0F, Scp106Entity.cleanDealt(false, 39.0F, 400.0F), 1.0E-6F);
      assertEquals(9.0F, Scp106Entity.cleanDealt(false, 50.0F, 10.0F), 1.0E-6F, "stops one point above death");
      assertEquals(0.0F, Scp106Entity.cleanDealt(false, 50.0F, 1.0F), 1.0E-6F, "at one point nothing more");
      assertEquals(50.0F, Scp106Entity.cleanDealt(true, 50.0F, 10.0F), 1.0E-6F, "/kill and the void still kill");
      String s = src();
      int clean = s.indexOf("public void cleanDamage(DamageSource source, float amount) {");
      assertTrue(clean > 0);
      String body = s.substring(clean, s.indexOf("static float cleanDealt", clean));
      assertTrue(body.contains("this.isInvulnerableTo(source)"), "submerged/phase/retreat immunity");
      assertTrue(body.contains("shrugsOff(source)"), "same immune damage types as hurt()");
      assertTrue(body.contains("this.recordHit(amount)"), "retreat accounting");
      int hurt = s.indexOf("public boolean hurt(DamageSource source, float amount) {");
      assertTrue(s.indexOf("this.recordHit(amount);", hurt) > hurt, "hurt() shares the accounting");
   }
}
