package dev.baranhan.viltrumitecore.hero.speech;

import java.util.Objects;

/**
 * What makes a line play: a started ability (its registry id, e.g.
 * {@code reinhard:world_cut}) or a named hero event (any namespaced key the
 * hero code fires through {@link HeroSpeech#trigger}).
 */
public record HeroLineTrigger(Kind kind, String key) {
   public enum Kind {
      ABILITY,
      EVENT
   }

   public HeroLineTrigger {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(key, "key");
      if (key.isBlank()) {
         throw new IllegalArgumentException("blank trigger key");
      }
   }

   public static HeroLineTrigger ability(String abilityId) {
      return new HeroLineTrigger(Kind.ABILITY, abilityId);
   }

   public static HeroLineTrigger event(String key) {
      return new HeroLineTrigger(Kind.EVENT, key);
   }
}
