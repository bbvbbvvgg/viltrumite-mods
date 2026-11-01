package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * The Ghost Rider's spoken lines: cinematic subtitles only, never voice audio. Text keys
 * {@code subtitle.viltrumitecore.ghost_rider.*}, speaker {@code subtitle.viltrumitecore.speaker.ghost_rider}.
 */
public final class GhostRiderLines {
   /** «Сделка есть сделка.» — becoming the hero (60 s cooldown). */
   public static final HeroLine ENTER = line("ghost_rider:enter", "enter", HeroLine.Priority.ABILITY, 20 * 60);
   /** «Пора платить по счетам.» — the transformation. */
   public static final HeroLine TRANSFORM = line(GhostRiderAbility.TRANSFORM.id(), "transform", HeroLine.Priority.ABILITY, 20 * 20);
   /** «Дорога в ад открыта.» — the Hell Cycle forms. */
   public static final HeroLine CYCLE = line(GhostRiderAbility.CYCLE.id(), "cycle", HeroLine.Priority.ABILITY, 20 * 45);
   /** «Прочь с дороги!» — Hell Dash. */
   public static final HeroLine DASH = line(GhostRiderAbility.DASH.id(), "dash", HeroLine.Priority.AMBIENT, 20 * 30);
   /** «Цепи ада!» — Chain Whirl. */
   public static final HeroLine WHIRL = line(GhostRiderAbility.WHIRL.id(), "whirl", HeroLine.Priority.ABILITY, 20 * 25);
   /** «Гори!» — Hellfire Wave. */
   public static final HeroLine WAVE = line(GhostRiderAbility.WAVE.id(), "wave", HeroLine.Priority.ABILITY, 20 * 25);
   /** «Посмотри мне в глаза.» — Penance Stare. */
   public static final HeroLine STARE = HeroLine.builder(GhostRiderAbility.STARE.id())
      .text("subtitle.viltrumitecore.ghost_rider.stare")
      .speaker("subtitle.viltrumitecore.speaker.ghost_rider")
      .priority(HeroLine.Priority.ULTIMATE)
      .range(48.0)
      .build();
   /** «Почувствуй их боль.» — a guilty verdict. */
   public static final HeroLine GUILTY = line("ghost_rider:guilty", "guilty", HeroLine.Priority.ULTIMATE, 0);
   /** «Ты невиновен.» — no sin found. */
   public static final HeroLine INNOCENT = line("ghost_rider:innocent", "innocent", HeroLine.Priority.ABILITY, 0);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(GhostRiderAbility.TRANSFORM.id(), TRANSFORM)
      .onAbility(GhostRiderAbility.CYCLE.id(), CYCLE)
      .onAbility(GhostRiderAbility.DASH.id(), DASH)
      .onAbility(GhostRiderAbility.WHIRL.id(), WHIRL)
      .onAbility(GhostRiderAbility.WAVE.id(), WAVE)
      .onAbility(GhostRiderAbility.STARE.id(), STARE)
      .add(GUILTY)
      .add(INNOCENT)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   private GhostRiderLines() {
   }

   private static HeroLine line(String id, String key, HeroLine.Priority priority, int cooldown) {
      return HeroLine.builder(id)
         .text("subtitle.viltrumitecore.ghost_rider." + key)
         .speaker("subtitle.viltrumitecore.speaker.ghost_rider")
         .priority(priority)
         .cooldown(cooldown)
         .range(32.0)
         .build();
   }
}
