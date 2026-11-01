package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * Pure numbers and small rules of the Ultron uprising (docs/plans/2026-11-01-ultron-uprising.md).
 * No Minecraft statics: unit tests read everything here directly.
 */
public final class UltronRules {
   public static final int TPS = 20;

   // ---- area ------------------------------------------------------------------------------------
   /** Players this far (horizontal) from the anchor see the HUD, the bars and the subtitles. */
   public static final double VIEW_RANGE = 256.0;
   /** Area radius without a city (nodes stand on a ring inside it). */
   public static final double AREA_RADIUS = 92.0;
   public static final double MIN_AREA_RADIUS = 56.0;
   public static final double MAX_AREA_RADIUS = 150.0;
   /** Nobody in the dimension for this long: the event ends itself (nothing runs unobserved). */
   public static final int EMPTY_LEVEL_TICKS = 3 * 60 * TPS;

   // ---- phases ----------------------------------------------------------------------------------
   private static final int[] MIN_TICKS = {60 * TPS, 150 * TPS, 180 * TPS, 150 * TPS, 0, 0};
   private static final int[] MAX_TICKS = {100 * TPS, 300 * TPS, 330 * TPS, 270 * TPS, 0, 0};
   /** Machine war ends early once the summed adaptation pressure reaches this (and the minimum passed). */
   public static final float WAR_PRESSURE_EXIT = 90.0F;
   /** Adaptation ends early after this many protocol activations (and the minimum passed). */
   public static final int ADAPT_ACTIVATIONS_EXIT = 2;
   /** The network down to this many standing nodes (core included) brings Ultron in person. */
   public static final int FINALE_NODE_FLOOR = 2;
   /** Boss assembly cinematic before the fight. */
   public static final int ASSEMBLY_TICKS = 140;
   /** Victory cascade (shutdown of the machines, nodes popping) before the clean-up. */
   public static final int VICTORY_TICKS = 12 * TPS;
   /** Defeat sequence (upload complete, Ultron ascends) before the clean-up. */
   public static final int DEFEAT_TICKS = 10 * TPS;

   // ---- units -----------------------------------------------------------------------------------
   private static final int[] KIND_CAP = {8, 12, 4, 5, 4};
   private static final int[] FAB_TICKS = {120, 180, 320, 240, 200};
   private static final int[] PHASE_CAP = {6, 14, 24, 26, 20, 0};
   private static final int[] PHASE_BUDGET = {8, 30, 50, 45, 30, 0};
   /** Units built over a whole event never exceed this, whatever the phases add up to. */
   public static final int EVENT_PRODUCTION_CAP = 150;
   /** Extra alive units per player beyond the first (at most {@link #MAX_EXTRA_PLAYERS} counted). */
   public static final int CAP_PER_EXTRA_PLAYER = 2;
   public static final int MAX_EXTRA_PLAYERS = 3;
   /** Units spawned per server tick at most (spawn queue). */
   public static final int SPAWNS_PER_TICK = 2;

   // ---- nodes -----------------------------------------------------------------------------------
   private static final float[] NODE_HULL = {420.0F, 240.0F, 300.0F, 260.0F, 160.0F, 220.0F};
   private static final float[] NODE_SHIELD = {160.0F, 90.0F, 100.0F, 140.0F, 60.0F, 80.0F};
   /** Two nodes link when they are at most this far apart (blocks). */
   public static final double LINK_RANGE = 84.0;
   public static final int MAX_LINKS = 3;
   /** Engineers raise a node in this many ticks of welding. */
   public static final int BUILD_TICKS = 30 * TPS;
   /** An engineer reports one weld step every this many ticks while its beam is on the node. */
   public static final int WELD_EVERY = 4;
   /** A hacked node stays dark this long (engineers can reboot it sooner). */
   public static final int DISABLE_TICKS = 60 * TPS;
   /** Welding that reboots a disabled node. */
   public static final int REBOOT_TICKS = 10 * TPS;
   /** Sneak + use this long next to a node core disables it. */
   public static final int HACK_TICKS = 60;
   public static final double HACK_REACH = 3.75;
   /** Hack progress lost per tick when nobody hacks, and per hit the hacker takes. */
   public static final int HACK_DECAY = 2;
   public static final int HACK_HIT_LOSS = 20;
   /** Discovery: line of sight within this, or anything within the short range. */
   public static final double DISCOVER_SIGHT = 26.0;
   public static final double DISCOVER_NEAR = 10.0;
   /** A downed drone reveals the nearest unknown node within this range. */
   public static final double SALVAGE_RANGE = 140.0;
   /** One hit takes at most this share of a node's full hull (never less than {@link #NODE_MIN_HIT}). */
   public static final float NODE_HIT_CAP = 0.10F;
   public static final float NODE_MIN_HIT = 12.0F;
   /** All hits within a second take at most this share of the hull. */
   public static final float NODE_SECOND_CAP = 0.18F;
   /** Shield regen per second after {@link #SHIELD_DELAY} ticks without a hit. */
   public static final float SHIELD_REGEN = 2.5F;
   public static final int SHIELD_DELAY = 5 * TPS;
   public static final float BULWARK_REGEN_FACTOR = 2.5F;
   public static final float FORTIFY_SHIELD_FACTOR = 1.5F;
   /** Units this close to a powered bulwark take less damage. */
   public static final double BULWARK_AURA = 18.0;
   public static final float BULWARK_REDUCTION = 0.3F;
   /** Repair node: heal per second to units in range and to linked nodes. */
   public static final double REPAIR_RADIUS = 20.0;
   public static final float REPAIR_UNIT_HEAL = 1.0F;
   public static final float REPAIR_NODE_HEAL = 3.0F;
   /** Sensor and uplink ranges. */
   public static final double SENSOR_RADIUS = 48.0;
   public static final double CAPTURE_RADIUS = 26.0;
   /** Telemetry: a hit is reported only within this range of a powered node (the network's coverage). */
   public static final double COVERAGE = 52.0;
   public static final int CAPTURE_GAP_TICKS = 15 * TPS;
   public static final int MAX_CAPTURED_GOLEMS = 4;
   public static final int MAX_CAPTURED_SUITS = 3;
   /** Captured Legion suit: hits go into this shell instead of the roster. */
   public static final float SUIT_SHELL = 60.0F;
   /** A captured golem beaten down is freed with this share of its health. */
   public static final float GOLEM_FREE_HEALTH = 0.3F;

   // ---- finale ----------------------------------------------------------------------------------
   /** Upload per tick with one powered uplink; each further powered uplink adds this share. */
   public static final float UPLOAD_EXTRA_UPLINK = 0.25F;

   private UltronRules() {
   }

   public static int minTicks(UltronPhase phase) {
      return MIN_TICKS[phase.ordinal()];
   }

   public static int maxTicks(UltronPhase phase) {
      return MAX_TICKS[phase.ordinal()];
   }

   public static int kindCap(UltronUnitKind kind) {
      return KIND_CAP[kind.ordinal()];
   }

   public static int fabTicks(UltronUnitKind kind) {
      return FAB_TICKS[kind.ordinal()];
   }

   /** Alive units allowed in a phase for {@code players} players in range. */
   public static int aliveCap(UltronPhase phase, int players) {
      int extra = Math.max(0, Math.min(MAX_EXTRA_PLAYERS, players - 1));
      int base = PHASE_CAP[phase.ordinal()];
      return base == 0 ? 0 : base + extra * CAP_PER_EXTRA_PLAYER;
   }

   public static int phaseBudget(UltronPhase phase) {
      return PHASE_BUDGET[phase.ordinal()];
   }

   public static float hull(UltronNodeKind kind) {
      return NODE_HULL[kind.ordinal()];
   }

   public static float shield(UltronNodeKind kind) {
      return NODE_SHIELD[kind.ordinal()];
   }

   /** Max shield of a node, raised while FORTIFY covers it. */
   public static float shield(UltronNodeKind kind, boolean fortified) {
      return shield(kind) * (fortified ? FORTIFY_SHIELD_FACTOR : 1.0F);
   }

   /** Per-hit cap on a node (shield and hull together). */
   public static float nodeHitCap(UltronNodeKind kind) {
      return Math.max(NODE_MIN_HIT, NODE_HIT_CAP * hull(kind));
   }

   public static float nodeSecondCap(UltronNodeKind kind) {
      return Math.max(nodeHitCap(kind), NODE_SECOND_CAP * hull(kind));
   }

   /** Shield regen per tick: base, faster with a powered bulwark linked, faster again while fortified. */
   public static float shieldRegenPerTick(boolean bulwarkLinked, boolean fortified) {
      float perSecond = SHIELD_REGEN * (bulwarkLinked ? BULWARK_REGEN_FACTOR : 1.0F) * (fortified ? 2.0F : 1.0F);
      return perSecond / TPS;
   }

   /** Upload progress per tick (1.0 per tick with one powered uplink), 0 without any. */
   /** A tick counter as a share 0..1 of its total (0 when there is no total). */
   public static float share(float value, int total) {
      return total > 0 ? Math.max(0.0F, Math.min(1.0F, value / total)) : 0.0F;
   }

   /** Is (x, z) outside the ring of this radius around (ax, az)? */
   public static boolean outside(double ax, double az, double ring, double x, double z) {
      double dx = x - ax;
      double dz = z - az;
      return dx * dx + dz * dz > ring * ring;
   }

   public static float uploadRate(int poweredUplinks) {
      return poweredUplinks <= 0 ? 0.0F : 1.0F + UPLOAD_EXTRA_UPLINK * (poweredUplinks - 1);
   }

   /**
    * Pure: should the phase end now? {@code ticks} in the phase; the extra signals only count after the minimum.
    * FINALE and ENDED never end by time (the fight or the upload decides).
    */
   public static boolean phaseDone(UltronPhase phase, int ticks, float scale, int builtNodes, int targetNodes, float pressure, int activations,
      int standingNodes) {
      if (phase == UltronPhase.FINALE || phase == UltronPhase.ENDED) {
         return false;
      }
      int min = Math.round(minTicks(phase) * scale);
      int max = Math.round(maxTicks(phase) * scale);
      if (ticks >= max) {
         return true;
      }
      if (ticks < min) {
         return false;
      }
      return switch (phase) {
         case AWAKENING -> builtNodes >= 1;
         case SPREAD -> builtNodes >= targetNodes;
         case MACHINE_WAR -> pressure >= WAR_PRESSURE_EXIT || standingNodes <= FINALE_NODE_FLOOR + 1;
         case ADAPTATION -> activations >= ADAPT_ACTIVATIONS_EXIT || standingNodes <= FINALE_NODE_FLOOR;
         default -> false;
      };
   }

   /** Pure: the network fell so far before the finale that Ultron comes in person now (core lost, or almost nothing left). */
   public static boolean earlyFinale(UltronPhase phase, boolean coreStanding, int standingNodes) {
      if (!phase.atLeast(UltronPhase.SPREAD) || phase.atLeast(UltronPhase.FINALE)) {
         return false;
      }
      return !coreStanding || phase.atLeast(UltronPhase.MACHINE_WAR) && standingNodes <= FINALE_NODE_FLOOR - 1;
   }
}
