package dev.baranhan.viltrumitecore.client.reinhard.cut;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.baranhan.viltrumitecore.client.render.vfx.*;
import dev.baranhan.viltrumitecore.network.packet.CutRainS2CPacket;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
/** Bounded, server-contact-driven waves; geometry never deals client damage. */
@EventBusSubscriber(modid="viltrumitecore",value=Dist.CLIENT)
public final class CutRainWaves {
   private static final ArrayDeque<CutRainS2CPacket> WAVES=new ArrayDeque<>();
   private static net.minecraft.client.multiplayer.ClientLevel last;
   private CutRainWaves() {}
   public static void receive(CutRainS2CPacket p) {
      var level=Minecraft.getInstance().level;if(level!=last){last=level;WAVES.clear();}
      if(WAVES.size()>=48)WAVES.removeFirst();WAVES.add(p);
      if(p.index()==11)CameraShake.cinematicAt(p.origin(),1.5F,48);
   }
   @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
      if(e.phase!=TickEvent.Phase.END)return;var m=Minecraft.getInstance();
      if(m.level!=last) {last=m.level;WAVES.clear();}
      if(m.level==null)return;WAVES.removeIf(w->m.level.getGameTime()-w.contactTime()>12);
   }
   @SubscribeEvent public static void render(RenderLevelStageEvent e) {
      if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||WAVES.isEmpty()||dev.baranhan.viltrumiteflight.client.util.ShaderCompat.isShadowPass())return;
      var m=Minecraft.getInstance();if(m.level==null)return;var c=e.getCamera();Vec3 eye=c.getPosition();
      PoseStack stack=RenderSystem.getModelViewStack();stack.pushPose();stack.setIdentity();PixelVfx.rotateCamera(stack,c.getXRot(),c.getYRot());RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();RenderSystem.setShader(GameRenderer::getPositionColorShader);
      var tess=Tesselator.getInstance();var b=tess.getBuilder();
      try {b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
         for(var w:WAVES) {
            double t=m.level.getGameTime()-w.contactTime()+e.getPartialTick();if(t<0||t>12)continue;
            Vec3 right=w.direction().cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
            Vec3 up=right.cross(w.direction()).normalize();double angle=(w.index()%3-1)*.65;
            Vec3 span=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            for(int trail=0;trail<4;trail++) {
               double distance=Math.max(0,Math.min(40,(t-trail*.35)*6));
               Vec3 centre=w.origin().add(w.direction().scale(distance));double radius=1.3+distance*.075;
               int alpha=(int)(230*Math.max(0,1-t/12)/(1+trail*.7));
               CrescentWave.draw(b,eye,c,centre,span,w.direction(),radius,1.2,20,trail==0?.13F:.08F,255,trail==0?249:216,trail==0?238:130,alpha);
            }
         }tess.end();
      } finally {RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();stack.popPose();RenderSystem.applyModelViewMatrix();}
   }
}
