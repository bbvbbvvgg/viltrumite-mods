package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.hero.book.BookClass;
import dev.baranhan.viltrumitecore.hero.book.BookProfile;

/**
 * Every number of Rohan Kishibe and Heaven's Door (docs/rohan/heavens-door-design.md §3, §6, §8). Pure: unit-tested in
 * RohanRulesTest.
 */
public final class RohanRules {
   // ---- body (tier B, BalanceTable.MANGAKA) ----
   public static final double HEALTH_BONUS = 10.0;
   public static final double ARMOR = 4.0;

   // ---- resources ----
   public static final float INK_MAX = 100.0F;
   /** Ink per second in a fight / out of combat (no damage dealt or taken for {@link #CALM_TICKS}). */
   public static final float INK_REGEN = 2.0F;
   public static final float INK_REGEN_CALM = 4.0F;
   public static final int CALM_TICKS = 100;
   public static final float INSPIRATION_MAX = 100.0F;
   /** Inspiration for reading a species / hero / boss for the first time. */
   public static final float INSPIRATION_SPECIES = 10.0F;
   public static final float INSPIRATION_HERO = 25.0F;
   public static final float INSPIRATION_BOSS = 40.0F;
   /** Inspiration for reading a known book again (small, so farming one cow is slow). */
   public static final float INSPIRATION_REREAD = 1.0F;

   // ---- cooldown indices (state cooldowns / snapshot extra cooldowns) ----
   public static final int CD_DOOR = 0;
   public static final int CD_SKETCH = 1;
   public static final int CD_NIBS = 2;
   public static final int CD_COUNTER = 3;
   public static final int CD_SELF_EDIT = 4;
   public static final int CD_MANUSCRIPT = 5;
   public static final int COOLDOWN_COUNT = 6;
   public static final int[] COOLDOWN_TICKS = {120, 400, 30, 280, 200, 1800};
   /** A Heaven's Door touch that finds nobody costs only this cooldown. */
   public static final int CD_DOOR_MISS = 40;
   /** A Faster than a Punch stance that nobody hits. */
   public static final int CD_COUNTER_MISS = 100;

   // ---- costs ----
   public static final float COST_DOOR = 20.0F;
   public static final float COST_SKETCH = 35.0F;
   public static final float COST_NIBS = 6.0F;
   public static final float COST_COUNTER = 20.0F;
   public static final float COST_SELF_EDIT = 15.0F;
   public static final float COST_RIP = 10.0F;

   // ---- Heaven's Door (touch) ----
   public static final int DOOR_SUMMON = 8;
   public static final int DOOR_LUNGE = 6;
   public static final int DOOR_LENGTH = DOOR_SUMMON + DOOR_LUNGE;
   /** The stand's hand reaches the target on this tick of the move. */
   public static final int DOOR_CONTACT = DOOR_SUMMON + 4;
   public static final double DOOR_RANGE = 5.0;
   /** Range against a body carrying one of his ink marks (pen nib hit). */
   public static final double DOOR_RANGE_MARKED = 9.0;
   /** A book stays readable while its owner is within this distance of Rohan. */
   public static final double BOOK_KEEP_RANGE = 16.0;

   // ---- Air Sketch (sight) ----
   public static final int SKETCH_DRAW = 16;
   public static final int SKETCH_SHOW = 80;
   public static final int SKETCH_MAX_VIEWERS = 4;
   public static final double SKETCH_RANGE = 12.0;
   /** Half-angle of a mob's / a player's view cone that must hold the sketch. */
   public static final double SKETCH_CONE_MOB = 35.0;
   public static final double SKETCH_CONE_PLAYER = 20.0;

   // ---- Pen Nibs ----
   public static final int NIBS_COUNT = 3;
   public static final int NIBS_LENGTH = 6;
   public static final double NIBS_RANGE = 16.0;
   public static final float NIB_DAMAGE = 2.0F;
   public static final int INK_MARK_TICKS = 120;

   // ---- Faster than a Punch ----
   public static final int COUNTER_WINDOW = 12;
   public static final double COUNTER_RANGE = 5.0;
   /** The attacker's book stays open this long, with an automatic Safety Lock. */
   public static final int COUNTER_OPEN = 40;

   // ---- Self-Edit ----
   public static final int SELF_EDIT_WRITE = 10;
   public static final int SELF_EDIT_FLY = 10;
   /** Blocks per tick: 70 km/h = 19.4 m/s = 0.97 blocks per tick. */
   public static final double SELF_EDIT_SPEED = 0.97;

   // ---- Manuscript (ultimate) ----
   public static final int MANUSCRIPT_SHOW = 30;
   public static final int MANUSCRIPT_BOOKS = 100;
   public static final int MANUSCRIPT_MAX = 8;
   public static final double MANUSCRIPT_RANGE = 14.0;
   public static final double MANUSCRIPT_CONE = 60.0;
   public static final float MANUSCRIPT_COST = 100.0F;

   // ---- writing ----
   /** Ticks of the writing pose after each written line. */
   public static final int WRITE_POSE = 12;
   /** A book closes this many ticks after its last line. */
   public static final int CLOSE_DELAY = 10;
   /** Struggle: a press of the opened player shortens the window and every written line. */
   public static final int STRUGGLE_WINDOW = 4;
   public static final int STRUGGLE_LINE = 6;
   /** At most this many struggle presses per second count. */
   public static final int STRUGGLE_PER_SECOND = 5;
   /** Hitting a body you made safe halves the remaining Safety Lock. */
   public static final float SAFETY_HIT_FACTOR = 0.5F;
   /** Damage the reader takes when a boss throws a line back. */
   public static final float BACKLASH_DAMAGE = 3.0F;
   /** A ripped page: forget Rohan this long. */
   public static final int RIP_TICKS = 600;
   /** ...and lose this much "body mass" (canon: ripped pages make the body lighter). */
   public static final float RIP_DAMAGE = 1.0F;

   private RohanRules() {
   }

   /** Ink regenerated in one tick. */
   public static float inkPerTick(int idleTicks) {
      return (idleTicks >= CALM_TICKS ? INK_REGEN_CALM : INK_REGEN) / 20.0F;
   }

   /** Inspiration for reading this book (first time of its kind or again). */
   public static float inspiration(boolean firstTime, BookClass bookClass, boolean hero) {
      if (!firstTime) {
         return INSPIRATION_REREAD;
      }
      if (bookClass == BookClass.BOSS) {
         return INSPIRATION_BOSS;
      }
      return hero ? INSPIRATION_HERO : INSPIRATION_SPECIES;
   }

   /**
    * Duration of one written line: the instruction's base for the class, scaled by the book, capped by the book's
    * longest line, minus what struggling took away; never below one second for a line that took at all.
    */
   public static int lineTicks(int base, BookProfile profile, int struggles) {
      if (base <= 0) {
         return 0;
      }
      int ticks = Math.round(base * Math.max(0.0F, profile.durationScale()));
      if (profile.maxInstruction() > 0) {
         ticks = Math.min(ticks, profile.maxInstruction());
      }
      ticks -= struggles * STRUGGLE_LINE;
      return Math.max(20, ticks);
   }

   /** Ink a line costs in this book. */
   public static float lineCost(float base, BookProfile profile) {
      return Math.max(0.0F, base * profile.costScale());
   }

   /** Is {@code to} within a cone of {@code halfAngleDeg} around the look vector (ax, ay, az unit)? */
   public static boolean inCone(double lx, double ly, double lz, double tx, double ty, double tz, double halfAngleDeg) {
      double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
      if (len < 1.0E-6) {
         return true;
      }
      double cos = (lx * tx + ly * ty + lz * tz) / len;
      return cos >= Math.cos(Math.toRadians(halfAngleDeg));
   }

   /** Touch range against a body (marked or not). */
   public static double doorRange(boolean marked) {
      return marked ? DOOR_RANGE_MARKED : DOOR_RANGE;
   }
}
