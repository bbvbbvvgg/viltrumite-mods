package dev.baranhan.viltrumitecore.codex.data;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * How a Codex 2.0 entry opens (pure). {@code target} is a lowercase id: a hero key, an entity type, a
 * dimension, a world event type or an interaction id; empty for {@link Type#DEFAULT} and {@link Type#MANUAL}.
 */
public record UnlockCondition(Type type, String target) {
   public enum Type {
      /** Open from the start. */
      DEFAULT("default", false),
      /** The player has become this hero at least once. */
      HERO_CHOSEN("hero_chosen", true),
      /** The player killed an entity of this type. */
      ENTITY_KILLED("entity_killed", true),
      /** An entity of this type came near the player. */
      ENTITY_SEEN("entity_seen", true),
      /** The player entered this dimension. */
      DIMENSION("dimension", true),
      /** A world event of this type ran in the player's level. */
      WORLD_EVENT("world_event", true),
      /** A contextual interaction with this id fired for the player. */
      INTERACTION("interaction", true),
      /** Only commands (and future quests) open it. */
      MANUAL("manual", false);

      public final String key;
      public final boolean needsTarget;

      Type(String key, boolean needsTarget) {
         this.key = key;
         this.needsTarget = needsTarget;
      }

      @Nullable
      public static Type byKey(@Nullable String key) {
         if (key == null) {
            return null;
         }
         String k = key.trim().toLowerCase(Locale.ROOT);
         for (Type t : values()) {
            if (t.key.equals(k)) {
               return t;
            }
         }
         return null;
      }
   }

   public UnlockCondition {
      target = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
   }

   public boolean matches(Type type, @Nullable String value) {
      return this.type == type && value != null && this.target.equals(value.trim().toLowerCase(Locale.ROOT));
   }
}
