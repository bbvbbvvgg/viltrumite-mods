package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Reinhard's spoken lines (cinematic subtitles, docs/plans/2026-10-28-hero-subtitles.md).
 * Text is in the lang files under {@code subtitle.viltrumitecore.reinhard.*};
 * the name is {@code subtitle.viltrumitecore.speaker.reinhard}.
 *
 * <p>A longer scene (e.g. his full monologue) is one line with numbered
 * fragments: add {@code subtitle.viltrumitecore.reinhard.<scene>.1..N} to every
 * lang file, then register it here with
 * {@code HeroLine.builder("reinhard:<scene>").textSeries("subtitle.viltrumitecore.reinhard.<scene>.", N)}
 * and bind it with {@code onEvent}/{@code onAbility}; fire an event with
 * {@code HeroSpeech.trigger(player, HeroLineTrigger.event("reinhard:<scene>"))}.
 * Each fragment becomes its own page and is timed by its length.
 */
public final class ReinhardLines {
   /** The ultimate: World Cut. Spoken when the cut starts; the cast is never cancelled once started, so neither is the line. */
   public static final HeroLine WORLD_CUT = HeroLine.builder(ReinhardAbility.WORLD_CUT.id())
      .text("subtitle.viltrumitecore.reinhard.world_cut")
      .priority(HeroLine.Priority.ULTIMATE)
      .range(48.0)
      .build();

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(ReinhardAbility.WORLD_CUT.id(), WORLD_CUT)
      .build();

   private ReinhardLines() {
   }
}
