package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import org.junit.jupiter.api.Test;

/** Mode switch on MMB (spec §5): cycle, short technical cooldown, refusal. */
class ModeCycleTest {
   @Test
   void pressCyclesAll() {
      ModeCycle.Result first = ModeCycle.press(ReinhardMode.RESTRAINED, 0, false);
      assertTrue(first.accepted());
      assertSame(ReinhardMode.KNIGHT, first.next());
      assertSame(ReinhardMode.HOLY_SWORD, ModeCycle.press(ReinhardMode.KNIGHT, 0, false).next());
      assertSame(ReinhardMode.RESTRAINED, ModeCycle.press(ReinhardMode.HOLY_SWORD, 0, false).next());
   }

   @Test
   void cooldownRefuses() {
      ModeCycle.Result result = ModeCycle.press(ReinhardMode.KNIGHT, 1, false);
      assertFalse(result.accepted());
      assertSame(ReinhardMode.KNIGHT, result.next());
   }

   @Test
   void busyRefuses() {
      ModeCycle.Result result = ModeCycle.press(ReinhardMode.HOLY_SWORD, 0, true);
      assertFalse(result.accepted());
      assertSame(ReinhardMode.HOLY_SWORD, result.next());
   }

   @Test
   void cooldownIsSixtyTicks() {
      assertEquals(4, ReinhardRules.MODE_COOLDOWN);
   }

   @Test
   void pageMatchesMode() {
      assertEquals(0, ReinhardMode.RESTRAINED.page());
      assertEquals(1, ReinhardMode.KNIGHT.page());
      assertEquals(2, ReinhardMode.HOLY_SWORD.page());
   }

   @Test
   void middleMouseClaimsModeCycle() {
      assertSame(HeroAction.MODE_CYCLE, ReinhardHero.INSTANCE.mouseAction(MouseButton.MIDDLE, null));
      assertEquals(HeroAction.PRIMARY_ATTACK, ReinhardHero.INSTANCE.mouseAction(MouseButton.PRIMARY, null));
      assertNull(ReinhardHero.INSTANCE.mouseAction(MouseButton.SECONDARY, null));
   }

   @Test
   void modeCycleAppendedAfterSignature() {
      assertEquals(HeroAction.SIGNATURE.ordinal() + 1, HeroAction.MODE_CYCLE.ordinal());
      assertEquals(25, HeroAction.MODE_CYCLE.ordinal());
   }
}
