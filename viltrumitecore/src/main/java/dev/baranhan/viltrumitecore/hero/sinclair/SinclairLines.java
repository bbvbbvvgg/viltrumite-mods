package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sinclair's spoken lines (PR #65 subtitles + 3D portrait in the top hat). Text lives in the lang files under
 * {@code subtitle.viltrumitecore.sinclair.*}; the name is {@code subtitle.viltrumitecore.speaker.sinclair}.
 *
 * <p>Readability (playtest 2026-10-30: «the card hides too fast»): every line has a fixed on-screen time
 * ({@link #ULT_TICKS} / {@link #ABILITY_TICKS}), and one Sinclair line never cuts another one short: an ability
 * line is skipped while a line is still on screen, the bow and «the audience is bored» wait for it
 * ({@link #say}, {@link #tick}). Only the capture and the act line itself start at once (the ultimate).
 */
public final class SinclairLines {
   public static final String PREFIX = "subtitle.viltrumitecore.sinclair.";
   /** On-screen time of an ultimate line (capture, act, bow), ticks. */
   public static final int ULT_TICKS = 80;
   /** On-screen time of an ability line, ticks. */
   public static final int ABILITY_TICKS = 70;
   /** How long a waiting line may wait for the current one to finish before it is dropped, ticks. */
   public static final int WAIT_TICKS = 100;
   /** The capture: "a volunteer from the audience!" (two variants). */
   public static final HeroLine CAPTURE_A = ult("sinclair:capture_a", "capture_a");
   public static final HeroLine CAPTURE_B = ult("sinclair:capture_b", "capture_b");
   public static final HeroLine ACT_BEAM = ult("sinclair:act_beam", "act_beam");
   public static final HeroLine ACT_NOVA = ult("sinclair:act_nova", "act_nova");
   public static final HeroLine ACT_CUT = ult("sinclair:act_cut", "act_cut");
   public static final HeroLine ACT_BARRAGE = ult("sinclair:act_barrage", "act_barrage");
   public static final HeroLine ACT_AEGIS = ult("sinclair:act_aegis", "act_aegis");
   public static final HeroLine ACT_FINALE = ult("sinclair:act_finale", "act_finale");
   public static final HeroLine BOW = ult("sinclair:bow", "bow");
   public static final HeroLine WINDOW_LOST = ability("sinclair:window_lost", "window_lost", 0);
   public static final HeroLine SUMMON = ability("sinclair:summon", "summon", 400);
   public static final HeroLine HEX = ability("sinclair:hex", "hex", 300);
   public static final HeroLine SNAP = ability("sinclair:snap", "snap", 300);
   public static final HeroLine TADA = ability("sinclair:tada", "tada", 200);

   public static final String EVENT_BOW = "sinclair:bow";
   public static final String EVENT_WINDOW_LOST = "sinclair:window_lost";
   public static final String EVENT_TADA = "sinclair:tada";

   public static final HeroLines LINES = HeroLines.builder()
      .onAbility(SinclairAbilities.AUDIENCE_PARTICIPATION, CAPTURE_A)
      .onAbility(SinclairAbilities.AUDIENCE_PARTICIPATION, CAPTURE_B)
      .onEvent(event(ActShape.BEAM), ACT_BEAM)
      .onEvent(event(ActShape.NOVA), ACT_NOVA)
      .onEvent(event(ActShape.CUT), ACT_CUT)
      .onEvent(event(ActShape.BARRAGE), ACT_BARRAGE)
      .onEvent(event(ActShape.AEGIS), ACT_AEGIS)
      .onEvent(event(ActShape.FINALE), ACT_FINALE)
      .onEvent(EVENT_BOW, BOW)
      .onEvent(EVENT_WINDOW_LOST, WINDOW_LOST)
      .onEvent(EVENT_TADA, TADA)
      .onAbility(SinclairAbilities.SPECTRAL_ASSISTANT, SUMMON)
      .onAbility(SinclairAbilities.RABBIT_HEX, HEX)
      .onAbility(SinclairAbilities.MISDIRECTION, SNAP)
      .build();

   /** Every line id with its lang key (lang tests and the checker). */
   public static final String[] KEYS = {"capture_a", "capture_b", "act_beam", "act_nova", "act_cut", "act_barrage", "act_aegis", "act_finale",
      "bow", "window_lost", "summon", "hex", "snap", "tada"};

   private static final Map<UUID, Pending> PENDING = new HashMap<>();

   private record Pending(HeroLineTrigger trigger, long until) {
   }

   private SinclairLines() {
   }

   /** True while one of Sinclair's lines is still on screen for this speaker (server estimate). */
   public static boolean busy(ServerPlayer player) {
      for (HeroLine line : LINES.all()) {
         if (HeroSpeech.speaking(player, line.id())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Speaks the line of {@code event} without cutting the current one: at once when nothing is on screen,
    * else it waits ({@code wait}, one slot, up to {@link #WAIT_TICKS}) or is skipped.
    */
   public static void say(ServerPlayer player, String event, boolean wait) {
      HeroLineTrigger trigger = HeroLineTrigger.event(event);
      if (!busy(player)) {
         PENDING.remove(player.getUUID());
         HeroSpeech.trigger(player, trigger);
      } else if (wait) {
         PENDING.put(player.getUUID(), new Pending(trigger, player.server.getTickCount() + WAIT_TICKS));
      }
   }

   /** Ability line (summon, hex, snap): skipped while another line is still being read. */
   public static void ability(ServerPlayer player, String abilityId) {
      if (!busy(player)) {
         HeroSpeech.ability(player, abilityId);
      }
   }

   /** Server tick of a Sinclair: a waiting line starts once the current one has finished. */
   public static void tick(ServerPlayer player) {
      Pending pending = PENDING.get(player.getUUID());
      if (pending == null) {
         return;
      }

      if (player.server.getTickCount() > pending.until()) {
         PENDING.remove(player.getUUID());
      } else if (!busy(player)) {
         PENDING.remove(player.getUUID());
         HeroSpeech.trigger(player, pending.trigger());
      }
   }

   public static void clear(ServerPlayer player) {
      PENDING.remove(player.getUUID());
   }

   public static String event(ActShape shape) {
      return "sinclair:act_" + shape.key();
   }

   private static HeroLine ult(String id, String key) {
      return HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ULTIMATE).range(48.0).duration(ULT_TICKS).build();
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      HeroLine.Builder builder = HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ABILITY).range(32.0).duration(ABILITY_TICKS);
      if (cooldown > 0) {
         builder.cooldown(cooldown);
      }

      return builder.build();
   }
}
