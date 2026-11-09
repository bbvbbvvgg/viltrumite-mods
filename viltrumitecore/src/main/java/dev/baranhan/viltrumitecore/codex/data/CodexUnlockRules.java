package dev.baranhan.viltrumitecore.codex.data;

import dev.baranhan.viltrumitecore.codex.CodexRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Which Codex 2.0 entries a record has open (pure, unit-tested). An entry is open when it is open by
 * default, when it was unlocked explicitly (saved in the record), or when one of its conditions is already
 * satisfied by the record itself (a chosen hero, a discovered interaction): those need no extra save.
 */
public final class CodexUnlockRules {
   private CodexUnlockRules() {
   }

   public static boolean isUnlocked(CodexEntryDef entry, @Nullable CodexRecord record) {
      if (entry.openByDefault()) {
         return true;
      }
      if (record == null) {
         return false;
      }
      if (record.hasEntry(entry.id())) {
         return true;
      }
      for (UnlockCondition c : entry.unlock()) {
         if (c.type() == UnlockCondition.Type.HERO_CHOSEN && record.hasHero(c.target())
            || c.type() == UnlockCondition.Type.INTERACTION && record.hasInteraction(c.target())) {
            return true;
         }
      }
      return false;
   }

   /** Entries one of whose conditions is exactly {@code type}/{@code value}. */
   public static List<CodexEntryDef> matching(Collection<CodexEntryDef> entries, UnlockCondition.Type type, @Nullable String value) {
      List<CodexEntryDef> out = new ArrayList<>();
      if (value == null) {
         return out;
      }
      for (CodexEntryDef entry : entries) {
         for (UnlockCondition c : entry.unlock()) {
            if (c.matches(type, value)) {
               out.add(entry);
               break;
            }
         }
      }
      return out;
   }

   /** Entries that the event would open now: matching and not yet open. */
   public static List<CodexEntryDef> newlyUnlocked(Collection<CodexEntryDef> entries, UnlockCondition.Type type, @Nullable String value,
      @Nullable CodexRecord record) {
      List<CodexEntryDef> out = new ArrayList<>();
      for (CodexEntryDef entry : matching(entries, type, value)) {
         if (!isUnlocked(entry, record)) {
            out.add(entry);
         }
      }
      return out;
   }

   /** {open, total} over the given entries. */
   public static int[] progress(Collection<CodexEntryDef> entries, @Nullable CodexRecord record) {
      int open = 0;
      for (CodexEntryDef entry : entries) {
         if (isUnlocked(entry, record)) {
            open++;
         }
      }
      return new int[]{open, entries.size()};
   }
}
