package dev.baranhan.viltrumitecore.client.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import net.minecraft.util.Mth;

/**
 * Citizen body language on the shared {@link PoseRig} (animation-system: vanilla body, key
 * tables, {@code finish()} after the layer). Every {@link CitizenState} owns a pose layer whose
 * weight eases in and out in real time (exponential smoothing on the age clock), so a citizen
 * flinches, runs, ducks and gets up without snapping. Calm citizens add idle gestures picked
 * from their seed: phone, crossed arms, hands behind the back, a look at the watch, a head
 * scratch; talkers gesture and nod. Pure client code: the state is the synced byte.
 */
public final class CitizenPoser {
   static final int NONE = 0;
   static final int PHONE = 1;
   static final int CROSSED = 2;
   static final int BEHIND = 3;
   static final int WATCH = 4;
   static final int SCRATCH = 5;
   /** Gesture window, ticks: a calm citizen reconsiders what to do with the hands this often. */
   static final int WINDOW = 160;

   // right arm = main arm (rig side +1); left arm rows are mirrored by hand (yaw/roll negated)
   private static final float[][] PHONE_R = row(-62.0F, -28.0F, 4.0F);
   private static final float[][] PHONE_HEAD = row(22.0F, -6.0F, 0.0F);
   private static final float[][] CROSSED_R = row(-50.0F, -44.0F, 10.0F);
   private static final float[][] CROSSED_L = row(-54.0F, 44.0F, -10.0F);
   private static final float[][] BEHIND_R = row(24.0F, 0.0F, -14.0F);
   private static final float[][] BEHIND_L = row(24.0F, 0.0F, 14.0F);
   private static final float[][] WATCH_L = row(-78.0F, 38.0F, 0.0F);
   private static final float[][] WATCH_HEAD = row(26.0F, -16.0F, 0.0F);
   private static final float[][] SCRATCH_R = row(-152.0F, -24.0F, 20.0F);
   private static final float[][] SHADE_R = row(-128.0F, -40.0F, 12.0F);
   private static final float[][] SHADE_HEAD = row(-8.0F, 0.0F, 0.0F);
   private static final float[][] TENSE_R = row(-22.0F, -12.0F, 6.0F);
   private static final float[][] TENSE_L = row(-22.0F, 12.0F, -6.0F);
   private static final float[][] FLINCH_R = {PoseKeys.under(0.0F), PoseKeys.strike(2.5F, -112.0F, -34.0F, 8.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(9.0F, -100.0F, -30.0F, 6.0F)};
   private static final float[][] FLINCH_L = {PoseKeys.under(0.0F), PoseKeys.strike(2.5F, -112.0F, 34.0F, -8.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(9.0F, -100.0F, 30.0F, -6.0F)};
   private static final float[][] FLINCH_BODY = {PoseKeys.under(0.0F), PoseKeys.strike(2.5F, -11.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(9.0F, -6.0F, 0.0F, 0.0F)};
   private static final float[][] RUN_BODY = row(16.0F, 0.0F, 0.0F);
   private static final float[][] HANDS_UP_R = row(-164.0F, 0.0F, 22.0F);
   private static final float[][] HANDS_UP_L = row(-164.0F, 0.0F, -22.0F);
   private static final float[][] COVER_R = row(-150.0F, -12.0F, 30.0F);
   private static final float[][] COVER_L = row(-150.0F, 12.0F, -30.0F);
   private static final float[][] COVER_HEAD = row(28.0F, 0.0F, 0.0F);
   private static final float[][] FALLEN_R = row(-8.0F, 0.0F, 62.0F);
   private static final float[][] FALLEN_L = row(-8.0F, 0.0F, -62.0F);
   private static final float[][] FALLEN_RL = row(0.0F, 0.0F, 9.0F);
   private static final float[][] FALLEN_LL = row(0.0F, 0.0F, -9.0F);
   private static final float[][] RISE_R = {PoseKeys.key(0.0F, -8.0F, 0.0F, 62.0F), PoseKeys.key(6.0F, -62.0F, -6.0F, 12.0F), PoseKeys.key(13.0F, -30.0F, 0.0F, 6.0F), PoseKeys.under(18.0F)};
   private static final float[][] RISE_L = {PoseKeys.key(0.0F, -8.0F, 0.0F, -62.0F), PoseKeys.key(6.0F, -62.0F, 6.0F, -12.0F), PoseKeys.key(13.0F, -30.0F, 0.0F, -6.0F), PoseKeys.under(18.0F)};
   private static final float[][] RISE_BODY = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(8.0F, 34.0F, 0.0F, 0.0F), PoseKeys.key(14.0F, 14.0F, 0.0F, 0.0F), PoseKeys.under(18.0F)};
   private static final float[][] RISE_RL = {PoseKeys.key(0.0F, 0.0F, 0.0F, 9.0F), PoseKeys.key(7.0F, -58.0F, 0.0F, 4.0F), PoseKeys.under(16.0F)};
   private static final float[][] HUG_R = row(-44.0F, -58.0F, -6.0F);
   private static final float[][] HUG_L = row(-44.0F, 58.0F, 6.0F);

   private CitizenPoser() {
   }

   private static float[][] row(float pitch, float yaw, float roll) {
      return new float[][]{PoseKeys.key(0.0F, pitch, yaw, roll)};
   }

   /** Per-citizen animation memory, kept on the entity (client side only). */
   static final class Anim {
      final float[] w = new float[CitizenState.values().length];
      CitizenState state = CitizenState.IDLE;
      float since;
      float last = Float.NaN;
      int gesture;
      float gw;
   }

   static Anim anim(CitizenEntity e) {
      if (e.clientAnim instanceof Anim a) {
         return a;
      }
      Anim a = new Anim();
      a.state = e.citizenState();
      a.w[a.state.ordinal()] = 1.0F;
      e.clientAnim = a;
      return a;
   }

   /** Exponential approach factor for {@code dt} ticks at {@code rate} per tick. */
   static float approach(float dt, float rate) {
      return 1.0F - (float)Math.exp(-dt * rate);
   }

   /** Pure: the idle gesture of a seed in a gesture window (watch/scratch only early in it). */
   static int gestureFor(int seed, int window, int inWindow, boolean walking) {
      int h = seed * 0x9E3779B1 ^ window * 0x85EBCA6B;
      h ^= h >>> 15;
      h *= 0x2C1B3C6D;
      h ^= h >>> 12;
      int pick = (h >>> 8 & 0xFF) % 9;
      if (walking) {
         // only phone people keep the phone out on the move
         return (seed & 0x30000) == 0 && pick < 5 ? PHONE : NONE;
      }
      return switch (pick) {
         case 3, 4 -> PHONE;
         case 5 -> CROSSED;
         case 6 -> BEHIND;
         case 7 -> inWindow < 50 ? WATCH : NONE;
         case 8 -> inWindow < 36 ? SCRATCH : NONE;
         default -> NONE;
      };
   }

   static void pose(CitizenModel model, CitizenEntity e, float limbSwing, float limbAmount, float age, float headPitch) {
      Anim a = anim(e);
      CitizenState s = e.citizenState();
      float dt = Float.isNaN(a.last) ? 1.0F : Mth.clamp(age - a.last, 0.0F, 4.0F);
      a.last = age;
      if (s != a.state) {
         a.state = s;
         a.since = age;
      }
      float elapsed = age - a.since;
      float rate = switch (s) {
         case STARTLED -> 0.9F;
         case FALLEN, COVER -> 0.55F;
         case FLEEING -> 0.45F;
         default -> 0.22F;
      };
      float k = approach(dt, rate);
      for (int i = 0; i < a.w.length; i++) {
         float target = i == s.ordinal() ? 1.0F : 0.0F;
         a.w[i] += (target - a.w[i]) * k;
      }

      int seed = e.skin();
      boolean calmish = s == CitizenState.IDLE || s == CitizenState.WALKING;
      int window = (int)(age / WINDOW) + (seed & 63);
      int want = calmish ? gestureFor(seed, window, (int)(age % WINDOW), s == CitizenState.WALKING || limbAmount > 0.25F) : NONE;
      if (want != a.gesture) {
         a.gw -= a.gw * approach(dt, 0.3F);
         if (a.gw < 0.03F) {
            a.gesture = want;
            a.gw = 0.0F;
         }
      } else if (a.gesture != NONE) {
         a.gw += (1.0F - a.gw) * approach(dt, 0.18F);
      }

      apply(model, a.w, a.gesture, a.gw, s, elapsed, seed, e.look(), limbSwing, limbAmount, age);
   }

   /**
    * The pose itself, from explicit weights (one per {@link CitizenState} ordinal): the live path
    * passes the smoothed weights, tests and previews pass one-hot weights.
    */
   static void apply(CitizenModel model, float[] w, int gestureId, float gestureWeight, CitizenState s, float elapsed, int seed, CitizenLook look,
      float limbSwing, float limbAmount, float age) {
      PoseRig rig = PoseRig.begin(model, model.crouching, 1.0F, null);
      float idle = w[CitizenState.IDLE.ordinal()] + w[CitizenState.WALKING.ordinal()];
      // breathing / weight shift while calm
      rig.add(model.body, 0.0F, 0.0F, Mth.sin(age * 0.031F + (seed & 15)) * 1.2F * idle);
      if (gestureWeight > 0.01F) {
         gesture(rig, model, gestureId, gestureWeight * Math.min(1.0F, idle + 0.2F), age);
      }

      float walk = w[CitizenState.WALKING.ordinal()] + w[CitizenState.RECOVERING.ordinal()];
      if (walk > 0.01F) {
         // torso counter-twist with the stride
         rig.add(model.body, 0.0F, Mth.sin(limbSwing * 0.6662F) * 5.0F * limbAmount * walk, 0.0F);
      }

      float talk = w[CitizenState.TALKING.ordinal()];
      if (talk > 0.01F) {
         float phase = (seed & 255) * 0.1F;
         float active = Mth.clamp(Mth.sin(age * 0.045F + phase) * 2.0F, 0.0F, 1.0F) * talk;
         if (active > 0.01F) {
            rig.mainArm(row(-34.0F + Mth.sin(age * 0.26F) * 12.0F, -18.0F + Mth.sin(age * 0.17F) * 8.0F, 6.0F), 0.0F, active, false);
         }
         float listen = talk - active * 0.6F;
         float nod = Math.max(0.0F, Mth.sin(age * 0.34F)) * Mth.clamp(Mth.sin(age * 0.07F + phase) * 3.0F, 0.0F, 1.0F);
         rig.add(model.head, nod * 7.0F * listen, 0.0F, Mth.sin(age * 0.05F + phase) * 4.0F * talk);
         rig.offArm(row(-14.0F, 10.0F, -4.0F), 0.0F, talk * 0.6F, false);
      }

      float alert = w[CitizenState.ALERT.ordinal()];
      if (alert > 0.01F) {
         if ((seed & 0x400) != 0) {
            rig.mainArm(SHADE_R, 0.0F, alert, false);
            rig.head(SHADE_HEAD, 0.0F, alert);
         } else {
            rig.mainArm(TENSE_R, 0.0F, alert, false);
            rig.offArm(TENSE_L, 0.0F, alert, false);
            rig.add(model.body, -4.0F * alert, 0.0F, 0.0F);
         }
      }

      float startled = w[CitizenState.STARTLED.ordinal()];
      if (startled > 0.01F) {
         float t = s == CitizenState.STARTLED ? elapsed : 9.0F;
         rig.mainArm(FLINCH_R, t, startled, false);
         rig.offArm(FLINCH_L, t, startled, false);
         rig.body(FLINCH_BODY, t, startled, false);
      }

      float flee = w[CitizenState.FLEEING.ordinal()];
      if (flee > 0.01F) {
         rig.body(RUN_BODY, 0.0F, flee, false);
         if ((seed & 0x1800) == 0x1800) {
            // hands over the head, bouncing with the stride
            float bob = Mth.cos(limbSwing * 0.6662F) * 8.0F * limbAmount;
            rig.mainArm(HANDS_UP_R, 0.0F, flee, false);
            rig.offArm(HANDS_UP_L, 0.0F, flee, false);
            rig.add(model.rightArm, bob * flee, 0.0F, 0.0F);
            rig.add(model.leftArm, -bob * flee, 0.0F, 0.0F);
         } else {
            // pumping arms: bent forward, a wider swing than the walk
            float swing = Mth.cos(limbSwing * 0.6662F) * 24.0F * limbAmount * flee;
            rig.add(model.rightArm, -18.0F * flee - swing, 0.0F, 6.0F * flee);
            rig.add(model.leftArm, -18.0F * flee + swing, 0.0F, -6.0F * flee);
         }
      }

      float cover = w[CitizenState.COVER.ordinal()];
      if (cover > 0.01F) {
         float tremble = Mth.sin(age * 2.7F + seed) * 1.2F * cover;
         rig.mainArm(COVER_R, 0.0F, cover, false);
         rig.offArm(COVER_L, 0.0F, cover, false);
         rig.head(COVER_HEAD, 0.0F, cover);
         rig.add(model.rightArm, tremble, 0.0F, tremble);
         rig.add(model.leftArm, -tremble, 0.0F, -tremble);
         rig.add(model.body, 0.0F, 0.0F, tremble * 0.5F);
      }

      float fallen = w[CitizenState.FALLEN.ordinal()];
      if (fallen > 0.01F) {
         rig.mainArm(FALLEN_R, 0.0F, fallen, false);
         rig.offArm(FALLEN_L, 0.0F, fallen, false);
         rig.mainLeg(FALLEN_RL, 0.0F, fallen, false);
         rig.offLeg(FALLEN_LL, 0.0F, fallen, false);
         // the head rests: no look pitch / yaw while lying
         model.head.xRot *= 1.0F - fallen;
         model.head.yRot *= 1.0F - fallen;
      }

      float rise = w[CitizenState.GETTING_UP.ordinal()];
      if (rise > 0.01F) {
         float t = s == CitizenState.GETTING_UP ? elapsed : 18.0F;
         rig.mainArm(RISE_R, t, rise, false);
         rig.offArm(RISE_L, t, rise, false);
         rig.body(RISE_BODY, t, rise, false);
         rig.mainLeg(RISE_RL, t, rise, false);
      }

      float recover = w[CitizenState.RECOVERING.ordinal()];
      if (recover > 0.01F) {
         float hug = (seed & 0x2000) != 0 ? recover * (s == CitizenState.RECOVERING ? Mth.clamp(1.0F - (elapsed - 140.0F) / 60.0F, 0.0F, 1.0F) : 1.0F) : 0.0F;
         if (hug > 0.01F) {
            rig.mainArm(HUG_R, 0.0F, hug, false);
            rig.offArm(HUG_L, 0.0F, hug, false);
         }
         // wide, wary look-around
         rig.add(model.head, 0.0F, Mth.sin(age * 0.07F + (seed & 31)) * 28.0F * recover, 0.0F);
      }

      rig.finish();
      ponytail(model, look, limbSwing, limbAmount, age, flee);
   }

   private static void gesture(PoseRig rig, CitizenModel model, int g, float wt, float age) {
      switch (g) {
         case PHONE -> {
            rig.mainArm(PHONE_R, 0.0F, wt, false);
            rig.head(PHONE_HEAD, 0.0F, wt);
         }
         case CROSSED -> {
            rig.mainArm(CROSSED_R, 0.0F, wt, false);
            rig.offArm(CROSSED_L, 0.0F, wt, false);
         }
         case BEHIND -> {
            rig.mainArm(BEHIND_R, 0.0F, wt, false);
            rig.offArm(BEHIND_L, 0.0F, wt, false);
         }
         case WATCH -> {
            rig.offArm(WATCH_L, 0.0F, wt, false);
            rig.head(WATCH_HEAD, 0.0F, wt);
         }
         case SCRATCH -> {
            rig.mainArm(SCRATCH_R, 0.0F, wt, false);
            rig.add(model.rightArm, 0.0F, 0.0F, Mth.sin(age * 1.3F) * 5.0F * wt);
         }
         default -> {
         }
      }
   }

   /** The ponytail hangs from the head: swings with the stride, flies back when running. */
   private static void ponytail(CitizenModel model, CitizenLook look, float limbSwing, float limbAmount, float age, float flee) {
      if (look.hair() != CitizenLook.Hair.PONYTAIL) {
         return;
      }
      model.ponytail.xRot = 0.35F + limbAmount * (0.5F + flee * 0.5F) - model.head.xRot * 0.6F + Mth.sin(limbSwing * 1.3324F) * 0.12F * limbAmount;
      model.ponytail.zRot = Mth.sin(age * 0.09F) * 0.05F + Mth.sin(limbSwing * 0.6662F) * 0.18F * limbAmount;
   }
}
