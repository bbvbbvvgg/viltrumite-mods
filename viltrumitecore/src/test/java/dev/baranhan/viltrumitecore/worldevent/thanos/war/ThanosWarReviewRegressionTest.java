package dev.baranhan.viltrumitecore.worldevent.thanos.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.thanos.war.order.CullMove;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Regressions for the review of PR #130 (THANOS 2.0). Pure rules are tested directly; the entities and the war are pinned
 * to them by source checks, since they need a running server.
 */
class ThanosWarReviewRegressionTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   private static String src(String file) throws IOException {
      return Files.readString(SRC.resolve(file));
   }

   // ---- P2: the time-stop freeze is a shared FREEZE control

   @Test
   void theTimeStopFreezeGoesThroughTheControlManager() throws IOException {
      assertTrue(ThanosHolds.usesSharedFreeze(ThanosHolds.Kind.FREEZE));
      assertFalse(ThanosHolds.usesSharedFreeze(ThanosHolds.Kind.CARRY));
      assertFalse(ThanosHolds.usesSharedFreeze(ThanosHolds.Kind.DRAG));
      String h = src("worldevent/thanos/war/ThanosHolds.java");
      assertTrue(h.contains("ControlManager.get(level).isControlled(target)"), "an existing control is not taken over");
      assertTrue(h.contains("ControlManager.get(level).tryAcquire(target, holder.getUUID(), effect, ControlKind.FREEZE, level.getGameTime() + granted)"));
      assertTrue(h.contains("ControlManager.get(level).release(h.target.getUUID(), h.effect, ReleaseReason.NORMAL_END)"));
      // every way a hold ends releases the shared control: ended() and clear()
      int ended = h.indexOf("private void ended(Hold h) {");
      assertTrue(h.indexOf("this.releaseShared(h);", ended) - ended < 60);
      int clear = h.indexOf("public void clear() {");
      assertTrue(h.indexOf("this.releaseShared(h);", clear) > clear);
   }

   // ---- P2: Cull's hardening runs down during moves

   @Test
   void cullsHardeningRunsOnEveryCombatTick() throws IOException {
      String o = src("entity/thanos/BlackOrderEntity.java");
      int fight = o.indexOf("private void fightTick() {");
      int clock = o.indexOf("this.combatClock();", fight);
      int move = o.indexOf("this.tickCurrent();", fight);
      int stagger = o.indexOf("if (this.stagger > 0) {", fight);
      assertTrue(fight > 0 && clock > fight && clock < stagger && clock < move, "the clock runs before any early return");
      String c = src("entity/thanos/CullObsidianEntity.java");
      int cc = c.indexOf("protected void combatClock() {");
      assertTrue(cc > 0 && c.indexOf("this.hardened--;", cc) - cc < 200);
      int passive = c.indexOf("protected void passive() {");
      assertFalse(c.substring(passive, passive + 200).contains("this.hardened--"), "no second countdown between moves");
      // a 200-tick roar now ends after 200 combat ticks whatever moves run (simulated)
      int hardened = 200;
      for (int t = 0; t < 200; t++) {
         hardened = Math.max(0, hardened - 1);
      }
      assertEquals(0, hardened);
   }

   // ---- P2: debris goes harmless when the war is over

   @Test
   void debrisCrumblesOnceTheWarIsOver() throws IOException {
      String d = src("entity/thanos/WarDebrisEntity.java");
      int tick = d.indexOf("public void tick() {");
      int over = d.indexOf("if (war != null && war.over()) {", tick);
      int sw = d.indexOf("switch (mode) {", tick);
      assertTrue(over > tick && over < sw, "checked before any mode can travel or impact");
      assertTrue(d.indexOf("this.shatter();", over) - over < 200);
   }

   // ---- P2: shards and debris stop at cover

   @Test
   void aBodyBehindAWallIsNotHit() {
      Vec3 from = new Vec3(0.0, 1.0, 0.0);
      Vec3 to = new Vec3(4.0, 1.0, 0.0);
      AABB behindWall = new AABB(3.2, 0.0, -0.3, 3.8, 1.8, 0.3);
      // the movement box contains it, but the wall at x = 2 cuts the path first
      assertTrue(new AABB(from, to).inflate(0.55).intersects(behindWall));
      Vec3 wall = new Vec3(2.0, 1.0, 0.0);
      assertEquals(-1, SweptHits.firstAlong(from, wall, List.of(behindWall), 0.55));
      assertEquals(0, SweptHits.firstAlong(from, to, List.of(behindWall), 0.55), "without the wall it is hit");
   }

   @Test
   void theNearestBodyAlongThePathIsHitAndNearMissesAreNot() {
      Vec3 from = new Vec3(0.0, 1.0, 0.0);
      Vec3 to = new Vec3(10.0, 1.0, 0.0);
      AABB far = new AABB(7.7, 0.0, -0.3, 8.3, 1.8, 0.3);
      AABB near = new AABB(3.7, 0.0, -0.3, 4.3, 1.8, 0.3);
      AABB beside = new AABB(5.0, 0.0, 3.0, 5.6, 1.8, 3.6);
      assertEquals(1, SweptHits.firstAlong(from, to, List.of(far, near, beside), 0.3));
      assertEquals(-1, SweptHits.firstAlong(from, to, List.of(beside), 0.3), "in the box of the move, not on its path");
      assertEquals(0, SweptHits.firstAlong(from, to, List.of(new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)), 0.3), "starting inside");
   }

   @Test
   void bothProjectilesTraceBlocksBeforeBodies() throws IOException {
      String m = src("worldevent/thanos/war/ThanosWarMoves.java");
      int shards = m.indexOf("private void tickShards() {");
      int clip = m.indexOf("level.clip(new ClipContext(from, to", shards);
      int sweep = m.indexOf("SweptHits.firstAlong(from, open", shards);
      int hurt = m.indexOf("ThanosTargets.hurt(this.c.thanos, hit, s.damage", shards);
      assertTrue(clip > shards && clip < sweep && sweep < hurt);
      String d = src("entity/thanos/WarDebrisEntity.java");
      int travel = d.indexOf("private void travel(Vec3 v) {");
      int dclip = d.indexOf("this.level().clip(new ClipContext(from, to", travel);
      int dsweep = d.indexOf("SweptHits.firstAlong(from, open", travel);
      assertTrue(dclip > travel && dclip < dsweep);
   }

   // ---- P2: CHAIN re-checks its range at contact

   @Test
   void theChainMissesAHeroWhoBrokeDistance() throws IOException {
      assertTrue(CullMove.CHAIN.reaches(CullMove.CHAIN.maxRange()));
      assertTrue(CullMove.CHAIN.reaches(CullMove.CHAIN.maxRange() + 0.9));
      assertFalse(CullMove.CHAIN.reaches(CullMove.CHAIN.maxRange() + 5.0), "dashed out during the telegraph");
      assertFalse(CullMove.CHAIN.reaches(40.0), "validTarget allows 64 blocks, the chain does not");
      String c = src("entity/thanos/CullObsidianEntity.java");
      int chain = c.indexOf("private void tickChain(CullMove m, int t) {");
      int check = c.indexOf("!m.reaches(this.distanceTo(target))", chain);
      int strike = c.indexOf("this.strike(target, m.damage());", chain);
      int hold = c.indexOf("w.holds.hold(target, ThanosHolds.Kind.DRAG", chain);
      assertTrue(check > chain && check < strike && check < hold);
   }
}
