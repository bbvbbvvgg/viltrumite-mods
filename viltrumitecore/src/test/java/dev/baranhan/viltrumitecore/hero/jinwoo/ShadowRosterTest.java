package dev.baranhan.viltrumitecore.hero.jinwoo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Roster rules (spec §2, §5.7, §6.2) and shadow classification. */
class ShadowRosterTest {
   @Test
   void seedIsIgrisAndThreeInfantry() {
      ShadowRoster r = new ShadowRoster();
      r.seedIfEmpty();
      r.seedIfEmpty();
      assertEquals(4, r.size());
      assertTrue(r.entries().get(0).elite);
      assertEquals(ShadowKind.KNIGHT, r.entries().get(0).kind);
   }

   @Test
   void firstInsectIsBeruFirstBeastIsTank() {
      ShadowRoster r = new ShadowRoster();
      assertTrue(r.addExtracted(ShadowKind.INSECT).elite);
      assertFalse(r.addExtracted(ShadowKind.INSECT).elite);
      assertTrue(r.addExtracted(ShadowKind.BEAST).elite);
      assertFalse(r.addExtracted(ShadowKind.KNIGHT).elite);
   }

   @Test
   void capAndOrder() {
      ShadowRoster r = new ShadowRoster();
      for (int i = 0; i < 30; i++) {
         r.add(ShadowKind.INFANTRY, false);
      }
      assertEquals(JinwooRules.ARMY_ROSTER, r.size());
      assertNull(r.addExtracted(ShadowKind.INFANTRY));
      ShadowRoster s = new ShadowRoster();
      s.add(ShadowKind.INFANTRY, false);
      s.add(ShadowKind.KNIGHT, true);
      assertTrue(s.summonOrder(12).get(0).elite);
   }

   @Test
   void fallenShadowRevivesAfterCooldown() {
      ShadowRoster r = new ShadowRoster();
      r.seedIfEmpty();
      ShadowRoster.Entry e = r.entries().get(1);
      e.active = UUID.randomUUID();
      assertEquals(3, r.summonOrder(12).size());
      r.fell(e);
      assertFalse(e.ready());
      for (int i = 0; i < JinwooRules.SHADOW_REVIVE; i++) {
         r.tick();
      }
      assertTrue(e.ready());
   }

   @Test
   void saveLoadKeepsSerials() {
      ShadowRoster r = new ShadowRoster();
      r.seedIfEmpty();
      r.addExtracted(ShadowKind.BEAST);
      ShadowRoster c = r.copy();
      assertEquals(5, c.size());
      assertEquals(ShadowKind.BEAST, c.entries().get(4).kind);
      assertTrue(c.entries().get(4).elite);
      assertEquals(5, c.add(ShadowKind.INFANTRY, false).serial);
   }

   @Test
   void classification() {
      assertEquals(ShadowKind.INFANTRY, ShadowKind.classify("zombie", false, false, true, false));
      assertEquals(ShadowKind.KNIGHT, ShadowKind.classify("wither_skeleton", false, false, true, false));
      assertEquals(ShadowKind.KNIGHT, ShadowKind.classify("", true, false, false, false));
      assertEquals(ShadowKind.INSECT, ShadowKind.classify("spider", false, true, true, false));
      assertEquals(ShadowKind.BEAST, ShadowKind.classify("ravager", false, false, true, false));
      assertEquals(ShadowKind.BEAST, ShadowKind.classify("iron_golem", false, false, false, false));
      assertNull(ShadowKind.classify("villager", false, false, false, true));
      assertNull(ShadowKind.classify("cow", false, false, false, true));
   }

   @Test
   void kindStatsMatchSpec() {
      assertEquals(24, ShadowKind.INFANTRY.health);
      assertEquals(40, ShadowKind.KNIGHT.health);
      assertEquals(60, ShadowKind.KNIGHT.eliteHealth);
      assertEquals(50, ShadowKind.INSECT.eliteHealth);
      assertEquals(90, ShadowKind.BEAST.eliteHealth);
      assertEquals(10, ShadowKind.BEAST.armor);
      assertTrue(ShadowKind.BEAST.knockbackImmune());
      assertEquals(9, ShadowKind.KNIGHT.damage);
      assertEquals(11, ShadowKind.KNIGHT_LUNGE_DAMAGE);
   }
}
