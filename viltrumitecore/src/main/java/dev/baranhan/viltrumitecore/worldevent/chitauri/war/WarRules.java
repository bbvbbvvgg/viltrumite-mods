package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

/**
 * Pure numbers of the city war layer (docs/plans/2026-11-04-chitauri-evolution.md): sector sizes and control,
 * the director's clock and reinforcement budget, live caps of the new units, captive limits. No Minecraft types.
 */
public final class WarRules {
   // ---- sectors ----
   /** Cell size of ring sectors outside a city (blocks between sector centres). */
   public static final double CELL = 34.0;
   /** Largest war radius around the anchor (a bigger city still gets sectors up to here). */
   public static final double MAX_RADIUS = 176.0;
   /** Sector planner limit: more sectors only cost ticks. */
   public static final int MAX_SECTORS = 40;
   /** Ticks between two sector presence updates (one batch of sectors per update). */
   public static final int SECTOR_PERIOD = 20;
   /** Control step per update and unit weights. */
   public static final float CONTROL_STEP = 0.035F;
   public static final float HERO_WEIGHT = 2.5F;
   public static final float SEIZE_CONTROL = 0.7F;
   public static final float CONTEST_CONTROL = 0.2F;
   public static final float LIBERATE_CONTROL = -0.3F;

   // ---- director ----
   /** Ticks between two director decisions (15 s). */
   public static final int DIRECTOR_PERIOD = 300;
   /** Reinforcement points for a whole invasion: every new elite unit costs points, so waves never become endless. */
   public static final int BUDGET = 30;
   /** Extra points the hive gains per captive delivered to containment (capped). */
   public static final int CAPTIVE_BONUS_CAP = 6;
   public static final int COST_CAPTOR = 1;
   public static final int COST_HEAVY = 3;
   public static final int COST_INTERCEPTOR = 2;
   public static final int COST_SIEGE = 4;
   public static final int COST_COMMANDER = 4;

   // ---- live caps of the new units ----
   public static final int MAX_CAPTORS = 4;
   public static final int MAX_HEAVIES = 3;
   public static final int MAX_INTERCEPTORS = 4;
   public static final int MAX_SIEGES = 2;
   public static final int MAX_COMMANDERS = 2;
   /** Reinforcement calls one commander may make in its life. */
   public static final int COMMANDER_CALLS = 2;

   // ---- squads ----
   /** A squad that lost this share of its members retreats and regroups. */
   public static final float RETREAT_LOSS = 0.6F;
   /** Ticks a retreating squad needs before it may take a new order. */
   public static final int REGROUP_TICKS = 200;

   // ---- captives ----
   public static final int MAX_CAPTIVES = 12;
   public static final int CONTAINMENT_CAPACITY = 6;
   public static final double CONTAINMENT_SPACING = 40.0;
   /** Damage share (of max health) in one hit that makes a captor drop its captive. */
   public static final float DROP_SHARE = 0.25F;

   private WarRules() {
   }

   public static int cost(UnitKind kind) {
      return switch (kind) {
         case CAPTOR -> COST_CAPTOR;
         case HEAVY -> COST_HEAVY;
         case INTERCEPTOR -> COST_INTERCEPTOR;
         case SIEGE -> COST_SIEGE;
         case COMMANDER -> COST_COMMANDER;
      };
   }

   public static int cap(UnitKind kind) {
      return switch (kind) {
         case CAPTOR -> MAX_CAPTORS;
         case HEAVY -> MAX_HEAVIES;
         case INTERCEPTOR -> MAX_INTERCEPTORS;
         case SIEGE -> MAX_SIEGES;
         case COMMANDER -> MAX_COMMANDERS;
      };
   }

   /** Points the hive has in total: the base budget plus the captive bonus (each held captive = 1, capped). */
   public static int budget(int captivesDelivered) {
      return BUDGET + Math.min(CAPTIVE_BONUS_CAP, Math.max(0, captivesDelivered));
   }

   /** The new unit kinds the director buys with its budget. */
   public enum UnitKind {
      CAPTOR,
      HEAVY,
      INTERCEPTOR,
      SIEGE,
      COMMANDER
   }
}
