package dev.baranhan.viltrumitecore.worldevent.chitauri;

/**
 * Pure numbers of the Chitauri invasion (docs/plans/2026-10-31-chitauri-invasion.md): phase
 * lengths, opening beats, wave plans, caps and the portal opening curve. No Minecraft types,
 * so tests and both sides share them.
 */
public final class ChitauriRules {
   // ---- caps (live entities per invasion, blocks per invasion) ----
   public static final int MAX_SOLDIERS = 24;
   public static final int MAX_CHARIOTS = 8;
   public static final int MAX_LEVIATHANS = 2;
   /** Leviathans one invasion may send in total (the climax may replace a dead one once). */
   public static final int MAX_LEVIATHANS_TOTAL = 3;
   public static final int MAX_DESTROYED_BLOCKS = 40000;
   /** Falling debris blocks alive at once (HeroDebris), shared by every Chitauri smash. */
   public static final int MAX_FLYING_DEBRIS = 48;

   // ---- portal geometry ----
   public static final double PORTAL_HEIGHT = 62.0;
   public static final float PORTAL_RADIUS = 20.0F;
   /** Tilt of the portal plane from horizontal (degrees): it faces down and a little sideways. */
   public static final float PORTAL_TILT = 18.0F;

   // ---- phase lengths (ticks) ----
   public static final int OPENING_TICKS = 700;
   public static final int GROUND_TICKS = 1800;
   public static final int AIR_TICKS = 1600;
   public static final int LEVIATHAN_TICKS = 1800;
   public static final int CLIMAX_TICKS = 2400;
   public static final int CLOSING_TICKS = 320;

   // ---- opening beats (ticks into OPENING) ----
   /** Sky darkens and the air shimmers. */
   public static final int BEAT_DARKEN = 0;
   /** The energy beam rises from the anchor and the sky tears open. */
   public static final int BEAT_BEAM = 120;
   /** The portal reaches full size. */
   public static final int BEAT_PORTAL_FULL = 300;
   /** Lightning, vortex and energy streams. */
   public static final int BEAT_STORM = 300;
   public static final int BEAT_FIRST_CHARIOTS = 420;
   public static final int BEAT_FIRST_LEVIATHAN = 560;

   // ---- closing beats (ticks into CLOSING) ----
   /** The hive link breaks: soldiers collapse, chariots fall, Leviathans die. */
   public static final int BEAT_SEVER = 70;
   /** The portal is gone. */
   public static final int BEAT_PORTAL_GONE = 260;

   /** Ticks without any player in the invasion level before it stops by itself. */
   public static final int EMPTY_LEVEL_TICKS = 600;

   private ChitauriRules() {
   }

   /** One spawn wave: carriers bring 2 troopers each, gunships carry a pilot and a gunner. */
   public record Wave(int carriers, int gunships, int leviathans, int pause) {
      public int soldiers() {
         return this.carriers * 3 + this.gunships * 2;
      }
   }

   public static int length(ChitauriPhase phase) {
      return switch (phase) {
         case OPENING -> OPENING_TICKS;
         case GROUND_ASSAULT -> GROUND_TICKS;
         case AIR_ASSAULT -> AIR_TICKS;
         case LEVIATHAN -> LEVIATHAN_TICKS;
         case CLIMAX -> CLIMAX_TICKS;
         case CLOSING -> CLOSING_TICKS;
         case ENDED -> 0;
      };
   }

   /**
    * The wave plan of a fighting phase. {@code index} counts the waves already sent in the phase;
    * null when the phase has no more planned waves (the climax repeats its last wave).
    */
   public static Wave wave(ChitauriPhase phase, int index) {
      return switch (phase) {
         case GROUND_ASSAULT -> switch (index) {
            case 0 -> new Wave(3, 1, 0, 420);
            case 1 -> new Wave(4, 1, 0, 480);
            case 2 -> new Wave(5, 0, 0, 520);
            default -> null;
         };
         case AIR_ASSAULT -> switch (index) {
            case 0 -> new Wave(1, 4, 0, 500);
            case 1 -> new Wave(2, 6, 0, 600);
            default -> null;
         };
         case LEVIATHAN -> switch (index) {
            case 0 -> new Wave(2, 2, 1, 600);
            case 1 -> new Wave(3, 2, 0, 600);
            default -> null;
         };
         case CLIMAX -> new Wave(index % 2 == 0 ? 3 : 2, 3, index == 2 ? 1 : 0, 420);
         default -> null;
      };
   }

   /** Pure: portal opening 0..1 at {@code t} ticks into {@code phase} (eased; shrinks while closing). */
   public static float portalOpen(ChitauriPhase phase, float t) {
      switch (phase) {
         case OPENING: {
            if (t < BEAT_BEAM) {
               return 0.0F;
            }
            float f = Math.min(1.0F, (t - BEAT_BEAM) / (float)(BEAT_PORTAL_FULL - BEAT_BEAM));
            return smooth(f);
         }
         case CLOSING: {
            float f = Math.min(1.0F, Math.max(0.0F, (t - 20.0F) / (BEAT_PORTAL_GONE - 20.0F)));
            return 1.0F - smooth(f) * smooth(f);
         }
         case ENDED:
            return 0.0F;
         default:
            return 1.0F;
      }
   }

   /** Pure: sky darkening 0..1 (opening ramps in over the first beats, closing fades it out). */
   public static float skyDark(ChitauriPhase phase, float t) {
      switch (phase) {
         case OPENING:
            return smooth(Math.min(1.0F, t / (float)BEAT_BEAM));
         case CLOSING:
            return 1.0F - smooth(Math.min(1.0F, Math.max(0.0F, (t - 120.0F) / 200.0F)));
         case ENDED:
            return 0.0F;
         default:
            return 1.0F;
      }
   }

   /** Pure: true while the portal sends nothing (opening before the first chariots, closing). */
   public static boolean quietPortal(ChitauriPhase phase, int t) {
      return phase == ChitauriPhase.OPENING && t < BEAT_FIRST_CHARIOTS || phase == ChitauriPhase.CLOSING || phase == ChitauriPhase.ENDED;
   }

   /** Pure: how many of a wave's units fit under the live caps. */
   public static int fit(int wanted, int live, int cap) {
      return Math.max(0, Math.min(wanted, cap - live));
   }

   /** Pure: the portal normal (unit, pointing out of the portal towards the city) for a tilt yaw in degrees. */
   public static double[] portalNormal(float tiltYawDeg) {
      double tilt = Math.toRadians(PORTAL_TILT);
      double yaw = Math.toRadians(tiltYawDeg);
      return new double[]{Math.sin(tilt) * -Math.sin(yaw), -Math.cos(tilt), Math.sin(tilt) * Math.cos(yaw)};
   }

   public static float smooth(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }
}
