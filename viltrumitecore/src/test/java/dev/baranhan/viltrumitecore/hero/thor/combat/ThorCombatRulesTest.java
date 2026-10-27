package dev.baranhan.viltrumitecore.hero.thor.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ComboBook.Button;
import dev.baranhan.viltrumitecore.hero.thor.combat.ComboBook.Starter;
import dev.baranhan.viltrumitecore.hero.thor.combat.ElectricAction.Kind;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Session B pure rules: combo routes, electric recognizer, sky target policy, curves. */
class ThorCombatRulesTest {
   private static final Button L = Button.LMB;
   private static final Button R = Button.RMB;

   @Test
   void approvedComboCounts() {
      assertTrue(ComboBook.ARMED.size() >= 8 && ComboBook.ARMED.size() <= 10, "#38: 8–10 armed combos");
      assertTrue(ComboBook.UNARMED.size() >= 4 && ComboBook.UNARMED.size() <= 5, "#145: 4–5 unarmed combos");
      for (ComboBook.Route route : ComboBook.ARMED) {
         assertSame(ThorMove.HAMMER_COMBO, route.move());
         route.strikes().forEach(strike -> assertSame(ThorMove.HAMMER_COMBO, strike.move(), route.id()));
      }

      for (ComboBook.Route route : ComboBook.UNARMED) {
         assertSame(ThorMove.FIST_COMBO, route.move());
         route.strikes().forEach(strike -> assertSame(ThorMove.FIST_COMBO, strike.move(), route.id()));
      }
   }

   @Test
   void routesAreDistinctAndSharedPrefixesPlayTheSameStrikes() {
      for (List<ComboBook.Route> book : List.of(ComboBook.ARMED, ComboBook.UNARMED)) {
         Set<String> keys = new HashSet<>();
         for (ComboBook.Route a : book) {
            assertTrue(keys.add(a.starter() + a.buttons().toString()), "duplicate route " + a.id());
            assertTrue(a.length() <= ThorCombatTimings.COMBO_STEPS, a.id());
            for (ComboBook.Route b : book) {
               if (a.starter() != b.starter()) {
                  continue;
               }

               for (int n = 1; n <= Math.min(a.length(), b.length()); n++) {
                  if (a.buttons().subList(0, n).equals(b.buttons().subList(0, n))) {
                     assertSame(a.strikes().get(n - 1), b.strikes().get(n - 1), a.id() + " / " + b.id() + " step " + n);
                  }
               }
            }
         }
      }
   }

   @Test
   void everyRouteIsReachableAndEndsWhereItEnds() {
      for (List<ComboBook.Route> book : List.of(ComboBook.ARMED, ComboBook.UNARMED)) {
         for (ComboBook.Route route : book) {
            for (int n = 1; n <= route.length(); n++) {
               ComboBook.Match match = ComboBook.match(route.move(), route.starter(), route.buttons().subList(0, n));
               assertNotNull(match, route.id() + " " + n);
               assertSame(route.strikes().get(n - 1), match.strike(), route.id() + " " + n);
               if (n == route.length()) {
                  assertTrue(match.completed(), route.id() + " completes");
               }
            }
         }
      }
   }

   @Test
   void rhythmAndContextPickTheStrikes() {
      assertSame(ThorStrike.SPIN, ComboBook.match(ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L, L, L)).strike());
      assertTrue(ComboBook.match(ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L, L, L)).last());
      ComboBook.Match uppercut = ComboBook.match(ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L, R));
      assertSame(ThorStrike.UPPERCUT, uppercut.strike());
      assertTrue(uppercut.last() && uppercut.completed());
      ComboBook.Match open = ComboBook.match(ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, L));
      assertFalse(open.last(), "LL continues into LLL / LLR");
      assertNull(ComboBook.match(ThorMove.HAMMER_COMBO, Starter.ANY, List.of(L, R, L)), "no route: the press starts a new chain");
      assertSame(ThorStrike.LUNGE, ComboBook.match(ThorMove.HAMMER_COMBO, Starter.SPRINT, List.of(L)).strike());
      assertSame(ThorStrike.FALLING_SLAM, ComboBook.match(ThorMove.HAMMER_COMBO, Starter.AIRBORNE, List.of(L)).strike());
      assertSame(ThorStrike.LOW_SWEEP, ComboBook.match(ThorMove.HAMMER_COMBO, Starter.SNEAK, List.of(R)).strike());
      assertSame(ThorStrike.HEAVY_DOWN, ComboBook.match(ThorMove.HAMMER_COMBO, Starter.SPRINT, List.of(R)).strike(), "no sprint RMB route: ANY");
      assertSame(ThorStrike.JAB, ComboBook.match(ThorMove.FIST_COMBO, Starter.ANY, List.of(L)).strike());
      assertSame(ThorStrike.GROUND_POUND, ComboBook.match(ThorMove.FIST_COMBO, Starter.ANY, List.of(R, R, R)).strike());
      assertNull(ComboBook.match(ThorMove.ELECTRIC_COMBO, Starter.ANY, List.of(L)), "electric LMB is not a melee route");
      for (Starter starter : Starter.values()) {
         for (ThorMove move : List.of(ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO)) {
            assertNotNull(ComboBook.match(move, starter, List.of(L)), "a single LMB always strikes: " + move + " " + starter);
            assertNotNull(ComboBook.match(move, starter, List.of(R)), "a single RMB always strikes: " + move + " " + starter);
         }
      }
   }

   @Test
   void everyStrikeBelongsToARouteOrTheMmbBranch() {
      Set<ThorStrike> used = EnumSet.noneOf(ThorStrike.class);
      ComboBook.ARMED.forEach(route -> used.addAll(route.strikes()));
      ComboBook.UNARMED.forEach(route -> used.addAll(route.strikes()));
      for (ThorStrike strike : ThorStrike.values()) {
         assertTrue(used.contains(strike) || strike.move() == ThorMove.HAMMER_STRIKE, strike + " is never played");
      }
   }

   @Test
   void strikePosesRoundTrip() {
      Set<Integer> poses = new HashSet<>();
      for (ThorStrike strike : ThorStrike.values()) {
         assertTrue(strike.pose() > 0 && poses.add(strike.pose()));
         assertSame(strike, ThorStrike.byPose(strike.pose()));
         assertTrue(strike.reach() > 0 && strike.reach() <= 5.0, strike + ": melee reach");
         assertTrue(EnumSet.of(ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE).contains(strike.move()));
      }

      assertNull(ThorStrike.byPose(0));
      assertNull(ThorStrike.byPose(ThorStrike.values().length + 1));
   }

   private static ElectricAction act(Kind kind, MoveDirection direction, boolean airborne, long tick) {
      return ElectricAction.of(kind, direction, airborne, false, tick);
   }

   @Test
   void exactlySixElectricCombosOfTwoOrThreeActions() {
      assertEquals(6, ElectricCombo.values().length, "#103: exactly six");
      for (ElectricCombo combo : ElectricCombo.values()) {
         assertTrue(combo.length() >= 2 && combo.length() <= 3, combo + ": #51");
      }

      Set<Kind> used = EnumSet.noneOf(Kind.class);
      assertTrue(ElectricCombo.CONDUIT_SMASH.needsHammer() && ElectricCombo.STORM_LANCE.needsHammer() && ElectricCombo.JUDGMENT.needsHammer());
      assertFalse(ElectricCombo.RICOCHET.needsHammer() || ElectricCombo.THUNDER_RING.needsHammer() || ElectricCombo.RECALL_FAN.needsHammer(),
         "#16: basic lightning works without Mjölnir");
      for (List<ElectricAction> sequence : canonical().values()) {
         sequence.forEach(action -> used.add(action.kind()));
      }

      assertEquals(EnumSet.allOf(Kind.class), used, "LMB / RMB / hold / MMB strike / throw / recall all take part (#147)");
   }

   private static java.util.Map<ElectricCombo, List<ElectricAction>> canonical() {
      java.util.Map<ElectricCombo, List<ElectricAction>> map = new java.util.EnumMap<>(ElectricCombo.class);
      map.put(ElectricCombo.RICOCHET, List.of(act(Kind.LMB, MoveDirection.BACK, false, 0), act(Kind.LMB, MoveDirection.FORWARD, false, 10)));
      map.put(ElectricCombo.THUNDER_RING, List.of(act(Kind.RMB, MoveDirection.NONE, false, 0), act(Kind.RMB, MoveDirection.NONE, true, 10)));
      map.put(ElectricCombo.CONDUIT_SMASH, List.of(act(Kind.LMB, MoveDirection.NONE, false, 0), act(Kind.MMB_STRIKE, MoveDirection.NONE, false, 10)));
      map.put(ElectricCombo.STORM_LANCE, List.of(act(Kind.HOLD_RMB, MoveDirection.NONE, false, 0),
         ElectricAction.of(Kind.MMB_THROW, MoveDirection.NONE, false, true, 10)));
      map.put(ElectricCombo.RECALL_FAN, List.of(act(Kind.MMB_RECALL, MoveDirection.NONE, false, 0), act(Kind.LMB, MoveDirection.NONE, false, 10)));
      map.put(ElectricCombo.JUDGMENT, List.of(act(Kind.RMB, MoveDirection.LEFT, false, 0), act(Kind.LMB, MoveDirection.FORWARD, false, 10),
         act(Kind.MMB_STRIKE, MoveDirection.NONE, false, 20)));
      return map;
   }

   @Test
   void everyComboIsRecognisedAsItself() {
      for (var entry : canonical().entrySet()) {
         ElectricRecognizer recognizer = new ElectricRecognizer();
         ElectricRecognizer.Result result = null;
         for (ElectricAction action : entry.getValue()) {
            result = recognizer.push(action, 30, true);
         }

         assertNotNull(result, entry.getKey().name());
         assertSame(entry.getKey(), result.combo());
         assertTrue(result.fires());
         assertTrue(recognizer.history().isEmpty(), "a recognised combo consumes its actions");
      }
   }

   @Test
   void longestComboWinsAndGapsBreakSequences() {
      ElectricRecognizer recognizer = new ElectricRecognizer();
      assertNull(recognizer.push(act(Kind.RMB, MoveDirection.RIGHT, false, 0), 30, true));
      assertNull(recognizer.push(act(Kind.LMB, MoveDirection.FORWARD, false, 10), 30, true));
      assertSame(ElectricCombo.JUDGMENT, recognizer.push(act(Kind.MMB_STRIKE, MoveDirection.NONE, false, 20), 30, true).combo(),
         "JUDGMENT over its CONDUIT_SMASH suffix");

      recognizer.clear();
      assertNull(recognizer.push(act(Kind.LMB, MoveDirection.BACK, false, 0), 30, true));
      assertNull(recognizer.push(act(Kind.LMB, MoveDirection.FORWARD, false, 31), 30, true), "too slow: the sequence restarts");
      assertEquals(1, recognizer.history().size());
   }

   @Test
   void strongCombosNeedMjolnirInTheHandAndRecallNothing() {
      ElectricRecognizer recognizer = new ElectricRecognizer();
      recognizer.push(act(Kind.LMB, MoveDirection.NONE, false, 0), 30, false);
      ElectricRecognizer.Result smash = recognizer.push(act(Kind.MMB_STRIKE, MoveDirection.NONE, false, 5), 30, false);
      assertSame(ElectricCombo.CONDUIT_SMASH, smash.combo());
      assertFalse(smash.fires(), "#148: without Mjölnir it does not fire");

      recognizer.push(act(Kind.HOLD_RMB, MoveDirection.NONE, false, 10), 30, true);
      ElectricRecognizer.Result lance = recognizer.push(ElectricAction.of(Kind.MMB_THROW, MoveDirection.NONE, false, true, 15), 30, false);
      assertTrue(lance.fires(), "the hammer was in the hand for the throw itself");

      recognizer.push(act(Kind.MMB_RECALL, MoveDirection.NONE, false, 20), 30, false);
      assertTrue(recognizer.push(act(Kind.LMB, MoveDirection.NONE, false, 25), 30, false).fires(), "#128: the fan comes from the hand");
   }

   @Test
   void skyTargetPolicies() {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      UUID c = UUID.randomUUID();
      assertSame(b, SkyTargetPolicy.LAST_PRIMARY_HIT.select(List.of(a, b), List.of(a, b, c)));
      assertSame(c, SkyTargetPolicy.LAST_ANY_HIT.select(List.of(a, b), List.of(a, b, c)));
      assertNull(SkyTargetPolicy.AIM_POINT.select(List.of(a), List.of(a)));
      assertSame(c, SkyTargetPolicy.LAST_PRIMARY_HIT.select(List.of(), List.of(c)));
      assertNull(SkyTargetPolicy.LAST_PRIMARY_HIT.select(List.of(), List.of()));
      assertSame(SkyTargetPolicy.LAST_PRIMARY_HIT, new ThorCombatConfig().skyTargetPolicy, "PLACEHOLDER default");
   }

   @Test
   void directionsFollowTheView() {
      assertSame(MoveDirection.NONE, MoveDirection.classify(0.01, 0.0, 0.0F));
      assertSame(MoveDirection.FORWARD, MoveDirection.classify(0.0, 0.2, 0.0F), "yaw 0 looks to +Z");
      assertSame(MoveDirection.BACK, MoveDirection.classify(0.0, -0.2, 0.0F));
      assertSame(MoveDirection.RIGHT, MoveDirection.classify(-0.2, 0.0, 0.0F), "the right of +Z is -X");
      assertSame(MoveDirection.LEFT, MoveDirection.classify(0.2, 0.0, 0.0F));
      assertSame(MoveDirection.FORWARD, MoveDirection.classify(-0.2, 0.0, 90.0F), "yaw 90 looks to -X");
   }

   @Test
   void stormGrowsAndBurstsWithItsSize() {
      assertEquals(3.0, StormCurve.radius(0, 100, 3.0, 16.0), 1.0E-9);
      assertEquals(16.0, StormCurve.radius(100, 100, 3.0, 16.0), 1.0E-9);
      assertEquals(16.0, StormCurve.radius(500, 100, 3.0, 16.0), 1.0E-9);
      double previous = 0.0;
      for (int t = 0; t <= 100; t += 5) {
         double r = StormCurve.radius(t, 100, 3.0, 16.0);
         assertTrue(r >= previous);
         previous = r;
      }

      assertTrue(StormCurve.burst(12.0F, 16.0, 16.0) > StormCurve.burst(12.0F, 3.0, 16.0));
   }

   @Test
   void ultimateConcentratesInOnePoint() {
      assertEquals(40.0F, WrathMove.damageAt(0.0, 2.5, 7.0, 40.0F), 1.0E-6);
      assertEquals(40.0F, WrathMove.damageAt(2.5, 2.5, 7.0, 40.0F), 1.0E-6);
      assertTrue(WrathMove.damageAt(5.0, 2.5, 7.0, 40.0F) < 40.0F);
      assertEquals(0.0F, WrathMove.damageAt(7.5, 2.5, 7.0, 40.0F), 1.0E-6);
      assertFalse(new ThorCombatConfig().wrathRequiresHammer, "OPEN: no hammer rule decided for the ultimate");
   }

   @Test
   void hitGeometryAndLethalCheck() {
      Vec3 look = new Vec3(0.0, 0.0, 1.0);
      assertTrue(CombatHits.withinArc(look, new Vec3(0.5, 0.0, 3.0), 45.0));
      assertFalse(CombatHits.withinArc(look, new Vec3(0.0, 0.0, -3.0), 90.0), "nothing behind the view (no auto turn)");
      assertTrue(CombatHits.withinArc(look, new Vec3(0.0, 0.0, -3.0), 180.0), "spins hit all around");
      assertTrue(CombatHits.lethal(10.0F, 10.0F));
      assertFalse(CombatHits.lethal(9.9F, 10.0F));
      assertFalse(CombatHits.lethal(0.0F, 0.0F));
   }

   @Test
   void cooldownsAndCraters() {
      // B-M3: cooldowns are PersistentCooldowns ticks (online time), not a per-body counter; the player-less store is for tests.
      ThorCombatState combat = new ThorCombatState();
      assertTrue(combat.ready(null, ThorCombatState.Cooldown.WRATH));
      combat.startCooldown(null, ThorCombatState.Cooldown.WRATH, 10);
      assertFalse(combat.ready(null, ThorCombatState.Cooldown.WRATH));
      combat.ticks += 10;
      assertFalse(combat.ready(null, ThorCombatState.Cooldown.WRATH), "the body's own counter no longer drives cooldowns");
      for (int i = 0; i < 10; i++) {
         combat.tickLocalCooldowns();
      }

      assertTrue(combat.ready(null, ThorCombatState.Cooldown.WRATH));
      assertFalse(ElectricFx.crater(net.minecraft.core.BlockPos.ZERO, 3, 0.5).isEmpty());
      assertEquals(24, ElectricFx.column(net.minecraft.core.BlockPos.ZERO, 24).size());
      assertTrue(ElectricFx.column(net.minecraft.core.BlockPos.ZERO, 24).stream().allMatch(p -> p.getY() > 0), "only above the target");
   }

   @Test
   void meleeChainsNeverReportElectricSuccess() {
      UUID thor = UUID.randomUUID();
      var end = MeleeMoves.meleeComboEnd(thor, null, ThorMove.HAMMER_COMBO, 3, true);
      assertSame(dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType.COMBO_END, end.type());
      assertFalse(end.success(), "contract: success = true only for a completed electric combo");
      assertSame(ThorMove.HAMMER_COMBO, end.move());
   }

   @Test
   void aFistBlowIsNeverAHammerBlow() {
      assertTrue(CombatHits.hammerBlow(ThorMove.HAMMER_COMBO, true));
      assertTrue(CombatHits.hammerBlow(ThorMove.HAMMER_STRIKE, true));
      assertFalse(CombatHits.hammerBlow(ThorMove.HAMMER_STRIKE, false));
      assertFalse(CombatHits.hammerBlow(ThorMove.FIST_COMBO, true), "#166: a fist chain finishing after the catch is not a hammer blow");
   }

   @Test
   void theStormChargesInStages() {
      assertEquals(1, StormCurve.stage(0, 100, 3));
      assertEquals(1, StormCurve.stage(33, 100, 3));
      assertEquals(2, StormCurve.stage(34, 100, 3));
      assertEquals(3, StormCurve.stage(67, 100, 3));
      assertEquals(3, StormCurve.stage(100, 100, 3));
      assertEquals(3, StormCurve.stage(900, 100, 3));
      assertEquals(1, StormCurve.stage(50, 100, 1));
      assertTrue(StormCurve.burstAtStage(12.0F, 3, 3) > StormCurve.burstAtStage(12.0F, 2, 3));
      assertTrue(StormCurve.burstAtStage(12.0F, 2, 3) > StormCurve.burstAtStage(12.0F, 1, 3));
      assertEquals(12.0F, StormCurve.burstAtStage(12.0F, 3, 3), 1.0E-6);
   }

   @Test
   void theThunderRingStartsOnTheGround() {
      ElectricRecognizer recognizer = new ElectricRecognizer();
      assertNull(recognizer.push(act(Kind.RMB, MoveDirection.NONE, true, 0), 30, false));
      assertNull(recognizer.push(act(Kind.RMB, MoveDirection.NONE, true, 10), 30, false), "two airborne waves are no combo");
   }
}
