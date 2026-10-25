package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Energy projection of the existing Reid mesh, sampled from the attack's grip track.
 * Never edits the cached player skeleton or the physical sword's dimensions. */
public final class ReinhardTitanBlade {
   private ReinhardTitanBlade() {}
   static GeoBone sample(GeoBone source,GeoBone parent,Animation clip,double seconds) {
      GeoBone b=new GeoBone(source.name,parent,source.pivotX,source.pivotY,source.pivotZ,source.initRotX,source.initRotY,source.initRotZ);
      var a=clip.boneAnimations().get(b.name);if(a==null)return b;
      b.rotX+=a.rotX().sample(seconds);b.rotY+=a.rotY().sample(seconds);b.rotZ+=a.rotZ().sample(seconds);
      b.posX=a.posX().sample(seconds);b.posY=a.posY().sample(seconds);b.posZ=a.posZ().sample(seconds);
      b.scaleX=a.scaleX().sample(seconds);b.scaleY=a.scaleY().sample(seconds);b.scaleZ=a.scaleZ().sample(seconds);
      return b;
   }
   static Matrix4f gripFrame(double ticks) { return gripFrame("world_cut",ticks); }
   static Matrix4f gripFrame(String name,double ticks) {
      var body=AnimCache.model(ReinhardAssets.BODY);var clip=AnimCache.animation(ReinhardAssets.ANIMATIONS,name);
      if(body==null||clip==null)return null;
      GeoBone root=sample(body.getBone("body"),null,clip,ticks/20);
      GeoBone grip=sample(body.getBone("reid_grip"),root,clip,ticks/20);
      PoseStack s=new PoseStack();ReinhardRenderer.boneFrame(s,grip);return new Matrix4f(s.last().pose());
   }
   /** Same sampled draw grip as the physical mesh; no cached arm/bone mutation. */
   public static Vec3 drawPoint(net.minecraft.world.entity.LivingEntity caster,double ticks,double nativeY) {
      Matrix4f frame=gripFrame("reid_draw",ticks);if(frame==null)return caster.position().add(0,1,0);
      Matrix4f world=new Matrix4f().translation((float)caster.getX(),(float)caster.getY(),(float)caster.getZ())
         .rotateY((float)Math.toRadians(180-caster.yBodyRot)).scale(ReinhardRenderer.PLAYER_SCALE).mul(frame)
         .scale(ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE*ReinhardSword.LENGTH_SCALE,ReinhardSword.SWORD_SCALE)
         .translate(-ReinhardGrip.HANDLE.x,-ReinhardGrip.HANDLE.y,-ReinhardGrip.HANDLE.z);
      var p=world.transformPosition(new org.joml.Vector3f(0,(float)nativeY/16,0));return new Vec3(p.x,p.y,p.z);
   }
   public static void render(Vec3 origin,Vec3 direction,double ticks,PoseStack unused,Vec3 eye) {
      if(ticks<12||ticks>94)return;Matrix4f frame=gripFrame(ticks);if(frame==null)return;
      var blade=AnimCache.model(ReinhardAssets.BLADE);if(blade==null)return;
      float grow=(float)Math.min(1,(ticks-12)/28),alpha=(float)Math.min(1,(94-ticks)/10)*.72F;
      PoseStack s=new PoseStack();s.translate(origin.x-eye.x,origin.y-eye.y,origin.z-eye.z);
      s.mulPose(Axis.YP.rotationDegrees((float)(180-Math.toDegrees(Math.atan2(-direction.x,direction.z)))));
      s.mulPoseMatrix(frame);
      // Scale about the SAME grip, not the character origin: the sweep follows the real weapon.
      float k=65*grow;s.scale(k,k,k);
      s.scale(ReinhardSword.SWORD_SCALE,ReinhardSword.SWORD_SCALE*ReinhardSword.LENGTH_SCALE,ReinhardSword.SWORD_SCALE);
      s.translate(-ReinhardGrip.HANDLE.x,-ReinhardGrip.HANDLE.y,-ReinhardGrip.HANDLE.z);
      s.mulPose(Axis.YP.rotation((float)Math.PI/2));
      var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
      RenderType type=RenderType.entityTranslucent(ReinhardAssets.SWORD_TEXTURE);
      AnimRenderer.render(blade,s,null,buffers.getBuffer(type),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,.94F,.65F,alpha,null);
      buffers.endBatch(type);
   }
}
