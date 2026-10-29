package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ReleaseCause;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.combat.ElectricMoves;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirWorld;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Round-1 shared safety: the belt state (RC-2), forced storm release, persistent cooldowns on the wire, one target rule. */
class ThorRound1SafetyTest {
   private static final Path MAIN = Path.of("src/main/java/dev/baranhan/viltrumitecore");

   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
   }

   @Test
   void mmbOffersNoHammerMoveFromTheBelt() {
      // F-M13: HOLSTERED and not away = Mjölnir only on the belt (or no hammer yet): no throw "from the belt", no hammer blow.
      ThorContext belt = ThorContext.grounded(HammerState.HOLSTERED, ThorMode.NORMAL);
      assertNull(ThorControls.resolve(ThorInput.HAMMER, belt));
      assertTrue(ThorControls.onBelt(ThorInput.HAMMER, belt), "the server answers the press");
      ThorContext beltNearEnemy = new ThorContext(HammerState.HOLSTERED, ThorMode.NORMAL, true, false, false, false, true, false);
      assertNull(ThorControls.resolve(ThorInput.HAMMER, beltNearEnemy));
      assertFalse(ThorControls.onBelt(ThorInput.STRIKE, belt));

      ThorContext lying = new ThorContext(HammerState.HOLSTERED, ThorMode.NORMAL, true, false, false, false, false, false, true, false);
      assertSame(ThorMove.RECALL, ThorControls.resolve(ThorInput.HAMMER, lying), "a detached hammer is recalled");
      ThorContext window = new ThorContext(HammerState.HOLSTERED, ThorMode.NORMAL, true, false, false, false, false, true);
      assertSame(ThorMove.GRAB, ThorControls.resolve(ThorInput.HAMMER, window), "the grab needs no hammer");
      assertSame(ThorMove.HAMMER_THROW, ThorControls.resolve(ThorInput.HAMMER, ThorContext.grounded(HammerState.IN_HAND, ThorMode.NORMAL)));
   }

   @Test
   void theStoreNeverKeepsMjolnirOnTheBeltOutsideATechnicalSwap() {
      // RC-2 / F-M11: an End-return clone, an old save or a dev write cannot strand the hammer on the belt.
      assertSame(HammerState.IN_HAND, MjolnirWorld.inHandProjection(HammerState.HOLSTERED, false));
      assertSame(HammerState.HOLSTERED, MjolnirWorld.inHandProjection(HammerState.HOLSTERED, true), "a technical DRAW / HOLSTER keeps its swap");
      assertSame(HammerState.IN_HAND, MjolnirWorld.inHandProjection(HammerState.IN_HAND, true));
      assertSame(HammerState.IN_HAND, MjolnirWorld.inHandProjection(HammerState.THROWN, false));
   }

   @Test
   void onlyThePlayersReleaseOfAFreeThorBurstsTheStorm() {
      // B-M1: death, logout, a grab by another hero or a hero change never detonate the storm.
      assertTrue(ElectricMoves.bursts(ReleaseCause.PLAYER, true));
      assertFalse(ElectricMoves.bursts(ReleaseCause.PLAYER, false), "dead / anchored / offline / in a scene: quiet cancel");
      assertFalse(ElectricMoves.bursts(ReleaseCause.FORCED, true));
      assertFalse(ElectricMoves.bursts(ReleaseCause.FORCED, false));
   }

   @Test
   void cooldownsRideTheSnapshotAndGreyTheirSlot() {
      // B-M3 / F-M7: the HUD sees the ultimate recharging.
      ThorMoves.register("B", ThorMove.WRATH, new ThorMoveDefinition(24, 8, 20, null, 1), ThorMoveHandler.NONE);
      int[] cooldowns = new int[HeroPublicSnapshot.COOLDOWN_COUNT];
      cooldowns[ThorCooldowns.WRATH_INDEX] = 120;
      cooldowns[ThorCooldowns.STORM_INDEX] = 40;
      HeroPublicSnapshot snapshot = ThorHero.composeSnapshot(new ThorState(), cooldowns);
      assertArrayEquals(cooldowns, snapshot.cooldowns());
      assertTrue(ThorAbilities.slotGrey(snapshot, ThorInput.ULTIMATE));
      assertFalse(ThorAbilities.slotGrey(ThorHero.composeSnapshot(new ThorState()), ThorInput.ULTIMATE), "ready: lit");
      assertEquals(ThorAbilities.WRATH, ThorAbilities.slotIds()[ThorCooldowns.WRATH_INDEX], "the wire index is the panel slot");
      assertFalse(ThorCooldowns.recharging(cooldowns, ThorInput.POWER), "R has no cooldown");
      assertFalse(ThorCooldowns.recharging(null, ThorInput.ULTIMATE));
      assertEquals(-1, ThorCooldowns.index(ThorInput.STANCE), "the storm has no slot");
   }

   @Test
   void sessionsShareOneTargetRule() throws IOException {
      // F-M12 / B-M6 / A-M7: the resolver, B's strikes and A's flights ask HeroTargets.canHit (no allies, own pets, own mount).
      assertTrue(read("hero/thor/ThorTargets.java").contains("HeroTargets.canHit(player, entity)"));
      assertTrue(read("hero/thor/combat/CombatHits.java").contains("HeroTargets.canHit(player, entity)"));
      assertTrue(read("hero/thor/hammer/MjolnirEntity.java").contains("!HeroTargets.canHit(owner, living)"));
      String rule = read("hero/HeroTargets.java");
      for (String clause : new String[]{"attacker.isAlliedTo(target) || target.isAlliedTo(attacker)", "OwnableEntity", "getOwnerUUID",
         "isPassengerOfSameVehicle", "canHarmPlayer", "PartEntity", "ArmorStand", "isCreative"}) {
         assertTrue(rule.contains(clause), clause);
      }

      assertFalse(read("hero/ironman/combat/IronManTargets.java").contains("HeroTargets"), "Iron Man unchanged");
   }

   @Test
   void sessionsStartAndStopMovesOnlyThroughTheBridge() throws IOException {
      // F-B1 / F-B2: no hand-copied lifecycle and no direct timeline interrupt outside the foundation flow.
      for (String file : new String[]{"hero/thor/scene/ThorScenes.java", "hero/thor/scene/ThorSceneCommands.java", "hero/thor/combat/ElectricMoves.java",
         "hero/thor/combat/ThorCombatCommands.java", "hero/thor/hammer/MjolnirModule.java"}) {
         String source = read(file);
         assertFalse(source.contains("timeline.interrupt("), file);
         assertFalse(source.contains("ThorActions.start("), file);
         assertFalse(source.contains(".onStart(new ThorMoveContext"), file);
         assertFalse(source.contains(".onStop(new ThorMoveContext"), file);
      }

      // INT-12 / F-m11: the dev grab window names its target; the dev hammer fixture no longer writes the projection.
      String dev = read("hero/thor/dev/ThorDevCommands.java");
      assertTrue(dev.contains("openGrabWindow(state, ticks, target.getId())"));
      assertFalse(dev.contains("state.hammer =") || dev.contains("project("));
   }

   private static String read(String relative) throws IOException {
      return Files.readString(MAIN.resolve(relative));
   }
}
