package dev.baranhan.viltrumitecore.hero.ironman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.BodyScale;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.VeronicaView;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class HulkbusterLayerTest {
   static HulkbusterLayer active() {
      HulkbusterLayer layer = new HulkbusterLayer();
      assertTrue(layer.start());
      for (int i = 0; i < HulkbusterLayer.DROP_TICKS_GROUND; i++) {
         layer.tick();
      }

      HulkbusterLayer.Event event = HulkbusterLayer.Event.NONE;
      for (int i = 0; i < HulkbusterLayer.ASSEMBLE_TICKS; i++) {
         event = layer.tick();
      }

      assertSame(HulkbusterLayer.Event.READY, event);
      layer.activate();
      return layer;
   }

   @Test
   void dropAssembleActivate() {
      HulkbusterLayer layer = active();
      assertTrue(layer.active());
      assertTrue(layer.big());
      assertEquals(HulkbusterLayer.FULL_PARTS, layer.parts());
      assertEquals(HulkbusterLayer.DURABILITY, layer.durability());
   }

   @Test
   void refusedOnCooldown() {
      HulkbusterLayer layer = active();
      layer.breakNow();
      assertEquals(HulkbusterLayer.COOLDOWN, layer.cooldown());
      assertFalse(layer.start());
   }

   @Test
   void exitStartsCooldownAfterClimbingOut() {
      HulkbusterLayer layer = active();
      assertTrue(layer.exit());
      assertTrue(layer.big(), "still big while climbing out");
      HulkbusterLayer.Event event = HulkbusterLayer.Event.NONE;
      for (int i = 0; i < HulkbusterLayer.EXIT_TICKS; i++) {
         event = layer.tick();
      }

      assertSame(HulkbusterLayer.Event.EXITED, event);
      assertFalse(layer.present());
      assertEquals(HulkbusterLayer.COOLDOWN, layer.cooldown());
   }

   @Test
   void hitsGoToDurabilityAndBreak() {
      HulkbusterLayer layer = active();
      assertTrue(layer.absorb(100.0F));
      assertTrue(layer.durability() < HulkbusterLayer.DURABILITY);
      assertTrue(layer.absorb(10000.0F), "the emptying hit is still absorbed (no spill)");
      assertTrue(layer.broken());
   }

   @Test
   void cancelBeforeTheTorsoHasNoCooldownAfterItCannotStop() {
      HulkbusterLayer layer = new HulkbusterLayer();
      assertTrue(layer.start(true));
      assertEquals(HulkbusterLayer.DROP_TICKS_AIR, layer.dropTicks());
      for (int i = 0; i < layer.dropTicks() + HulkbusterLayer.TORSO_TICK - 1; i++) {
         layer.tick();
      }

      assertFalse(layer.torsoClosed());
      assertFalse(layer.big(), "full size only once the torso closes (no early scale jump)");
      assertFalse(layer.absorb(5.0F), "durability is not active in the scene");
      assertTrue(layer.cancel());
      assertFalse(layer.present());
      assertEquals(0, layer.cooldown(), "stopped before the torso: no cooldown");

      HulkbusterLayer late = new HulkbusterLayer();
      late.start(false);
      HulkbusterLayer.Event event = HulkbusterLayer.Event.NONE;
      for (int i = 0; i < late.dropTicks() + HulkbusterLayer.TORSO_TICK; i++) {
         event = late.tick();
      }

      assertSame(HulkbusterLayer.Event.TORSO_CLOSED, event);
      assertTrue(late.torsoClosed());
      assertTrue(late.big());
      assertFalse(late.cancel(), "after the torso the scene runs to its end");
      assertTrue(late.present());
   }

   @Test
   void platesLockInOrder() {
      int previous = 0;
      for (int i = 0; i < HulkbusterLayer.PARTS; i++) {
         int mask = HulkbusterLayer.lockedParts(HulkbusterLayer.PART_LOCK[i]);
         assertEquals((1 << (i + 1)) - 1, mask, "legs, torso, arms, shoulders, helmet");
         assertTrue(mask > previous);
         previous = mask;
         assertTrue(i == 0 || HulkbusterLayer.PART_LOCK[i] > HulkbusterLayer.PART_LOCK[i - 1]);
      }

      assertEquals(HulkbusterLayer.PART_LOCK[1], HulkbusterLayer.TORSO_TICK);
      assertEquals(HulkbusterLayer.FULL_PARTS, HulkbusterLayer.lockedParts(HulkbusterLayer.ASSEMBLE_TICKS));
   }

   @Test
   void relogMidSceneCancelsBeforeTheTorsoAndKeepsTheMachineAfterIt() {
      HulkbusterLayer early = new HulkbusterLayer();
      early.start(false);
      for (int i = 0; i < early.dropTicks() + 3; i++) {
         early.tick();
      }

      CompoundTag tag = new CompoundTag();
      early.save(tag);
      HulkbusterLayer loaded = new HulkbusterLayer();
      loaded.load(tag);
      assertFalse(loaded.present());
      assertEquals(0, loaded.cooldown());

      HulkbusterLayer late = new HulkbusterLayer();
      late.start(false);
      for (int i = 0; i < late.dropTicks() + HulkbusterLayer.TORSO_TICK + 4; i++) {
         late.tick();
      }

      CompoundTag tag2 = new CompoundTag();
      late.save(tag2);
      HulkbusterLayer loaded2 = new HulkbusterLayer();
      loaded2.load(tag2);
      assertTrue(loaded2.active());
      assertTrue(loaded2.needsFitCheck());

      HulkbusterLayer exiting = active();
      exiting.markSpareUsed();
      exiting.exit();
      CompoundTag tag3 = new CompoundTag();
      exiting.save(tag3);
      HulkbusterLayer loaded3 = new HulkbusterLayer();
      loaded3.load(tag3);
      assertFalse(loaded3.present(), "climbing out when saved: the exit finishes");
      assertEquals(HulkbusterLayer.COOLDOWN, loaded3.cooldown());
      assertTrue(loaded3.spareUsed(), "the spare arm flag is saved");
   }

   @Test
   void refuseWithoutRoomHasNoCooldown() {
      HulkbusterLayer layer = new HulkbusterLayer();
      layer.start();
      for (int i = 0; i < HulkbusterLayer.DROP_TICKS_GROUND + HulkbusterLayer.ASSEMBLE_TICKS; i++) {
         layer.tick();
      }

      layer.refuse();
      assertFalse(layer.present());
      assertEquals(0, layer.cooldown());
   }

   @Test
   void saveLoadInsideKeepsLayerAndChecksSpace() {
      HulkbusterLayer layer = active();
      layer.absorb(50.0F);
      CompoundTag tag = new CompoundTag();
      layer.save(tag);
      HulkbusterLayer loaded = new HulkbusterLayer();
      loaded.load(tag);
      assertTrue(loaded.active());
      assertTrue(loaded.needsFitCheck(), "the first tick checks the space for the big body");
      assertEquals(layer.durability(), loaded.durability(), 1.0E-5F);
      assertEquals(0, loaded.cooldown(), "no double cooldown");
   }

   @Test
   void deathBreaksWithCooldownHeroChangeResets() {
      IronManState state = new IronManState();
      HulkbusterLayer layer = state.hulkbuster;
      layer.start();
      for (int i = 0; i < HulkbusterLayer.DROP_TICKS_GROUND + HulkbusterLayer.ASSEMBLE_TICKS; i++) {
         layer.tick();
      }

      layer.activate();
      state.onCleanup(CleanupReason.DEATH);
      assertFalse(layer.present());
      assertEquals(HulkbusterLayer.COOLDOWN, layer.cooldown());
      IronManState respawned = IronManState.cloneForRespawn(state);
      assertEquals(HulkbusterLayer.COOLDOWN, respawned.hulkbuster.cooldown());
      respawned.onCleanup(CleanupReason.HERO_CHANGE);
      assertEquals(0, respawned.hulkbuster.cooldown());
   }

   @Test
   void noFlightWithHulkbuster() {
      IronManState state = new IronManState();
      state.suit.toggle();
      for (int i = 0; i < IronManRules.SUIT_DEPLOY_TICKS; i++) {
         state.suit.tick();
      }

      assertTrue(state.wantsFlight());
      state.hulkbuster.start();
      assertFalse(state.wantsFlight(), "the hop is not flight (spec §14.3)");
   }

   @Test
   void bodyScaleFindsFreeSpotOrRefuses() {
      Vec3 feet = new Vec3(0.5, 64.0, 0.5);
      // A low ceiling right here, room one block east.
      Vec3 spot = BodyScale.findFree(feet, 1.02F, 3.06F, box -> box.minX >= 1.0);
      assertNotNull(spot);
      assertTrue(spot.x >= 1.0);
      assertNull(BodyScale.findFree(feet, 1.02F, 3.06F, box -> false));
      AABB box = BodyScale.box(feet, 1.02F, 3.06F);
      assertEquals(3.06, box.getYsize(), 1.0E-6);
      assertEquals(feet, BodyScale.findFree(feet, 1.02F, 3.06F, b -> true));
   }

   @Test
   void snapshotSyncsPhaseAndDurability() {
      IronManState state = new IronManState();
      state.suit.toggle();
      for (int i = 0; i < IronManRules.SUIT_DEPLOY_TICKS; i++) {
         state.suit.tick();
      }

      HulkbusterLayer layer = state.hulkbuster;
      layer.start();
      for (int i = 0; i < HulkbusterLayer.DROP_TICKS_GROUND + HulkbusterLayer.ASSEMBLE_TICKS; i++) {
         layer.tick();
      }

      layer.activate();
      layer.absorb(200.0F);
      HeroPublicSnapshot snapshot = IronManHero.snapshotOf(state);
      assertEquals(HulkbusterLayer.Phase.ACTIVE.ordinal(), IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.HULKBUSTER_PHASE));
      assertEquals(HulkbusterLayer.FULL_PARTS, IronManVariant.hulkParts(snapshot.variant()));
      assertEquals(layer.durability() / HulkbusterLayer.DURABILITY, IronManVariant.hulkDurability(snapshot.variant()), 0.01F);
   }

   @Test
   void veronicaViewCarriesHulkbusterCard() {
      VeronicaView view = VeronicaView.of(new MarkRoster(), VeronicaView.HULKBUSTER_ON, 300);
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      view.write(buffer);
      VeronicaView read = VeronicaView.read(buffer);
      assertEquals(VeronicaView.HULKBUSTER_ON, read.hulkbuster());
      assertEquals(300, read.podTicksLeft());
   }
}
