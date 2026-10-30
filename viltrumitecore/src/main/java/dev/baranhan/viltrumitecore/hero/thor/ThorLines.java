package dev.baranhan.viltrumitecore.hero.thor;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerPlayer;

/**
 * Thor's spoken lines (cinematic subtitles with his portrait; v3 spec §1.3).
 * Text keys {@code subtitle.viltrumitecore.thor.*}, speaker
 * {@code subtitle.viltrumitecore.speaker.thor}. Each line starts once when the
 * server accepts the action; the frequent ones (throw, crush, thunder) have a
 * long cooldown so they stay a flourish, not chatter.
 */
public final class ThorLines {
   /** Mjölnir left the hand (the release of MMB, not the charge). */
   public static final String THROW_EVENT = "thor:throw";

   /** The ultimate: three pages (call to the sky, the god of thunder, look up). */
   public static final HeroLine WRATH = HeroLine.builder(ThorAbilities.WRATH)
      .textSeries("subtitle.viltrumitecore.thor.wrath.", 3)
      .priority(HeroLine.Priority.ULTIMATE)
      .range(64.0)
      .build();
   /** The teleport went through: "Bifröst!" */
   public static final HeroLine BIFROST = HeroLine.builder(ThorAbilities.TELEPORT)
      .text("subtitle.viltrumitecore.thor.bifrost")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 10)
      .range(48.0)
      .build();
   public static final HeroLine THROW = HeroLine.builder(THROW_EVENT)
      .text("subtitle.viltrumitecore.thor.throw")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 30)
      .range(32.0)
      .build();
   public static final HeroLine CRUSH = HeroLine.builder(ThorAbilities.CRUSH)
      .text("subtitle.viltrumitecore.thor.crush")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 20)
      .range(32.0)
      .build();
   /** Electric mode switched on (not off). */
   public static final HeroLine THUNDER = HeroLine.builder(ThorAbilities.THUNDER)
      .text("subtitle.viltrumitecore.thor.thunder")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 30)
      .range(32.0)
      .build();

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(ThorAbilities.WRATH, WRATH)
      .onAbility(ThorAbilities.TELEPORT, BIFROST)
      .onAbility(ThorAbilities.CRUSH, CRUSH)
      .onAbility(ThorAbilities.THUNDER, THUNDER)
      .onEvent(THROW_EVENT, THROW)
      .build();

   private ThorLines() {
   }

   /** Mjölnir was thrown. */
   public static void thrown(ServerPlayer thor) {
      HeroSpeech.trigger(thor, HeroLineTrigger.event(THROW_EVENT));
   }
}
