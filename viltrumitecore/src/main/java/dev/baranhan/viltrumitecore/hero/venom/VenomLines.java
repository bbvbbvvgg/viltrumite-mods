package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Venom's spoken lines: cinematic subtitles only, never voice audio. Text keys {@code subtitle.viltrumitecore.venom.*},
 * speaker {@code subtitle.viltrumitecore.speaker.venom}. The inner voices of a takeover go to the host alone
 * ({@link #whisper}), speaker {@code subtitle.viltrumitecore.speaker.venom_inner}.
 */
public final class VenomLines {
   public static final int WHISPERS = 8;
   /** «Мы — Веном.» — becoming the hero. */
   public static final HeroLine ENTER = line("venom:enter", "enter", HeroLine.Priority.ABILITY, 20 * 60);
   /** «Мы голодны.» — the symbiote covers the host. */
   public static final HeroLine TRANSFORM = line(VenomAbility.SYMBIOSIS.id(), "transform", HeroLine.Priority.ABILITY, 20 * 20);
   /** «Сюда!» — a tendril yank. */
   public static final HeroLine PULL = line("venom:pull", "pull", HeroLine.Priority.AMBIENT, 20 * 25);
   /** «Голод!» — the frenzy. */
   public static final HeroLine RAGE = line(VenomAbility.RAGE.id(), "rage", HeroLine.Priority.ABILITY, 20 * 20);
   /** «Теперь ты — мы.» — a takeover. */
   public static final HeroLine TAKEOVER = line(VenomAbility.TAKEOVER.id(), "takeover", HeroLine.Priority.ULTIMATE, 0);
   /** «Паучок...» — a Spider is near. */
   public static final HeroLine SPIDER = line("venom:spider", "spider", HeroLine.Priority.AMBIENT, 20 * 90);
   /** «Огонь! Больно!» — recoil. */
   public static final HeroLine RECOIL = line("venom:recoil", "recoil", HeroLine.Priority.AMBIENT, 20 * 30);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(VenomAbility.SYMBIOSIS.id(), TRANSFORM)
      .onAbility(VenomAbility.RAGE.id(), RAGE)
      .add(TAKEOVER)
      .add(PULL)
      .add(SPIDER)
      .add(RECOIL)
      .onEvent(HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   private VenomLines() {
   }

   private static HeroLine line(String id, String key, HeroLine.Priority priority, int cooldown) {
      return HeroLine.builder(id)
         .text("subtitle.viltrumitecore.venom." + key)
         .speaker("subtitle.viltrumitecore.speaker.venom")
         .priority(priority)
         .cooldown(cooldown)
         .range(32.0)
         .build();
   }

   /** One inner voice, for the host's ears only (never broadcast). */
   public static void whisper(ServerPlayer host, RandomSource random) {
      String key = "subtitle.viltrumitecore.venom.whisper_" + random.nextInt(WHISPERS);
      HeroSpeech.announce(List.of(host), null, "venom", "venom:whisper", "subtitle.viltrumitecore.speaker.venom_inner", List.of(key),
         HeroLine.Priority.ULTIMATE);
   }
}
