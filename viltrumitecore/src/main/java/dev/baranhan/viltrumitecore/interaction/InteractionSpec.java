package dev.baranhan.viltrumitecore.interaction;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Static description of one contextual interaction: stable catalog id ({@code docs/contextual-interactions.json}),
 * trigger, priority (higher is asked first; the first match wins), the per-pair re-trigger cooldown and the
 * aftermath window during which further hits between the same two entities are swallowed (one collision never
 * deals damage twice). Pure.
 */
public record InteractionSpec(String id, InteractionTrigger trigger, int priority, int cooldownTicks, int aftermathTicks) {
   private static final Pattern ID = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");

   public InteractionSpec {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(trigger, "trigger");
      if (!ID.matcher(id).matches()) {
         throw new IllegalArgumentException("interaction id must be snake_case: " + id);
      }
      if (cooldownTicks < 0 || aftermathTicks < 0 || aftermathTicks > cooldownTicks && cooldownTicks > 0) {
         throw new IllegalArgumentException("bad timing for " + id + ": cooldown " + cooldownTicks + ", aftermath " + aftermathTicks);
      }
   }
}
