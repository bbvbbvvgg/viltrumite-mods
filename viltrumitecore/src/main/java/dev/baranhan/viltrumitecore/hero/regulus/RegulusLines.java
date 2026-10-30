package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Regulus' spoken lines through the shared subtitle system (RU + EN and every locale). Text lives
 * under {@code subtitle.viltrumitecore.regulus.*}; own phrasing in his manner, no anime quotes.
 */
public final class RegulusLines {
   public static final String PREFIX = "subtitle.viltrumitecore.regulus.";

   public static final HeroLine ULT_ACCUSE = ult("regulus:ult_accuse", "ult_accuse");
   public static final HeroLine ULT_SENTENCE = ult("regulus:ult_sentence", "ult_sentence");
   public static final HeroLine ULT_FLICK = ult("regulus:ult_flick", "ult_flick");
   public static final HeroLine LION = ability("regulus:lion", "lion", 300);
   public static final HeroLine SEIZURE = ability("regulus:seizure", "seizure", 0);
   public static final HeroLine KING = ability("regulus:king", "king", 400);
   public static final HeroLine REPLY = ability("regulus:reply", "reply", 300);
   public static final HeroLine HOST_LOST = ability("regulus:host_lost", "host_lost", 200);

   public static final String EVENT_ULT_SENTENCE = "regulus:ult_sentence";
   public static final String EVENT_ULT_FLICK = "regulus:ult_flick";
   public static final String EVENT_SEIZURE = "regulus:seizure";
   public static final String EVENT_REPLY = "regulus:reply";
   public static final String EVENT_HOST_LOST = "regulus:host_lost";

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(RegulusAbilities.KINGDOM_OF_ONE, ULT_ACCUSE)
      .onEvent(EVENT_ULT_SENTENCE, ULT_SENTENCE)
      .onEvent(EVENT_ULT_FLICK, ULT_FLICK)
      .onAbility(RegulusAbilities.LIONS_HEART, LION)
      .onAbility(RegulusAbilities.LITTLE_KING, KING)
      .onEvent(EVENT_SEIZURE, SEIZURE)
      .onEvent(EVENT_REPLY, REPLY)
      .onEvent(EVENT_HOST_LOST, HOST_LOST)
      .build();

   /** Every line key (lang tests). */
   public static final String[] KEYS = {"ult_accuse", "ult_sentence", "ult_flick", "lion", "seizure", "king", "reply", "host_lost"};

   private RegulusLines() {
   }

   private static HeroLine ult(String id, String key) {
      return HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ULTIMATE).range(48.0).build();
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      HeroLine.Builder builder = HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ABILITY).range(32.0);
      if (cooldown > 0) {
         builder.cooldown(cooldown);
      }

      return builder.build();
   }
}
