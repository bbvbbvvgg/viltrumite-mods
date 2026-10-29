package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves;
import dev.baranhan.viltrumitecore.hero.thor.combat.CombatHits;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatModule;
import dev.baranhan.viltrumitecore.hero.thor.dev.ThorDevCommands;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirModule;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirWorld;
import dev.baranhan.viltrumitecore.hero.thor.scene.FatalityRules;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The seams between sessions A (hammer), B (combat) and C (scenes) on the
 * foundation: shared flags on one wire, one MMB order, the item-in-hand rule,
 * the full-flight rule, the fatality gate and the one-owner registrations.
 */
class ThorIntegrationSeamsTest {
   @AfterEach
   void reset() {
      ThorMoves.resetForTests();
      ThorEvents.resetForTests();
      ThorBridge.resetForTests();
      ThorDevCommands.resetForTests();
      ThorCombatModule.resetForTests();
   }

   private static ThorContext context(HammerState hammer, boolean handFree, boolean flying, boolean enemyNear, boolean grabWindow, boolean detached,
      boolean fistChainOpen) {
      return new ThorContext(hammer, ThorMode.NORMAL, handFree, false, flying, flying, enemyNear, grabWindow, detached, fistChainOpen);
   }

   // --- one wire for A + C flags ---

   @Test
   void everySessionFlagSurvivesTheWireAtOnce() {
      ThorState state = new ThorState();
      ThorStateFixtures.hammer(state, HammerState.HOLSTERED);
      ThorStateFixtures.detached(state, true);
      state.setMode(ThorMode.ELECTRIC);
      ThorBridge.openGrabWindow(state, 5);
      assertTrue(ThorBridge.acquireCamera(state, ThorScenes.OWNER));
      assertTrue(ThorBridge.lockScene(state, ThorScenes.OWNER));

      HeroPublicSnapshot sent = ThorHero.composeSnapshot(state);
      HeroPublicSnapshot got = HeroPublicSnapshot.decode(sent.encode());
      int flags = got.heroFlags();
      assertEquals(sent.heroFlags(), flags);
      assertSame(HammerState.HOLSTERED, ThorFlags.hammer(flags));
      assertSame(ThorMode.ELECTRIC, ThorFlags.mode(flags));
      assertTrue(ThorFlags.grabWindow(flags));
      assertTrue(ThorFlags.cameraLease(flags));
      assertTrue(ThorFlags.hammerDetached(flags), "session A bit");
      assertTrue(ThorFlags.scene(flags), "session C bit");
   }

   @Test
   void theFlagBitsNeverOverlap() {
      for (HammerState hammer : HammerState.values()) {
         int base = ThorFlags.compose(hammer, ThorMode.NORMAL, false, false, false, false);
         assertSame(hammer, ThorFlags.hammer(base));
         int[] singles = {
            ThorFlags.compose(hammer, ThorMode.ELECTRIC, false, false, false, false),
            ThorFlags.compose(hammer, ThorMode.NORMAL, true, false, false, false),
            ThorFlags.compose(hammer, ThorMode.NORMAL, false, true, false, false),
            ThorFlags.compose(hammer, ThorMode.NORMAL, false, false, true, false),
            ThorFlags.compose(hammer, ThorMode.NORMAL, false, false, false, true)};
         int seen = 0;
         for (int single : singles) {
            int bit = single & ~base;
            assertEquals(1, Integer.bitCount(bit), "one flag, one bit: " + Integer.toBinaryString(single));
            assertEquals(0, seen & bit, "bits overlap");
            assertEquals(0, bit & ThorFlags.HAMMER_MASK, "a flag inside the hammer field");
            seen |= bit;
         }

         int all = ThorFlags.compose(hammer, ThorMode.ELECTRIC, true, true, true, true);
         assertEquals(base | seen, all);
         assertSame(hammer, ThorFlags.hammer(all));
      }
   }

   // --- MMB: grab window > recall > melee > throw, across A / B / C states ---

   @Test
   void mmbOrderHoldsAcrossTheSessions() {
      for (HammerState hammer : HammerState.values()) {
         for (boolean detached : new boolean[]{false, true}) {
            for (boolean enemy : new boolean[]{false, true}) {
               for (boolean chain : new boolean[]{false, true}) {
                  for (boolean free : new boolean[]{false, true}) {
                     ThorContext window = context(hammer, free, false, enemy, true, detached, chain);
                     assertSame(ThorMove.GRAB, ThorControls.resolve(ThorInput.HAMMER, window), "C's window wins: " + window);
                     ThorContext open = context(hammer, free, false, enemy, false, detached, chain);
                     // Belt only (HOLSTERED, attached): no hammer move (F-M13, OPEN design §10).
                     ThorMove expected = hammer.away() || detached ? ThorMove.RECALL
                        : hammer == HammerState.HOLSTERED ? null : enemy ? ThorMove.HAMMER_STRIKE : ThorMove.HAMMER_THROW;
                     assertSame(expected, ThorControls.resolve(ThorInput.HAMMER, open), open.toString());
                  }
               }
            }
         }
      }
   }

   @Test
   void withAAndBRegisteredMmbIsClaimedOnTheGroundAndOnlyForTheRecallInFullFlight() {
      MjolnirModule.registerMoves();
      ThorCombatModule.install();
      ThorContext throwing = context(HammerState.IN_HAND, true, false, false, false, false, false);
      ThorContext melee = context(HammerState.IN_HAND, true, false, true, false, false, false);
      ThorContext thrown = context(HammerState.THROWN, true, false, true, false, false, false);
      assertSame(HeroAction.TOOL_CYCLE, ThorControls.claimedMouse(MouseButton.MIDDLE, throwing));
      assertSame(HeroAction.TOOL_CYCLE, ThorControls.claimedMouse(MouseButton.MIDDLE, melee));
      assertSame(HeroAction.TOOL_CYCLE, ThorControls.claimedMouse(MouseButton.MIDDLE, thrown));
      assertNull(ThorControls.claimedMouse(MouseButton.MIDDLE, context(HammerState.IN_HAND, true, true, true, false, false, false)),
         "no melee / throw in full flight");
      assertNull(ThorControls.claimedMouse(MouseButton.PRIMARY, context(HammerState.IN_HAND, true, true, false, false, false, false)));
      assertSame(HeroAction.TOOL_CYCLE, ThorControls.claimedMouse(MouseButton.MIDDLE, context(HammerState.RETURNING, true, true, false, false, false,
         false)), "the recall is not an attack");
   }

   // --- A's belt rule meets B's combos ---

   @Test
   void anItemInTheHandGivesFistsAndNoHammerBlow() {
      ThorContext busy = context(HammerState.IN_HAND, false, false, false, false, false, false);
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STRIKE, busy));
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STANCE, busy));
      assertSame(MjolnirPlace.BELT, MjolnirPlace.of(HammerState.IN_HAND, false), "the item keeps Mjölnir on the belt");
      assertSame(ThorCombat.presence(HammerState.IN_HAND, false), ThorCombat.presence(HammerState.HOLSTERED, true), "fists either way");
      ThorContext free = context(HammerState.IN_HAND, true, false, false, false, false, false);
      assertSame(ThorMove.HAMMER_COMBO, ThorControls.resolve(ThorInput.STRIKE, free));
      assertSame(MjolnirPlace.HAND, MjolnirPlace.of(HammerState.IN_HAND, true));
   }

   @Test
   void aCatchMidFistChainFinishesTheChainFirst() {
      ThorContext caught = context(HammerState.IN_HAND, true, false, true, false, false, true);
      assertSame(ThorMove.FIST_COMBO, ThorControls.resolve(ThorInput.STRIKE, caught), "#166");
      assertSame(ThorMove.HAMMER_STRIKE, ThorControls.resolve(ThorInput.HAMMER, caught), "MMB is not part of the open chain");
      assertFalse(CombatHits.hammerBlow(ThorMove.FIST_COMBO, true), "a fist blow right after the catch is no hammer blow");
      ThorContext closed = context(HammerState.IN_HAND, true, false, true, false, false, false);
      assertSame(ThorMove.HAMMER_COMBO, ThorControls.resolve(ThorInput.STRIKE, closed));
   }

   // --- B's lethal hit meets C's fatality ---

   @Test
   void onlyALethalBlowWithMjolnirInTheHandStartsAFatality() {
      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE}) {
         for (boolean inHand : new boolean[]{false, true}) {
            boolean blow = CombatHits.hammerBlow(move, inHand);
            FatalityRules.Verdict verdict = FatalityRules.decide(ThorEventType.LETHAL_HIT, blow, true, true, false, false);
            assertSame(blow ? FatalityRules.Verdict.START : FatalityRules.Verdict.HAMMER_NOT_IN_HAND, verdict, move + " inHand=" + inHand);
            assertEquals(inHand && move != ThorMove.FIST_COMBO, blow);
         }
      }

      assertSame(FatalityRules.Verdict.THOR_BUSY, FatalityRules.decide(ThorEventType.LETHAL_HIT, true, true, true, false, true),
         "a locked scene (A's transformation, C's grab) blocks it");
      assertSame(FatalityRules.Verdict.NOT_LETHAL, FatalityRules.decide(ThorEventType.MELEE_HIT, true, true, true, false, false));
   }

   @Test
   void aBlowThatStartsASceneStopsItsOwnSideEffects() throws IOException {
      String melee = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/combat/MeleeMoves.java"));
      int loop = melee.indexOf("CombatHits.hit(player, target, damage, move, attackId)");
      int guard = melee.indexOf("ThorBridge.sceneLocked(state)", loop);
      int push = melee.indexOf("CombatHits.push(", loop);
      assertTrue(loop > 0 && guard > loop && guard < push, "landStrike leaves the loop before the push once the hit started a scene");
      String bridge = Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/thor/bridge/ThorBridge.java"));
      assertTrue(bridge.contains("view.inHand() && ThorHands.free(player)"), "one hammer-in-hand rule for B and C");
   }

   // --- registrations: one owner per move ---

   @Test
   void sessionsAAndBShareTheRegistryAndLeaveCsScenesFree() {
      MjolnirModule.registerMoves();
      ThorCombatModule.install();
      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_THROW, ThorMove.RECALL, ThorMove.HAMMER_PULL}) {
         assertEquals(MjolnirWorld.OWNER, ThorMoves.owner(move), move.name());
      }

      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE, ThorMove.ELECTRIC_MODE,
         ThorMove.ELECTRIC_COMBO, ThorMove.ELECTRIC_WAVE, ThorMove.ELECTRIC_STORM, ThorMove.WRATH}) {
         assertEquals(ThorCombatModule.OWNER, ThorMoves.owner(move), move.name());
      }

      for (ThorMove move : new ThorMove[]{ThorScenes.GRAB, ThorScenes.FATALITY, ThorScenes.TELEPORT}) {
         assertNull(ThorMoves.owner(move), move + " stays free for session C");
      }
   }

   // --- the merged language files ---

   @Test
   void mergedLanguageFilesHaveNoDuplicateKeysAndTheSameKeys() throws IOException {
      Path dir = Path.of("src/main/resources/assets/viltrumitecore/lang");
      Pattern key = Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*:");
      Map<String, Set<String>> keysByFile = new HashMap<>();
      List<Path> files = new ArrayList<>();
      try (Stream<Path> stream = Files.list(dir)) {
         stream.filter(path -> path.toString().endsWith(".json")).forEach(files::add);
      }

      assertFalse(files.isEmpty());
      for (Path file : files) {
         Set<String> keys = new HashSet<>();
         Matcher matcher = key.matcher(Files.readString(file));
         while (matcher.find()) {
            assertTrue(keys.add(matcher.group(1)), file.getFileName() + " repeats " + matcher.group(1));
         }

         keysByFile.put(file.getFileName().toString(), keys);
      }

      Set<String> english = keysByFile.get("en_us.json");
      for (Map.Entry<String, Set<String>> entry : keysByFile.entrySet()) {
         assertEquals(english, entry.getValue(), entry.getKey() + " has the same keys as en_us.json");
      }
   }
}
