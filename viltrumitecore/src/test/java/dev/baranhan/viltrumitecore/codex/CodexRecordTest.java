package dev.baranhan.viltrumitecore.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Per-player encyclopedia progress: ids, limits, isolation of copies and the NBT round trip. */
class CodexRecordTest {
   @Test
   void heroesAndInteractionsAreNormalised() {
      CodexRecord r = new CodexRecord();
      assertTrue(r.addHero("Thor"));
      assertFalse(r.addHero("thor"), "same hero twice");
      assertTrue(r.hasHero("THOR"));
      assertFalse(r.hasHero("dio"));
      assertFalse(r.addHero(null));
      assertFalse(r.addHero("bad id with spaces"));
      assertTrue(r.addInteraction("dio_thanos_timestop"));
      assertTrue(r.hasInteraction("DIO_THANOS_TIMESTOP"));
      assertTrue(r.removeInteraction("dio_thanos_timestop"));
      assertFalse(r.hasInteraction("dio_thanos_timestop"));
   }

   @Test
   void limitsHold() {
      CodexRecord r = new CodexRecord();
      for (int i = 0; i < CodexRecord.MAX_HEROES + 10; i++) {
         r.addHero("h" + i);
      }
      for (int i = 0; i < CodexRecord.MAX_INTERACTIONS + 10; i++) {
         r.addInteraction("i" + i);
      }
      assertEquals(CodexRecord.MAX_HEROES, r.heroes().size());
      assertEquals(CodexRecord.MAX_INTERACTIONS, r.interactions().size());
      assertFalse(CodexRecord.validId("x".repeat(CodexRecord.MAX_ID + 1)));
   }

   @Test
   void copiesAreIndependent() {
      CodexRecord a = new CodexRecord(List.of("thor"), List.of("one"));
      CodexRecord b = a.copy();
      b.addHero("dio");
      b.addInteraction("two");
      assertFalse(a.hasHero("dio"), "another record (another player) never changes");
      assertFalse(a.hasInteraction("two"));
   }

   @Test
   void nbtRoundTrip() {
      CodexRecord r = new CodexRecord(List.of("thor", "captain"), List.of("a_b", "c"));
      CompoundTag tag = CodexProgress.toTag(r);
      CodexRecord back = CodexProgress.fromTag(tag);
      assertEquals(r.heroes(), back.heroes());
      assertEquals(r.interactions(), back.interactions());
      assertTrue(CodexProgress.fromTag(new CompoundTag()).heroes().isEmpty(), "old saves start empty");
   }
}
