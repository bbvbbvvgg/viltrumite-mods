package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * What a foundry builds next (pure, unit-tested): the kind with the biggest weighted deficit under the
 * caps. Weights come from the scenario's composition, shifted by the doctrine and the running protocols
 * (ANTI_AIR favours interceptors, DEFLECTOR heavies, engineers when there is building to do).
 * Returns null when nothing may be built (caps, budgets).
 */
public final class UltronProduction {
   private UltronProduction() {
   }

   /** Room to build at all: alive below the phase cap, the phase budget and the event cap not spent. */
   public static boolean mayBuild(UltronPhase phase, int alive, int players, int builtThisPhase, int builtTotal) {
      return alive < UltronRules.aliveCap(phase, players) && builtThisPhase < UltronRules.phaseBudget(phase)
         && builtTotal < UltronRules.EVENT_PRODUCTION_CAP;
   }

   /**
    * Next kind for counts {@code alive[kind]}, or null. {@code buildJobs}: sites to raise / nodes to reboot.
    */
   @javax.annotation.Nullable
   public static UltronUnitKind next(UltronScenario scenario, int[] alive, UltronDoctrine doctrine, boolean antiAir, boolean deflector, int buildJobs) {
      UltronUnitKind best = null;
      float bestNeed = 0.0F;
      for (UltronUnitKind kind : UltronUnitKind.values()) {
         int have = alive[kind.ordinal()];
         if (have >= UltronRules.kindCap(kind)) {
            continue;
         }
         float w = scenario.composition(kind) * weight(kind, doctrine, antiAir, deflector, buildJobs);
         if (w <= 0.0F) {
            continue;
         }
         // deficit: the weight shared among the ones alive, with a bonus so a missing kind comes first
         float need = w / (have + 1.0F) + (have == 0 ? 0.5F * w : 0.0F);
         if (need > bestNeed) {
            bestNeed = need;
            best = kind;
         }
      }
      return best;
   }

   static float weight(UltronUnitKind kind, UltronDoctrine doctrine, boolean antiAir, boolean deflector, int buildJobs) {
      float w = 1.0F;
      switch (kind) {
         case INTERCEPTOR -> w *= antiAir ? 3.0F : 1.0F;
         case BRUTE -> w *= (deflector ? 1.5F : 1.0F) * (doctrine == UltronDoctrine.SIEGE ? 1.5F : 1.0F);
         case SENTRY -> w *= doctrine == UltronDoctrine.SIEGE ? 1.25F : 1.0F;
         case DRONE -> w *= doctrine == UltronDoctrine.HUNT ? 1.6F : antiAir ? 1.3F : 1.0F;
         case ENGINEER -> w *= buildJobs > 0 || doctrine == UltronDoctrine.EXPANSION || doctrine == UltronDoctrine.ASSIMILATION ? 1.4F : 0.5F;
      }
      return w;
   }
}
