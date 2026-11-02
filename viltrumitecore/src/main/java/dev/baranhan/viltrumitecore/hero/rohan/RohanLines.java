package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Rohan's spoken lines: cinematic subtitles only, never voice audio. Text keys {@code subtitle.viltrumitecore.rohan.*},
 * speaker {@code subtitle.viltrumitecore.speaker.rohan}.
 */
public final class RohanLines {
   /** «Кишибэ Рохан. Мангака.» — becoming the hero. */
   public static final HeroLine ENTER = line("rohan:enter", "enter", HeroLine.Priority.ABILITY, 20 * 60);
   /** «Хэвенс Дор!» — the touch. */
   public static final HeroLine DOOR = line(RohanAbility.DOOR.id(), "door", HeroLine.Priority.ABILITY, 20 * 12);
   /** «Смотри внимательно.» — Air Sketch. */
   public static final HeroLine SKETCH = line(RohanAbility.SKETCH.id(), "sketch", HeroLine.Priority.ABILITY, 20 * 25);
   /** «Быстрее, чем твой удар.» — Faster than a Punch. */
   public static final HeroLine COUNTER = line(RohanAbility.COUNTER.id(), "counter", HeroLine.Priority.ABILITY, 20 * 20);
   /** «Пишу на себе.» — Self-Edit. */
   public static final HeroLine SELF_EDIT = line(RohanAbility.SELF_EDIT.id(), "self_edit", HeroLine.Priority.AMBIENT, 20 * 30);
   /** «Читайте мою рукопись!» — the Manuscript. */
   public static final HeroLine MANUSCRIPT = HeroLine.builder(RohanAbility.MANUSCRIPT.id())
      .text("subtitle.viltrumitecore.rohan.manuscript")
      .speaker("subtitle.viltrumitecore.speaker.rohan")
      .priority(HeroLine.Priority.ULTIMATE)
      .range(48.0)
      .build();
   /** «Какая интересная книга...» — the first reading of a new kind of body. */
   public static final HeroLine READ = line("rohan:read", "read", HeroLine.Priority.AMBIENT, 20 * 20);
   /** «Не может атаковать Кишибэ Рохана.» — a Safety Lock is written. */
   public static final HeroLine SAFETY = line("rohan:safety", "safety", HeroLine.Priority.ABILITY, 20 * 15);
   /** «Ни за что!» — a book that will not take a line. */
   public static final HeroLine REFUSED = line("rohan:refused", "refused", HeroLine.Priority.AMBIENT, 20 * 15);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(RohanAbility.DOOR.id(), DOOR)
      .onAbility(RohanAbility.SKETCH.id(), SKETCH)
      .onAbility(RohanAbility.COUNTER.id(), COUNTER)
      .onAbility(RohanAbility.SELF_EDIT.id(), SELF_EDIT)
      .onAbility(RohanAbility.MANUSCRIPT.id(), MANUSCRIPT)
      .add(READ)
      .add(SAFETY)
      .add(REFUSED)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   private RohanLines() {
   }

   private static HeroLine line(String id, String key, HeroLine.Priority priority, int cooldown) {
      return HeroLine.builder(id)
         .text("subtitle.viltrumitecore.rohan." + key)
         .speaker("subtitle.viltrumitecore.speaker.rohan")
         .priority(priority)
         .cooldown(cooldown)
         .range(32.0)
         .build();
   }
}
