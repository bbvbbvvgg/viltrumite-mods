package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
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

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(CaptainAbility.ASSEMBLE.id(), ASSEMBLE)
      .add(ALL_DAY)
      .build();

   private CaptainLines() {
   }
}
