package dev.baranhan.viltrumitecore.hero.ironman.combat;

import dev.baranhan.viltrumitecore.hero.ironman.Energy;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;

/**
 * Repulsor rules (spec §8.1), pure. RMB press starts a charge; the release
 * decides: under {@link IronManRules#REPULSOR_TAP_TICKS} a shot (cost 2,
 * hands alternate), else a charged shot (cost 6, power by charge), at the
 * full charge a volley from both hands (cost 10). Energy is spent on release.
 */
public final class Repulsor {
   public enum Kind {
      NONE,
      SHOT,
      CHARGED,
      VOLLEY,
      FIZZLE
   }

   /** Result of one release. {@code power} 0..1 drives damage, knockback and movement. */
   public record Shot(Kind kind, float power, float damage, float cost, boolean rightHand) {
      public static final Shot NONE = new Shot(Kind.NONE, 0.0F, 0.0F, 0.0F, true);

      public boolean fired() {
         return this.kind == Kind.SHOT || this.kind == Kind.CHARGED || this.kind == Kind.VOLLEY;
      }
   }

   private boolean charging;
   private int chargeTicks;
   private int cooldown;

   /** Debug reset: ready to charge again (a charge in progress is untouched). */
   public void clearCooldown() {
      this.cooldown = 0;
   }
   private boolean nextRight = true;

   /** True when a charge started (not while charging or cooling down). */
   public boolean press() {
      if (this.charging || this.cooldown > 0) {
         return false;
      }

      this.charging = true;
      this.chargeTicks = 0;
      return true;
   }

   public void tick() {
      if (this.charging && this.chargeTicks < IronManRules.REPULSOR_CHARGE_MAX) {
         this.chargeTicks++;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
      }
   }

   /** Forced release (suit off, control, screen): no shot, no cost. */
   public void cancel() {
      this.charging = false;
      this.chargeTicks = 0;
   }

   public boolean charging() {
      return this.charging;
   }

   public int chargeTicks() {
      return this.chargeTicks;
   }

   /** Charge 0..1 above the tap window. */
   public float charge() {
      return charge(this.chargeTicks);
   }

   public static float charge(int ticks) {
      int span = IronManRules.REPULSOR_CHARGE_MAX - IronManRules.REPULSOR_TAP_TICKS;
      return Math.max(0.0F, Math.min(1.0F, (ticks - IronManRules.REPULSOR_TAP_TICKS) / (float)span));
   }

   public static Kind kindFor(int chargeTicks) {
      if (chargeTicks >= IronManRules.REPULSOR_CHARGE_MAX) {
         return Kind.VOLLEY;
      }

      return chargeTicks < IronManRules.REPULSOR_TAP_TICKS ? Kind.SHOT : Kind.CHARGED;
   }

   public static float cost(Kind kind) {
      return switch (kind) {
         case SHOT -> IronManRules.COST_REPULSOR;
         case CHARGED -> IronManRules.COST_REPULSOR_CHARGED;
         case VOLLEY -> IronManRules.COST_REPULSOR_VOLLEY;
         default -> 0.0F;
      };
   }

   /** Release: spend energy and fire, or fizzle when locked / not enough. */
   public Shot release(Energy energy) {
      if (!this.charging) {
         return Shot.NONE;
      }

      int ticks = this.chargeTicks;
      this.cancel();
      Kind kind = kindFor(ticks);
      float cost = cost(kind);
      if (energy.weaponsLocked() || !energy.spend(cost)) {
         this.cooldown = IronManRules.REPULSOR_COOLDOWN;
         return new Shot(Kind.FIZZLE, 0.0F, 0.0F, 0.0F, this.nextRight);
      }

      boolean right = this.nextRight;
      this.nextRight = !this.nextRight;
      this.cooldown = IronManRules.REPULSOR_COOLDOWN;
      float charge = charge(ticks);
      return switch (kind) {
         case SHOT -> new Shot(kind, 0.35F, IronManRules.REPULSOR_SHOT, cost, right);
         case CHARGED -> new Shot(kind, 0.35F + 0.65F * charge, IronManRules.REPULSOR_CHARGED_MIN
            + (IronManRules.REPULSOR_CHARGED_MAX - IronManRules.REPULSOR_CHARGED_MIN) * charge, cost, right);
         default -> new Shot(kind, 1.0F, IronManRules.REPULSOR_VOLLEY, cost, right);
      };
   }

   // ---- movement (spec §8.1) ----

   /** Back shot in flight: normalized shot direction · normalized velocity below the threshold. */
   public static boolean brakes(boolean flying, double dirDotVelocity, double speed) {
      return flying && speed > 0.05 && dirDotVelocity < IronManRules.REPULSOR_BRAKE_DOT;
   }

   public static double brakeFactor(float power) {
      return 1.0 - IronManRules.REPULSOR_BRAKE * power;
   }

   /** Down shot in HOVER (pitch in degrees, positive = down). */
   public static boolean lifts(boolean hover, float pitch) {
      return hover && pitch >= IronManRules.REPULSOR_LIFT_PITCH;
   }

   public static double liftVelocity(float power) {
      return IronManRules.REPULSOR_LIFT * power;
   }

   /** Full-charge volley into the ground under the feet. */
   public static boolean groundShockwave(boolean onGround, Kind kind, float pitch) {
      return onGround && kind == Kind.VOLLEY && pitch >= IronManRules.REPULSOR_SHOCKWAVE_PITCH;
   }

   public static double knockback(float power) {
      return IronManRules.REPULSOR_KNOCKBACK_TAP + (IronManRules.REPULSOR_KNOCKBACK_MAX - IronManRules.REPULSOR_KNOCKBACK_TAP) * power;
   }
}
