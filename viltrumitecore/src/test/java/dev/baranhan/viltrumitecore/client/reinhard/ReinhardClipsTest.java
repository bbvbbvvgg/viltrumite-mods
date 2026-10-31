package dev.baranhan.viltrumitecore.client.reinhard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.animation.AnimationParser;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Stage 3 Task 11: every shipped ability's clips exist and cover the action they play over. */
class ReinhardClipsTest {
   private static final Path ROOT = Path.of("src/main/resources/assets/viltrumitecore");
   /** A hold clip (no fixed length): it must hold its last frame. */
   private static final int HOLD = -1;
   private static final int LOOP = -2;

   private static Map<String, Animation> clips() throws IOException {
      AnimationParser.lastWarnings.clear();
      Map<String, Animation> clips = new HashMap<>(AnimationParser.parse(JsonParser.parseString(Files.readString(ROOT.resolve(ReinhardAssets.ANIMATIONS.getPath()))).getAsJsonObject()));
      // THE FINAL PUNCH keeps its 24 s take in its own file (FinalPunchPose).
      clips.putAll(AnimationParser.parse(JsonParser.parseString(Files.readString(ROOT.resolve(FinalPunchPose.CLIP.getPath()))).getAsJsonObject()));
      assertTrue(AnimationParser.lastWarnings.isEmpty(), AnimationParser.lastWarnings.toString());
      return clips;
   }

   /** Ticks each clip plays for (the action length, or the part of it the clip covers). */
   private static Map<String, Integer> ticks() {
      Map<String, Integer> t = new HashMap<>();
      for (var move : dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves.FIST) t.put(move.clip(), move.ticks());
      for (var move : dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves.REID) t.put(move.clip(), move.ticks());
      t.put(ReinhardClips.STEP_DASH, ReinhardRules.STEP_ACTION_TICKS);
      t.put(ReinhardClips.CATCH_READY, ReinhardRules.CATCH_WINDOW);
      t.put(ReinhardClips.CATCH, ReinhardRules.CATCH_SNAP_TICKS);
      t.put(ReinhardClips.COMBO_1, ReinhardRules.COMBO_STEP_TICKS[0]);
      t.put(ReinhardClips.COMBO_2, ReinhardRules.COMBO_STEP_TICKS[1]);
      t.put(ReinhardClips.COMBO_3, ReinhardRules.COMBO_STEP_TICKS[2]);
      t.put(ReinhardClips.KICK_TOSS, ReinhardRules.KICK_TOSS_TICKS);
      t.put(ReinhardClips.KICK, ReinhardRules.KICK_ACTION_TICKS - ReinhardRules.KICK_TOSS_TICKS);
      t.put(ReinhardClips.GRAB_HOLD, HOLD);
      t.put(ReinhardClips.THROW, ReinhardRules.THROW_ACTION_TICKS);
      t.put(ReinhardClips.DASH_BEHIND, ReinhardRules.BEHIND_ACTION_TICKS);
      t.put(ReinhardClips.STOMP, ReinhardRules.STOMP_TICKS);
      t.put(ReinhardClips.REFLECT_READY, ReinhardRules.REFLECT_WINDOW);
      t.put(ReinhardClips.REFLECT, ReinhardRules.REFLECT_SNAP_TICKS);
      t.put(ReinhardClips.REID_DRAW, ReinhardRules.REID_DRAW_TICKS);
      t.put(ReinhardClips.REID_SHEATHE, ReinhardRules.REID_SHEATHE_TICKS);
      t.put(ReinhardClips.IDLE_HOLY_DRAWN, LOOP);
      t.put(ReinhardClips.SHEATH_STRIKE, ReinhardRules.SHEATH_TICKS);
      t.put(ReinhardClips.SAW_SPHERE, dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography.LENGTH);
      t.put(ReinhardClips.WORLD_CUT, ReinhardRules.WORLD_CUT_BLAST+20);
      t.put(ReinhardClips.WORLD_CUT_FROM_LEFT, ReinhardRules.WORLD_CUT_BLAST+20);
      t.put("diagonal_world_cut",ReinhardRules.WORLD_CUT_BLAST+20);
      t.put("world_erasure", ReinhardRules.ERASE_CLIP_LENGTH);
      t.put(ReinhardClips.FINAL_PUNCH, dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules.LENGTH);
      t.put(ReinhardClips.CHOP_LEFT,20); t.put(ReinhardClips.CHOP_RIGHT,20);
      return t;
   }

   @Test
   void everyAbilityHasClip() throws IOException {
      Map<String, Animation> clips = clips();
      Map<String, Integer> ticks = ticks();
      int checked = 0;
      for (ReinhardAbility ability : ReinhardAbility.values()) {
         if (ability.stage() > 6) {
            continue;
         }

         assertFalse(ReinhardClips.names(ability).isEmpty(), ability + " has clips");
         for (String name : ReinhardClips.names(ability)) {
            Animation clip = clips.get(name);
            assertNotNull(clip, "clip " + name + " of " + ability);
            Integer length = ticks.get(name);
            assertNotNull(length, "action length known for " + name);
            if (length == LOOP) {
               assertEquals(Animation.LoopType.LOOP, clip.loopType(), name + " loops");
            } else if (length == HOLD) {
               assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, clip.loopType(), name + " holds");
            } else {
               assertTrue(clip.lengthSeconds() + 1.0E-9 >= length / 20.0, name + ": " + clip.lengthSeconds() + " s < " + length + " t");
            }

            checked++;
         }
      }

      assertEquals(32, checked, "registered abilities and their action clips");
      // Every ordinary strike ends exactly on its lock: the chain never starts over a running clip.
      for (var moves : java.util.List.of(dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves.FIST, dev.baranhan.viltrumitecore.hero.reinhard.melee.MeleeMoves.REID))
         for (var move : moves) {
            assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, clips.get(move.clip()).loopType(), move.clip());
            assertEquals(move.ticks() / 20.0, clips.get(move.clip()).lengthSeconds(), 1.0E-6, move.clip());
         }
      for (String loop : new String[]{ReinhardClips.DRAWN_STANCE, ReinhardClips.DRAWN_WALK, ReinhardClips.DRAWN_RUN})
         assertEquals(Animation.LoopType.LOOP, clips.get(loop).loopType(), loop);
      for (String pulse : new String[]{ReinhardClips.AIR_STEP, ReinhardClips.LAND})
         assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, clips.get(pulse).loopType(), pulse);
      // Lethal Chop is the kit chop (no ReinhardAbility): both hand sweeps cover the 1 s chop.
      for (String chop : new String[]{ReinhardClips.CHOP_LEFT, ReinhardClips.CHOP_RIGHT}) {
         assertNotNull(clips.get(chop), "clip " + chop);
         assertEquals(Animation.LoopType.HOLD_ON_LAST_FRAME, clips.get(chop).loopType(), chop);
      }
   }
}
