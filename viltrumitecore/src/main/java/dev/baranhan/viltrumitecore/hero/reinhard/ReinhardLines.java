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

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(ReinhardAbility.WORLD_ERASURE.id(), WORLD_ERASURE)
      .onAbility(ReinhardAbility.WORLD_CUT.id(), WORLD_CUT)
      .onAbility(ReinhardAbility.DIAGONAL_WORLD_CUT.id(), DIAGONAL_WORLD_CUT)
      .onEvent(BLAST_EVENT, BLAST)
      .build();

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
