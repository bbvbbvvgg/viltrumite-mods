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
   /** Players following the ult: the cut is seen up to 320 blocks along a 200-block line. */
   static final double ULT_RANGE = 160.0;
   /** Event fired when a World Cut / Diagonal World Cut blasts (WorldCut.Environment.blast). */
   public static final String BLAST_EVENT = "reinhard:world_cut_blast";

   /**
    * The ultimate: World Cut. Two pages over the swing and the silence, timed to end on the blast
    * (before: one three-row page with an automatic, longer duration that ran over the blast line).
    * The cast is never cancelled once started, so neither is the line.
    */
   public static final HeroLine WORLD_CUT = HeroLine.builder(ReinhardAbility.WORLD_CUT.id())
      .textSeries("subtitle.viltrumitecore.reinhard.world_cut.", 2)
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(ReinhardRules.WORLD_CUT_BLAST)
      .range(ULT_RANGE)
      .build();

   /** Diagonal World Cut: same timeline (swing + silence, then the blast). */
   public static final HeroLine DIAGONAL_WORLD_CUT = HeroLine.builder(ReinhardAbility.DIAGONAL_WORLD_CUT.id())
      .textSeries("subtitle.viltrumitecore.reinhard.diagonal_cut.", 2)
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(ReinhardRules.WORLD_CUT_BLAST)
      .range(ULT_RANGE)
      .build();

   /** The blast itself: one short page while the wave runs (equal priority: it waits for the first line, never overlaps). */
   public static final HeroLine BLAST = HeroLine.builder(BLAST_EVENT)
      .text("subtitle.viltrumitecore.reinhard.world_cut.blast")
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(ReinhardRules.WORLD_CUT_WAVE_TICKS + 20)
      .cooldown(ReinhardRules.WORLD_CUT_WAVE_TICKS)
      .range(ULT_RANGE)
      .build();

   /** Pages of the World Erasure line (subtitle.viltrumitecore.reinhard.world_erasure.1..N). */
   public static final int WORLD_ERASURE_PAGES = 3;
   /**
    * Whole World Erasure line, ticks: spoken over the speech pre-roll and the crack/shatter, the
    * last page (plus its fade) gone before the dark phase (playtest: "before the black screen").
    */
   public static final int WORLD_ERASURE_TICKS = 90;

   /** World Erasure (slot 6, N): spoken while the world is paused, before the crack turns dark. */
   public static final HeroLine WORLD_ERASURE = HeroLine.builder(ReinhardAbility.WORLD_ERASURE.id())
      .textSeries("subtitle.viltrumitecore.reinhard.world_erasure.", WORLD_ERASURE_PAGES)
      .priority(HeroLine.Priority.ULTIMATE)
      .duration(WORLD_ERASURE_TICKS)
      .range(ULT_RANGE)
      .activeWhile(player -> {
         ReinhardState state = ReinhardState.of(player);
         return state != null && state.worldErasure != null && !state.worldErasure.ended();
      })
      .build();

   public static final String HOLY_SWORD_EVENT = "reinhard:holy_sword";
   public static final String PHOENIX_EVENT = "reinhard:phoenix";

   /*
    * Global subtitles expansion: short lines in his chivalrous manner, own phrasing. ReinhardActions.press
    * already speaks the line of every accepted ability; these are bound to the abilities that matter
    * (not punches, dashes or kicks) with long cooldowns. World Erasure (slot 6) keeps its line untouched.
    */
   public static final HeroLine REID = ability(ReinhardAbility.REID.id(), "reid", 20 * 60);
   public static final HeroLine SHEATH_STRIKE = ability(ReinhardAbility.SHEATH_STRIKE.id(), "sheath_strike", 20 * 30);
   public static final HeroLine SAW_SPHERE = ability(ReinhardAbility.SAW_SPHERE.id(), "saw_sphere", 20 * 30);
   public static final HeroLine REFLECT = ability(ReinhardAbility.REFLECT.id(), "reflect", 20 * 30);
   public static final HeroLine CATCH = ability(ReinhardAbility.CATCH.id(), "catch", 20 * 30);
   /** The mode cycle reached Holy Sword (ReinhardHero mode change). */
   public static final HeroLine HOLY_SWORD = ability(HOLY_SWORD_EVENT, "holy_sword", 20 * 60);
   /** The Phoenix blessing saved him from a lethal divine hit (its own long cooldown keeps it rare). */
   public static final HeroLine PHOENIX = HeroLine.builder(PHOENIX_EVENT).text("subtitle.viltrumitecore.reinhard.phoenix")
      .priority(HeroLine.Priority.ULTIMATE).duration(60).cooldown(0).range(48.0).build();
   public static final HeroLine KILL = ambient("reinhard:kill", "kill", 20 * 60);
   public static final HeroLine LOW_HEALTH = ambient("reinhard:low_health", "low_health", 20 * 90);
   public static final HeroLine ENTER = ability("reinhard:enter", "enter", 20 * 60);

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(ReinhardAbility.WORLD_ERASURE.id(), WORLD_ERASURE)
      .onAbility(ReinhardAbility.WORLD_CUT.id(), WORLD_CUT)
      .onAbility(ReinhardAbility.DIAGONAL_WORLD_CUT.id(), DIAGONAL_WORLD_CUT)
      .onEvent(BLAST_EVENT, BLAST)
      .onAbility(ReinhardAbility.REID.id(), REID)
      .onAbility(ReinhardAbility.SHEATH_STRIKE.id(), SHEATH_STRIKE)
      .onAbility(ReinhardAbility.SAW_SPHERE.id(), SAW_SPHERE)
      .onAbility(ReinhardAbility.REFLECT.id(), REFLECT)
      .onAbility(ReinhardAbility.CATCH.id(), CATCH)
      .onEvent(HOLY_SWORD_EVENT, HOLY_SWORD)
      .onEvent(PHOENIX_EVENT, PHOENIX)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.KILL, KILL)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.LOW_HEALTH, LOW_HEALTH)
      .onEvent(dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.HERO_ENTER, ENTER)
      .build();

   /** A Reinhard event line; no-op for any other hero. */
   public static void say(net.minecraft.server.level.ServerPlayer player, String event) {
      dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.trigger(player, dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger.event(event));
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      return HeroLine.builder(id).text("subtitle.viltrumitecore.reinhard." + key).priority(HeroLine.Priority.ABILITY).cooldown(cooldown).range(32.0).build();
   }

   private static HeroLine ambient(String id, String key, int cooldown) {
      return HeroLine.builder(id).text("subtitle.viltrumitecore.reinhard." + key).priority(HeroLine.Priority.AMBIENT).cooldown(cooldown).range(24.0).build();
   }

   private ReinhardLines() {
   }

   /**
    * The cut blasted: its caster speaks the blast line if still online, alive, in that level and
    * still Reinhard (the cast is detached and may outlive all of that).
    */
   public static void blast(net.minecraft.server.level.ServerLevel level, java.util.UUID caster) {
      net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(caster);
      if (player != null && player.isAlive() && player.serverLevel() == level) {
         dev.baranhan.viltrumitecore.hero.speech.HeroSpeech.trigger(player, dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger.event(BLAST_EVENT));
      }
   }
}
