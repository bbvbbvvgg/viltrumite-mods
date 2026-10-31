package dev.baranhan.viltrumitecore.client.codex;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * One article of the encyclopedia (pure data, no Minecraft types). Texts live in the lang files under
 * {@code codex.viltrumitecore.<id>.<section>}; {@link #sections()} lists the text sections in reading order,
 * with the live ones ({@link #LIVE_ABILITIES}, {@link #LIVE_KEYS}, {@link #INTERACTIONS}) filled from the
 * game itself. {@code aliases} are the ids a contextual-interaction catalog may use for this article.
 */
public record CodexEntry(String id, Category category, @Nullable String kind, int accent, Visual visual, List<String> sections,
   List<String> relations, Set<String> aliases) {
   public static final String LIVE_ABILITIES = "@abilities";
   public static final String LIVE_KEYS = "@keys";
   public static final String INTERACTIONS = "@interactions";

   public enum Category {
      HEROES("heroes"),
      NPCS("npcs"),
      EVENTS("events"),
      INTERACTIONS("interactions");

      public final String key;

      Category(String key) {
         this.key = key;
      }
   }

   /** What the portrait box shows. */
   public enum VisualType {
      /** The hero's subtitle portrait (custom model, or the bust of the hero skin). */
      HERO,
      /** Player-model busts of one or more skins (cycled), {@code slim} per skin. */
      SKINS,
      /** A client-side preview of a registered entity type, {@code variant} optional. */
      ENTITY,
      /** Od Lagna's halo. */
      HALO
   }

   public record Visual(VisualType type, List<String> refs, List<Boolean> slim, @Nullable String variant) {
      public static Visual hero(String heroKey) {
         return new Visual(VisualType.HERO, List.of(heroKey), List.of(), null);
      }

      public static Visual skin(String texture, boolean slim) {
         return new Visual(VisualType.SKINS, List.of(texture), List.of(slim), null);
      }

      public static Visual skins(List<String> textures, List<Boolean> slim) {
         return new Visual(VisualType.SKINS, List.copyOf(textures), List.copyOf(slim), null);
      }

      public static Visual entity(String type, @Nullable String variant) {
         return new Visual(VisualType.ENTITY, List.of(type), List.of(), variant);
      }

      public static Visual halo() {
         return new Visual(VisualType.HALO, List.of(), List.of(), null);
      }
   }

   public String nameKey() {
      return key(this.id, "name");
   }

   public String roleKey() {
      return key(this.id, "role");
   }

   public static String key(String id, String section) {
      return "codex.viltrumitecore." + id + "." + section;
   }

   public static String gui(String key) {
      return "codex.viltrumitecore.gui." + key;
   }
}
