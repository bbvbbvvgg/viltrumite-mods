package dev.baranhan.viltrumitecore.hero.reinhard;

/** Pure MMB mode switch (spec §5): Restrained → Knight → Holy Sword → Restrained. */
public final class ModeCycle {
   /** Outcome of one press; a refused press keeps the current mode. */
   public record Result(boolean accepted, ReinhardMode next) {
   }

   private ModeCycle() {
   }

   /** Refused while the mode cooldown runs or Reinhard is busy (Reid drawing/sheathing, World Cut, Erasure). */
   public static Result press(ReinhardMode current, int cooldown, boolean busy) {
      if (cooldown > 0 || busy) {
         return new Result(false, current);
      }

      return new Result(true, current.next());
   }
}
