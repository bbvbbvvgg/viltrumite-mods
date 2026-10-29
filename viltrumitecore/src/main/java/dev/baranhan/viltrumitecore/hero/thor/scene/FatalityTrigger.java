package dev.baranhan.viltrumitecore.hero.thor.scene;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * When a fatality starts (C-B1, C-M13, C-M16; decisions 53, 69): B's
 * LETHAL_HIT only marks a candidate (the blow would take the last health);
 * the scene starts in the victim's real death (LivingDeathEvent), which
 * vanilla reaches only after the Totem of Undying check failed. A totem that
 * saves the victim means no death event, so the candidate simply expires at
 * the end of the tick: no fatality, no burnt totem followed by a forced kill.
 * A candidate is claimed once, only by a death in the same tick whose
 * killer is that Thor. Pure; ThorScenes holds the server instance.
 */
public final class FatalityTrigger {
   private record Candidate(UUID thor, long tick) {
   }

   private final Map<UUID, Candidate> candidates = new HashMap<>();

   /** LETHAL_HIT accepted by the verdict: this victim may get a fatality if it really dies this tick. */
   public void mark(UUID victim, UUID thor, long tick) {
      this.candidates.put(victim, new Candidate(thor, tick));
   }

   /**
    * The victim is dying now, killed by {@code killer}: the Thor whose
    * candidate this is (consumed), or null (no candidate, another killer, or
    * a stale mark from an earlier tick).
    */
   @Nullable
   public UUID claim(UUID victim, @Nullable UUID killer, long tick) {
      Candidate candidate = this.candidates.remove(victim);
      if (candidate == null || candidate.tick() != tick || killer == null || !candidate.thor().equals(killer)) {
         return null;
      }

      return candidate.thor();
   }

   /** End of a server tick: unclaimed candidates (the victim survived: totem, absorption, a cancelled death) are dropped. */
   public void expire() {
      this.candidates.clear();
   }

   public boolean pending(UUID victim) {
      return this.candidates.containsKey(victim);
   }

   public void forget(UUID entity) {
      this.candidates.remove(entity);
      this.candidates.values().removeIf(candidate -> candidate.thor().equals(entity));
   }
}
