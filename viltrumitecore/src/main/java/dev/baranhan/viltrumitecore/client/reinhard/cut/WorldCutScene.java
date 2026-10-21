package dev.baranhan.viltrumitecore.client.reinhard.cut;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.baranhan.viltrumitecore.client.render.vfx.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut;
import dev.baranhan.viltrumitecore.network.packet.WorldCutS2CPacket;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

/** Packet/game-time pixel scene. Server DONE, not a fixed timer, releases the carve curtain. */
public final class WorldCutScene {
   public static final int END=190;
   private static final Map<UUID,Active> SCENES=new LinkedHashMap<>();
   private static Active[] frames=new Active[0];
   private static final Set<ResourceLocation> OWN_SOUNDS=Set.of(
      new ResourceLocation("viltrumitecore","reinhard.world_cut_hum"),
      new ResourceLocation("viltrumitecore","reinhard.world_cut_pulse"));
   private WorldCutScene() {}
   public static final class Active {
      public final WorldCutS2CPacket packet;
      private boolean blastReceived, blasted, ducked;
      private long lastPacket,doneAt=-1;
      public boolean working() { return doneAt<0; }
      public double fade(long now) { return doneAt<0?1:Math.max(0,1-(now-doneAt)/70.0); }
      /** Last elapsed tick strictly before DONE; +inf while the server still reports WORK. */
      public double acceptedUntil() { return doneAt<0?Double.POSITIVE_INFINITY:doneAt-packet.startTime()-1.0; }
      Active(WorldCutS2CPacket packet) { this.packet=packet; }
      public boolean blastReceived() { return blastReceived; }
   }
   public static Iterable<Active> active() { return SCENES.values(); }
   static Active[] frames() { return frames; }
   private static void refresh() { frames=SCENES.values().toArray(Active[]::new); }
   public static void onPacket(WorldCutS2CPacket packet) {
      Active scene=SCENES.computeIfAbsent(packet.castId(),id -> new Active(packet));
      scene.blastReceived |= packet.phase()!=WorldCutS2CPacket.START;
      var mc=Minecraft.getInstance();
      scene.lastPacket=mc==null||mc.level==null?packet.startTime():mc.level.getGameTime();
      if(packet.phase()==WorldCutS2CPacket.DONE&&scene.doneAt<0) {
         scene.doneAt=scene.lastPacket;
         if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,CLIENT_DONE,ground,{},{}",packet.castId(),scene.doneAt);
      }
      refresh();
   }
   public static double elapsed(long start,long now,double partial) { return now-start+partial; }
   public static boolean silence(double t) { return t>=ReinhardRules.WORLD_CUT_SWING && t<ReinhardRules.WORLD_CUT_BLAST; }
   public static int front(double t) { return WorldCut.visualFront(t); }
   public static boolean shakeCaster(boolean caster) { return true; }
   public static void clear() {
      for(Active a:SCENES.values()) { SoundDuck.INSTANCE.release(a.packet.castId());CutAtmosphere.release(a.packet.castId()); }
      SCENES.clear(); refresh(); WorldCutFov.reset(); WorldCutAudio.clear();
   }
   public static void prune(long now) {
      SCENES.values().removeIf(a -> {
         if(now-a.packet.startTime()<12000 && (a.doneAt<0 ? now-a.lastPacket<=140 : now-a.doneAt<=70)) return false;
         if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,FADE,ground,{},{},{}",a.packet.castId(),a.doneAt<0?-1:now-a.doneAt,Minecraft.getInstance().getFps());
         CutAtmosphere.release(a.packet.castId());
         SoundDuck.INSTANCE.release(a.packet.castId());
         WorldCutAudio.stop(a.packet.castId()); return true;
      });
      refresh();
   }
   public static void onUnload(LevelEvent.Unload event) {
      if(event.getLevel().isClientSide()) clear();
   }
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if(event.phase!=TickEvent.Phase.END) return;
      Minecraft mc=Minecraft.getInstance();
      if(mc.level==null || mc.player==null) { if(!SCENES.isEmpty()) clear(); return; }
      if(mc.isPaused()) return;
      long now=mc.level.getGameTime(); prune(now); WorldCutAudio.prune();
      for(Active scene:frames) {
         if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED && now%20==0)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,FPS,ground,{},{},{}",scene.packet.castId(),now-scene.packet.startTime(),mc.getFps());
         double t=elapsed(scene.packet.startTime(),now,0);
         double distance=WorldCutFov.distance(scene.packet.shape(),mc.player.getX(),mc.player.getZ());
         boolean inRange=distance<=ReinhardRules.WORLD_CUT_VIEW_RANGE;
         boolean hush=inRange && silence(t) && !scene.blastReceived;
         if(hush && !scene.ducked) {
            SoundDuck.INSTANCE.duck(scene.packet.castId(),SoundSource.MASTER,.15F,4,OWN_SOUNDS); scene.ducked=true;
         } else if(!hush && scene.ducked) {
            SoundDuck.INSTANCE.release(scene.packet.castId()); scene.ducked=false;
         }
         Vec3 nearest=nearest(scene,mc.player.position());
         double acceptedUntil=scene.acceptedUntil();
         WorldCutAudio.update(scene.packet.castId(),nearest,t,acceptedUntil,(float)scene.fade(now),inRange);
         if(inRange && WorldCutPulse.due((long)t,acceptedUntil)) WorldCutAudio.pulse(nearest,now);
         CutAtmosphere.update(scene.packet.castId(),nearest,t,scene.working(),scene.fade(now),inRange,now,
            WorldCutPulse.shake(t,acceptedUntil));
         if(hush) CameraShake.addAt(nearest,.3F,ReinhardRules.WORLD_CUT_VIEW_RANGE);
         if(inRange && !scene.working() && !scene.blasted) {
            scene.blasted=true;
            if(shakeCaster(scene.packet.casterId().equals(mc.player.getUUID()))) CameraShake.addAt(nearest,5,200);
            for(int a=0;a<200;a+=20) HeroImpactFx.shockwaveVisual(point(scene,a+.5),20);
         }
      }
   }
   private static Vec3 point(Active a,double along) {
      Vec3 p=a.packet.shape().axis(along);
      return new Vec3(p.x,a.packet.surface()[Math.max(0,Math.min(199,(int)along))]+1.04,p.z);
   }
   private static Vec3 nearest(Active scene,Vec3 listener) {
      Vec3 start=scene.packet.shape().axis(0), dir=scene.packet.shape().dirXZ();
      double along=Math.max(0,Math.min(199,(listener.x-start.x)*dir.x+(listener.z-start.z)*dir.z));
      return point(scene,along);
   }
   public static void onRender(RenderLevelStageEvent event) {
      if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || frames.length==0
         || dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass()) return;
      Minecraft mc=Minecraft.getInstance(); if(mc.level==null) return;
      Camera camera=event.getCamera(); Vec3 eye=camera.getPosition();
      PoseStack stack=RenderSystem.getModelViewStack(); stack.pushPose(); stack.setIdentity();
      PixelVfx.rotateCamera(stack,camera.getXRot(),camera.getYRot()); RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
      RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess=Tesselator.getInstance(); BufferBuilder buffer=tess.getBuilder();
      try {
         buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
         double pixelScale=Math.tan(Math.toRadians(mc.options.fov().get()/2.0))/Math.max(1,mc.getWindow().getHeight());
         double cloudY=mc.level.effects().getCloudHeight(); if(!Double.isFinite(cloudY)) cloudY=192;
         for(Active scene:frames) {
            double t=elapsed(scene.packet.startTime(),mc.level.getGameTime(),event.getPartialTick());
            if(t<8) continue;
            if(silence(t) && !scene.blastReceived) {
               for(double a=0;a<200;a+=.25) {
                  Vec3 ground=point(scene,a), sky=new Vec3(ground.x,cloudY,ground.z);
                  int alpha=(int)(190+45*Math.sin(t*.15+a*.1));
                  pixel(buffer,eye,camera,ground,(float)Math.max(.005,eye.distanceTo(ground)*pixelScale),255,255,255,alpha);
                  pixel(buffer,eye,camera,sky,(float)Math.max(.005,eye.distanceTo(sky)*pixelScale),255,255,255,alpha);
               }
               for(int a=0;a<200;a+=2) {
                  Vec3 p=point(scene,a).add(Math.sin(a*13)*2,.15+.02*Math.sin(t*3+a),Math.cos(a*9)*2);
                  pixel(buffer,eye,camera,p,.04F,148,131,107,145);
               }
            } else {
               int front=front(t); double fade=scene.fade(mc.level.getGameTime());
               Vec3 dir=scene.packet.shape().dirXZ();
               for(int a=0;a<front;a++) {
                  Vec3 p=point(scene,a+.5); double edge=scene.packet.shape().geometry().halfWidth(a,0)+.5;
                  for(int side=-1;side<=1;side+=2) {
                     Vec3 wall=p.add(-dir.z*edge*side,0,dir.x*edge*side);
                     for(int crack=0;crack<5;crack++) pixel(buffer,eye,camera,
                        wall.add(-dir.z*side*crack*.35,.04,dir.x*side*crack*.35),.08F,50,44,35,(int)(180*fade));
                  }
                  if(a>=front-3) {
                     double strike=Math.max(0,1-Math.abs(a-front+1)/3.0);
                     for(int y=0;y<40;y++) {
                        double spread=1+y*.07;
                        Vec3 core=p.add(-dir.z*Math.sin(y*.8+t*.2)*spread,y*.9,dir.x*Math.sin(y*.8+t*.2)*spread);
                        pixel(buffer,eye,camera,core,.22F,245,250,255,(int)(220*fade*strike));
                        if(y%3==0) pixel(buffer,eye,camera,core.add(-dir.z*1.4,0,dir.x*1.4),.10F,255,218,130,(int)(180*fade*strike));
                     }
                  }
                  if(a>=front-8) for(int y=0;y<12;y++) {
                     Vec3 fire=p.add(Math.sin(a*5+y)*4,y*.65,Math.cos(a*7+y)*4);
                     pixel(buffer,eye,camera,fire,.11F,255,239,155,(int)(220*fade));
                  }
                  if(a%2==0) for(int y=0;y<12;y++) {
                     double rise=((Math.max(0,t-80-a*.15)*.12)%14);
                     Vec3 dust=p.add(Math.sin(a*11+y)*(3+y*.8),rise+y*.8,Math.cos(a*7+y)*(3+y*.8));
                     pixel(buffer,eye,camera,dust,.18F+y*.015F,149,133,112,(int)(90*fade));
                  }
               }
               int pulseAge=WorldCutPulse.age(t,scene.acceptedUntil());
               if(pulseAge>=0 && pulseAge<WorldCutPulse.LENGTH) {
                  double swell=1.0-pulseAge/(double)WorldCutPulse.LENGTH;
                  for(int a=0;a<front;a+=3) {
                     Vec3 wound=point(scene,a+.5);
                     for(int side=-1;side<=1;side+=2) {
                        double reach=1.2+pulseAge*1.15;
                        for(int k=0;k<3;k++) {
                           double r=reach*(.7+k*.25)+Math.sin(a*.37+k)*.6;
                           Vec3 dust=wound.add(-dir.z*side*r,.3+pulseAge*.06+k*.35,dir.x*side*r);
                           pixel(buffer,eye,camera,dust,.15F+k*.01F,152,136,115,(int)(120*fade*swell));
                        }
                        if(pulseAge<8) {
                           Vec3 lip=wound.add(-dir.z*side*(reach+.4),.45,dir.x*side*(reach+.4));
                           pixel(buffer,eye,camera,lip,.09F,214,192,150,(int)(80*fade*swell));
                        }
                     }
                  }
               }
            }
         }
         tess.end();
         for(Active scene:frames) {
            double t=elapsed(scene.packet.startTime(),mc.level.getGameTime(),event.getPartialTick());
            dev.baranhan.viltrumitecore.client.reinhard.ReinhardTitanBlade.render(scene.packet.shape().origin(),
               scene.packet.shape().dirXZ(),t,event.getPoseStack(),eye);
         }
      } finally {
         RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend(); stack.popPose(); RenderSystem.applyModelViewMatrix();
      }
   }
   private static void pixel(BufferBuilder b,Vec3 eye,Camera c,Vec3 p,float size,int r,int g,int blue,int alpha) {
      PixelVfx.billboardPixel(b,eye,c,p,size,r,g,blue,alpha);
   }
}
