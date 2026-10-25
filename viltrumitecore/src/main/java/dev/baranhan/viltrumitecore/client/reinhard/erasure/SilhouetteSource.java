package dev.baranhan.viltrumitecore.client.reinhard.erasure;

/** A missing/cross-dimension entity never becomes a missing cinematic. */
public enum SilhouetteSource {
   REAL_PROJECTED, REAL_CENTER, GENERIC_CENTER;
   public static SilhouetteSource choose(boolean sameLevel, boolean resolved, boolean onScreen) {
      return !sameLevel || !resolved ? GENERIC_CENTER : onScreen ? REAL_PROJECTED : REAL_CENTER;
   }
}
