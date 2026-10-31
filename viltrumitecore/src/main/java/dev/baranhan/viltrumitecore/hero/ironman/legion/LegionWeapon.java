package dev.baranhan.viltrumitecore.hero.ironman.legion;

/** The stage 4 signature each mark brings into the Legion (MarkId.signatureKey). */
public enum LegionWeapon {
   MICRO_LASER,
   ROCKET_FIST,
   CAMO,
   STARBOOST,
   PULSE_UNIBEAM,
   SHOULDER_GUN,
   SLAM;

   /** Splash or beam that can catch bystanders: never close to Tony, never as overkill. */
   public boolean heavy() {
      return this == PULSE_UNIBEAM || this == SLAM;
   }

   /** Needs a target to fire at (camouflage and the starboost dash are self moves). */
   public boolean needsTarget() {
      return this != CAMO;
   }
}
