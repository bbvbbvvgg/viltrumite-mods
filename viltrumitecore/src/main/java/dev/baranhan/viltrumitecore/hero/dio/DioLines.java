package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * DIO's spoken lines (cinematic subtitles). Text keys
 * {@code subtitle.viltrumitecore.dio.*}, speaker {@code subtitle.viltrumitecore.speaker.dio}.
 * Each line starts once when the server accepts the action; a cancelled cast interrupts it.
 */
public final class DioLines {
   public static final String RESUME_EVENT = "dio:time_resume";
   public static final String SUMMON_EVENT = "dio:summon";

   /** Time stop accepted: "ZA WARUDO! Toki yo tomare!" */
   public static final HeroLine TIME_STOP = HeroLine.builder(DioAbilities.TIME_STOP)
      .text("subtitle.viltrumitecore.dio.time_stop")
      .priority(HeroLine.Priority.ABILITY)
      .range(48.0)
      .build();
   /** Twenty ticks before the world moves again. */
   public static final HeroLine TIME_RESUME = HeroLine.builder(RESUME_EVENT)
      .text("subtitle.viltrumitecore.dio.time_resume")
      .priority(HeroLine.Priority.ABILITY)
      .range(48.0)
      .build();
   /** The ultimate: three pages (Road Roller, no escape, MUDA + WRYYY). */
   public static final HeroLine ROAD_ROLLER = HeroLine.builder(DioAbilities.ROAD_ROLLER)
      .textSeries("subtitle.viltrumitecore.dio.road_roller.", 3)
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(DioRules.ROLLER_RESUME)
      .range(64.0)
      .build();
   /** First summon of a session-like cooldown: "The World!" */
   public static final HeroLine SUMMON = HeroLine.builder(SUMMON_EVENT)
      .text("subtitle.viltrumitecore.dio.summon")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 30)
      .range(32.0)
      .build();

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(DioAbilities.TIME_STOP, TIME_STOP)
      .onAbility(DioAbilities.ROAD_ROLLER, ROAD_ROLLER)
      .onEvent(RESUME_EVENT, TIME_RESUME)
      .onEvent(SUMMON_EVENT, SUMMON)
      .build();

   private DioLines() {
   }
}
