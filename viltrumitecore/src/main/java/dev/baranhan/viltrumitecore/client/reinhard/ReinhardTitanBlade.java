package dev.baranhan.viltrumitecore.client.reinhard;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.animation.Animation;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Energy projection of the existing Reid mesh, sampled from the attack's grip track.
 * Never edits the cached player skeleton or the physical sword's dimensions.
 *
 * <p>REINHARD OVERHAUL item 7: the projection used to be scaled about Reinhard's own hands, so its hilt, guard and
 * pommel (tens of blocks wide) sat on his body and filled the first-person camera. It now copies only the ROTATION
 * of the sampled grip (the same clip as his hands, both {@link dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep}
 * shoulders) and swings about its own hilt {@link #AHEAD} blocks ahead along the cut and {@link #RISE} blocks above
 * his feet (kept between {@link #RISE} and {@link #MAX_RISE} above the trench ground). Through the stroke an extra pitch about the cut's lateral axis brings the blade from his hand's angle to
 * the line from that hilt to the trench ground {@link #AIM_ALONG} blocks out, so on the BLAST it lays its edge into
 * the trench whether he stands on the ground or casts from the air. No part of the mesh comes within six blocks of
 * him or between his model and a third-person camera (behind him or in front of him); near any other camera it fades
 * instead of filling the screen.
 */
public final class ReinhardTitanBlade {
   /** Mesh scale of the projection: about 96 blocks of blade, 26 of guard. */
   static final float SCALE = 65;
   /** Hilt of the projection: ahead of Reinhard along the cut and above his feet (blocks). */
   static final double AHEAD = 20, RISE = 8;
   /** Highest hilt above the trench ground: from high in the air the blade still reaches the cut. */
   static final double MAX_RISE = 40;
   /** The contact aims at the trench ground this far along the cut (blocks from its start). */
   static final int AIM_ALONG = 72;
   static final int VISIBLE_FROM = 12, VISIBLE_TO = 94;
   /** Extra lean of the projection over the raised shoulder through the hold (degrees), gone by the contact. */
   static final float SHOULDER_LEAN = 12;
   /** Native mesh points of the pommel end and the tip (px / 16), blade.geo.json. */
   static final Vector3f POMMEL = new Vector3f(0, -8.5F / 16, 0), TIP = new Vector3f(0, 49.58F / 16, 0);
   /** Half-width of the blade at full scale (blocks): the camera fade measures from its edge. */
   static final double HALF_WIDTH = 2.12 / 16 * SCALE * ReinhardSword.SWORD_SCALE;

   private ReinhardTitanBlade() {}
   static GeoBone sample(GeoBone source,GeoBone parent,Animation clip,double seconds) {
      GeoBone b=new GeoBone(source.name,parent,source.pivotX,source.pivotY,source.pivotZ,source.initRotX,source.initRotY,source.initRotZ);
      var a=clip.boneAnimations().get(b.name);if(a==null)return b;
      b.rotX+=a.rotX().sample(seconds);b.rotY+=a.rotY().sample(seconds);b.rotZ+=a.rotZ().sample(seconds);
      b.posX=a.posX().sample(seconds);b.posY=a.posY().sample(seconds);b.posZ=a.posZ().sample(seconds);
      b.scaleX=a.scaleX().sample(seconds);b.scaleY=a.scaleY().sample(seconds);b.scaleZ=a.scaleZ().sample(seconds);
      return b;
   }
   static Matrix4f gripFrame(String name,double ticks) {
      var body=AnimCache.model(ReinhardAssets.BODY);var clip=AnimCache.animation(ReinhardAssets.ANIMATIONS,name);
      return body==null||clip==null?null:gripFrame(body,clip,ticks);
   }
   /** Pure: the grip bone frame of {@code clip} at {@code ticks}, on fresh bones (the cached skeleton is untouched). */
   static Matrix4f gripFrame(BakedGeoModel body,Animation clip,double ticks) {
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

   /** Where the projection is: its hilt (world) and the mesh transform about it (native blade px/16 to blocks). */
   record Placement(Vec3 hilt, Matrix4f local) {
      Vec3 point(Vector3f nativePoint) {
         Vector3f p = local.transformPosition(new Vector3f(nativePoint));
         return hilt.add(p.x, p.y, p.z);
      }
   }

   /** 0 through the hold, 1 from the contact: how much of the aim correction is applied. */
   static float swing(double ticks) {
      double t = Math.max(0, Math.min(1, (ticks - (ReinhardRules.WORLD_CUT_BLAST - 14)) / 14.0));
      return (float)(t * t * (3 - 2 * t));
   }

   static float grow(double ticks) {
      return (float)Math.max(0, Math.min(1, (ticks - VISIBLE_FROM) / 28.0));
   }

   /** Hilt of the projection for a cast at {@code origin} cutting along {@code dir} into ground at {@code aimY}. */
   static Vec3 hilt(Vec3 origin, Vec3 dir, double aimY) {
      double y = Math.max(aimY + RISE, Math.min(aimY + MAX_RISE, origin.y + RISE));
      return new Vec3(origin.x + dir.x * AHEAD, y, origin.z + dir.z * AHEAD);
   }

   /**
    * Pure placement at {@code ticks}. {@code grip} and {@code impactGrip} are grip frames of the same clip (now and
    * at the contact); {@code aimY} is the top of the trench ground at {@link #AIM_ALONG}, plus one; {@code side} is
    * the {@link dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep#side} of the raised shoulder.
    */
   static Placement placement(Vec3 origin, Vec3 dir, double aimY, double ticks, Matrix4f grip, Matrix4f impactGrip, int side) {
      Vec3 hilt = hilt(origin, dir, aimY);
      double run = ReinhardRules.CUT_START_OFFSET + AIM_ALONG - AHEAD;
      double aim = Math.atan2(hilt.y - aimY, run);
      Vector3f hand = impactGrip.transformDirection(new Vector3f(0, 1, 0)).normalize();
      double handPitch = Math.atan2(-hand.y, Math.hypot(hand.x, hand.z));
      float yaw = (float)(180 - Math.toDegrees(Math.atan2(-dir.x, dir.z)));
      float k = SCALE * grow(ticks);
      float swing = swing(ticks);
      Matrix4f local = new Matrix4f().rotateY((float)Math.toRadians(yaw))
         // Model z is the cut line: lean further over the raised shoulder (model +x is his right) while it is held.
         .rotateZ((float)Math.toRadians(-SHOULDER_LEAN * side * (1 - swing)))
         // Model x is the cut's lateral axis: lift (or lower) the blade from the hand's angle to the aim line.
         .rotateX((float)((handPitch - aim) * swing))
         .mul(new Matrix4f(grip).setTranslation(0, 0, 0))
         .scale(k)
         .scale(ReinhardSword.SWORD_SCALE, ReinhardSword.SWORD_SCALE * ReinhardSword.LENGTH_SCALE, ReinhardSword.SWORD_SCALE)
         .translate(-ReinhardGrip.HANDLE.x, -ReinhardGrip.HANDLE.y, -ReinhardGrip.HANDLE.z)
         .rotateY((float)Math.PI / 2);
      return new Placement(hilt, local);
   }

   /** 1 away from the camera, 0 when the camera is inside the blade: never a screen-filling translucent wall. */
   static float cameraFade(Placement placement, Vec3 eye) {
      Vec3 a = placement.point(POMMEL), b = placement.point(TIP), ab = b.subtract(a);
      double length = ab.lengthSqr();
      double t = length < 1E-9 ? 0 : Math.max(0, Math.min(1, eye.subtract(a).dot(ab) / length));
      double clear = eye.distanceTo(a.add(ab.scale(t))) - HALF_WIDTH;
      double x = Math.max(0, Math.min(1, clear / 5));
      return (float)(x * x * (3 - 2 * x));
   }

   static float alpha(double ticks) {
      return (float)Math.min(1, (VISIBLE_TO - ticks) / 10) * .72F;
   }

   /** Clip of the projection: the same shoulder as the caster's hands. */
   static String clip(int sweep) {
      return ReinhardClips.worldCut(sweep);
   }

   public static void render(Vec3 origin,Vec3 direction,int[] surface,int sweep,double ticks,Vec3 eye) {
      if(ticks<VISIBLE_FROM||ticks>VISIBLE_TO||surface.length==0)return;
      String name=clip(sweep);
      Matrix4f frame=gripFrame(name,ticks),impact=gripFrame(name,ReinhardRules.WORLD_CUT_BLAST);
      if(frame==null||impact==null)return;
      var blade=AnimCache.model(ReinhardAssets.BLADE);if(blade==null)return;
      Placement placement=placement(origin,direction,surface[Math.min(surface.length-1,AIM_ALONG)]+1.0,ticks,frame,impact,
         dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutSweep.side(sweep));
      float alpha=alpha(ticks)*cameraFade(placement,eye);
      if(alpha<=.004F)return;
      PoseStack s=new PoseStack();
      s.translate(placement.hilt().x-eye.x,placement.hilt().y-eye.y,placement.hilt().z-eye.z);
      s.mulPoseMatrix(placement.local());
      var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
      RenderType type=RenderType.entityTranslucent(ReinhardAssets.SWORD_TEXTURE);
      AnimRenderer.render(blade,s,null,buffers.getBuffer(type),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,1,.94F,.65F,alpha,null);
      buffers.endBatch(type);
   }
}
