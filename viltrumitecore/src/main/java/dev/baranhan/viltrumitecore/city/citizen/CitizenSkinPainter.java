package dev.baranhan.viltrumitecore.city.citizen;

/**
 * Draws the civilian skins of city pedestrians (pure, ARGB 64x64 in the vanilla player
 * skin layout). Sixteen hand-picked outfits of a modern city: suits, hoodies, jeans,
 * dresses, uniforms. Even indices use the wide player model, odd ones the slim model
 * (see {@link CitizenEntity#slim()}). Generated at runtime: no texture files to ship.
 */
public final class CitizenSkinPainter {
   public static final int SIZE = 64;

   enum Top { TEE, LONG, SUIT, HOODIE, JACKET, DRESS, POLO, BLOUSE }

   enum Bottom { JEANS, TROUSERS, SHORTS, SKIRT, LEGGINGS }

   enum Hair { SHORT, BUZZ, LONG, BOB, BALD, CAP, PONYTAIL, CURLY }

   record Outfit(int skin, int hair, Hair hairStyle, boolean beard, int top, int accent, Top topStyle, int bottom, Bottom bottomStyle, int shoes) {
   }

   private static final int[] TONES = {0xF2CBA6, 0xE0AC80, 0xC68B5E, 0x9E6B45, 0x74482C, 0x5A3622, 0xF5D7BC, 0xD9A27A};

   /** index: even = wide arms, odd = slim arms. */
   static final Outfit[] OUTFITS = {
      new Outfit(TONES[0], 0x3B2A1E, Hair.SHORT, false, 0x1F2A44, 0xA8232A, Top.SUIT, 0x1F2A44, Bottom.TROUSERS, 0x141414),
      new Outfit(TONES[1], 0x5A3A22, Hair.LONG, false, 0xE8E4DA, 0x2E5C8A, Top.BLOUSE, 0x2C3E66, Bottom.JEANS, 0x6B4A2E),
      new Outfit(TONES[3], 0x161210, Hair.BUZZ, false, 0x3A3F45, 0xC9C9C9, Top.HOODIE, 0x2E3A55, Bottom.JEANS, 0xE6E6E6),
      new Outfit(TONES[6], 0xD8B46A, Hair.PONYTAIL, false, 0xC23B4E, 0xF2F2F2, Top.DRESS, 0xC23B4E, Bottom.SKIRT, 0x2A1A12),
      new Outfit(TONES[2], 0x241A14, Hair.SHORT, true, 0x5E7A4A, 0x2B2B2B, Top.JACKET, 0x3C3C40, Bottom.TROUSERS, 0x4A3222),
      new Outfit(TONES[4], 0x120E0C, Hair.CURLY, false, 0xF0C64A, 0xFFFFFF, Top.TEE, 0x3B4E7A, Bottom.JEANS, 0xF2F2F2),
      new Outfit(TONES[0], 0x8A8A8A, Hair.BALD, true, 0x54575C, 0x2E4A7A, Top.SUIT, 0x54575C, Bottom.TROUSERS, 0x2A1A10),
      new Outfit(TONES[7], 0x2A1C14, Hair.BOB, false, 0x22262C, 0x8A1C2C, Top.SUIT, 0x22262C, Bottom.SKIRT, 0x101010),
      new Outfit(TONES[5], 0x0E0B0A, Hair.CAP, false, 0x2F6FB0, 0xF2F2F2, Top.TEE, 0x7A6A55, Bottom.SHORTS, 0x2B2B2B),
      new Outfit(TONES[2], 0x1C1410, Hair.LONG, false, 0x6C4A8E, 0x2A2A2A, Top.HOODIE, 0x1E1E22, Bottom.LEGGINGS, 0xEDEDED),
      new Outfit(TONES[1], 0x6A4A2C, Hair.SHORT, false, 0x8CB4D8, 0x1E3456, Top.LONG, 0x2A2F3A, Bottom.TROUSERS, 0x3A2416),
      new Outfit(TONES[0], 0xA0522D, Hair.PONYTAIL, false, 0x2E8B76, 0xF3EBDD, Top.POLO, 0xD9CBB0, Bottom.SHORTS, 0xF5F5F5),
      new Outfit(TONES[3], 0x1A1410, Hair.SHORT, true, 0x7A2E2E, 0xD9D9D9, Top.POLO, 0x34404F, Bottom.JEANS, 0x5A3A22),
      new Outfit(TONES[6], 0xE6CF8E, Hair.BOB, false, 0xE5DCCB, 0x9A6A3A, Top.JACKET, 0x2B3E6B, Bottom.JEANS, 0x8A5A3A),
      new Outfit(TONES[7], 0x3A2618, Hair.CAP, true, 0xD0D0D0, 0xB02020, Top.HOODIE, 0x394B3A, Bottom.TROUSERS, 0x202020),
      new Outfit(TONES[4], 0x16100C, Hair.CURLY, false, 0x1F7A8C, 0xF0E0A0, Top.DRESS, 0x1F7A8C, Bottom.SKIRT, 0xB5874F),
   };

   public static final int COUNT = OUTFITS.length;

   private enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

   @FunctionalInterface
   private interface Shader {
      /** Color (RGB, or -1 to leave transparent) of pixel x,y on a face of size w x h. */
      int at(Face f, int x, int y, int w, int h);
   }

   private final int[] px = new int[SIZE * SIZE];
   private final int seed;

   private CitizenSkinPainter(int seed) {
      this.seed = seed;
   }

   /** ARGB pixels (row-major, 64 x 64) of skin {@code index}. */
   public static int[] paint(int index) {
      int i = Math.floorMod(index, COUNT);
      CitizenSkinPainter p = new CitizenSkinPainter(i * 7919 + 17);
      p.draw(OUTFITS[i], (i & 1) == 1);
      return p.px;
   }

   private void set(int x, int y, int rgb) {
      if (rgb >= 0 && x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
         this.px[y * SIZE + x] = 0xFF000000 | rgb & 0xFFFFFF;
      }
   }

   /** Cube faces in the vanilla UV layout of a box of w x h x d texels at u,v. */
   private void box(int u, int v, int w, int h, int d, Shader s) {
      this.face(u + d, v, w, d, Face.TOP, s);
      this.face(u + d + w, v, w, d, Face.BOTTOM, s);
      this.face(u, v + d, d, h, Face.RIGHT, s);
      this.face(u + d, v + d, w, h, Face.FRONT, s);
      this.face(u + d + w, v + d, d, h, Face.LEFT, s);
      this.face(u + d + w + d, v + d, w, h, Face.BACK, s);
   }

   private void face(int u, int v, int w, int h, Face f, Shader s) {
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int c = s.at(f, x, y, w, h);
            if (c >= 0) {
               this.set(u + x, v + y, this.grain(c, u + x, v + y));
            }
         }
      }
   }

   /** Subtle per-texel brightness variation so flat colours read as fabric. */
   private int grain(int rgb, int x, int y) {
      int h = (x * 73856093) ^ (y * 19349663) ^ this.seed * 83492791;
      h ^= h >>> 13;
      h *= 0x5bd1e995;
      h ^= h >>> 15;
      float k = 1.0F + ((h & 0xFF) / 255.0F - 0.5F) * 0.08F;
      return scale(rgb, k);
   }

   static int scale(int rgb, float k) {
      int r = Math.min(255, Math.max(0, Math.round((rgb >> 16 & 0xFF) * k)));
      int g = Math.min(255, Math.max(0, Math.round((rgb >> 8 & 0xFF) * k)));
      int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * k)));
      return r << 16 | g << 8 | b;
   }

   private void draw(Outfit o, boolean slim) {
      int aw = slim ? 3 : 4;
      Shader head = this.head(o);
      this.box(0, 0, 8, 8, 8, head);
      if (o.hairStyle == Hair.CAP) {
         this.box(32, 0, 8, 8, 8, (f, x, y, w, h) -> f == Face.TOP || f != Face.BOTTOM && y <= 2 ? (y == 2 && f == Face.FRONT ? scale(o.accent, 0.8F) : o.accent) : -1);
      }

      Shader torso = this.torso(o);
      this.box(16, 16, 8, 12, 4, torso);
      Shader arm = this.arm(o);
      this.box(40, 16, aw, 12, 4, arm);
      this.box(32, 48, aw, 12, 4, arm);
      Shader leg = this.leg(o);
      this.box(0, 16, 4, 12, 4, leg);
      this.box(16, 48, 4, 12, 4, leg);
   }

   private Shader head(Outfit o) {
      int skin = o.skin;
      int hair = o.hair;
      int shade = scale(skin, 0.86F);
      return (f, x, y, w, h) -> {
         boolean bald = o.hairStyle == Hair.BALD;
         int depth = switch (o.hairStyle) {
            case LONG, PONYTAIL -> 8;
            case BOB, CURLY -> 6;
            case SHORT, CAP -> 3;
            case BUZZ -> 2;
            case BALD -> 1;
         };
         switch (f) {
            case TOP:
               return bald ? skin : hair;
            case BOTTOM:
               return o.hairStyle == Hair.LONG ? hair : shade;
            case BACK:
               if (o.hairStyle == Hair.PONYTAIL && x >= 3 && x <= 4) {
                  return scale(hair, 0.85F);
               }

               return y < depth && !(bald && y > 0) ? hair : bald && y == 0 ? scale(o.hair, 1.0F) : skin;
            case RIGHT:
            case LEFT: {
               // x runs back -> front on the right face and front -> back on the left face
               int fromFront = f == Face.RIGHT ? w - 1 - x : x;
               int d = depth >= 6 ? (fromFront < 2 ? 2 : depth) : fromFront < 2 ? Math.min(depth, 2) : depth;
               if (bald) {
                  return y == 0 && fromFront > 2 ? o.hair : skin;
               }

               if (y < d) {
                  return hair;
               }

               if (o.beard && y >= 5 && fromFront < 4) {
                  return scale(hair, 1.05F);
               }

               return fromFront == 4 && y == 4 ? shade : skin; // ear shadow
            }
            case FRONT:
            default: {
               // hair fringe / frame
               if (!bald) {
                  int fringe = switch (o.hairStyle) {
                     case BUZZ -> 0;
                     case CURLY, BOB -> 2;
                     default -> 1;
                  };
                  if (y <= fringe && !(o.hairStyle == Hair.SHORT && y == 1 && x >= 2 && x <= 5)) {
                     return hair;
                  }

                  if ((o.hairStyle == Hair.LONG || o.hairStyle == Hair.BOB || o.hairStyle == Hair.CURLY) && (x == 0 || x == 7) && y <= 6) {
                     return hair;
                  }
               } else if (y == 0) {
                  return shade;
               }

               if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) {
                  return bald ? scale(skin, 0.7F) : scale(hair, 0.9F); // brows
               }

               if (y == 4) {
                  if (x == 1 || x == 6) {
                     return 0xF4F4F4;
                  }

                  if (x == 2 || x == 5) {
                     return 0x2A3A4A;
                  }
               }

               if (y == 5 && (x == 3 || x == 4)) {
                  return shade; // nose
               }

               if (o.beard && y >= 5 && (x <= 2 || x >= 5 || y == 7)) {
                  return hair;
               }

               if (y == 6 && x >= 3 && x <= 4) {
                  return scale(0xA0504A, skin > 0xC00000 ? 1.0F : 0.7F); // mouth
               }

               return skin;
            }
         }
      };
   }

   private Shader torso(Outfit o) {
      int c = o.top;
      int a = o.accent;
      int dark = scale(c, 0.78F);
      return (f, x, y, w, h) -> {
         if (f == Face.BOTTOM) {
            return o.topStyle == Top.DRESS ? c : o.bottom;
         }

         if (f == Face.TOP) {
            return o.topStyle == Top.HOODIE ? dark : c;
         }

         // waistband / belt on the last row
         if (y == h - 1 && o.topStyle != Top.DRESS) {
            return o.topStyle == Top.SUIT || o.bottomStyle == Bottom.TROUSERS ? 0x1A1410 : scale(o.bottom, 0.9F);
         }

         switch (o.topStyle) {
            case SUIT:
               if (f == Face.FRONT) {
                  if (x >= 3 && x <= 4 && y <= 7) {
                     return y == 0 ? a : y <= 7 ? (x == 3 || y < 6 ? a : scale(a, 0.8F)) : c; // tie
                  }

                  if ((x == 2 || x == 5) && y <= 4) {
                     return 0xF2F2F2; // shirt
                  }

                  if ((x == 1 && y <= 6 || x == 6 && y <= 6) && y >= 1) {
                     return dark; // lapels
                  }

                  if (x == 4 && (y == 8 || y == 10)) {
                     return 0x111111; // buttons
                  }
               }

               return c;
            case HOODIE:
               if (f == Face.FRONT) {
                  if ((x == 2 || x == 5) && y >= 1 && y <= 3) {
                     return a; // strings
                  }

                  if (y == 0 && x >= 1 && x <= 6) {
                     return dark; // hood rim
                  }

                  if (y >= 7 && y <= 9 && x >= 1 && x <= 6) {
                     return y == 7 ? scale(c, 0.7F) : dark; // pocket
                  }
               }

               if (f == Face.BACK && y <= 3) {
                  return dark; // hood
               }

               return c;
            case JACKET:
               if (f == Face.FRONT) {
                  if (x >= 2 && x <= 5) {
                     return y == 0 && (x == 2 || x == 5) ? scale(a, 0.8F) : a; // tee under an open jacket
                  }

                  return x == 1 || x == 6 ? dark : c;
               }

               return c;
            case DRESS:
               if (y == 6) {
                  return dark; // waist
               }

               if (f == Face.FRONT && y == 0 && x >= 2 && x <= 5) {
                  return o.skin; // neckline
               }

               return c;
            case POLO:
               if (f == Face.FRONT && y <= 1 && x >= 2 && x <= 5) {
                  return y == 0 ? a : (x == 3 || x == 4 ? o.skin : a); // collar
               }

               if (f == Face.FRONT && x == 4 && y >= 1 && y <= 3) {
                  return a;
               }

               return c;
            case BLOUSE:
               if (f == Face.FRONT && y == 0 && x >= 3 && x <= 4) {
                  return o.skin;
               }

               if (f == Face.FRONT && x == 4 && y % 3 == 1) {
                  return a;
               }

               return c;
            case LONG:
               if (f == Face.FRONT && y == 0 && x >= 2 && x <= 5) {
                  return scale(c, 1.1F); // collar
               }

               if (f == Face.FRONT && x == 4 && y % 3 == 2) {
                  return 0xF0F0F0;
               }

               return c;
            case TEE:
            default:
               if (f == Face.FRONT && y == 0 && x >= 3 && x <= 4) {
                  return o.skin;
               }

               if (f == Face.FRONT && y >= 3 && y <= 5 && x >= 2 && x <= 5) {
                  return a; // print
               }

               return c;
         }
      };
   }

   private Shader arm(Outfit o) {
      int sleeve = switch (o.topStyle) {
         case TEE, POLO, DRESS -> 4;
         case BLOUSE -> 7;
         default -> 11;
      };
      int c = o.topStyle == Top.JACKET ? o.top : o.top;
      return (f, x, y, w, h) -> {
         if (f == Face.TOP) {
            return o.topStyle == Top.DRESS ? o.skin : c;
         }

         if (f == Face.BOTTOM) {
            return o.skin;
         }

         if (y < sleeve) {
            if (o.topStyle == Top.DRESS && y < 2) {
               return c;
            }

            return y == sleeve - 1 && sleeve < 11 ? scale(c, 0.85F) : c;
         }

         if (o.topStyle == Top.SUIT && y == 11 && sleeve == 11) {
            return 0xF2F2F2;
         }

         return o.skin;
      };
   }

   private Shader leg(Outfit o) {
      int c = o.bottom;
      int cloth = switch (o.bottomStyle) {
         case SHORTS -> 5;
         case SKIRT -> o.topStyle == Top.DRESS ? 6 : 5;
         default -> 10;
      };
      int bottomColor = o.topStyle == Top.DRESS ? o.top : c;
      return (f, x, y, w, h) -> {
         if (f == Face.TOP) {
            return bottomColor;
         }

         if (f == Face.BOTTOM) {
            return scale(o.shoes, 0.7F);
         }

         if (y >= 10) {
            return y == 11 ? scale(o.shoes, 0.75F) : o.shoes;
         }

         if (y < cloth) {
            if (o.bottomStyle == Bottom.JEANS && f == Face.FRONT && x == (w - 1) && y < cloth - 1) {
               return scale(c, 1.15F); // seam
            }

            if (o.bottomStyle == Bottom.TROUSERS && f == Face.FRONT && x == 1) {
               return scale(c, 0.85F); // crease
            }

            return y == cloth - 1 && cloth < 10 ? scale(bottomColor, 0.85F) : bottomColor;
         }

         if (o.bottomStyle == Bottom.SKIRT && o.topStyle != Top.DRESS) {
            return scale(o.skin, 0.95F);
         }

         return o.skin;
      };
   }
}
