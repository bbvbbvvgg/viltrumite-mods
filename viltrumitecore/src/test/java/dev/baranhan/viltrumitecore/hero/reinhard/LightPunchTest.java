package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.ability.LightPunch;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.Strikes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Spec §6.1 Light Punch (stage 3 Task 2). */
class LightPunchTest {
   @Test
   void damageIsFour() {
      assertEquals(4.0F, ReinhardRules.LIGHT_PUNCH_DAMAGE);
      assertEquals(ReinhardRules.LIGHT_PUNCH_DAMAGE, LightPunch.damage());
      assertEquals(3.5, ReinhardRules.LIGHT_PUNCH_REACH);
   }

   @Test
   void knockbackIsPointSix() {
      assertEquals(0.6, ReinhardRules.LIGHT_PUNCH_KNOCKBACK);
      // Along the look, horizontal only, whatever the pitch.
      Vec3 push = Strikes.push(new Vec3(0.0, -0.8, 0.6), ReinhardRules.LIGHT_PUNCH_KNOCKBACK);
      assertEquals(0.6, push.length(), 1.0E-9);
      assertEquals(0.0, push.y, 1.0E-9);
      assertTrue(push.z > 0.0);
      // Straight down: no direction, no push.
      assertEquals(0.0, Strikes.push(new Vec3(0.0, -1.0, 0.0), 0.6).length(), 1.0E-9);
      assertTrue(ReinhardRules.LIGHT_PUNCH_KNOCKBACK <= 0.6, "Restrained never pushes hard");
   }

   @Test
   void hitOnTick3() {
      assertEquals(8, ReinhardRules.LIGHT_PUNCH_TICKS);
      assertEquals(3, ReinhardRules.LIGHT_PUNCH_HIT_TICK);
      // Router order: the ability ticks, then the timeline advances.
      ActionTimeline timeline = new ActionTimeline();
      timeline.start(ReinhardAbility.LIGHT_PUNCH, ReinhardRules.LIGHT_PUNCH_TICKS, -1);
      List<Integer> hits = new ArrayList<>();
      int tick = 0;
      while (timeline.running()) {
         if (LightPunch.hitsAt(timeline.elapsed())) {
            hits.add(tick);
         }

         timeline.tick();
         tick++;
      }

      assertEquals(List.of(3), hits, "exactly one hit, on tick 3");
      assertEquals(ReinhardRules.LIGHT_PUNCH_TICKS, tick, "the action lock is the clip length");
   }

   @Test
   void noDestruction() {
      assertEquals(DestructionLevel.NONE, LightPunch.DESTRUCTION);
      assertEquals(DestructionLevel.of(ReinhardAbility.LIGHT_PUNCH.mode()), LightPunch.DESTRUCTION);
      assertFalse(LightPunch.DESTRUCTION.mayBreak());
   }

   @Test
   void routedThroughTheRouter() {
      assertInstanceOf(LightPunch.class, ReinhardActions.handler(ReinhardAbility.LIGHT_PUNCH));
   }
}
