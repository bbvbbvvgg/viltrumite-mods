package dev.baranhan.viltrumitecore.hero.book;

/** How far a book opens. */
public enum BookAccess {
   /** Readable and writable. */
   OPEN,
   /** Readable only: writing is refused (and may backfire, see {@link BookProfile#reflectTicks}). */
   READ_ONLY,
   /** The book cannot be opened at all (Regulus's Lion's Heart: time is stopped inside him). */
   CLOSED
}
