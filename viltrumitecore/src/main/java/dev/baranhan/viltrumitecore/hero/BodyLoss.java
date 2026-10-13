package dev.baranhan.viltrumitecore.hero;

/** Explicit same-entity death and fresh-body sequencing. Minecraft adapters own the side effects. */
public final class BodyLoss {
   private BodyLoss() {}
   public interface ErasedBody {
      void cleanupHero(CleanupReason reason);
      void clearHeldInputs();
      void releaseControls();
      void releaseLegacyGrabs();
      void resetMotionAndSession();
      void clearOwnerSections();
      void sync();
   }
   public interface FreshBody {
      void controlBaseline();
      void restorePendingFlags();
      void flightSync();
      void resendOwner();
      void heroRespawn();
      void legacyRespawn();
   }
   public static void erase(ErasedBody body) {
      body.cleanupHero(CleanupReason.ERASED);
      body.clearHeldInputs();
      body.releaseControls();
      body.releaseLegacyGrabs();
      body.resetMotionAndSession();
      body.clearOwnerSections();
      body.sync();
   }
   public static void fresh(FreshBody body) {
      body.controlBaseline();
      body.restorePendingFlags();
      body.flightSync();
      body.resendOwner();
      body.heroRespawn();
      body.legacyRespawn();
   }
}
