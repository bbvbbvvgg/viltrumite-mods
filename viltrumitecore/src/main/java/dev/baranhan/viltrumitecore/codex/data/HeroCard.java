package dev.baranhan.viltrumitecore.codex.data;

import java.util.List;

/**
 * What the hero select screen shows about one hero (pure): role, play styles, difficulty 1..5, accent colour
 * and the three-step first-time tutorial. Texts: {@code heroselect.viltrumitecore.role.<role>},
 * {@code .style.<style>}, {@code .hero.<hero>.lore}; a tutorial step's {@code text} is a lang key with one
 * {@code %s} for the key name, {@code key} is a KeyMapping name such as {@code key.viltrumitecore.ability_1}.
 */
public record HeroCard(String hero, String role, List<String> styles, int difficulty, int accent, List<TutorialStep> tutorial) {
   public static final List<String> ROLES = List.of("brawler", "tank", "assassin", "mage", "marksman", "summoner", "controller", "human");
   public static final List<String> STYLES = List.of("melee", "ranged", "flight", "speed", "area", "control", "summons", "defense", "burst",
      "time", "armor", "mobility", "stealth", "sustain", "transform");
   public static final int MIN_DIFFICULTY = 1;
   public static final int MAX_DIFFICULTY = 5;
   public static final int TUTORIAL_STEPS = 3;
   public static final int MAX_STYLES = 3;
   public static final int DEFAULT_ACCENT = 0xFFE3A3;

   public record TutorialStep(String text, String key) {
   }

   public HeroCard {
      styles = List.copyOf(styles);
      tutorial = List.copyOf(tutorial);
   }

   /** A hero without a card still shows up: neutral role, no styles, no tutorial. */
   public static HeroCard fallback(String hero) {
      return new HeroCard(hero, "brawler", List.of(), 2, DEFAULT_ACCENT, List.of());
   }

   public static String roleKey(String role) {
      return "heroselect.viltrumitecore.role." + role;
   }

   public static String styleKey(String style) {
      return "heroselect.viltrumitecore.style." + style;
   }

   public String loreKey() {
      return "heroselect.viltrumitecore.hero." + this.hero + ".lore";
   }
}
