package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import org.junit.jupiter.api.Test;

/** The ability gate of the router (stage 3 Task 1): one refusal per failed condition. */
class ReinhardActionsTest {
   private static ReinhardActions.Gate ok() {
      return new ReinhardActions.Gate(true, false, ReinhardMode.KNIGHT, ReinhardMode.KNIGHT, 0, false, false);
   }

   @Test
   void passes() {
      assertEquals(ReinhardActions.Refusal.NONE, ReinhardActions.gate(ok()));
   }

   @Test
   void controlledWhenCannotAct() {
      assertEquals(ReinhardActions.Refusal.CONTROLLED,
         ReinhardActions.gate(new ReinhardActions.Gate(false, false, ReinhardMode.KNIGHT, ReinhardMode.KNIGHT, 0, false, false)));
   }

   @Test
   void controlledWhenAnchored() {
      assertEquals(ReinhardActions.Refusal.CONTROLLED,
         ReinhardActions.gate(new ReinhardActions.Gate(true, true, ReinhardMode.KNIGHT, ReinhardMode.KNIGHT, 0, false, false)));
   }

   @Test
   void wrongMode() {
      assertEquals(ReinhardActions.Refusal.WRONG_MODE,
         ReinhardActions.gate(new ReinhardActions.Gate(true, false, ReinhardMode.RESTRAINED, ReinhardMode.KNIGHT, 0, false, false)));
   }

   @Test
   void cooldown() {
      assertEquals(ReinhardActions.Refusal.COOLDOWN,
         ReinhardActions.gate(new ReinhardActions.Gate(true, false, ReinhardMode.KNIGHT, ReinhardMode.KNIGHT, 5, false, false)));
   }

   @Test
   void busyOrTimeline() {
      assertEquals(ReinhardActions.Refusal.BUSY,
         ReinhardActions.gate(new ReinhardActions.Gate(true, false, ReinhardMode.KNIGHT, ReinhardMode.KNIGHT, 0, true, false)));
      assertEquals(ReinhardActions.Refusal.BUSY,
         ReinhardActions.gate(new ReinhardActions.Gate(true, false, ReinhardMode.KNIGHT, ReinhardMode.KNIGHT, 0, false, true)));
   }

   @Test
   void controlWinsOverEverything() {
      assertEquals(ReinhardActions.Refusal.CONTROLLED,
         ReinhardActions.gate(new ReinhardActions.Gate(false, true, ReinhardMode.RESTRAINED, ReinhardMode.KNIGHT, 9, true, true)));
   }

   @Test
   void actionMapsToAbility() {
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         assertEquals(ability, ReinhardActions.abilityFor(ability.action()));
      }

      assertNull(ReinhardActions.abilityFor(HeroAction.MODE_CYCLE));
      assertNull(ReinhardActions.abilityFor(HeroAction.JUMP));
   }
}
