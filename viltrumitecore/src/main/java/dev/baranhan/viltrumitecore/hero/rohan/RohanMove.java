package dev.baranhan.viltrumitecore.hero.rohan;

/** Rohan's timed moves (snapshot actionId = ordinal; the client poser and the stand renderer read it). Append only. */
public enum RohanMove {
   /** Heaven's Door: the stand appears beside him, then lunges and touches. */
   DOOR(RohanRules.DOOR_LENGTH, true),
   /** Air Sketch: quick strokes in the air with the pen. */
   SKETCH(RohanRules.SKETCH_DRAW, false),
   /** Pen Nibs: three flicks. */
   NIBS(RohanRules.NIBS_LENGTH, false),
   /** Faster than a Punch: the stand's open-palm guard. */
   COUNTER(RohanRules.COUNTER_WINDOW, true),
   /** Self-Edit: the stand writes on his own cheek, then he flies backwards. */
   SELF_EDIT(RohanRules.SELF_EDIT_WRITE + RohanRules.SELF_EDIT_FLY, true),
   /** Manuscript: he raises the finished page for everyone to see. */
   MANUSCRIPT(RohanRules.MANUSCRIPT_SHOW, false),
   /** A line is being written (the stand's pen strokes). */
   WRITE(RohanRules.WRITE_POSE, true),
   /** A page is torn out. */
   RIP(10, true);

   public final int length;
   /** The stand is out for this move. */
   public final boolean stand;

   RohanMove(int length, boolean stand) {
      this.length = length;
      this.stand = stand;
   }

   public static RohanMove byId(int id) {
      RohanMove[] values = values();
      return id >= 0 && id < values.length ? values[id] : null;
   }

   /** Moves that root him (he stands still while drawing or presenting). */
   public boolean roots() {
      return this == SKETCH || this == MANUSCRIPT;
   }
}
