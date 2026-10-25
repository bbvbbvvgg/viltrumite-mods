package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ProjectileMath;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.CatchWindow;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.ReflectWindow;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Spec §6.2 Reflect Projectile (stage 3 Task 10). */
class ReflectWindowTest {
   @Test
   void windowTwentyTicks() {
      assertEquals(20, ReinhardRules.REFLECT_WINDOW);
      ReinhardState state = new ReinhardState();
      ReflectWindow.open(state);
      assertTrue(ReinhardFlags.has(ReinhardHero.stateFlags(state, 0), ReinhardFlags.REFLECT_WINDOW), "the window is synced as a flag");
      int open = 0;
      while (ReflectWindow.isOpen(state) && open <= 100) {
         open++;
         ReflectWindow.tickWindow(state);
         state.action.tick();
      }

      assertEquals(20, open);
      assertFalse(ReinhardFlags.has(ReinhardHero.stateFlags(state, 0), ReinhardFlags.REFLECT_WINDOW));
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.projectileResponse(state), "after the window: the passive again");
      ReflectWindow.open(state);
      ReflectWindow.close(state);
      assertEquals(ProjectileResponse.DEFLECT, ReinhardHero.projectileResponse(state), "a mode switch / cleanup closes it at once");
   }

   @Test
   void cooldownEightyFromPress() {
      assertEquals(80, ReinhardRules.REFLECT_COOLDOWN);
      ReinhardState state = new ReinhardState();
      ReflectWindow.open(state);
      assertEquals(80, state.cooldowns[ReinhardAbility.REFLECT.ordinal()], "the cooldown starts at the press");
      assertTrue(state.action.is(ReinhardAbility.REFLECT));
      ReflectWindow.tickWindow(state);
      state.tick();
      ReflectWindow.snap(state);
      assertEquals(79, state.cooldowns[ReinhardAbility.REFLECT.ordinal()], "a reflection does not restart it");
      assertEquals(ActionTimeline.PHASE_SNAP, state.action.phase(), "the swat plays");
      assertTrue(state.action.length() >= ReinhardRules.REFLECT_SNAP_TICKS);
   }

   @Test
   void reflectReturnsToShooter() {
      ReinhardState state = new ReinhardState();
      ReflectWindow.open(state);
      assertEquals(ProjectileResponse.REFLECT, ReinhardHero.projectileResponse(state));
      CatchWindow.open(state);
      assertEquals(ProjectileResponse.REFLECT, ReinhardHero.projectileResponse(state), "reflect beats catch");

      // An arrow at speed 3 hitting him at the origin goes back to the skeleton's eyes, same speed.
      Vec3 v = new Vec3(3.0, 0.0, 0.0);
      Vec3 eye = new Vec3(-12.0, 1.6, 0.0);
      Vec3 back = ProjectileMath.reflected(v, Vec3.ZERO, eye);
      assertEquals(3.0, back.length(), 1.0E-9);
      assertEquals(0.0, back.normalize().subtract(eye.normalize()).length(), 1.0E-9);
      assertEquals(0.0, ProjectileMath.reflected(v, Vec3.ZERO, null).subtract(-3.0, 0.0, 0.0).length(), 1.0E-9, "no shooter: straight back");
   }
}
