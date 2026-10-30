package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerPlayer;

/**
 * Iron Man's spoken lines, every Mark and the Hulkbuster (global subtitles expansion; before: none,
 * and the Hulkbuster's "Sleep!" sat on the action bar). Text-only subtitles: the film voice is not
 * licensed and JARVIS recordings stay muted, so nothing here plays audio. Text keys
 * {@code subtitle.viltrumitecore.ironman.*}; the Hulkbuster finisher reuses its existing key
 * {@code hud.viltrumitecore.ironman.hulkbuster.line_sleep}.
 */
public final class IronManLines {
   public static final String PREFIX = "subtitle.viltrumitecore.ironman.";
   public static final String SPEAKER = "subtitle.viltrumitecore.speaker.ironman";
   public static final String EVENT_UNIBEAM = "ironman:unibeam_charge";
   public static final String EVENT_MISSILES = "ironman:missiles_fired";
   public static final String EVENT_CORE = "ironman:core_explosion";
   public static final String EVENT_VERONICA = "ironman:veronica_drop";
   public static final String EVENT_SUIT_UP = "ironman:suit_up";
   public static final String EVENT_HULK_SLEEP = "ironman:hulk_sleep";
   public static final String EVENT_HULK_RAM = "ironman:hulk_ram";

   public static final HeroLine UNIBEAM = ability(EVENT_UNIBEAM, "unibeam", 20 * 30);
   public static final HeroLine MISSILES = ability(EVENT_MISSILES, "missiles", 20 * 30);
   /** Rare: the overdraft blew the core (the suit is gone for 30 s). */
   public static final HeroLine CORE = HeroLine.builder(EVENT_CORE).text(PREFIX + "core").speaker(SPEAKER).priority(HeroLine.Priority.ULTIMATE)
      .cooldown(20 * 10).range(32.0).build();
   public static final HeroLine VERONICA = ability(EVENT_VERONICA, "veronica", 20 * 60);
   public static final HeroLine SUIT_UP = ability(EVENT_SUIT_UP, "suit_up", 20 * 90);
   /** Hulkbuster grab finisher: the spec's "Sleep!" (§6.2.4), now a subtitle for the owner and everyone around. */
   public static final HeroLine HULK_SLEEP = HeroLine.builder(EVENT_HULK_SLEEP).text("hud.viltrumitecore.ironman.hulkbuster.line_sleep").speaker(SPEAKER)
      .priority(HeroLine.Priority.ABILITY).duration(40).cooldown(20 * 15).range(32.0).build();
   /** Hulkbuster ultimate (jet ram) ignition. */
   public static final HeroLine HULK_RAM = HeroLine.builder(EVENT_HULK_RAM).text(PREFIX + "hulk_ram").speaker(SPEAKER).priority(HeroLine.Priority.ULTIMATE)
      .duration(50).cooldown(0).range(48.0).build();
   public static final HeroLine KILL_A = ambient("ironman:kill_a", "kill_a", 20 * 60);
   public static final HeroLine KILL_B = ambient("ironman:kill_b", "kill_b", 20 * 60);
   public static final HeroLine LOW_HEALTH = ambient("ironman:low_health", "low_health", 20 * 90);
   public static final HeroLine ENTER = ability("ironman:enter", "enter", 20 * 60);

   public static final HeroLines LINES = HeroLines.builder()
      .onEvent(EVENT_UNIBEAM, UNIBEAM)
      .onEvent(EVENT_MISSILES, MISSILES)
      .onEvent(EVENT_CORE, CORE)
      .onEvent(EVENT_VERONICA, VERONICA)
      .onEvent(EVENT_SUIT_UP, SUIT_UP)
      .onEvent(EVENT_HULK_SLEEP, HULK_SLEEP)
      .onEvent(EVENT_HULK_RAM, HULK_RAM)
      .onEvent(HeroLineEvents.KILL, KILL_A)
      .onEvent(HeroLineEvents.KILL, KILL_B)
      .onEvent(HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   /** Every subtitle key of this table under {@link #PREFIX} (lang tests). */
   public static final String[] KEYS = {"unibeam", "missiles", "core", "veronica", "suit_up", "hulk_ram", "kill_a", "kill_b", "low_health", "enter"};

   private IronManLines() {
   }

   public static void say(ServerPlayer player, String event) {
      HeroSpeech.trigger(player, HeroLineTrigger.event(event));
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(PREFIX + key).speaker(SPEAKER).priority(HeroLine.Priority.ABILITY).cooldown(cooldown).range(32.0).build();
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text(PREFIX + key).speaker(SPEAKER).priority(HeroLine.Priority.AMBIENT).cooldown(cooldown).range(24.0).build();
   }
}
