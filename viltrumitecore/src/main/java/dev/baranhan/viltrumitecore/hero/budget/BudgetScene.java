package dev.baranhan.viltrumitecore.hero.budget;

/**
 * Short ultimate wind-up scenes (1-3 s). The server holds the ultimate for
 * {@link #windup} ticks, the client sways the camera and flashes the screen.
 * The camera offset is a pure function of the elapsed time and is zero at
 * both ends, so the view always returns to normal.
 */
public enum BudgetScene {
   NONE(0, 0.0F, 0, false, false),
   /** Gojo: a line, a small camera turn, a purple flash. */
   GOJO(30, 14.0F, 0xA020F0, false, false),
   /** Sukuna: freezes for a second, then a particle burst. */
   SUKUNA(20, 4.0F, 0xC00000, true, false),
   /** Goku: raises the hands, a sound, an energy ball, then the beam. */
   GOKU(40, 6.0F, 0x60C0FF, true, false),
   /** Superman: hovers in the air and fires a beam. */
   SUPERMAN(30, 5.0F, 0xFF3010, false, true),
   /** Shrek: a very loud roar. */
   SHREK(20, 10.0F, 0x8AB830, false, false);

   public final int windup;
   public final float cameraYaw;
   public final int flashColor;
   public final boolean freeze;
   public final boolean hover;

   BudgetScene(int windup, float cameraYaw, int flashColor, boolean freeze, boolean hover) {
      this.windup = windup;
      this.cameraYaw = cameraYaw;
      this.flashColor = flashColor;
      this.freeze = freeze;
      this.hover = hover;
   }

   public String lineKey() {
      return "scene.viltrumitecore.budget." + this.name().toLowerCase(java.util.Locale.ROOT);
   }

   public static BudgetScene byId(int ordinal) {
      BudgetScene[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
   }
}
