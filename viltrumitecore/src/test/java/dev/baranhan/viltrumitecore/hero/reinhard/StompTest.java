package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroDebris;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.Stomp;
import org.junit.jupiter.api.Test;

/** Spec §6.2 Stomp (stage 3 Task 9). */
class StompTest {
   @Test
   void radius8Damage12() {
      assertEquals(8.0, ReinhardRules.STOMP_RADIUS);
      assertEquals(12.0F, ReinhardRules.STOMP_DAMAGE);
      assertEquals(0.8, ReinhardRules.STOMP_KNOCKBACK);
      assertTrue(Stomp.inArea(8.0, 0.0, 0.0));
      assertTrue(Stomp.inArea(5.0, 0.0, -6.0), "on the circle (5, 6) is within 8");
      assertFalse(Stomp.inArea(6.0, 0.0, 6.0), "8.49 away");
      assertFalse(Stomp.inArea(8.1, 0.0, 0.0));
   }

   @Test
   void cooldown160() {
      ReinhardState state = new ReinhardState();
      Stomp.start(state);
      assertEquals(160, ReinhardRules.STOMP_COOLDOWN);
      assertEquals(ReinhardRules.STOMP_COOLDOWN, state.cooldowns[ReinhardAbility.STOMP.ordinal()]);
      assertTrue(state.action.is(ReinhardAbility.STOMP));
      assertEquals(ReinhardRules.STOMP_TICKS, state.action.length());
      assertEquals(14, ReinhardRules.STOMP_TICKS);
      assertEquals(8, ReinhardRules.STOMP_HIT_TICK);
      for (int i = 0; i < ReinhardRules.STOMP_TICKS; i++) {
         assertEquals(i == ReinhardRules.STOMP_HIT_TICK, Stomp.hitNow(state.action), "tick " + i);
         state.action.tick();
      }
   }

   @Test
   void airborneTargetsAbove3Missed() {
      assertTrue(Stomp.inArea(2.0, 3.0, 0.0));
      assertTrue(Stomp.inArea(2.0, -3.0, 0.0));
      assertFalse(Stomp.inArea(2.0, 3.5, 0.0), "flying above the ground plane");
      assertFalse(Stomp.inArea(0.0, -4.0, 0.0));
   }

   @Test
   void moderateEruptionOnlyInKnight() {
      HeroDebris.Eruption knight = Stomp.eruption(DestructionLevel.of(ReinhardMode.KNIGHT));
      assertNotNull(knight);
      assertEquals(4.0, knight.radius(), 1.0E-9);
      assertTrue(knight.depth() <= 1.0);
      assertTrue(knight.maxFlying() <= 16, "few flying blocks");
      assertNull(Stomp.eruption(DestructionLevel.of(ReinhardMode.RESTRAINED)), "Restrained breaks nothing");
      assertNull(Stomp.eruption(DestructionLevel.NONE));
      assertEquals(ReinhardMode.KNIGHT, ReinhardAbility.STOMP.mode());
   }
}
