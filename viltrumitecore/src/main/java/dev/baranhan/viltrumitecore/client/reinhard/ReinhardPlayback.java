package dev.baranhan.viltrumitecore.client.reinhard;

import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.pose.ActionClock;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.player.AbstractClientPlayer;

/** Sampling policy for Reinhard's stateless geo clips; not a second animation engine. */
public final class ReinhardPlayback {
   private ReinhardPlayback() {}
   private static final Map<AbstractClientPlayer, ActionClock> CLOCKS = new WeakHashMap<>();

   public static void reset(AbstractClientPlayer player) { CLOCKS.remove(player); }

   public static double actionTicks(AbstractClientPlayer player,
      HeroPublicSnapshot snapshot, float partial) {
      return sample(CLOCKS.computeIfAbsent(player,p -> new ActionClock()),snapshot.actionId(),snapshot.variant(),
         snapshot.actionElapsed(),snapshot.actionLength(),player.tickCount+(Float.isFinite(partial)?partial:0),partial);
   }

   /**
    * Pure sampling policy: the world cinematics stay on the authoritative
    * {@code elapsed + partial} their scenes (silence, shatter, blast, erasure)
    * also run on, so the body and the first-person blade never lead them;
    * every other cast uses the bounded predictive render clock.
    */
   static double sample(ActionClock clock, int action, int variant, int elapsed, int length, double clientTicks, float partial) {
      if (authoritative(action)) {
         clock.reset();
         return actionTicks(elapsed, length, partial, 0);
      }
      return clock.sample(action, variant, elapsed, length, clientTicks);
   }

   /** World Cut, Diagonal World Cut, World Erasure and the Saw Sphere: casts with a client scene on the packet/game-time clock. */
   static boolean authoritative(int action) {
      return action == ReinhardAbility.WORLD_CUT.ordinal() || action == ReinhardAbility.DIAGONAL_WORLD_CUT.ordinal()
         || action == ReinhardAbility.WORLD_ERASURE.ordinal() || action == ReinhardAbility.SAW_SPHERE.ordinal();
   }

   /** Identical bounded server time for first person, third person and cinematics. */
   public static double actionTicks(int elapsed, int length, float partial, double held) {
      double ticks = elapsed + (Float.isFinite(partial) ? partial : 0) - (Double.isFinite(held) ? Math.max(0, held) : 0);
      return Math.max(0, length > 0 ? Math.min(length, ticks) : ticks);
   }

   public static double time(Animation clip, double seconds) {
      if (!Double.isFinite(seconds) || seconds <= 0.0 || clip.lengthSeconds() <= 0.0) return 0.0;
      return clip.loopType() == Animation.LoopType.LOOP
         ? seconds % clip.lengthSeconds() : Math.min(seconds, clip.lengthSeconds());
   }

   /** Two-tick eased entry and recovery over the current underlying locomotion. */
   public static float weight(double seconds, double length) {
      if (!Double.isFinite(seconds) || length <= 0 || seconds <= 0 || seconds >= length) return 0;
      double in = Math.min(1, seconds / .1);
      double out = Math.min(1, (length - seconds) / .1);
      return (float)(smooth(in) * smooth(out));
   }

   /** A snap/throw continues the preceding guard/hold: never fade back to idle on its first frame. */
   public static float exit(double seconds, double length) {
      if (!Double.isFinite(seconds) || length <= 0 || seconds >= length) return 0;
      return (float)smooth(Math.max(0, Math.min(1, (length-seconds)/.1)));
   }

   public static float entry(double seconds) {
      return Double.isFinite(seconds) ? (float)smooth(Math.max(0, Math.min(1, seconds/.1))) : 0;
   }

   /** A ready/snap window owns its guard until the actual server window ends. */
   public static float window(double seconds, double length, boolean snap) {
      return exit(seconds, length) * (snap ? 1 : entry(seconds));
   }

   /** Fade body channels only at the sheathed boundary. The grip remains exact. */
   public static float reidWeight(boolean drawing, double seconds, double length) {
      double t = drawing ? seconds/.2 : (length-seconds)/.2;
      return Double.isFinite(t) ? (float)smooth(Math.max(0,Math.min(1,t))) : 0;
   }

   private static double smooth(double t) { return t * t * (3 - 2 * t); }
}
