package dev.baranhan.viltrumitecore.hero.speech;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A hero's spoken lines and what triggers them. Returned by
 * {@code HeroDefinition#lines()}; immutable once built. Several lines on one
 * trigger are variants played in rotation.
 */
public final class HeroLines {
   public static final HeroLines NONE = builder().build();

   private final Map<String, HeroLine> byId;
   private final Map<HeroLineTrigger, List<HeroLine>> byTrigger;

   private HeroLines(Map<String, HeroLine> byId, Map<HeroLineTrigger, List<HeroLine>> byTrigger) {
      this.byId = byId;
      this.byTrigger = byTrigger;
   }

   public static Builder builder() {
      return new Builder();
   }

   public Optional<HeroLine> byId(String id) {
      return Optional.ofNullable(this.byId.get(id));
   }

   /** Variants bound to this trigger, in registration order; empty when none. */
   public List<HeroLine> on(HeroLineTrigger trigger) {
      return this.byTrigger.getOrDefault(trigger, List.of());
   }

   public Collection<HeroLine> all() {
      return this.byId.values();
   }

   public boolean isEmpty() {
      return this.byId.isEmpty();
   }

   public static final class Builder {
      private final Map<String, HeroLine> byId = new LinkedHashMap<>();
      private final Map<HeroLineTrigger, List<HeroLine>> byTrigger = new LinkedHashMap<>();

      private Builder() {
      }

      /** Registers a line, optionally bound to triggers (none = played only by id, e.g. a scripted scene). */
      public Builder add(HeroLine line, HeroLineTrigger... triggers) {
         Objects.requireNonNull(line, "line");
         if (this.byId.putIfAbsent(line.id(), line) != null) {
            throw new IllegalStateException("Duplicate hero line id " + line.id());
         }

         for (HeroLineTrigger trigger : triggers) {
            this.byTrigger.computeIfAbsent(Objects.requireNonNull(trigger, "trigger"), t -> new ArrayList<>()).add(line);
         }

         return this;
      }

      public Builder onAbility(String abilityId, HeroLine line) {
         return this.add(line, HeroLineTrigger.ability(abilityId));
      }

      public Builder onEvent(String key, HeroLine line) {
         return this.add(line, HeroLineTrigger.event(key));
      }

      public HeroLines build() {
         Map<HeroLineTrigger, List<HeroLine>> triggers = new LinkedHashMap<>();
         this.byTrigger.forEach((trigger, lines) -> triggers.put(trigger, List.copyOf(lines)));
         return new HeroLines(Collections.unmodifiableMap(new LinkedHashMap<>(this.byId)), Collections.unmodifiableMap(triggers));
      }
   }
}
