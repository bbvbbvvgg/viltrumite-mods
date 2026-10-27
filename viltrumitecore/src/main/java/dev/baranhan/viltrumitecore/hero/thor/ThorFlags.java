package dev.baranhan.viltrumitecore.hero.thor;

/**
 * HeroPublicSnapshot.heroFlags layout for Thor.
 * <ul>
 *   <li>bits 0-1: {@link HammerState} ordinal (0 = on the belt);</li>
 *   <li>bit 2: electric mode;</li>
 *   <li>bit 3: grab window open (the client MMB claim reads it);</li>
 *   <li>bit 4: camera lease held (the client camera of the leasing session);</li>
 *   <li>bit 5: scene lock held (a scene owns Thor's mouse and flight).</li>
 * </ul>
 * The running move is not a flag: it travels as actionId / variant
 * (phase | step &lt;&lt; 2).
 */
public final class ThorFlags {
   public static final int HAMMER_MASK = 0b11;
   public static final int ELECTRIC = 2;
   public static final int GRAB_WINDOW = 3;
   public static final int CAMERA_LEASE = 4;
   public static final int SCENE = 5;

   private ThorFlags() {
   }

   public static int compose(HammerState hammer, ThorMode mode, boolean grabWindow) {
      return compose(hammer, mode, grabWindow, false);
   }

   public static int compose(HammerState hammer, ThorMode mode, boolean grabWindow, boolean cameraLease) {
      return compose(hammer, mode, grabWindow, cameraLease, false);
   }

   public static int compose(HammerState hammer, ThorMode mode, boolean grabWindow, boolean cameraLease, boolean scene) {
      int flags = hammer.ordinal() & HAMMER_MASK;
      if (mode == ThorMode.ELECTRIC) {
         flags |= 1 << ELECTRIC;
      }

      if (grabWindow) {
         flags |= 1 << GRAB_WINDOW;
      }

      if (cameraLease) {
         flags |= 1 << CAMERA_LEASE;
      }

      if (scene) {
         flags |= 1 << SCENE;
      }

      return flags;
   }

   public static HammerState hammer(int flags) {
      return HammerState.byOrdinal(flags & HAMMER_MASK);
   }

   public static ThorMode mode(int flags) {
      return (flags & 1 << ELECTRIC) != 0 ? ThorMode.ELECTRIC : ThorMode.NORMAL;
   }

   public static boolean grabWindow(int flags) {
      return (flags & 1 << GRAB_WINDOW) != 0;
   }

   public static boolean cameraLease(int flags) {
      return (flags & 1 << CAMERA_LEASE) != 0;
   }

   public static boolean scene(int flags) {
      return (flags & 1 << SCENE) != 0;
   }
}
