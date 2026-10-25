package dev.baranhan.viltrumitecore.hero.homelander;

/**
 * Eye heat 0..100 (spec §6). Lasers and focus heat additively; after
 * HEAT_COOL_DELAY idle ticks it cools; at 100 both sources lock for
 * OVERHEAT_LOCK_TICKS. Pure: one call per server tick.
 */
public final class EyeHeat {
   private float heat;
   private int idleTicks;
   private int lockTicks;
   private boolean justOverheated;

   /** @param laser laser beam on this tick; @param focus focus on this tick */
   public void tick(boolean laser, boolean focus) {
      this.tick(laser, focus, 1.0F);
   }

   /** @param coolScale foreign slow-down of the cooling only (HeroResourceRegen), 0..1 */
   public void tick(boolean laser, boolean focus, float coolScale) {
      this.justOverheated = false;
      boolean allowed = this.lockTicks <= 0;
      if (this.lockTicks > 0) {
         this.lockTicks--;
      }

      float gain = 0.0F;
      if (allowed && laser) {
         gain += HomelanderRules.HEAT_LASER_PER_TICK;
      }

      if (allowed && focus) {
         gain += HomelanderRules.HEAT_FOCUS_PER_TICK;
      }

      if (gain > 0.0F) {
         this.idleTicks = 0;
         this.heat = Math.min(HomelanderRules.HEAT_MAX, this.heat + gain);
         if (this.heat >= HomelanderRules.HEAT_MAX) {
            this.lockTicks = HomelanderRules.OVERHEAT_LOCK_TICKS;
            this.justOverheated = true;
         }
      } else {
         this.idleTicks++;
         if (this.idleTicks > HomelanderRules.HEAT_COOL_DELAY && this.heat > 0.0F) {
            this.heat -= HomelanderRules.HEAT_COOL_PER_TICK * coolScale;
            if (this.heat < 1.0E-3F) {
               this.heat = 0.0F;
            }
         }
      }
   }

   public float heat() {
      return this.heat;
   }

   public boolean locked() {
      return this.lockTicks > 0;
   }

   /** True only on the tick heat reached 100. */
   public boolean justOverheated() {
      return this.justOverheated;
   }

   /** May lasers and focus run now. */
   public boolean sourcesAllowed() {
      return this.lockTicks <= 0;
   }

   /** Heat for HeroPublicSnapshot.resource (x10, 0..1000). */
   public int snapshotValue() {
      return Math.round(this.heat * 10.0F);
   }

   public void reset() {
      this.heat = 0.0F;
      this.idleTicks = 0;
      this.lockTicks = 0;
      this.justOverheated = false;
   }
}
