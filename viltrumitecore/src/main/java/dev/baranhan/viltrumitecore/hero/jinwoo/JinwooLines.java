package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerPlayer;

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

   /** Whole ultimate line: «…восстань!» starts about when the army rises (ULT_RISE_START 20 .. wave 60), at any subtitle scale. */
   public static final int ARMY_TICKS = 70;

   /**
    * «Армия теней… восстань!» — the ultimate; interrupted when the cast breaks before the rise. Fixed
    * duration (before: auto-timed, so with a slower reading scale "…arise!" came after the wave).
    */
   public static final HeroLine ARMY = HeroLine.builder(JinwooAbility.MONARCH.id())
      .text("subtitle.viltrumitecore.jinwoo.arise.1", "subtitle.viltrumitecore.jinwoo.arise.2")
      .speaker("subtitle.viltrumitecore.speaker.jinwoo")
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(ARMY_TICKS)
      .range(JinwooRules.ULT_OBSERVER_RANGE + 8.0)
      .build();

   public static final String EVENT_EXTRACT_FAIL = "jinwoo:extract_fail";
   public static final String EVENT_RIPOSTE = "jinwoo:riposte";

   public static final HeroLine MUTILATION = ability(JinwooAbility.MUTILATION.id(), "mutilation", 20 * 25);
   public static final HeroLine AUTHORITY = ability(JinwooAbility.AUTHORITY.id(), "authority", 20 * 25);
   public static final HeroLine EXCHANGE = ability(JinwooAbility.EXCHANGE.id(), "exchange", 20 * 20);
   public static final HeroLine CHARGE = ability(JinwooAbility.CHARGE.id(), "charge", 20 * 25);
   /** Only a real summon (an empty field), never the recall of an army already out. */
   public static final HeroLine SUMMON = ability(JinwooAbility.SUMMON.id(), "summon", 20 * 30);
   public static final HeroLine ATTACK = ability(JinwooAbility.ATTACK.id(), "army_attack", 20 * 20);
   public static final HeroLine GUARD = ability(JinwooAbility.GUARD.id(), "army_guard", 20 * 20);
   public static final HeroLine SENTINEL = ability(JinwooAbility.SENTINEL.id(), "army_sentinel", 20 * 20);
   public static final HeroLine EXTRACT_FAIL = ability(EVENT_EXTRACT_FAIL, "extract_fail", 20 * 20);
   public static final HeroLine RIPOSTE = ability(EVENT_RIPOSTE, "riposte", 20 * 30);
   public static final HeroLine KILL_A = ambient("jinwoo:kill_a", "kill_a", 20 * 60);
   public static final HeroLine KILL_B = ambient("jinwoo:kill_b", "kill_b", 20 * 60);
   public static final HeroLine LOW_HEALTH = ambient("jinwoo:low_health", "low_health", 20 * 90);
   public static final HeroLine ENTER = ability("jinwoo:enter", "enter", 20 * 60);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(JinwooAbility.EXTRACTION.id(), ARISE_ONE)
      .onAbility(JinwooAbility.MONARCH.id(), ARMY)
      .onAbility(JinwooAbility.MUTILATION.id(), MUTILATION)
      .onAbility(JinwooAbility.AUTHORITY.id(), AUTHORITY)
      .onAbility(JinwooAbility.EXCHANGE.id(), EXCHANGE)
      .onAbility(JinwooAbility.CHARGE.id(), CHARGE)
      .onAbility(JinwooAbility.SUMMON.id(), SUMMON)
      .onAbility(JinwooAbility.ATTACK.id(), ATTACK)
      .onAbility(JinwooAbility.GUARD.id(), GUARD)
      .onAbility(JinwooAbility.SENTINEL.id(), SENTINEL)
      .onEvent(EVENT_EXTRACT_FAIL, EXTRACT_FAIL)
      .onEvent(EVENT_RIPOSTE, RIPOSTE)
      .onEvent(HeroLineEvents.KILL, KILL_A)
      .onEvent(HeroLineEvents.KILL, KILL_B)
      .onEvent(HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   /** A Jinwoo event line; no-op for any other hero. */
   public static void say(ServerPlayer player, String event) {
      HeroSpeech.trigger(player, HeroLineTrigger.event(event));
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      return HeroLine.builder(id).text("subtitle.viltrumitecore.jinwoo." + key).speaker("subtitle.viltrumitecore.speaker.jinwoo")
         .priority(HeroLine.Priority.ABILITY).cooldown(cooldown).range(32.0).build();
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text("subtitle.viltrumitecore.jinwoo." + key).speaker("subtitle.viltrumitecore.speaker.jinwoo")
         .priority(HeroLine.Priority.AMBIENT).cooldown(cooldown).range(24.0).build();
   }

   private JinwooLines() {
   }
}
