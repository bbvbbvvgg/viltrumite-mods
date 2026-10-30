package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Regulus' spoken lines through the shared subtitle system (RU + EN and every locale). Text lives
 * under {@code subtitle.viltrumitecore.regulus.*}; own phrasing in his manner, no anime quotes.
 */
public final class RegulusLines {
   public static final String PREFIX = "subtitle.viltrumitecore.regulus.";

   /*
    * The ultimate's three phase lines are timed to their phase (RegulusRules ULT_SENTENCE / ULT_FLICK /
    * ULT_LENGTH) and stop when the scene aborts. Before: automatic durations of ~5-6 s each, so the
    * accusation was cut by the sentence after 2.5 s, mid-read.
    */
   public static final HeroLine ULT_ACCUSE = ult("regulus:ult_accuse", "ult_accuse", RegulusRules.ULT_SENTENCE);
   public static final HeroLine ULT_SENTENCE = ult("regulus:ult_sentence", "ult_sentence", RegulusRules.ULT_FLICK - RegulusRules.ULT_SENTENCE);
   public static final HeroLine ULT_FLICK = ult("regulus:ult_flick", "ult_flick", RegulusRules.ULT_LENGTH - RegulusRules.ULT_FLICK);
   public static final HeroLine LION = ability("regulus:lion", "lion", 300);
   public static final HeroLine SEIZURE = ability("regulus:seizure", "seizure", 0);
   public static final HeroLine KING = ability("regulus:king", "king", 400);
   public static final HeroLine REPLY = ability("regulus:reply", "reply", 300);
   public static final HeroLine HOST_LOST = ability("regulus:host_lost", "host_lost", 200);
   public static final HeroLine SAND = ability("regulus:sand", "sand", 500);
   public static final HeroLine SEVER = ability("regulus:sever", "sever", 500);
   public static final HeroLine TRAMPLE = ability("regulus:trample", "trample", 500);
   public static final HeroLine KILL_A = ambient("regulus:kill_a", "kill_a", 1200);
   public static final HeroLine KILL_B = ambient("regulus:kill_b", "kill_b", 1200);
   public static final HeroLine LOW_HEALTH = ambient("regulus:low_health", "low_health", 1800);
   public static final HeroLine ENTER = ability("regulus:enter", "enter", 1200);

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
      .onAbility(RegulusAbilities.STOPPED_SAND, SAND)
      .onAbility(RegulusAbilities.AIR_SEVERANCE, SEVER)
      .onAbility(RegulusAbilities.TRAMPLE, TRAMPLE)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.KILL, KILL_A)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.KILL, KILL_B)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   /** Every line key (lang tests). */
   public static final String[] KEYS = {"ult_accuse", "ult_sentence", "ult_flick", "lion", "seizure", "king", "reply", "host_lost", "sand", "sever",
      "trample", "kill_a", "kill_b", "low_health", "enter"};

   private RegulusLines() {
   }

   private static HeroLine ult(String id, String key, int ticks) {
      return HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ULTIMATE).range(48.0).duration(ticks).cooldown(0)
         .activeWhile(RegulusLines::inUlt).build();
   }

   private static boolean inUlt(net.minecraft.server.level.ServerPlayer player) {
      RegulusState state = RegulusState.of(player);
      return state != null && state.ult != null && state.move == RegulusMove.ULT;
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.AMBIENT).range(24.0).cooldown(cooldown).build();
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      HeroLine.Builder builder = HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ABILITY).range(32.0);
      if (cooldown > 0) {
         builder.cooldown(cooldown);
      }

      return builder.build();
   }
}
