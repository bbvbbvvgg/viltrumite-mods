package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.reinhard.ReinhardClips;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.SheathStrike;
import dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec §7 Sheath Strike (stage 4 Task 7). */
class SheathStrikeTest {
   @Test
   void onlyWhenSheathed() {
      assertTrue(Reid.allows(ReinhardAbility.SHEATH_STRIKE, Reid.Phase.SHEATHED));
      for (Reid.Phase phase : Reid.Phase.values()) {
         if (phase != Reid.Phase.SHEATHED) {
            assertFalse(Reid.allows(ReinhardAbility.SHEATH_STRIKE, phase), phase.name());
         }
      }
      assertEquals(ReinhardMode.HOLY_SWORD, ReinhardAbility.SHEATH_STRIKE.mode());
      assertInstanceOf(SheathStrike.class, ReinhardActions.handler(ReinhardAbility.SHEATH_STRIKE));
   }

   @Test
   void radius6Damage20Stun60() {
      assertEquals(6.0, ReinhardRules.SHEATH_RADIUS);
      assertEquals(20.0F, ReinhardRules.SHEATH_DAMAGE);
      assertEquals(60, ReinhardRules.SHEATH_STUN);
      assertTrue(SheathStrike.inArea(6.0, 0.0, 0.0));
      assertTrue(SheathStrike.inArea(0.0, 5.9, 0.0), "a sphere: above counts too");
      assertFalse(SheathStrike.inArea(4.5, 0.0, 4.5), "6.36 away");
      assertFalse(SheathStrike.inArea(0.0, -6.1, 0.0));
      assertEquals(1060L, SheathStrike.stunUntil(1000L));
      assertEquals(List.of(ReinhardClips.SHEATH_STRIKE), ReinhardClips.names(ReinhardAbility.SHEATH_STRIKE));
   }

   @Test
   void cooldown160() {
      ReinhardState state = new ReinhardState();
      SheathStrike.start(state);
      assertEquals(8, ReinhardRules.SHEATH_COOLDOWN);
      assertEquals(ReinhardRules.SHEATH_COOLDOWN, state.cooldowns[ReinhardAbility.SHEATH_STRIKE.ordinal()]);
      assertTrue(state.action.is(ReinhardAbility.SHEATH_STRIKE));
      assertEquals(ReinhardRules.SHEATH_TICKS, state.action.length());
      assertEquals(6, ReinhardRules.SHEATH_HIT_TICK);
      for (int i = 0; i < ReinhardRules.SHEATH_TICKS; i++) {
         assertEquals(i == ReinhardRules.SHEATH_HIT_TICK, SheathStrike.hitNow(state.action), "tick " + i);
         state.action.tick();
      }
   }

   @Test
   void uncontrollableTakesDamageOnly() {
      assertTrue(SheathStrike.stuns(true));
      assertFalse(SheathStrike.stuns(false), "a hero immune to freeze only takes the 20 damage");
      assertEquals(ControlKind.FREEZE, SheathStrike.STUN_KIND);
      assertTrue(SheathStrike.showsStars(ControlKind.FREEZE.ordinal(), true));
      assertFalse(SheathStrike.showsStars(ControlKind.FREEZE.ordinal(), false), "Regulus' freeze has no stars");
      assertFalse(SheathStrike.showsStars(ControlKind.IMPULSE.ordinal(), true));
      assertFalse(SheathStrike.showsStars(-1, true));
   }
}
