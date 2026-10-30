package dev.baranhan.viltrumitecore.hero.homelander;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerPlayer;

/**
 * Homelander's spoken lines (global subtitles expansion; before: none). Text-only subtitles, no voice
 * audio: text keys {@code subtitle.viltrumitecore.homelander.*}, speaker
 * {@code subtitle.viltrumitecore.speaker.homelander} (explicit, so a legacy Viltrumite save that loads
 * as Homelander shows the same name). The roar is wordless and has no line. Long cooldowns: the
 * lasers are a held weapon used all the time, the line is a flourish on the first press only.
 */
public final class HomelanderLines {
   public static final String PREFIX = "subtitle.viltrumitecore.homelander.";
   public static final String SPEAKER = "subtitle.viltrumitecore.speaker.homelander";
   public static final String EVENT_OVERHEAT = "homelander:overheat";

   public static final HeroLine LASERS = ability(HomelanderAbilities.LASERS, "lasers", 20 * 30);
   public static final HeroLine FOCUS = ability(HomelanderAbilities.FOCUS, "focus", 20 * 30);
   public static final HeroLine OVERHEAT = ability(EVENT_OVERHEAT, "overheat", 20 * 45);
   public static final HeroLine KILL_A = ambient("homelander:kill_a", "kill_a", 20 * 60);
   public static final HeroLine KILL_B = ambient("homelander:kill_b", "kill_b", 20 * 60);
   public static final HeroLine LOW_HEALTH = ambient("homelander:low_health", "low_health", 20 * 90);
   public static final HeroLine ENTER = ability("homelander:enter", "enter", 20 * 60);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(HomelanderAbilities.LASERS, LASERS)
      .onAbility(HomelanderAbilities.FOCUS, FOCUS)
      .onEvent(EVENT_OVERHEAT, OVERHEAT)
      .onEvent(HeroLineEvents.KILL, KILL_A)
      .onEvent(HeroLineEvents.KILL, KILL_B)
      .onEvent(HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   /** Every line key (lang tests). */
   public static final String[] KEYS = {"lasers", "focus", "overheat", "kill_a", "kill_b", "low_health", "enter"};

   private HomelanderLines() {
   }

   public static void say(ServerPlayer player, String event) {
      HeroSpeech.trigger(player, HeroLineTrigger.event(event));
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(PREFIX + key).speaker(SPEAKER).priority(HeroLine.Priority.ABILITY).cooldown(cooldown).range(32.0).build();
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(PREFIX + key).speaker(SPEAKER).priority(HeroLine.Priority.AMBIENT).cooldown(cooldown).range(24.0).build();
   }
}
