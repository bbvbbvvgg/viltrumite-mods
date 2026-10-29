package dev.baranhan.viltrumitecore.hero.jinwoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Timelines, loadout and slot rules of Jinwoo's moves. */
class JinwooTimelineTest {
   @Test
   void comboMovesMatchSpec() {
      for (int i = 0; i < 5; i++) {
         JinwooMove m = JinwooMove.combo(i);
         assertEquals(JinwooRules.COMBO_LENGTH[i], m.length);
         assertEquals(JinwooRules.COMBO_CONTACT[i], m.contact);
         assertTrue(m.isCombo());
      }
      assertEquals(8, JinwooMove.LOW_SWEEP.length);
      assertEquals(12, JinwooMove.DRAW.length);
      assertEquals(8, JinwooMove.SHEATH.length);
      assertEquals(100, JinwooMove.ARISE.length);
      assertEquals(60, JinwooMove.ARISE.contact);
   }

   @Test
   void contactsInsideMoves() {
      for (JinwooMove m : JinwooMove.values()) {
         assertTrue(m.length > 0, m.name());
         assertTrue(m.contact <= m.length, m.name());
         assertNotNull(m.clip());
         assertEquals(m, JinwooMove.byId(m.ordinal()));
      }
   }

   @Test
   void unstoppableMoves() {
      assertTrue(JinwooMove.RUSH.unstoppable());
      assertTrue(JinwooMove.CHARGE.unstoppable());
      assertTrue(JinwooMove.ARISE.unstoppable());
      assertFalse(JinwooMove.COMBO_1.unstoppable());
   }

   @Test
   void loadoutTwoPagesAndUniqueActions() {
      assertEquals(18, JinwooAbilities.LOADOUT.length);
      Set<HeroAction> actions = new HashSet<>();
      for (JinwooAbility a : JinwooAbility.values()) {
         assertTrue(actions.add(a.action()), a.name());
         assertEquals(a, JinwooAbility.byId(a.id()));
         assertEquals(a, JinwooAbility.byAction(a.action()));
         assertTrue(JinwooAbilities.owns(a.id()));
      }
      for (int i = 0; i < 6; i++) {
         assertTrue(JinwooAbilities.LOADOUT[i].startsWith("jinwoo:"));
      }
      for (int i = 6; i < 11; i++) {
         assertTrue(JinwooAbilities.LOADOUT[i].startsWith("jinwoo:"));
      }
      assertTrue(JinwooAbility.values().length <= JinwooAbility.BLOCK_CD);
   }

   @Test
   void slotGreyRules() {
      assertTrue(JinwooAbilities.slotGrey(JinwooAbility.SHADOW_STEP, 5, 100, 0, false));
      assertTrue(JinwooAbilities.slotGrey(JinwooAbility.MONARCH, 0, 79, 0, false));
      assertFalse(JinwooAbilities.slotGrey(JinwooAbility.MONARCH, 0, 80, 0, false));
      assertTrue(JinwooAbilities.slotGrey(JinwooAbility.ATTACK, 0, 100, 0, false));
      assertFalse(JinwooAbilities.slotGrey(JinwooAbility.ATTACK, 0, 100, 3, false));
      assertTrue(JinwooAbilities.slotGrey(JinwooAbility.EXTRACTION, 0, 100, 0, false));
      assertFalse(JinwooAbilities.slotGrey(JinwooAbility.EXTRACTION, 0, 100, 0, true));
      assertFalse(JinwooAbilities.slotGrey(JinwooAbility.SUMMON, 0, 0, 0, false));
   }

   @Test
   void flagsRoundTrip() {
      int f = 0;
      f = JinwooFlags.set(f, JinwooFlags.DAGGERS, true);
      f = JinwooFlags.set(f, JinwooFlags.DOMAIN, true);
      assertTrue(JinwooFlags.has(f, JinwooFlags.DAGGERS));
      assertTrue(JinwooFlags.has(f, JinwooFlags.DOMAIN));
      f = JinwooFlags.set(f, JinwooFlags.DAGGERS, false);
      assertFalse(JinwooFlags.has(f, JinwooFlags.DAGGERS));
   }

   @Test
   void snapshotCarriesManaAndArmy() {
      JinwooState state = new JinwooState();
      state.roster.seedIfEmpty();
      state.mana = 42.5F;
      state.daggers = true;
      state.start(JinwooMove.COMBO_2, 7);
      state.elapsed = 3;
      var snap = JinwooHero.compose(state, 2, net.minecraft.world.phys.Vec3.ZERO);
      assertEquals(425, snap.resource());
      assertEquals(204, snap.resource2());
      assertEquals(JinwooMove.COMBO_2.ordinal(), snap.actionId());
      assertEquals(3, snap.actionElapsed());
      assertEquals(7, snap.controlTargetId());
      assertTrue(JinwooFlags.has(snap.heroFlags(), JinwooFlags.DAGGERS));
   }

   @Test
   void stateSaveLoad() {
      JinwooState state = new JinwooState();
      state.roster.seedIfEmpty();
      state.mana = 33;
      state.cooldowns[JinwooAbility.MONARCH.ordinal()] = 500;
      net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
      state.save(tag);
      JinwooState back = new JinwooState();
      back.load(tag);
      assertEquals(33, back.mana, 1e-4);
      assertEquals(500, back.cooldowns[JinwooAbility.MONARCH.ordinal()]);
      assertEquals(4, back.roster.size());
      JinwooState respawn = state.respawnCopy();
      assertEquals(JinwooRules.MANA_MAX, respawn.mana);
      assertEquals(4, respawn.roster.size());
   }
}
