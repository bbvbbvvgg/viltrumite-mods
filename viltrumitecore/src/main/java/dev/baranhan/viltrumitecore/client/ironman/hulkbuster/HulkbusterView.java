package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterWire;
import javax.annotation.Nullable;

/**
 * The Hulkbuster state as every client reads it from the synced Iron Man snapshot (pure): the layer phase,
 * the running move (actionId = HulkMove.WIRE_OFFSET + wire id, phase and step in HulkbusterWire.MOVE_VARIANT),
 * the motion word, the suit-up scene word and the cooldowns. One place decodes the wire for the renderer,
 * the motion client, the HUD and the scene.
 */
public final class HulkbusterView {
   private HulkbusterView() {
   }

   public static HulkbusterLayer.Phase phase(HeroPublicSnapshot snapshot) {
      int ordinal = IronManFlags.get(snapshot.heroFlags(), IronManFlags.Field.HULKBUSTER_PHASE);
      HulkbusterLayer.Phase[] values = HulkbusterLayer.Phase.values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : HulkbusterLayer.Phase.NONE;
   }

   /** The machine is the player's body (ACTIVE or climbing out). */
   public static boolean big(HulkbusterLayer.Phase phase) {
      return phase == HulkbusterLayer.Phase.ACTIVE || phase == HulkbusterLayer.Phase.EXITING;
   }

   /** Tony is inside the machine (ACTIVE, or the exit before he steps out): the Mark 48 first-person arms. */
   public static boolean inside(HeroPublicSnapshot snapshot) {
      HulkbusterLayer.Phase phase = phase(snapshot);
      return phase == HulkbusterLayer.Phase.ACTIVE
         || phase == HulkbusterLayer.Phase.EXITING && dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterExit.big(snapshot.actionElapsed());
   }

   /** The suit-up scene runs (capsule, open frame, plates closing). */
   public static boolean scene(HulkbusterLayer.Phase phase) {
      return phase == HulkbusterLayer.Phase.DROPPING || phase == HulkbusterLayer.Phase.ASSEMBLING;
   }

   public static int extra(HeroPublicSnapshot snapshot, int index) {
      int[] extra = snapshot.extraCooldowns();
      return extra != null && index < extra.length ? extra[index] : 0;
   }

   /** The running Hulkbuster move, null when none. */
   @Nullable
   public static HulkMove move(HeroPublicSnapshot snapshot) {
      return snapshot.actionId() >= HulkMove.WIRE_OFFSET ? HulkMove.byWire(snapshot.actionId()) : null;
   }

   public static MovePhase movePhase(HeroPublicSnapshot snapshot) {
      return StagedTimeline.phaseOf(extra(snapshot, HulkbusterWire.MOVE_VARIANT));
   }

   public static int step(HeroPublicSnapshot snapshot) {
      return StagedTimeline.stepOf(extra(snapshot, HulkbusterWire.MOVE_VARIANT));
   }

   public static int motionWord(HeroPublicSnapshot snapshot) {
      return extra(snapshot, HulkbusterWire.MOTION);
   }

   public static HulkbusterMotion.State motion(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.state(motionWord(snapshot));
   }

   public static int motionTicks(HeroPublicSnapshot snapshot) {
      return extra(snapshot, HulkbusterWire.MOTION_TICKS);
   }

   public static int direction(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.direction(motionWord(snapshot));
   }

   public static int dashCharges(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.dashCharges(motionWord(snapshot));
   }

   public static int damageStage(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.damageStage(motionWord(snapshot));
   }

   public static int chargeLevel(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.chargeLevel(motionWord(snapshot));
   }

   /** Entity id of the held victim, -1 when none. */
   public static int victim(HeroPublicSnapshot snapshot) {
      return extra(snapshot, HulkbusterWire.VICTIM) - 1;
   }

   public static int escape(HeroPublicSnapshot snapshot) {
      return extra(snapshot, HulkbusterWire.ESCAPE);
   }

   public static int sceneWord(HeroPublicSnapshot snapshot) {
      return extra(snapshot, HulkbusterWire.SCENE);
   }

   /** Air variant of the suit-up (kind 1); ground = 2. */
   public static boolean sceneAir(HeroPublicSnapshot snapshot) {
      return HulkbusterWire.sceneKind(sceneWord(snapshot)) == 1;
   }
}
