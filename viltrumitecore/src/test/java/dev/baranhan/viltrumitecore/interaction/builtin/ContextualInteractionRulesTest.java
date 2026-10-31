package dev.baranhan.viltrumitecore.interaction.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.captain.CaptainHero;
import dev.baranhan.viltrumitecore.hero.combat.CombatPresence;
import dev.baranhan.viltrumitecore.hero.combat.CombatStance;
import dev.baranhan.viltrumitecore.hero.combat.CombatTags;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.IronManHero;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardHero;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorCombat;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.interaction.ContextualInteraction;
import dev.baranhan.viltrumitecore.interaction.ContextualInteractions;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The tag rules behind the duels match the heroes' real presences, in the right direction only. */
class ContextualInteractionRulesTest {
   static final CombatPresence THOR_STRIKE = ThorCombat.presence(HammerState.IN_HAND, true, ThorMove.HAMMER_COMBO, MovePhase.MAIN);
   static final CombatPresence THOR_IDLE = ThorCombat.presence(HammerState.IN_HAND, true, null, null);
   static final CombatPresence THOR_FISTS = ThorCombat.presence(HammerState.THROWN, true, ThorMove.FIST_COMBO, MovePhase.MAIN);

   @Test
   void reidStrokeBreaksTheRaisedShieldOnly() {
      assertTrue(Duels.directed(ReidShattersVibranium.RULE, ReinhardHero.REID_STROKE, CaptainHero.SHIELD_GUARD));
      assertFalse(Duels.directed(ReidShattersVibranium.RULE, ReinhardHero.REID_DRAWN, CaptainHero.SHIELD_GUARD), "a drawn but idle Reid does not strike");
      assertFalse(Duels.directed(ReidShattersVibranium.RULE, CaptainHero.SHIELD_GUARD, ReinhardHero.REID_STROKE), "direction matters");
      assertFalse(Duels.directed(ReidShattersVibranium.RULE, THOR_STRIKE, CaptainHero.SHIELD_GUARD), "Mjolnir does not annihilate");
      assertFalse(Duels.directed(ReidShattersVibranium.RULE, ReinhardHero.REID_STROKE, CombatPresence.NONE), "no raised shield, no shatter");
   }

   @Test
   void mjolnirStrikeOnTheShieldMakesTheStorm() {
      assertTrue(Duels.directed(MjolnirMeetsVibranium.RULE, THOR_STRIKE, CaptainHero.SHIELD_GUARD));
      assertFalse(Duels.directed(MjolnirMeetsVibranium.RULE, THOR_IDLE, CaptainHero.SHIELD_GUARD), "only the striking window");
      assertFalse(Duels.directed(MjolnirMeetsVibranium.RULE, THOR_FISTS, CaptainHero.SHIELD_GUARD), "no hammer, no storm");
      assertFalse(Duels.directed(MjolnirMeetsVibranium.RULE, ReinhardHero.REID_STROKE, CaptainHero.SHIELD_GUARD));
      assertTrue(CaptainHero.SHIELD_GUARD.has(CombatTags.WEAPON_SHIELD));
      assertEquals(CombatStance.GUARD, CaptainHero.SHIELD_GUARD.stance());
   }

   @Test
   void divineClashNeedsBothStrikesInEitherDirection() {
      assertTrue(Duels.directed(DivineWeaponClash.RULE, ReinhardHero.REID_STROKE, THOR_STRIKE));
      assertFalse(Duels.directed(DivineWeaponClash.RULE, ReinhardHero.REID_STROKE, THOR_IDLE));
      assertFalse(Duels.directed(DivineWeaponClash.RULE, ReinhardHero.REID_DRAWN, THOR_STRIKE));
   }

   @Test
   void ironManSuitIsPoweredArmor() {
      assertTrue(IronManHero.POWERED_ARMOR.has(CombatTags.ARMOR_POWERED));
      assertTrue(IronManHero.POWERED_ARMOR.has(CombatTags.ARMOR));
      assertFalse(THOR_STRIKE.has(CombatTags.ARMOR));
   }

   @Test
   void builtinsAreUniqueAndRegistryOrdersByPriority() {
      List<ContextualInteraction> all = ContextualInteractions.builtins();
      assertEquals(8, all.size());
      assertEquals(all.size(), all.stream().map(i -> i.spec().id()).distinct().count());
      for (int i = 1; i < all.size(); i++) {
         assertTrue(all.get(i - 1).spec().priority() >= all.get(i).spec().priority(), "catalog order follows priority: " + all.get(i).spec().id());
      }
   }
}
