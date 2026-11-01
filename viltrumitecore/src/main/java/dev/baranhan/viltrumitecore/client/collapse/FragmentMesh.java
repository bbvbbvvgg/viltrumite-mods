package dev.baranhan.viltrumitecore.client.collapse;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.List;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * One falling fragment baked once into a static GPU buffer (the blocks' baked model quads, inner faces culled, light
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

   private static final Direction[] FACES = Direction.values();

   /**
    * Bakes the blocks (positions relative to the pivot); null when nothing is visible. Faces hidden by a full
    * opaque neighbour of the same fragment are skipped (found in game: three simultaneous collapses under software
    * rendering spent ~17 ms a frame on fragment faces that can never be seen).
    */
   static FragmentMesh bake(BlockState[] states, float[] rx, float[] ry, float[] rz, int[] light, int count) {
      Minecraft mc = Minecraft.getInstance();
      BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
      BlockColors colors = mc.getBlockColors();
      Long2IntOpenHashMap index = new Long2IntOpenHashMap(count * 2);
      index.defaultReturnValue(-1);
      for (int i = 0; i < count; i++) {
         index.put(dev.baranhan.viltrumitecore.world.collapse.BlockKey.of(Math.round(rx[i]), Math.round(ry[i]), Math.round(rz[i])), i);
      }
      BufferBuilder builder = new BufferBuilder(Math.max(256, count * 12 * DefaultVertexFormat.NEW_ENTITY.getVertexSize()));
      builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
      PoseStack pose = new PoseStack();
      RandomSource random = RandomSource.create();
      int quads = 0;
      for (int i = 0; i < count; i++) {
         BlockState state = states[i];
         if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            continue;
         }
         BakedModel model = dispatcher.getBlockModel(state);
         int x = Math.round(rx[i]), y = Math.round(ry[i]), z = Math.round(rz[i]);
         pose.pushPose();
         pose.translate(rx[i], ry[i], rz[i]);
         random.setSeed(42L);
         for (RenderType layer : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            for (int f = 0; f <= FACES.length; f++) {
               Direction face = f < FACES.length ? FACES[f] : null;
               if (face != null && occluded(states, index, x + face.getStepX(), y + face.getStepY(), z + face.getStepZ())) {
                  continue;
               }
               random.setSeed(42L);
               List<BakedQuad> list = model.getQuads(state, face, random, ModelData.EMPTY, layer);
               for (BakedQuad quad : list) {
                  float r = 1.0F, g = 1.0F, b = 1.0F;
                  if (quad.isTinted()) {
                     int c = colors.getColor(state, null, null, quad.getTintIndex());
                     r = (c >> 16 & 255) / 255.0F;
                     g = (c >> 8 & 255) / 255.0F;
                     b = (c & 255) / 255.0F;
                  }
                  builder.putBulkData(pose.last(), quad, r, g, b, light[i], OverlayTexture.NO_OVERLAY);
                  quads++;
               }
            }
         }
         pose.popPose();
      }
      BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
      if (rendered == null || quads == 0) {
         if (rendered != null) {
            rendered.release();
         }
         return null;
      }
      VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
      vb.bind();
      vb.upload(rendered);
      VertexBuffer.unbind();
      return new FragmentMesh(vb, quads);
   }

   private static boolean occluded(BlockState[] states, Long2IntOpenHashMap index, int x, int y, int z) {
      int j = index.get(dev.baranhan.viltrumitecore.world.collapse.BlockKey.of(x, y, z));
      if (j < 0) {
         return false;
      }
      BlockState n = states[j];
      return n != null && n.getRenderShape() == RenderShape.MODEL && n.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
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
