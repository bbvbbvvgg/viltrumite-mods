package dev.baranhan.viltrumitecore.hero.venom;

/** Bits of {@code HeroPublicSnapshot.heroFlags} for Venom (append only). */
public final class VenomFlags {
   /** The symbiote covers the host (Venom form; also true during the transformation's last stages). */
   public static final int FORM = 0;
   /** Rage ("feeding frenzy"). */
   public static final int RAGE = 1;
   /** The hardened shell is up. */
   public static final int SHELL = 2;
   /** A body hangs on the tendrils (controlTargetId). */
   public static final int HOLDING = 3;
   /** A tendril line is out (actionTarget = its end). */
   public static final int TENDRIL = 4;
   /** Clinging to a wall (server confirms the client's crawl for everyone's pose). */
   public static final int WALL = 5;
   /** Possessing a host: this body is the husk; controlTargetId = the host. */
   public static final int POSSESSING = 6;
   /** Recoiling from fire or a loud sound. */
   public static final int RECOIL = 7;
   /** Fought recently. */
   public static final int IN_COMBAT = 8;
   /** A Spider-Man is near (symbiote reaction, client tint and whisper). */
   public static final int SPIDER_NEAR = 9;
   /** May crawl walls now (form, no shell, not recoiling, not possessing). */
   public static final int CRAWL = 10;
   /** The tendril line is wrapped around a Spider's web line. */
   public static final int ON_WEB = 11;

   private VenomFlags() {
   }

   public static boolean has(int flags, int bit) {
      return (flags & 1 << bit) != 0;
   }

   public static int set(int flags, int bit, boolean on) {
      return on ? flags | 1 << bit : flags & ~(1 << bit);
   }
}
