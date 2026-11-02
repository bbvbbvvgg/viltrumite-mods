package dev.baranhan.viltrumitecore.worldevent.godzilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Regressions for the review of PR #131 (Godzilla). The rules are pure functions; the destruction, the session and the
 * entity are pinned to them by source checks, since they need a running server.
 */
class GodzillaReviewRegressionTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   private static String src(String file) throws IOException {
      return Files.readString(SRC.resolve(file));
   }

   // ---- P1: a never-reported cell must be due, not blocked by an overflow

   @Test
   void aCellNeverReportedIsDueAtOnce() {
      long now = 123_456L;
      // the old check: now - Long.MIN_VALUE overflows to a negative number, so the cell was never due
      assertTrue(now - Long.MIN_VALUE < GodzillaRules.AFTERMATH_INTERVAL, "documents the overflow the fix avoids");
      assertTrue(GodzillaRules.reportDue(now, Long.MIN_VALUE, GodzillaRules.AFTERMATH_INTERVAL));
      assertTrue(GodzillaRules.reportDue(0L, Long.MIN_VALUE, GodzillaRules.INFRA_INTERVAL), "game time 0 too");
      assertFalse(GodzillaRules.reportDue(now, now - 10, GodzillaRules.AFTERMATH_INTERVAL));
      assertTrue(GodzillaRules.reportDue(now, now - GodzillaRules.AFTERMATH_INTERVAL, GodzillaRules.AFTERMATH_INTERVAL));
      assertTrue(GodzillaRules.reportDue(10L, 5_000L, GodzillaRules.INFRA_INTERVAL), "time going backwards (/time set) is due");
   }

   @Test
   void bothThrottlesUseTheOverflowSafeCheck() throws IOException {
      String d = src("worldevent/godzilla/GodzillaDestruction.java");
      assertTrue(d.contains("GodzillaRules.reportDue(this.tick, this.reported.get(cell), GodzillaRules.AFTERMATH_INTERVAL)"));
      assertTrue(d.contains("GodzillaRules.reportDue(this.tick, this.infra.get(cell), GodzillaRules.INFRA_INTERVAL)"));
      assertFalse(d.contains("this.tick - this.reported.get(cell)"));
      assertFalse(d.contains("this.tick - this.infra.get(cell)"));
   }

   // ---- P2: each offset shatter position is validated

   @Test
   void everyShatteredPaneIsCheckedForLoadAndArena() throws IOException {
      String d = src("worldevent/godzilla/GodzillaDestruction.java");
      int q = d.indexOf("BlockPos q = this.cursor.offset(");
      int guard = d.indexOf("if (!level.isLoaded(q) || arena != null && !arena.inArena(q.getX(), q.getY(), q.getZ()))", q);
      int read = d.indexOf("level.getBlockState(q)", q);
      assertTrue(q > 0 && guard > q && guard < read, "the load and arena checks come before the block is read");
   }

   // ---- P2: mobGriefing off stops queued destruction

   @Test
   void queuedSmashesStopWhenMobGriefingIsOff() throws IOException {
      assertFalse(GodzillaRules.mayDrainSmashes(false, false));
      assertFalse(GodzillaRules.mayDrainSmashes(true, true));
      assertTrue(GodzillaRules.mayDrainSmashes(true, false));
      String d = src("worldevent/godzilla/GodzillaDestruction.java");
      int tick = d.indexOf("void tick(ServerLevel level, boolean rage, boolean retreat)");
      int gate = d.indexOf("if (!GodzillaRules.mayDrainSmashes(this.griefing, this.exhausted())) {", tick);
      int drain = d.indexOf("this.work(level, s, this.left());", tick);
      assertTrue(tick > 0 && gate > tick && gate < drain, "the queue is dropped before it is drained");
      int work = d.indexOf("private void work(ServerLevel level, Smash s, int limit) {");
      assertTrue(d.indexOf("if (!this.griefing) {", work) - work < 120, "work() itself refuses to carve with griefing off");
   }

   // ---- P2: late viewers join a move at its real progress

   @Test
   void aLateViewerStartsMidMove() {
      // tracking from the start: the client's own tick
      assertEquals(500, GodzillaRules.clientMoveStart(500, 10_000L, 10_000L));
      assertEquals(500, GodzillaRules.clientMoveStart(500, 10_002L, 10_000L), "time-sync slack");
      // joined 120 ticks into the move: the clip starts 120 ticks in the past
      assertEquals(380, GodzillaRules.clientMoveStart(500, 10_120L, 10_000L));
      // no start known yet (a fresh entity): its own tick
      assertEquals(500, GodzillaRules.clientMoveStart(500, 10_120L, 0L));
   }

   @Test
   void theEntitySyncsItsMoveStart() throws IOException {
      String e = src("entity/godzilla/GodzillaEntity.java");
      assertTrue(e.contains("EntityDataAccessor<Long> MOVE_START"));
      assertTrue(e.contains("this.entityData.define(MOVE_START, 0L);"));
      int set = e.indexOf("this.entityData.set(MOVE_START, this.level().getGameTime());");
      assertTrue(set > 0 && set < e.indexOf("this.entityData.set(MOVE_SEQ, this.entityData.get(MOVE_SEQ) + 1);"));
      assertTrue(e.contains("this.clientMoveStart = GodzillaRules.clientMoveStart(this.tickCount, this.level().getGameTime(), this.entityData.get(MOVE_START));"));
   }

   // ---- P2: boss-bar viewers who changed dimension are removed

   @Test
   void theBarIsReconciledAgainstItsViewers() throws IOException {
      assertFalse(GodzillaRules.keepsBar(false, false), "changed dimension");
      assertFalse(GodzillaRules.keepsBar(true, true), "logged out");
      assertTrue(GodzillaRules.keepsBar(true, false));
      String s = src("worldevent/godzilla/GodzillaSession.java");
      int bar = s.indexOf("void updateBar(ServerLevel level, GodzillaEntity e)");
      assertTrue(s.indexOf("for (ServerPlayer p : new java.util.ArrayList<>(this.bar.getPlayers()))", bar) > bar);
      assertTrue(s.indexOf("GodzillaRules.keepsBar(p.level() == level, p.isRemoved())", bar) > bar);
   }
}
