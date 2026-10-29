package dev.baranhan.viltrumitecore.hero.thor;

/** Test fixtures: put Mjölnir somewhere on a bare state without the world store (package-private writer). */
public final class ThorStateFixtures {
   private ThorStateFixtures() {
   }

   public static void hammer(ThorState state, HammerState hammer) {
      state.projectForTests(hammer, state.hammerDetached());
   }

   public static void detached(ThorState state, boolean detached) {
      state.projectForTests(state.hammer(), detached);
   }
}
