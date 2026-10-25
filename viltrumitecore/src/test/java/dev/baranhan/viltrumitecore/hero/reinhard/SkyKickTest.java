package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroSuperJump;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.SkyKick;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec §6.2 Sky Kick (stage 3 Task 6). */
class SkyKickTest {
   @Test
   void apexAtLeast60() {
      assertTrue(HeroSuperJump.apex(ReinhardRules.KICK_VELOCITY) >= 60.0, "60+ blocks");
   }

   @Test
   void velocityWithinPacketCap() {
      // ClientboundSetEntityMotionPacket clamps every axis to ±3.9: more would fly only on the server.
      assertTrue(ReinhardRules.KICK_VELOCITY <= 3.9);
      assertEquals(0.0, SkyKick.launchVelocity().x);
      assertTrue(SkyKick.launchVelocity().z>2.9);
      assertEquals(40,Math.toDegrees(Math.atan2(SkyKick.launchVelocity().y,SkyKick.launchVelocity().horizontalDistance())),1E-6);
      assertEquals(ReinhardRules.KICK_VELOCITY,SkyKick.launchVelocity().length(),1E-6);
      assertTrue(Math.abs(ReinhardRules.KICK_TOSS_VELOCITY) <= 3.9);
   }

   @Test
   void timelineTossKickLaunch() {
      assertEquals(6, ReinhardRules.KICK_TOSS_TICKS);
      assertEquals(3, ReinhardRules.HIT_STOP_TICKS);
      ReinhardState state = new ReinhardState();
      assertTrue(SkyKick.start(state, true, true, 42));
      List<String> beats = new ArrayList<>();
      int tick = 0;
      while (state.action.running()) {
         SkyKick.Beat beat = SkyKick.beat(state.action.elapsed());
         if (beat != SkyKick.Beat.NONE) {
            beats.add(tick + ":" + beat);
         }

         state.action.tick();
         tick++;
      }

      assertEquals(List.of("0:TOSS", "6:KICK"), beats, "prepare, single contact and launch together; no later hold overwrites the impulse");
      assertEquals(ReinhardRules.KICK_ACTION_TICKS, tick);
   }

   @Test
   void technicalCooldown() {
      assertEquals(8, ReinhardRules.KICK_COOLDOWN);
      assertEquals(20.0F, ReinhardRules.KICK_DAMAGE);
      ReinhardState state = new ReinhardState();
      SkyKick.start(state, true, true, 7);
      assertEquals(8, state.cooldowns[ReinhardAbility.SKY_KICK.ordinal()]);
      assertTrue(state.action.is(ReinhardAbility.SKY_KICK));
      assertEquals(7, state.action.targetId(), "the target is synced (controlTargetId)");
   }

   @Test
   void impulseRefusedNoCooldown() {
      ReinhardState state = new ReinhardState();
      assertFalse(SkyKick.start(state, true, false, 7), "Lion's Heart / anchored target: no impulse, no kick");
      assertFalse(SkyKick.start(state, false, true, -1), "nobody in reach");
      assertEquals(0, state.cooldowns[ReinhardAbility.SKY_KICK.ordinal()]);
      assertFalse(state.action.running());
   }

   @Test
   void entityIdsSurviveTheFxPacket() {
      int[] ids = {0, 1, 65535, 65536, 123456789, Integer.MAX_VALUE};
      assertArrayEquals(ids, HeroFx.decodeIds(HeroFx.encodeIds(ids)));
      assertEquals(0, HeroFx.decodeIds(new float[0]).length);
   }
}
