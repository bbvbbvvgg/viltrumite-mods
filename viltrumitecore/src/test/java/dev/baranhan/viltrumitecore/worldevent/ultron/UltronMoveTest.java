package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UltronMoveTest {
   @Test
   void contactsLieInsideTheMove() {
      for (UltronMove m : UltronMove.values()) {
         int last = -1;
         for (int t = 0; t <= m.length + 5; t++) {
            if (m.contact(t)) {
               assertTrue(t > 0 && t < m.length, m + " contact " + t);
               assertTrue(t > last, m + " contacts ascend");
               assertEquals(m.contactIndex(t), m.contactIndex(t));
               last = t;
            }
         }
         assertEquals(m.contacts() == 0 ? -1 : m.firstContact(), m.firstContact());
         assertSame(m, UltronMove.byId(m.ordinal()));
         if (m != UltronMove.NONE) {
            assertFalse(m.clip.isEmpty());
            assertTrue(m.length > 0);
            if (m.contacts() > 0) {
               // anticipation: no strike lands on its first frames
               assertTrue(m.firstContact() >= 4, m + " needs a wind-up");
            }
         }
      }
      assertSame(UltronMove.NONE, UltronMove.byId(-1));
   }

   @Test
   void moveIdsFitTheActionByte() {
      assertTrue(UltronMove.values().length < 256);
      Set<String> names = new HashSet<>();
      for (UltronMove m : UltronMove.values()) {
         names.add(m.name());
      }
      assertEquals(UltronMove.values().length, names.size());
   }
}
