package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.HeroInteractions;
import dev.baranhan.viltrumitecore.hero.combat.InteractionKind;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.thor.bridge.HammerView;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.combat.CombatHits;
import dev.baranhan.viltrumitecore.hero.thor.combat.MeleeMoves;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorCombatState;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Round 3: the read model, event types and interaction layer the sessions share (foundation m6 / m7 / m8, B m4). */
class ThorRound3ContractsTest {
   private static final UUID THOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");

   @AfterEach
   void reset() {
      HeroInteractions.resetForTests();
   }

   @Test
   void eventTypesOnlyAppend() {
      String[] wire = {"MELEE_HIT", "LETHAL_HIT", "COMBO_END", "THROW", "RECALL", "CATCH", "HAMMER_STATE_CHANGED", "HAMMER_OWNER_CHANGED",
         "STRIKE_START", "STRIKE_END", "SCENE_START", "SCENE_END"};
      assertEquals(wire.length, ThorEventType.values().length);
      for (int i = 0; i < wire.length; i++) {
         assertEquals(wire[i], ThorEventType.values()[i].name(), "ordinal " + i);
      }
   }

   @Test
   void pureEventsCarryAnUnknownWhereAndNoDetail() {
      ThorEvent event = new ThorEvent(1, ThorEventType.THROW, THOR, null, null, 0, false, 0.0F, true);
      assertSame(ThorEvent.Where.UNKNOWN, event.where());
      assertFalse(event.where().known());
      assertEquals(0, event.detail());
      ThorEvent detailed = event.withDetail(3);
      assertNotSame(event, detailed);
      assertEquals(0, event.detail(), "immutable");
      assertEquals(3, detailed.detail());
      assertEquals(event.eventId(), detailed.eventId(), "same dedupe key");
   }

   @Test
   void comboEndCarriesNoFakeDamage() {
      ThorEvent end = MeleeMoves.meleeComboEnd(THOR, null, ThorMove.HAMMER_COMBO, 4, true);
      assertEquals(0.0F, end.amount());
      assertEquals(0, end.detail());
      assertFalse(end.success(), "a melee chain never reports success");
   }

   @Test
   void strikeEndWithoutAPlayerOnlyClears() {
      ThorCombatState combat = ThorCombatState.of(new ThorState());
      combat.openStrike = 9;
      combat.openStrikeMove = ThorMove.HAMMER_COMBO;
      CombatHits.strikeEnd(null, combat);
      assertEquals(0, combat.openStrike);
      assertEquals(null, combat.openStrikeMove);
   }

   @Test
   void thorStanceIsActiveOnlyInTheDamageWindowOfAStrike() {
      for (ThorMove move : new ThorMove[]{ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE, ThorMove.ELECTRIC_COMBO, ThorMove.WRATH}) {
         assertEquals(CombatStance.ACTIVE, ThorCombat.stance(move, MovePhase.MAIN), move.name());
         assertEquals(CombatStance.NONE, ThorCombat.stance(move, MovePhase.INTRO), move.name());
         assertEquals(CombatStance.NONE, ThorCombat.stance(move, MovePhase.FINISH), move.name());
      }

      assertEquals(CombatStance.NONE, ThorCombat.stance(ThorMove.RECALL, MovePhase.MAIN));
      assertEquals(CombatStance.NONE, ThorCombat.stance(null, MovePhase.MAIN));
      assertSame(ThorCombat.MJOLNIR, ThorCombat.presence(HammerState.IN_HAND, true, null, null), "idle presence is the shared constant");
      assertEquals(ThorCombat.FISTS.weaponId(), ThorCombat.presence(HammerState.IN_HAND, false, ThorMove.FIST_COMBO, MovePhase.MAIN).weaponId());
   }

   @Test
   void theSharedRulesResolveThorAgainstAGuardingWeapon() {
      CombatPresence sword = new CombatPresence("test:sword", Set.of(CombatTags.WEAPON_SWORD), CombatStance.GUARD, 0);
      CombatPresence swinging = ThorCombat.presence(HammerState.IN_HAND, true, ThorMove.HAMMER_COMBO, MovePhase.MAIN);
      assertEquals(InteractionKind.BLOCK, HeroInteractions.resolve(swinging, sword).orElseThrow().kind());
      assertEquals(InteractionKind.BLOCK, HeroInteractions.resolve(sword, swinging).orElseThrow().kind(), "symmetric");
      CombatPresence carried = ThorCombat.presence(HammerState.IN_HAND, true, null, null);
      assertTrue(HeroInteractions.resolve(carried, sword).isEmpty(), "a carried hammer meets nothing");
      assertSame(HeroInteractions.rules(), HeroInteractions.rules(), "one instance");
   }

   @Test
   void hammerViewSeparatesThrownFromAnyFlight() {
      HammerView thrown = new HammerView(null, THOR, HammerState.THROWN, false, null, null);
      HammerView returning = new HammerView(null, THOR, HammerState.RETURNING, false, null, null);
      HammerView belt = new HammerView(null, THOR, HammerState.HOLSTERED, false, null, null);
      assertTrue(thrown.flying() && thrown.airborne());
      assertFalse(returning.flying(), "Y pulls only to a thrown hammer");
      assertTrue(returning.airborne());
      assertTrue(belt.onBelt());
      HammerView lying = new HammerView(null, THOR, HammerState.HOLSTERED, false, null, null, true, false);
      assertFalse(lying.onBelt(), "a detached hammer is not on the belt");
   }
}
