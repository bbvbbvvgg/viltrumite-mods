package dev.baranhan.viltrumitecore.scp.scp106.pocket;

/**
 * Block ids of the pure painter (PocketPainter). The chunk generator maps them to block states
 * (PocketBlockMap); the reachability check reads them directly. Never reorder: tests and the
 * runtime repaint of mutating corridors share these values.
 */
public final class PocketPalette {
   public static final byte AIR = 0;
   public static final byte CONCRETE = 1;
   public static final byte MASS = 2;
   public static final byte TILE = 3;
   public static final byte CORRODED = 4;
   public static final byte METAL = 5;
   public static final byte GRATE = 6;
   public static final byte OOZE = 7;
   public static final byte OOZE_STAIN = 8;
   public static final byte PILLAR = 9;
   public static final byte LIGHT = 10;
   public static final byte LIGHT_DEAD = 11;
   public static final byte VOID_GLASS = 12;
   public static final byte THIN_WALL = 13;
   public static final byte BREACH = 14;
   public static final byte WATER = 15;
   public static final byte PLASTER = 16;
   public static final byte PLANKS = 17;
   public static final byte SIGIL = 18;
   public static final byte NOTE = 19;
   public static final byte SEAM = 20;
   public static final byte CHAIN = 21;
   public static final byte BARS = 22;
   public static final byte BOOKSHELF = 23;
   public static final byte FENCE = 24;
   public static final byte PLATE = 25;
   public static final byte CARPET = 26;
   public static final byte POT = 27;
   public static final byte ROAD = 28;
   public static final byte SIDEWALK = 29;
   public static final byte LANE = 30;
   public static final byte FACADE = 31;
   public static final byte CAULDRON = 32;
   /** Stairs that ascend towards N, E, S, W (+0..3). */
   public static final byte STAIR = 33;
   /** Chairs (wooden stairs) whose seat faces N, E, S, W (+0..3). */
   public static final byte CHAIR = 37;
   public static final byte CHAIR_CEIL = 41;
   /** Ladders facing N, E, S, W (+0..3). */
   public static final byte LADDER = 42;
   /** Wooden door lower / upper half facing N, E, S, W (+0..3). */
   public static final byte DOOR_LOW = 46;
   public static final byte DOOR_UP = 50;
   /** The home door (iron, opened by the server only), lower / upper facing N, E, S, W. */
   public static final byte HOME_LOW = 54;
   public static final byte HOME_UP = 58;
   public static final byte BONE = 62;
   public static final byte SKULL = 63;
   public static final byte COBWEB = 64;
   /** The false daylight of a decoy dead end (a bright, cold glowing block). */
   public static final byte DAYLIGHT = 65;
   /** Hanging lantern of a sanctuary (warm light, not a full block). */
   public static final byte LANTERN = 66;
   public static final byte ROCK = 67;
   public static final byte ROCK_DARK = 68;
   public static final byte MOSAIC = 69;
   public static final byte COUNT = 70;

   private PocketPalette() {
   }

   /** Blocks a body cannot stand in (doors, ladders, thin walls, water and flat decorations are passable for the route check). */
   public static boolean solid(byte b) {
      if (b >= LADDER && b < HOME_LOW + 8) {
         return false;
      }
      return switch (b) {
         case AIR, WATER, OOZE_STAIN, THIN_WALL, CARPET, PLATE, NOTE, CHAIN, SEAM, SKULL, COBWEB, LANTERN -> false;
         default -> true;
      };
   }

   /** Something to stand on (full blocks, stairs, ooze pools, the breach; water and ladders hold a swimmer or climber). */
   public static boolean support(byte b) {
      if (b >= LADDER && b < LADDER + 4 || b == WATER) {
         return true;
      }
      return solid(b) && b != BARS && b != FENCE && b != POT && b != CAULDRON && b != CHAIN;
   }

   public static boolean door(byte b) {
      return b >= DOOR_LOW && b < HOME_LOW + 8;
   }
}
