package dev.baranhan.viltrumitecore.hero.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import org.junit.jupiter.api.Test;

class ControlManagerTest {

   @Test
   void freezeRecordDropsOnlyWhenProvablyGone() {
      // A resolved projectile keeps its freeze record.
      assertFalse(ControlManager.shouldDropFreezeRecord(true, true));
      assertFalse(ControlManager.shouldDropFreezeRecord(true, false));
      // An unloaded chunk keeps the record too: the projectile is still there
      // and re-resolves on reload, so its original gravity survives.
      assertFalse(ControlManager.shouldDropFreezeRecord(false, false));
      // Loaded chunk but no entity = removed or moved away; the record drops
      // and the gravity restore defers to the next entity join.
      assertTrue(ControlManager.shouldDropFreezeRecord(false, true));
   }

   // --- deferred queue cap math --------------------------------------------

   @Test
   void deferredQueueAccumulatesUpToTheCap() {
      // Freeze cap: 40% of max health (spec 8.3); dome cap: 35% (spec 9.2).
      assertEquals(8.0F, ControlRules.freezeDeferredCap(20.0F));
      assertEquals(7.0F, ControlRules.domeDeferredCap(20.0F));
      assertEquals(40.0F, ControlRules.freezeDeferredCap(100.0F));
      assertEquals(35.0F, ControlRules.domeDeferredCap(100.0F));
   }

   @Test
   void deferredAccumulateNeverCrossesTheCap() {
      float cap = ControlRules.freezeDeferredCap(100.0F);
      assertEquals(10.0F, ControlRules.deferredAccumulate(0.0F, 10.0F, cap));
      assertEquals(30.0F, ControlRules.deferredAccumulate(10.0F, 20.0F, cap));
      assertEquals(cap, ControlRules.deferredAccumulate(30.0F, 20.0F, cap));
      assertEquals(cap, ControlRules.deferredAccumulate(cap, 999.0F, cap));
      // Garbage in never produces negative queue.
      assertEquals(0.0F, ControlRules.deferredAccumulate(0.0F, -5.0F, cap));
   }

   // --- acquire precedence --------------------------------------------------

   @Test
   void aControlledTargetRejectsEveryNewAcquire() {
      for (ControlKind kind : ControlKind.values()) {
         assertFalse(ControlManager.mayAcquire(kind, true, false, true), kind + " must not stack controls");
      }
   }

   @Test
   void aGrabbedTargetRejectsEveryKind() {
      // Precedence: the legacy viltrumite grab owns the victim outright.
      for (ControlKind kind : ControlKind.values()) {
         assertFalse(ControlManager.mayAcquire(kind, false, true, true), kind + " must refuse grab-tagged targets");
      }
   }

   @Test
   void heroPolicyAndFreeTargets() {
      for (ControlKind kind : ControlKind.values()) {
         assertTrue(ControlManager.mayAcquire(kind, false, false, true));
         assertFalse(ControlManager.mayAcquire(kind, false, false, false));
      }
   }

   // --- anchored targets -----------------------------------------------------

   @Test
   void anchorKindsStripTheVictimsActions() {
      // Spec 8.3/9.2: FREEZE and STASIS pin the target AND deny its own attacks,
      // items and abilities; PULL never does (spec 8.2), nor do the policy kinds.
      assertTrue(ControlManager.deniesActions(ControlKind.FREEZE));
      assertTrue(ControlManager.deniesActions(ControlKind.STASIS));
      assertFalse(ControlManager.deniesActions(ControlKind.PULL), "a pulled victim keeps its own actions");
      assertFalse(ControlManager.deniesActions(ControlKind.IMPULSE));
      assertFalse(ControlManager.deniesActions(ControlKind.VILTRUMITE_GRAB));
   }

   // --- caster cleanup ------------------------------------------------------

   @Test
   void casterDeathKeepsDomeLinkedStasisOnly() {
      // Spec 9.2: a dead caster's dome lives its full duration, victims included.
      assertFalse(ControlManager.releaseOnCasterCleanup(ControlKind.STASIS, true, CleanupReason.DEATH));
      assertTrue(ControlManager.releaseOnCasterCleanup(ControlKind.PULL, false, CleanupReason.DEATH));
      assertTrue(ControlManager.releaseOnCasterCleanup(ControlKind.FREEZE, false, CleanupReason.DEATH));
      // A STASIS record whose dome is already gone releases normally.
      assertTrue(ControlManager.releaseOnCasterCleanup(ControlKind.STASIS, false, CleanupReason.DEATH));
   }

   @Test
   void disconnectAndHeroChangeReleaseEverything() {
      for (ControlKind kind : new ControlKind[] {ControlKind.PULL, ControlKind.FREEZE, ControlKind.STASIS}) {
         assertTrue(ControlManager.releaseOnCasterCleanup(kind, true, CleanupReason.DISCONNECT));
         assertTrue(ControlManager.releaseOnCasterCleanup(kind, true, CleanupReason.HERO_CHANGE));
      }
   }

   @Test
   void cleanupReasonsMapOntoReleaseReasons() {
      assertEquals(ReleaseReason.CASTER_DEATH, ControlManager.cleanupReleaseReason(CleanupReason.DEATH));
      assertEquals(ReleaseReason.CASTER_DISCONNECT, ControlManager.cleanupReleaseReason(CleanupReason.DISCONNECT));
      assertEquals(ReleaseReason.HERO_CHANGE, ControlManager.cleanupReleaseReason(CleanupReason.HERO_CHANGE));
   }

   // --- deferred restores ---------------------------------------------------

   @Test
   void pendingRestoreMapsEvictTheOldestEntry() {
      // Restores deferred for a never-rejoining entity must not accumulate
      // without bound (a3 audit finding).
      BoundedMap<java.util.UUID, Boolean> map = new BoundedMap<>(3);
      java.util.UUID first = java.util.UUID.randomUUID();
      map.put(first, Boolean.TRUE);
      map.put(java.util.UUID.randomUUID(), Boolean.TRUE);
      map.put(java.util.UUID.randomUUID(), Boolean.TRUE);
      map.put(java.util.UUID.randomUUID(), Boolean.TRUE);
      assertEquals(3, map.size());
      assertFalse(map.containsKey(first), "the eldest entry is evicted first");
   }
}
