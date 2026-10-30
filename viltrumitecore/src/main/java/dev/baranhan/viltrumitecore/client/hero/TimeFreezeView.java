package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Local player in stopped time: the view is locked (mouse dropped in
 * TimeFreezeMouseMixin, yaw/pitch pinned here every tick and every frame), so
 * no camera turn leaks through other hooks either. The lock is derived live
 * from the frozen-id set: death, respawn (new entity id), level change,
 * logout and the end of the field all release it with nothing to undo.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class TimeFreezeView {
   private static boolean held;
   private static int heldId;
   private static float yaw, pitch;

   private TimeFreezeView() {
   }

   /** Live: the local player is alive and frozen right now. */
   public static boolean locked() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc == null ? null : mc.player;
      return player != null && player.isAlive() && !player.isSpectator()
         && (TimeFreeze.clientFrozen(player.getId()) || TimeFreeze.clientViewLocked());
   }

   /** Called by the frozen-set owner after every change, and every tick. */
   public static void refresh() {
      if (!locked()) {
         held = false;
         return;
      }

      LocalPlayer player = Minecraft.getInstance().player;
      if (!held || heldId != player.getId()) {
         held = true;
         heldId = player.getId();
         yaw = player.getYRot();
         pitch = player.getXRot();
         dev.baranhan.viltrumitecore.client.TargetLockManager.lockedTarget = null;
      }
   }

   /** The server corrected the view (teleport packet): that is the new lock. */
   public static void acceptServerView(float serverYaw, float serverPitch) {
      if (held) {
         yaw = serverYaw;
         pitch = serverPitch;
      }
   }

   /** Pin the view; safe to call any time. */
   public static void pin() {
      refresh();
      LocalPlayer player = Minecraft.getInstance().player;
      if (!held || player == null) {
         return;
      }

      // Anything but a server correction (acceptServerView) that turned us is undone.
      player.setYRot(yaw);
      player.setXRot(pitch);
      player.yRotO = yaw;
      player.xRotO = pitch;
      player.setYHeadRot(yaw);
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.START || event.phase == TickEvent.Phase.END) {
         pin();
      }
   }

   @SubscribeEvent
   public static void onRender(TickEvent.RenderTickEvent event) {
      if (event.phase == TickEvent.Phase.START) {
         pin();
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      TimeFreeze.clearClient();
      held = false;
   }
}
