package dev.baranhan.viltrumitecore.hero.ghostrider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics.Input;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCyclePhysics.State;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Regressions for the review of PR #132 (Ghost Rider). Pure rules and the real physics are tested directly; the hero and
 * the bike entity are pinned to them by source checks, since they need a running server.
 */
class GhostRiderReviewRegressionTest {
   private static final Path SRC = Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/ghostrider");

   private static String src(String file) throws IOException {
      return Files.readString(SRC.resolve(file));
   }

   private static void ground(State s, Input in) {
      s.onGround = true;
      HellCyclePhysics.step(s, in);
      if (s.velY < 0.0) {
         s.velY = 0.0;
      }
   }

   // ---- P2: PARK is a brake, never reverse

   @Test
   void aParkedBikeFromRestNeverRollsBack() {
      State s = new State();
      double travelled = 0.0;
      for (int t = 0; t < 40; t++) {
         ground(s, Input.PARK);
         travelled += s.forward();
      }
      assertEquals(0.0, s.forward(), 1.0E-9, "the review measured -0.18 b/t with the old PARK");
      assertEquals(0.0, travelled, 1.0E-9, "the review measured 5.264 blocks backwards");
   }

   @Test
   void parkingStopsARollingBikeFromEitherDirectionAndOnASlope() {
      for (double v : new double[]{0.9, -0.15}) {
         State s = new State();
         s.velZ = v;
         for (int t = 0; t < 120; t++) {
            ground(s, Input.PARK);
            assertTrue(Math.signum(s.forward()) == 0.0 || Math.signum(s.forward()) == Math.signum(v), "never crosses zero");
         }
         assertEquals(0.0, s.forward(), 1.0E-9);
      }
      State hill = new State();
      hill.pitch = 26.57;
      for (int t = 0; t < 60; t++) {
         ground(hill, Input.PARK);
      }
      assertEquals(0.0, hill.speed(), 1.0E-9, "no roll on a slope either");
      assertTrue(Input.PARK.park());
      assertFalse(new Input(-1.0, 0.0, false, false, false).park(), "a rider's brake is still a brake-then-reverse");
   }

   // ---- P2: burnout progress on the observer's own clock

   @Test
   void aLateObserverSeesTheBikeBurnAway() throws IOException {
      // server bike 900 ticks old starts burning; an observer tracking for 50 ticks gets the state at its tick 50
      int start = HellCycleRules.clientBurnStart(true, -1, 50);
      assertEquals(50, start);
      assertEquals(start, HellCycleRules.clientBurnStart(true, start, 55), "kept while burning");
      assertEquals(-1, HellCycleRules.clientBurnStart(false, start, 60));
      assertEquals(0.0F, HellCycleRules.burnProgress(50, start, 0.0F));
      assertEquals(0.5F, HellCycleRules.burnProgress(50 + HellCycleRules.DEMATERIALIZE_TICKS / 2, start, 0.0F), 1.0E-4F);
      assertEquals(1.0F, HellCycleRules.burnProgress(50 + HellCycleRules.DEMATERIALIZE_TICKS, start, 0.0F));
      // the old mix of clocks: client tick 50 minus server tick 900 stayed clamped at 0 until the bike vanished
      assertEquals(0.0F, HellCycleRules.burnProgress(50 + HellCycleRules.DEMATERIALIZE_TICKS, 900, 0.0F));
      String e = src("bike/HellCycleEntity.java");
      assertTrue(e.contains("int start = this.level().isClientSide ? this.clientBurnStart : this.entityData.get(DATA_BURN);"));
      assertTrue(e.contains("this.clientBurnStart = HellCycleRules.clientBurnStart(this.entityData.get(DATA_BURN) >= 0, this.clientBurnStart, this.tickCount);"));
   }

   // ---- P2: a stun stops running attacks and the chain

   @Test
   void aStunInterruptsMovesAndTheChain() throws IOException {
      assertTrue(GhostRiderCombat.stunInterrupts(true, GhostRiderMove.LASH_1, false));
      assertTrue(GhostRiderCombat.stunInterrupts(true, null, true), "a chained body is let go");
      assertFalse(GhostRiderCombat.stunInterrupts(false, GhostRiderMove.LASH_1, true));
      assertFalse(GhostRiderCombat.stunInterrupts(true, GhostRiderMove.TRANSFORM, false), "the form change runs on");
      assertFalse(GhostRiderCombat.stunInterrupts(true, null, false));
      String c = src("GhostRiderCombat.java");
      int tick = c.indexOf("static void tick(ServerPlayer player, GhostRiderState state) {");
      int stun = c.indexOf("stunInterrupts(HeroDamage.isAnchored(player), state.move, state.chained())", tick);
      int chain = c.indexOf("chainTick(player, state);", tick);
      int moves = c.indexOf("switch (move) {", tick);
      assertTrue(tick > 0 && stun > tick && stun < chain && stun < moves, "checked before the chain and any contact");
      assertTrue(c.indexOf("releaseChain(player, state, ReleaseReason.INTERRUPTED, false);", stun) > stun);
   }

   // ---- P2: custom landing damage only in Rider form

   @Test
   void johnnyLandsLikeAnyone() throws IOException {
      String h = src("GhostRiderHero.java");
      int landed = h.indexOf("public void onLanded(ServerPlayer player, float fallDistance) {");
      int guard = h.indexOf("if (!rider(player)) {", landed);
      int hurt = h.indexOf("player.hurt(player.damageSources().fall(), damage);", landed);
      assertTrue(landed > 0 && guard > landed && guard < hurt);
   }

   // ---- P2: the chained body moves along its path, never through a wall

   @Test
   void aChainedBodyStopsAtTheWall() throws IOException {
      // a wall filling x in [3, 4): the body (a point here) cannot be inside it
      java.util.function.Predicate<Vec3> free = p -> p.x < 3.0 || p.x >= 4.0;
      Vec3 from = new Vec3(0.0, 64.0, 0.0);
      Vec3 beyond = new Vec3(6.0, 64.0, 0.0);
      assertTrue(free.test(beyond), "the destination itself is clear: the old endpoint check let it through");
      Vec3 at = GhostRiderCombat.sweep(from, beyond, 0.0, free);
      assertTrue(at.x < 3.0 && at.x > 2.5, "stopped before the wall: " + at);
      assertEquals(beyond, GhostRiderCombat.sweep(from, beyond, 0.0, p -> true), "an open path reaches the point");
      // a kerb: blocked at the feet, free 0.6 higher - it steps up instead of stopping
      java.util.function.Predicate<Vec3> kerb = p -> p.x < 2.0 || p.y >= 64.5;
      Vec3 up = GhostRiderCombat.sweep(from, new Vec3(4.0, 64.0, 0.0), 0.6, kerb);
      assertEquals(4.0, up.x, 1.0E-9);
      assertEquals(64.6, up.y, 1.0E-9);
      String c = src("GhostRiderCombat.java");
      int place = c.indexOf("static void place(LivingEntity t, Vec3 at) {");
      assertTrue(c.indexOf("Vec3 safe = sweep(before, at, 0.6,", place) > place);
   }
}
