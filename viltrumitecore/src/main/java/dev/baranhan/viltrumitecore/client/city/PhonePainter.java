package dev.baranhan.viltrumitecore.client.city;

/**
 * The texture of the citizens' phones (#151), painted in code (pure ARGB, unit tested) like the citizen skins:
 * one small atlas shared by every phone. Row 0: case colours ({@link #CASES} swatches of 4x4). Rows 4..11: the
 * backs (case colour, a dark camera module with two lenses, a small logo). Rows 16..47: the screens, 8 px wide
 * and 32 px tall; the face of a phone shows a 16 px window of one, and the scrolling ones (chat, feed) repeat
 * every 16 px so the window slides through them without a seam.
 */
public final class PhonePainter {
   public static final int W = 64;
   public static final int H = 64;
   public static final int CASES = 8;
   public static final int SCREEN_W = 8;
   public static final int SCREEN_H = 16;
   public static final int SCREENS_Y = 16;
   /** Screens (columns of the atlas). */
   public static final int OFF = 0;
   public static final int LOCK = 1;
   public static final int CHAT = 2;
   public static final int FEED = 3;
   public static final int MAP = 4;
   public static final int DIAL = 5;
   public static final int CALL = 6;
   public static final int VIDEO = 7;
   static final int[] CASE_COLORS = {0xFF1C1C1E, 0xFFE9E6E1, 0xFF2F4F7F, 0xFFB43C3C, 0xFF3E6B4F, 0xFFD9B8C4, 0xFF8A8F96, 0xFFD8C27A};

   private PhonePainter() {
   }

   /** Case colour index of a person's phone. */
   public static int caseOf(int seed) {
      return Math.floorMod(seed >>> 9 ^ seed >>> 21, CASES);
   }

   /** What the screen shows at a moment of the phone timeline (OFF when dark: at the ear, in the pocket). */
   public static int screenFor(dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Moment m) {
      if (!m.screenOn()) {
         return OFF;
      }
      return switch (m.phase()) {
         case LOOK -> new int[]{CHAT, FEED, MAP, VIDEO}[m.variant() >> 1 & 3];
         case DIAL -> DIAL;
         case HANG_UP -> CALL;
         default -> LOCK;
      };
   }

   /** Rows the 16 px window of a scrolling screen is moved down by (0 for the still ones), always in [0, 16). */
   public static float scroll(int screen, dev.baranhan.viltrumitecore.city.life.PhoneRoutine.Moment m) {
      if (!scrolls(screen)) {
         return 0.0F;
      }
      // flicks: a quick slide, then a pause to read
      float t = m.t() / 22.0F;
      float flick = (float)Math.floor(t) + Math.min(1.0F, (t - (float)Math.floor(t)) * 4.0F);
      return (flick * 5.0F + m.variant() * 3) % SCREEN_H;
   }

   /** Scrolling screens slide through their 32 px column (16 px period). */
   public static boolean scrolls(int screen) {
      return screen == CHAT || screen == FEED;
   }

   public static int[] paint() {
      int[] px = new int[W * H];
      for (int i = 0; i < CASES; i++) {
         int c = CASE_COLORS[i];
         fill(px, i * 4, 0, 4, 4, c);
         // back: the case, a camera module at the top corner, a small logo in the middle
         fill(px, i * 4, 4, 4, 8, c);
         fill(px, i * 4, 4, 2, 2, 0xFF101012);
         set(px, i * 4, 4, 0xFF3A4A66);
         set(px, i * 4 + 1, 5, 0xFF3A4A66);
         set(px, i * 4 + 2, 8, shade(c, 0.8F));
      }

      for (int s = 0; s < 8; s++) {
         int x0 = s * SCREEN_W;
         for (int y = 0; y < 32; y++) {
            for (int x = 0; x < SCREEN_W; x++) {
               set(px, x0 + x, SCREENS_Y + y, screen(s, x, y % SCREEN_H, y));
            }
         }
      }
      return px;
   }

   /** One pixel of a screen: x 0..7, y 0..15 inside the period (yy over the whole column). */
   static int screen(int s, int x, int y, int yy) {
      boolean edge = x == 0 || x == SCREEN_W - 1;
      switch (s) {
         case OFF:
            // dark glass with a faint diagonal reflection
            return (x + y) % 9 == 0 ? 0xFF2A2D33 : 0xFF0B0C0F;
         case LOCK:
            if (y >= 3 && y <= 5 && x >= 2 && x <= 5) {
               return 0xFFF2F2F2; // the clock
            }
            if (y == 13 && x >= 3 && x <= 4) {
               return 0xFFDADADA; // swipe bar
            }
            return y < 8 ? 0xFF3B5BA9 : 0xFF6A4C93; // wallpaper
         case CHAT:
            // bubbles left (grey) and right (blue) on white, repeating every 16 px
            if (edge) {
               return 0xFFF4F4F6;
            }
            int row = y / 4;
            boolean mine = row % 2 == 1;
            boolean bubble = y % 4 != 3 && (mine ? x >= 3 : x <= 4 + (row == 2 ? 1 : 0));
            return bubble ? (mine ? 0xFF2F7CF6 : 0xFFD9DCE1) : 0xFFF4F4F6;
         case FEED:
            // posts: a picture block, then two text lines
            int py = y % 8;
            if (py < 5) {
               return edge ? 0xFFFFFFFF : ((y / 8 & 1) == 0 ? (py < 2 ? 0xFF7FB3E0 : 0xFF4E8A4F) : (py < 3 ? 0xFFE8A060 : 0xFFB0523C));
            }
            return py == 6 && x >= 1 && x <= 5 ? 0xFF9A9A9A : 0xFFFFFFFF;
         case MAP:
            if (x == 3 || y == 7 || x + y == 12) {
               return 0xFFFFFFFF; // roads
            }
            if (y >= 4 && y <= 5 && x >= 5 && x <= 6) {
               return 0xFFE53935; // the pin
            }
            if (yy >= 16 && yy < 18) {
               return 0xFF4285F4;
            }
            return (x * 3 + y) % 7 < 2 ? 0xFF9CCC8C : 0xFFE6E2D8;
         case DIAL:
            if (y >= 1 && y <= 2 && x >= 1 && x <= 6) {
               return 0xFF202020; // the number typed so far
            }
            if (y >= 4 && y <= 11 && (x == 1 || x == 3 || x == 5) && y % 2 == 0) {
               return 0xFFB8BCC4; // keys 3x4
            }
            if (y >= 13 && y <= 14 && x >= 3 && x <= 4) {
               return 0xFF34C759; // call
            }
            return 0xFFF7F7F7;
         case CALL:
            if (y >= 3 && y <= 6 && x >= 2 && x <= 5 && !((y == 3 || y == 6) && (x == 2 || x == 5))) {
               return 0xFF9AA5B1; // the caller's picture
            }
            if (y == 8 && x >= 1 && x <= 6) {
               return 0xFFE0E0E0; // the name
            }
            if (y >= 13 && y <= 14 && x >= 3 && x <= 4) {
               return 0xFFFF3B30; // hang up
            }
            return 0xFF1E2430;
         default:
            // a video: a picture with a progress bar
            if (y == 12) {
               return x < 3 ? 0xFFFF0000 : 0xFF555555;
            }
            if (y < 5 || y > 10) {
               return 0xFF000000;
            }
            return y < 8 ? 0xFF5FA8D3 : (x + y & 1) == 0 ? 0xFF3C7A3C : 0xFF4C8A44;
      }
   }

   private static void fill(int[] px, int x0, int y0, int w, int h, int c) {
      for (int y = y0; y < y0 + h; y++) {
         for (int x = x0; x < x0 + w; x++) {
            set(px, x, y, c);
         }
      }
   }

   private static void set(int[] px, int x, int y, int c) {
      px[y * W + x] = c;
   }

   static int shade(int c, float k) {
      int r = (int)((c >> 16 & 0xFF) * k);
      int g = (int)((c >> 8 & 0xFF) * k);
      int b = (int)((c & 0xFF) * k);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }
}
