package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

/**
 * The Hulkbuster block of the Iron Man public snapshot (plan H2): extraCooldowns indices from
 * {@link #BASE} on (Iron Man owns 0..8). Pure encode / decode; absent trailing values decode as 0.
 * The move clock itself travels in actionId (= HulkMove.WIRE_OFFSET + wire id), actionElapsed and
 * actionLength; the phase and combo step here.
 */
public final class HulkbusterWire {
   public static final int BASE = 9;
   /** Phase (bits 0-1) and step (2+) of the running move, StagedTimeline.variant. */
   public static final int MOVE_VARIANT = BASE;
   /** Motion: state (bits 0-3), direction (4-5), dash charges (6-7), damage stage (8-9), spare arm used (10), charge level (11-12). */
   public static final int MOTION = BASE + 1;
   /** Ticks since the motion state began. */
   public static final int MOTION_TICKS = BASE + 2;
   public static final int CD_REPULSORS = BASE + 3;
   public static final int CD_RIFT = BASE + 4;
   public static final int CD_CAGE = BASE + 5;
   public static final int CD_SPARE_ARM = BASE + 6;
   public static final int CD_ULTIMATE = BASE + 7;
   public static final int CD_GRAB = BASE + 8;
   public static final int DASH_RECHARGE = BASE + 9;
   /** Grab victim entity id + 1 (0 = none). */
   public static final int VICTIM = BASE + 10;
   /** Escape meter of the victim 0..100. */
   public static final int ESCAPE = BASE + 11;
   /** Suit-up scene: tick of the scene (or 0) and its kind (1 air, 2 ground) in bit 16+. */
   public static final int SCENE = BASE + 12;
   /** Veronica cage: phase + 1 in bits 12+, ticks below (HulkbusterCage.wire); 0 = none. */
   public static final int CAGE = BASE + 13;
   public static final int SIZE = BASE + 14;

   private HulkbusterWire() {
   }

   public static int motion(HulkbusterMotion.State state, int direction, int dashCharges, int damageStage, boolean spareUsed, int chargeLevel) {
      return state.ordinal() & 15 | (direction & 3) << 4 | (dashCharges & 3) << 6 | (damageStage & 3) << 8 | (spareUsed ? 1 : 0) << 10 | (chargeLevel & 3) << 11;
   }

   public static HulkbusterMotion.State state(int motion) {
      return HulkbusterMotion.State.byOrdinal(motion & 15);
   }

   public static int direction(int motion) {
      return motion >> 4 & 3;
   }

   public static int dashCharges(int motion) {
      return motion >> 6 & 3;
   }

   public static int damageStage(int motion) {
      return motion >> 8 & 3;
   }

   public static boolean spareUsed(int motion) {
      return (motion >> 10 & 1) != 0;
   }

   public static int chargeLevel(int motion) {
      return motion >> 11 & 3;
   }

   /** Scene word: tick (bits 0-7), kind 1 air / 2 ground (8-9), frame yaw as a byte (10-17), assemble tick + 1 (18-24). */
   public static int scene(int tick, int kind, float yaw, int assemble) {
      int yawByte = Math.floorMod(Math.round(yaw / 360.0F * 256.0F), 256);
      return Math.max(0, Math.min(255, tick)) | (kind & 3) << 8 | yawByte << 10 | Math.max(0, Math.min(127, assemble + 1)) << 18;
   }

   /**
    * Exit word (HulkbusterExit): the exit tick, kind 1 = Veronica takes the empty shell / 2 = it crumbles, the frozen
    * facing, and in the assemble bits 1 when Tony steps out forward (0 = in place).
    */
   public static int exitScene(int tick, boolean veronica, float yaw, boolean stepOut) {
      return scene(tick, veronica ? 1 : 2, yaw, stepOut ? 0 : -1);
   }

   public static boolean exitVeronica(int scene) {
      return sceneKind(scene) == 1;
   }

   public static boolean exitStepOut(int scene) {
      return sceneAssemble(scene) == 0;
   }

   public static int sceneTick(int scene) {
      return scene & 0xFF;
   }

   public static int sceneKind(int scene) {
      return scene >> 8 & 3;
   }

   public static float sceneYaw(int scene) {
      return (scene >> 10 & 0xFF) * 360.0F / 256.0F;
   }

   /** Assemble tick, -1 before the assembly (or no scene). */
   public static int sceneAssemble(int scene) {
      return (scene >> 18 & 0x7F) - 1;
   }
}
