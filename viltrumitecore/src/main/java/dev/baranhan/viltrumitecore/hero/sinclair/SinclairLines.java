package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;

/**
 * Sinclair's spoken lines (PR #65 subtitles + 3D portrait in the top hat). Text lives in the lang files under
 * {@code subtitle.viltrumitecore.sinclair.*}; the name is {@code subtitle.viltrumitecore.speaker.sinclair}.
 */
public final class SinclairLines {
   public static final String PREFIX = "subtitle.viltrumitecore.sinclair.";
   /** The capture: "a volunteer from the audience!" (two variants). */
   public static final HeroLine CAPTURE_A = ult("sinclair:capture_a", "capture_a");
   public static final HeroLine CAPTURE_B = ult("sinclair:capture_b", "capture_b");
   public static final HeroLine ACT_BEAM = ult("sinclair:act_beam", "act_beam");
   public static final HeroLine ACT_NOVA = ult("sinclair:act_nova", "act_nova");
   public static final HeroLine ACT_CUT = ult("sinclair:act_cut", "act_cut");
   public static final HeroLine ACT_BARRAGE = ult("sinclair:act_barrage", "act_barrage");
   public static final HeroLine ACT_AEGIS = ult("sinclair:act_aegis", "act_aegis");
   public static final HeroLine ACT_FINALE = ult("sinclair:act_finale", "act_finale");
   public static final HeroLine BOW = HeroLine.builder("sinclair:bow").text(PREFIX + "bow").priority(HeroLine.Priority.ULTIMATE).range(48.0).build();
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

   private SinclairLines() {
   }

   public static String event(ActShape shape) {
      return "sinclair:act_" + shape.key();
   }

   private static HeroLine ult(String id, String key) {
      return HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ULTIMATE).range(48.0).build();
   }

   private static HeroLine ability(String id, String key, int cooldown) {
      HeroLine.Builder builder = HeroLine.builder(id).text(PREFIX + key).priority(HeroLine.Priority.ABILITY).range(32.0);
      if (cooldown > 0) {
         builder.cooldown(cooldown);
      }

      return builder.build();
   }
}
