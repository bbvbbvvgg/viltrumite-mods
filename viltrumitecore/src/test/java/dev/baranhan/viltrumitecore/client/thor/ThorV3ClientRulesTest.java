package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import org.junit.jupiter.api.Test;

/** Audit v3: wrath camera and Mjölnir trails, pure halves. */
class ThorV3ClientRulesTest {
   @Test
   void theWrathCameraNarrowsKicksAndSettles() {
      int contact = ThorAttacks.envelope(ThorMove.WRATH).phases().contactTick();
      assertEquals(1.0F, WrathCamera.fov(-1.0F, contact));
      assertEquals(1.0F, WrathCamera.fov(0.0F, contact), 1.0E-4F);
      assertTrue(WrathCamera.fov(contact - 1, contact) < 0.9F, "narrowed into the build-up");
      assertEquals(WrathCamera.KICK, WrathCamera.fov(contact + 2.0F, contact), 1.0E-4F);
      assertEquals(1.0F, WrathCamera.fov(contact + 2.0F + WrathCamera.SETTLE, contact), 1.0E-4F);
      float prev = 0.0F;
      for (int t = 0; t < contact; t++) {
         float f = WrathCamera.fov(t, contact);
         assertTrue(t == 0 || f <= prev + 1.0E-6F, "monotone narrowing");
         prev = f;
      }
   }

   @Test
   void trailsFollowTheSwingOnly() {
      ThorAttacks.Attack swing = ThorAttacks.byClip("hammer_swing_right");
      int windup = swing.phases().windup();
      assertFalse(MjolnirTrails.emitting(swing, windup - MjolnirTrails.LEAD - 1));
      assertTrue(MjolnirTrails.emitting(swing, windup));
      assertTrue(MjolnirTrails.emitting(swing, windup + swing.phases().contact()));
      assertFalse(MjolnirTrails.emitting(swing, windup + swing.phases().contact() + 1), "no trail in the recovery");
      assertFalse(MjolnirTrails.emitting(ThorAttacks.byClip("fist_tackle"), 5), "a row without a trail");
      assertFalse(MjolnirTrails.emitting(ThorAttacks.envelope(ThorMove.DRAW), 6), "technical moves do not trail");
      assertFalse(MjolnirTrails.emitting(null, 0));
   }
}
