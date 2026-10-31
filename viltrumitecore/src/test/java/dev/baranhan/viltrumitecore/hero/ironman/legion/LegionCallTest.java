package dev.baranhan.viltrumitecore.hero.ironman.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkRoster;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegionCallTest {
   private static int[] ids(MarkId... marks) {
      int[] out = new int[marks.length];
      for (int i = 0; i < marks.length; i++) {
         out[i] = marks[i].ordinal();
      }

      return out;
   }

   @Test
   void picksUpToThree() {
      MarkRoster roster = new MarkRoster();
      List<MarkId> call = LegionCall.validate(ids(MarkId.MARK_7, MarkId.MARK_42, MarkId.MARK_15, MarkId.MARK_39), roster);
      assertEquals(List.of(MarkId.MARK_7, MarkId.MARK_42, MarkId.MARK_15), call);
   }

   @Test
   void dropsDuplicatesUnknownAndUnavailable() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.WORN);
      roster.breakMark(MarkId.MARK_17);
      int[] request = {MarkId.MARK_7.ordinal(), MarkId.MARK_7.ordinal(), 99, -1, MarkId.MARK_42.ordinal(), MarkId.MARK_17.ordinal(), MarkId.MARK_39.ordinal()};
      assertEquals(List.of(MarkId.MARK_7, MarkId.MARK_39), LegionCall.validate(request, roster));
   }

   @Test
   void countsSuitsAlreadyFlying() {
      MarkRoster roster = new MarkRoster();
      roster.move(MarkId.MARK_7, MarkLocation.STORED, MarkLocation.LEGION);
      roster.move(MarkId.MARK_42, MarkLocation.STORED, MarkLocation.LEGION);
      List<MarkId> call = LegionCall.validate(ids(MarkId.MARK_7, MarkId.MARK_15, MarkId.MARK_39), roster);
      // MARK_7 already flies; only one free place is left.
      assertEquals(List.of(MarkId.MARK_15), call);
      roster.move(MarkId.MARK_15, MarkLocation.STORED, MarkLocation.LEGION);
      assertTrue(LegionCall.validate(ids(MarkId.MARK_39), roster).isEmpty());
   }

   @Test
   void noCallWithoutAvailableMarks() {
      MarkRoster roster = new MarkRoster();
      for (MarkId id : MarkId.values()) {
         roster.breakMark(id);
      }

      assertTrue(LegionCall.validate(ids(MarkId.values()), roster).isEmpty());
      assertTrue(LegionCall.suggest(roster).isEmpty());
      assertTrue(LegionCall.validate(null, roster).isEmpty());
   }

   @Test
   void suggestionPrefersHealthySuits() {
      MarkRoster roster = new MarkRoster();
      roster.setDurability(MarkId.MARK_7, 10.0F);
      roster.setDurability(MarkId.MARK_42, 20.0F);
      List<MarkId> suggestion = LegionCall.suggest(roster);
      assertEquals(LegionRules.MAX_SUITS, suggestion.size());
      assertTrue(!suggestion.contains(MarkId.MARK_7) && !suggestion.contains(MarkId.MARK_42));
   }
}
