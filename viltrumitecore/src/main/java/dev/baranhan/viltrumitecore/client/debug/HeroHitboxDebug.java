package dev.baranhan.viltrumitecore.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.hero.HeroBodySize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * F3+B for a scaled hero body seen from inside: vanilla draws no hitbox for the camera entity in
 * first person, so a big hero (Thor, the Hulkbuster) could not check its own box and eye height.
 * Draws the live box (white) and the eye plane (red, like vanilla) of the local player while
 * hitboxes are on, the view is first person and {@link HeroBodySize#of} is not vanilla. Debug only:
 * not an animation or VFX, nothing is drawn with F3+B off. Third person keeps vanilla's own
 * hitbox pass (EntityRenderDispatcher draws it even when a hero renderer cancels the model).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeroHitboxDebug {
   private HeroHitboxDebug() {
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || !mc.getEntityRenderDispatcher().shouldRenderHitBoxes() || !mc.options.getCameraType().isFirstPerson()
         || mc.getCameraEntity() != player || HeroBodySize.of(player).vanilla()) {
         return;
      }

      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      double x = Mth.lerp(partial, player.xo, player.getX()) - cam.x;
      double y = Mth.lerp(partial, player.yo, player.getY()) - cam.y;
      double z = Mth.lerp(partial, player.zo, player.getZ()) - cam.z;
      AABB box = player.getBoundingBox().move(-player.getX() + x, -player.getY() + y, -player.getZ() + z);
      PoseStack pose = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      VertexConsumer lines = buffers.getBuffer(RenderType.lines());
      LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 1.0F, 1.0F, 1.0F);
      double eye = box.minY + player.getEyeHeight();
      LevelRenderer.renderLineBox(pose, lines, new AABB(box.minX, eye - 0.01, box.minZ, box.maxX, eye + 0.01, box.maxZ), 1.0F, 0.0F, 0.0F, 1.0F);
      buffers.endBatch(RenderType.lines());
   }
}
