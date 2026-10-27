package dev.baranhan.viltrumitecore.hero.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Saved state (only where Mjölnir is), flags layout and fresh bodies. */
class ThorStateTest {
   @Test
   void savesOnlyTheHammer() {
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      state.mode = ThorMode.ELECTRIC;
      state.timeline.request(ThorMove.HOLSTER, -1);
      CompoundTag nbt = new CompoundTag();
      state.save(nbt);
      assertEquals("in_hand", nbt.getCompound(ThorState.KEY).getString(ThorState.HAMMER_KEY));
      assertEquals(1, nbt.getCompound(ThorState.KEY).size());

      ThorState loaded = new ThorState();
      loaded.load(nbt);
      assertSame(HammerState.IN_HAND, loaded.hammer);
      assertSame(ThorMode.NORMAL, loaded.mode, "the mode is transient");
      assertFalse(loaded.timeline.running(), "the running move is transient");
   }

   @Test
   void aHammerSavedInFlightKeepsItsProjectionUntilTheWorldStoreSpeaks() {
      for (HammerState away : new HammerState[]{HammerState.THROWN, HammerState.RETURNING}) {
         ThorState state = new ThorState();
         state.hammer = away;
         CompoundTag nbt = new CompoundTag();
         state.save(nbt);
         ThorState loaded = new ThorState();
         loaded.load(nbt);
         assertSame(away, loaded.hammer, away.name() + ": session A re-projects it from the world store on login");
         assertFalse(loaded.hammerDetached, "the detached flag is transient");
      }
   }

   @Test
   void missingOrUnknownDataLoadsOnTheBelt() {
      ThorState loaded = new ThorState();
      loaded.load(new CompoundTag());
      assertSame(HammerState.HOLSTERED, loaded.hammer);

      CompoundTag nbt = new CompoundTag();
      CompoundTag thor = new CompoundTag();
      thor.putString(ThorState.HAMMER_KEY, "lost_in_niflheim");
      nbt.put(ThorState.KEY, thor);
      loaded.hammer = HammerState.IN_HAND;
      loaded.load(nbt);
      assertSame(HammerState.HOLSTERED, loaded.hammer);
   }

   @Test
   void freshBodiesKeepOnlyTheHammerProjection() {
      // F-M11 (rewritten, it pinned the bug): a fresh HOLSTERED body after an End return stranded Mjölnir on the belt.
      // The copy keeps the last projection; session A's store re-projects it on the first tick.
      ThorState state = new ThorState();
      state.hammer = HammerState.IN_HAND;
      state.mode = ThorMode.ELECTRIC;
      state.timeline.request(ThorMove.HOLSTER, -1);
      for (ThorState copy : new ThorState[]{state.respawnCopy(), state.erasedCopy()}) {
         assertNotSame(state, copy);
         assertSame(HammerState.IN_HAND, copy.hammer);
         assertSame(ThorMode.NORMAL, copy.mode);
         assertFalse(copy.timeline.running());
      }
   }

   @Test
   void flagsRoundTripEveryState() {
      for (HammerState hammer : HammerState.values()) {
         for (ThorMode mode : ThorMode.values()) {
            for (int bits = 0; bits < 4; bits++) {
               boolean window = (bits & 1) != 0;
               boolean camera = (bits & 2) != 0;
               int flags = ThorFlags.compose(hammer, mode, window, camera);
               assertSame(hammer, ThorFlags.hammer(flags));
               assertSame(mode, ThorFlags.mode(flags));
               assertEquals(window, ThorFlags.grabWindow(flags));
               assertEquals(camera, ThorFlags.cameraLease(flags));
               assertEquals(0, flags & ~0b11111, "bits 5+ are free");
            }
         }
      }

      assertEquals(0, ThorFlags.compose(HammerState.HOLSTERED, ThorMode.NORMAL, false), "an empty snapshot shows the hammer on the belt");
      assertTrue(HammerState.values().length <= ThorFlags.HAMMER_MASK + 1);
   }

   @Test
   void hammerKeysAndOrdinalsAreStable() {
      assertEquals(0, HammerState.HOLSTERED.ordinal());
      assertEquals(1, HammerState.IN_HAND.ordinal());
      assertEquals(2, HammerState.THROWN.ordinal());
      assertEquals(3, HammerState.RETURNING.ordinal());
      for (HammerState hammer : HammerState.values()) {
         assertSame(hammer, HammerState.fromKey(hammer.key()));
         assertSame(hammer, HammerState.byOrdinal(hammer.ordinal()));
      }

      assertSame(HammerState.HOLSTERED, HammerState.byOrdinal(9));
      assertTrue(HammerState.THROWN.away());
      assertFalse(HammerState.IN_HAND.away());
   }

   @Test
   void mjolnirIsDrawnWhereTheStateSays() {
      assertSame(MjolnirPlace.HAND, MjolnirPlace.of(HammerState.IN_HAND, true));
      assertSame(MjolnirPlace.BELT, MjolnirPlace.of(HammerState.IN_HAND, false), "an item in the hand wins");
      assertSame(MjolnirPlace.BELT, MjolnirPlace.of(HammerState.HOLSTERED, true));
      assertSame(MjolnirPlace.BELT, MjolnirPlace.of(HammerState.HOLSTERED, false));
      assertSame(MjolnirPlace.HIDDEN, MjolnirPlace.of(HammerState.THROWN, true));
      assertSame(MjolnirPlace.HIDDEN, MjolnirPlace.of(HammerState.RETURNING, false));
   }
}
