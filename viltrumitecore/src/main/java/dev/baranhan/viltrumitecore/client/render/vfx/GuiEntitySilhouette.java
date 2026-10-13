package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/** Reuses the real entity renderer, replacing ONLY its model vertex material with white GUI quads. */
public final class GuiEntitySilhouette {
   private GuiEntitySilhouette() {}
   public static VertexConsumer white(VertexConsumer target, float alpha) {
      return new White(target, Math.round(255*Math.max(0,Math.min(1,alpha))));
   }
   public static void render(GuiGraphics gui, LivingEntity entity, float x, float y, float height, float partialTick, float alpha) {
      if(alpha <= 0) return;
      Minecraft mc=Minecraft.getInstance(); var dispatcher=mc.getEntityRenderDispatcher();
      Quaternionf camera=new Quaternionf(dispatcher.cameraOrientation()); var pose=gui.pose();
      gui.flush(); pose.pushPose();
      VertexConsumer white=white(gui.bufferSource().getBuffer(RenderType.gui()),alpha);
      try {
         float scale=height/Math.max(.1F,entity.getBbHeight());
         pose.translate(x,y+height*.5F,200); pose.scale(scale,scale,-scale); pose.mulPose(Axis.ZP.rotationDegrees(180));
         dispatcher.overrideCameraOrientation(new Quaternionf()); dispatcher.setRenderShadow(false);
         Lighting.setupForEntityInInventory();
         RenderSystem.runAsFancy(() -> dispatcher.render(entity,0,0,0,entity.getYRot(),partialTick,pose,
            // Font/leash/fire/debug/glint streams are not model geometry and must not enter this buffer.
            type -> type.format().equals(DefaultVertexFormat.NEW_ENTITY) ? white : Discard.INSTANCE, LightTexture.FULL_BRIGHT));
         gui.flush();
      } finally {
         white.unsetDefaultColor(); dispatcher.setRenderShadow(true); dispatcher.overrideCameraOrientation(camera);
         Lighting.setupFor3DItems(); pose.popPose();
      }
   }
   private record White(VertexConsumer target, int alpha) implements VertexConsumer {
      public VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
      public VertexConsumer color(int r,int g,int b,int a){target.color(255,255,255,alpha);return this;}
      public VertexConsumer uv(float u,float v){return this;}
      public VertexConsumer overlayCoords(int u,int v){return this;}
      public VertexConsumer uv2(int u,int v){return this;}
      public VertexConsumer normal(float x,float y,float z){return this;}
      public void endVertex(){target.endVertex();}
      public void defaultColor(int r,int g,int b,int a){target.defaultColor(255,255,255,alpha);}
      public void unsetDefaultColor(){target.unsetDefaultColor();}
   }
   private enum Discard implements VertexConsumer {
      INSTANCE;
      public VertexConsumer vertex(double x,double y,double z){return this;} public VertexConsumer color(int r,int g,int b,int a){return this;}
      public VertexConsumer uv(float u,float v){return this;} public VertexConsumer overlayCoords(int u,int v){return this;}
      public VertexConsumer uv2(int u,int v){return this;} public VertexConsumer normal(float x,float y,float z){return this;}
      public void endVertex(){} public void defaultColor(int r,int g,int b,int a){} public void unsetDefaultColor(){}
   }
}
