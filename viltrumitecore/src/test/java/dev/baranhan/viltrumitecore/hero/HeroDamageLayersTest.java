package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure damage-layer pipeline with fake hero layers (no world). */
class HeroDamageLayersTest {
   private static final float EPS = 1.0E-5F;

   /** Fake hero: shield -> mark durability -> HP floor; counts every call. */
   private static final class FakeHero implements HeroDamageLayers.Target, HeroDamageLayers.Attacker {
      boolean shieldUp;
      float mark;
      float hpFloorCap = Float.MAX_VALUE;
      float outgoingMul = 1.0F;
      float reduceTo = -1.0F;
      int absorbCalls;
      int clampCalls;
      int outgoingCalls;

      @Override
      public DamageAbsorb absorbIncoming(float raw) {
         this.absorbCalls++;
         if (this.shieldUp) {
            return DamageAbsorb.ABSORBED;
         }

         if (this.mark > 0.0F) {
            // A breaking hit is absorbed fully, the rest does not spill.
            this.mark = Math.max(0.0F, this.mark - raw);
            return DamageAbsorb.ABSORBED;
         }

         return this.reduceTo >= 0.0F ? DamageAbsorb.reduceTo(this.reduceTo) : DamageAbsorb.PASS;
      }

      @Override
      public float clampFinalDamage(float afterArmor) {
         this.clampCalls++;
         return Math.min(afterArmor, this.hpFloorCap);
      }

      @Override
      public float modifyOutgoingDamage(float amount) {
         this.outgoingCalls++;
         return amount * this.outgoingMul;
      }
   }

   @Test
   void noLayersMeansVanilla() {
      DamageAbsorb attack = HeroDamageLayers.attack(HeroDamageLayers.NO_LAYERS, false, false, 7.0F);
      assertFalse(attack.absorbed());
      assertEquals(7.0F, HeroDamageLayers.passOn(attack, 7.0F), EPS);
      assertEquals(1.0F, HeroDamageLayers.hurtScale(attack, 7.0F), EPS);
      assertEquals(5.5F, HeroDamageLayers.finalDamage(HeroDamageLayers.NO_LAYERS, false, 5.5F), EPS);
      assertEquals(4.0F, HeroDamageLayers.outgoing(HeroDamageLayers.NO_ATTACKER, 4.0F), EPS);
      assertEquals(9.0F, HeroDamageLayers.clean(HeroDamageLayers.NO_LAYERS, HeroDamageLayers.NO_ATTACKER, false, 9.0F), EPS);
   }

   @Test
   void absorbedCancelsWholeHit() {
      FakeHero hero = new FakeHero();
      hero.shieldUp = true;
      assertTrue(HeroDamageLayers.attack(hero, false, false, 12.0F).absorbed());
      assertEquals(0.0F, HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, false, 12.0F), EPS);
   }

   @Test
   void breakingHitDoesNotSpill() {
      FakeHero hero = new FakeHero();
      hero.mark = 3.0F;
      assertTrue(HeroDamageLayers.attack(hero, false, false, 20.0F).absorbed());
      assertEquals(0.0F, hero.mark, EPS);
      // Next hit reaches Tony.
      assertFalse(HeroDamageLayers.attack(hero, false, false, 20.0F).absorbed());
   }

   @Test
   void partialReductionScalesTheHit() {
      FakeHero hero = new FakeHero();
      hero.reduceTo = 3.0F;
      DamageAbsorb attack = HeroDamageLayers.attack(hero, false, false, 12.0F);
      assertFalse(attack.absorbed());
      assertEquals(3.0F, HeroDamageLayers.passOn(attack, 12.0F), EPS);
      assertEquals(0.25F, HeroDamageLayers.hurtScale(attack, 12.0F), EPS);
      // A layer can never raise the hit.
      hero.reduceTo = 50.0F;
      assertEquals(12.0F, HeroDamageLayers.passOn(HeroDamageLayers.attack(hero, false, false, 12.0F), 12.0F), EPS);
   }

   @Test
   void clampAppliesAfterArmor() {
      FakeHero hero = new FakeHero();
      hero.hpFloorCap = 2.0F;
      assertEquals(2.0F, HeroDamageLayers.finalDamage(hero, false, 6.0F), EPS);
      assertEquals(1.0F, HeroDamageLayers.finalDamage(hero, false, 1.0F), EPS);
   }

   @Test
   void cleanPathRunsLayersOnce() {
      FakeHero hero = new FakeHero();
      hero.hpFloorCap = 4.0F;
      assertEquals(4.0F, HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, false, 10.0F), EPS);
      assertEquals(1, hero.absorbCalls);
      assertEquals(1, hero.clampCalls);
   }

   @Test
   void deferredPayoutChargedOnce() {
      FakeHero hero = new FakeHero();
      hero.mark = 100.0F;
      // While anchored, routing queues the hit before any layer runs.
      assertEquals(HeroDamage.DamageResult.QUEUED, HeroDamage.decide(HeroDamage.DamageKind.EXTERNAL, true, true, false, 10.0F));
      assertEquals(0, hero.absorbCalls);
      // The payout goes through the clean path once.
      HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, false, 10.0F);
      assertEquals(1, hero.absorbCalls);
      assertEquals(90.0F, hero.mark, EPS);
   }

   @Test
   void outgoingModifiedOnce() {
      FakeHero attacker = new FakeHero();
      attacker.outgoingMul = 2.0F;
      assertEquals(10.0F, HeroDamageLayers.outgoing(attacker, 5.0F), EPS);
      assertEquals(1, attacker.outgoingCalls);
      assertEquals(10.0F, HeroDamageLayers.clean(HeroDamageLayers.NO_LAYERS, attacker, false, 5.0F), EPS);
      assertEquals(2, attacker.outgoingCalls);
   }

   @Test
   void otherSourcesStillLethal() {
      FakeHero hero = new FakeHero();
      assertEquals(40.0F, HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, false, 40.0F), EPS);
      assertEquals(40.0F, HeroDamageLayers.finalDamage(hero, false, 40.0F), EPS);
   }

   @Test
   void voidNeverAbsorbed() {
      FakeHero hero = new FakeHero();
      hero.shieldUp = true;
      hero.hpFloorCap = 1.0F;
      assertFalse(HeroDamageLayers.attack(hero, true, false, 1000.0F).absorbed());
      assertEquals(1000.0F, HeroDamageLayers.finalDamage(hero, true, 1000.0F), EPS);
      assertEquals(1000.0F, HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, true, 1000.0F), EPS);
      assertEquals(0, hero.absorbCalls);
      assertEquals(0, hero.clampCalls);
   }

   @Test
   void invulnerabilityFramesSkipLayers() {
      FakeHero hero = new FakeHero();
      hero.mark = 100.0F;
      assertFalse(HeroDamageLayers.attack(hero, false, true, 5.0F).absorbed());
      assertEquals(0, hero.absorbCalls);
      assertTrue(HeroDamageLayers.inInvulnerabilityFrames(15, 6.0F, 5.0F));
      assertFalse(HeroDamageLayers.inInvulnerabilityFrames(15, 4.0F, 5.0F));
      assertFalse(HeroDamageLayers.inInvulnerabilityFrames(10, 6.0F, 5.0F));
   }

   /** Fake hero with an unblockable HP floor (Reinhard-like); counts hook calls. */
   private static final class FloorHero implements HeroDamageLayers.Target {
      final float health;
      int unblockableCalls;

      FloorHero(float health) {
         this.health = health;
      }

      @Override
      public DamageAbsorb absorbIncoming(float raw) {
         return DamageAbsorb.PASS;
      }

      @Override
      public float clampFinalDamage(float afterArmor) {
         return afterArmor;
      }

      @Override
      public float clampUnblockable(float amount) {
         this.unblockableCalls++;
         return Math.max(0.0F, Math.min(amount, this.health - 1.0F));
      }
   }

   @Test
   void unblockableDefaultUnchanged() {
      assertEquals(50.0F, HeroDamageLayers.finalDamage(HeroDamageLayers.NO_LAYERS, true, false, 50.0F), EPS);
      assertEquals(Float.MAX_VALUE, HeroDamageLayers.finalDamage(HeroDamageLayers.NO_LAYERS, true, false, Float.MAX_VALUE), EPS);
      assertEquals(50.0F, HeroDamageLayers.clean(HeroDamageLayers.NO_LAYERS, HeroDamageLayers.NO_ATTACKER, true, false, 50.0F), EPS);
   }

   @Test
   void unblockableHookFloors() {
      FloorHero hero = new FloorHero(20.0F);
      assertEquals(19.0F, HeroDamageLayers.finalDamage(hero, true, false, Float.MAX_VALUE), EPS);
      assertEquals(19.0F, HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, true, false, 4000.0F), EPS);
      assertEquals(2, hero.unblockableCalls);
   }

   @Test
   void adminKillSkipsHook() {
      FloorHero hero = new FloorHero(20.0F);
      assertEquals(Float.MAX_VALUE, HeroDamageLayers.finalDamage(hero, true, true, Float.MAX_VALUE), EPS);
      assertEquals(4000.0F, HeroDamageLayers.clean(hero, HeroDamageLayers.NO_ATTACKER, true, true, 4000.0F), EPS);
      assertEquals(0, hero.unblockableCalls);
   }

   @Test
   void nonBypassPathIgnoresUnblockableHook() {
      FloorHero hero = new FloorHero(20.0F);
      assertEquals(30.0F, HeroDamageLayers.finalDamage(hero, false, false, 30.0F), EPS);
      assertEquals(0, hero.unblockableCalls);
   }
}
