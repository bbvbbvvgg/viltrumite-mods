package dev.baranhan.viltrumitecore.client.reinhard.cut;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.baranhan.viltrumitecore.client.render.erasure.ErasureRenderer;
import dev.baranhan.viltrumitecore.client.render.vfx.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.cut.DiagonalCutGeometry;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.network.packet.DiagonalCutS2CPacket;
import java.util.*;
import net.minecraft.client.Camera;
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

/**
 * A tilted reality wound with a finite healing tail. The scar in blocks never heals.
 *
 * <p>REINHARD OVERHAUL items 10 and 14: the wound itself is The Hand erasure of {@code /erasuretest}
 * ({@link DiagonalCutErasure}, drawn by the erasure renderer), and the blade crescent of Reinhard's aerial diagonal
 * strikes ({@link CrescentWave}, the Cut Rain wave) flies out of it along the cut with the carving front. The old
 * narrow aperture rift is kept only as the fallback for when the erasure cannot be drawn (a shader pack owns the
 * frame, or the shader failed to load), so there is never a second rift. Sounds and timing are untouched.
 */
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
      DiagonalCutErasure.INSTANCE.track(p,m==null?null:m.level);
      if(p.phase()==3&&s.done<0) {s.done=now;
         if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,CLIENT_DONE,diagonal,{},{}",p.castId(),now);
      }
   }
   private static Vec3 nearest(Scene s,Vec3 p) {
      var g=s.packet.shape();double a=Math.max(0,Math.min(200,p.subtract(g.origin()).dot(g.forward())));
      return g.point(a,0,0);
   }
   @SubscribeEvent public static void unload(LevelEvent.Unload e) {
      if(!e.getLevel().isClientSide())return;
      for(UUID id:SCENES.keySet())CutAtmosphere.release(id);SCENES.clear();CutFinishSound.clear();DiagonalCutErasure.INSTANCE.clear();
   }
   @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
      if(e.phase!=TickEvent.Phase.END)return;var m=Minecraft.getInstance();
      if(m.level==null||m.player==null) {for(UUID id:SCENES.keySet())CutAtmosphere.release(id);SCENES.clear();CutFinishSound.clear();DiagonalCutErasure.INSTANCE.clear();return;}
      if(m.isPaused())return;long now=m.level.getGameTime();
      SCENES.values().removeIf(s->{boolean end=now-s.last>140||now-s.packet.startTime()>12000||(s.done>=0&&now-s.done>70);
         if(end) {CutAtmosphere.release(s.packet.castId());
            if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,FADE,diagonal,{},{},{}",s.packet.castId(),s.done<0?-1:now-s.done,m.getFps());}return end;});
      for(Scene s:SCENES.values()) {
         if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED && now%20==0)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,FPS,diagonal,{},{},{}",s.packet.castId(),now-s.packet.startTime(),m.getFps());
         double t=now-s.packet.startTime();Vec3 near=nearest(s,m.player.position());boolean range=near.distanceTo(m.player.position())<=320;
         CutAtmosphere.update(s.packet.castId(),near,t,s.done<0,s.done<0?1:Math.max(0,1-(now-s.done)/70.0),range,now);
         if(!range)continue;
         if(CutFinishSound.pulseDue(now-s.packet.startTime(),s.done<0))CutFinishSound.pulse(near,now);
         if(t>=20&&!s.hush) {play(near,ReinhardSounds.WORLD_CUT_SILENCE.get(),1.2F);s.hush=true;}
         if(s.done>=0&&!s.blast) {CutFinishSound.play(near,now);HeroImpactFx.shockwaveVisual(near,28);s.blast=true;}
         if(s.done>=0)s.tail=true;
      }
   }
   private static void play(Vec3 p,net.minecraft.sounds.SoundEvent s,float v) {
      Minecraft.getInstance().level.playLocalSound(p.x,p.y,p.z,s,SoundSource.PLAYERS,v,1,false);
   }
   @SubscribeEvent public static void render(RenderLevelStageEvent e) {
      if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||SCENES.isEmpty()
         ||dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass())return;
      var m=Minecraft.getInstance();if(m.level==null)return;var camera=e.getCamera();Vec3 eye=camera.getPosition();
      // Item 10: the wound is The Hand erasure; the aperture rift only stands in when that cannot be drawn.
      boolean rift=!ErasureRenderer.available();
      PoseStack stack=RenderSystem.getModelViewStack();stack.pushPose();stack.setIdentity();
      PixelVfx.rotateCamera(stack,camera.getXRot(),camera.getYRot());RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);var tess=Tesselator.getInstance();var b=tess.getBuilder();
      try {
         b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
         for(Scene s:SCENES.values()) {
            double t=m.level.getGameTime()-s.packet.startTime()+e.getPartialTick();if(t<20)continue;
            var g=s.packet.shape();
            wave(b,eye,camera,g,t);
            if(rift) {
               double fade=s.done<0?1:Math.max(0,1-(m.level.getGameTime()-s.done+e.getPartialTick())/70.0);
               rift(b,eye,camera,g,t,fade);
            }
         }
         tess.end();
      } finally {RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();stack.popPose();RenderSystem.applyModelViewMatrix();}
   }
   /** Echoes of the blade wave behind its front, the lag of each in ticks, as in Cut Rain. */
   static final int WAVE_ECHOES=4;
   static final double WAVE_ECHO_LAG=.5, WAVE_FADE_TICKS=6;
   /** Pixels on the wave: about two thirds of a block apart across a 50-block crescent. */
   static final int WAVE_POINTS=96;
   /** Distance along the cut of the carving front at elapsed tick t, blocks; unclamped (negative before BLAST). */
   static double waveAlong(double t) {
      return (t-ReinhardRules.WORLD_CUT_BLAST)*ReinhardRules.CUT_LENGTH/ReinhardRules.WORLD_CUT_WAVE_TICKS;
   }
   /** Elapsed tick at which the front reaches the end of the cut. */
   static double waveEnd() {
      return ReinhardRules.WORLD_CUT_BLAST+ReinhardRules.WORLD_CUT_WAVE_TICKS;
   }
   /**
    * Opacity of echo {@code trail} of the wave at elapsed tick t (0..1): it sets off from the erasure wound
    * ({@code from} blocks along the cut, so the two read as one strike), rises over 8 blocks, and dies out over
    * {@link #WAVE_FADE_TICKS} once the front has crossed the whole cut. 0 when it is not drawn.
    */
   static double waveAlpha(double t,int trail,double from) {
      double along=waveAlong(t-trail*WAVE_ECHO_LAG);
      if(along<from)return 0;
      double rise=Math.min(1,(along-from)/8), out=1-Math.max(0,Math.min(1,(t-waveEnd())/WAVE_FADE_TICKS));
      return Math.max(0,rise*out)/(1+trail*.7);
   }
   /** Centre of echo {@code trail} on the cut's axis at elapsed tick t; it rests at the end of the cut. */
   static Vec3 waveCentre(DiagonalCutGeometry g,double t,int trail) {
      return g.point(Math.min(DiagonalCutGeometry.LENGTH,waveAlong(t-trail*WAVE_ECHO_LAG)),0,0);
   }
   /** Half width of the wave: nine tenths of the cut's width there (the wave tapers with the cut). */
   static double waveRadius(DiagonalCutGeometry g,double along) {
      return g.width(Math.min(DiagonalCutGeometry.LENGTH-1,along))*.9;
   }
   /** Item 14: the Cut Rain crescent at the size of the cut, flying with the carving front. */
   static void wave(BufferBuilder b,Vec3 eye,Camera camera,DiagonalCutGeometry g,double t) {
      if(t<ReinhardRules.WORLD_CUT_BLAST||t>waveEnd()+WAVE_FADE_TICKS)return;
      double from=DiagonalCutErasure.along(g);
      for(int trail=0;trail<WAVE_ECHOES;trail++) {
         int alpha=(int)(230*waveAlpha(t,trail,from));if(alpha<=0)continue;
         Vec3 centre=waveCentre(g,t,trail);
         double radius=waveRadius(g,Math.min(DiagonalCutGeometry.LENGTH,waveAlong(t-trail*WAVE_ECHO_LAG)));
         CrescentWave.draw(b,eye,camera,centre,g.span(),g.forward(),radius,radius*.22,WAVE_POINTS,trail==0?.35F:.22F,
            255,trail==0?249:216,trail==0?238:130,alpha);
      }
   }
   /** The former narrow aperture rift, now only the fallback when The Hand erasure cannot be drawn. */
   static void rift(BufferBuilder b,Vec3 eye,Camera camera,DiagonalCutGeometry g,double t,double fade) {
      double open=t<80?.02:Math.min(1,(t-80)/10)*fade;
      double front=t<80?200:WorldCutScene.front(t);
      // Narrow spatial aperture, not a filled 56-block gray sheet. A recessed
      // opaque core and two sidewalls give parallax; thin irregular lips glow.
      Vec3 normal=g.normal(), span=g.span();
      for(int a=0;a<front;a+=2) {
         double end=Math.min(200,a+2);
         double taper=Math.min(1,Math.min((a+2)/16.0,(200-a)/22.0));
         double aperture=(.32+.08*Math.sin(a*.83+(g.seed()&255)))*open*taper;
         if(aperture<.001)continue;
         double aperture2=(.32+.08*Math.sin(end*.83+(g.seed()&255)))*open*taper;
         Vec3 c=g.point(a,0,0), d=g.point(end,0,0);
         Vec3 left=c.subtract(span.scale(aperture)),right=c.add(span.scale(aperture));
         Vec3 left2=d.subtract(span.scale(aperture2)),right2=d.add(span.scale(aperture2));
         // Two inward-facing cavities: depth is visible from either side of the cut.
         for(int face=-1;face<=1;face+=2) {
            Vec3 lip=normal.scale(face*.14*open), recess=normal.scale(-face*.48*open);
            quad(b,eye,left.add(recess),left2.add(recess),right2.add(recess),right.add(recess),1,1,3,255);
            for(int side=-1;side<=1;side+=2) {
               Vec3 edge=c.add(span.scale(side*aperture)),edge2=d.add(span.scale(side*aperture2));
               quad(b,eye,edge.add(lip),edge2.add(lip),edge2.add(recess),edge.add(recess),8,6,14,255);
               Vec3 rim=span.scale(side*.055*open);
               quad(b,eye,edge.add(lip),edge2.add(lip),edge2.add(lip).add(rim),edge.add(lip).add(rim),255,224,140,(int)(235*fade));
               if(a%8==0) {
                  Vec3 p=edge.add(lip).add(span.scale(side*(.15+.15*Math.sin(t*.06+a))));
                  PixelVfx.billboardPixel(b,eye,camera,p,.08F,255,247,220,(int)(190*fade));
                  PixelVfx.billboardPixel(b,eye,camera,p.add(normal.scale(face*.22)),.035F,255,209,110,(int)(120*fade));
               }
            }
         }
      }
   }
   static void quad(BufferBuilder b,Vec3 eye,Vec3 a,Vec3 c,Vec3 d,Vec3 f,int r,int g,int blue,int alpha) {
      for(Vec3 p:new Vec3[]{a,c,d,f})b.vertex(p.x-eye.x,p.y-eye.y,p.z-eye.z).color(r,g,blue,alpha).endVertex();
   }
}
