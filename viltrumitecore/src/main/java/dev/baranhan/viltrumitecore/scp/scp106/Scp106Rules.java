package dev.baranhan.viltrumitecore.scp.scp106;

/** Pure numbers and decisions of SCP-106 (design §2), unit-tested. */
public final class Scp106Rules {
   public static final float WIDTH = 0.7F;
   public static final float HEIGHT = 2.1F;
   public static final double MAX_HEALTH = 400.0;
   /** Movement attribute (a slow walk, about 2.3 blocks per second). */
   public static final double SPEED = 0.2;
   public static final int EMERGE_TICKS = 70;
   public static final int SINK_TICKS = 45;
   public static final int CAPTURE_TICKS = 60;
   /** Tick of the capture animation when the prey is gone from the world (design: 2.6 s). */
   public static final int CAPTURE_TRANSFER = 52;
   public static final double CAPTURE_REACH = 1.8;
   /** Damage within RETREAT_WINDOW ticks that makes it retreat (it never dies). */
   public static final float RETREAT_DAMAGE = 80.0F;
   public static final int RETREAT_WINDOW = 200;
   /** Somebody who escaped cannot be taken again for this long (10 minutes). */
   public static final int RECAPTURE_COOLDOWN = 12_000;
   /** A hunt gives up after this long without a capture (4 minutes) and it sinks away. */
   public static final int HUNT_TIMEOUT = 4_800;
   /** Walls up to this many blocks thick can be passed through; thicker ones make it sink and re-emerge. */
   public static final int PHASE_THICKNESS = 3;
   public static final double PHASE_SPEED = 0.07;
   public static final int PHASE_MAX_TICKS = 160;
   /** Inside the pocket: damage of being dragged by the Old Man, and the pause between two hunts. */
   public static final float DRAG_DAMAGE = 6.0F;
   public static final int STALK_MIN = 2_400;
   public static final int STALK_SPREAD = 3_600;
   public static final int POCKET_HUNT_TICKS = 1_400;

   private Scp106Rules() {
   }

   /** Ticks a corrosion film lives (6..10 minutes). */
   public static int corrosionLifetime(double u) {
      return 7_200 + (int)(clamp01(u) * 4_800);
   }

   /** Chance of one natural roll (fraction), doubled at night. */
   public static double naturalChance(double percent, boolean night) {
      double p = Math.max(0.0, Math.min(100.0, percent)) / 100.0;
      return Math.min(1.0, night ? p * 2.0 : p);
   }

   public static long cooldownTicks(double minutes) {
      return (long)(Math.max(0.0, Math.min(144_000.0, minutes)) * 1_200.0);
   }

   public static int checkTicks(double seconds) {
      return (int)Math.round(Math.max(10.0, Math.min(86_400.0, seconds)) * 20.0);
   }

   /** Ticks until the next hunt inside the pocket (2..5 minutes). */
   public static int stalkerDelay(double u) {
      return STALK_MIN + (int)(clamp01(u) * STALK_SPREAD);
   }

   /** Sum of the damage still inside the retreat window, oldest first: entries are {tick, damage}. */
   public static boolean shouldRetreat(long now, long[] ticks, float[] damage, int count) {
      float sum = 0;
      for (int i = 0; i < count; i++) {
         if (now - ticks[i] <= RETREAT_WINDOW) {
            sum += damage[i];
         }
      }
      return sum >= RETREAT_DAMAGE;
   }

   /** How long it stares when it decides to stop (2..4 s). */
   public static int stareTicks(double u) {
      return 40 + (int)(clamp01(u) * 40);
   }

   /**
    * Decision taken once a second while stalking: 0 keep walking, 1 stop and stare, 2 sink and re-emerge behind the
    * prey. Staring needs the prey in sight and not too far; re-emerging needs some distance (it is slow, not stupid).
    */
   public static int decide(double u, double distance, boolean seesPrey) {
      if (seesPrey && distance < 18.0 && distance > 3.5 && u < 0.07) {
         return 1;
      }
      if (distance > 12.0 && u > 0.955) {
         return 2;
      }
      return 0;
   }

   /** Capture progress 0..1 of the capture animation tick. */
   public static double captureProgress(int tick) {
      return clamp01(tick / (double)CAPTURE_TRANSFER);
   }

   private static double clamp01(double u) {
      return u < 0 ? 0 : u > 1 ? 1 : u;
   }

   /** Health actually removed by a clean hit: ordinary damage stops one point above death. */
   public static float cleanDealt(boolean bypass, float amount, float health) {
      return bypass ? amount : Math.max(0.0F, Math.min(amount, health - 1.0F));
   }

   /**
    * Whether an entity seen for the first time this session must be dropped: only one deserialized from disk
    * (chunk reload, restart) — a saved pocket hunter, or one away for more than a minute. A fresh spawn never is.
    */
   public static boolean staleOnLoad(boolean loadedFromDisk, boolean pocketHunter, long lastSeen, long now) {
      return loadedFromDisk && (pocketHunter || lastSeen != Long.MIN_VALUE && now - lastSeen > 1_200);
   }
}
