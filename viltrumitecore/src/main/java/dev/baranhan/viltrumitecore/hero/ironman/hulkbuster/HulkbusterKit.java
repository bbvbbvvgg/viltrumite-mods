package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Transient Hulkbuster kit state (rework spec §6–7): the move timeline, the grab, the movement and the
 * ability cooldowns. Lives while the suit is on; the layer (HulkbusterLayer) owns durability and save data.
 */
public final class HulkbusterKit {
   public final StagedTimeline<HulkMove> timeline = new StagedTimeline<>();
   public final HulkbusterMotion motion = new HulkbusterMotion();
   public final Grab grab = new Grab();
   /** LMB press waiting for the tap / hold decision (-1 = none). */
   public int lmbHeld = -1;
   /** Ticks since the last combo step ended (chain restarts after HulkbusterRules.COMBO_CHAIN_WINDOW). */
   public int comboIdle = 1000;
   public int comboNext;
   public boolean comboBuffered;
   /** Charged punch: the INTRO ticks held at the release (level from HulkbusterRules.chargeLevel). */
   public int chargeHeld;
   public int blockTicks;
   /** Walked distance since the last heavy step. */
   public double stepDistance;
   public boolean blockHeld;
   public int riftWindow;
   public int repulsorsCooldown;
   public int riftCooldown;
   public int cageCooldown;
   public int ultimateCooldown;
   public int grabCooldown;
   /** Ultimate: the captured target (id) and the dive start height. */
   public int ultTarget = -1;
   @Nullable
   public UUID ultEffect;
   @Nullable
   public UUID ultTargetUuid;
   @Nullable
   public Vec3 ultDir;
   /** Suit-up scene: the player's feet when it began and the frame's facing (HulkbusterSuitUp.path). */
   @Nullable
   public Vec3 sceneStart;
   public float sceneYaw;
   /** Thrown bodies: id -> ticks left of the impact check. */
   public final Map<Integer, Integer> thrown = new HashMap<>();
   /** Veronica's cage of this Hulkbuster (one at a time). */
   @Nullable
   public HulkbusterCage cage;
   /** Last durability fraction seen (damage-stage FX edges). */
   public int lastStage;

   /** The grabbed victim (spec §6.2). */
   public static final class Grab {
      public int victimId = -1;
      @Nullable
      public UUID victimUuid;
      @Nullable
      public UUID effect;
      public int held;
      public int punches;
      public float escape;
      public boolean strong;

      public boolean active() {
         return this.victimId >= 0;
      }

      public void clear() {
         this.victimId = -1;
         this.victimUuid = null;
         this.effect = null;
         this.held = 0;
         this.punches = 0;
         this.escape = 0.0F;
         this.strong = false;
      }
   }

   public void tickCooldowns() {
      if (this.repulsorsCooldown > 0) {
         this.repulsorsCooldown--;
      }

      if (this.riftCooldown > 0) {
         this.riftCooldown--;
      }

      if (this.cageCooldown > 0) {
         this.cageCooldown--;
      }

      if (this.ultimateCooldown > 0) {
         this.ultimateCooldown--;
      }

      if (this.grabCooldown > 0) {
         this.grabCooldown--;
      }

      if (this.riftWindow > 0) {
         this.riftWindow--;
      }

      if (this.comboIdle < 1000) {
         this.comboIdle++;
      }

      this.motion.tickRecharge();
   }

   /** Every channel off (suit gone, control, death). Cooldowns stay. */
   public void stopChannels() {
      this.timeline.clear();
      this.lmbHeld = -1;
      this.comboBuffered = false;
      this.blockHeld = false;
      this.blockTicks = 0;
      this.chargeHeld = 0;
      this.ultTarget = -1;
      this.ultDir = null;
      this.motion.set(HulkbusterMotion.State.NONE);
   }

   public void reset() {
      this.stopChannels();
      this.repulsorsCooldown = 0;
      this.riftCooldown = 0;
      this.cageCooldown = 0;
      this.ultimateCooldown = 0;
      this.grabCooldown = 0;
      this.riftWindow = 0;
      this.comboIdle = 1000;
      this.comboNext = 0;
      this.thrown.clear();
      this.motion.reset();
   }

   /** Cooldown ticks of an input (HulkbusterControls). */
   public int cooldown(HulkbusterControls.Input input) {
      return switch (input) {
         case RMB -> this.grabCooldown;
         case SLOT_1 -> this.repulsorsCooldown;
         case SLOT_2 -> this.riftCooldown;
         case SLOT_3 -> this.cageCooldown;
         case ULTIMATE -> this.ultimateCooldown;
         default -> 0;
      };
   }
}
