package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.InteractionKind;
import dev.baranhan.viltrumitecore.hero.combat.InteractionRules;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero;
import dev.baranhan.viltrumitecore.integration.ShippedLang;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Thor identity, registration, chassis, panel, wire snapshot, combat presence and texts. */
class ThorHeroTest {
   private static final ThorHero HERO = ThorHero.INSTANCE;

   @Test
   void thorIsAppendedAfterEveryExistingHero() {
      assertSame(HeroId.THOR, HeroId.values()[6]);
      assertSame(HeroId.REINHARD, HeroId.values()[5]);
      assertSame(HeroId.THOR, HeroId.fromKey("thor"));
      assertSame(HeroId.THOR, HeroId.legacySet(HeroId.THOR, true), "the legacy boolean never converts Thor");
      assertSame(HeroId.THOR, HeroId.legacySet(HeroId.THOR, false));
   }

   @Test
   void registeredUnderItsId() {
      HeroRegistry.registerDefaults();
      assertSame(HERO, HeroRegistry.get(HeroId.THOR));
      assertSame(HeroId.THOR, HERO.id());
   }

   @Test
   void vanillaChassisInTheFoundation() {
      assertFalse(HERO.allowsFlight(null), "no flight in the foundation");
      assertFalse(HERO.allowsLegacyAbilities(null));
      assertFalse(HERO.allowsAbilityPages(null));
      assertTrue(HERO.hasAbilityPanel(null));
      assertFalse(HERO.claimsBlockAttack(null), "LMB on a block always mines");
   }

   @Test
   void newHeroActionsKeepEveryExistingOrdinal() {
      assertEquals(HeroAction.DIAGONAL_WORLD_CUT.ordinal() + 1, HeroAction.CONTEXT_POWER.ordinal());
      assertEquals(HeroAction.CONTEXT_POWER.ordinal() + 1, HeroAction.CONTEXT_MOBILITY.ordinal());
      assertEquals(HeroAction.CONTEXT_MOBILITY.ordinal() + 1, HeroAction.ULTIMATE.ordinal());
      assertEquals(HeroAction.ULTIMATE.ordinal() + 1, HeroAction.CONTEXT_TELEPORT.ordinal());
      // Heroes merged after Thor (DIO, Sinclair, Jinwoo) append their actions after CONTEXT_TELEPORT.
      assertEquals(HeroAction.DIAGONAL_WORLD_CUT.ordinal() + 4, HeroAction.CONTEXT_TELEPORT.ordinal());
   }

   @Test
   void fivePanelSlotsInKeyOrderRYUltimateVB() {
      assertArrayEquals(new String[]{ThorAbilities.THUNDER, ThorAbilities.ONSLAUGHT, ThorAbilities.WRATH, ThorAbilities.TELEPORT, ThorAbilities.CRUSH},
         HERO.heroInputSlots());
      assertSame(HeroAction.SIGNATURE, HERO.heroActionFor(ThorAbilities.CRUSH));
      assertSame(ThorInput.CRUSH, ThorInput.of(HeroAction.SIGNATURE));
      assertSame(HeroAction.CONTEXT_TELEPORT, HERO.heroActionFor(ThorAbilities.TELEPORT));
      assertSame(HeroAction.CONTEXT_POWER, HERO.heroActionFor(ThorAbilities.THUNDER));
      assertSame(HeroAction.CONTEXT_MOBILITY, HERO.heroActionFor(ThorAbilities.ONSLAUGHT));
      assertSame(HeroAction.ULTIMATE, HERO.heroActionFor(ThorAbilities.WRATH));
      assertNull(HERO.heroActionFor("reinhard:world_cut"));
      assertTrue(HERO.ownsAbility(ThorAbilities.WRATH));
      assertFalse(HERO.ownsAbility("thor:mjolnir"));
      assertFalse(HERO.ownsAbility(null));
      assertEquals("textures/gui/ability/thor/thunder.png", HERO.abilityIcon(ThorAbilities.THUNDER).getPath());
      assertNull(HERO.abilityIcon("homelander:laser"));

      String[] loadout = HERO.defaultLoadout();
      assertEquals(18, loadout.length);
      // Slots 1-5 are the keys R, Y, Z (temporary ultimate default), V, B (Crush, v3).
      assertArrayEquals(HERO.heroInputSlots(), new String[]{loadout[0], loadout[1], loadout[2], loadout[3], loadout[4]});
      String[] v2 = HERO.previousDefaultLoadouts()[0];
      assertEquals("", v2[4], "the v2 default had no Crush: an untouched save moves to the new default");
      for (int i = 5; i < loadout.length; i++) {
         assertEquals("", loadout[i]);
      }

      List<ViltrumiteAbility> panel = HERO.panelAbilities();
      assertEquals(5, panel.size());
      for (int i = 0; i < panel.size(); i++) {
         assertEquals(HERO.heroInputSlots()[i], panel.get(i).getId());
         assertEquals(HERO.abilityIcon(panel.get(i).getId()), panel.get(i).getIcon());
      }
   }

   @Test
   void slotsStayGreyUntilASessionAuthorsTheirMove() {
      HeroPublicSnapshot idle = ThorHero.composeSnapshot(new ThorState());
      for (ThorInput input : ThorInput.values()) {
         // Audit v3: MMB's draw from the belt is the foundation's own technical move, so its slot is lit.
         assertTrue(input == ThorInput.HAMMER ? !ThorAbilities.slotGrey(idle, input) : ThorAbilities.slotGrey(idle, input),
            input + ": only the foundation's MMB draw is authored in the foundation");
      }

      try {
         dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves.register("test", ThorMove.ELECTRIC_MODE,
            new dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveDefinition(0, 1, 0, null, 1),
            dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoveHandler.NONE);
         assertFalse(ThorAbilities.slotGrey(idle, ThorInput.POWER), "an authored input lights while idle");
         assertTrue(ThorAbilities.slotGrey(idle, ThorInput.MOBILITY), "other slots are untouched");
         ThorState running = new ThorState();
         running.timeline.request(ThorMove.DRAW, -1);
         assertTrue(ThorAbilities.slotGrey(ThorHero.composeSnapshot(running), ThorInput.POWER), "grey while a move runs");
         assertTrue(ThorAbilities.slotGrey((HeroPublicSnapshot)null, ThorInput.POWER));
      } finally {
         dev.baranhan.viltrumitecore.hero.thor.bridge.ThorMoves.resetForTests();
      }
   }

   @Test
   void snapshotCarriesTheHammerAndTheRunningMove() {
      HeroPublicSnapshot idle = ThorHero.composeSnapshot(new ThorState());
      assertSame(HeroId.THOR, idle.heroId());
      assertEquals(-1, idle.actionId());
      assertFalse(idle.actionBusy());
      assertEquals(0, idle.heroFlags());
      assertEquals(-1, idle.controlTargetId());

      ThorState state = new ThorState();
      ThorStateFixtures.hammer(state, HammerState.IN_HAND);
      state.timeline.request(ThorMove.HOLSTER, -1);
      for (int i = 0; i < ThorRules.HOLSTER_INTRO + 1; i++) {
         state.timeline.tick();
      }

      HeroPublicSnapshot running = HeroPublicSnapshot.decode(ThorHero.composeSnapshot(state).encode());
      assertSame(HeroId.THOR, running.heroId());
      assertSame(ThorMove.HOLSTER, ThorMove.byWireId(running.actionId()));
      assertEquals(ThorRules.HOLSTER_INTRO + 1, running.actionElapsed());
      assertEquals(ThorMove.HOLSTER.length(), running.actionLength());
      assertTrue(running.actionBusy());
      assertSame(HammerState.IN_HAND, ThorFlags.hammer(running.heroFlags()));
      assertSame(MovePhase.MAIN, StagedTimeline.phaseOf(running.variant()));
      assertEquals(0, StagedTimeline.stepOf(running.variant()));

      dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge.openGrabWindow(state, 4);
      assertTrue(ThorFlags.grabWindow(HeroPublicSnapshot.decode(ThorHero.composeSnapshot(state).encode()).heroFlags()), "the client claim sees the grab window");
   }

   @Test
   void presenceIsMjolnirInAFreeHandOtherwiseFists() {
      assertSame(ThorCombat.MJOLNIR, ThorCombat.presence(HammerState.IN_HAND, true));
      assertSame(ThorCombat.FISTS, ThorCombat.presence(HammerState.IN_HAND, false));
      assertSame(ThorCombat.FISTS, ThorCombat.presence(HammerState.HOLSTERED, true));
      assertSame(ThorCombat.FISTS, ThorCombat.presence(HammerState.THROWN, true));

      CombatPresence mjolnir = ThorCombat.MJOLNIR;
      for (String tag : new String[]{CombatTags.WEAPON, CombatTags.WEAPON_HAMMER, CombatTags.ELEMENT_LIGHTNING, CombatTags.TRAIT_DIVINE,
         CombatTags.TRAIT_THROWABLE, CombatTags.TRAIT_RETURNING, CombatTags.TRAIT_UNBREAKABLE}) {
         assertTrue(mjolnir.has(tag), tag);
      }

      assertTrue(ThorCombat.FISTS.has(CombatTags.BODY));
      assertFalse(ThorCombat.FISTS.has(CombatTags.WEAPON));
      assertSame(CombatStance.NONE, mjolnir.stance(), "no stance until strikes and guard ship");
   }

   @Test
   void mjolnirJoinsTheGenericRulesByItsTags() {
      // Any other weapon is described by tags only; no rule names Thor or Mjölnir.
      CombatPresence axe = new CombatPresence("test:axe", Set.of(CombatTags.WEAPON_AXE, CombatTags.ELEMENT_FROST), CombatStance.GUARD, 0);
      InteractionRules rules = InteractionRules.defaults();
      assertSame(InteractionKind.BLOCK, rules.resolve(ThorCombat.MJOLNIR.withStance(CombatStance.ACTIVE), axe).orElseThrow().kind());
      assertSame(InteractionKind.BLOCK, rules.resolve(ThorCombat.FISTS.withStance(CombatStance.ACTIVE), axe).orElseThrow().kind());
      assertTrue(rules.resolve(ThorCombat.MJOLNIR, axe).isEmpty(), "a carried hammer does not interact");
   }

   @Test
   void presenceWithoutAPlayerIsNone() {
      // Reinhard (drawn Reid) and Captain (raised shield) bring presences since the contextual interactions; with no
      // player (seam probe) there is nothing to present.
      assertSame(CombatPresence.NONE, ReinhardHero.INSTANCE.combatPresence(null));
      assertSame(CombatPresence.NONE, dev.baranhan.viltrumitecore.hero.captain.CaptainHero.INSTANCE.combatPresence(null));
   }

   @Test
   void everyLocaleHasEveryThorText() throws IOException {
      List<String> keys = new ArrayList<>(List.of("gui.viltrumitecore.race_selection.become_thor", ThorFeedback.NOT_READY, ThorFeedback.HANDS_BUSY,
         ThorFeedback.BUSY, ThorFeedback.CONTROLS));
      for (String slot : ThorAbilities.slotIds()) {
         String name = "ability.viltrumitecore.thor_" + slot.substring(slot.indexOf(':') + 1);
         keys.add(name + ".name");
         keys.add(name + ".desc");
      }

      for (ThorMove move : ThorMove.values()) {
         keys.add(move.translationKey());
      }

      // Shipped strings = resources/lang + src/main/lang-overlays (merged by processResources).
      Map<String, JsonObject> locales = ShippedLang.merged();
      assertTrue(locales.size() >= 11, "every shipped locale");
      for (Map.Entry<String, JsonObject> locale : locales.entrySet()) {
         JsonObject lang = locale.getValue();
         for (String key : keys) {
            assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), locale.getKey() + " misses " + key);
         }

         assertTrue(lang.get(ThorFeedback.NOT_READY).getAsString().contains("%1$s") || lang.get(ThorFeedback.NOT_READY).getAsString().contains("%s"),
            locale.getKey() + ": the refusal names the move");
      }
   }
}
