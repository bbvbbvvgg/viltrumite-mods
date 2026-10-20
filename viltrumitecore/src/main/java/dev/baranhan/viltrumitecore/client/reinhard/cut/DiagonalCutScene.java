package dev.baranhan.viltrumitecore.client.reinhard.cut;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.baranhan.viltrumitecore.client.render.vfx.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** A tilted reality wound with a finite healing tail. The scar in blocks never heals. */
@EventBusSubscriber(modid="viltrumitecore",value=Dist.CLIENT)
public final class DiagonalCutScene {
   private static final Map<UUID,Scene> SCENES=new LinkedHashMap<>();
   private static final class Scene {
      final DiagonalCutS2CPacket packet;long last,done=-1;boolean blast,hush,tail;
      Scene(DiagonalCutS2CPacket p,long now) {packet=p;last=now;}
   }
   private DiagonalCutScene() {}
   public static void onPacket(DiagonalCutS2CPacket p) {
      var m=Minecraft.getInstance();long now=m==null||m.level==null?p.startTime():m.level.getGameTime();
      if(SCENES.size()>=16&&!SCENES.containsKey(p.castId()))return;
      Scene s=SCENES.computeIfAbsent(p.castId(),id->new Scene(p,now));s.last=now;
      if(p.phase()==3&&s.done<0)s.done=now;
   }
   private static Vec3 nearest(Scene s,Vec3 p) {
      var g=s.packet.shape();double a=Math.max(0,Math.min(200,p.subtract(g.origin()).dot(g.forward())));
      return g.point(a,0,0);
   }
   @SubscribeEvent public static void unload(LevelEvent.Unload e) {
      if(!e.getLevel().isClientSide())return;
      for(UUID id:SCENES.keySet())CutAtmosphere.release(id);SCENES.clear();
   }
   @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
      if(e.phase!=TickEvent.Phase.END)return;var m=Minecraft.getInstance();
      if(m.level==null||m.player==null) {for(UUID id:SCENES.keySet())CutAtmosphere.release(id);SCENES.clear();return;}
      if(m.isPaused())return;long now=m.level.getGameTime();
      SCENES.values().removeIf(s->{boolean end=now-s.last>140||now-s.packet.startTime()>12000||(s.done>=0&&now-s.done>90);
         if(end)CutAtmosphere.release(s.packet.castId());return end;});
      for(Scene s:SCENES.values()) {
         double t=now-s.packet.startTime();Vec3 near=nearest(s,m.player.position());boolean range=near.distanceTo(m.player.position())<=320;
         CutAtmosphere.update(s.packet.castId(),near,t,s.done<0,s.done<0?1:Math.max(0,1-(now-s.done)/90.0),range,now);
         if(!range)continue;
         if(t>=8&&s.done<0&&(now-s.packet.startTime())%28==0)play(near,ReinhardSounds.CUT_RUMBLE.get(),t<80?1.4F:2.8F);
         if(t>=20&&!s.hush) {play(near,ReinhardSounds.WORLD_CUT_SILENCE.get(),1.2F);s.hush=true;}
         if(t>=80&&!s.blast) {play(near,ReinhardSounds.WORLD_CUT_BLAST.get(),4);HeroImpactFx.shockwaveVisual(near,28);s.blast=true;}
         if(s.done>=0&&!s.tail) {play(near,ReinhardSounds.WORLD_CUT_TAIL.get(),2);s.tail=true;}
      }
   }
   private static void play(Vec3 p,net.minecraft.sounds.SoundEvent s,float v) {
      Minecraft.getInstance().level.playLocalSound(p.x,p.y,p.z,s,SoundSource.PLAYERS,v,1,false);
   }
   @SubscribeEvent public static void render(RenderLevelStageEvent e) {
      if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||SCENES.isEmpty()
         ||dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass())return;
      var m=Minecraft.getInstance();if(m.level==null)return;var camera=e.getCamera();Vec3 eye=camera.getPosition();
      PoseStack stack=RenderSystem.getModelViewStack();stack.pushPose();stack.setIdentity();
      PixelVfx.rotateCamera(stack,camera.getXRot(),camera.getYRot());RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);var tess=Tesselator.getInstance();var b=tess.getBuilder();
      try {
         b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
         for(Scene s:SCENES.values()) {
            double t=m.level.getGameTime()-s.packet.startTime()+e.getPartialTick();if(t<20)continue;
            var g=s.packet.shape();double fade=s.done<0?1:Math.max(0,1-(m.level.getGameTime()-s.done+e.getPartialTick())/90.0);
            double open=t<80?.02:Math.min(1,(t-80)/10)*fade;
            double front=t<80?200:WorldCutScene.front(t);
            // 200 x 2 ribbons, real 3-D sheet volume. Edges close only after server DONE.
            for(int a=0;a<front;a+=2) {
               double w=g.width(a),gap=(.25+.6*Math.sin(a*.17+t*.03))*open+1.4*open;
               Vec3 low=g.point(a,-w,0),high=g.point(a,w,0),lo2=g.point(Math.min(200,a+2),-g.width(a+2),0),hi2=g.point(Math.min(200,a+2),g.width(a+2),0);
               Vec3 n=g.normal().scale(gap);
               quad(b,eye,low.subtract(n),lo2.subtract(n),hi2.add(n),high.add(n),3,7,16,(int)(235*fade));
               for(int side:new int[]{-1,1}) {
                  Vec3 c=g.point(a,side*w,0),d=g.point(a+2,side*g.width(a+2),0);
                  quad(b,eye,c.subtract(n),d.subtract(n),d.add(n),c.add(n),255,233,170,(int)(240*fade));
                  // Seeded branching cracks and floating, bounded fragments on each lip.
                  if(a%4==0) for(int k=0;k<4;k++) {
                     Vec3 p=c.add(g.span().scale(side*k*.8)).add(g.forward().scale(Math.sin(a+k)*.6));
                     PixelVfx.billboardPixel(b,eye,camera,p,.11F,255,245,215,(int)(180*fade/(1+k*.2)));
                  }
                  if(a%8==0) {
                     Vec3 p=c.add(g.normal().scale(Math.sin(t*.12+a)*2)).add(g.span().scale(side*2));
                     PixelVfx.billboardPixel(b,eye,camera,p,.35F,130,158,184,(int)(145*fade));
                  }
               }
            }
         }
         tess.end();
      } finally {RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();stack.popPose();RenderSystem.applyModelViewMatrix();}
   }
   static void quad(BufferBuilder b,Vec3 eye,Vec3 a,Vec3 c,Vec3 d,Vec3 f,int r,int g,int blue,int alpha) {
      for(Vec3 p:new Vec3[]{a,c,d,f})b.vertex(p.x-eye.x,p.y-eye.y,p.z-eye.z).color(r,g,blue,alpha).endVertex();
   }
}
