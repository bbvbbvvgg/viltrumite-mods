package dev.baranhan.viltrumitecore.client.t1000;

import dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.client.city.CitizenModel;
import dev.baranhan.viltrumitecore.t1000.T1000Action;
import dev.baranhan.viltrumitecore.t1000.T1000Arm;
import dev.baranhan.viltrumitecore.t1000.T1000Entity;
import dev.baranhan.viltrumitecore.t1000.T1000Form;
import dev.baranhan.viltrumitecore.t1000.T1000Rules;
import net.minecraft.util.Mth;

/**
 * The T-1000's body language on the shared {@link PoseRig} (animation-system rule: vanilla body, key tables,
 * {@code finish()} after the layer), stacked over the citizen poses of {@code CitizenPoser}. Disguised it adds
 * nothing: it walks, idles and gestures exactly like the citizens around it. Revealed it eases into the hunter:
 * an upright sprint with long arm strokes, the blade arm carried off the leg, and one timeline per synced
 * {@link T1000Action} (stab, slash, hook throw, grab and lift, parry riposte, leap, reactions, the iconic head
 * tilt), plus the frozen stiffness, the burning writhe and the death throes. Pure client code: everything is
 * derived from synced state and the age clock.
 */
public final class T1000Poser {
   // right arm = main arm (rig side +1); left arm rows carry their own (mirrored) yaw/roll signs
   static final float[][] STAB_R = {PoseKeys.under(0.0F), PoseKeys.key(3.0F, -48.0F, 22.0F, 10.0F, 0.0F, 0.0F, 2.5F),
      PoseKeys.strike(5.0F, -92.0F, -6.0F, 2.0F, 0.0F, 0.0F, -2.0F), PoseKeys.key(8.5F, -88.0F, -4.0F, 2.0F, 0.0F, 0.0F, -1.0F), PoseKeys.under(12.0F)};
   static final float[][] STAB_BODY = {PoseKeys.under(0.0F), PoseKeys.key(3.0F, -2.0F, 16.0F, 0.0F), PoseKeys.strike(5.0F, 8.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(9.0F, 5.0F, -8.0F, 0.0F), PoseKeys.under(12.0F)};
   static final float[][] STAB_L = {PoseKeys.under(0.0F), PoseKeys.key(3.0F, -24.0F, 0.0F, -10.0F), PoseKeys.key(5.0F, 18.0F, 0.0F, -8.0F), PoseKeys.under(12.0F)};
   static final float[][] SLASH_R = {PoseKeys.under(0.0F), PoseKeys.key(5.0F, -158.0F, 34.0F, 38.0F), PoseKeys.strike(8.0F, -78.0F, -52.0F, -14.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(12.0F, -46.0F, -40.0F, -4.0F), PoseKeys.under(16.0F)};
   static final float[][] SLASH_BODY = {PoseKeys.under(0.0F), PoseKeys.key(5.0F, -4.0F, 26.0F, -4.0F), PoseKeys.strike(8.0F, 10.0F, -26.0F, 4.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(12.0F, 6.0F, -16.0F, 0.0F), PoseKeys.under(16.0F)};
   static final float[][] SLASH_L = {PoseKeys.under(0.0F), PoseKeys.key(5.0F, -30.0F, 0.0F, -18.0F), PoseKeys.key(9.0F, 20.0F, 0.0F, -12.0F), PoseKeys.under(16.0F)};
   static final float[][] HOOK_L = {PoseKeys.under(0.0F), PoseKeys.key(4.0F, -62.0F, -10.0F, -24.0F, 0.0F, 0.0F, 2.0F),
      PoseKeys.strike(7.0F, -96.0F, 6.0F, 0.0F, 0.0F, 0.0F, -2.0F), PoseKeys.key(11.0F, -94.0F, 4.0F, 0.0F, 0.0F, 0.0F, -1.0F),
      PoseKeys.key(16.0F, -58.0F, 0.0F, -6.0F, 0.0F, 0.0F, 2.5F), PoseKeys.under(22.0F)};
   static final float[][] HOOK_BODY = {PoseKeys.under(0.0F), PoseKeys.key(4.0F, -3.0F, -18.0F, 0.0F), PoseKeys.strike(7.0F, 6.0F, 14.0F, 0.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(13.0F, -10.0F, 6.0F, 0.0F), PoseKeys.key(17.0F, -6.0F, 0.0F, 0.0F), PoseKeys.under(22.0F)};
   static final float[][] HOOK_R = {PoseKeys.under(0.0F), PoseKeys.key(7.0F, 16.0F, 0.0F, 14.0F), PoseKeys.key(14.0F, -38.0F, 18.0F, 10.0F), PoseKeys.under(22.0F)};
   static final float[][] GRAB_L = {PoseKeys.under(0.0F), PoseKeys.strike(6.0F, -98.0F, 8.0F, 0.0F, 0.0F, 0.0F, -1.5F), PoseKeys.key(10.0F, -104.0F, 4.0F, 0.0F),
      PoseKeys.key(15.0F, -128.0F, 0.0F, -6.0F), PoseKeys.key(25.0F, -126.0F, 0.0F, -6.0F), PoseKeys.under(30.0F)};
   static final float[][] GRAB_R = {PoseKeys.under(0.0F), PoseKeys.key(8.0F, -20.0F, 10.0F, 12.0F), PoseKeys.key(15.0F, -46.0F, 26.0F, 12.0F, 0.0F, 0.0F, 2.5F),
      PoseKeys.strike(19.0F, -94.0F, -10.0F, 2.0F, 0.0F, 0.0F, -2.0F), PoseKeys.key(25.0F, -86.0F, -6.0F, 2.0F), PoseKeys.under(30.0F)};
   static final float[][] GRAB_BODY = {PoseKeys.under(0.0F), PoseKeys.strike(6.0F, 12.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(15.0F, -6.0F, 4.0F, 0.0F),
      PoseKeys.strike(19.0F, 4.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.under(30.0F)};
   static final float[][] COUNTER_R = {PoseKeys.under(0.0F), PoseKeys.strike(3.0F, -124.0F, -42.0F, -22.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(4.5F, -118.0F, -38.0F, -18.0F),
      PoseKeys.strike(6.0F, -88.0F, 0.0F, 4.0F, 0.0F, 0.0F, -2.0F), PoseKeys.key(10.0F, -82.0F, -4.0F, 2.0F), PoseKeys.under(14.0F)};
   static final float[][] COUNTER_BODY = {PoseKeys.under(0.0F), PoseKeys.key(3.0F, -6.0F, 14.0F, 0.0F), PoseKeys.strike(6.0F, 8.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.under(14.0F)};
   static final float[][] LEAP_R = {PoseKeys.under(0.0F), PoseKeys.key(2.0F, 26.0F, 0.0F, 18.0F), PoseKeys.strike(4.0F, -150.0F, -14.0F, 8.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(9.0F, -110.0F, -10.0F, 6.0F), PoseKeys.under(14.0F)};
   static final float[][] LEAP_L = {PoseKeys.under(0.0F), PoseKeys.key(2.0F, 26.0F, 0.0F, -18.0F), PoseKeys.strike(4.0F, -150.0F, 14.0F, -8.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(9.0F, -110.0F, 10.0F, -6.0F), PoseKeys.under(14.0F)};
   static final float[][] LEAP_BODY = {PoseKeys.under(0.0F), PoseKeys.key(2.0F, 22.0F, 0.0F, 0.0F), PoseKeys.strike(4.0F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(10.0F, 12.0F, 0.0F, 0.0F), PoseKeys.under(14.0F)};
   static final float[][] LEAP_RL = {PoseKeys.under(0.0F), PoseKeys.key(2.0F, -40.0F, 0.0F, 4.0F), PoseKeys.key(5.0F, 34.0F, 0.0F, 4.0F), PoseKeys.under(14.0F)};
   static final float[][] LEAP_LL = {PoseKeys.under(0.0F), PoseKeys.key(2.0F, 12.0F, 0.0F, -4.0F), PoseKeys.key(5.0F, -30.0F, 0.0F, -4.0F), PoseKeys.under(14.0F)};
   static final float[][] FLOW_R = {PoseKeys.under(0.0F), PoseKeys.key(4.0F, -6.0F, 0.0F, -6.0F), PoseKeys.key(14.0F, -6.0F, 0.0F, -6.0F), PoseKeys.under(18.0F)};
   static final float[][] FLOW_L = {PoseKeys.under(0.0F), PoseKeys.key(4.0F, -6.0F, 0.0F, 6.0F), PoseKeys.key(14.0F, -6.0F, 0.0F, 6.0F), PoseKeys.under(18.0F)};
   static final float[][] FLOW_LEG = {PoseKeys.under(0.0F), PoseKeys.key(4.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(14.0F, 0.0F, 0.0F, 0.0F), PoseKeys.under(18.0F)};
   // reactions: additive, scaled by the hit direction (front > 0 = hit from the front, side > 0 = from its right)
   static final float[][] HIT_PITCH = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.strike(2.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(10.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] HEAVY_PITCH = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.strike(3.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(9.0F, 0.75F, 0.0F, 0.0F), PoseKeys.key(26.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] HEAVY_ARMS = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.strike(3.0F, -40.0F, 0.0F, 46.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(12.0F, -18.0F, 0.0F, 20.0F), PoseKeys.key(26.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] SPLIT_HEAD = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.strike(2.0F, -38.0F, 0.0F, 8.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(16.0F, -30.0F, 0.0F, 6.0F), PoseKeys.key(34.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(40.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] SPLIT_BODY = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.strike(3.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
      PoseKeys.key(18.0F, -8.0F, 0.0F, 0.0F), PoseKeys.key(36.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] SPLIT_ARMS = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(4.0F, -22.0F, 0.0F, 18.0F), PoseKeys.key(20.0F, -16.0F, 0.0F, 14.0F),
      PoseKeys.key(36.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] TILT_HEAD = {PoseKeys.key(0.0F, 0.0F, 0.0F, 0.0F), PoseKeys.key(9.0F, 4.0F, 0.0F, 21.0F), PoseKeys.key(22.0F, 4.0F, 0.0F, 21.0F),
      PoseKeys.key(30.0F, 0.0F, 0.0F, 0.0F)};
   static final float[][] FREEZE_R = {PoseKeys.under(0.0F), PoseKeys.key(6.0F, -34.0F, 0.0F, 22.0F), PoseKeys.key(40.0F, -34.0F, 0.0F, 22.0F)};
   static final float[][] FREEZE_L = {PoseKeys.under(0.0F), PoseKeys.key(6.0F, -28.0F, 0.0F, -20.0F), PoseKeys.key(40.0F, -28.0F, 0.0F, -20.0F)};
   static final float[][] STIFF_LEG = {PoseKeys.under(0.0F), PoseKeys.key(6.0F, 0.0F, 0.0F, 0.0F)};

   private T1000Poser() {
   }

   /** Client memory of one T-1000 (in {@link T1000Entity#t1000Client}). */
   static final class Mem {
      float last = Float.NaN;
      /** 0 citizen .. 1 hunter stance, eased in real time. */
      float stance;
      /** 0 .. 1 frozen stiffness. */
      float stiff;
   }

   static Mem mem(T1000Entity e) {
      if (e.t1000Client instanceof Mem m) {
         return m;
      }
      Mem m = new Mem();
      e.t1000Client = m;
      return m;
   }

   /** Hunter stance target of a form at an age: eases in over the reveal, out over the conceal. */
   static float stanceTarget(T1000Form form, float formAge) {
      return switch (form) {
         case HUMAN, CONCEALING -> 0.0F;
         case REVEALING -> formAge > T1000Rules.REVEAL_TICKS * 0.4F ? 1.0F : 0.0F;
         default -> 1.0F;
      };
   }

   static float approach(float dt, float rate) {
      return 1.0F - (float)Math.pow(1.0F - rate, dt);
   }

   static void pose(CitizenModel model, T1000Entity e, float limbSwing, float limbAmount, float age, float partialTick) {
      Mem mem = mem(e);
      float dt = Float.isNaN(mem.last) ? 1.0F : Mth.clamp(age - mem.last, 0.0F, 4.0F);
      mem.last = age;
      T1000Form form = e.form();
      float formAge = e.formAge(partialTick);
      mem.stance += (stanceTarget(form, formAge) - mem.stance) * approach(dt, 0.2F);
      boolean frozen = (e.flags() & T1000Entity.FLAG_FROZEN) != 0;
      mem.stiff += ((frozen ? 1.0F : 0.0F) - mem.stiff) * approach(dt, 0.25F);
      T1000Action action = e.acting() ? e.action() : T1000Action.NONE;
      float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
      float alpha = (float)Math.toRadians(e.lastHitYaw() - bodyYaw);
      apply(model, form, formAge, mem.stance, mem.stiff, action, e.actionAge(partialTick), e.rightArm(), e.leftArm(), (e.flags() & T1000Entity.FLAG_HEATED) != 0,
         Mth.cos(alpha), Mth.sin(alpha), e.lastHitStrength(), limbSwing, limbAmount, age, e.getId());
   }

   /** The pose from explicit state (the live path and the tests). */
   static void apply(CitizenModel model, T1000Form form, float formAge, float stance, float stiff, T1000Action action, float t, T1000Arm right, T1000Arm left,
      boolean heated, float front, float side, float strength, float limbSwing, float limbAmount, float age, int seed) {
      if (stance < 0.002F && action == T1000Action.NONE && form != T1000Form.DYING && stiff < 0.002F && !heated) {
         return; // disguised: the citizen pose alone
      }
      PoseRig rig = PoseRig.begin(model, model.crouching, 1.0F, null).slerp();
      // the hunter's sprint: longer arm strokes on the vanilla swing, torso pitched into the run
      float run = Mth.clamp(limbAmount * 1.6F, 0.0F, 1.0F) * stance;
      if (stance > 0.002F) {
         float swing = Mth.cos(limbSwing * 0.6662F) * limbAmount;
         model.rightArm.xRot += -swing * 0.45F * stance;
         model.leftArm.xRot += swing * 0.45F * stance;
         rig.add(model.body, 9.0F * run, 0.0F, 0.0F);
         rig.add(model.head, -4.0F * run, 0.0F, 0.0F);
         // weapons carried off the legs, a touch forward: ready
         if (right != T1000Arm.HAND) {
            rig.add(model.rightArm, -10.0F * stance, 0.0F, 9.0F * stance);
         }
         if (left != T1000Arm.HAND) {
            rig.add(model.leftArm, -8.0F * stance, 0.0F, -9.0F * stance);
         }
         // unnatural stillness of the head while standing: no idle sway, a slight forward set
         float still = (1.0F - run) * stance;
         rig.add(model.head, 3.0F * still, 0.0F, 0.0F);
      }
      switch (action) {
         case STAB -> {
            rig.mainArm(STAB_R, t, 1.0F, false);
            rig.offArm(STAB_L, t, 1.0F, false);
            rig.body(STAB_BODY, t, 1.0F, false);
         }
         case SLASH -> {
            rig.mainArm(SLASH_R, t, 1.0F, false);
            rig.offArm(SLASH_L, t, 1.0F, false);
            rig.body(SLASH_BODY, t, 1.0F, false);
         }
         case HOOK -> {
            rig.offArm(HOOK_L, t, 1.0F, false);
            rig.mainArm(HOOK_R, t, 1.0F, false);
            rig.body(HOOK_BODY, t, 1.0F, false);
         }
         case GRAB -> {
            rig.offArm(GRAB_L, t, 1.0F, false);
            rig.mainArm(GRAB_R, t, 1.0F, false);
            rig.body(GRAB_BODY, t, 1.0F, false);
         }
         case COUNTER -> {
            rig.mainArm(COUNTER_R, t, 1.0F, false);
            rig.body(COUNTER_BODY, t, 1.0F, false);
         }
         case LEAP -> {
            rig.mainArm(LEAP_R, t, 1.0F, false);
            rig.offArm(LEAP_L, t, 1.0F, false);
            rig.body(LEAP_BODY, t, 1.0F, false);
            rig.mainLeg(LEAP_RL, t, 1.0F, false);
            rig.offLeg(LEAP_LL, t, 1.0F, false);
         }
         case FLOW -> {
            rig.mainArm(FLOW_R, t, 1.0F, false);
            rig.offArm(FLOW_L, t, 1.0F, false);
            rig.mainLeg(FLOW_LEG, t, 1.0F, false);
            rig.offLeg(FLOW_LEG, t, 1.0F, false);
         }
         case HIT -> {
            float k = PoseKeys.sample(HIT_PITCH, t, 0, 0.0F, false) * (0.4F + 0.6F * strength);
            rig.add(model.body, -11.0F * front * k, 0.0F, 9.0F * side * k);
            rig.add(model.head, -9.0F * front * k, -10.0F * side * k, 6.0F * side * k);
            rig.add(model.rightArm, -10.0F * k, 0.0F, 8.0F * k);
            rig.add(model.leftArm, -10.0F * k, 0.0F, -8.0F * k);
         }
         case HEAVY -> {
            float k = PoseKeys.sample(HEAVY_PITCH, t, 0, 0.0F, false);
            rig.add(model.body, -30.0F * front * k, 0.0F, 18.0F * side * k);
            rig.add(model.head, -24.0F * front * k, 0.0F, 12.0F * side * k);
            float ap = PoseKeys.sample(HEAVY_ARMS, t, 0, 0.0F, false);
            float ar = PoseKeys.sample(HEAVY_ARMS, t, 2, 0.0F, false);
            rig.add(model.rightArm, ap, 0.0F, ar);
            rig.add(model.leftArm, ap, 0.0F, -ar);
         }
         case FACE_SPLIT -> {
            rig.head(SPLIT_HEAD, t, 1.0F);
            rig.body(SPLIT_BODY, t, 1.0F, true);
            float ap = PoseKeys.sample(SPLIT_ARMS, t, 0, 0.0F, false);
            float ar = PoseKeys.sample(SPLIT_ARMS, t, 2, 0.0F, false);
            rig.add(model.rightArm, ap, 0.0F, ar);
            rig.add(model.leftArm, ap, 0.0F, -ar);
         }
         case BURN -> heated = true;
         case FREEZE -> {
            rig.mainArm(FREEZE_R, t, 1.0F, false);
            rig.offArm(FREEZE_L, t, 1.0F, false);
            stiff = Math.max(stiff, Mth.clamp(t / 6.0F, 0.0F, 1.0F));
         }
         case TILT -> rig.head(TILT_HEAD, t, 1.0F);
         case SHAKE -> {
            float a = (1.0F - t / T1000Action.SHAKE.ticks) * 1.0F;
            float w = Mth.sin(t * 2.6F);
            rig.add(model.body, 0.0F, w * 5.0F * a, w * 3.0F * a);
            rig.add(model.head, 0.0F, -w * 9.0F * a, -w * 6.0F * a);
            rig.add(model.rightArm, 0.0F, 0.0F, w * 7.0F * a);
            rig.add(model.leftArm, 0.0F, 0.0F, w * 7.0F * a);
         }
         default -> {
         }
      }
      if (heated) {
         // the metal boils: flailing, twisting against the heat
         float a = action == T1000Action.BURN ? Mth.clamp(t / 4.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((t - 24.0F) / 6.0F, 0.0F, 1.0F)) : 0.45F;
         float w = age * 0.55F + (seed & 7);
         rig.add(model.body, 6.0F * Mth.sin(w * 0.7F) * a, 14.0F * Mth.sin(w) * a, 6.0F * Mth.sin(w * 1.3F) * a);
         rig.add(model.rightArm, (-60.0F - 40.0F * Mth.sin(w * 1.1F)) * a, 0.0F, (20.0F + 18.0F * Mth.sin(w * 1.7F)) * a);
         rig.add(model.leftArm, (-60.0F - 40.0F * Mth.sin(w * 1.1F + 2.0F)) * a, 0.0F, (-20.0F - 18.0F * Mth.sin(w * 1.5F)) * a);
         rig.add(model.head, -14.0F * a, 18.0F * Mth.sin(w * 0.9F) * a, 0.0F);
      }
      if (stiff > 0.002F) {
         // frozen: the stride locks, the limbs set where they are, a faint brittle tremor
         float k = Mth.clamp(stiff, 0.0F, 1.0F);
         model.rightLeg.xRot *= 1.0F - k;
         model.leftLeg.xRot *= 1.0F - k;
         model.rightArm.xRot = Mth.lerp(k * 0.8F, model.rightArm.xRot, -0.5F);
         model.leftArm.xRot = Mth.lerp(k * 0.8F, model.leftArm.xRot, -0.42F);
         rig.add(model.body, 0.0F, 0.0F, Mth.sin(age * 3.1F) * 0.6F * k);
      }
      if (form == T1000Form.DYING) {
         throes(rig, model, formAge, age);
      } else if (form == T1000Form.REFORMING) {
         // rising out of the puddle: head down, arms hanging, lifting to the stance
         float r = 1.0F - T1000Shapes.smooth(formAge / T1000Rules.REFORM_TICKS);
         rig.add(model.head, 34.0F * r, 0.0F, 0.0F);
         rig.add(model.body, 18.0F * r, 0.0F, 0.0F);
         rig.add(model.rightArm, 10.0F * r, 0.0F, 6.0F * r);
         rig.add(model.leftArm, 10.0F * r, 0.0F, -6.0F * r);
      } else if (form == T1000Form.REVEALING) {
         // the reveal: chin up, a slow turn of the head onto the target, the shoulders squaring
         float r = T1000Shapes.smooth(formAge / T1000Rules.REVEAL_TICKS);
         float bell = Mth.sin(r * Mth.PI);
         rig.add(model.head, -6.0F * bell, 0.0F, 12.0F * bell);
         rig.add(model.rightArm, -6.0F * bell, 0.0F, 10.0F * bell);
         rig.add(model.leftArm, -6.0F * bell, 0.0F, -10.0F * bell);
      }
      rig.finish();
   }

   /** Death throes: the body fights the destruction, twisting ever harder, then sags into the melt. */
   static void throes(PoseRig rig, CitizenModel model, float formAge, float age) {
      float rise = T1000Shapes.smooth(formAge / 10.0F);
      float sag = T1000Shapes.smooth((formAge - (T1000Rules.DEATH_MELT_START - 6)) / 18.0F);
      float a = rise * (1.0F - sag * 0.7F);
      float w = age * (0.9F + 0.5F * rise);
      rig.add(model.body, (10.0F * Mth.sin(w * 0.8F) + 18.0F * sag) * a, 22.0F * Mth.sin(w * 0.6F) * a, 10.0F * Mth.sin(w * 1.2F) * a);
      rig.add(model.head, -26.0F * a + 10.0F * Mth.sin(w * 1.4F) * a, 30.0F * Mth.sin(w * 0.9F) * a, 22.0F * Mth.sin(w * 0.7F) * a);
      rig.add(model.rightArm, (-90.0F + 70.0F * Mth.sin(w * 1.1F)) * a, 20.0F * Mth.sin(w * 0.5F) * a, (30.0F + 25.0F * Mth.sin(w * 1.9F)) * a);
      rig.add(model.leftArm, (-90.0F + 70.0F * Mth.sin(w * 1.1F + 2.4F)) * a, -20.0F * Mth.sin(w * 0.5F + 1.0F) * a, (-30.0F - 25.0F * Mth.sin(w * 1.7F)) * a);
      rig.add(model.rightLeg, 14.0F * Mth.sin(w * 0.8F) * a + 24.0F * sag, 0.0F, 8.0F * a);
      rig.add(model.leftLeg, -14.0F * Mth.sin(w * 0.8F) * a + 24.0F * sag, 0.0F, -8.0F * a);
   }
}
