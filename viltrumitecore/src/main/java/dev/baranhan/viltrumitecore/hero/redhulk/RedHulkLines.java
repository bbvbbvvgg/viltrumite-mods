package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * The Red Hulk's spoken lines: cinematic subtitles only, never voice audio (no TTS, no recordings). Text keys
 * {@code subtitle.viltrumitecore.red_hulk.*}, speaker {@code subtitle.viltrumitecore.speaker.red_hulk}.
 */
public final class RedHulkLines {
   /** «Красный — цвет ярости.» — when the player becomes the Red Hulk (60 s cooldown). */
   public static final HeroLine ENTER = line("red_hulk:enter", "enter", HeroLine.Priority.ABILITY, 20 * 60);
   /** «Чем жарче — тем сильнее!» — Thermal Fury. */
   public static final HeroLine FURY = HeroLine.builder(RedHulkAbility.FURY.id())
      .text("subtitle.viltrumitecore.red_hulk.fury")
      .speaker("subtitle.viltrumitecore.speaker.red_hulk")
      .priority(HeroLine.Priority.ABILITY)
      .cooldown(20 * 20)
      .range(32.0)
      .build();
   /** «Всё вокруг сгорит!» — Critical Overheat. */
   public static final HeroLine OVERHEAT_ULT = HeroLine.builder(RedHulkAbility.OVERHEAT.id())
      .text("subtitle.viltrumitecore.red_hulk.critical")
      .speaker("subtitle.viltrumitecore.speaker.red_hulk")
      .priority(HeroLine.Priority.ULTIMATE)
      .range(RedHulkRules.OBSERVER_RANGE)
      .build();
   /** «Слишком... горячо...» — the body overheats and vents (30 s cooldown). */
   public static final HeroLine OVERHEATED = line("red_hulk:overheated", "overheated", HeroLine.Priority.AMBIENT, 20 * 30);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(RedHulkAbility.FURY.id(), FURY)
      .onAbility(RedHulkAbility.OVERHEAT.id(), OVERHEAT_ULT)
      .add(OVERHEATED)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   private RedHulkLines() {
   }

   private static HeroLine line(String id, String key, HeroLine.Priority priority, int cooldown) {
      return HeroLine.builder(id)
         .text("subtitle.viltrumitecore.red_hulk." + key)
         .speaker("subtitle.viltrumitecore.speaker.red_hulk")
         .priority(priority)
         .cooldown(cooldown)
         .range(32.0)
         .build();
   }
}
