package dev.baranhan.viltrumitecore.client.thor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The R2b clip plan lists every row of the attack table with its length, contact and beats. */
class ThorClipPlanTest {
   static final Path PLAN = Path.of("../docs/design/2026-10-28-thor-r2b-clip-plans.md");
   private static final Pattern ROW = Pattern.compile("^\\| `([a-z0-9_]+)` \\| (\\d+) \\([0-9.]+ с\\) \\| (\\d+) \\| ([0-9 ·]+) \\| ([a-z ·]+) \\|", Pattern.MULTILINE);

   @Test
   void beatsFollowTheRow() {
      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         List<ThorClipBeats.Beat> beats = ThorClipBeats.of(row.phases());
         assertEquals(row.length(), beats.get(beats.size() - 1).tick(), row.clip() + " ends on the length");
         assertTrue(beats.stream().anyMatch(b -> b.name().equals(ThorClipBeats.CONTACT) && b.tick() == row.phases().contactTick()), row.clip());
         for (int i = 1; i < beats.size(); i++) {
            assertTrue(beats.get(i).tick() > beats.get(i - 1).tick(), row.clip() + " beats ordered");
         }
      }
   }

   @Test
   void thePlanMatchesTheTable() throws Exception {
      String plan = Files.readString(PLAN);
      Map<String, String[]> rows = new HashMap<>();
      Matcher m = ROW.matcher(plan);
      while (m.find()) {
         rows.put(m.group(1), new String[]{m.group(2), m.group(3), m.group(4).trim(), m.group(5).trim()});
      }

      for (ThorAttacks.Attack row : ThorAttacks.ALL) {
         String[] line = rows.get(row.clip());
         assertNotNull(line, row.clip() + " is in the plan");
         List<ThorClipBeats.Beat> beats = ThorClipBeats.of(row.phases());
         assertEquals(row.length(), Integer.parseInt(line[0]), row.clip() + " length");
         assertEquals(row.phases().contactTick(), Integer.parseInt(line[1]), row.clip() + " contact");
         assertEquals(beats.stream().map(b -> Integer.toString(b.tick())).collect(Collectors.joining(" · ")), line[2], row.clip() + " beats");
         assertEquals(beats.stream().map(ThorClipBeats.Beat::name).collect(Collectors.joining(" · ")), line[3], row.clip() + " beat names");
      }

      assertEquals(ThorAttacks.ALL.size(), rows.size(), "no rows for clips outside the table");
   }
}
