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

   public static final String WRATH_STRIKE_EVENT = "thor:wrath_strike";
   public static final String FATALITY_EVENT = "thor:fatality";
   /** Wrath windup (ThorAttacks: 40 intro ticks before the strike at ENTER_MAIN). */
   public static final int WRATH_WINDUP = 40;

   /**
    * The ultimate, split on its timeline: the call to the sky over the 40-tick windup, "Then look up!"
    * on the strike (event {@link #WRATH_STRIKE_EVENT} from WrathMove ENTER_MAIN). Before: three
    * auto-timed pages from the press, so "look up" came about three seconds after the lightning.
    * A windup cancelled before the strike stops the call (WrathMove.onStop).
    */
   public static final HeroLine WRATH = HeroLine.builder(ThorAbilities.WRATH)
      .text("subtitle.viltrumitecore.thor.wrath.1")
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(WRATH_WINDUP)
      .range(64.0)
      .build();
   public static final HeroLine WRATH_STRIKE = HeroLine.builder(WRATH_STRIKE_EVENT)
      .text("subtitle.viltrumitecore.thor.wrath.3")
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(40)
      .cooldown(0)
      .range(64.0)
      .build();
   /** The paired finisher scene began (FatalitySessions.start). */
   public static final HeroLine FATALITY = HeroLine.builder(FATALITY_EVENT)
      .text("subtitle.viltrumitecore.thor.fatality")
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(50)
      .cooldown(20 * 10)
      .range(32.0)
      .build();
   /** "You wanted to see the god of thunder?" — the old middle page of Wrath, now said over a fallen foe. */
   public static final HeroLine KILL_A = ambient("thor:kill_a", "subtitle.viltrumitecore.thor.wrath.2", 20 * 60);
   public static final HeroLine KILL_B = ambient("thor:kill_b", "subtitle.viltrumitecore.thor.kill", 20 * 60);
   public static final HeroLine LOW_HEALTH = ambient("thor:low_health", "subtitle.viltrumitecore.thor.low_health", 20 * 90);
   public static final HeroLine ENTER = HeroLine.builder("thor:enter").text("subtitle.viltrumitecore.thor.enter").priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 60).range(32.0).build();
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
      .onEvent(WRATH_STRIKE_EVENT, WRATH_STRIKE)
      .onEvent(FATALITY_EVENT, FATALITY)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.KILL, KILL_A)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.KILL, KILL_B)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   private ThorLines() {
   }

   /** A Thor event line (strike, fatality); no-op for any other hero. */
   public static void say(ServerPlayer thor, String event) {
      HeroSpeech.trigger(thor, HeroLineTrigger.event(event));
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(key).priority(HeroLine.Priority.AMBIENT).cooldown(cooldown).range(24.0).build();
   }

   /** Mjölnir was thrown. */
   public static void thrown(ServerPlayer thor) {
      HeroSpeech.trigger(thor, HeroLineTrigger.event(THROW_EVENT));
   }
}
