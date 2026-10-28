package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Jinwoo's spoken lines (subtitles + 3D portrait, spec §5.7, §5.9). Text lives under
 * {@code subtitle.viltrumitecore.jinwoo.*}; speaker {@code subtitle.viltrumitecore.speaker.jinwoo}.
 */
public final class JinwooLines {
   /** «Встань.» — every extraction channel. */
   public static final HeroLine ARISE_ONE = HeroLine.builder(JinwooAbility.EXTRACTION.id())
      .text("subtitle.viltrumitecore.jinwoo.extraction")
      .speaker("subtitle.viltrumitecore.speaker.jinwoo")
      .priority(HeroLine.Priority.ABILITY)
      .range(32.0)
      .build();

   /** «Армия теней… восстань!» — the ultimate; interrupted when the cast breaks before the rise. */
   public static final HeroLine ARMY = HeroLine.builder(JinwooAbility.MONARCH.id())
      .text("subtitle.viltrumitecore.jinwoo.arise.1", "subtitle.viltrumitecore.jinwoo.arise.2")
      .speaker("subtitle.viltrumitecore.speaker.jinwoo")
      .priority(HeroLine.Priority.ULTIMATE)
      .range(JinwooRules.ULT_OBSERVER_RANGE + 8.0)
      .build();

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(JinwooAbility.EXTRACTION.id(), ARISE_ONE)
      .onAbility(JinwooAbility.MONARCH.id(), ARMY)
      .build();

   private JinwooLines() {
   }
}
