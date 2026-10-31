package dev.baranhan.viltrumitecore.hero.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.speech.HeroLine;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Final Punch speech: subtitles only (RU/EN and every shipped locale), twelve pages fired
 * by the server scene at {@link FinalPunchRules#LINE_AT}; each replaces the previous one
 * (ULTIMATE), all stop when the scene is cancelled. Keys {@code subtitle.viltrumitecore.reinhard.final_punch.<1..12>}.
 */
public final class FinalPunchLines {
   public static final String PREFIX = "reinhard:final_punch_line_";
   public static final String KEY = "subtitle.viltrumitecore.reinhard.final_punch.";
   static final double RANGE = 64.0;
   private static final HeroLine[] LINES = new HeroLine[FinalPunchRules.LINES];

   static {
      for (int i = 0; i < LINES.length; i++) {
         LINES[i] = HeroLine.builder(event(i)).text(KEY + (i + 1)).priority(HeroLine.Priority.ULTIMATE)
            .duration(FinalPunchRules.lineTicks(i)).cooldown(0).range(RANGE)
            .activeWhile(FinalPunchLines::scene).build();
      }
   }

   private FinalPunchLines() {
   }

   public static String event(int index) {
      return PREFIX + (index + 1);
   }

   public static HeroLines.Builder bind(HeroLines.Builder builder) {
      for (int i = 0; i < LINES.length; i++) {
         builder.onEvent(event(i), LINES[i]);
      }
      return builder;
   }

   public static void say(ServerPlayer player, int index) {
      HeroSpeech.trigger(player, HeroLineTrigger.event(event(index)));
   }

   private static boolean scene(ServerPlayer player) {
      ReinhardState state = ReinhardState.of(player);
      return state != null && state.finalPunch != null;
   }
}
