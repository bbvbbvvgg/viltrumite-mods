package dev.baranhan.viltrumitecore.client.ironman;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;
import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.strike;
import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.under;

import dev.baranhan.viltrumitecore.client.anim.pose.ArmAim;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.client.ironman.anim.IronManAnimation;
import dev.baranhan.viltrumitecore.client.ironman.anim.IronManAnimation.Layer;
import dev.baranhan.viltrumitecore.client.ironman.anim.IronManAnimation.Signature;
import dev.baranhan.viltrumitecore.client.ironman.anim.IronManAnimation.View;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkState;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * Iron Man third-person poses at setupAnim TAIL (animation-system §3, §6),
 * after the flight pose mixins. Locomotion: hover (palms down, arms out,
 * sway), glide (arms spread) and the heavy-landing kneel. Combat (PR 19
 * iteration 2, Regulus-style): keyed timelines on the shared {@link PoseRig}
 * in slerp mode (shortest-arc blends, never a twist through a third
 * orientation) for the Unibeam, the forehand / backhand blade slashes, the
 * hammer raise-and-smash and its wind-up, the nano forming / dissolving
 * flourish, the shield guard and the signatures; then {@link ArmAim} points
 * the arms exactly along the look for the repulsor stances (each palm holds
 * its stance after a shot, recoil kick along the head's up axis), the
 * forearm missile launchers, the blade lunge and the aimed signatures.
 * Aiming works in model space from the head rotation, so it is also right in
 * cruise flight. Timing and weights come from {@link IronManAnimation}.
 * Also hides Tony's hat layer once the helmet starts to close.
 */
public final class IronManPoser {
   private static final WeakHashMap<LivingEntity, Weights> WEIGHTS = new WeakHashMap<>();
   /** Arms-out weight while the suit parts fly and wrap (0..1). */
   private static final WeakHashMap<LivingEntity, float[]> EQUIP = new WeakHashMap<>();
   /** Kneel geometry (model pixels): body lean and hip height above the ground. */
   private static final float KNEEL_LEAN = 0.7F;
   private static final float KNEEL_HIP = 4.0F;

   // Unibeam: chest out, arms swept back and down (charge draws them further back).
   private static final float[][] UNIBEAM_BODY = {key(0, -7.0F, 0.0F, 0.0F)};
   private static final float[][] UNIBEAM_RIGHT = {key(0, 28.0F, 0.0F, 30.0F)};
   private static final float[][] UNIBEAM_LEFT = mirror(UNIBEAM_RIGHT);
   private static final float[][] OVERHEAT_BODY = {key(0, 9.0F, 0.0F, 0.0F)};
   private static final float[][] OVERHEAT_RIGHT = {key(0, -12.0F, 0.0F, 9.0F)};
   private static final float[][] OVERHEAT_LEFT = mirror(OVERHEAT_RIGHT);

   // Blade forehand: cocked out right, cut across to the left, follow through, recover (8 t + tail).
   private static final float[][] SLASH_RIGHT = {
      under(0), key(2, -100.0F, 68.0F, 22.0F), strike(4, -86.0F, -38.0F, -10.0F, 0.0F, 0.0F, 0.0F), key(6, -80.0F, -62.0F, -16.0F), key(9, -68.0F, -44.0F, -10.0F), under(14)
   };
   private static final float[][] SLASH_LEFT = {under(0), key(2, -18.0F, 0.0F, -14.0F), key(5, 24.0F, 0.0F, -20.0F), key(9, 12.0F, 0.0F, -12.0F), under(14)};
   private static final float[][] SLASH_BODY = {under(0), key(2, 0.0F, 16.0F, 0.0F), key(5, 2.0F, -20.0F, 0.0F), key(9, 0.0F, -10.0F, 0.0F), under(14)};
   // Blade backhand: cocked across the chest, cut out to the right.
   private static final float[][] BACKHAND_RIGHT = {
      under(0), key(2, -96.0F, -58.0F, -26.0F), strike(4, -86.0F, 42.0F, 14.0F, 0.0F, 0.0F, 0.0F), key(6, -80.0F, 70.0F, 20.0F), key(9, -68.0F, 44.0F, 10.0F), under(14)
   };
   private static final float[][] BACKHAND_LEFT = {under(0), key(2, -34.0F, 18.0F, -4.0F), key(5, 22.0F, 0.0F, -18.0F), key(9, 10.0F, 0.0F, -10.0F), under(14)};
   private static final float[][] BACKHAND_BODY = {under(0), key(2, 0.0F, -16.0F, 0.0F), key(5, 2.0F, 18.0F, 0.0F), key(9, 0.0F, 9.0F, 0.0F), under(14)};
   // Hammer: raise high behind the head, hold a beat, smash down in front, settle (14 t + tail).
   private static final float[][] HAMMER_RIGHT = {
      under(0), key(4, -165.0F, 10.0F, 10.0F), key(6, -172.0F, 6.0F, 8.0F), strike(8, -36.0F, -2.0F, 4.0F, 0.0F, 0.0F, 0.0F), key(11, -36.0F, 0.0F, 4.0F), key(14, -30.0F, 0.0F, 3.0F), under(20)
   };
   private static final float[][] HAMMER_LEFT = {under(0), key(4, -40.0F, 0.0F, -24.0F), key(8, 28.0F, 0.0F, -20.0F), key(14, 14.0F, 0.0F, -12.0F), under(20)};
   private static final float[][] HAMMER_BODY = {under(0), key(4, -8.0F, 0.0F, 0.0F), key(6, -10.0F, 0.0F, 0.0F), key(8, 22.0F, 0.0F, 0.0F), key(12, 15.0F, 0.0F, 0.0F), under(20)};
   private static final float[][] HAMMER_HEAD = {under(0), key(6, -6.0F, 0.0F, 0.0F), key(8, 10.0F, 0.0F, 0.0F), under(20)};
   // Hammer charge: drawn back over the right shoulder, left hand forward.
   private static final float[][] WINDUP_RIGHT = {key(0, -158.0F, 34.0F, 18.0F)};
   private static final float[][] WINDUP_LEFT = {key(0, -55.0F, -24.0F, -8.0F)};
   private static final float[][] WINDUP_BODY = {key(0, -6.0F, 14.0F, 0.0F)};
   // Nano forming: fist raised before the eyes while the weapon grows; dissolving: a flick down.
   private static final float[][] FORM_RIGHT = {under(0), key(3, -55.0F, -20.0F, 10.0F), key(6, -70.0F, -14.0F, 6.0F), key(8, -62.0F, -10.0F, 4.0F), under(14)};
   private static final float[][] DISSOLVE_RIGHT = {under(0), key(3, -45.0F, -12.0F, 4.0F), key(5, -24.0F, 4.0F, 12.0F), under(11)};
   // Shield guard: left forearm across the chest, plate forward (head follows).
   private static final float[][] GUARD_LEFT = {key(0, -83.0F, 64.0F, -6.0F, 0.0F, 0.0F, -3.5F)};
   // Signatures.
   private static final float[][] LASER_LEFT = {key(0, -28.0F, 14.0F, 0.0F)};
   private static final float[][] GUN_RIGHT = {key(0, -69.0F, -17.0F, 0.0F)};
   private static final float[][] GUN_LEFT = {key(0, -57.0F, 26.0F, 0.0F)};
   private static final float[][] SLAM_RIGHT = {key(0, -166.0F, 0.0F, -9.0F)};
   private static final float[][] SLAM_LEFT = mirror(SLAM_RIGHT);
   private static final float[][] LUNGE_BODY = {key(0, 18.0F, 0.0F, 0.0F)};
   private static final float[][] LUNGE_LEFT = {key(0, 34.0F, 0.0F, -18.0F)};
   private static final float[][] STANCE_BODY_RIGHT = {key(0, 0.0F, -9.0F, 0.0F)};
   private static final float[][] STANCE_BODY_LEFT = {key(0, 0.0F, 9.0F, 0.0F)};

   private IronManPoser() {
   }

   /** Left-arm rows of right-arm keys: yaw, roll and x negated (PoseRig mirrors only by the rig side). */
   static float[][] mirror(float[][] keys) {
      float[][] out = new float[keys.length][];
      for (int i = 0; i < keys.length; i++) {
         float[] row = keys[i].clone();
         if (!Float.isNaN(row[1])) {
            row[2] = -row[2];
            row[3] = -row[3];
            row[4] = -row[4];
         }

         out[i] = row;
      }

      return out;
   }

   public static void poseThirdPerson(PlayerModel<?> model, LivingEntity entity) {
      HeroPublicSnapshot snapshot = IronManView.of(entity);
      if (snapshot == null) {
         WEIGHTS.remove(entity);
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      boolean localFirstPerson = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
      if (localFirstPerson && !ShaderCompat.isShadowPass()) {
         return;
      }

      float partialTick = minecraft.getFrameTime();
      if (IronManView.helmet(IronManPartsProvider.helmetFrame(entity, snapshot, partialTick))) {
         model.hat.visible = false;
      }

      // Stage 3: hand to the face while the helmet folds / unfolds (spec §10).
      float gesture = HelmetAnim.gesture(entity, partialTick);

      ThrusterFlames.Mode mode = entity instanceof AbstractClientPlayer player ? ThrusterFlames.mode(player, snapshot) : ThrusterFlames.Mode.NONE;
      Weights w = WEIGHTS.computeIfAbsent(entity, e -> new Weights());
      w.advance(mode == ThrusterFlames.Mode.HOVER, IronManView.glide(snapshot) && !entity.onGround(), IronManView.heavyLanding(snapshot), 12.0F);
      MarkState mark = MarkState.of(snapshot);
      float[] equip = EQUIP.computeIfAbsent(entity, e -> new float[]{0.0F, Float.NaN});
      boolean arming = mark.markOn() && (mark.equipping() || mark.equipPhase() == IronManFlags.EQUIP_PARTIAL);
      // Real-time easing: the pose runs once per render pass (shadow pass too), so a per-call lerp would race.
      float seconds = dev.baranhan.viltrumitecore.client.anim.RealTime.seconds();
      float dt = Float.isNaN(equip[1]) ? 0.0F : Math.min(0.1F, seconds - equip[1]);
      equip[1] = seconds;
      equip[0] = Mth.lerp(1.0F - (float)Math.exp(-10.0F * dt), equip[0], arming ? 1.0F : 0.0F);

      float time = entity.tickCount + partialTick;
      if (w.hover > 0.001F) {
         hover(model, w.hover, time);
      }

      if (w.glide > 0.001F) {
         glide(model, w.glide);
      }

      if (w.kneel > 0.001F) {
         kneel(model, w.kneel);
      }

      if (equip[0] > 0.001F) {
         // Arms slightly out while the parts fly and wrap, like Mark 42 (spec §12.4).
         rotate(model.rightArm, equip[0], 0.0F, 0.0F, 0.22F);
         rotate(model.leftArm, equip[0], 0.0F, 0.0F, -0.22F);
      }

      IronManAnimation.State state = IronManAnimation.of(entity);
      if (state != null) {
         combat(model, entity, state, partialTick, time);
      }

      if (gesture > 0.001F) {
         // Left hand up to the faceplate: aimed from the head axes, so it follows any head turn.
         Vector3f dir = new Vector3f(ArmAim.look(model.head)).mul(0.55F).add(ArmAim.up(model.head).mul(0.62F)).add(ArmAim.right(model.head).mul(0.34F)).normalize();
         ArmAim.aim(model.leftArm, dir, 0.0F, gesture);
      }

      float rest = dev.baranhan.viltrumitecore.client.ironman.mark.SuitEntryDriver.restWeight(entity, partialTick);
      if (rest > 0.001F) {
         // Walking into the empty suit: straight rest pose, so the closing plates meet the limbs (spec §12.6).
         restPose(model, rest);
      }

      PoseRig.copyLayers(model);
   }

   private static void restPose(PlayerModel<?> model, float k) {
      for (net.minecraft.client.model.geom.ModelPart part : new net.minecraft.client.model.geom.ModelPart[]{
         model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
         part.xRot = Mth.lerp(k, part.xRot, 0.0F);
         part.yRot = Mth.lerp(k, part.yRot, 0.0F);
         part.zRot = Mth.lerp(k, part.zRot, 0.0F);
      }
   }

   /** Combat layers: keyed poses on the rig, then the aimed arms on top. */
   private static void combat(PlayerModel<?> model, LivingEntity entity, IronManAnimation.State s, float partialTick, float time) {
      float stanceR = s.weight(View.MODEL, Layer.STANCE_RIGHT, s.stance(true));
      float stanceL = s.weight(View.MODEL, Layer.STANCE_LEFT, s.stance(false));
      float missiles = s.weight(View.MODEL, Layer.MISSILES, s.missiles());
      int phase = s.unibeamPhase();
      float unibeam = s.weight(View.MODEL, Layer.UNIBEAM, phase == 1 || phase == 2);
      float overheat = s.weight(View.MODEL, Layer.OVERHEAT, phase == 3);
      float windup = s.weight(View.MODEL, Layer.WINDUP, s.windup());
      float lunge = s.weight(View.MODEL, Layer.LUNGE, s.lunge());
      float guard = s.weight(View.MODEL, Layer.GUARD, s.guard());
      float strike = s.weight(View.MODEL, Layer.STRIKE, s.striking());
      float form = s.weight(View.MODEL, Layer.FORM, s.forming());
      Signature sig = s.signature() != Signature.NONE ? s.signature() : s.lastSignature();
      float signature = s.weight(View.MODEL, Layer.SIGNATURE, s.signature() != Signature.NONE);
      if (stanceR + stanceL + missiles + unibeam + overheat + windup + lunge + guard + strike + form + signature < 0.001F) {
         return;
      }

      PoseRig rig = PoseRig.begin(model, entity.isCrouching(), 1.0F, null).slerp();
      if (overheat > 0.001F) {
         rig.body(OVERHEAT_BODY, 0.0F, overheat, true);
         rig.mainArm(OVERHEAT_RIGHT, 0.0F, overheat, false);
         rig.offArm(OVERHEAT_LEFT, 0.0F, overheat, false);
      }

      if (unibeam > 0.001F) {
         float charge = phase == 1 ? 1.0F : 0.0F;
         rig.body(UNIBEAM_BODY, 0.0F, unibeam, true);
         rig.mainArm(UNIBEAM_RIGHT, 0.0F, unibeam, false);
         rig.offArm(UNIBEAM_LEFT, 0.0F, unibeam, false);
         // Charge: arms drawn further back; beam: a fine shake from the output.
         float shake = phase == 2 ? 1.2F * Mth.sin(time * 3.1F) : 0.0F;
         rig.add(model.body, (-3.0F * charge + shake) * unibeam, 0.0F, 0.0F);
         rig.add(model.rightArm, 10.0F * charge * unibeam, 0.0F, 4.0F * charge * unibeam);
         rig.add(model.leftArm, 10.0F * charge * unibeam, 0.0F, -4.0F * charge * unibeam);
      }

      // Single-palm stance: the firing shoulder turns in a little (bladed stance).
      if (stanceR > 0.001F && stanceL < 0.5F) {
         rig.body(STANCE_BODY_RIGHT, 0.0F, stanceR * (1.0F - stanceL), true);
      } else if (stanceL > 0.001F && stanceR < 0.5F) {
         rig.body(STANCE_BODY_LEFT, 0.0F, stanceL * (1.0F - stanceR), true);
      }

      if (windup > 0.001F) {
         rig.body(WINDUP_BODY, 0.0F, windup, true);
         rig.mainArm(WINDUP_RIGHT, 0.0F, windup, false);
         rig.offArm(WINDUP_LEFT, 0.0F, windup, false);
         if (s.windupCharge() >= 0.999F) {
            rig.add(model.rightArm, 2.0F * Mth.sin(time * 4.0F) * windup, 0.0F, 0.0F);
         }
      }

      if (lunge > 0.001F) {
         rig.body(LUNGE_BODY, 0.0F, lunge, true);
         rig.offArm(LUNGE_LEFT, 0.0F, lunge, false);
      }

      if (form > 0.001F) {
         rig.mainArm(s.dissolve() ? DISSOLVE_RIGHT : FORM_RIGHT, s.formTime(partialTick), form, false);
      }

      if (strike > 0.001F) {
         float t = s.strikeTime(partialTick);
         if (s.hammer()) {
            rig.head(HAMMER_HEAD, t, strike);
            rig.body(HAMMER_BODY, t, strike, true);
            rig.mainArm(HAMMER_RIGHT, t, strike, false);
            rig.offArm(HAMMER_LEFT, t, strike, false);
         } else if (s.backhand()) {
            rig.body(BACKHAND_BODY, t, strike, true);
            rig.mainArm(BACKHAND_RIGHT, t, strike, false);
            rig.offArm(BACKHAND_LEFT, t, strike, false);
         } else {
            rig.body(SLASH_BODY, t, strike, true);
            rig.mainArm(SLASH_RIGHT, t, strike, false);
            rig.offArm(SLASH_LEFT, t, strike, false);
         }
      }

      if (guard > 0.001F) {
         rig.offArm(GUARD_LEFT, 0.0F, guard, false);
         rig.add(model.leftArm, (float)Math.toDegrees(model.head.xRot) * 0.5F * guard, (float)Math.toDegrees(model.head.yRot) * 0.5F * guard, 0.0F);
      }

      if (signature > 0.001F) {
         switch (sig) {
            case LASER -> rig.offArm(LASER_LEFT, 0.0F, signature, false);
            case GUN -> {
               rig.mainArm(GUN_RIGHT, 0.0F, signature, false);
               rig.offArm(GUN_LEFT, 0.0F, signature, false);
            }
            case SLAM_JUMP -> {
               rig.mainArm(SLAM_RIGHT, 0.0F, signature, false);
               rig.offArm(SLAM_LEFT, 0.0F, signature, false);
            }
            default -> {
            }
         }
      }

      rig.finish();

      // Aimed arms (after the rig: the hip lock already moved the shoulders).
      Vector3f look = ArmAim.look(model.head);
      Vector3f up = ArmAim.up(model.head);
      Vector3f right = ArmAim.right(model.head);
      if (missiles > 0.001F) {
         // Forearm launchers: both arms along the look, a touch inward, a small kick on the volley.
         float k = 10.0F * IronManAnimation.kick(s.sinceMissiles(partialTick) * 1.5F);
         ArmAim.aim(model.rightArm, ArmAim.tilt(ArmAim.tilt(look, new Vector3f(right).negate(), 2.5F), up, k), 0.0F, missiles);
         ArmAim.aim(model.leftArm, ArmAim.tilt(ArmAim.tilt(look, right, 2.5F), up, k), 0.0F, missiles);
      }

      aimPalm(model, model.rightArm, s, true, stanceR * (1.0F - missiles), look, up, right, partialTick, time);
      aimPalm(model, model.leftArm, s, false, stanceL * (1.0F - missiles), look, up, right, partialTick, time);
      if (lunge > 0.001F) {
         ArmAim.aim(model.rightArm, ArmAim.tilt(look, new Vector3f(right).negate(), 3.0F), 0.0F, lunge);
      }

      if (signature > 0.001F) {
         switch (sig) {
            case LASER, FIST -> ArmAim.aim(model.rightArm, ArmAim.tilt(look, new Vector3f(right).negate(), 3.0F), 0.0F, signature);
            case SLAM_DIVE -> {
               ArmAim.aim(model.rightArm, ArmAim.tilt(look, new Vector3f(right).negate(), 4.0F), 0.0F, signature);
               ArmAim.aim(model.leftArm, ArmAim.tilt(look, right, 4.0F), 0.0F, signature);
            }
            default -> {
            }
         }
      }
   }

   /** Repulsor palm on the crosshair: converging a little, kicked up along the head's up axis on a shot. */
   private static void aimPalm(PlayerModel<?> model, ModelPart arm, IronManAnimation.State s, boolean rightArm, float weight, Vector3f look, Vector3f up,
      Vector3f right, float partialTick, float time) {
      if (weight <= 0.001F) {
         return;
      }

      Vector3f inward = rightArm ? new Vector3f(right).negate() : new Vector3f(right);
      float kick = IronManAnimation.kick(s.sinceShot(rightArm, partialTick));
      float charge = s.charge(rightArm);
      float tremble = charge * 0.8F * Mth.sin(time * (2.6F + charge));
      Vector3f dir = ArmAim.tilt(ArmAim.tilt(look, inward, 3.5F), up, 20.0F * kick + tremble);
      ArmAim.aim(arm, dir, 0.0F, weight);
      // The shoulder takes the recoil.
      arm.z += 1.4F * kick * weight;
   }

   /** Palms down, arms slightly out and moving, feet stabilizing (spec §7.5). */
   private static void hover(PlayerModel<?> model, float w, float time) {
      float sway = 0.06F * Mth.sin(time * 0.11F);
      float drift = 0.04F * Mth.sin(time * 0.07F + 1.0F);
      rotate(model.rightArm, w, -0.12F + sway, 0.0F, 0.32F + drift);
      rotate(model.leftArm, w, -0.12F - sway, 0.0F, -0.32F - drift);
      rotate(model.rightLeg, w, 0.08F + 0.5F * sway, 0.0F, 0.07F);
      rotate(model.leftLeg, w, -0.04F - 0.5F * sway, 0.0F, -0.07F);
   }

   /** Unpowered glide: arms spread like wings, legs together. */
   private static void glide(PlayerModel<?> model, float w) {
      rotate(model.rightArm, w, 0.0F, 0.0F, 1.25F);
      rotate(model.leftArm, w, 0.0F, 0.0F, -1.25F);
      rotate(model.rightLeg, w, 0.05F, 0.0F, 0.03F);
      rotate(model.leftLeg, w, 0.05F, 0.0F, -0.03F);
   }

   /**
    * Superhero landing: body leaned around the neck, the whole model lowered
    * so the hips sit KNEEL_HIP above the ground, right knee forward, left leg
    * back along the ground, right fist down, left arm back and out.
    */
   private static void kneel(PlayerModel<?> model, float w) {
      float cos = Mth.cos(KNEEL_LEAN);
      float sin = Mth.sin(KNEEL_LEAN);
      float hipY = 24.0F - KNEEL_HIP;
      float neckY = hipY - 12.0F * cos;
      float hipZ = 12.0F * sin;
      model.body.xRot = Mth.lerp(w, model.body.xRot, KNEEL_LEAN);
      model.body.y = Mth.lerp(w, model.body.y, neckY);
      model.head.y = Mth.lerp(w, model.head.y, neckY);
      model.head.xRot = Mth.lerp(w, model.head.xRot, -0.35F);
      float shoulderY = neckY + 2.0F * cos;
      float shoulderZ = 2.0F * sin;
      model.rightArm.y = Mth.lerp(w, model.rightArm.y, shoulderY);
      model.leftArm.y = Mth.lerp(w, model.leftArm.y, shoulderY);
      model.rightArm.z = Mth.lerp(w, model.rightArm.z, shoulderZ);
      model.leftArm.z = Mth.lerp(w, model.leftArm.z, shoulderZ);
      rotate(model.rightArm, w, -0.25F, 0.0F, 0.08F);
      rotate(model.leftArm, w, 0.65F, 0.0F, -0.55F);
      model.rightLeg.y = Mth.lerp(w, model.rightLeg.y, hipY);
      model.leftLeg.y = Mth.lerp(w, model.leftLeg.y, hipY);
      model.rightLeg.z = Mth.lerp(w, model.rightLeg.z, hipZ);
      model.leftLeg.z = Mth.lerp(w, model.leftLeg.z, hipZ);
      rotate(model.rightLeg, w, -1.3F, 0.0F, 0.1F);
      rotate(model.leftLeg, w, 1.25F, 0.0F, -0.12F);
   }

   private static void rotate(ModelPart part, float w, float x, float y, float z) {
      part.xRot = Mth.lerp(w, part.xRot, x);
      part.yRot = Mth.lerp(w, part.yRot, y);
      part.zRot = Mth.lerp(w, part.zRot, z);
   }

   /** Locomotion weights per entity (animation-system §6). Render thread only. */
   private static final class Weights {
      float hover;
      float glide;
      float kneel;
      long lastNanos = System.nanoTime();

      void advance(boolean hoverOn, boolean glideOn, boolean kneelOn, float k) {
         long now = System.nanoTime();
         float dt = Math.min(0.1F, (now - this.lastNanos) / 1.0E9F);
         this.lastNanos = now;
         float a = 1.0F - (float)Math.exp(-k * dt);
         this.hover = Mth.lerp(a, this.hover, hoverOn ? 1.0F : 0.0F);
         this.glide = Mth.lerp(a, this.glide, glideOn ? 1.0F : 0.0F);
         this.kneel = Mth.lerp(a, this.kneel, kneelOn ? 1.0F : 0.0F);
      }
   }
}
