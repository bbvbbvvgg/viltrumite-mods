package dev.baranhan.viltrumitecore.worldevent.godzilla;

import java.util.Locale;

/**
 * The stages of the catastrophe. ARRIVAL: it walks in from outside the city and roars, the sirens warn.
 * EXPLORE: it walks between districts and wrecks what stands in its way. COMBAT: a hero challenged it. RAGE: hurt
 * badly (or hit hard in a short time) it moves faster, recovers sooner and burns longer. FINALE: near death, the last
 * stand. RETREAT: the event time is over, it walks away and sinks. DEATH: it fell and stays down for a while.
 */
public enum GodzillaPhase {
   ARRIVAL,
   EXPLORE,
   COMBAT,
   RAGE,
   FINALE,
   RETREAT,
   DEATH;

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   /** Fights heroes on sight (otherwise only when attacked or approached). */
   public boolean fighting() {
      return this == COMBAT || this == RAGE || this == FINALE;
   }

   public boolean enraged() {
      return this == RAGE || this == FINALE;
   }

   public boolean over() {
      return this == RETREAT || this == DEATH;
   }

   public static GodzillaPhase byKey(String key) {
      for (GodzillaPhase p : values()) {
         if (p.key().equals(key)) {
            return p;
         }
      }
      return null;
   }
}
