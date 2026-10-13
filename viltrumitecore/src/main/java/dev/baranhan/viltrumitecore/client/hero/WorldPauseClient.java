package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid="viltrumitecore",value=Dist.CLIENT)
public final class WorldPauseClient {
   private static UUID pauseId,owner; private static int left;
   private static final List<Runnable> END_LISTENERS=new ArrayList<>();
   private WorldPauseClient() {}
   public static void onEnd(Runnable listener) {END_LISTENERS.add(listener);}
   public static void accept(WorldPauseS2CPacket p) {
      if(!p.active()) {if(pauseId==null || pauseId.equals(p.pauseId())) clear(); return;}
      if(pauseId!=null && !pauseId.equals(p.pauseId())) clear();
      pauseId=p.pauseId(); owner=p.owner(); left=Math.max(0,p.ticksLeft());
   }
   public static boolean active() {return pauseId!=null && left>0;}
   public static boolean isPaused() {
      var player=Minecraft.getInstance().player; return active() && player!=null && !player.getUUID().equals(owner);
   }
   public static boolean hidesAnchor(UUID effect) {return WorldPause.hidesAnchor(active(),pauseId,effect);}
   private static void clear() {pauseId=null; owner=null; left=0; for(Runnable r:END_LISTENERS) r.run();}
   @SubscribeEvent public static void onMovement(MovementInputUpdateEvent e) {
      if(!isPaused()) return;
      var input=e.getInput(); input.forwardImpulse=0; input.leftImpulse=0;
      input.up=false; input.down=false; input.left=false; input.right=false; input.jumping=false; input.shiftKeyDown=false;
      e.getEntity().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
   }
   @SubscribeEvent public static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
      if(isPaused()) {e.setCanceled(true); e.setSwingHand(false);}
   }
   @SubscribeEvent public static void onTick(TickEvent.ClientTickEvent e) {
      if(e.phase==TickEvent.Phase.END && pauseId!=null && --left<=0) clear();
   }
   @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {clear();}
}
