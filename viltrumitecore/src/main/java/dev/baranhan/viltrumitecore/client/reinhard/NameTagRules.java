package dev.baranhan.viltrumitecore.client.reinhard;

/**
 * Name tag rules of vanilla/Forge mirrored for the cancelled player render. The text itself is
 * drawn by vanilla {@code renderNameTag} through {@code EntityRendererNameTagInvoker}.
 */
public final class NameTagRules {
   /** First text pass colour of vanilla renderNameTag (553648127): alpha 0x20 white. */
   public static final int FAINT_TEXT = 0x20FFFFFF;

   private NameTagRules() {
   }

   /** Forge EntityRenderer.render: DENY hides, ALLOW forces, DEFAULT asks shouldShowName. */
   public static boolean show(boolean deny, boolean allow, boolean shouldShowName) {
      return !deny && (allow || shouldShowName);
   }

   /** The faint pass is see-through through walls unless the player sneaks (isDiscrete). */
   public static boolean seeThrough(boolean discrete) {
      return !discrete;
   }

   /** The solid white pass is drawn only when not sneaking. */
   public static boolean solidPass(boolean discrete) {
      return !discrete;
   }
}
