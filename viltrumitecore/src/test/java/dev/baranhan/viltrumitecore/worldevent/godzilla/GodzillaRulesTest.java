package dev.baranhan.viltrumitecore.worldevent.godzilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules.Cooldowns;
import dev.baranhan.viltrumitecore.worldevent.godzilla.GodzillaRules.Zone;
import dev.baranhan.viltrumitecore.worldevent.godzilla.net.GodzillaFxS2CPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Pure rules of the kaiju: damage caps, breath staging, gait, tail whip, target zones, move choice and budgets. */
class GodzillaRulesTest {
   private static final float H = GodzillaRules.HEIGHT;
   private static final Cooldowns READY = new Cooldowns(0, 0, 0, 0, 0, 0);
   private static final Cooldowns BUSY = new Cooldowns(99, 99, 99, 99, 99, 99);

   @Test
   void theModelIsScaledToTheEventHeight() {
      assertEquals(GodzillaRules.HEIGHT, GodzillaRules.MODEL_HEIGHT * GodzillaRules.modelScale(), 1.0E-3F);
      assertTrue(GodzillaRules.HEIGHT >= 100.0F, "towers of Atheria reach ~100 blocks: the kaiju must stand over them");
   }

   @Test
   void hitsAreCappedAndScaledByTheZone() {
      assertEquals(0.0F, GodzillaRules.hit(Float.NaN, 1.0F, false));
      assertEquals(0.0F, GodzillaRules.hit(-5.0F, 1.0F, false));
      assertEquals(10.0F * 1.35F, GodzillaRules.hit(10.0F, 1.35F, false), 1.0E-4F);
      assertEquals(GodzillaRules.MAX_HIT * 0.5F, GodzillaRules.hit(10000.0F, 0.5F, false), 1.0E-3F);
      assertEquals(10000.0F, GodzillaRules.hit(10000.0F, 0.5F, true), "kill commands pass untouched");
      assertEquals(0.0F, GodzillaRules.allowance(GodzillaRules.MAX_DAMAGE_PER_TICK + 5.0F, 50.0F));
      assertEquals(20.0F, GodzillaRules.allowance(GodzillaRules.MAX_DAMAGE_PER_TICK - 20.0F, 50.0F), 1.0E-4F);
      assertEquals(GodzillaRules.PARTS, GodzillaRules.PART_SIZE.length);
      assertEquals(GodzillaRules.PARTS, GodzillaRules.PART_FACTOR.length);
      float best = 0.0F;
      for (float f : GodzillaRules.PART_FACTOR) {
         best = Math.max(best, f);
      }
      assertEquals(GodzillaRules.PART_FACTOR[GodzillaRules.P_HEAD], best, "the head is the weak spot");
      float tail = 0.0F;
      for (float l : GodzillaRules.TAIL_LENGTH) {
         tail += l;
      }
      assertEquals(1.35F, tail, 0.01F);
   }

   @Test
   void theBreathChargesFromTheTailToTheMouth() {
      assertEquals(0, GodzillaRules.chargeStage(0.0F));
      assertEquals(1, GodzillaRules.chargeStage(GodzillaRules.CHARGE_BRACE));
      assertEquals(2, GodzillaRules.chargeStage(GodzillaRules.CHARGE_IGNITE));
      assertEquals(3, GodzillaRules.chargeStage(GodzillaRules.CHARGE_SPINE));
      float last = -1.0F;
      for (int t = 0; t <= GodzillaRules.CHARGE_TICKS; t++) {
         float f = GodzillaRules.glowFront(t);
         assertTrue(f >= last - 1.0E-6F, "the glow front never runs back (t=" + t + ")");
         last = f;
         assertTrue(GodzillaRules.plateGlow(0.1F, f) >= GodzillaRules.plateGlow(0.9F, f), "tail plates light first (t=" + t + ")");
      }
      assertEquals(0.0F, GodzillaRules.glowFront(GodzillaRules.CHARGE_BRACE - 1));
      assertEquals(1.0F, GodzillaRules.glowFront(GodzillaRules.CHARGE_SPINE), 1.0E-4F);
      assertEquals(0.0F, GodzillaRules.plateGlow(0.5F, 0.0F));
      assertEquals(1.0F, GodzillaRules.plateGlow(0.1F, 0.9F), 1.0E-4F);
      assertEquals(0.0F, GodzillaRules.plateGlow(0.9F, 0.2F), 1.0E-4F);
      assertEquals(0.0F, GodzillaRules.mouthGlow(GodzillaRules.CHARGE_IGNITE), 1.0E-4F);
      assertEquals(1.0F, GodzillaRules.mouthGlow(GodzillaRules.CHARGE_TICKS), 1.0E-4F);
   }

   @Test
   void theBeamIsNeverEndless() {
      assertTrue(GodzillaRules.fireTicks(false, false) < GodzillaRules.fireTicks(true, false));
      assertTrue(GodzillaRules.fireTicks(true, false) < GodzillaRules.fireTicks(true, true));
      assertTrue(GodzillaRules.breathCooldown(true) < GodzillaRules.breathCooldown(false));
      for (boolean rage : new boolean[]{false, true}) {
         assertTrue(GodzillaRules.fireTicks(rage, rage) < GodzillaRules.breathCooldown(rage), "a pause longer than the beam");
      }
      assertTrue(GodzillaRules.beamTurn(true) > GodzillaRules.beamTurn(false));
      assertTrue(GodzillaRules.beamTurn(false) < GodzillaRules.HEAD_TURN, "the beam lags behind a fast target");
   }

   @Test
   void movesKeepTheirContactInsideTheClip() {
      for (GodzillaMove m : GodzillaMove.values()) {
         assertEquals(m, GodzillaMove.byKey(m.key()));
         if (m.contact >= 0 && m.length > 0) {
            assertTrue(m.contact <= m.contactEnd && m.contactEnd <= m.length, m + " contact " + m.contact + ".." + m.contactEnd + " / " + m.length);
            assertTrue(m.inContact(m.contact) && !m.inContact(m.contactEnd + 1), m.name());
         }
      }
      assertNull(GodzillaMove.byKey("no_such_move"));
      assertTrue(GodzillaMove.CHARGE.breath() && GodzillaMove.FIRE.breath() && GodzillaMove.BREATH_END.breath());
      assertFalse(GodzillaMove.ROAR.breath());
      for (GodzillaPhase p : GodzillaPhase.values()) {
         assertEquals(p, GodzillaPhase.byKey(p.key()));
      }
      assertTrue(GodzillaPhase.RAGE.enraged() && GodzillaPhase.FINALE.enraged() && !GodzillaPhase.COMBAT.enraged());
      assertTrue(GodzillaPhase.COMBAT.fighting() && !GodzillaPhase.EXPLORE.fighting());
   }

   @Test
   void footfallsAlternateOnTheGait() {
      assertEquals(0, GodzillaRules.footfall(0.10, 0.20));
      assertEquals(2, GodzillaRules.footfall(0.45, 0.55), "half cycle: right foot");
      assertEquals(1, GodzillaRules.footfall(0.95, 1.05), "whole cycle: left foot");
      assertTrue(GodzillaRules.gaitAdvance(1.0, 0.0F) > 0.0F);
      assertTrue(GodzillaRules.gaitAdvance(0.0, 10.0F) > 0.0F, "turning on the spot steps too");
      assertEquals(-GodzillaRules.footForward(0.0, true), GodzillaRules.footForward(0.0, false), 1.0E-5F);
      assertTrue(GodzillaRules.speed(true, false) > GodzillaRules.speed(false, false));
      assertTrue(GodzillaRules.turnRate(false, false) != GodzillaRules.turnRate(true, false));
   }

   @Test
   void theTailWhipRunsDownTheTail() {
      // the first segment is already winding up while the tip still rests
      assertTrue(Math.abs(GodzillaRules.tailStrike(0, 6.0F, 1)) > 0.0F);
      assertEquals(0.0F, GodzillaRules.tailStrike(6, 6.0F, 1));
      // the swing goes to the strike side, mirrored for the other one
      assertTrue(GodzillaRules.tailStrike(3, 40.0F, 1) > 0.0F);
      assertEquals(-GodzillaRules.tailStrike(3, 40.0F, 1), GodzillaRules.tailStrike(3, 40.0F, -1), 1.0E-5F);
      assertTrue(GodzillaRules.hipsTwist(38.0F, 1) > 30.0F);
      assertEquals(0.0F, GodzillaRules.hipsTwist(GodzillaMove.TAIL.length + 30, 1), 1.0E-4F);
      assertEquals(0.0F, GodzillaRules.tailSway(3, 0.3, 0.0F), "a standing kaiju keeps its tail still");
      assertTrue(Math.abs(GodzillaRules.tailSway(6, 0.3, 1.0F)) > Math.abs(GodzillaRules.tailSway(0, 0.3, 1.0F)) * 0.5F);
   }

   @Test
   void targetsAreClassifiedAroundTheBody() {
      assertEquals(Zone.FEET, GodzillaRules.classify(2.0, 1.0, 5.0));
      assertEquals(Zone.FRONT, GodzillaRules.classify(0.0, 0.4 * H, 0.35 * H));
      assertEquals(Zone.SIDE_L, GodzillaRules.classify(0.35 * H, 0.4 * H, 0.05 * H));
      assertEquals(Zone.SIDE_R, GodzillaRules.classify(-0.35 * H, 0.4 * H, 0.05 * H));
      assertEquals(Zone.BEHIND, GodzillaRules.classify(0.0, 0.1 * H, -0.8 * H));
      assertEquals(Zone.AIR_NEAR, GodzillaRules.classify(0.0, 0.8 * H, 0.3 * H));
      assertEquals(Zone.AIR_FAR, GodzillaRules.classify(0.0, 0.9 * H, 1.2 * H));
      assertEquals(Zone.FAR, GodzillaRules.classify(0.0, 0.0, 2.0 * H));
   }

   @Test
   void theMoveFitsTheZone() {
      assertEquals(GodzillaMove.STOMP, GodzillaRules.choose(Zone.FEET, READY, false, 0.0F, 1.0F));
      assertEquals(GodzillaMove.TAIL, GodzillaRules.choose(Zone.BEHIND, READY, false, 0.0F, 1.0F));
      assertEquals(GodzillaMove.CLAW_R, GodzillaRules.choose(Zone.FRONT, READY, false, 0.0F, 1.0F));
      assertEquals(GodzillaMove.CHARGE, GodzillaRules.choose(Zone.FRONT, READY, false, 200.0F, 1.0F), "a dangerous hero gets the breath");
      assertEquals(GodzillaMove.CLAW_L, GodzillaRules.choose(Zone.SIDE_L, READY, false, 0.0F, 1.0F));
      assertEquals(GodzillaMove.CHARGE, GodzillaRules.choose(Zone.FAR, READY, false, 0.0F, 1.0F));
      assertEquals(GodzillaMove.CHARGE, GodzillaRules.choose(Zone.AIR_FAR, READY, false, 0.0F, 1.0F), "fliers far away get the breath");
      assertEquals(GodzillaMove.NONE, GodzillaRules.choose(Zone.FRONT, BUSY, false, 0.0F, 1.0F));
      assertEquals(GodzillaMove.NONE, GodzillaRules.choose(Zone.NONE, READY, true, 500.0F, 0.1F));
      assertTrue(GodzillaRules.cooldown(GodzillaRules.TAIL_COOLDOWN, true) < GodzillaRules.TAIL_COOLDOWN);
   }

   @Test
   void budgetsAndFallCurvesStayInRange() {
      assertTrue(GodzillaRules.blockBudget(true) > GodzillaRules.blockBudget(false));
      assertEquals(0.2F, GodzillaRules.severity(0), 1.0E-5F);
      assertEquals(1.0F, GodzillaRules.severity(100000), 1.0E-5F);
      assertEquals(1.0F, GodzillaRules.falloff(0.0, 10.0), 1.0E-5F);
      assertEquals(0.0F, GodzillaRules.falloff(12.0, 10.0), 1.0E-5F);
      assertEquals(0.0F, GodzillaRules.fallTilt(GodzillaMove.FALL, 0.0F), 1.0E-5F);
      assertEquals(1.0F, GodzillaRules.fallTilt(GodzillaMove.FALL, GodzillaMove.FALL.length), 1.0E-5F);
      assertEquals(1.0F, GodzillaRules.fallTilt(GodzillaMove.RISE, 0.0F), 1.0E-5F);
      assertEquals(0.0F, GodzillaRules.fallTilt(GodzillaMove.RISE, GodzillaMove.RISE.length), 1.0E-5F);
      assertEquals(0.0F, GodzillaRules.fallTilt(GodzillaMove.ROAR, 30.0F));
      assertTrue(GodzillaRules.FX_NEAR < GodzillaRules.FX_FAR);
   }

   @Test
   void fxPacketsSurviveTheWire() {
      GodzillaFxS2CPacket p = new GodzillaFxS2CPacket(GodzillaFxS2CPacket.BEAM_BLAST, 10.5, -51.0, 2048.25, 0.25F, -1.0F, 0.5F, 1.75F, 7);
      FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
      p.toBytes(b);
      assertEquals(p, new GodzillaFxS2CPacket(b));
      assertEquals(0, b.readableBytes());
      assertTrue(GodzillaFxS2CPacket.LAST >= GodzillaFxS2CPacket.STEP);
   }
}