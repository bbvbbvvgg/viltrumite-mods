package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Spec §6.2 Grab & Throw (stage 3 Task 7). */
class GrabThrowTest {
   /** A fake ControlManager: records every acquire and release. */
   static final class FakeControl implements GrabThrow.Control {
      final boolean accepts;
      final List<UUID> acquired = new ArrayList<>();
      final List<String> released = new ArrayList<>();

      FakeControl(boolean accepts) {
         this.accepts = accepts;
      }

      @Override
      public boolean acquire(UUID effectId) {
         this.acquired.add(effectId);
         return this.accepts;
      }

      @Override
      public void release(UUID targetId, UUID effectId, ReleaseReason reason) {
         this.released.add(targetId + "/" + effectId + "/" + reason);
      }
   }

   private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000000a");

   private static GrabThrow.Candidate candidate(boolean control, boolean impulse) {
      return new GrabThrow.Candidate(42, TARGET, control, impulse);
   }

   /** Vanilla living-entity flight: move by the velocity, then drag 0.91 horizontally, gravity 0.08 and 0.98 vertically. */
   private static double flight(int sustain, double startHeight) {
      Vec3 v = GrabThrow.throwVelocity(0.0F, 0.0F);
      double x = 0.0;
      double y = startHeight;
      double vx = 0.0;
      double vy = 0.0;
      for (int tick = 0; tick < 400 && y > 0.0; tick++) {
         if (tick < sustain) {
            vx = v.z;
            vy = v.y;
         }

         x += vx;
         y += vy;
         vx *= 0.91;
         vy = (vy - 0.08) * 0.98;
      }

      return x;
   }

   @Test
   void sustainCoversAbout120() {
      assertEquals(120, ReinhardRules.THROW_DISTANCE);
      assertTrue(ReinhardRules.THROW_SPEED <= 3.9, "within the motion packet cap");
      assertEquals(ReinhardRules.THROW_SUSTAIN_TICKS, GrabThrow.sustainTicks(ReinhardRules.THROW_DISTANCE,
         ReinhardRules.THROW_SPEED * Math.cos(Math.toRadians(ReinhardRules.THROW_UP_DEGREES)), 0.91));
      double covered = flight(ReinhardRules.THROW_SUSTAIN_TICKS, 1.0);
      assertEquals(120.0, covered, 10.0, "the flight covers ~120 blocks, got " + covered);
      Vec3 v = GrabThrow.throwVelocity(0.0F, 0.0F);
      assertEquals(ReinhardRules.THROW_SPEED, v.length(), 1.0E-3);
      assertTrue(v.y > 0.0, "a small up angle");
      assertEquals(Math.sin(Math.toRadians(ReinhardRules.THROW_UP_DEGREES)), v.y / v.length(), 1.0E-3, "float trigonometry of Vec3.directionFromRotation");
   }

   @Test
   void damage15OnThrow() {
      assertEquals(15.0F, ReinhardRules.THROW_DAMAGE);
      assertEquals(3.5, ReinhardRules.GRAB_REACH);
      assertEquals(100, ReinhardRules.GRAB_MAX_TICKS);
      ReinhardState state = new ReinhardState();
      FakeControl control = new FakeControl(true);
      assertTrue(GrabThrow.grab(state, candidate(true, true), control));
      assertEquals(42, GrabThrow.throwNow(state), "the throw hits the held target");
      assertEquals(1, control.released.size(), "released before the hit (the hurt lands on a free target)");
      assertTrue(control.released.get(0).endsWith("/" + ReleaseReason.NORMAL_END));
      assertTrue(state.action.is(ReinhardAbility.GRAB_THROW));
      assertEquals(ActionTimeline.PHASE_SNAP, state.action.phase(), "the throw clip");
      assertEquals(42, state.action.targetId());
      // Hit-stop on the throw: held for HIT_STOP_TICKS, then the launch.
      assertEquals(GrabThrow.Beat.HOLD, GrabThrow.beat(0));
      assertEquals(GrabThrow.Beat.HOLD, GrabThrow.beat(ReinhardRules.HIT_STOP_TICKS - 1));
      assertEquals(GrabThrow.Beat.LAUNCH, GrabThrow.beat(ReinhardRules.HIT_STOP_TICKS));
      assertEquals(GrabThrow.Beat.NONE, GrabThrow.beat(ReinhardRules.HIT_STOP_TICKS + 1));
   }

   @Test
   void cooldownFromThrow() {
      assertEquals(240, ReinhardRules.GRAB_COOLDOWN);
      ReinhardState state = new ReinhardState();
      FakeControl control = new FakeControl(true);
      assertTrue(GrabThrow.grab(state, candidate(true, true), control));
      assertEquals(0, state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()], "no cooldown while holding");
      assertNotNull(state.grab);
      assertTrue(GrabThrow.holding(state), "the second press belongs to the grab");
      GrabThrow.throwNow(state);
      assertEquals(240, state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()], "the cooldown starts at the throw");
      assertNull(state.grab);
      assertFalse(GrabThrow.holding(state));
   }

   @Test
   void heldTooLongIsThrown() {
      ReinhardState state = new ReinhardState();
      FakeControl control = new FakeControl(true);
      GrabThrow.grab(state, candidate(true, true), control);
      int ticks = 0;
      while (GrabThrow.holding(state) && ticks < 1000) {
         if (GrabThrow.holdTick(state)) {
            GrabThrow.throwNow(state);
         }

         state.action.tick();
         ticks++;
      }

      assertEquals(ReinhardRules.GRAB_MAX_TICKS, ticks, "the hold ends with a throw after GRAB_MAX_TICKS");
      assertEquals(ActionTimeline.PHASE_SNAP, state.action.phase());
   }

   @Test
   void releaseOnEveryEnd() {
      List<CleanupReason> reasons = new ArrayList<>(List.of(CleanupReason.values()));
      reasons.add(null);
      for (CleanupReason reason : reasons) {
         ReinhardState state = new ReinhardState();
         FakeControl control = new FakeControl(true);
         assertTrue(GrabThrow.grab(state, candidate(true, true), control));
         UUID effect = control.acquired.get(0);
         // null = a mode switch / dimension change / control on Reinhard: the router cancels every action.
         if (reason == null) {
            ReinhardActions.onModeChange(null, state);
         } else {
            ReinhardActions.cleanup(null, state, reason);
         }

         assertEquals(List.of(TARGET + "/" + effect + "/" + GrabThrow.releaseReason(reason)), control.released,
            "one release with the grab's own effect id: " + reason);
         assertNull(state.grab, "no throw after a forced end: " + reason);
         assertFalse(state.action.running(), "no stuck pose: " + reason);
         assertEquals(240, state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()], "a forced release starts the cooldown: " + reason);
      }

      assertEquals(ReleaseReason.CASTER_DEATH, GrabThrow.releaseReason(CleanupReason.DEATH));
      assertEquals(ReleaseReason.CASTER_DISCONNECT, GrabThrow.releaseReason(CleanupReason.DISCONNECT));
      assertEquals(ReleaseReason.HERO_CHANGE, GrabThrow.releaseReason(CleanupReason.HERO_CHANGE));
      assertEquals(ReleaseReason.INTERRUPTED, GrabThrow.releaseReason(null));
      // The target's own end (death, logout, another dimension): the same single release.
      ReinhardState state = new ReinhardState();
      FakeControl control = new FakeControl(true);
      GrabThrow.grab(state, candidate(true, true), control);
      GrabThrow.release(state, ReleaseReason.TARGET_LOST);
      GrabThrow.release(state, ReleaseReason.TARGET_LOST);
      assertEquals(1, control.released.size(), "released once");
      assertTrue(control.released.get(0).endsWith("/" + ReleaseReason.TARGET_LOST));
   }

   @Test
   void oneEffectIdPerGrab() {
      ReinhardState state = new ReinhardState();
      FakeControl control = new FakeControl(true);
      GrabThrow.grab(state, candidate(true, true), control);
      GrabThrow.throwNow(state);
      state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()] = 0;
      state.action.cancel();
      GrabThrow.grab(state, candidate(true, true), control);
      assertEquals(2, control.acquired.size());
      assertNotEquals(control.acquired.get(0), control.acquired.get(1), "a fresh UUID per grab");
      assertEquals(control.acquired.get(1), state.grab.effectId());
   }

   @Test
   void refusesUncontrollable() {
      for (GrabThrow.Candidate c : new GrabThrow.Candidate[]{null, candidate(false, true), candidate(true, false)}) {
         ReinhardState state = new ReinhardState();
         FakeControl control = new FakeControl(true);
         assertFalse(GrabThrow.grab(state, c, control), "Lion's Heart / anchored / nobody: refused");
         assertTrue(control.acquired.isEmpty(), "the control is never asked");
         assertNull(state.grab);
         assertFalse(state.action.running());
         assertEquals(0, state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()], "no cooldown on a refusal");
      }

      ReinhardState state = new ReinhardState();
      FakeControl busy = new FakeControl(false);
      assertFalse(GrabThrow.grab(state, candidate(true, true), busy), "already controlled by someone else");
      assertNull(state.grab);
      assertEquals(0, state.cooldowns[ReinhardAbility.GRAB_THROW.ordinal()]);
   }
}
