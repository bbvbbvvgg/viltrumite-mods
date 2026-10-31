package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Bag;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Bottom;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Eyewear;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Hair;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Hat;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Shoes;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook.Top;

/**
 * Paints the texture of one {@link CitizenLook} (pure, ARGB {@link #W} x {@link #H}).
 * The left 64x64 is the vanilla player skin layout (base and outer layers: the outer
 * layers carry hair volume, jackets and coats); the right half holds the citizen-only
 * parts of {@code client.city.CitizenModel} (hair shells and tails, hats, glasses, nose,
 * skirt or coat tail, bags, shoes, collar/scarf). Pixel art by rules: light from above
 * (faces and rows are shaded), fabric grain, seams, buttons, pockets, laces. Generated at
 * runtime, cached per look on the client: no texture files to ship.
 */
public final class CitizenPainter {
   public static final int W = 128;
   public static final int H = 64;

   // UV origins of the citizen-only parts (must match CitizenModel)
   public static final int[] HAIR_BIG = {64, 0};
   public static final int[] CROWN = {96, 0};
   public static final int[] VISOR = {96, 12};
   public static final int[] BUN = {116, 12};
   public static final int[] LONG_BACK = {64, 16};
   public static final int[] PONYTAIL = {84, 16};
   public static final int[] GLASSES = {92, 18};
   public static final int[] QUIFF = {110, 18};
   public static final int[] NOSE = {110, 24};
   public static final int[] SKIRT = {64, 30};
   public static final int[] BACKPACK = {88, 30};
   public static final int[] BAG = {106, 30};
   public static final int[] SHOE_R = {64, 42};
   public static final int[] SHOE_L = {82, 42};
   public static final int[] COLLAR = {100, 42};
   public static final int[] BRIM = {64, 50};

   enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

   @FunctionalInterface
   interface Shader {
      /** Colour (RGB; -1 = transparent) of pixel x,y on a face of size w x h. */
      int at(Face f, int x, int y, int w, int h);
   }

   private final int[] px = new int[W * H];
   private final CitizenLook look;
   private final int seed;

   private CitizenPainter(CitizenLook look) {
      this.look = look;
      this.seed = look.textureKey();
   }

   public static int[] paint(CitizenLook look) {
      CitizenPainter p = new CitizenPainter(look);
      p.draw();
      return p.px;
   }

   // ------------------------------------------------------------------ helpers

   static int scale(int rgb, float k) {
      int r = Math.min(255, Math.max(0, Math.round((rgb >> 16 & 0xFF) * k)));
      int g = Math.min(255, Math.max(0, Math.round((rgb >> 8 & 0xFF) * k)));
      int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * k)));
      return r << 16 | g << 8 | b;
   }

   static int mixRgb(int a, int b, float t) {
      int r = Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
      int g = Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
      int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
      return r << 16 | g << 8 | bl;
   }

   private static float light(Face f) {
      return switch (f) {
         case TOP -> 1.08F;
         case BOTTOM -> 0.72F;
         case FRONT -> 1.0F;
         case BACK -> 0.86F;
         default -> 0.92F;
      };
   }

   private int hash(int x, int y) {
      int h = x * 73856093 ^ y * 19349663 ^ this.seed * 83492791;
      h ^= h >>> 13;
      h *= 0x5bd1e995;
      h ^= h >>> 15;
      return h;
   }

   private void set(int x, int y, int rgb, float grain) {
      if (rgb >= 0 && x >= 0 && y >= 0 && x < W && y < H) {
         float k = 1.0F + ((this.hash(x, y) & 0xFF) / 255.0F - 0.5F) * grain;
         this.px[y * W + x] = 0xFF000000 | scale(rgb, k) & 0xFFFFFF;
      }
   }

   private void box(int[] uv, int w, int h, int d, float grain, Shader s) {
      this.box(uv[0], uv[1], w, h, d, grain, s);
   }

   /** Cube faces in the vanilla UV layout of a box of w x h x d texels at u,v, lit from above. */
   private void box(int u, int v, int w, int h, int d, float grain, Shader s) {
      this.face(u + d, v, w, d, Face.TOP, grain, s);
      this.face(u + d + w, v, w, d, Face.BOTTOM, grain, s);
      this.face(u, v + d, d, h, Face.RIGHT, grain, s);
      this.face(u + d, v + d, w, h, Face.FRONT, grain, s);
      this.face(u + d + w, v + d, d, h, Face.LEFT, grain, s);
      this.face(u + d + w + d, v + d, w, h, Face.BACK, grain, s);
   }

   private void face(int u, int v, int w, int h, Face f, float grain, Shader s) {
      float lf = light(f);
      for (int y = 0; y < h; y++) {
         // soft vertical gradient on the sides: lighter at the top, a touch of occlusion at the bottom
         float vg = f == Face.TOP || f == Face.BOTTOM || h < 3 ? 1.0F : 1.04F - 0.08F * y / (h - 1);
         for (int x = 0; x < w; x++) {
            int c = s.at(f, x, y, w, h);
            if (c >= 0) {
               this.set(u + x, v + y, scale(c, lf * vg), grain);
            }
         }
      }
   }

   /** Distance of a side-face column from the front edge (RIGHT runs back->front, LEFT front->back). */
   private static int fromFront(Face f, int x, int w) {
      return f == Face.RIGHT ? w - 1 - x : x;
   }

   // ------------------------------------------------------------------ drawing

   private void draw() {
      CitizenLook o = this.look;
      boolean slim = o.slim();
      int aw = slim ? 3 : 4;
      this.box(0, 0, 8, 8, 8, 0.05F, this.head());
      this.box(32, 0, 8, 8, 8, 0.07F, this.hairShell(0.5F));
      this.box(16, 16, 8, 12, 4, 0.07F, this.torso(false));
      this.box(16, 32, 8, 12, 4, 0.07F, this.torso(true));
      this.box(40, 16, aw, 12, 4, 0.06F, this.arm(false, true));
      this.box(32, 48, aw, 12, 4, 0.06F, this.arm(false, false));
      this.box(40, 32, aw, 12, 4, 0.06F, this.arm(true, true));
      this.box(48, 48, aw, 12, 4, 0.06F, this.arm(true, false));
      this.box(0, 16, 4, 12, 4, 0.06F, this.leg(true));
      this.box(16, 48, 4, 12, 4, 0.06F, this.leg(false));
      // parts of the citizen model
      this.box(HAIR_BIG, 8, 8, 8, 0.09F, this.hairShell(1.0F));
      this.box(LONG_BACK, 8, 11, 2, 0.08F, this.longHair());
      this.box(PONYTAIL, 2, 6, 2, 0.08F, (f, x, y, w, h) -> y % 3 == 2 ? scale(o.hairColor(), 0.85F) : o.hairColor());
      this.box(BUN, 3, 3, 3, 0.1F, (f, x, y, w, h) -> (x + y) % 2 == 0 ? o.hairColor() : scale(o.hairColor(), 0.88F));
      this.box(QUIFF, 4, 2, 3, 0.08F, (f, x, y, w, h) -> f == Face.TOP && y == 0 ? scale(o.hairColor(), 1.12F) : o.hairColor());
      this.box(NOSE, 2, 1, 1, 0.0F, (f, x, y, w, h) -> f == Face.BOTTOM ? scale(o.skinTone(), 0.7F) : scale(o.skinTone(), 0.97F));
      this.box(GLASSES, 8, 2, 1, 0.0F, this.glasses());
      this.box(CROWN, 8, 3, 8, 0.08F, this.crown());
      this.box(VISOR, 6, 1, 4, 0.05F, (f, x, y, w, h) -> f == Face.BOTTOM ? scale(o.hatColor(), 0.6F) : f == Face.TOP && y == h - 1 ? scale(o.hatColor(), 0.85F) : o.hatColor());
      this.box(BRIM, 12, 1, 12, 0.06F, (f, x, y, w, h) -> f == Face.TOP && (x == 0 || y == 0 || x == w - 1 || y == h - 1) ? scale(o.hatColor(), 0.85F) : o.hatColor());
      this.box(SKIRT, 8, 7, 4, 0.07F, this.skirt());
      this.box(BACKPACK, 6, 7, 3, 0.08F, this.backpack());
      this.box(BAG, 4, 4, 2, 0.06F, (f, x, y, w, h) -> f == Face.FRONT && y == 1 ? scale(o.bagColor(), 0.75F) : f == Face.FRONT && y == 2 && x == w / 2 ? 0xC9B26A : o.bagColor());
      Shader shoe = this.shoe();
      this.box(SHOE_R, 4, 2, 5, 0.05F, shoe);
      this.box(SHOE_L, 4, 2, 5, 0.05F, shoe);
      this.box(COLLAR, 8, 2, 4, 0.08F, this.collar());
   }

   private int shade(int c) {
      return scale(c, 0.82F);
   }

   private Shader head() {
      CitizenLook o = this.look;
      int skin = o.skinTone();
      int hair = o.hairColor();
      int dark = scale(skin, 0.84F);
      int lip = o.female() ? mixRgb(scale(skin, 0.82F), 0xB0485A, 0.45F) : scale(skin, 0.72F);
      Hair hs = o.hair();
      boolean bald = hs == Hair.BALD;
      // how many rows the hair covers on the sides and the back of the base head
      int side = switch (hs) {
         case LONG, CURLY_LONG, BRAIDS -> 7;
         case BOB -> 6;
         case PONYTAIL, BUN, MAN_BUN, CURLY -> 3;
         case BUZZ, PIXIE, SHORT, SIDE_PART, QUIFF -> 2;
         case BALD -> 0;
      };
      int back = switch (hs) {
         case BALD -> 0;
         case BUZZ -> 3;
         case SHORT, SIDE_PART, QUIFF, PIXIE -> 4;
         case CURLY, MAN_BUN -> 5;
         default -> 7;
      };
      return (f, x, y, w, h) -> {
         switch (f) {
            case TOP:
               if (bald) {
                  return x == 3 && y == 2 ? scale(skin, 1.12F) : skin; // shine
               }

               // a parting line on side parts, otherwise strands
               if (hs == Hair.SIDE_PART && x == 2) {
                  return scale(hair, 1.15F);
               }

               return (x + y) % 3 == 0 ? scale(hair, 0.9F) : hair;
            case BOTTOM:
               return hs.longBack() ? hair : o.beard() && y < 4 ? scale(hair, 0.9F) : dark;
            case BACK:
               if (bald) {
                  return y >= 3 && y <= 4 && o.hairColor() != skin ? scale(hair, 0.95F) : skin;
               }

               if (y < back) {
                  return y == back - 1 && back < 7 ? scale(hair, 0.85F) : hair;
               }

               return dark; // nape
            case RIGHT:
            case LEFT: {
               int ff = fromFront(f, x, w);
               if (bald) {
                  return y >= 3 && y <= 4 && ff >= 4 && o.hairColor() != skin ? scale(hair, 0.95F) : (ff == 4 && y == 4 ? dark : skin);
               }

               // sideburns at the front edge, the hairline curving back over the ear
               int cover = ff < 2 ? Math.min(side, 2) : ff >= 3 && ff <= 5 && side < 6 ? side : Math.max(side, back > 4 ? 3 : side);
               if (ff >= 5) {
                  cover = Math.max(cover, back);
               }

               if (y < cover) {
                  return y == cover - 1 ? scale(hair, 0.9F) : hair;
               }

               if (o.beard() && y >= 4 && ff < 4) {
                  return y == 4 && ff >= 2 ? scale(hair, 0.85F) : hair;
               }

               if (ff == 4 && (y == 4 || y == 5)) {
                  return y == 4 ? scale(skin, 0.9F) : dark; // ear
               }

               return skin;
            }
            case FRONT:
            default: {
               // hair: fringe and frame
               if (!bald) {
                  int fringe = switch (hs) {
                     case BUZZ -> 0;
                     case BOB, PIXIE, CURLY, CURLY_LONG -> 2;
                     default -> 1;
                  };
                  if (y < fringe || y == fringe && fringe > 0 && (hs == Hair.PIXIE || hs == Hair.SIDE_PART ? x < 5 : x == 0 || x == 7)) {
                     return y == fringe ? scale(hair, 0.88F) : hair;
                  }

                  if (hs == Hair.BUZZ && y == 0) {
                     return scale(hair, 1.0F);
                  }

                  if ((hs == Hair.LONG || hs == Hair.BOB || hs == Hair.CURLY_LONG || hs == Hair.BRAIDS) && (x == 0 || x == 7) && y <= 6) {
                     return scale(hair, 0.92F);
                  }
               }

               if (y == 0 && (bald || hs == Hair.BUZZ)) {
                  return hs == Hair.BUZZ ? scale(hair, 0.9F) : scale(skin, 1.05F);
               }

               // brows
               if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) {
                  int brow = bald ? scale(skin, 0.6F) : scale(hair, hair > 0x808080 ? 0.7F : 0.95F);
                  return (x == 1 || x == 6) && !o.female() ? brow : (x == 2 || x == 5) ? brow : scale(brow, 1.1F);
               }

               // eyes: white outside, iris inside, a lash for women
               if (y == 4) {
                  if (x == 1 || x == 6) {
                     return o.female() ? 0xE8E4E0 : 0xF2F0EC;
                  }

                  if (x == 2 || x == 5) {
                     return o.eyeColor();
                  }

                  if (x == 3 || x == 4) {
                     return scale(skin, 0.95F); // nose bridge
                  }
               }

               if (y == 3 && o.female() && (x == 0 || x == 7) && hs != Hair.LONG && hs != Hair.BOB) {
                  return scale(skin, 0.96F);
               }

               // nose shadow, cheeks, mouth, chin
               if (y == 5) {
                  if (x == 3 || x == 4) {
                     return scale(skin, 0.86F);
                  }

                  if ((x == 1 || x == 6) && o.female()) {
                     return mixRgb(skin, 0xE08A8A, 0.22F);
                  }
               }

               if (o.beard()) {
                  if (y == 6 && (x == 3 || x == 4)) {
                     return scale(lip, 0.95F);
                  }

                  if (y >= 5 && (y >= 6 || x <= 1 || x >= 6)) {
                     return (x + y) % 2 == 0 ? hair : scale(hair, 0.88F);
                  }
               }

               if (y == 6 && x >= 2 && x <= 5) {
                  return x == 2 || x == 5 ? scale(skin, 0.9F) : lip;
               }

               if (y == 7) {
                  return x >= 2 && x <= 5 ? scale(skin, 0.93F) : dark;
               }

               return skin;
            }
         }
      };
   }

   /** Outer hair volume: the vanilla hat layer (inflate 0.5) or the big shell (inflate 1.0, curls and bobs). */
   private Shader hairShell(float inflate) {
      CitizenLook o = this.look;
      Hair hs = o.hair();
      int hair = o.hairColor();
      boolean big = inflate > 0.75F;
      boolean on = big == hs.voluminous() && hs != Hair.BALD && hs != Hair.BUZZ;
      boolean hatOn = o.hat() != Hat.NONE;
      int rows = switch (hs) {
         case LONG, CURLY_LONG, BRAIDS -> 7;
         case BOB -> 6;
         case CURLY -> 3;
         case PONYTAIL, BUN, MAN_BUN -> 2;
         default -> 1;
      };
      int backRows = switch (hs) {
         case LONG, CURLY_LONG, BRAIDS, BOB -> 8;
         case PONYTAIL, BUN, CURLY -> 5;
         default -> 3;
      };
      return (f, x, y, w, h) -> {
         if (!on) {
            return -1;
         }

         int c = big ? ((x / 2 + y / 2) % 2 == 0 ? hair : scale(hair, 0.86F)) : (x * 3 + y) % 5 == 0 ? scale(hair, 0.9F) : hair;
         // under a hat only what shows below it
         int top = hatOn ? 3 : 0;
         switch (f) {
            case TOP:
               return hatOn ? -1 : c;
            case BOTTOM:
               return -1;
            case FRONT:
               if (y < top) {
                  return -1;
               }

               if (y == 0 || y == 1 && (hs == Hair.BOB || hs == Hair.CURLY || hs == Hair.CURLY_LONG) || hs == Hair.PIXIE && y == 1 && x < 5) {
                  return c;
               }

               return (x == 0 || x == 7) && y < rows ? c : -1;
            case BACK:
               return y >= top && y < backRows ? c : -1;
            default: {
               int ff = fromFront(f, x, w);
               if (y < top) {
                  return -1;
               }

               int r = ff >= 4 ? Math.max(rows, backRows - 1) : rows;
               return y < r ? c : -1;
            }
         }
      };
   }

   private Shader longHair() {
      CitizenLook o = this.look;
      int hair = o.hairColor();
      boolean braids = o.hair() == Hair.BRAIDS;
      return (f, x, y, w, h) -> {
         if (f == Face.FRONT) {
            return -1; // against the back of the head
         }

         if (braids) {
            return (y + (x & 1)) % 2 == 0 ? hair : scale(hair, 0.78F);
         }

         int c = x % 3 == 1 ? scale(hair, 0.9F) : hair;
         return y == h - 1 ? ((x & 1) == 0 ? scale(c, 0.85F) : -1) : c; // uneven tips
      };
   }

   private Shader glasses() {
      CitizenLook o = this.look;
      boolean sun = o.eyewear() == Eyewear.SUNGLASSES;
      int frame = sun ? 0x141414 : ((o.seed() & 4) == 0 ? 0x2A2A2E : 0x6B4A2E);
      return (f, x, y, w, h) -> {
         if (o.eyewear() == Eyewear.NONE) {
            return -1;
         }

         if (f != Face.FRONT) {
            return f == Face.TOP || f == Face.BOTTOM ? -1 : frame;
         }

         if (y == 0) {
            return x == 0 || x == 7 ? -1 : frame;
         }

         // lenses over the eyes (x 1-2 and 5-6), the bridge and the frame edges
         if (x == 0 || x == 7 || x == 3 || x == 4) {
            return x == 3 || x == 4 ? -1 : frame;
         }

         return sun ? (x == 1 || x == 5 ? 0x2A3A4A : 0x10161C) : -1;
      };
   }

   private Shader crown() {
      CitizenLook o = this.look;
      int c = o.hatColor();
      return (f, x, y, w, h) -> {
         switch (o.hat()) {
            case CAP:
               if (f == Face.BOTTOM) {
                  return -1;
               }

               if (f == Face.TOP) {
                  return x == w / 2 && y == h / 2 ? scale(c, 0.7F) : (x == 3 || x == 4) ? scale(c, 0.92F) : c;
               }

               if (f == Face.FRONT && y == 1 && (x == 3 || x == 4)) {
                  return o.accent(); // a little logo
               }

               return y == h - 1 && f == Face.BACK && x >= 2 && x <= 5 ? -1 : c;
            case BEANIE:
               if (f == Face.BOTTOM) {
                  return -1;
               }

               if (f == Face.TOP) {
                  return x == 3 && y == 3 ? scale(c, 1.2F) : c;
               }

               return y == h - 1 ? scale(c, 0.82F) : (x % 2 == 0 ? c : scale(c, 0.9F)); // ribbing and the folded edge
            case FEDORA:
               if (f == Face.BOTTOM) {
                  return -1;
               }

               if (f == Face.TOP) {
                  return y >= 3 && y <= 4 ? scale(c, 0.8F) : c; // the pinch
               }

               return y == h - 1 ? 0x1A1414 : c; // band
            default:
               return -1;
         }
      };
   }

   private boolean longSleeves() {
      Top t = this.look.top();
      return t != Top.TEE && t != Top.DRESS && t != Top.VEST && !(t == Top.BLOUSE && (this.look.seed() & 8) == 0)
         && !(t == Top.SHIRT && (this.look.seed() & 16) == 0 && this.look.bottom() == Bottom.SHORTS);
   }

   /** Outer layer garments: open over the body and the sleeves (volume). */
   private boolean outer() {
      Top t = this.look.top();
      return t == Top.JACKET || t == Top.COAT || t == Top.SUIT || t == Top.HOODIE;
   }

   private Shader torso(boolean overlay) {
      CitizenLook o = this.look;
      Top t = o.top();
      int c = o.topColor();
      int acc = o.accent();
      int skin = o.skinTone();
      boolean tie = t == Top.SUIT || t == Top.SHIRT && (o.seed() & 32) != 0;
      int tieColor = new int[]{0xA8232A, 0x1F2A44, 0x2E5C8A, 0x3E5A3A, 0x6C4A8E, 0x8A6A4A}[Math.floorMod(o.seed() >> 6, 6)];
      int belt = CitizenLook.LEATHER[Math.floorMod(o.seed() >> 3, CitizenLook.LEATHER.length)];
      boolean print = t == Top.TEE && (o.seed() & 64) != 0;
      return (f, x, y, w, h) -> {
         if (f == Face.TOP) {
            if (overlay) {
               return -1;
            }

            return t == Top.DRESS || t == Top.BLOUSE || t == Top.TEE ? (x >= 2 && x <= 5 && y >= 1 && y <= 2 ? skin : c) : c;
         }

         if (f == Face.BOTTOM) {
            return overlay ? -1 : this.legColor(true);
         }

         if (overlay) {
            if (!this.outer()) {
               return -1;
            }

            // jackets, coats and suit jackets hang open in front over what is under them
            if (f == Face.FRONT) {
               if (t == Top.HOODIE) {
                  // hoodie: pocket and drawstrings
                  if (y >= 7 && y <= 9 && x >= 2 && x <= 5) {
                     return y == 7 || x == 2 || x == 5 ? scale(c, 0.8F) : scale(c, 0.93F);
                  }

                  if ((x == 3 || x == 4) && y >= 1 && y <= 3) {
                     return y == 3 ? 0xD8D8D8 : 0xEDEDED;
                  }

                  return y == 11 ? scale(c, 0.85F) : c;
               }

               int open = t == Top.SUIT ? Math.max(0, 3 - y / 2) : t == Top.COAT ? (y < 3 ? 1 : 0) : 1;
               if (x >= 4 - open && x <= 3 + open) {
                  return -1;
               }

               if (x == 3 - open || x == 4 + open) {
                  return scale(c, t == Top.SUIT ? 0.8F : 0.86F); // lapel / zip edge
               }

               if (t == Top.COAT && (x == 2 || x == 5) && (y == 4 || y == 7 || y == 10)) {
                  return 0x1A1612; // double-breasted buttons
               }

               if (t == Top.SUIT && x == 4 && (y == 7 || y == 9)) {
                  return scale(c, 0.6F);
               }

               if (t == Top.SUIT && x == 1 && y == 3) {
                  return (o.seed() & 128) != 0 ? 0xEDEBE6 : scale(c, 0.9F); // pocket square
               }

               if (t == Top.JACKET && (x == 1 || x == 6) && y == 8) {
                  return scale(c, 0.75F); // pocket flaps
               }

               return y == 11 ? scale(c, 0.85F) : c;
            }

            if (f == Face.BACK && t == Top.HOODIE && y <= 3 && x >= 1 && x <= 6) {
               return y == 3 || x == 1 || x == 6 ? scale(c, 0.78F) : scale(c, 0.9F); // the hood
            }

            return y == 11 ? scale(c, 0.85F) : c;
         }

         // base body: what is under (shirt under a jacket) or the garment itself
         int base = this.outer() && t != Top.HOODIE ? acc : c;
         if (t == Top.VEST) {
            base = acc;
         }

         if (y >= 11 && t != Top.DRESS && t != Top.COAT && t != Top.HOODIE && t != Top.SWEATER) {
            // waistband, belt with buckle in front
            boolean tucked = t == Top.SHIRT || t == Top.SUIT || t == Top.BLOUSE;
            if (tucked) {
               return f == Face.FRONT && (x == 3 || x == 4) ? 0xC9B26A : belt;
            }
         }

         if (f == Face.FRONT) {
            if (o.bag() == Bag.SHOULDER && x == 6 - y * 6 / 9 && y <= 9) {
               return scale(o.bagColor(), 0.9F); // strap across the chest
            }

            if (o.bag() == Bag.BACKPACK && (x == 1 || x == 6) && y <= 7) {
               return scale(o.bagColor(), 0.8F);
            }

            switch (t) {
               case TEE:
                  if (y == 0 && x >= 3 && x <= 4) {
                     return skin;
                  }

                  if (print && y >= 3 && y <= 5 && x >= 2 && x <= 5) {
                     return (x + y) % 2 == 0 ? acc : scale(acc, 0.8F);
                  }

                  return y == 11 ? scale(c, 0.86F) : c;
               case SHIRT:
               case SUIT:
               case JACKET:
               case COAT:
                  if (y == 0 && (x == 2 || x == 5)) {
                     return scale(base, 1.06F); // collar points
                  }

                  if (tie && (x == 3 || x == 4) && y >= 1 && y <= 8) {
                     return y == 1 ? scale(tieColor, 0.85F) : y == 8 && x == 4 ? base : tieColor;
                  }

                  if (!tie && (x == 3 || x == 4) && y == 0) {
                     return skin;
                  }

                  if (x == 4 && !tie && (y == 3 || y == 6 || y == 9)) {
                     return scale(base, 0.78F); // buttons
                  }

                  return base;
               case HOODIE:
                  return c;
               case SWEATER:
                  if (y == 0) {
                     return x >= 2 && x <= 5 ? acc : scale(c, 0.9F); // collar of the shirt under it
                  }

                  return y == 11 || y == 10 ? scale(c, 0.85F) : (y % 2 == 0 ? c : scale(c, 0.94F));
               case BLOUSE:
                  if (y <= 2 && x >= 3 && x <= 4 || y == 0 && (x == 2 || x == 5)) {
                     return y == 0 && (x == 2 || x == 5) ? scale(c, 1.05F) : skin;
                  }

                  return (x == 3 || x == 4) && y >= 4 && y % 3 == 1 ? scale(c, 0.82F) : c;
               case DRESS:
                  if (y <= 1 && x >= 2 && x <= 5) {
                     return y == 1 && (x == 2 || x == 5) ? c : skin; // scoop neck
                  }

                  return y == 7 ? acc : c; // waist band
               case VEST:
                  if (x <= 1 || x >= 6) {
                     return y == 0 ? acc : scale(c, 1.0F);
                  }

                  return y == 0 && (x == 3 || x == 4) ? skin : acc;
               default:
                  return c;
            }
         }

         if (f == Face.BACK) {
            if (o.bag() == Bag.SHOULDER && x == 1 + y * 6 / 9 && y <= 9) {
               return scale(o.bagColor(), 0.9F);
            }

            if (t == Top.VEST) {
               return y == 0 ? acc : c;
            }

            if (t == Top.DRESS && y == 7) {
               return acc;
            }

            return y == 0 && base != c && this.outer() ? scale(base, 1.05F) : base;
         }

         // sides
         if (t == Top.VEST) {
            return acc;
         }

         if (t == Top.DRESS && y == 7) {
            return acc;
         }

         return base;
      };
   }

   private Shader arm(boolean overlay, boolean right) {
      CitizenLook o = this.look;
      int skin = o.skinTone();
      Top t = o.top();
      int c = o.topColor();
      int under = this.outer() && t != Top.HOODIE ? o.accent() : c;
      boolean sleeves = this.longSleeves();
      boolean watch = !right && (o.seed() & 512) != 0;
      return (f, x, y, w, h) -> {
         if (overlay) {
            if (!this.outer()) {
               return -1;
            }

            if (f == Face.BOTTOM) {
               return -1;
            }

            if (y > 9) {
               return -1;
            }

            return y == 9 ? scale(c, 0.82F) : c; // cuff
         }

         if (f == Face.BOTTOM) {
            return scale(skin, 0.9F); // the hand
         }

         int sleeveEnd = sleeves ? 10 : t == Top.VEST ? 0 : 4;
         if (f == Face.TOP) {
            return sleeveEnd > 0 ? (t == Top.VEST ? skin : under) : skin;
         }

         if (y < sleeveEnd) {
            int sc = t == Top.VEST ? skin : under;
            return y == sleeveEnd - 1 && sleeveEnd > 4 ? scale(sc, 0.86F) : y == sleeveEnd - 1 ? scale(sc, 0.9F) : sc;
         }

         if (watch && y == 10) {
            return f == Face.FRONT ? 0xD8D8D8 : 0x2A2A2E;
         }

         // hand: knuckles a shade darker at the bottom
         return y == h - 1 ? scale(skin, 0.9F) : skin;
      };
   }

   private int legColor(boolean top) {
      CitizenLook o = this.look;
      return switch (o.bottom()) {
         case SKIRT -> o.top() == Top.DRESS ? o.topColor() : o.bottomColor();
         default -> o.bottomColor();
      };
   }

   private Shader leg(boolean right) {
      CitizenLook o = this.look;
      int skin = o.skinTone();
      int c = o.bottomColor();
      Bottom b = o.bottom();
      boolean tights = b == Bottom.SKIRT && (o.seed() & 1024) != 0;
      int tightColor = tights ? 0x1E1E22 : skin;
      int shoe = o.shoeColor();
      return (f, x, y, w, h) -> {
         if (f == Face.TOP) {
            return c;
         }

         if (f == Face.BOTTOM) {
            return o.shoes() == Shoes.SNEAKERS ? 0xEDEDED : 0x1A1412;
         }

         if (y >= 11) {
            return shoe; // the shoe part covers this, kept for far views
         }

         switch (b) {
            case SHORTS:
               if (y >= 6) {
                  return y == 6 ? scale(skin, 0.9F) : skin;
               }

               return y == 5 ? scale(c, 0.85F) : c;
            case SKIRT:
               return y < 5 ? c : tightColor;
            case LEGGINGS:
               return y == 10 ? scale(c, 0.85F) : c;
            case JEANS: {
               int ff = f == Face.RIGHT || f == Face.LEFT ? fromFront(f, x, w) : -1;
               if (ff == 1 || ff == 2) {
                  return mixRgb(c, 0xC9A35C, 0.25F); // outer seam stitching
               }

               if (f == Face.FRONT && y == 1 && (right ? x == 1 : x == 2)) {
                  return scale(c, 0.75F); // pocket
               }

               if (f == Face.BACK && y >= 1 && y <= 3 && x >= 1 && x <= 2) {
                  return y == 1 ? scale(c, 0.8F) : scale(c, 0.95F);
               }

               if (y >= 4 && y <= 7 && f == Face.FRONT && (x == 1 || x == 2)) {
                  return scale(c, 1.08F); // worn knees
               }

               return y == 10 ? scale(c, 0.85F) : c;
            }
            case TROUSERS:
            case CHINOS:
            default:
               if (f == Face.FRONT && (right ? x == 2 : x == 1) && y >= 2) {
                  return scale(c, 0.9F); // crease
               }

               return y == 10 ? scale(c, 0.88F) : c;
         }
      };
   }

   private Shader skirt() {
      CitizenLook o = this.look;
      boolean coat = o.top() == Top.COAT;
      int c = coat ? o.topColor() : o.top() == Top.DRESS ? o.topColor() : o.bottomColor();
      return (f, x, y, w, h) -> {
         if (f == Face.TOP || f == Face.BOTTOM) {
            return -1;
         }

         if (coat) {
            if (f == Face.FRONT && (x == 3 || x == 4)) {
               return y < 2 ? scale(c, 0.86F) : -1; // the coat is open below the waist
            }

            if (f == Face.BACK && x == 4 && y >= 3) {
               return scale(c, 0.7F); // back vent
            }

            return y == h - 1 ? scale(c, 0.82F) : c;
         }

         if (y > 4) {
            return -1; // the skirt ends above the knee; the box is long for coats only
         }

         return y == 4 ? scale(c, 0.82F) : x % 2 == 0 ? c : scale(c, 0.92F); // pleats
      };
   }

   private Shader backpack() {
      CitizenLook o = this.look;
      int c = o.bagColor();
      return (f, x, y, w, h) -> {
         if (f == Face.BACK) {
            if (y >= 3 && y <= 5 && x >= 1 && x <= 4) {
               return y == 3 ? scale(c, 0.75F) : scale(c, 0.92F); // front pocket
            }

            if (y == 1 && x >= 1 && x <= 4) {
               return 0x2A2A2E; // zip
            }
         }

         return f == Face.TOP ? scale(c, 1.05F) : c;
      };
   }

   private Shader shoe() {
      CitizenLook o = this.look;
      int c = o.shoeColor();
      Shoes s = o.shoes();
      int sole = s == Shoes.SNEAKERS ? 0xF4F4F4 : s == Shoes.BOOTS ? 0x2A1E16 : 0x141210;
      return (f, x, y, w, h) -> {
         if (f == Face.BOTTOM) {
            return sole;
         }

         if (f == Face.TOP) {
            if (s == Shoes.SNEAKERS && x >= 1 && x <= 2 && y >= 1 && y <= 3) {
               return y % 2 == 1 ? 0xF4F4F4 : scale(c, 0.9F); // laces
            }

            if (s == Shoes.HEELS && y < 3) {
               return scale(o.skinTone(), 0.95F); // open top of the foot
            }

            return c;
         }

         if (y == h - 1) {
            return s == Shoes.HEELS && f != Face.FRONT ? (f == Face.BACK ? scale(c, 0.6F) : -1) : sole;
         }

         if (s == Shoes.SNEAKERS && f != Face.FRONT && f != Face.BACK && x % 2 == 0) {
            return mixRgb(c, o.accent(), 0.5F); // side stripe
         }

         return f == Face.FRONT ? scale(c, 1.05F) : c;
      };
   }

   private Shader collar() {
      CitizenLook o = this.look;
      boolean scarf = o.scarf();
      int c = scarf ? o.scarfColor() : o.topColor();
      boolean collar = o.top() == Top.COAT || o.top() == Top.JACKET && (o.seed() & 2048) != 0;
      return (f, x, y, w, h) -> {
         if (!scarf && !collar) {
            return -1;
         }

         if (f == Face.BOTTOM) {
            return -1;
         }

         if (f == Face.TOP) {
            return x >= 1 && x <= w - 2 && y >= 1 && y <= h - 2 ? -1 : c;
         }

         if (!scarf && f == Face.FRONT && x >= 2 && x <= 5) {
            return -1; // the collar is open in front
         }

         if (scarf) {
            return (x + (o.seed() & 1)) % 3 == 0 ? scale(c, 0.8F) : c; // stripes
         }

         return y == 0 ? scale(c, 1.05F) : scale(c, 0.92F);
      };
   }
}
