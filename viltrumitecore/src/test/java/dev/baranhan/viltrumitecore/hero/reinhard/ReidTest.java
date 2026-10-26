package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Stage 4 Task 2: Reid's state machine, the Holy Sword slot conditions and the auto-return (spec §7.1, §7.3, §5). */
class ReidTest {
   private static Reid drawn() {
      Reid reid = new Reid();
      assertTrue(reid.toggle());
      for (int i = 0; i < ReinhardRules.REID_DRAW_TICKS; i++) {
         reid.tick();
      }

      assertSame(Reid.Phase.DRAWN, reid.phase());
      return reid;
   }

   @Test
   void drawTakes120() {
      assertEquals(120, ReinhardRules.REID_DRAW_TICKS);
      Reid reid = new Reid();
      assertSame(Reid.Phase.SHEATHED, reid.phase());
      assertTrue(reid.toggle());
      assertSame(Reid.Phase.DRAWING, reid.phase());
      assertTrue(reid.busy());
      for (int i = 1; i < 120; i++) {
         assertSame(Reid.Event.NONE, reid.tick(), "tick " + i);
      }

      assertSame(Reid.Event.DRAW_DONE, reid.tick());
      assertSame(Reid.Phase.DRAWN, reid.phase());
      assertFalse(reid.busy());
      assertEquals(ReinhardRules.REID_IDLE_TICKS, reid.idleLeft());
   }

   @Test
   void sheatheTakes20() {
      assertEquals(20, ReinhardRules.REID_SHEATHE_TICKS);
      Reid reid = drawn();
      assertTrue(reid.toggle());
      assertSame(Reid.Phase.SHEATHING, reid.phase());
      assertTrue(reid.busy());
      for (int i = 1; i < 20; i++) {
         assertSame(Reid.Event.NONE, reid.tick());
      }

      assertSame(Reid.Event.SHEATHE_DONE, reid.tick());
      assertSame(Reid.Phase.SHEATHED, reid.phase());
   }

   @Test
   void togglesIgnoredMidway() {
      Reid reid = new Reid();
      reid.toggle();
      assertFalse(reid.toggle(), "no toggle while drawing");
      Reid sheathing = drawn();
      sheathing.toggle();
      assertFalse(sheathing.toggle(), "no toggle while sheathing");
   }

   @Test
   void autoReturnAfter1200Idle() {
      assertEquals(1200, ReinhardRules.REID_IDLE_TICKS);
      Reid reid = drawn();
      for (int i = 1; i < 1200; i++) {
         assertSame(Reid.Event.NONE, reid.tick());
      }

      assertSame(Reid.Event.AUTO_RETURN, reid.tick());
      assertSame(Reid.Phase.SHEATHING, reid.phase());
      assertEquals(0, reid.idleLeft());
   }

   @Test
   void combatResetsIdle() {
      Reid reid = drawn();
      for (int i = 0; i < 600; i++) {
         reid.tick();
      }

      assertEquals(600, reid.idleLeft());
      reid.combat();
      assertEquals(1200, reid.idleLeft());
      for (int i = 1; i < 1200; i++) {
         assertSame(Reid.Event.NONE, reid.tick());
      }

      assertSame(Reid.Event.AUTO_RETURN, reid.tick());
      Reid sheathed = new Reid();
      sheathed.combat();
      assertSame(Reid.Phase.SHEATHED, sheathed.phase(), "combat never draws");
   }

   @Test
   void leaveModeSheathes() {
      ReinhardState state = new ReinhardState();
      state.reid.toggle();
      for (int i = 0; i < ReinhardRules.REID_DRAW_TICKS; i++) {
         state.reid.tick();
      }

      state.onLeaveHolySword();
      assertSame(Reid.Phase.SHEATHING, state.reid.phase());
      assertTrue(state.busy());
   }

   @Test
   void cancelDrawingOnLeave() {
      Reid reid = new Reid();
      reid.toggle();
      reid.tick();
      assertTrue(reid.sheatheNow(), "the draw was cut short");
      assertSame(Reid.Phase.SHEATHED, reid.phase());
      assertFalse(new Reid().sheatheNow());
      Reid drawn = drawn();
      assertFalse(drawn.sheatheNow());
      assertSame(Reid.Phase.SHEATHING, drawn.phase());
      drawn.reset();
      assertSame(Reid.Phase.SHEATHED, drawn.phase());
   }

   @Test
   void slotConditions() {
      for (Reid.Phase phase : Reid.Phase.values()) {
         assertTrue(Reid.allows(ReinhardAbility.REID, phase), "REID " + phase);
         assertEquals(phase == Reid.Phase.SHEATHED, Reid.allows(ReinhardAbility.SHEATH_STRIKE, phase), "SHEATH " + phase);
         for (ReinhardAbility drawnOnly : new ReinhardAbility[]{ReinhardAbility.SAW_SPHERE, ReinhardAbility.WORLD_CUT, ReinhardAbility.WORLD_ERASURE}) {
            assertEquals(phase == Reid.Phase.DRAWN, Reid.allows(drawnOnly, phase), drawnOnly + " " + phase);
         }

         for (ReinhardAbility other : ReinhardAbility.values()) {
            if (other.mode() != ReinhardMode.HOLY_SWORD) {
               assertTrue(Reid.allows(other, phase), other + " " + phase);
            }
         }
      }
   }

   @Test
   void conditionRefusedByTheGate() {
      ReinhardActions.Gate gate = new ReinhardActions.Gate(true, false, ReinhardMode.HOLY_SWORD, ReinhardMode.HOLY_SWORD, 0, false, false, false);
      assertSame(ReinhardActions.Refusal.CONDITION, ReinhardActions.gate(gate));
      assertSame(ReinhardActions.Refusal.NONE,
         ReinhardActions.gate(new ReinhardActions.Gate(true, false, ReinhardMode.HOLY_SWORD, ReinhardMode.HOLY_SWORD, 0, false, false, true)));
   }

   @Test
   void phaseRoundTripsThroughFlags() {
      for (Reid.Phase phase : Reid.Phase.values()) {
         assertSame(phase, Reid.phaseOf(Reid.withFlags(0, phase)));
      }

      assertSame(Reid.Phase.SHEATHED, Reid.phaseOf(0));
   }

   @Test
   void transientPhasesLoadSheathed() {
      Reid drawing = new Reid();
      drawing.toggle();
      assertSame(Reid.Phase.SHEATHED, roundTrip(drawing).phase());
      Reid sheathing = drawn();
      sheathing.toggle();
      assertSame(Reid.Phase.SHEATHED, roundTrip(sheathing).phase());
      Reid drawn = drawn();
      for (int i = 0; i < 100; i++) {
         drawn.tick();
      }

      Reid loaded = roundTrip(drawn);
      assertSame(Reid.Phase.DRAWN, loaded.phase());
      assertEquals(1100, loaded.idleLeft());
   }

   @Test
   void stateSavesReid() {
      ReinhardState state = new ReinhardState();
      state.reid.toggle();
      for (int i = 0; i < ReinhardRules.REID_DRAW_TICKS; i++) {
         state.reid.tick();
      }

      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      ReinhardState loaded = new ReinhardState();
      loaded.load(nbt);
      assertSame(Reid.Phase.DRAWN, loaded.reid.phase());
      assertSame(Reid.Phase.SHEATHED, state.respawnCopy().reid.phase(), "a fresh body has Reid sheathed");
   }

   private static Reid roundTrip(Reid reid) {
      CompoundTag tag = reid.save();
      Reid loaded = new Reid();
      loaded.load(tag);
      return loaded;
   }

   @Test
   void panelGreyFollowsReid() {
      int drawn = Reid.withFlags(0, Reid.Phase.DRAWN);
      assertTrue(ReinhardAbilities.slotGrey(ReinhardAbility.SAW_SPHERE, 0, 0), "Saw Sphere grey while sheathed");
      assertFalse(ReinhardAbilities.slotGrey(ReinhardAbility.SAW_SPHERE, 0, drawn));
      assertTrue(ReinhardAbilities.slotGrey(ReinhardAbility.SHEATH_STRIKE, 0, drawn), "Sheath Strike grey while drawn");
      assertTrue(ReinhardAbilities.slotGrey(ReinhardAbility.SHEATH_STRIKE, 0, 0));
      assertTrue(ReinhardAbilities.slotGrey(ReinhardAbility.SHEATH_STRIKE, 5, 0), "cooldown greys too");
      assertFalse(ReinhardAbilities.slotGrey(ReinhardAbility.REID, 0, Reid.withFlags(0, Reid.Phase.DRAWING)));
   }

   @Test
   void combatOnlyWithACause() {
      assertFalse(dev.baranhan.viltrumitecore.hero.reinhard.reid.ReidLife.isCombat(null, null));
   }
}
