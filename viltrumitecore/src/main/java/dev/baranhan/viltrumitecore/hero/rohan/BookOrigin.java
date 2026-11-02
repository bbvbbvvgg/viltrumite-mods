package dev.baranhan.viltrumitecore.hero.rohan;

/** How a book was opened (sight-based origins fail on blind or sight-immune bodies). */
public enum BookOrigin {
   TOUCH(false),
   SKETCH(true),
   COUNTER(false),
   MANUSCRIPT(true);

   public final boolean sight;

   BookOrigin(boolean sight) {
      this.sight = sight;
   }
}
