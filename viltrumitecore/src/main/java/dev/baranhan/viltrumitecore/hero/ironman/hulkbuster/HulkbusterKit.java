package dev.baranhan.viltrumitecore.hero.ironman.hulkbuster;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Transient Hulkbuster kit state (spec §14.3, plan stage 5 Tasks 6-7) and
 * its pure rules. Cooldowns of the slam and the hop persist through the
 * layer's life only (they are short); the layer cooldown is in HulkbusterLayer.
 */
public final class HulkbusterKit {
   public static final float PUNCH_DAMAGE = 14.0F;
   public static final int PUNCH_CADENCE = 12;
   public static final double PUNCH_REACH = 4.5;
   public static final double PUNCH_KNOCKBACK = 1.8;
   public static final double JACKHAMMER_RANGE = 3.5;
   public static final int JACKHAMMER_INTERVAL = 4;
   public static final float JACKHAMMER_DAMAGE = 4.0F;
   public static final int JACKHAMMER_MAX = 60;
   public static final double JACKHAMMER_RELEASE_KNOCKBACK = 1.6;
   public static final int SLOW_REPULSOR_CHARGE = 30;
   public static final float SLOW_REPULSOR_DAMAGE = 14.0F;
   public static final float SLOW_REPULSOR_COST = 10.0F;
   public static final double GRAB_RANGE = 3.5;
   public static final double THROW_SPEED = 2.2;
   public static final float THROW_IMPACT_DAMAGE = 8.0F;
   /** Longest carry (4 s): the target drops after it (no endless carry in PvP). */
   public static final int GRAB_MAX_TICKS = 80;
   public static final int THROW_TRACK_TICKS = 40;
   public static final int SLAM_COOLDOWN = 200;
   public static final double SLAM_RADIUS = 6.0;
   public static final float SLAM_DAMAGE = 10.0F;
   public static final int SLAM_ARM_TICKS = 60;
   public static final int HOP_COOLDOWN = 80;
   public static final float HOP_COST = 8.0F;
   public static final int HOP_TICKS = 10;
   public static final float SHIELD_STRENGTH = 1.5F;

   public int punchCooldown;
   public boolean punchRight;
   /** Jackhammer: ticks held (-1 idle), pinned target id. */
   public int jackhammerTicks = -1;
   public int jackhammerTarget = -1;
   /** Slow repulsor charge ticks (-1 idle). */
   public int charge = -1;
   /** Grab: carried entity id and its control effect id. */
   public int grabbedId = -1;
   /** UUID of the carried target: the control is released by it even when the entity is gone. */
   @Nullable
   public UUID grabbedUuid;
   @Nullable
   public UUID grabEffect;
   public int grabTicks;
   /** Thrown entities: id → ticks left of the impact check. */
   public final Map<Integer, Integer> thrown = new HashMap<>();
   public int slamCooldown;
   public boolean slamArmed;
   public int slamTicks;
   public int hopCooldown;
   public int hopTicks;

   /** Punch ready: cadence passed. */
   public boolean punchReady() {
      return this.punchCooldown <= 0;
   }

   /** Jackhammer hit on this held tick. */
   public static boolean jackhammerHit(int heldTicks) {
      return heldTicks > 0 && heldTicks % JACKHAMMER_INTERVAL == 0;
   }

   public static boolean slowRepulsorCharged(int chargeTicks) {
      return chargeTicks >= SLOW_REPULSOR_CHARGE;
   }

   /** Slam ring damage with distance falloff (full at the centre, half at the edge). */
   public static float slamDamage(double distance) {
      if (distance > SLAM_RADIUS) {
         return 0.0F;
      }

      return (float)(SLAM_DAMAGE * (1.0 - 0.5 * distance / SLAM_RADIUS));
   }

   public void tickCooldowns() {
      if (this.punchCooldown > 0) {
         this.punchCooldown--;
      }

      if (this.slamCooldown > 0) {
         this.slamCooldown--;
      }

      if (this.hopCooldown > 0) {
         this.hopCooldown--;
      }
   }

   /** Every channel off (layer gone, control, death). Cooldowns stay. */
   public void stopChannels() {
      this.jackhammerTicks = -1;
      this.jackhammerTarget = -1;
      this.charge = -1;
      this.slamArmed = false;
      this.slamTicks = 0;
      this.hopTicks = 0;
   }

   public void reset() {
      this.stopChannels();
      this.punchCooldown = 0;
      this.slamCooldown = 0;
      this.hopCooldown = 0;
      this.thrown.clear();
   }
}
