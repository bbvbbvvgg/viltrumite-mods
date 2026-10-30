package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.network.packet.WorldPauseS2CPacket;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client half of the absolute stop. While a pause runs every entity but its owner is held
 * through the shared TimeFreeze client flag (no simulation, lerp, walk/idle or particle motion),
 * every player's view is pinned (owner included), input is dropped, and the local body is held
 * exactly where the stop found it (position and view bob), so the camera cannot drift.
 */
@EventBusSubscriber(modid="viltrumitecore",value=Dist.CLIENT)
public final class WorldPauseClient {
   private static UUID pauseId,owner; private static int left;
   private static final List<Runnable> END_LISTENERS=new ArrayList<>();
   /** Local body held by the stop; re-anchored by a server position correction. */
   private static Hold hold;
   private WorldPauseClient() {}
   public static void onEnd(Runnable listener) {END_LISTENERS.add(listener);}
   public static void accept(WorldPauseS2CPacket p) {
      if(!p.active()) {if(pauseId==null || pauseId.equals(p.pauseId())) clear(); return;}
      if(pauseId!=null && !pauseId.equals(p.pauseId())) clear();
      pauseId=p.pauseId(); owner=p.owner(); left=Math.max(0,p.ticksLeft());
      publish();
   }
   public static boolean active() {return pauseId!=null && left>0;}
   public static int remainingTicks() {return Math.max(0,left);}
   @javax.annotation.Nullable public static UUID owner() {return active()?owner:null;}
   /** Same unpaused local clock as the cinematic, including an integrated game. */
   public static void tick(boolean gamePaused) {
      if(!gamePaused && pauseId!=null && --left<=0) clear();
   }
   /** A non-owner player in the pause (legacy predicate). */
   public static boolean isPaused() {
      var player=Minecraft.getInstance().player; return active() && player!=null && !player.getUUID().equals(owner);
   }
   /** Everyone's input stops, the owner's too: the scene owns the world. Limbo is a separate world. */
   public static boolean blocksInput() {
      var player=Minecraft.getInstance().player; return active() && player!=null && !BodyIsolationClient.active(player);
   }
   /** Pure: the world is held for every entity but the owner, the view for everyone outside isolation. */
   public static boolean holdsWorld(boolean active,boolean isolated) {return active && !isolated;}
   public static boolean hidesAnchor(UUID effect) {return WorldPause.hidesAnchor(active(),pauseId,effect);}
   private static void clear() {
      pauseId=null; owner=null; left=0; hold=null;
      TimeFreeze.setClientWorld(false,Integer.MIN_VALUE,false); TimeFreezeView.refresh();
      for(Runnable r:END_LISTENERS) r.run();
   }
   /** Publish the stop to the shared TimeFreeze client flags (owner id resolves late after a join). */
   private static void publish() {
      Minecraft mc=Minecraft.getInstance(); LocalPlayer player=mc.player;
      boolean isolated=BodyIsolationClient.active(player);
      boolean on=holdsWorld(active(),isolated) && mc.level!=null;
      int exempt=Integer.MIN_VALUE;
      if(on && owner!=null) {var caster=mc.level.getPlayerByUUID(owner); if(caster!=null) exempt=caster.getId();}
      TimeFreeze.setClientWorld(on,exempt,on);
      TimeFreezeView.refresh();
      if(!on || player==null) {hold=null; return;}
      if(hold==null || hold.entityId!=player.getId()) hold=Hold.of(player);
   }
   /** The server corrected the local position during the stop: that is the new anchor. */
   public static void acceptServerPosition() {
      LocalPlayer player=Minecraft.getInstance().player;
      if(hold!=null && player!=null && hold.entityId==player.getId()) hold=Hold.of(player);
   }
   private record Hold(int entityId,Vec3 at,float walkDist,float walkDistO,float bob,float oBob) {
      static Hold of(LocalPlayer p) {return new Hold(p.getId(),p.position(),p.walkDist,p.walkDistO,p.bob,p.oBob);}
      void apply(LocalPlayer p) {
         p.setDeltaMovement(Vec3.ZERO); p.setPos(at); p.xo=at.x; p.yo=at.y; p.zo=at.z; p.xOld=at.x; p.yOld=at.y; p.zOld=at.z;
         p.walkDist=walkDist; p.walkDistO=walkDistO; p.bob=bob; p.oBob=oBob; p.fallDistance=0;
      }
   }
   @SubscribeEvent public static void onMovement(MovementInputUpdateEvent e) {
      if(!blocksInput()) return;
      var input=e.getInput(); input.forwardImpulse=0; input.leftImpulse=0;
      input.up=false; input.down=false; input.left=false; input.right=false; input.jumping=false; input.shiftKeyDown=false;
      e.getEntity().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
   }
   @SubscribeEvent public static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
      if(blocksInput()) {e.setCanceled(true); e.setSwingHand(false);}
   }
   @SubscribeEvent(priority=EventPriority.LOWEST) public static void onTick(TickEvent.ClientTickEvent e) {
      Minecraft mc=Minecraft.getInstance();
      if(e.phase==TickEvent.Phase.END) tick(mc.isPaused());
      if(pauseId!=null) publish();
      if(hold!=null && mc.player!=null && mc.player.getId()==hold.entityId && TimeFreeze.clientWorldStopped()) hold.apply(mc.player);
   }
   @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {clear();}
}
