package dev.baranhan.viltrumitecore.hero;

/**
 * The legacy ability bar: 18 slots = {@link #PAGES} pages of {@link #PAGE_SIZE} keys; the synced
 * active page picks which six the keys fire. Pure rules shared by the Alt swap (server) and the
 * hero change.
 */
public final class AbilityPages {
   public static final int PAGES = 3;
   public static final int PAGE_SIZE = 6;
   /** The page every hero starts on (a hero may move it in {@code enter}, e.g. Reinhard's mode). */
   public static final int FIRST = 0;

   private AbilityPages() {
   }

   /**
    * Alt: the next page after {@code current} that holds at least one ability, wrapping around.
    * Empty pages are skipped (Iron Man's two pages toggle 1 ↔ 2 instead of landing on a dead third
    * page); with no other filled page the bar stays where it is. An out-of-range page restarts the cycle.
    */
   public static int next(int current, String[] slots) {
      int from = current >= 0 && current < PAGES ? current : PAGES - 1;
      for (int step = 1; step <= PAGES; step++) {
         int page = (from + step) % PAGES;
         if (page == from) {
            break;
         }

         if (filled(page, slots)) {
            return page;
         }
      }

      return current >= 0 && current < PAGES ? current : FIRST;
   }

   /** Login / respawn repair: an empty or out-of-range active page moves to the next filled page. */
   public static int settle(int current, String[] slots) {
      if (current >= 0 && current < PAGES && filled(current, slots)) {
         return current;
      }

      int page = next(current, slots);
      return page >= 0 && page < PAGES && filled(page, slots) ? page : FIRST;
   }

   public static boolean filled(int page, String[] slots) {
      for (int i = page * PAGE_SIZE; i < (page + 1) * PAGE_SIZE && i < slots.length; i++) {
         if (slots[i] != null && !slots[i].isEmpty()) {
            return true;
         }
      }

      return false;
   }
}
