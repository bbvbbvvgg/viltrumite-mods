package dev.baranhan.viltrumitecore.hero;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HeroOwnerSnapshotTest {
   @Test
   void sectionsIndependent() {
      OwnerSections store = new OwnerSections();
      store.set(OwnerSection.THREATS, HeroOwnerSnapshot.Section.of(new int[]{1, 2}), 0L);
      store.set(OwnerSection.MARKS, HeroOwnerSnapshot.Section.of(new int[]{3, 4, 5, 6}), 0L);
      store.set(OwnerSection.SCAN, new HeroOwnerSnapshot.Section(new int[]{9}, new int[]{200}), 0L);
      store.set(OwnerSection.THREATS, HeroOwnerSnapshot.Section.of(new int[]{7}), 50L);
      assertArrayEquals(new int[]{7}, store.ids(OwnerSection.THREATS, 60L));
      assertArrayEquals(new int[]{3, 4, 5, 6}, store.ids(OwnerSection.MARKS, 60L));
      assertArrayEquals(new int[]{9}, store.ids(OwnerSection.SCAN, 199L));
      assertArrayEquals(new int[0], store.ids(OwnerSection.SCAN, 200L), "expired at 200 t");
   }

   @Test
   void carriersViewUnchanged() {
      HeroOwnerSnapshot snapshot = new HeroOwnerSnapshot(new int[]{11, 12});
      assertArrayEquals(new int[]{11, 12}, snapshot.carrierEntityIds());
      HeroOwnerSnapshot marked = snapshot.with(OwnerSection.MARKS, HeroOwnerSnapshot.Section.of(new int[]{5}));
      assertArrayEquals(new int[]{11, 12}, marked.carrierEntityIds(), "a MARKS update leaves the carriers");
      assertArrayEquals(new int[]{5}, marked.section(OwnerSection.MARKS).ids());
   }

   @Test
   void liveArrayIsCachedUntilTheNextExpiry() {
      OwnerSections store = new OwnerSections();
      store.set(OwnerSection.SCAN, new HeroOwnerSnapshot.Section(new int[]{1, 2}, new int[]{0, 100}), 0L);
      int[] first = store.ids(OwnerSection.SCAN, 10L);
      assertSame(first, store.ids(OwnerSection.SCAN, 50L), "no new array per call");
      assertTrue(store.contains(OwnerSection.SCAN, 2, 99L));
      assertFalse(store.contains(OwnerSection.SCAN, 2, 100L));
      assertArrayEquals(new int[]{1}, store.ids(OwnerSection.SCAN, 100L));
      // A new world restarts game time: the cache must not survive going back.
      assertArrayEquals(new int[]{1, 2}, store.ids(OwnerSection.SCAN, 5L));
   }

   @Test
   void clearDropsEverything() {
      OwnerSections store = new OwnerSections();
      store.set(OwnerSection.THREATS, HeroOwnerSnapshot.Section.of(new int[]{4}), 0L);
      store.clear();
      assertFalse(store.contains(OwnerSection.THREATS, 4, 1L));
   }

   @Test
   void emptySectionRemoved() {
      OwnerSections store = new OwnerSections();
      store.set(OwnerSection.MARKS, HeroOwnerSnapshot.Section.of(new int[]{1}), 0L);
      store.set(OwnerSection.MARKS, HeroOwnerSnapshot.Section.EMPTY, 1L);
      assertEquals(0, store.ids(OwnerSection.MARKS, 2L).length);
   }
}
