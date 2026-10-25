package dev.baranhan.viltrumitecore.client.reinhard.limbo;

import dev.baranhan.viltrumitecore.client.hero.BodyIsolationClient;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;

/** HUD, never a Screen: chat, escape/pause and F1 keep their vanilla behaviour. */
public final class LimboOverlay {
   public static final int HEARTBEAT_TICKS=24; // 20 ticks/s * 60 / 50 beats/min, not sample-length tuning.
   private static final Set<String> HIDDEN=Set.of("hotbar","crosshair","experience_bar","player_health","food_level","armor_level","air_level","mount_health","jump_bar","item_name");
   public interface Environment {void beat();void stopHeartbeat();void flash();}
   public static final class State {
      private final Environment environment;
      private int left,beatsElapsed,flashTicks=-1;
      private boolean active,entered,heartbeat,flashPending;
      public State(Environment environment){this.environment=environment;}
      public boolean active(){return active;}
      public int left(){return left;}
      public int elapsed(){return 600-left;}
      public int flashTicks(){return flashTicks;}
      public void start(int remaining){stopBeat();left=Math.max(0,Math.min(600,remaining));active=true;entered=false;beatsElapsed=0;flashPending=false;flashTicks=-1;}
      public void end(){stopBeat();active=false;entered=false;left=0;flashPending=true;}
      public void reset(){stopBeat();active=false;entered=false;left=0;flashPending=false;flashTicks=-1;beatsElapsed=0;}
      private void stopBeat(){if(heartbeat){environment.stopHeartbeat();heartbeat=false;}}
      public void tick(boolean inVoid,boolean overworld) {
         if(active && inVoid){
            entered=true;
            if(beatsElapsed++%HEARTBEAT_TICKS==0){environment.beat();heartbeat=true;}
            left=Math.max(0,left-1);
         } else if(active && entered){stopBeat();active=false;entered=false;}
         if(flashPending && overworld){flashPending=false;flashTicks=0;environment.flash();}
         else if(flashTicks>=0 && overworld) flashTicks++;
      }
   }
   private static LimboHeartbeat heartbeat;
   private static final State STATE=new State(new Environment(){
      public void beat(){stopSound();heartbeat=new LimboHeartbeat();Minecraft.getInstance().getSoundManager().play(heartbeat);}
      public void stopHeartbeat(){stopSound();}
      public void flash(){FlashOverlay.flashEnvelope(1,10,30);}
   });
   private static boolean initialized;
   private LimboOverlay(){}
   public static void init(){
      if(initialized) return;initialized=true;
      MinecraftForge.EVENT_BUS.addListener(LimboOverlay::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(LimboOverlay::onRender);
      MinecraftForge.EVENT_BUS.addListener(LimboOverlay::onLogout);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,LimboOverlay::onRenderHand);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,LimboOverlay::onHudPre);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,LimboOverlay::onMovement);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,LimboOverlay::onInteraction);
   }
   public static void onPacket(LimboS2CPacket packet){if(packet.active()) STATE.start(packet.ticksLeft());else STATE.end();}
   public static int lineIndex(int elapsed){return Math.max(0,Math.min(4,elapsed/100));}
   public static float lineAlpha(float elapsed){int line=lineIndex((int)elapsed);return Math.max(0,Math.min(1,(elapsed-line*100)/20));}
   /** Font.adjustColor turns alpha 0..3 into opaque; omit those sub-pixel fade frames. */
   public static int textArgb(float opacity){int alpha=Math.round(255*Math.max(0,Math.min(1,opacity)));return alpha<4?0:(alpha<<24)|0xFFFFFF;}
   public static float flashAlpha(float elapsed){return FlashOverlay.envelopeAlpha(1,10,30,elapsed);}
   public static boolean hidesHands(String dimension){return BodyIsolationClient.active(dimension);}
   public static boolean hidesHud(String dimension,String overlay){return hidesHands(dimension)&&HIDDEN.contains(overlay);}
   private static String dimension(){var level=Minecraft.getInstance().level;return level==null?"":level.dimension().location().toString();}
   public static boolean hearingHeartbeat(){return STATE.active()&&hidesHands(dimension());}
   private static void stopSound(){if(heartbeat!=null){heartbeat.cancel();Minecraft.getInstance().getSoundManager().stop(heartbeat);heartbeat=null;}}
   public static void onClientTick(TickEvent.ClientTickEvent event){
      if(event.phase!=TickEvent.Phase.END) return;
      Minecraft mc=Minecraft.getInstance();if(mc.player==null || mc.level==null){stopSound();return;}
      if(mc.isPaused()) return;
      boolean isolated=hidesHands(dimension());
      STATE.tick(isolated,"minecraft:overworld".equals(dimension()));
      if(STATE.active()&&isolated) dev.baranhan.viltrumiteflight.util.FlightPermissions.resetModFlight(mc.player);
   }
   public static void onRender(RenderGuiEvent.Post event){
      Minecraft mc=Minecraft.getInstance();if(!hearingHeartbeat() || mc.options.hideGui) return;
      float elapsed=STATE.elapsed()+event.getPartialTick(),alpha=lineAlpha(elapsed);if(alpha<=0) return;
      var text=Component.translatable("viltrumitecore.limbo.line"+(lineIndex((int)elapsed)+1));
      var gui=event.getGuiGraphics();int width=gui.guiWidth(),height=gui.guiHeight();
      var lines=mc.font.split(text,Math.max(50,width-40));int y=height/2-lines.size()*mc.font.lineHeight/2;
      int color=textArgb(alpha);if(color==0) return;
      for(var line:lines){gui.drawString(mc.font,line,(width-mc.font.width(line))/2,y,color,false);y+=mc.font.lineHeight;}
   }
   public static void onRenderHand(RenderHandEvent event){if(hidesHands(dimension())) event.setCanceled(true);}
   public static void onHudPre(RenderGuiOverlayEvent.Pre event){if(hidesHud(dimension(),event.getOverlay().id().getPath())) event.setCanceled(true);}
   public static void onMovement(MovementInputUpdateEvent event){
      if(!hearingHeartbeat()) return;var input=event.getInput();input.forwardImpulse=input.leftImpulse=0;
      input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;
      event.getEntity().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
   }
   public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event){if(hearingHeartbeat()){event.setCanceled(true);event.setSwingHand(false);}}
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event){STATE.reset();stopSound();}
}
