package dev.baranhan.viltrumitecore.city.citizen;

/**
 * Numbers and pure decisions of citizens in peril (#138, unit tested). A citizen only falls for a physical
 * reason: the floor under it was removed (a collapse section, a crater), a blast threw it off a balcony, debris
 * pushed it; nothing here starts a fall on its own. These rules only say what a person does once the ground is
 * gone: a drop under {@link #FALL_MIN_DROP} is a stumble, a longer one is a fall (scream, flailing, witnesses); a
 * person next to a remaining edge may grab it and hang on until somebody comes, it climbs back up or lets go.
 */
public final class PerilRules {
   /** A drop shorter than this (blocks) is a stumble: no fall state, no scream. */
   public static final double FALL_MIN_DROP = 4.0;
   /** A drop at least this long gets the long falling scream. */
   public static final double SCREAM_DROP = 6.0;
   /** The top of a ledge a falling person can grab lies this far above its feet (arms raised). */
   public static final double GRAB_LOW = 0.6;
   public static final double GRAB_HIGH = 2.3;
   /** At most this far (blocks) from the body centre to the edge of the column it grabs. */
   public static final double GRAB_REACH = 0.85;
   /** People hanging from ledges at once per level (more fall straight away). */
   public static final int MAX_HANGING = 4;
   /** Hanging on lasts this long (ticks) plus up to {@link #HANG_SPREAD}. */
   public static final int HANG_MIN = 70;
   public static final int HANG_SPREAD = 110;
   /** Longest a person teeters on a floor that does not go after all (ticks). */
   public static final int TEETER_MAX = 160;
   /** A fall within this many ticks after a collapse marked the floor counts as caused by it. */
   public static final int PERIL_MEMORY = 600;
   /** Witnesses of a fall: range (blocks) and how many react aloud. */
   public static final double WITNESS_RANGE = 28.0;
   public static final int WITNESSES = 2;
   /** A flying player this close to a falling person catches it. */
   public static final double CATCH_RANGE = 1.9;
   /** A player this close to a hanging person pulls it up. */
   public static final double RESCUE_RANGE = 2.5;
   /** The street crowd is heard panicking at most once per this many ticks per level. */
   public static final int CROWD_COOLDOWN = 240;
   /** Street citizens near a collapse needed for the crowd panic. */
   public static final int CROWD_MIN = 3;
   /** Callers for help from other floors of a collapsing building, per collapse. */
   public static final int MAX_CALLERS = 3;

   private PerilRules() {
   }

   /** True for the citizen voice sound events ({@code viltrumitecore:citizen.*}); the client occlusion wraps exactly these. */
   public static boolean isVoice(net.minecraft.resources.ResourceLocation id) {
      return "viltrumitecore".equals(id.getNamespace()) && id.getPath().startsWith("citizen.");
   }

   /** True when a drop of this many blocks is a real fall. */
   public static boolean isFall(double drop) {
      return drop >= FALL_MIN_DROP;
   }

   /** Chance to grab a ledge: better when the floor gave way under the person (it saw it coming), braver is quicker. */
   public static float grabChance(float bravery, boolean collapse) {
      float base = collapse ? 0.55F : 0.3F;
      return Math.max(0.0F, Math.min(0.85F, base * (0.6F + 0.8F * bravery)));
   }

   /** Chance to climb back up alone when the strength runs out (otherwise it lets go). */
   public static float climbChance(float bravery) {
      return 0.25F + 0.35F * Math.max(0.0F, Math.min(1.0F, bravery));
   }

   /** How long one person hangs on, from its seed (ticks). */
   public static int hangTicks(int seed) {
      return HANG_MIN + Math.round(CitizenTraits.unit(seed, 211) * HANG_SPREAD);
   }

   /** True when the top of a ledge at {@code ledgeTop} is within reach of raised hands over feet at {@code feetY}. */
   public static boolean reachable(double feetY, double ledgeTop) {
      double up = ledgeTop - feetY;
      return up >= GRAB_LOW && up <= GRAB_HIGH;
   }

   /**
    * Voice budget of a level (token bucket): a burst of {@link #CAPACITY} voices, refilled at {@link #PER_TICK}
    * per tick. Callers keep a reserve so the falling screams are never crowded out by calls for help.
    */
   public static final class VoiceBudget {
      public static final float CAPACITY = 6.0F;
      public static final float PER_TICK = 0.2F;
      private float tokens = CAPACITY;
      private long last = Long.MIN_VALUE;

      /** Takes one voice when at least {@code reserve} tokens stay after it. */
      public boolean take(long now, float reserve) {
         if (this.last != Long.MIN_VALUE && now > this.last) {
            this.tokens = Math.min(CAPACITY, this.tokens + (now - this.last) * PER_TICK);
         }
         this.last = Math.max(this.last, now);
         if (this.tokens - 1.0F < reserve) {
            return false;
         }
         this.tokens -= 1.0F;
         return true;
      }
   }
}
