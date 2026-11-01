package dev.baranhan.viltrumitecore.world.collapse;

/**
 * Pure numbers and timing of structural collapses (no Minecraft classes, unit tested). Ticks are server
 * ticks (20 per second). The server owns every phase tick; clients only schedule visuals and sounds on them.
 */
public final class CollapseRules {
   // ---------------------------------------------------------------- damage detection
   /** Weighted removed blocks in a cluster (3x3x3 ledger cells) before a structure is analysed. */
   public static final float SCORE_THRESHOLD = 60.0F;
   /** Removed blocks that are still air in the analysis box; fewer means no real structural damage. */
   public static final int MIN_DAMAGED = 24;
   /** Ledger memory: damage older than this no longer counts (a series of hits within one minute adds up). */
   public static final int WINDOW_TICKS = 1200;
   /** A cluster is analysed only after this many ticks without new damage (a Leviathan is still ploughing). */
   public static final int SETTLE_TICKS = 8;
   /** Explicit impacts (meteor, Leviathan crash) settle faster: phase 1 lands on the hit. */
   public static final int IMPACT_SETTLE_TICKS = 3;
   /** Removed positions kept per ledger cell (8x8x8 blocks) and per level. */
   public static final int CELL_CAP = 512;
   public static final int LEVEL_CAP = 32768;
   /** Ledger cell size as a shift (8 blocks). */
   public static final int CELL_SHIFT = 3;

   // ---------------------------------------------------------------- analysis box
   /** Horizontal half extent of the analysis box around the damage centre. */
   public static final int MAX_HALF = 12;
   /** Rows above the highest damaged block that the analysis may look at. */
   public static final int MAX_UP = 30;
   /** Rows below the lowest damaged block (support search). */
   public static final int MAX_DOWN = 4;
   /** Structural blocks above the damage needed to call it a building. */
   public static final int MIN_STRUCTURE = 60;
   /** Height of structure above the lowest damage needed to call it multi-storey. */
   public static final int MIN_TOWER_HEIGHT = 6;
   /** Severed (unsupported) blocks needed for a severed-section collapse. */
   public static final int MIN_SEVERED = 12;
   /** Smallest collapse section worth a scene. */
   public static final int MIN_SECTION = 24;
   /** Overhang rule: vertical extent of the hole and its horizontal span. */
   public static final int OVERHANG_MIN_DEPTH = 3;
   public static final int OVERHANG_MIN_SPAN = 4;
   public static final int OVERHANG_MIN_DAMAGED = 40;
   /** Local (safe) scenario: needs heavy damage; its section is small. */
   public static final int LOCAL_MIN_DAMAGED = 80;
   public static final int LOCAL_MAX_BLOCKS = 160;

   // ---------------------------------------------------------------- limits (config can lower them)
   public static final int DEFAULT_MAX_ACTIVE = 3;
   public static final int DEFAULT_MAX_BLOCKS = 1600;
   public static final int DEFAULT_BLOCKS_PER_TICK = 900;
   /** No new collapse within this distance of a running or recent one... */
   public static final double REGION_RADIUS = 14.0;
   /** ...for this many ticks after it started. */
   public static final int REGION_COOLDOWN = 400;
   /** Rubble placed at the landing zone: share of the section and hard cap. */
   public static final float RUBBLE_SHARE = 0.08F;
   public static final int RUBBLE_MAX = 160;
   /** Weak blocks (glass, leaves) broken by the ground impact. */
   public static final int SECONDARY_MAX = 32;

   // ---------------------------------------------------------------- severity
   public static final int MINOR_MAX_BLOCKS = 250;
   public static final int MAJOR_MAX_BLOCKS = 800;

   // ---------------------------------------------------------------- timeline
   /** Phase 1 (impact flash, crumbs) before the structure starts to fail. */
   public static final int IMPACT_TICKS = 6;
   /** Phase 4: dust settles, then the scene ends. */
   public static final int SETTLE_PHASE_TICKS = 200;
   /** Vanilla falling-block gravity (blocks per tick squared), used for the landing tick. */
   public static final double GRAVITY = 0.04;
   /** Players within this distance get the scene packets; beyond {@link #NEAR_RANGE} they get the far mix. */
   public static final double PACKET_RANGE = 320.0;
   public static final double NEAR_RANGE = 160.0;

   private CollapseRules() {
   }

   /** Phase 2 length (creaking, shedding, cracks): longer for bigger sections, seeded +-4 ticks. */
   public static int creakTicks(CollapseVariant variant, CollapseSeverity severity, long seed) {
      int base = switch (variant) {
         case TOPPLE -> 30;
         case FACADE_SHEAR -> 24;
         case PANCAKE -> 22;
         case CRUMBLE -> 10;
      };
      base += severity.ordinal() * 4;
      return base + (int)Math.floorMod(seed >>> 7, 9L) - 4;
   }

   /** Ticks a section needs to fall {@code height} blocks under {@link #GRAVITY}, clamped 6..40. */
   public static int fallTicks(double height) {
      double h = Math.max(0.0, height);
      int t = (int)Math.ceil(Math.sqrt(2.0 * h / GRAVITY));
      return Math.max(6, Math.min(40, t));
   }

   /**
    * Phase 3 length, from the removal to the ground slam: the free fall of the section's lowest block plus the
    * time a toppling or peeling section needs to swing down (a 30 block tower tips over in about two seconds).
    * Clients aim their falling fragments at this tick; the server applies the impact on it.
    */
   public static int impactTicks(CollapseVariant variant, double drop, int height) {
      double h = Math.max(0.0, drop);
      return switch (variant) {
         case TOPPLE -> Math.min(52, Math.round(fallTicks(h + height * 0.6) * 1.3F));
         case FACADE_SHEAR -> Math.min(46, Math.round(fallTicks(h + height * 0.4) * 1.15F));
         case PANCAKE -> fallTicks(h + height * 0.25);
         case CRUMBLE -> fallTicks(h + 1.0);
      };
   }

   /** Facade pieces removed during phase 2: a few percent of the section, at least one per two ticks. */
   public static int shedCount(int sectionBlocks, int creakTicks) {
      int n = Math.round(sectionBlocks * 0.04F);
      return Math.max(Math.min(sectionBlocks / 4, creakTicks / 2), Math.min(n, 64));
   }

   /** Damage to a living entity under the falling section at the landing tick. */
   public static float impactDamage(CollapseSeverity severity, double distanceShare) {
      double near = 1.0 - Math.max(0.0, Math.min(1.0, distanceShare));
      return (float)((6.0 + 16.0 * severity.strength()) * (0.45 + 0.55 * near));
   }

   /** Rubble blocks for a section of {@code blocks}. */
   public static int rubbleCount(int blocks) {
      return Math.min(RUBBLE_MAX, Math.round(blocks * RUBBLE_SHARE));
   }
}
