package dev.baranhan.viltrumitecore.worldevent.ultron;

/**
 * Doctrine choice (pure, unit-tested): every doctrine gets a score from the situation and the
 * scenario's weights; Ultron switches when another doctrine beats the current one by
 * {@link #SWITCH_MARGIN} after holding it at least {@link #MIN_HOLD} ticks, or at once when the current
 * one stops making sense (score below {@link #INVALID}).
 */
public final class UltronStrategy {
   public static final float SWITCH_MARGIN = 1.2F;
   public static final int MIN_HOLD = 45 * UltronRules.TPS;
   public static final float INVALID = 0.15F;

   /** What the director knows right now (only real observations: sightings, losses, its own network). */
   public record Situation(UltronPhase phase, int sitesLeft, int engineers, int units, int unitCap, int foundries, int controllers, int captureTargets,
      float topThreat, int playersSeen, int recentLosses) {
   }

   private UltronStrategy() {
   }

   public static float score(UltronDoctrine d, Situation s, UltronScenario scenario) {
      float w = scenario.doctrineWeight(d);
      float phaseWar = s.phase.atLeast(UltronPhase.MACHINE_WAR) ? 1.0F : 0.0F;
      float fill = s.unitCap <= 0 ? 0.0F : Math.min(1.0F, (float)s.units / s.unitCap);
      float v = switch (d) {
         case EXPANSION -> (s.sitesLeft > 0 ? 1.0F : 0.15F) * (s.phase == UltronPhase.SPREAD ? 1.6F : s.phase == UltronPhase.AWAKENING ? 1.2F : 0.7F)
            * (s.engineers > 0 ? 1.0F : 0.35F) + (s.recentLosses >= 4 ? 0.3F : 0.0F);
         case SIEGE -> (0.4F + fill) * (0.45F + phaseWar) * (s.foundries > 0 ? 1.0F : 0.7F) * (s.playersSeen > 0 ? 1.0F : 0.5F);
         case HUNT -> (s.playersSeen > 0 ? 0.5F + Math.min(1.5F, s.topThreat / 40.0F) : 0.05F) * (0.5F + 0.7F * phaseWar);
         case ASSIMILATION -> (s.captureTargets > 0 ? 0.6F + Math.min(1.0F, s.captureTargets * 0.25F) : 0.05F) * (s.controllers > 0 ? 1.0F : 0.1F);
      };
      return w * v;
   }

   /** Best doctrine (ties: the lower ordinal, so the choice is deterministic). */
   public static UltronDoctrine best(Situation s, UltronScenario scenario) {
      UltronDoctrine best = UltronDoctrine.EXPANSION;
      float bestScore = -1.0F;
      for (UltronDoctrine d : UltronDoctrine.values()) {
         float sc = score(d, s, scenario);
         if (sc > bestScore) {
            bestScore = sc;
            best = d;
         }
      }
      return best;
   }

   /** The doctrine to hold now, given the current one and how long it has been held. */
   public static UltronDoctrine decide(UltronDoctrine current, int heldTicks, Situation s, UltronScenario scenario) {
      UltronDoctrine best = best(s, scenario);
      if (best == current) {
         return current;
      }
      float cur = score(current, s, scenario);
      if (cur < INVALID * scenario.doctrineWeight(current)) {
         return best;
      }
      if (heldTicks < MIN_HOLD) {
         return current;
      }
      return score(best, s, scenario) >= cur * SWITCH_MARGIN ? best : current;
   }
}
