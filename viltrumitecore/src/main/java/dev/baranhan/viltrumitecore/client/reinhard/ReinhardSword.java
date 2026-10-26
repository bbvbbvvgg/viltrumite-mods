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

   /** The freed blade settles into the hand after the release (first person); the same curve backwards when sheathing. */
   public static float handWeight(ReinhardPoses.ReidPhase phase, double ticks) {
      return switch (phase) {
         case DRAWN -> 1;
         case DRAWING, SHEATHING -> smooth((ReidDrawChoreo.drawTick(phase,ticks) - ReidDrawChoreo.RELEASE)
            / (ReidDrawChoreo.LENGTH - ReidDrawChoreo.RELEASE));
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
         case DRAWING, SHEATHING -> ReidDrawChoreo.roll(ReidDrawChoreo.drawTick(phase,ticks));
         default -> 0;
      };
   }
   static float twoHandWeight(ReinhardPoses.ReidPhase phase,double ticks) {
      return switch(phase) {
         case DRAWN -> 1;
         case DRAWING, SHEATHING -> ReidDrawChoreo.twoHandWeight(ReidDrawChoreo.drawTick(phase,ticks));
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

   /** Ordinary drawn strokes are one-handed: the left palm leaves the pommel for the
    * scabbard mouth during the first ticks and returns over the last ones. */
   public static float strikeRelease(double ticks,int length) {
      if(!Double.isFinite(ticks) || length<=0) return 0;
      return smooth(ticks/2.5)*smooth((length-ticks)/3);
   }

   static org.joml.Matrix4f[] equipment(BakedGeoModel body,ReinhardPoses.ReidPhase phase,
      double ticks,boolean strike,double actionTicks,float holyWeight) {
      return equipment(body,phase,ticks,strike,actionTicks,holyWeight,0);
   }

   /** TP choreography: one sampled grip bone, then both wrist constraints, then one
    * immutable pair of mesh frames. Never recompute the weapon from the solved arm.
    * {@code oneHand} (0..1) releases the support hand from the pommel (strikeRelease). */
   static org.joml.Matrix4f[] equipment(BakedGeoModel body,ReinhardPoses.ReidPhase phase,
      double ticks,boolean strike,double actionTicks,float holyWeight,float oneHand) {
      org.joml.Matrix4f hip=hip(body), blade=new org.joml.Matrix4f(hip);
      if(phase!=ReinhardPoses.ReidPhase.SHEATHED || strike) {
         PoseStack anchor=new PoseStack();ReinhardRenderer.boneFrame(anchor,body.getBone("reid_grip"));
         blade=new org.joml.Matrix4f(anchor.last().pose()).scale(SWORD_SCALE,SWORD_SCALE*LENGTH_SCALE,SWORD_SCALE)
            .translate(-ReinhardGrip.HANDLE.x,-ReinhardGrip.HANDLE.y,-ReinhardGrip.HANDLE.z);
      }
      if(!strike && (phase==ReinhardPoses.ReidPhase.DRAWING || phase==ReinhardPoses.ReidPhase.SHEATHING))
         return drawing(body,ReidDrawChoreo.drawTick(phase,ticks),hip,blade,holyWeight);
      org.joml.Matrix4f sheath=sheathOf(strike?blade:hip);
      // Sheath and assembled Sheath Strike keep their exact original frame.
      // The owner selected the +90-degree axial audit variant (front edge,
      // broad face from the side). Apply it only to the final free blade mesh,
      // after the unchanged grip solve below; never rotate the sheath or stance.
      float right=phase==ReinhardPoses.ReidPhase.DRAWN || strike?1:0;
      float gripWeight=0;
      float leftBlade=phase==ReinhardPoses.ReidPhase.SHEATHED?1:0;
      // Left hand grips the actual hilt until hand-off, then supports the sheath
      // mouth. It does not disappear because an unrelated action overlay is active.
      if(strike) {
         right=smooth((actionTicks-2)/2)*smooth((12-actionTicks)/3);
         ReinhardGrip.solve(body,blade,1-right,true);
      } else {
         org.joml.Matrix4f support=new org.joml.Matrix4f(hip).translate(0,3.3F/16,0);
         org.joml.Matrix4f leftTarget=interpolate(support,blade,leftBlade);
         ReinhardGrip.support(body,leftTarget,holyWeight,gripWeight);
      }
      // The short native hilt has two distinct grips: main hand by the guard,
      // support hand by the pommel. Never stack two glove centres at HANDLE.
      float pairedGrip=strike?0:twoHandWeight(phase,ticks);
      float twoHands=pairedGrip*(1-Math.max(0,Math.min(1,oneHand)));
      if(twoHands>0)ReinhardGrip.paired(body,gripAt(blade,-8.5F),holyWeight*twoHands,true);
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

   /** The scabbard of a blade frame that sits fully in it ("sheath as blade"). */
   static org.joml.Matrix4f sheathOf(org.joml.Matrix4f sheathAsBlade) {
      return new org.joml.Matrix4f(sheathAsBlade)
         .translate(ReinhardAssets.SHEATHED_OFFSET.x/16,-ReinhardAssets.SHEATHED_OFFSET.y/16,-ReinhardAssets.SHEATHED_OFFSET.z/16);
   }

   /** A weapon frame carried by an anchor bone (handle centre convention of reid_grip). */
   static org.joml.Matrix4f anchored(BakedGeoModel body,String bone) {
      PoseStack anchor=new PoseStack();ReinhardRenderer.boneFrame(anchor,body.getBone(bone));
      return new org.joml.Matrix4f(anchor.last().pose()).scale(SWORD_SCALE,SWORD_SCALE*LENGTH_SCALE,SWORD_SCALE)
         .translate(-ReinhardGrip.HANDLE.x,-ReinhardGrip.HANDLE.y,-ReinhardGrip.HANDLE.z);
   }

   /** REINHARD OVERHAUL item 15: the Reid draw / sheathe at draw tick {@code d} ({@link ReidDrawChoreo}).
    * The clip carries the blade (reid_grip, unrolled) and, once free, the scabbard (reid_sheath);
    * until the release the scabbard rides the blade, offset by the extraction. The left palm closes
    * on the scabbard throat (8-16) and carries it until it is docked (110); the right palm takes the
    * handle behind the guard (26-30). Both keep the clip's elbow planes (16-102). At d = 0 this is
    * exactly the sheathed equipment, at d = 120 exactly the Sword Drawn one. */
   static org.joml.Matrix4f[] drawing(BakedGeoModel body,double d,org.joml.Matrix4f hip,org.joml.Matrix4f blade,float holyWeight) {
      org.joml.Matrix4f sheathAsBlade=d>=ReidDrawChoreo.DOCKED ? new org.joml.Matrix4f(hip)
         : ReidDrawChoreo.sheathFree(d) && body.getBone("reid_sheath")!=null ? anchored(body,"reid_sheath")
         : new org.joml.Matrix4f(blade).translate(0,ReidDrawChoreo.extraction(d)/16,0);
      org.joml.Matrix4f throat=gripAt(sheathAsBlade,ReidDrawChoreo.MOUTH+ReidDrawChoreo.THROAT_GRIP);
      float poles=ReidDrawChoreo.poleWeight(d);
      // The left glove follows its forearm on the scabbard (grip weight 0): the throat frame is
      // ambiguous by a half turn about the scabbard axis, and chasing it flipped the wrist
      // through the clamp's antipode twice during the lift. The palm position stays exact.
      if(d<ReidDrawChoreo.THROAT_CLOSED) {
         float w=ReidDrawChoreo.throatWeight(d);
         ReinhardGrip.hold(body,interpolate(blade,throat,w),holyWeight,w,poles);
      } else if(d<ReidDrawChoreo.DOCKED) {
         ReinhardGrip.hold(body,throat,holyWeight,1,poles);
      } else {
         float w=ReidDrawChoreo.leaveThroat(d);
         ReinhardGrip.hold(body,leaving(body,throat,new org.joml.Matrix4f(hip).translate(0,3.3F/16,0),w),holyWeight,1-w,poles);
      }
      float twoHands=ReidDrawChoreo.twoHandWeight(d);
      if(twoHands>0)ReinhardGrip.paired(body,gripAt(blade,ReidDrawChoreo.POMMEL_GRIP),holyWeight*twoHands,holyWeight*twoHands,true,false,poles);
      float right=ReidDrawChoreo.rightWeight(d);
      if(right>0) {
         org.joml.Matrix4f target=gripAt(blade,ReidDrawChoreo.RIGHT_GRIP);
         if(twoHands>0)ReinhardGrip.paired(body,target,right,right*twoHands,false,true,poles);
         else ReinhardGrip.solve(body,target,right,false,true,null,poles);
      }
      return new org.joml.Matrix4f[]{sheathOf(sheathAsBlade),new org.joml.Matrix4f(blade).rotateY(ReidDrawChoreo.roll(d))};
   }

   /** Docked throat to the Sword Drawn support point along a quadratic arc through
    * {@link ReidDrawChoreo#SUPPORT_FRONT}, so the leaving palm never crosses the belly. */
   static org.joml.Matrix4f leaving(BakedGeoModel body,org.joml.Matrix4f throat,org.joml.Matrix4f support,float w) {
      org.joml.Matrix4f out=interpolate(throat,support,w);
      GeoBone bone=body.getBone(ReinhardAssets.BODY_BONE);
      if(bone==null || w<=0 || w>=1) return out;
      PoseStack frame=new PoseStack();ReinhardRenderer.boneFrame(frame,bone);
      float[] f=ReidDrawChoreo.SUPPORT_FRONT;
      org.joml.Vector3f front=frame.last().pose().transformPosition(new org.joml.Vector3f((-f[0]-bone.pivotX)/16,(f[1]-bone.pivotY)/16,(f[2]-bone.pivotZ)/16));
      org.joml.Vector3f a=throat.getTranslation(new org.joml.Vector3f()),b=support.getTranslation(new org.joml.Vector3f());
      // B(1/2) = front: control = 2 front - (a + b) / 2.
      org.joml.Vector3f control=new org.joml.Vector3f(front).mul(2).sub(new org.joml.Vector3f(a).add(b).mul(.5F));
      float u=1-w;
      org.joml.Vector3f p=new org.joml.Vector3f(a).mul(u*u).add(new org.joml.Vector3f(control).mul(2*u*w)).add(new org.joml.Vector3f(b).mul(w*w));
      return out.setTranslation(p);
   }

   static void drawEquipment(PoseStack stack,org.joml.Matrix4f[] frames,MultiBufferSource buffers,int light,int overlay) {
      drawFrame(ReinhardAssets.SHEATH,stack,frames[0],buffers,light,overlay);
      drawFrame(ReinhardAssets.BLADE,stack,frames[1],buffers,light,overlay);
   }

   /** Both meshes have a frame every render, so interruptions can recover without duplication. */
   static void drawAttached(Object player, PoseStack stack, org.joml.Matrix4f hip, org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase, double ticks, boolean strike, double actionTicks, int key,
      MultiBufferSource buffers, int light, int overlay) {
      drawAttached(player,stack,hip,hand,phase,ticks,strike,actionTicks,key,buffers,light,overlay,false);
   }

   static void drawAttached(Object player, PoseStack stack, org.joml.Matrix4f hip, org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase, double ticks, boolean strike, double actionTicks, int key,
      MultiBufferSource buffers, int light, int overlay, boolean physicalGrip) {
      org.joml.Matrix4f[] target=frames(hip,hand,phase,ticks,strike,actionTicks);
      if(physicalGrip && (phase==ReinhardPoses.ReidPhase.DRAWING || phase==ReinhardPoses.ReidPhase.SHEATHING))
         target[1]=bindGrip(target[1],hand);
      if(!strike)target[1]=rollDrawnBlade(target[1],phase,ticks);
      org.joml.Matrix4f[] frames = physicalGrip || (phase==ReinhardPoses.ReidPhase.SHEATHED && !strike) ? target
         : ReinhardRecovery.weapons(player,key,target[0],target[1],net.minecraft.Util.getNanos());
      drawFrame(ReinhardAssets.SHEATH, stack,frames[0],buffers,light,overlay);
      drawFrame(ReinhardAssets.BLADE, stack,frames[1],buffers,light,overlay);
   }

   static org.joml.Matrix4f[] frames(org.joml.Matrix4f hip,org.joml.Matrix4f hand,
      ReinhardPoses.ReidPhase phase,double ticks,boolean strike,double actionTicks) {
      float sw = strike ? strikeWeight(actionTicks) : 0;
      org.joml.Matrix4f sheath = interpolate(hip,hand,sw)
         .translate(ReinhardAssets.SHEATHED_OFFSET.x/16, -ReinhardAssets.SHEATHED_OFFSET.y/16, -ReinhardAssets.SHEATHED_OFFSET.z/16);
      org.joml.Matrix4f blade = strike ? interpolate(hip,hand,sw)
         : interpolate(new org.joml.Matrix4f(hip).translate(0,-extraction(phase,ticks)/16,0), hand, handWeight(phase,ticks));
      return new org.joml.Matrix4f[]{sheath,blade};
   }

   /** Blade travel out of the scabbard (native px) in a Reid phase: the draw timeline, FULL once in hand. */
   static float extraction(ReinhardPoses.ReidPhase phase,double ticks) {
      return switch(phase) {
         case DRAWN -> ReidDrawChoreo.FULL;
         case DRAWING, SHEATHING -> ReidDrawChoreo.extraction(ReidDrawChoreo.drawTick(phase,ticks));
         default -> 0;
      };
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
