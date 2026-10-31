package dev.baranhan.viltrumitecore.city.citizen;

/**
 * The appearance of one citizen, decoded from its synced seed (pure, both sides). A
 * person is a combination of independent parts: body (sex, build, height), face (skin
 * tone, eyes, beard), hair (style, colour), clothes (top, bottom, shoes with their
 * colours) and extras (glasses, hat, bag, scarf). Thousands of combinations from one
 * int: the crowd never looks like an army of clones, and nothing heavy is stored per
 * citizen (the client paints one small texture per look and caches it).
 */
public record CitizenLook(int seed, boolean female, int build, float height, int skinTone, int eyeColor, Hair hair, int hairColor,
   Top top, int topColor, int accent, Bottom bottom, int bottomColor, Shoes shoes, int shoeColor, Hat hat, int hatColor,
   Bag bag, int bagColor, Eyewear eyewear, boolean beard, boolean scarf, int scarfColor) {

   public enum Hair {
      BUZZ(false), SHORT(false), SIDE_PART(false), QUIFF(false), CURLY(false), BALD(false), MAN_BUN(false),
      LONG(true), PONYTAIL(true), BOB(true), BUN(true), CURLY_LONG(true), PIXIE(true), BRAIDS(true);

      public final boolean feminine;

      Hair(boolean feminine) {
         this.feminine = feminine;
      }

      /** Hair below the head at the back. */
      public boolean longBack() {
         return this == LONG || this == CURLY_LONG || this == BRAIDS;
      }

      /** Big volume shell around the head. */
      public boolean voluminous() {
         return this == CURLY || this == CURLY_LONG || this == BOB;
      }
   }

   public enum Top { TEE, SHIRT, HOODIE, JACKET, COAT, SUIT, SWEATER, BLOUSE, DRESS, VEST }

   public enum Bottom { JEANS, TROUSERS, CHINOS, SHORTS, SKIRT, LEGGINGS }

   public enum Shoes { SNEAKERS, BOOTS, OXFORDS, HEELS, LOAFERS }

   public enum Hat { NONE, CAP, BEANIE, FEDORA }

   public enum Bag { NONE, SHOULDER, BACKPACK }

   public enum Eyewear { NONE, GLASSES, SUNGLASSES }

   public static final int[] SKIN_TONES = {0xF6D5BC, 0xF2C8A4, 0xE6B48C, 0xD9A07A, 0xC68B5E, 0xAE7650, 0x96603E, 0x7C4E30, 0x633C24, 0x4E2E1C};
   public static final int[] HAIR_COLORS = {0x15110F, 0x2B1D15, 0x3E2A1C, 0x5C3A22, 0x7A4A26, 0xA8692E, 0xC9A35C, 0xE3CC8E, 0x8C8C8C, 0xD8D8D2, 0x8E3B20, 0x3A4E8C, 0xB0506E};
   public static final int[] EYE_COLORS = {0x3B2A1E, 0x5A3D22, 0x2E5C8A, 0x4E7A3A, 0x6A6A6A, 0x1E1612};
   /** City clothing palette: neutrals first (most people wear them), then colours. */
   public static final int[] CLOTH = {
      0x1E2228, 0x2E3440, 0x4A4F57, 0x7A7F87, 0xC9CBCF, 0xEDEBE6, 0x1F2A44, 0x2C3E66, 0x3D5A80, 0x6B4A2E, 0x8A6A4A, 0xC2A27A,
      0x5E7A4A, 0x3E5A3A, 0x7A2E2E, 0xA8323A, 0xD9573B, 0xE0A63A, 0xF0D26A, 0x2E8B76, 0x2F6FB0, 0x6C4A8E, 0xB05A86, 0xE5DCCB
   };
   public static final int[] DENIM = {0x2C3E66, 0x3B5578, 0x4E6C92, 0x1F2A3E, 0x2A2E36, 0x6A7E98};
   public static final int[] LEATHER = {0x141414, 0x2A1A12, 0x4A3222, 0x6B4A2E, 0x8A5A3A, 0x3A2416};

   private static int pick(int seed, int salt, int n) {
      return Math.floorMod(CitizenTraits.mix(seed, salt), n);
   }

   private static boolean chance(int seed, int salt, float p) {
      return CitizenTraits.unit(seed, salt) < p;
   }

   private static int cloth(int seed, int salt, boolean neutral) {
      // 60 % of the clothes are neutral city colours
      return neutral || chance(seed, salt + 1000, 0.6F) ? CLOTH[pick(seed, salt, 12)] : CLOTH[12 + pick(seed, salt, 12)];
   }

   public static CitizenLook of(int seed) {
      boolean female = chance(seed, 1, 0.5F);
      int build = female ? (chance(seed, 2, 0.6F) ? 0 : 1) : pick(seed, 2, 3);
      float h = CitizenTraits.unit(seed, 3) + CitizenTraits.unit(seed, 4) - 1.0F; // triangular -1..1
      float height = (female ? 0.95F : 1.0F) + h * 0.075F;
      int tone = pick(seed, 5, SKIN_TONES.length);
      Hair[] styles = Hair.values();
      Hair hair = styles[pick(seed, 6, styles.length)];
      // most people's hair matches their sex in the city crowd; some do not
      if (hair.feminine != female && !chance(seed, 7, 0.12F)) {
         Hair[] pool = female ? new Hair[]{Hair.LONG, Hair.PONYTAIL, Hair.BOB, Hair.BUN, Hair.CURLY_LONG, Hair.PIXIE, Hair.BRAIDS}
            : new Hair[]{Hair.BUZZ, Hair.SHORT, Hair.SIDE_PART, Hair.QUIFF, Hair.CURLY, Hair.BALD, Hair.MAN_BUN};
         hair = pool[pick(seed, 8, pool.length)];
      }

      int hairColor;
      boolean old = chance(seed, 9, 0.14F);
      if (old) {
         hairColor = HAIR_COLORS[8 + pick(seed, 10, 2)];
      } else if (chance(seed, 11, 0.05F)) {
         hairColor = HAIR_COLORS[10 + pick(seed, 12, 3)];
      } else {
         // darker skin: darker hair more often
         int max = tone >= 6 ? 3 : 8;
         hairColor = HAIR_COLORS[pick(seed, 13, max)];
      }

      if (hair == Hair.BALD && !old && chance(seed, 14, 0.5F)) {
         hair = Hair.BUZZ;
      }

      Top[] tops = Top.values();
      Top top = tops[pick(seed, 15, tops.length)];
      if (top == Top.DRESS && !female) {
         top = Top.SHIRT;
      }

      if (top == Top.BLOUSE && !female) {
         top = Top.SWEATER;
      }

      Bottom bottom;
      if (top == Top.DRESS) {
         bottom = Bottom.SKIRT;
      } else if (top == Top.SUIT) {
         bottom = female && chance(seed, 16, 0.4F) ? Bottom.SKIRT : Bottom.TROUSERS;
      } else {
         Bottom[] all = Bottom.values();
         bottom = all[pick(seed, 17, all.length)];
         if ((bottom == Bottom.SKIRT || bottom == Bottom.LEGGINGS) && !female) {
            bottom = chance(seed, 18, 0.5F) ? Bottom.JEANS : Bottom.CHINOS;
         }

         if (top == Top.COAT && bottom == Bottom.SHORTS) {
            bottom = Bottom.TROUSERS;
         }
      }

      Shoes shoes;
      if (top == Top.SUIT) {
         shoes = female && chance(seed, 19, 0.5F) ? Shoes.HEELS : Shoes.OXFORDS;
      } else {
         Shoes[] all = Shoes.values();
         shoes = all[pick(seed, 20, all.length)];
         if (shoes == Shoes.HEELS && !female) {
            shoes = Shoes.LOAFERS;
         }

         if (shoes == Shoes.OXFORDS && (top == Top.HOODIE || top == Top.TEE) && chance(seed, 21, 0.7F)) {
            shoes = Shoes.SNEAKERS;
         }
      }

      int topColor = top == Top.SUIT ? CLOTH[pick(seed, 22, 8)] : cloth(seed, 22, top == Top.COAT);
      int accent = top == Top.SUIT || top == Top.SHIRT ? (chance(seed, 23, 0.7F) ? 0xEDEBE6 : 0x9FB8D8) : cloth(seed, 23, false);
      if (accent == topColor) {
         accent = CLOTH[(pick(seed, 24, 24) + 5) % 24];
      }

      int bottomColor = switch (bottom) {
         case JEANS -> DENIM[pick(seed, 25, DENIM.length)];
         case TROUSERS -> top == Top.SUIT ? topColor : CLOTH[pick(seed, 25, 9)];
         case CHINOS -> new int[]{0xC2A27A, 0x8A6A4A, 0x5E6A4A, 0x2E3440, 0xD8CBB0}[pick(seed, 25, 5)];
         default -> top == Top.DRESS ? topColor : cloth(seed, 25, false);
      };
      int shoeColor = shoes == Shoes.SNEAKERS ? (chance(seed, 26, 0.55F) ? 0xEDEDED : cloth(seed, 26, false)) : LEATHER[pick(seed, 26, LEATHER.length)];
      Hat hat = Hat.NONE;
      float hr = CitizenTraits.unit(seed, 27);
      if (hr < 0.11F) {
         hat = Hat.CAP;
      } else if (hr < 0.18F) {
         hat = Hat.BEANIE;
      } else if (hr < 0.21F && !female) {
         hat = Hat.FEDORA;
      }

      Bag bag = Bag.NONE;
      float br = CitizenTraits.unit(seed, 28);
      if (br < (female ? 0.32F : 0.14F)) {
         bag = Bag.SHOULDER;
      } else if (br < (female ? 0.42F : 0.3F)) {
         bag = Bag.BACKPACK;
      }

      Eyewear eyes = Eyewear.NONE;
      float er = CitizenTraits.unit(seed, 29);
      if (er < (old ? 0.45F : 0.16F)) {
         eyes = Eyewear.GLASSES;
      } else if (er < (old ? 0.5F : 0.24F)) {
         eyes = Eyewear.SUNGLASSES;
      }

      boolean beard = !female && chance(seed, 30, old ? 0.5F : 0.26F);
      boolean scarf = chance(seed, 31, top == Top.COAT ? 0.45F : 0.06F);
      return new CitizenLook(seed, female, build, height, SKIN_TONES[tone], EYE_COLORS[pick(seed, 32, EYE_COLORS.length)], hair, hairColor, top,
         topColor, accent, bottom, bottomColor, shoes, shoeColor, hat, cloth(seed, 33, false), bag, bag == Bag.BACKPACK ? cloth(seed, 34, false) : LEATHER[pick(seed, 34, LEATHER.length)],
         eyes, beard, scarf, cloth(seed, 35, false));
   }

   /** Slim arms (3 px): women and slim men. */
   public boolean slim() {
      return this.female || this.build == 0;
   }

   /** Horizontal body scale of the build: slim, average, broad. */
   public float width() {
      return switch (this.build) {
         case 0 -> 0.93F;
         case 2 -> 1.08F;
         default -> 1.0F;
      };
   }

   /** A coat/dress tail below the waist. */
   public boolean skirtPart() {
      return this.bottom == Bottom.SKIRT || this.top == Top.COAT;
   }

   /** Texture identity: every field that changes a pixel (the seed's personality bits do not). */
   public int textureKey() {
      return java.util.Objects.hash(this.female, this.build == 0 || this.female, this.skinTone, this.eyeColor, this.hair, this.hairColor, this.top, this.topColor,
         this.accent, this.bottom, this.bottomColor, this.shoes, this.shoeColor, this.hat, this.hatColor, this.bag, this.bagColor, this.eyewear, this.beard,
         this.scarf, this.scarfColor);
   }
}
