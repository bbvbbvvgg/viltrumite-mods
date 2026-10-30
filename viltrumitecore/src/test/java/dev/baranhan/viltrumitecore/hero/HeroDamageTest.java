package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.HeroDamage.DamageKind;
import dev.baranhan.viltrumitecore.hero.HeroDamage.DamageResult;
import org.junit.jupiter.api.Test;

/**
 * Shared hero damage routing (spec 13-14): control queue before Lion block,
 * deferred release never re-queues, internal damage never blocked, the
 * attacker record and the one-per-session totem boundary.
 */
class HeroDamageTest {

   @Test
   void externalHitOnAnchoredTargetQueues() {
      assertEquals(DamageResult.QUEUED, HeroDamage.decide(DamageKind.EXTERNAL, true, true, false, 10.0F));
   }

   @Test
   void absoluteDamageIsNeverBankedUnderControl() {
      assertTrue(HeroDamage.queuesUnderControl(true, false), "ordinary hits bank while frozen");
      assertFalse(HeroDamage.queuesUnderControl(true, true), "/kill and the void land on a frozen victim");
      assertFalse(HeroDamage.queuesUnderControl(false, false));
   }

   @Test
   void internalDamageQueuesUnderAnchorToo() {
      assertEquals(DamageResult.QUEUED, HeroDamage.decide(DamageKind.INTERNAL, true, true, false, 0.6F), "blood price still queues under anchor");
   }

   @Test
   void deferredReleaseNeverRequeues() {
      assertEquals(
         DamageResult.PASS,
         HeroDamage.decide(DamageKind.DEFERRED_RELEASE, true, true, false, 5.0F),
         "a dome payout must not bounce back into the queue"
      );
   }

   @Test
   void lionBlocksExternalButNotInternal() {
      assertEquals(DamageResult.BLOCKED, HeroDamage.decide(DamageKind.EXTERNAL, true, false, true, 10.0F));
      assertEquals(DamageResult.PASS, HeroDamage.decide(DamageKind.INTERNAL, true, false, true, 0.6F), "Lion does not shield hero-internal damage");
   }

   @Test
   void cleanHitPassesThrough() {
      assertEquals(DamageResult.PASS, HeroDamage.decide(DamageKind.EXTERNAL, true, false, false, 8.0F));
   }

   @Test
   void deadOrZeroAmountPasses() {
      assertEquals(DamageResult.PASS, HeroDamage.decide(DamageKind.EXTERNAL, false, true, true, 8.0F));
      assertEquals(DamageResult.PASS, HeroDamage.decide(DamageKind.EXTERNAL, true, true, true, 0.0F));
   }

   @Test
   void attackerCaptureCoversLivingExternalHitsOnly() {
      assertTrue(HeroDamage.capturesAttacker(DamageKind.EXTERNAL, true, false), "a living attacker is recorded even when blocked");
      assertFalse(HeroDamage.capturesAttacker(DamageKind.INTERNAL, true, false), "internal damage has no attacker");
      assertFalse(HeroDamage.capturesAttacker(DamageKind.EXTERNAL, false, false), "environment damage does not count");
      assertFalse(HeroDamage.capturesAttacker(DamageKind.EXTERNAL, true, true), "self damage cannot arm the counter");
   }

   @Test
   void totemBoundaryIsRegulusOnlyOncePerSession() {
      assertTrue(HeroDamage.totemEligible(true, false, false), "regulus survives a normal lethal hit");
      assertFalse(HeroDamage.totemEligible(false, false, false), "non-regulus never consumes the hero totem");
      assertFalse(HeroDamage.totemEligible(true, true, false), "void and /kill bypass the totem");
      assertFalse(HeroDamage.totemEligible(true, false, true), "consumed once per life");
   }
}
