package dev.baranhan.viltrumitecore.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The hotbar's flight row (speed lock, fast takeoff, supersonic flight): owned abilities only. */
public final class FlightRow {
   private FlightRow() {
   }

   /**
    * Pure: the owned ids in their order; when the hero owns none of them the full row is kept
    * (unchanged layout for heroes that never had flight slots).
    */
   public static String[] visible(String[] ids, Predicate<String> owns) {
      List<String> out = new ArrayList<>();
      for (String id : ids) {
         if (owns.test(id)) {
            out.add(id);
         }
      }
      return out.isEmpty() ? ids.clone() : out.toArray(new String[0]);
   }
}
