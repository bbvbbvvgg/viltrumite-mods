package dev.baranhan.viltrumitecore.worldevent.ultron;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ultron's taunt rotation and the queue that keeps simultaneous protocol news from cutting each other. */
class UltronVoiceTest {
   private static final int TAUNT_CD = UltronVoice.TAUNT_COOLDOWN_S * UltronRules.TPS;

   @Test
   void tauntsAreAllHeardBeforeAnyRepeats() {
      Map<String, Integer> said = new HashMap<>();
      Set<String> heard = new HashSet<>();
      int clock = 0;
      for (int i = 0; i < UltronVoice.TAUNTS.size(); i++) {
         String t = UltronVoice.nextTaunt(said, clock);
         assertNotNull(t);
         assertTrue(heard.add(t), "repeated " + t + " before all were heard");
         said.put(t, clock);
         clock += UltronDirector.TAUNT_EVERY_S * UltronRules.TPS;
      }
      assertEquals(Set.copyOf(UltronVoice.TAUNTS), heard);
   }

   @Test
   void tauntsWaitForTheirCooldownThenTheOldestComesFirst() {
      Map<String, Integer> said = new HashMap<>();
      for (int i = 0; i < UltronVoice.TAUNTS.size(); i++) {
         said.put(UltronVoice.TAUNTS.get(i), i * 100);
      }
      int last = (UltronVoice.TAUNTS.size() - 1) * 100;
      assertNull(UltronVoice.nextTaunt(said, last + 1)); // all cooling down: he keeps quiet
      assertEquals(UltronVoice.TAUNTS.get(0), UltronVoice.nextTaunt(said, TAUNT_CD));
      assertEquals(UltronVoice.TAUNTS.get(0), UltronVoice.nextTaunt(said, last + TAUNT_CD)); // oldest of all ready
      said.put(UltronVoice.TAUNTS.get(0), last + TAUNT_CD);
      assertEquals(UltronVoice.TAUNTS.get(1), UltronVoice.nextTaunt(said, last + TAUNT_CD));
   }

   @Test
   void tauntLinesExistWithTheLongCooldown() {
      assertTrue(UltronVoice.TAUNTS.size() >= 6);
      for (String t : UltronVoice.TAUNTS) {
         UltronVoice.Line line = UltronVoice.line(t);
         assertNotNull(line, t);
         assertEquals(UltronVoice.TAUNT_COOLDOWN_S, line.cooldownSeconds(), t);
      }
      // a taunt cannot come back before the others had their turn
      assertTrue(UltronVoice.TAUNT_COOLDOWN_S >= UltronDirector.TAUNT_EVERY_S * (UltronVoice.TAUNTS.size() - 1));
   }

   @Test
   void theQueueKeepsOrderOnceEachAndDropsTheOldestWhenFull() {
      ArrayDeque<UltronVoice.Pending> q = new ArrayDeque<>();
      UltronVoice.enqueue(q, "protocol_anti_air", 10);
      UltronVoice.enqueue(q, "protocol_deflector", 11);
      UltronVoice.enqueue(q, "protocol_anti_air", 12); // again: moves to the tail, not twice
      assertEquals(List.of("protocol_deflector", "protocol_anti_air"), ids(q));
      UltronVoice.enqueue(q, "protocol_fortify", 13);
      UltronVoice.enqueue(q, "protocol_relocation", 14);
      assertEquals(UltronVoice.QUEUE_MAX, q.size());
      assertEquals(List.of("protocol_anti_air", "protocol_fortify", "protocol_relocation"), ids(q));
   }

   @Test
   void aCutLineGoesBackToTheHead() {
      ArrayDeque<UltronVoice.Pending> q = new ArrayDeque<>();
      UltronVoice.enqueue(q, "protocol_fortify", 1);
      UltronVoice.enqueue(q, "protocol_relocation", 2);
      UltronVoice.enqueue(q, "protocol_redistribution", 3);
      UltronVoice.requeue(q, "protocol_anti_air", 4);
      assertEquals(UltronVoice.QUEUE_MAX, q.size());
      assertEquals(List.of("protocol_anti_air", "protocol_fortify", "protocol_relocation"), ids(q));
      assertEquals(4, q.peekFirst().at());
   }

   private static List<String> ids(ArrayDeque<UltronVoice.Pending> q) {
      return q.stream().map(UltronVoice.Pending::id).toList();
   }
}
