package dev.baranhan.viltrumitecore.hero;

public enum CleanupReason {
   DEATH,
   DISCONNECT,
   HERO_CHANGE,
   /** Death without a replacement entity (erasure/limbo). */
   ERASED;

   public boolean losesBody() {return this == DEATH || this == ERASED;}
}
