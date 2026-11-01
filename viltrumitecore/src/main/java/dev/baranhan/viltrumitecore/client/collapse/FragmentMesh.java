package dev.baranhan.viltrumitecore.client.collapse;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * One falling fragment baked once into a static GPU buffer (block models via renderSingleBlock, light
 * sampled where the blocks stood), then drawn every frame with one matrix: no per-frame model work, no
 * entities. Render thread only; {@link #close()} frees the buffer.
 */
final class FragmentMesh implements AutoCloseable {
   private VertexBuffer buffer;
   final int quads;

   /** The cutout block sheet draws solid, cutout and (as cutout) translucent blocks of a fragment. */
   static RenderType type() {
      return Sheets.cutoutBlockSheet();
   }

   private FragmentMesh(VertexBuffer buffer, int quads) {
      this.buffer = buffer;
      this.quads = quads;
   }

   /** Bakes the blocks (positions relative to the pivot); null when nothing is visible. */
   static FragmentMesh bake(BlockState[] states, float[] rx, float[] ry, float[] rz, int[] light, int count) {
      Minecraft mc = Minecraft.getInstance();
      BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
      BufferBuilder builder = new BufferBuilder(Math.max(256, count * 24 * DefaultVertexFormat.NEW_ENTITY.getVertexSize()));
      builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
      MultiBufferSource single = type -> builder;
      PoseStack pose = new PoseStack();
      int drawn = 0;
      for (int i = 0; i < count; i++) {
         BlockState state = states[i];
         if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            continue;
         }
         pose.pushPose();
         pose.translate(rx[i], ry[i], rz[i]);
         dispatcher.renderSingleBlock(state, pose, single, light[i], OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
         pose.popPose();
         drawn++;
      }
      BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
      if (rendered == null || drawn == 0) {
         if (rendered != null) {
            rendered.release();
         }
         return null;
      }
      VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
      vb.bind();
      vb.upload(rendered);
      VertexBuffer.unbind();
      return new FragmentMesh(vb, drawn * 6);
   }

   void draw(Matrix4f modelView) {
      if (this.buffer != null && !this.buffer.isInvalid()) {
         this.buffer.bind();
         this.buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
      }
   }

   @Override
   public void close() {
      if (this.buffer != null) {
         this.buffer.close();
         this.buffer = null;
      }
   }
}
