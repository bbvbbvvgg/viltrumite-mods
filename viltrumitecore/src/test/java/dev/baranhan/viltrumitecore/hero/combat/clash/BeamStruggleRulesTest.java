package dev.baranhan.viltrumitecore.hero.combat.clash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.network.packet.ClashStateS2CPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Pure rules of the struggle runtime: report validation, aim-away check, power clamp, pressure, the wire format. */
class BeamStruggleRulesTest {
   private static ClashBeam beam(Vec3 origin, Vec3 direction, double range, float power) {
      return ClashBeam.of("test:beam", null, origin, direction, range, power);
   }

   @Test
   void validRejectsBrokenReports() {
      Vec3 o = Vec3.ZERO;
      Vec3 x = new Vec3(1.0, 0.0, 0.0);
      assertTrue(ClashBeams.valid(beam(o, x, 20.0, 1.0F)));
      assertFalse(ClashBeams.valid(beam(o, Vec3.ZERO, 20.0, 1.0F)), "zero direction");
      assertFalse(ClashBeams.valid(beam(new Vec3(Double.NaN, 0.0, 0.0), x, 20.0, 1.0F)), "NaN origin");
      assertFalse(ClashBeams.valid(beam(o, x, 0.5, 1.0F)), "too short");
      assertFalse(ClashBeams.valid(beam(o, x, Double.POSITIVE_INFINITY, 1.0F)), "infinite range");
      assertFalse(ClashBeams.valid(beam(o, x, 20.0, 0.0F)), "no power");
      assertFalse(ClashBeams.valid(beam(o, x, 20.0, Float.NaN)), "NaN power");
   }

   @Test
   void beamHoldsWhileItRunsThroughThePoint() {
      Vec3 point = new Vec3(10.0, 0.0, 0.0);
      assertTrue(BeamStruggles.holds(beam(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 20.0, 1.0F), point));
      assertTrue(BeamStruggles.holds(beam(Vec3.ZERO, new Vec3(1.0, 0.2, 0.0), 20.0, 1.0F), point), "a small wobble keeps it");
      assertFalse(BeamStruggles.holds(beam(Vec3.ZERO, new Vec3(1.0, 0.4, 0.0), 20.0, 1.0F), point), "aimed away");
      assertFalse(BeamStruggles.holds(beam(Vec3.ZERO, new Vec3(-1.0, 0.0, 0.0), 20.0, 1.0F), point), "turned around");
      assertFalse(BeamStruggles.holds(beam(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 8.0, 1.0F), point), "out of range");
      assertTrue(BeamStruggles.holds(beam(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 9.0, 1.0F), point), "contact radius");
   }

   @Test
   void powerIsClampedForOutcomes() {
      assertEquals(BeamStruggles.MIN_POWER, BeamStruggles.power(beam(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 20.0, 0.01F)));
      assertEquals(BeamStruggles.MAX_POWER, BeamStruggles.power(beam(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 20.0, 99.0F)));
      assertEquals(1.5F, BeamStruggles.power(beam(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 20.0, 1.5F)));
   }

   @Test
   void playerAndNpcPressureMatchTheDesign() {
      int beat = ClashRules.GRACE_TICKS + ClashRules.BEAT_PERIOD;
      MashMeter player = new MashMeter();
      for (int tick = beat - 9; tick <= beat; tick++) {
         player.pulse(tick, ClashRules.GRACE_TICKS, 0);
      }

      assertTrue(player.surging(beat), "the last pulse landed on the beat");
      double npc = new MashMeter().pressure(beat, 1.0) + 1.0 * ClashRules.AUTO_PRESSURE;
      double idle = new MashMeter().pressure(beat, 1.0);
      assertTrue(player.pressure(beat, 1.0) > npc, "a player mashing on the beat beats an NPC");
      assertTrue(idle < npc, "an idle player loses to an NPC");
   }

   @Test
   void statePacketRoundTrips() {
      String longId = "x".repeat(90);
      ClashStateS2CPacket out = new ClashStateS2CPacket(7, ClashStateS2CPacket.RUNNING, 12, -1, new Vec3(1.5, 64.0, -3.25),
         new Vec3(30000000.25, 70.0, 8.0), 0.37F, 55, ClashStateS2CPacket.FLAG_SURGE_A | ClashStateS2CPacket.FLAG_GRACE, 9, 300,
         "homelander:eye_lasers", longId);
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      out.encode(buf);
      ClashStateS2CPacket in = new ClashStateS2CPacket(buf);
      assertEquals(0, buf.readableBytes());
      assertEquals(7, in.id);
      assertTrue(in.running());
      assertEquals(12, in.entityA);
      assertEquals(-1, in.entityB);
      assertEquals(30000000.25, in.originB.x);
      assertEquals(0.37F, in.point);
      assertEquals(55, in.age);
      assertTrue(in.flag(ClashStateS2CPacket.FLAG_SURGE_A));
      assertFalse(in.flag(ClashStateS2CPacket.FLAG_SURGE_B));
      assertTrue(in.flag(ClashStateS2CPacket.FLAG_GRACE));
      assertEquals(9, in.pulsesA);
      assertEquals(127, in.pulsesB);
      assertEquals("homelander:eye_lasers", in.beamA);
      assertEquals(ClashStateS2CPacket.MAX_BEAM_ID, in.beamB.length());
   }
}
