package dev.baranhan.viltrumitecore.hero.venom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Venom's pure rules (spec §3, §6, §7, §8): the numbers, the stages, the weaknesses, the takeover's resistance. */
class VenomRulesTest {
   @Test
   void controlPhaseIsClampedAndSurvivesABrokenConfig() {
      assertEquals(60, VenomRules.controlTicks(1.0));
      assertEquals(200, VenomRules.controlTicks(10.0));
      assertEquals(300, VenomRules.controlTicks(99.0));
      assertEquals(200, VenomRules.controlTicks(Double.NaN));
      assertEquals(200, VenomRules.controlTicks(Double.POSITIVE_INFINITY));
      assertTrue(VenomRules.BOSS_CONTROL_TICKS < VenomRules.controlTicks(10.0), "the boss rides shorter");
   }

   @Test
   void transformationRunsThroughFiveStagesInOrder() {
      int last = -1;
      Set<Integer> seen = new HashSet<>();
      for (int t = 0; t < VenomRules.TRANSFORM_TICKS; t++) {
         int s = VenomRules.stage(t);
         assertTrue(s >= last, "stages never go back at " + t);
         last = s;
         seen.add(s);
      }
      assertEquals(Set.of(0, 1, 2, 3, 4), seen);
      assertEquals(0, VenomRules.stage(VenomRules.STAGE_TORSO - 1));
      assertEquals(1, VenomRules.stage(VenomRules.STAGE_TORSO));
      assertEquals(2, VenomRules.stage(VenomRules.STAGE_GROW));
      assertEquals(3, VenomRules.stage(VenomRules.STAGE_FACE));
      assertEquals(4, VenomRules.stage(VenomRules.STAGE_POSE));
      assertTrue(VenomRules.STAGE_POSE < VenomRules.TRANSFORM_TICKS, "the roar plays inside the move");
      assertEquals(VenomRules.STAGE_POSE, VenomMove.TRANSFORM.contact, "the form switches at the roar");
   }

   @Test
   void resistanceNeedsManyPressesAndDecays() {
      int r = 0;
      int presses = 0;
      while (r < VenomRules.RESIST_TO_BREAK) {
         r = VenomRules.resist(r, true, 0);
         presses++;
         assertTrue(presses < 100, "resistance must be breakable");
      }
      assertEquals(VenomRules.RESIST_TO_BREAK, r, "clamped at the break");
      assertEquals((VenomRules.RESIST_TO_BREAK + VenomRules.RESIST_PER_PRESS - 1) / VenomRules.RESIST_PER_PRESS, presses);
      assertTrue(presses >= 10, "a takeover is not shaken off by two taps");
      assertEquals(50 - VenomRules.RESIST_DECAY * 2, VenomRules.resist(50, false, 8));
      assertEquals(0, VenomRules.resist(3, false, 400), "never below zero");
      assertEquals(VenomRules.RESIST_TO_BREAK, VenomRules.resist(VenomRules.RESIST_TO_BREAK, true, 0));
   }

   @Test
   void onlyAWeakenedOrHeldTargetCanBeTakenOver() {
      assertTrue(VenomRules.weakened(12.0F, 20.0F, false));
      assertFalse(VenomRules.weakened(12.5F, 20.0F, false));
      assertTrue(VenomRules.weakened(20.0F, 20.0F, true), "held or cocooned counts as weakened");
      assertFalse(VenomRules.weakened(0.0F, 0.0F, false), "a broken max health is never weakened");
   }

   @Test
   void fireBurnsThroughTheShellAndSoundIsNotAbsorbed() {
      assertEquals(VenomRules.FIRE_TAKEN, VenomRules.incomingFactor(true, false, true));
      assertEquals(VenomRules.FIRE_TAKEN, VenomRules.incomingFactor(true, true, false));
      assertEquals(VenomRules.SONIC_TAKEN, VenomRules.incomingFactor(false, true, true));
      assertEquals(1.0F - VenomRules.SHELL_ABSORB, VenomRules.incomingFactor(false, false, true), 1.0E-6F);
      assertEquals(1.0F, VenomRules.incomingFactor(false, false, false));
      assertTrue(VenomRules.FIRE_TAKEN > 1.0F && VenomRules.SONIC_TAKEN > 1.0F, "both are real weaknesses");
   }

   @Test
   void theSymbioteTakesTheShockOfAnyFallUnderFortyBlocks() {
      assertEquals(0.0F, VenomRules.fallDamage(39.9F));
      assertEquals(0.0F, VenomRules.fallDamage(-5.0F));
      assertEquals(5.0F, VenomRules.fallDamage(50.0F));
   }

   @Test
   void hungerIsClamped() {
      assertEquals(VenomRules.HUNGER_MAX, VenomRules.feed(VenomRules.HUNGER_MAX - 10, VenomRules.HUNGER_KILL));
      assertEquals(0, VenomRules.feed(5, -VenomRules.RAGE_DRAIN * 10));
      assertEquals(VenomRules.HUNGER_CLAW, VenomRules.feed(0, VenomRules.HUNGER_CLAW));
      assertTrue(VenomRules.RAGE_MIN <= VenomRules.HUNGER_MAX && VenomRules.TAKEOVER_COST < VenomRules.HUNGER_MAX);
   }

   @Test
   void tablesHaveOneEntryPerSlot() {
      assertEquals(VenomRules.COOLDOWNS, VenomRules.COOLDOWN_TICKS.length);
      assertEquals(VenomRules.COMBO_LENGTH, VenomRules.CLAW_DAMAGE.length);
      assertTrue(VenomRules.CLAW_DAMAGE[VenomRules.COMBO_LENGTH - 1] > VenomRules.CLAW_DAMAGE[0], "the third claw is the finisher");
      Set<Integer> cds = new HashSet<>();
      for (VenomAbility a : VenomAbility.values()) {
         assertTrue(a.cooldown >= 0 && a.cooldown < VenomRules.COOLDOWNS, a.name());
         assertTrue(cds.add(a.cooldown), "one cooldown slot per ability: " + a);
         assertEquals(a, VenomAbility.byId(a.id()));
         assertEquals(a, VenomAbility.byAction(a.action()));
         assertTrue(a.cooldownTicks() > 0);
      }
      assertNull(VenomAbility.byId(null));
      assertNull(VenomAbility.byId("venom:nope"));
      assertTrue(VenomAbility.SYMBIOSIS.human());
      assertFalse(VenomAbility.TAKEOVER.human());
   }

   @Test
   void movesAreWellFormed() {
      for (VenomMove m : VenomMove.values()) {
         assertTrue(m.length > 0, m.name());
         assertTrue(m.contact < m.length, m + " contact inside the move");
         assertTrue(m.cancelFrom < m.length, m + " cancel inside the move");
         assertEquals(m, VenomMove.byId(m.ordinal()));
         if (m.locks()) {
            assertTrue(m.roots(), m + " locks, so it roots");
         }
      }
      assertNull(VenomMove.byId(-1));
      assertNull(VenomMove.byId(VenomMove.values().length));
      assertEquals(VenomMove.CLAW_1, VenomMove.claw(-3));
      assertEquals(VenomMove.CLAW_3, VenomMove.claw(9));
      assertEquals(VenomMove.LASH_SLAM, VenomMove.lash(2));
      assertTrue(VenomMove.CLAW_2.isClaw() && !VenomMove.LASH_SWEEP.isClaw());
      assertTrue(VenomMove.LASH_IMPALE.isLash() && !VenomMove.CLAW_1.isLash());
      assertTrue(VenomMove.SHELL.unstoppable() && VenomMove.SHELL.loops());
   }

   @Test
   void flagsRoundTrip() {
      int f = 0;
      for (int bit = 0; bit <= VenomFlags.ON_WEB; bit++) {
         f = VenomFlags.set(f, bit, true);
         assertTrue(VenomFlags.has(f, bit));
      }
      for (int bit = 0; bit <= VenomFlags.ON_WEB; bit++) {
         f = VenomFlags.set(f, bit, false);
         assertFalse(VenomFlags.has(f, bit));
      }
      assertEquals(0, f);
   }

   @Test
   void loudSoundsMakeItRecoilButNotItsOwnVoice() {
      ResourceLocation thunder = new ResourceLocation("minecraft", "entity.lightning_bolt.thunder");
      ResourceLocation step = new ResourceLocation("minecraft", "block.stone.step");
      assertTrue(VenomWeakness.loud(thunder, 1.0F, VenomRules.SOUND_RADIUS - 1));
      assertFalse(VenomWeakness.loud(thunder, 1.0F, VenomRules.SOUND_RADIUS + 1), "too far");
      assertTrue(VenomWeakness.loud(thunder, 3.0F, VenomRules.SOUND_RADIUS * 3 - 1), "a louder event reaches further");
      assertFalse(VenomWeakness.loud(thunder, 50.0F, VenomRules.SOUND_RADIUS * 3 + 1), "the reach is capped");
      assertFalse(VenomWeakness.loud(step, 1.0F, 1.0), "an ordinary step is no weapon");
      assertTrue(VenomWeakness.loud(step, VenomRules.SOUND_MIN_VOLUME, 1.0), "anything played loud enough is");
      assertFalse(VenomWeakness.loud(new ResourceLocation("viltrumitecore", "venom.roar"), 4.0F, 1.0), "its own roar");
      assertFalse(VenomWeakness.loud(null, 1.0F, 1.0));
      assertTrue(VenomWeakness.loudDistance(5.0, 1.0F, true));
      assertFalse(VenomWeakness.loudDistance(5.0, 1.0F, false));
   }

   /** Playtest (bell next to the boss): a loud sound has its own subtitle, the symbiote never cries «fire» at a bell. */
   @Test
   void aBellNeverSpeaksTheFireLine() throws Exception {
      assertFalse(VenomLines.RECOIL_SOUND.fragments().equals(VenomLines.RECOIL.fragments()));
      var merged = dev.baranhan.viltrumitecore.integration.ShippedLang.merged();
      assertEquals(12, merged.size(), "the twelve shipped locales");
      for (var entry : merged.entrySet()) {
         for (var line : VenomLines.LINES.all()) {
            for (String key : line.fragments()) {
               assertTrue(entry.getValue().has(key) && !entry.getValue().get(key).getAsString().isBlank(), entry.getKey() + " " + key);
            }
         }
      }
      var ru = merged.containsKey("ru_ru.json") ? merged.get("ru_ru.json") : merged.get("ru_ru");
      String sound = ru.get(VenomLines.RECOIL_SOUND.fragments().get(0)).getAsString();
      assertFalse(sound.contains("Огонь"), sound);
   }
}
