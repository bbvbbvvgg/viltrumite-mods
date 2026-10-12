package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Spec §6.2 Dash Behind (stage 3 Task 8). */
class DashBehindTest {
   private static final Vec3 SERVER = new Vec3(4.0, 64.0, 4.0);

   private static DashBehind.TargetFacts target(boolean found, boolean living, double distance) {
      return new DashBehind.TargetFacts(found, living, true, false, false, distance);
   }

   @Test
   void refusalKeepsCooldown() {
      ReinhardState state = new ReinhardState();
      assertFalse(DashBehind.start(state, Optional.empty(), 7), "no safe spot: refused");
      assertEquals(0, state.cooldowns[ReinhardAbility.DASH_BEHIND.ordinal()]);
      assertFalse(state.action.running());

      assertTrue(DashBehind.start(state, Optional.of(SERVER), 7));
      assertEquals(ReinhardRules.BEHIND_COOLDOWN, state.cooldowns[ReinhardAbility.DASH_BEHIND.ordinal()]);
      assertEquals(60, ReinhardRules.BEHIND_COOLDOWN);
      assertTrue(state.action.is(ReinhardAbility.DASH_BEHIND));
      assertEquals(7, state.action.targetId());
   }

   @Test
   void rangeIs24() {
      assertEquals(24.0, ReinhardRules.BEHIND_RANGE);
      assertEquals(1.5, ReinhardRules.BEHIND_OFFSET);
      assertEquals(3.0, ReinhardRules.BEHIND_SEARCH);
      assertTrue(DashBehind.validTarget(target(true, true, 24.0)));
      assertTrue(DashBehind.validTarget(target(true, true, 25.9)), "2 blocks of tolerance for the client's view");
      assertFalse(DashBehind.validTarget(target(true, true, 26.5)));
   }

   @Test
   void wantedSpotBehindTheTarget() {
      // Yaw 0 looks along +z: the spot is 1.5 blocks behind, on −z.
      Vec3 wanted = DashBehind.wanted(new Vec3(0.0, 64.0, 0.0), 0.0F);
      assertEquals(0.0, wanted.x, 1.0E-9);
      assertEquals(64.0, wanted.y, 1.0E-9);
      assertEquals(-1.5, wanted.z, 1.0E-9);
   }

   @Test
   void clientSpotAcceptedWithin1() {
      Vec3 client = SERVER.add(0.8, 0.0, 0.0);
      assertEquals(Optional.of(client), DashBehind.choose(client, Optional.of(SERVER), v -> true));
      assertEquals(Optional.of(SERVER), DashBehind.choose(client, Optional.of(SERVER), v -> !v.equals(client)),
         "the client spot must pass the server's own probe");
   }

   @Test
   void clientSpotFarAwayReplaced() {
      Vec3 client = SERVER.add(1.5, 0.0, 0.0);
      assertEquals(Optional.of(SERVER), DashBehind.choose(client, Optional.of(SERVER), v -> true));
      assertTrue(DashBehind.choose(client, Optional.empty(), v -> true).isEmpty(), "no server answer: refused");
   }

   @Test
   void packetWithForeignTargetRefused() {
      assertFalse(DashBehind.validTarget(target(false, false, 0.0)), "id unknown in the sender's level");
      assertFalse(DashBehind.validTarget(target(true, false, 3.0)), "not a living entity");
      assertFalse(DashBehind.validTarget(new DashBehind.TargetFacts(true, true, false, false, false, 3.0)), "dead");
      assertFalse(DashBehind.validTarget(new DashBehind.TargetFacts(true, true, true, true, false, 3.0)), "himself");
      assertFalse(DashBehind.validTarget(new DashBehind.TargetFacts(true, true, true, false, true, 3.0)), "a spectator");
   }

   @Test
   void nanCandidateRejected() {
      assertFalse(DashBehind.validCandidate(new Vec3(Double.NaN, 64.0, 0.0)));
      assertFalse(DashBehind.validCandidate(new Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)));
      assertFalse(DashBehind.validCandidate(new Vec3(0.0, 64.0, Double.NEGATIVE_INFINITY)));
      assertTrue(DashBehind.validCandidate(SERVER));
   }

   @Test
   void slotKeyNotRoutedThroughGenericInput() {
      assertFalse(Arrays.asList(ReinhardAbilities.inputSlots()).contains(ReinhardAbility.DASH_BEHIND.id()),
         "the separate packet carries the press");
      assertTrue(Arrays.asList(ReinhardAbilities.inputSlots()).contains(ReinhardAbility.GRAB_THROW.id()));
   }
}
