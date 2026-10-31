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
   SHREK(20, 10.0F, 0x8AB830, false, false),
   /** Aizen: a purple flash and a growing energy ring. */
   AIZEN(30, 8.0F, 0xA020F0, true, false),
   /** Ichigo: raises the arm, dark particles, then a huge dark beam. */
   ICHIGO(40, 6.0F, 0x200020, false, true),
   /** Vergil: everything stops, a series of simple slashes, a flash. */
   VERGIL(40, 12.0F, 0x3050FF, true, false),
   /** Eren: a (harmless) lightning strike, steam, then the stomp. */
   EREN(30, 10.0F, 0xFFE080, true, false),
   /** Ainz: ticking clock sounds, rising black smoke, then a pillar of light. */
   AINZ(40, 4.0F, 0xF0F0FF, true, false),
   /** Shadow: purple particles gather, then "I am atomic". */
   SHADOW(40, 14.0F, 0x9040FF, true, false),
   /** Escanor: hovers, a small sun of flame particles, then Cruel Sun. */
   ESCANOR(30, 6.0F, 0xFF8010, false, true),
   /** Multiverse Collapse: the rift opens (camera shake + purple flash, no hero). */
   MULTIVERSE_RIFT(40, 16.0F, 0x9040FF, false, false),
   /** Multiverse Collapse: a champion or the final boss arrives (white flash, no hero). */
   MULTIVERSE_FLASH(20, 10.0F, 0xFFFFFF, false, false);

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
