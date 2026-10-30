package dev.baranhan.viltrumitecore.hero;

/** Rules for the legacy 18-slot, three-page ability bar. */
public final class AbilityPages {
   public static final int PAGES = 3;
   public static final int PAGE_SIZE = 6;
   public static final int FIRST = 0;

   private AbilityPages() {}

   /** Alt skips empty pages; a hero with one populated page stays on that page. */
   public static int next(int current, String[] slots) {
      int from = current >= 0 && current < PAGES ? current : PAGES - 1;
      for (int step = 1; step <= PAGES; step++) {
         int page = (from + step) % PAGES;
         if (page == from) break;
         if (filled(page, slots)) return page;
      }
      return current >= 0 && current < PAGES ? current : FIRST;
   }

   /** Repair a saved page that is out of range or contains no abilities. */
   public static int settle(int current, String[] slots) {
      if (current >= 0 && current < PAGES && filled(current, slots)) return current;
      int page = next(current, slots);
      return page >= 0 && page < PAGES && filled(page, slots) ? page : FIRST;
   }

   public static boolean filled(int page, String[] slots) {
      for (int i = page * PAGE_SIZE; i < (page + 1) * PAGE_SIZE && i < slots.length; i++) {
         if (slots[i] != null && !slots[i].isEmpty()) return true;
      }
      return false;
   }
}
