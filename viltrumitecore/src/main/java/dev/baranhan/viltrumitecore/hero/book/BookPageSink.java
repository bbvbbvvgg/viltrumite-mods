package dev.baranhan.viltrumitecore.hero.book;

/**
 * Collects the lines of a book. Every line is a lang key plus plain string arguments (numbers, names, ids): the
 * reader's client translates, so every locale reads the same book. Heroes and adapters only add public data: what the
 * scanner, the codex or the game's own APIs already show, never other mods' private fields.
 */
public interface BookPageSink {
   /** Starts a new page with a title key. */
   void page(String titleKey);

   /** Adds a line to the current page. */
   void line(String key, String... args);
}
