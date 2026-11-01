package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

/**
 * A task the war layer gives to one squad (pure value; the soldier's order goal executes it).
 *
 * @param sector target sector id, or -1
 * @param target entity id of the encircle target, or -1
 * @param issued war clock tick of the order
 */
public record WarOrder(Type type, double x, double y, double z, int sector, int target, int issued) {
   public enum Type {
      /** Walk through remote sectors and report them. */
      SCOUT,
      /** Freshly dropped: secure the landing zone. */
      DROP,
      /** Take the sector: the first soldier at the spot plants a stronghold beacon. */
      SEIZE,
      /** Walk the streets of a held sector. */
      PATROL,
      /** Run to a contested sector the hive holds. */
      REINFORCE,
      /** Surround one hero from every side. */
      ENCIRCLE,
      /** Stay by the stronghold and fight from cover. */
      DEFEND,
      /** Break contact and fall back to regroup. */
      RETREAT,
      /** Captors: hunt citizens in the sector. */
      ABDUCT,
      /** Siege lancers: shell cover and hero positions. */
      SIEGE
   }

   /** Orders under which a soldier holds its ground near the point instead of roaming. */
   public boolean holds() {
      return this.type == Type.DEFEND || this.type == Type.SEIZE || this.type == Type.PATROL;
   }

   /**
    * Orders under which the soldier keeps moving even with a target in sight (no stopping to trade fire). A Captor on
    * its way to the people does not stop for a gunfight either; the order is lifted once it is among them.
    */
   public boolean breaksContact() {
      return this.type == Type.RETREAT || this.type == Type.ABDUCT;
   }
}
