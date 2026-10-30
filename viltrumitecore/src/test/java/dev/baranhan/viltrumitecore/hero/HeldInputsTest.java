package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeldInputsTest {
   /** Fake hero: configurable claims and canAct, records every routed edge. */
   static final class FakeSink implements HeldInputs.Sink {
      final Map<MouseButton, HeroAction> claims = new EnumMap<>(MouseButton.class);
      boolean canAct = true;
      int claimChecks;
      int canActChecks;
      final List<String> log = new ArrayList<>();

      @Override
      public HeroAction claim(MouseButton button) {
         this.claimChecks++;
         return this.claims.get(button);
      }

      @Override
      public boolean canAct(HeroAction action) {
         this.canActChecks++;
         return this.canAct;
      }

      @Override
      public void handle(HeroAction action, boolean pressed) {
         this.log.add(action + (pressed ? "+" : "-"));
      }
   }

   @Test
   void releaseAlwaysRouted() {
      FakeSink sink = new FakeSink();
      sink.claims.put(MouseButton.PRIMARY, HeroAction.PRIMARY_ATTACK);
      HeldInputs held = new HeldInputs();
      assertTrue(held.press(MouseButton.PRIMARY, sink));
      sink.canAct = false;
      sink.claims.clear();
      assertTrue(held.release(MouseButton.PRIMARY, sink));
      assertEquals(List.of("PRIMARY_ATTACK+", "PRIMARY_ATTACK-"), sink.log);
      assertFalse(held.release(MouseButton.PRIMARY, sink), "a second release has nothing to end");
   }

   @Test
   void repeatedPressIgnored() {
      FakeSink sink = new FakeSink();
      sink.claims.put(MouseButton.PRIMARY, HeroAction.PRIMARY_ATTACK);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.PRIMARY, sink);
      assertFalse(held.press(MouseButton.PRIMARY, sink));
      assertEquals(List.of("PRIMARY_ATTACK+"), sink.log);
   }

   @Test
   void pressCheckedOnlyOnce() {
      FakeSink sink = new FakeSink();
      sink.claims.put(MouseButton.SECONDARY, HeroAction.PRIMARY_ATTACK);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.SECONDARY, sink);
      int claims = sink.claimChecks;
      int acts = sink.canActChecks;
      held.release(MouseButton.SECONDARY, sink);
      assertEquals(claims, sink.claimChecks);
      assertEquals(acts, sink.canActChecks);

      sink.canAct = false;
      assertFalse(held.press(MouseButton.SECONDARY, sink), "refused press starts nothing");
      assertNull(held.heldAction(MouseButton.SECONDARY));
   }

   @Test
   void forcedReleaseOnSuitOff() {
      FakeSink sink = new FakeSink();
      sink.claims.put(MouseButton.PRIMARY, HeroAction.PRIMARY_ATTACK);
      sink.claims.put(MouseButton.MIDDLE, HeroAction.ASSIGN_HEART);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.PRIMARY, sink);
      held.press(MouseButton.MIDDLE, sink);
      sink.claims.clear();
      held.releaseAll(sink);
      assertTrue(held.isEmpty());
      assertTrue(sink.log.contains("PRIMARY_ATTACK-"));
      assertTrue(sink.log.contains("ASSIGN_HEART-"));
      assertFalse(held.release(MouseButton.PRIMARY, sink), "the late client release finds nothing");
   }

   @Test
   void forcedReleaseOnControl() {
      FakeSink sink = new FakeSink();
      sink.claims.put(MouseButton.PRIMARY, HeroAction.PRIMARY_ATTACK);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.PRIMARY, sink);
      sink.canAct = false;
      assertTrue(HeldInputs.forcesRelease(true, true));
      assertTrue(HeldInputs.forcesRelease(false, false));
      assertFalse(HeldInputs.forcesRelease(false, true));
      held.releaseAll(sink);
      assertEquals(List.of("PRIMARY_ATTACK+", "PRIMARY_ATTACK-"), sink.log);
   }

   @Test
   void releaseAfterToolChangeGoesToStartedAction() {
      FakeSink sink = new FakeSink();
      sink.claims.put(MouseButton.SECONDARY, HeroAction.PRIMARY_ATTACK);
      HeldInputs held = new HeldInputs();
      held.press(MouseButton.SECONDARY, sink);
      sink.claims.put(MouseButton.SECONDARY, HeroAction.SUIT);
      held.release(MouseButton.SECONDARY, sink);
      assertEquals(List.of("PRIMARY_ATTACK+", "PRIMARY_ATTACK-"), sink.log);
   }

   @Test
   void otherHeroesNeverClaim() {
      HeroDefinition[] others = {new HumanHero(), new dev.baranhan.viltrumitecore.hero.homelander.HomelanderHero()};
      for (HeroDefinition hero : others) {
         for (MouseButton button : MouseButton.values()) {
            assertNull(hero.mouseAction(button, null), hero.id() + " " + button);
         }
      }

      HeroDefinition regulus = dev.baranhan.viltrumitecore.hero.regulus.RegulusHero.INSTANCE;
      assertEquals(HeroAction.ASSIGN_HEART, regulus.mouseAction(MouseButton.MIDDLE, null));
      // without a player there is no empty hand to read: Greed's Touch is never claimed blind
      assertNull(regulus.mouseAction(MouseButton.PRIMARY, null));
      assertNull(regulus.mouseAction(MouseButton.SECONDARY, null));
   }
}
