package dev.baranhan.viltrumitecore.client.thanos;

/**
 * Where the Thanos HUD may go next to a hero's HUD (pure, unit-tested). The timer panel sits centred under
 * the boss bar and the warning strip under the panel. A hero's HUD owns the bottom-right corner: the vertical
 * ability bar of {@code ViltrumiteInGameHudMixin} (27 px from the right edge, its top 147 px above the bottom),
 * the emblem and page badge above it, and the style's attachments left of it ({@code PanelStyle.attachmentsLeft}:
 * a gauge for most heroes, Reinhard's mode icons with the mouse hint further left). At a GUI height near 240 (the automatic GUI scale at
 * 1280x720, 1920x1200, 2560x1440 and 3840x2160) that corner reaches the panel's rows, so the panel and the
 * strip keep left of it: the panel moves left, the strip narrows and wraps its hint. Elsewhere nothing moves.
 */
public final class ThanosHudLayout {
   /** The ability bar of the hero HUD: x = width - this, y = height - {@link #BAR_FROM_BOTTOM}. */
   public static final int BAR_FROM_RIGHT = 27;
   public static final int BAR_FROM_BOTTOM = 147;
   /** Emblem and page badge: above the bar (up to 36 px), from 4 px left of it. */
   public static final int COLUMN_UP = 36;
   public static final int COLUMN_LEFT = 4;
   /** Gap kept to the hero HUD and to the screen edge. */
   public static final int MARGIN = 3;
   /** The strip's side margins on an empty screen (the old layout). */
   public static final int SCREEN_MARGIN = 10;
   public static final int MIN_STRIP = 60;
   public static final int MAX_HINT_LINES = 3;

   private ThanosHudLayout() {
   }

   /**
    * The rightmost x a box spanning down to {@code bottom} may reach: the screen edge, or the left edge of every
    * part of the hero HUD whose top is above {@code bottom} (each part runs down to the bottom of the screen).
    * {@code reachLeft} / {@code reachUp}: the hero style's attachments ({@code PanelStyle.attachmentsLeft/Up}).
    */
   public static int maxRight(int width, int height, int bottom, boolean heroPanel, int reachLeft, int reachUp) {
      int right = width - MARGIN;
      if (!heroPanel) {
         return right;
      }

      int barX = width - BAR_FROM_RIGHT;
      int barY = height - BAR_FROM_BOTTOM;
      if (bottom > barY - COLUMN_UP) {
         right = Math.min(right, barX - COLUMN_LEFT - MARGIN);
      }

      if (reachLeft > 0 && bottom > barY - reachUp) {
         right = Math.min(right, barX - reachLeft - MARGIN);
      }

      return right;
   }

   /** The panel's x: centred, or as far right as {@code maxRight} lets it, never off the left edge. */
   public static int panelX(int width, int w, int maxRight) {
      return Math.max(MARGIN, Math.min((width - w) / 2, maxRight - w));
   }

   /** The strip's width: what its text wants, within the screen margins and left of {@code maxRight}. */
   public static int stripWidth(int want, int width, int maxRight) {
      return Math.max(MIN_STRIP, Math.min(want, Math.min(width - 2 * SCREEN_MARGIN, maxRight - MARGIN)));
   }

   /** The strip's x: centred on {@code cx}, pushed left of {@code maxRight}, never off the left edge. */
   public static int stripX(int cx, int w, int maxRight) {
      return Math.max(MARGIN, Math.min(cx - w / 2, maxRight - w));
   }

   /** Height of a strip with this many hint lines (one line: the old 22 px). */
   public static int stripHeight(int lines) {
      return 15 + 7 * Math.max(1, Math.min(MAX_HINT_LINES, lines));
   }

   /** The tallest strip, used to decide which parts of the hero HUD it may reach. */
   public static int maxStripHeight() {
      return stripHeight(MAX_HINT_LINES);
   }
}
