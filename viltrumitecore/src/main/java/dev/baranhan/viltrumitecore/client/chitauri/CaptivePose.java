package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriCaptor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * A person a Captor carries is drawn lying across its left shoulder (instead of standing upright next to it): the
 * renderer's own body yaw is undone and the body is laid horizontal along the captor's shoulder line, following the
 * captor's interpolated position so it does not lag a tick behind. Client only; nothing changes on the server.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class CaptivePose {
   /** carried entity id → captor id, rebuilt each client tick. */
   private static final Map<Integer, Integer> CARRIED = new HashMap<>();
   private static final Map<Integer, Boolean> PUSHED = new HashMap<>();

   private CaptivePose() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      CARRIED.clear();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      for (Entity e : mc.level.entitiesForRendering()) {
         if (e instanceof ChitauriCaptor c && c.isAlive() && c.carrying()) {
            CARRIED.put(c.carriedId(), c.getId());
         }
      }
   }

   /** Is this entity currently carried (client view)? */
   public static boolean carried(Entity e) {
      return CARRIED.containsKey(e.getId());
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
      LivingEntity e = event.getEntity();
      Integer captorId = CARRIED.get(e.getId());
      if (captorId == null || event.isCanceled()) {
         return;
      }
      if (!(e.level().getEntity(captorId) instanceof ChitauriCaptor c)) {
         return;
      }
      float partial = event.getPartialTick();
      PoseStack stack = event.getPoseStack();
      double cx = Mth.lerp(partial, c.xo, c.getX());
      double cy = Mth.lerp(partial, c.yo, c.getY());
      double cz = Mth.lerp(partial, c.zo, c.getZ());
      double ex = Mth.lerp(partial, e.xo, e.getX());
      double ey = Mth.lerp(partial, e.yo, e.getY());
      double ez = Mth.lerp(partial, e.zo, e.getZ());
      float cyaw = Mth.rotLerp(partial, c.yBodyRotO, c.yBodyRot);
      float eyaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      float rad = cyaw * Mth.DEG_TO_RAD;
      // the captor's left: (cos yaw, 0, sin yaw) in Minecraft's yaw convention
      double lx = Mth.cos(rad) * 0.28;
      double lz = Mth.sin(rad) * 0.28;
      float bob = Mth.sin((c.tickCount + partial) * 0.45F) * 0.03F * Math.min(1.0F, c.clientMove * 6.0F);
      stack.pushPose();
      stack.translate(cx + lx - ex, cy + 1.62 + bob - ey, cz + lz - ez);
      stack.mulPose(Axis.YP.rotationDegrees(-cyaw));
      stack.mulPose(Axis.XP.rotationDegrees(-90.0F));
      stack.translate(0.0, -e.getBbHeight() * 0.5, 0.0);
      // undo the living renderer's own body yaw so the lay-down axis is the captor's
      stack.mulPose(Axis.YP.rotationDegrees(-(180.0F - eyaw)));
      PUSHED.put(e.getId(), Boolean.TRUE);
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
      if (PUSHED.remove(event.getEntity().getId()) != null) {
         event.getPoseStack().popPose();
      }
   }
}
