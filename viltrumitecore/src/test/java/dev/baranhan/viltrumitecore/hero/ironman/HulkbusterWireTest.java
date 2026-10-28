package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.client.ironman.hulkbuster.HulkbusterView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterKit;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import org.junit.jupiter.api.Test;

/**
 * The Hulkbuster block of the Iron Man snapshot (plan H2): the pure words round-trip, and the production
 * composer (IronManHero.snapshotOf) survives encode / decode and reads back through the client's HulkbusterView.
 */
class HulkbusterWireTest {
   @Test
   void motionWordRoundTrips() {
      for (HulkbusterMotion.State state : HulkbusterMotion.State.values()) {
         for (int direction = 0; direction < 4; direction++) {
            for (int charges = 0; charges <= 2; charges++) {
               for (int stage = 0; stage <= 2; stage++) {
                  for (int level = 0; level <= 2; level++) {
                     int word = HulkbusterWire.motion(state, direction, charges, stage, (direction & 1) == 1, level);
                     assertSame(state, HulkbusterWire.state(word));
                     assertEquals(direction, HulkbusterWire.direction(word));
                     assertEquals(charges, HulkbusterWire.dashCharges(word));
                     assertEquals(stage, HulkbusterWire.damageStage(word));
                     assertEquals((direction & 1) == 1, HulkbusterWire.spareUsed(word));
                     assertEquals(level, HulkbusterWire.chargeLevel(word));
                  }
               }
            }
         }
      }
   }

   @Test
   void sceneWordRoundTrips() {
      int word = HulkbusterWire.scene(73, 1, 270.0F, 22);
      assertEquals(73, HulkbusterWire.sceneTick(word));
      assertEquals(1, HulkbusterWire.sceneKind(word));
      assertEquals(270.0F, HulkbusterWire.sceneYaw(word), 360.0F / 256.0F);
      assertEquals(22, HulkbusterWire.sceneAssemble(word));
      assertEquals(-1, HulkbusterWire.sceneAssemble(HulkbusterWire.scene(10, 2, -90.0F, -1)));
      assertEquals(270.0F, HulkbusterWire.sceneYaw(HulkbusterWire.scene(10, 2, -90.0F, -1)), 360.0F / 256.0F);
   }

   static IronManState activeState() {
      IronManState state = new IronManState();
      state.suit.toggle();
      for (int i = 0; i < IronManRules.SUIT_DEPLOY_TICKS; i++) {
         state.suit.tick();
      }

      HulkbusterLayer layer = state.hulkbuster;
      assertTrue(layer.start(true));
      for (int i = 0; i < layer.dropTicks() + HulkbusterLayer.ASSEMBLE_TICKS; i++) {
         layer.tick();
      }

      layer.activate();
      return state;
   }

   @Test
   void productionSnapshotCarriesTheMoveTheMotionAndTheCooldowns() {
      IronManState state = activeState();
      HulkbusterKit kit = state.hulkKit;
      kit.timeline.requestAt(HulkMove.COMBO, -1, 1);
      for (int i = 0; i < 13; i++) {
         kit.timeline.tick();
      }

      kit.motion.set(HulkbusterMotion.State.HOVER);
      kit.motion.direction = 3;
      kit.motion.dashCharges = 1;
      kit.motion.dashRecharge = 33;
      kit.repulsorsCooldown = 120;
      kit.riftCooldown = 77;
      kit.cageCooldown = 499;
      kit.ultimateCooldown = 1800;
      kit.grabCooldown = 160;
      state.hulkbuster.absorb(1000.0F);
      HeroPublicSnapshot snapshot = HeroPublicSnapshot.decode(IronManHero.snapshotOf(state).encode());

      assertSame(HulkbusterLayer.Phase.ACTIVE, HulkbusterView.phase(snapshot));
      assertSame(HulkMove.COMBO, HulkbusterView.move(snapshot));
      assertEquals(HulkMove.COMBO.wire(), snapshot.actionId());
      assertEquals(1, HulkbusterView.step(snapshot));
      assertEquals(kit.timeline.elapsed(), snapshot.actionElapsed());
      assertEquals(HulkMove.COMBO.length(1), snapshot.actionLength());
      assertSame(kit.timeline.phase(), HulkbusterView.movePhase(snapshot));
      assertSame(MovePhase.MAIN, HulkbusterView.movePhase(snapshot), "13 ticks into the 12-tick intro of step 2");
      assertSame(HulkbusterMotion.State.HOVER, HulkbusterView.motion(snapshot));
      assertEquals(3, HulkbusterView.direction(snapshot));
      assertEquals(1, HulkbusterView.dashCharges(snapshot));
      assertEquals(2, HulkbusterView.damageStage(snapshot), "1000 raw damage x plating leaves < 33 %");
      assertEquals(33, HulkbusterView.extra(snapshot, HulkbusterWire.DASH_RECHARGE));
      assertEquals(120, HulkbusterView.extra(snapshot, HulkbusterWire.CD_REPULSORS));
      assertEquals(77, HulkbusterView.extra(snapshot, HulkbusterWire.CD_RIFT));
      assertEquals(499, HulkbusterView.extra(snapshot, HulkbusterWire.CD_CAGE));
      assertEquals(1800, HulkbusterView.extra(snapshot, HulkbusterWire.CD_ULTIMATE));
      assertEquals(160, HulkbusterView.extra(snapshot, HulkbusterWire.CD_GRAB));
      assertEquals(-1, HulkbusterView.victim(snapshot));
   }

   @Test
   void sceneSnapshotCarriesTheFrameAndTheAssembly() {
      IronManState state = new IronManState();
      state.suit.toggle();
      for (int i = 0; i < IronManRules.SUIT_DEPLOY_TICKS; i++) {
         state.suit.tick();
      }

      HulkbusterLayer layer = state.hulkbuster;
      assertTrue(layer.start(false));
      state.equipSource = new net.minecraft.world.phys.Vec3(1.0, 64.0, 2.0);
      state.hulkKit.sceneYaw = 90.0F;
      for (int i = 0; i < layer.dropTicks() + HulkbusterLayer.TORSO_TICK; i++) {
         layer.tick();
      }

      HeroPublicSnapshot snapshot = HeroPublicSnapshot.decode(IronManHero.snapshotOf(state).encode());
      assertSame(HulkbusterLayer.Phase.ASSEMBLING, HulkbusterView.phase(snapshot));
      assertTrue(HulkbusterView.scene(HulkbusterView.phase(snapshot)));
      assertEquals(state.equipSource, snapshot.actionTarget());
      int word = HulkbusterView.sceneWord(snapshot);
      assertEquals(HulkbusterLayer.TORSO_TICK, HulkbusterWire.sceneAssemble(word));
      assertEquals(2, HulkbusterWire.sceneKind(word));
      assertEquals(90.0F, HulkbusterWire.sceneYaw(word), 1.5F);
      assertNull(HulkbusterView.move(snapshot));
   }
}
