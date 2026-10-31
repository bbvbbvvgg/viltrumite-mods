package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Steve's spoken lines (cinematic subtitles; text only, no fake voice audio). Text keys
 * {@code subtitle.viltrumitecore.captain.*}, speaker {@code subtitle.viltrumitecore.speaker.captain}.
 */
public final class CaptainLines {
   /** «Мстители, общий сбор!» — the ultimate call. */
   public static final HeroLine ASSEMBLE = HeroLine.builder(CaptainAbility.ASSEMBLE.id())
      .text("subtitle.viltrumitecore.captain.assemble")
      .speaker("subtitle.viltrumitecore.speaker.captain")
      .priority(HeroLine.Priority.ULTIMATE)
      .range(CaptainRules.OBSERVER_RANGE)
      .build();

   /** «Я могу так весь день.» — said on a parry (cooldown keeps it rare). */
   public static final HeroLine ALL_DAY = HeroLine.builder("captain:all_day")
      .text("subtitle.viltrumitecore.captain.all_day")
      .speaker("subtitle.viltrumitecore.speaker.captain")
      .priority(HeroLine.Priority.AMBIENT)
      .cooldown(20 * 45)
      .range(24.0)
      .build();

   /** Thor, joint move (rework §15.3): «Эй, кеп, подставь щит! Я заряжу атаку!» — played in Thor's voice. */
   public static final HeroLine TEAM_SHIELD = HeroLine.builder("captain:team_shield")
      .text("subtitle.viltrumitecore.captain.team_shield")
      .speaker("subtitle.viltrumitecore.speaker.thor")
      .priority(HeroLine.Priority.ABILITY)
      .range(CaptainRules.OBSERVER_RANGE)
      .build();

   /** Iron Man, joint move (rework §15.2): «Заряд — четыреста процентов!» — played in Iron Man's voice. */
   public static final HeroLine TEAM_BEAM = HeroLine.builder("captain:team_beam")
      .text("subtitle.viltrumitecore.captain.team_beam")
      .speaker("subtitle.viltrumitecore.speaker.ironman")
      .priority(HeroLine.Priority.ABILITY)
      .range(CaptainRules.OBSERVER_RANGE)
      .build();

   /** «Капитан Роджерс, прибыл для прохождения службы.» — when the player becomes Captain America (no spam: 60 s). */
   public static final HeroLine ENTER = HeroLine.builder("captain:enter")
      .text("subtitle.viltrumitecore.captain.enter")
      .speaker("subtitle.viltrumitecore.speaker.captain")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 60)
      .range(32.0)
      .build();

   /** Contextual interaction reid_shatters_vibranium: «Этот меч... не из нашего мира.» — the guard is broken. */
   public static final HeroLine SHIELD_SHATTERED = HeroLine.builder("captain:shield_shattered")
      .text("subtitle.viltrumitecore.captain.shield_shattered")
      .speaker("subtitle.viltrumitecore.speaker.captain")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 30)
      .range(32.0)
      .build();

   /** Contextual interaction mjolnir_meets_vibranium: «Вот это удар!» — Steve braced under Mjolnir. */
   public static final HeroLine STORM_SHIELD = HeroLine.builder("captain:storm_shield")
      .text("subtitle.viltrumitecore.captain.storm_shield")
      .speaker("subtitle.viltrumitecore.speaker.captain")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 30)
      .range(32.0)
      .build();

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(CaptainAbility.ASSEMBLE.id(), ASSEMBLE)
      .add(ALL_DAY)
      .add(TEAM_SHIELD)
      .add(TEAM_BEAM)
      .add(SHIELD_SHATTERED)
      .add(STORM_SHIELD)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   private CaptainLines() {
   }
}
