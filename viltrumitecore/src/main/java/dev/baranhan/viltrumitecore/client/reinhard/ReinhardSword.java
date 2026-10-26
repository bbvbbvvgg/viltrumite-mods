package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The Dragon Sword sheathed on Reinhard's left hip (spec §8): sheath + blade in its sheathed place,
 * attached to the body bone. Stage 4: the blade slides out along the sheath axis while Reid is drawn.
 */
public final class ReinhardSword {
   /** Sheath mouth on the body, Bedrock px (+x = Reinhard's left), model space. */
   public static final float HIP_X = 6.2F;
   public static final float HIP_Y = 15.8F;
   public static final float HIP_Z = -1.0F;
   /** Hilt slightly inward; long sheath trails behind the thigh, outside the walking legs. */
   public static final float TILT_Z = 0.0F;
   public static final float TILT_X = 50.0F;
   /** Owner correction: the whole weapon is 18% smaller than the first-pass 0.70 scale. */
   public static final float SWORD_SCALE = 0.574F;
   /** Shorten the archive's excessive longitudinal proportions to clear the ground at a waist mount. */
   public static final float LENGTH_SCALE = .78F;
   /** Stage 4: how far (sword px, along the sheath axis toward the hilt) the blade travels out at full draw. */
   public static final float DRAW_OUT_PX = 50.0F;

   private ReinhardSword() {
   }

   /** Guard ahead of the palm in the vanilla held-item frame (px). */
   public static final float HAND_GUARD_PX = 2;

   /** Smooth, rigid transfer: never linearly interpolate matrices (that shears the sword). */
   public static org.joml.Matrix4f interpolate(org.joml.Matrix4f from, org.joml.Matrix4f to, float weight) {
      float w = Math.max(0, Math.min(1, weight));
      if (w == 0) return new org.joml.Matrix4f(from);
      if (w == 1) return new org.joml.Matrix4f(to);
      org.joml.Vector3f p = from.getTranslation(new org.joml.Vector3f()).lerp(to.getTranslation(new org.joml.Vector3f()), w);
      org.joml.Vector3f scale = from.getScale(new org.joml.Vector3f()).lerp(to.getScale(new org.joml.Vector3f()), w);
      org.joml.Quaternionf q = from.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize()
         .slerp(to.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize(), w);
      return new org.joml.Matrix4f().translationRotateScale(p, q, scale);
   }

   public static float handWeight(ReinhardPoses.ReidPhase phase, double ticks) {
      return switch (phase) {
         case DRAWN -> 1;
         case DRAWING -> smooth((ticks - 96) / 24);
         case SHEATHING -> 1 - smooth(ticks / 8);
         default -> 0;
      };
   }

   public static float strikeWeight(double ticks) {
      return smooth(ticks / 4) * smooth((12 - ticks) / 4);
   }

   private static float smooth(double value) {
      double t = Math.max(0, Math.min(1, value));
      return (float)(t*t*(3-2*t));
   }

   /** Blade guard at the hip, before extraction. */
   static org.joml.Matrix4f hip(BakedGeoModel body) {
      PoseStack frame = new PoseStack();
      GeoBone bone = body.getBone(ReinhardAssets.BODY_BONE);
      if (bone != null) {
         ReinhardRenderer.boneFrame(frame, bone);
         frame.translate((-HIP_X-bone.pivotX)/16, (HIP_Y-bone.pivotY)/16, (HIP_Z-bone.pivotZ)/16);
      }
      frame.mulPose(Axis.ZP.rotationDegrees(TILT_Z));
      frame.mulPose(Axis.XP.rotationDegrees(-TILT_X));
      frame.scale(SWORD_SCALE,SWORD_SCALE*LENGTH_SCALE,SWORD_SCALE);
      frame.mulPose(Axis.ZP.rotationDegrees(180));
      // Native X is the broad face, Z its thickness. Turn X along the travel direction;
      // the thin Z edge now faces out from the hip. This is local Y, not a world-axis guess.
      frame.mulPose(Axis.YP.rotationDegrees(90));
      return new org.joml.Matrix4f(frame.last().pose());
   }

   /** Local Y is the blade's measured longitudinal axis; native X is its broad face.
    * Roll only the free blade. It is still aligned with the sheath through extraction,
    * then turns while clear of it. Reverse the same curve when putting Reid away. */
   static float drawnRoll(ReinhardPoses.ReidPhase phase,double ticks) {
      return switch(phase) {
         case DRAWN -> (float)Math.PI/2;
         case DRAWING -> smooth((ticks-64)/24)*(float)Math.PI/2;
         case SHEATHING -> smooth((120-ticks*6-64)/24)*(float)Math.PI/2;
         default -> 0;
      };
   }
   static float twoHandWeight(ReinhardPoses.ReidPhase phase,double ticks) {
      return switch(phase) {
         case DRAWN -> 1;
         case DRAWING -> smooth((ticks-100)/20);
         case SHEATHING -> smooth((120-ticks*6-100)/20);
         default -> 0;
      };
   }
   /** A different palm point on the SAME handle, without moving either weapon mesh. */
   static org.joml.Matrix4f gripAt(org.joml.Matrix4f blade,float nativeY) {
      return new org.joml.Matrix4f(blade).translate(0,nativeY/16-ReinhardGrip.HANDLE.y,0);
   }
   static org.joml.Matrix4f rollDrawnBlade(org.joml.Matrix4f blade,ReinhardPoses.ReidPhase phase,double ticks) {
      return new org.joml.Matrix4f(blade).rotateY(drawnRoll(phase,ticks));
   }

   /** Ordinary drawn strokes are one-handed: the left palm leaves the pommel during the first
    * ticks and returns over the last ones (REINHARD OVERHAUL 3b item 2: it is free meanwhile,
    * never sent to the empty scabbard). */
   public static float strikeRelease(double ticks,int length) {
      if(!Double.isFinite(ticks) || length<=0) return 0;
      return smooth(ticks/2.5)*smooth((length-ticks)/3);
   }

   /** Lethal Chop by the support hand: it lets go of the hilt (or the sheath) for the chop and
    * takes it again after the follow-through. {@code time01} is the chop's 0..1 clip time. */
   public static float chopRelease(double time01) {
      if(!Double.isFinite(time01) || time01<=0 || time01>=1) return 0;
      return smooth(time01/.12)*smooth((1-time01)/.3);
   }

   static org.joml.Matrix4f[] equipment(BakedGeoModel body,ReinhardPoses.ReidPhase phase,
      double ticks,boolean strike,double actionTicks,float holyWeight) {
      return equipment(body,phase,ticks,strike,actionTicks,holyWeight,0);
   }

   /** TP choreography: one sampled grip bone, then both wrist constraints, then one
    * immutable pair of mesh frames. Never recompute the weapon from the solved arm.
    * {@code release} (0..1, {@link ReinhardHands}) frees the support hand for its own clip. */
   static org.joml.Matrix4f[] equipment(BakedGeoModel body,ReinhardPoses.ReidPhase phase,
      double ticks,boolean strike,double actionTicks,float holyWeight,float release) {
      org.joml.Matrix4f hip=hip(body), blade=new org.joml.Matrix4f(hip);
      if(phase!=ReinhardPoses.ReidPhase.SHEATHED || strike) {
         PoseStack anchor=new PoseStack();ReinhardRenderer.boneFrame(anchor,body.getBone("reid_grip"));
         blade=new org.joml.Matrix4f(anchor.last().pose()).scale(SWORD_SCALE,SWORD_SCALE*LENGTH_SCALE,SWORD_SCALE)
            .translate(-ReinhardGrip.HANDLE.x,-ReinhardGrip.HANDLE.y,-ReinhardGrip.HANDLE.z);
      }
      org.joml.Matrix4f sheath=new org.joml.Matrix4f(strike?blade:hip)
         .translate(ReinhardAssets.SHEATHED_OFFSET.x/16,-ReinhardAssets.SHEATHED_OFFSET.y/16,-ReinhardAssets.SHEATHED_OFFSET.z/16);
      // Sheath and assembled Sheath Strike keep their exact original frame.
      // The owner selected the +90-degree axial audit variant (front edge,
      // broad face from the side). Apply it only to the final free blade mesh,
      // after the unchanged grip solve below; never rotate the sheath or stance.
      float right=phase==ReinhardPoses.ReidPhase.DRAWN || strike?1:0;
      float gripWeight=0;
      float leftBlade=phase==ReinhardPoses.ReidPhase.SHEATHED?1:0;
      if(phase==ReinhardPoses.ReidPhase.DRAWING || phase==ReinhardPoses.ReidPhase.SHEATHING) {
         double drawTick=phase==ReinhardPoses.ReidPhase.DRAWING?ticks:120-ticks*6;
         right=smooth((drawTick-88)/6);
         leftBlade=1-smooth((drawTick-94)/6);
         gripWeight=leftBlade*smooth((drawTick-12)/12);
      }
      // ONE owner of the left hand (ReinhardHands): the sheath while Reid is in it or passing
      // through it (it grips the hilt until the hand-off, then the sheath mouth), the pommel
      // while the drawn sword is held in two hands, else its own clip. The empty sheath of a
      // drawn Reid never claims it: a released hand follows its clip and comes back.
      ReinhardHands.Support hands=ReinhardHands.support(phase,ticks,holyWeight,release);
      if(strike) {
         right=smooth((actionTicks-2)/2)*smooth((12-actionTicks)/3);
         ReinhardGrip.solve(body,blade,1-right,true);
      } else if(hands.sheath()>0) {
         org.joml.Matrix4f support=new org.joml.Matrix4f(hip).translate(0,3.3F/16,0);
         org.joml.Matrix4f leftTarget=interpolate(support,blade,leftBlade);
         ReinhardGrip.support(body,leftTarget,hands.sheath(),gripWeight);
      }
      // The short native hilt has two distinct grips: main hand by the guard,
      // support hand by the pommel. Never stack two glove centres at HANDLE.
      float pairedGrip=strike?0:twoHandWeight(phase,ticks);
      float twoHands=pairedGrip*(1-Math.max(0,Math.min(1,release)));
      if(!strike && hands.hilt()>0)ReinhardGrip.paired(body,gripAt(blade,-8.5F),hands.hilt(),true);
      if(right>0) {
         // The main hand stays by the guard while the support hand lets go:
         // releasing the pommel must not slide the right glove along the hilt.
         var target=interpolate(blade,gripAt(blade,0),pairedGrip);
         // Position is already fully owned by the right hand. Only the new
         // paired pronation ramps with twoHands; an epsilon must not enable it fully.
         if(twoHands>0)ReinhardGrip.paired(body,target,right,right*twoHands,false,true);
         else ReinhardGrip.solve(body,target,right);
      }
      if(!strike)blade=rollDrawnBlade(blade,phase,ticks);
      return new org.joml.Matrix4f[]{sheath,blade};
   }

   static void drawEquipment(PoseStack stack,org.joml.Matrix4f[] frames,MultiBufferSource buffers,int light,int overlay) {
      drawFrame(ReinhardAssets.SHEATH,stack,frames[0],buffers,light,overlay);
      drawFrame(ReinhardAssets.BLADE,stack,frames[1],buffers,light,overlay);
   }

   /** First-person axial roll of the drawn blade: half of the third-person quarter turn, so the
    * camera sees the broad face along its diagonal instead of a thin edge (CPU preview fit). */
   static final float FIRST_PERSON_ROLL = .5F;
   /** How far (blocks, down the view) the first-person sheath drops at full hand weight. */
   static final float FIRST_PERSON_SHEATH_DROP = 1.5F;

   /** REINHARD OVERHAUL 3b item 1: the first-person sheath exists only while it is in use (draw,
    * sheathe, Sheath Strike). With Reid drawn it hangs empty at the hip, outside the camera view,
    * so it is not drawn at all (the third-person sheath is unaffected). */
   static boolean firstPersonSheathVisible(ReinhardPoses.ReidPhase phase, boolean strike) {
      return strike || phase != ReinhardPoses.ReidPhase.DRAWN;
   }

   /** While the blade travels the hand-relative first-person sheath sinks out of the view as the
    * hand rises into the guard (and back as it comes down to sheathe), so it never pops. */
   static float firstPersonSheathDrop(ReinhardPoses.ReidPhase phase, double ticks, boolean strike) {
      if (strike || (phase != ReinhardPoses.ReidPhase.DRAWING && phase != ReinhardPoses.ReidPhase.SHEATHING)) return 0;
      return FIRST_PERSON_SHEATH_DROP * handWeight(phase, ticks);
   }

   /** First person: the blade bound to the right palm through the draw, with the first-person
    * roll; the sheath ({@code sheathShift} = its stack-space drop, null = hidden). Returns the
    * blade frame before its roll: the support hand grips that handle. */
   static org.joml.Matrix4f drawFirstPerson(PoseStack stack, org.joml.Matrix4f belt, org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase, double ticks, boolean strike, double actionTicks,
      @javax.annotation.Nullable org.joml.Matrix4f sheathShift, MultiBufferSource buffers, int light, int overlay) {
      org.joml.Matrix4f[] target=frames(belt,hand,phase,ticks,strike,actionTicks);
      if(phase==ReinhardPoses.ReidPhase.DRAWING || phase==ReinhardPoses.ReidPhase.SHEATHING)
         target[1]=bindGrip(target[1],hand);
      org.joml.Matrix4f blade=new org.joml.Matrix4f(target[1]);
      if(!strike)target[1].rotateY(drawnRoll(phase,ticks)*FIRST_PERSON_ROLL);
      if(sheathShift!=null)drawFrame(ReinhardAssets.SHEATH,stack,new org.joml.Matrix4f(sheathShift).mul(target[0]),buffers,light,overlay);
      drawFrame(ReinhardAssets.BLADE,stack,target[1],buffers,light,overlay);
      return blade;
   }

   static org.joml.Matrix4f[] frames(org.joml.Matrix4f hip,org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase,double ticks,boolean strike,double actionTicks) {
      float sw = strike ? strikeWeight(actionTicks) : 0;
      org.joml.Matrix4f sheath = interpolate(hip,hand,sw)
         .translate(ReinhardAssets.SHEATHED_OFFSET.x/16, -ReinhardAssets.SHEATHED_OFFSET.y/16, -ReinhardAssets.SHEATHED_OFFSET.z/16);
      org.joml.Matrix4f blade = strike ? interpolate(hip,hand,sw)
         : interpolate(new org.joml.Matrix4f(hip).translate(0,-ReinhardPoses.bladeOut(phase,ticks)*DRAW_OUT_PX/16,0), hand, handWeight(phase,ticks));
      return new org.joml.Matrix4f[]{sheath,blade};
   }

   /** Keep the blade in the actual glove through extraction/reinsertion, preserving
    * authored orientation and scale. Reach-limited arm + this rigid translation
    * replaces the impossible 28px straight pull by a human-reachable extraction. */
   static org.joml.Matrix4f bindGrip(org.joml.Matrix4f blade,org.joml.Matrix4f hand) {
      org.joml.Vector3f correction=hand.transformPosition(new org.joml.Vector3f(ReinhardGrip.HANDLE))
         .sub(blade.transformPosition(new org.joml.Vector3f(ReinhardGrip.HANDLE)));
      return new org.joml.Matrix4f(blade).m30(blade.m30()+correction.x).m31(blade.m31()+correction.y).m32(blade.m32()+correction.z);
   }

   private static void drawFrame(net.minecraft.resources.ResourceLocation resource, PoseStack stack, org.joml.Matrix4f frame,
      MultiBufferSource buffers, int light, int overlay) {
      BakedGeoModel model = AnimCache.model(resource);
      if (model == null) return;
      model.resetBones();
      stack.pushPose();
      stack.mulPoseMatrix(frame);
      stack.last().normal().mul(new org.joml.Matrix3f(frame).invert().transpose());
      draw(model,stack,buffers,light,overlay);
      stack.popPose();
   }

   private static void draw(BakedGeoModel model, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
      AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.entityCutoutNoCull(ReinhardAssets.SWORD_TEXTURE)), light, overlay, 1.0F, 1.0F, 1.0F,
         1.0F, null);
      if (!ShaderCompat.isShadowPass()) {
         AnimRenderer.render(model, stack, null, buffers.getBuffer(RenderType.eyes(ReinhardAssets.SWORD_GLOW)), LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, null);
      }
   }
}
