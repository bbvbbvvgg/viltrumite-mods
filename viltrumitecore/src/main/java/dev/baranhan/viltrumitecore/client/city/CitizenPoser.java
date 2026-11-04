package dev.baranhan.viltrumitecore.client.city;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import dev.baranhan.viltrumitecore.city.citizen.CitizenState;
import dev.baranhan.viltrumitecore.city.life.PhoneRoutine;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.client.city.life.CityLifeVoices;
import net.minecraft.util.Mth;

/**
 * Citizen body language on the shared {@link PoseRig} (animation-system: vanilla body, key
 * tables, {@code finish()} after the layer). Every {@link CitizenState} owns a pose layer whose
 * weight eases in and out in real time (exponential smoothing on the age clock), so a citizen
 * flinches, runs, ducks and gets up without snapping. Calm citizens add idle gestures picked
 * from their seed: crossed arms, hands behind the back, a look at the watch, a head scratch;
 * talkers gesture and nod. Phone use (#151) follows the shared {@link PhoneRoutine} timeline and owns
 * the main arm and the head while it lasts ({@link #phone}); {@link CitizenPhoneLayer} draws the phone
 * in that hand. Pure client code: the state is the synced byte.
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
   // phone (#151): out of the pocket at the hip, up in front of the chest, to the ear and back
   static final float[] HOLD = {-62.0F, -28.0F, 4.0F};
   /** Arm along the head, the hand at the ear (pivot lowered 2 px as if the elbow folded; solved for the phone at the ear). */
   static final float[] EAR = {-174.0F, -48.0F, -6.0F, 0.0F, 2.0F, 0.0F};
   private static final float[][] DRAW_R = {PoseKeys.under(0.0F), PoseKeys.key(5.0F, 14.0F, -2.0F, 12.0F), PoseKeys.key(8.0F, 6.0F, -6.0F, 10.0F),
      PoseKeys.key(PhoneRoutine.DRAW, HOLD[0], HOLD[1], HOLD[2])};
   private static final float[][] STOW_R = {PoseKeys.key(0.0F, HOLD[0], HOLD[1], HOLD[2]), PoseKeys.key(6.0F, 6.0F, -6.0F, 10.0F),
      PoseKeys.key(9.0F, 14.0F, -2.0F, 12.0F), PoseKeys.under(PhoneRoutine.STOW)};
   private static final float[][] TO_EAR_R = {PoseKeys.key(0.0F, HOLD[0], HOLD[1], HOLD[2]), PoseKeys.key(4.0F, -112.0F, -40.0F, 2.0F, 0.0F, 1.0F, 0.0F),
      PoseKeys.key(PhoneRoutine.TO_EAR, EAR[0], EAR[1], EAR[2], EAR[3], EAR[4], EAR[5])};
   private static final float[][] FROM_EAR_R = {PoseKeys.key(0.0F, EAR[0], EAR[1], EAR[2], EAR[3], EAR[4], EAR[5]),
      PoseKeys.key(6.0F, -112.0F, -40.0F, 2.0F, 0.0F, 1.0F, 0.0F), PoseKeys.key(PhoneRoutine.FROM_EAR, HOLD[0], HOLD[1], HOLD[2])};
   private static final float[][] TALK_R = {PoseKeys.key(0.0F, EAR[0], EAR[1], EAR[2], EAR[3], EAR[4], EAR[5])};
   private static final float[][] TAP_L = row(-50.0F, 34.0F, -8.0F);
   private static final float[][] HIP_L = row(-12.0F, 0.0F, -38.0F);
   private static final float[][] EXPLAIN_L = row(-38.0F, 22.0F, -8.0F);
   // peril (issue #138): both hands up on the ledge while hanging
   private static final float[][] HANG_R = row(-174.0F, 0.0F, 9.0F);
   private static final float[][] HANG_L = row(-174.0F, 0.0F, -9.0F);

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
      /** Smoothed "my line is playing" of a voiced chat (issue #136), -1 while the chat is silent. */
      float speak = -1.0F;
      /** Age since when the person has been calm enough for its phone (NaN while not), see {@link PhoneRoutine#calm}. */
      float calmSince = Float.NaN;
      /** The phone moment of this frame, the one being faded out after an alarm, and the pose weight. */
      PhoneRoutine.Moment phone = PhoneRoutine.Moment.NONE;
      PhoneRoutine.Moment phoneLast = PhoneRoutine.Moment.NONE;
      float phoneW;
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
         // the phone is no idle mime any more: PhoneRoutine owns it (with the real phone in the hand)
         return NONE;
      }
      return switch (pick) {
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
         case TEETER, HANGING, FALLING -> 0.6F;
         default -> 0.22F;
      };
      float k = approach(dt, rate);
      for (int i = 0; i < a.w.length; i++) {
         float target = i == s.ordinal() ? 1.0F : 0.0F;
         a.w[i] += (target - a.w[i]) * k;
      }

      int seed = e.skin();
      boolean calmish = s == CitizenState.IDLE || s == CitizenState.WALKING;
      phone(a, e, calmish, limbAmount, age, dt, model.partialTick);
      int window = (int)(age / WINDOW) + (seed & 63);
      int want = calmish && !a.phone.active() ? gestureFor(seed, window, (int)(age % WINDOW), s == CitizenState.WALKING || limbAmount > 0.25F) : NONE;
      if (want != a.gesture) {
         a.gw -= a.gw * approach(dt, 0.3F);
         if (a.gw < 0.03F) {
            a.gesture = want;
            a.gw = 0.0F;
         }
      } else if (a.gesture != NONE) {
         a.gw += (1.0F - a.gw) * approach(dt, 0.18F);
      }

      float voiced = s == CitizenState.TALKING ? CityLifeVoices.speaking(e) : -1.0F;
      if (voiced < 0.0F) {
         a.speak = -1.0F;
      } else {
         a.speak = a.speak < 0.0F ? voiced : a.speak + (voiced - a.speak) * approach(dt, 0.25F);
      }

      apply(model, a.w, a.gesture, a.gw, s, elapsed, seed, e.look(), limbSwing, limbAmount, age, a.speak, a.phoneW > 0.001F ? a.phoneLast : PhoneRoutine.Moment.NONE,
         a.phoneW);
   }

   /** Most phone moments shown at once around the camera (the rest keep their gestures; far ones are never posed). */
   static final int MAX_PHONES = 24;
   /** Phone use is only posed and drawn this close to the camera (blocks); farther away it would be a few pixels. */
   static final double PHONE_LOD = 40.0;
   private static long phoneFrame = Long.MIN_VALUE;
   private static int phonesThisFrame;

   /**
    * The phone moment of this frame: the shared timeline, skipped while not calm and for a moment that began
    * before the person calmed down (an alarm puts the phone away at once: the pose fades in a few ticks, the
    * phone itself goes with the weight). Bounded by {@link #PHONE_LOD} and {@link #MAX_PHONES} per frame.
    */
   static void phone(Anim a, CitizenEntity e, boolean calm, float limbAmount, float age, float dt, float partialTick) {
      if (!calm) {
         a.calmSince = Float.NaN;
      } else if (Float.isNaN(a.calmSince)) {
         a.calmSince = age;
      }
      PhoneRoutine.Moment m = PhoneRoutine.Moment.NONE;
      if (calm && limbAmount < 0.8F && e.level() != null && near(e)) {
         m = PhoneRoutine.calm(PhoneRoutine.at(e.skin(), e.level().getGameTime(), partialTick), age - a.calmSince);
         if (m.active()) {
            long frame = e.level().getGameTime();
            if (frame != phoneFrame) {
               phoneFrame = frame;
               phonesThisFrame = 0;
            }
            if (a.phone.active() || phonesThisFrame < MAX_PHONES) {
               phonesThisFrame++;
            } else {
               m = PhoneRoutine.Moment.NONE;
            }
         }
      }
      a.phone = m;
      if (m.active()) {
         a.phoneLast = m;
         a.phoneW += (1.0F - a.phoneW) * approach(dt, 0.5F);
      } else {
         // alarm, end of the moment, out of range: the arm leaves the phone pose quickly
         a.phoneW -= a.phoneW * approach(dt, calm ? 0.5F : 0.8F);
         if (a.phoneW < 0.01F) {
            a.phoneW = 0.0F;
            a.phoneLast = PhoneRoutine.Moment.NONE;
         }
      }
   }

   private static boolean near(CitizenEntity e) {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.gameRenderer == null) {
         return true;
      }
      return mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(e.position()) < PHONE_LOD * PHONE_LOD;
   }

   /** The phone is drawn this frame: the current moment has it in the hand (an alarm takes it away at once). */
   static boolean phoneInHand(Anim a) {
      return a.phone.active() && a.phone.inHand();
   }

   /** 0 = reading grip (screen to the eyes), 1 = at the ear (screen to the cheek); eased across TO_EAR / FROM_EAR. */
   static float earGrip(PhoneRoutine.Moment m) {
      float k = switch (m.phase()) {
         case TO_EAR -> Mth.clamp((m.t() - 3.0F) / (PhoneRoutine.TO_EAR - 3.0F), 0.0F, 1.0F);
         case TALK -> 1.0F;
         case FROM_EAR -> 1.0F - Mth.clamp(m.t() / (PhoneRoutine.FROM_EAR - 3.0F), 0.0F, 1.0F);
         default -> 0.0F;
      };
      return k * k * (3.0F - 2.0F * k);
   }

   static void apply(CitizenModel model, float[] w, int gestureId, float gestureWeight, CitizenState s, float elapsed, int seed, CitizenLook look,
      float limbSwing, float limbAmount, float age) {
      apply(model, w, gestureId, gestureWeight, s, elapsed, seed, look, limbSwing, limbAmount, age, -1.0F);
   }

   /**
    * The pose itself, from explicit weights (one per {@link CitizenState} ordinal): the live path
    * passes the smoothed weights, tests and previews pass one-hot weights.
    */
   static void apply(CitizenModel model, float[] w, int gestureId, float gestureWeight, CitizenState s, float elapsed, int seed, CitizenLook look,
      float limbSwing, float limbAmount, float age, float speak) {
      apply(model, w, gestureId, gestureWeight, s, elapsed, seed, look, limbSwing, limbAmount, age, speak, PhoneRoutine.Moment.NONE, 0.0F);
   }

   static void apply(CitizenModel model, float[] w, int gestureId, float gestureWeight, CitizenState s, float elapsed, int seed, CitizenLook look,
      float limbSwing, float limbAmount, float age, float speak, PhoneRoutine.Moment phone, float phoneWeight) {
      PoseRig rig = PoseRig.begin(model, model.crouching, 1.0F, null);
      float idle = w[CitizenState.IDLE.ordinal()] + w[CitizenState.WALKING.ordinal()];
      // breathing / weight shift while calm
      rig.add(model.body, 0.0F, 0.0F, Mth.sin(age * 0.031F + (seed & 15)) * 1.2F * idle);
      if (gestureWeight > 0.01F) {
         gesture(rig, model, gestureId, gestureWeight * Math.min(1.0F, idle + 0.2F), age);
      }
      if (phoneWeight > 0.01F && phone.active()) {
         phonePose(rig, model, phone, phoneWeight * Math.min(1.0F, idle + 0.2F), age, seed);
      }

      float walk = w[CitizenState.WALKING.ordinal()] + w[CitizenState.RECOVERING.ordinal()];
      if (walk > 0.01F) {
         // torso counter-twist with the stride
         rig.add(model.body, 0.0F, Mth.sin(limbSwing * 0.6662F) * 5.0F * limbAmount * walk, 0.0F);
      }

      float talk = w[CitizenState.TALKING.ordinal()];
      if (talk > 0.01F) {
         float phase = (seed & 255) * 0.1F;
         float active;
         float nodGate;
         if (speak >= 0.0F) {
            // voiced chat (client/city/life/CityLifeVoices): the one whose line plays gestures with emphasis, the
            // other one listens and nods; they swap with the lines
            active = speak * (0.55F + 0.45F * Mth.clamp(Mth.sin(age * 0.11F + phase) * 1.6F + 0.6F, 0.0F, 1.0F)) * talk;
            nodGate = 1.0F - speak;
         } else {
            active = Mth.clamp(Mth.sin(age * 0.045F + phase) * 2.0F, 0.0F, 1.0F) * talk;
            nodGate = Mth.clamp(Mth.sin(age * 0.07F + phase) * 3.0F, 0.0F, 1.0F);
         }
         if (active > 0.01F) {
            rig.mainArm(row(-34.0F + Mth.sin(age * 0.26F) * 12.0F, -18.0F + Mth.sin(age * 0.17F) * 8.0F, 6.0F), 0.0F, active, false);
         }
         float listen = talk - active * 0.6F;
         float nod = Math.max(0.0F, Mth.sin(age * 0.34F)) * nodGate;
         float bob = speak > 0.0F ? Mth.sin(age * 0.58F + phase) * 2.5F * speak * talk : 0.0F;
         rig.add(model.head, nod * 7.0F * listen + bob, 0.0F, Mth.sin(age * 0.05F + phase) * 4.0F * talk);
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

      peril(rig, model, w, seed, age);
      rig.finish();
      float wind = w[CitizenState.FALLING.ordinal()] + w[CitizenState.HANGING.ordinal()] * 0.4F;
      ponytail(model, look, limbSwing, limbAmount, age, Math.min(1.0F, flee + wind));
   }

   /**
    * Issue #138: on a failing floor the arms windmill, the torso rocks and the eyes go down to
    * the drop; hanging from a ledge both hands are up on the edge and the legs kick for a hold;
    * falling, arms and legs flail (the renderer adds the tumble).
    */
   private static void peril(PoseRig rig, CitizenModel model, float[] w, int seed, float age) {
      float ph = (seed & 31) * 0.2F;
      float teeter = w[CitizenState.TEETER.ordinal()];
      if (teeter > 0.01F) {
         float sw = age * 0.55F + ph;
         // arms flung out to the sides, circling for balance
         rig.add(model.rightArm, (-45.0F + Mth.sin(sw) * 60.0F) * teeter, 0.0F, (78.0F + Mth.cos(sw) * 28.0F) * teeter);
         rig.add(model.leftArm, (-45.0F + Mth.sin(sw + 2.4F) * 60.0F) * teeter, 0.0F, (-78.0F - Mth.cos(sw + 2.4F) * 28.0F) * teeter);
         rig.add(model.body, Mth.sin(age * 0.33F + ph) * 9.0F * teeter, 0.0F, Mth.sin(age * 0.21F + ph) * 8.0F * teeter);
         rig.add(model.head, 22.0F * teeter, 0.0F, 0.0F);
         rig.add(model.rightLeg, 0.0F, 0.0F, 7.0F * teeter);
         rig.add(model.leftLeg, 0.0F, 0.0F, -7.0F * teeter);
      }

      float hang = w[CitizenState.HANGING.ordinal()];
      if (hang > 0.01F) {
         float kick = Mth.sin(age * 0.42F + ph) * 22.0F * hang;
         float strain = Mth.sin(age * 1.7F + ph) * 1.5F * hang;
         rig.mainArm(HANG_R, 0.0F, hang, false);
         rig.offArm(HANG_L, 0.0F, hang, false);
         rig.add(model.rightArm, strain, 0.0F, 0.0F);
         rig.add(model.leftArm, -strain, 0.0F, 0.0F);
         rig.add(model.rightLeg, kick, 0.0F, 4.0F * hang);
         rig.add(model.leftLeg, -kick, 0.0F, -4.0F * hang);
         rig.add(model.body, 5.0F * hang, 0.0F, 0.0F);
         // up at the hands, now and then a terrified glance down
         model.head.yRot *= 1.0F - hang * 0.7F;
         rig.add(model.head, (-26.0F + Math.max(0.0F, Mth.sin(age * 0.05F + ph)) * 66.0F) * hang, 0.0F, 0.0F);
      }

      float fall = w[CitizenState.FALLING.ordinal()];
      if (fall > 0.01F) {
         float f = age * 0.8F + ph;
         rig.add(model.rightArm, (-140.0F + Mth.sin(f) * 50.0F) * fall, 0.0F, (55.0F + Mth.cos(f * 1.3F) * 30.0F) * fall);
         rig.add(model.leftArm, (-140.0F + Mth.sin(f + 2.1F) * 50.0F) * fall, 0.0F, (-55.0F - Mth.cos(f * 1.3F + 2.1F) * 30.0F) * fall);
         rig.add(model.rightLeg, Mth.sin(f * 0.9F) * 38.0F * fall, 0.0F, 10.0F * fall);
         rig.add(model.leftLeg, -Mth.sin(f * 0.9F) * 38.0F * fall, 0.0F, -10.0F * fall);
         model.head.yRot *= 1.0F - fall * 0.5F;
         rig.add(model.head, -18.0F * fall, 0.0F, 0.0F);
      }
   }

   /**
    * #151: the phone moment on the main arm, the head and the free hand. Reading: the phone in front of the chest,
    * eyes down to it, the thumb scrolls; dialling: the free hand taps; a call: up to the ear, the head tilts to it,
    * the free hand talks along, rests on the hip or hangs; a slow weight shift while standing.
    */
   static void phonePose(PoseRig rig, CitizenModel model, PhoneRoutine.Moment m, float wt, float age, int seed) {
      int v = m.variant();
      float t = m.t();
      float lift = (v & 3) * 2.5F - 3.0F;
      switch (m.phase()) {
         case DRAW -> {
            rig.mainArm(DRAW_R, t, wt, false);
            rig.head(row(22.0F * Mth.clamp((t - 6.0F) / 8.0F, 0.0F, 1.0F), -6.0F, 0.0F), 0.0F, wt);
         }
         case STOW -> {
            rig.mainArm(STOW_R, t, wt, false);
            rig.head(row(22.0F * Mth.clamp(1.0F - t / 8.0F, 0.0F, 1.0F), -6.0F, 0.0F), 0.0F, wt);
         }
         case LOOK, DIAL, HANG_UP -> {
            rig.mainArm(row(HOLD[0] + lift, HOLD[1], HOLD[2]), 0.0F, wt, false);
            // the thumb flicks the feed now and then; dialling taps faster
            float flick = m.phase() == PhoneRoutine.Phase.LOOK ? Math.max(0.0F, Mth.sin(t * 0.33F + v)) * Math.max(0.0F, Mth.sin(t * 0.05F + v * 1.7F)) * 3.0F
               : Math.max(0.0F, Mth.sin(t * 1.4F)) * 1.6F;
            rig.add(model.rightArm, -flick * wt, 0.0F, 0.0F);
            float down = 24.0F + (v >> 1 & 3) * 3.0F + Mth.sin(t * 0.04F + v) * 3.0F;
            rig.head(row(down, -6.0F + Mth.sin(t * 0.03F + v) * 4.0F, 0.0F), 0.0F, wt);
            if (m.phase() == PhoneRoutine.Phase.DIAL || (v & 4) != 0 && m.phase() == PhoneRoutine.Phase.LOOK) {
               rig.offArm(TAP_L, 0.0F, wt, false);
               rig.add(model.leftArm, -Math.max(0.0F, Mth.sin(t * 1.4F + 1.2F)) * 4.0F * wt, 0.0F, 0.0F);
            }
         }
         case TO_EAR -> {
            rig.mainArm(TO_EAR_R, t, wt, false);
            rig.head(row(22.0F * (1.0F - t / PhoneRoutine.TO_EAR), -6.0F, 8.0F * t / PhoneRoutine.TO_EAR), 0.0F, wt);
         }
         case FROM_EAR -> {
            rig.mainArm(FROM_EAR_R, t, wt, false);
            rig.head(row(22.0F * t / PhoneRoutine.FROM_EAR, -6.0F, 8.0F * (1.0F - t / PhoneRoutine.FROM_EAR)), 0.0F, wt);
         }
         case TALK -> {
            rig.mainArm(TALK_R, 0.0F, wt, false);
            // the head leans into the phone and stays with it (no free look away from the hand)
            model.head.yRot *= 1.0F - 0.75F * wt;
            float nod = Math.max(0.0F, Mth.sin(t * 0.21F + v)) * Math.max(0.0F, Mth.sin(t * 0.045F)) * 6.0F;
            rig.add(model.head, (6.0F + nod) * wt, 0.0F, 8.0F * wt);
            switch (v >> 1 & 3) {
               case 0 -> {
                  // talking with the free hand
                  float g = Mth.clamp(Mth.sin(t * 0.06F + v) * 2.0F, 0.0F, 1.0F) * wt;
                  if (g > 0.01F) {
                     rig.offArm(EXPLAIN_L, 0.0F, g, false);
                     rig.add(model.leftArm, Mth.sin(t * 0.27F) * 10.0F * g, Mth.sin(t * 0.19F) * 6.0F * g, 0.0F);
                  }
               }
               case 1 -> rig.offArm(HIP_L, 0.0F, wt, false);
               default -> rig.add(model.leftArm, Mth.sin(t * 0.05F) * 3.0F * wt, 0.0F, 0.0F);
            }
            // weight from one foot to the other, a slow turn while listening
            rig.add(model.body, 0.0F, Mth.sin(t * 0.017F + v) * 6.0F * wt, Mth.sin(t * 0.023F + seed) * 1.8F * wt);
         }
         default -> {
         }
      }
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
