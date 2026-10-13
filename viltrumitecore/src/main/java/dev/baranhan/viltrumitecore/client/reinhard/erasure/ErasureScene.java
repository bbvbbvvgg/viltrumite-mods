package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import dev.baranhan.viltrumitecore.client.hero.WorldPauseClient;
import dev.baranhan.viltrumitecore.client.render.vfx.*;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** One all-dimension cinematic. Clock is server age + local unpaused client ticks, not a dimension's time. */
public final class ErasureScene {
   public enum EndReason { NORMAL, CANCEL, LOGOUT, LEVEL_UNLOAD, RELOAD, WATCHDOG }
   public interface SoundEnvironment {void crack(); void duck(int fadeTicks); void inhale(); void release(int fadeTicks);}
   public static final class Timeline {
      private int age; private boolean ducked,released,inhalePlayed,ended;
      private final SoundEnvironment sound;
      public Timeline(int initialAge, SoundEnvironment sound) {
         this.age=Math.max(0,initialAge); this.sound=sound;
         if(age==0) sound.crack();
         if(age>=30 && age<110) {sound.duck(Math.max(0,36-age));ducked=true;}
         if(age>=170) end(EndReason.WATCHDOG);
      }
      public int age(){return age;}
      public boolean ended(){return ended;}
      public ErasureUniforms uniforms(float partialTick){return ended ? ErasureUniforms.ZERO : ErasureUniforms.at(age,partialTick);}
      public void advance(int tick) {
         if(ended) return;
         int before=age; age=Math.max(age,tick);
         if(age>=30 && age<110 && !ducked) {sound.duck(Math.max(0,36-age));ducked=true;}
         if(before<110 && age>=110 && !inhalePlayed) {
            sound.inhale(); inhalePlayed=true;
            if(ducked && !released) {sound.release(40);released=true;}
         }
         if(age>=170) end(EndReason.WATCHDOG);
      }
      public void end(EndReason reason) {
         if(ended) return; ended=true;
         if(ducked && !released) {sound.release(6);released=true;}
      }
   }
   /** A re-sent packet rebases once, preserving monotonic age without waiting for the old receipt. */
   public static final class Clock {
      private int serverAge; private long receipt;
      public Clock(int age,long now){serverAge=Math.max(0,age);receipt=now;}
      public int at(long now){return elapsed(serverAge,receipt,now);}
      public void sync(int age,long now){serverAge=Math.max(at(now),Math.max(0,age));receipt=now;}
   }
   private static final Set<ResourceLocation> OWN_SOUNDS=Set.of(new ResourceLocation("viltrumitecore","reinhard.erase_crack"),new ResourceLocation("viltrumitecore","reinhard.erase_inhale"));
   private static Active scene; private static long clientTicks; private static boolean initialized;
   private ErasureScene() {}
   public static void init() {
      if(initialized) return; initialized=true;
      ErasurePost.bind(ErasureScene::active,ErasureScene::uniforms);
      WorldPauseClient.onEnd(() -> clear(EndReason.NORMAL));
   }
   private static final class Active {
      final ErasureS2CPacket packet; final Clock clock; final Timeline timeline; final float seed;
      final List<GenericSilhouette.Pixel> pixels; boolean realFailed;
      Active(ErasureS2CPacket packet) {
         this.packet=packet;this.clock=new Clock(packet.age(),clientTicks);
         long bits=packet.pauseId().getMostSignificantBits()^packet.pauseId().getLeastSignificantBits();
         seed=(bits&65535)/65535F;pixels=GenericSilhouette.pixels(bits,packet.humanoid());
         timeline=new Timeline(packet.age(),new SoundEnvironment() {
            public void crack(){Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ReinhardSounds.ERASE_CRACK.get(),1));}
            public void duck(int fade){SoundDuck.INSTANCE.duck(Active.this,SoundSource.MASTER,0,fade,OWN_SOUNDS);}
            public void inhale(){Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ReinhardSounds.ERASE_INHALE.get(),1));}
            public void release(int fade){SoundDuck.INSTANCE.release(Active.this,fade);}
         });
      }
   }
   public static int elapsed(int serverAge,long received,long now) {
      long delta=now>=received ? now-received : 0;
      return delta>=Integer.MAX_VALUE-Math.max(0,serverAge) ? Integer.MAX_VALUE : Math.max(0,serverAge)+(int)delta;
   }
   public static boolean active(){return scene!=null && !scene.timeline.ended() && scene.timeline.age()<150;}
   public static ErasureUniforms uniforms(float partialTick){return active()?scene.timeline.uniforms(partialTick).withSeed(scene.seed):ErasureUniforms.ZERO;}
   public static float dustAlpha(float t){return t>=80 && t<90 ? Math.max(0,1-(t-80)/10):0;}
   public static float shapeAlpha(float t,boolean erase) {
      if(t<70 || t>=110) return 0;
      if(t<80) return 1;
      if(t<90) return dustAlpha(t);
      return !erase && t>=95 ? Math.min(1,(t-95)/5):0;
   }
   public static void onPacket(ErasureS2CPacket packet) {
      if(scene!=null && scene.packet.pauseId().equals(packet.pauseId())) {scene.clock.sync(packet.age(),clientTicks);scene.timeline.advance(scene.clock.at(clientTicks));return;}
      clear(EndReason.CANCEL); scene=new Active(packet);
   }
   public static void clear(EndReason reason){if(scene!=null) scene.timeline.end(reason);scene=null;}
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if(event.phase!=TickEvent.Phase.END) return;
      Minecraft mc=Minecraft.getInstance();
      if(mc.level==null || mc.player==null) {clear(EndReason.LOGOUT);return;}
      if(mc.isPaused()) return;
      clientTicks++;
      if(scene==null) return;
      scene.timeline.advance(scene.clock.at(clientTicks));
      if(scene.timeline.ended()) clear(EndReason.WATCHDOG);
   }
   public static void onRender(RenderGuiEvent.Post event) {
      Active current=scene;if(current==null) return;
      Minecraft mc=Minecraft.getInstance();if(mc.level==null) return;
      float t=current.timeline.age()+event.getPartialTick();
      if(t<70 || t>=110) return;
      int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
      var p=current.packet;
      boolean sameLevel=mc.level.dimension().equals(p.targetLevel());
      LivingEntity real=sameLevel && mc.level.getEntity(p.targetId()) instanceof LivingEntity living ? living : null;
      var point=ScreenProjector.project(p.targetPos().add(0,p.bbHeight()/2,0),width,height);
      var source=SilhouetteSource.choose(sameLevel,real!=null && !current.realFailed,point!=null && point.onScreen(width,height));
      float x=source==SilhouetteSource.REAL_PROJECTED ? point.x():width*.5F;
      float y=source==SilhouetteSource.REAL_PROJECTED ? point.y():height*.5F;
      float span=height*.4F,aspect=GenericSilhouette.ratio(p.bbWidth(),p.bbHeight()),alpha=shapeAlpha(t,p.erase());
      if(source!=SilhouetteSource.GENERIC_CENTER && alpha>0) {
         try {GuiEntitySilhouette.render(event.getGuiGraphics(),real,x,y,span,event.getPartialTick(),alpha);}
         catch(RuntimeException error) {current.realFailed=true;com.mojang.logging.LogUtils.getLogger().warn("Erasure real silhouette failed; using flat metadata fallback",error);source=SilhouetteSource.GENERIC_CENTER;x=width*.5F;y=height*.5F;}
      }
      if(source==SilhouetteSource.GENERIC_CENTER) GenericSilhouette.draw(event.getGuiGraphics(),x,y,span,aspect,p.humanoid(),alpha);
      // Shared paint-pixel primitives; seeded once per scene, never a model/network snapshot.
      if(dustAlpha(t)>0) {
         GenericSilhouette.dust(event.getGuiGraphics(),current.pixels,x,y,span,aspect,p.humanoid(),t);
      }
   }
   public static void onUnload(LevelEvent.Unload event){if(event.getLevel().isClientSide()) clear(EndReason.LEVEL_UNLOAD);}
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event){clear(EndReason.LOGOUT);}
   @EventBusSubscriber(modid="viltrumitecore",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
   public static final class Registrar {
      @SubscribeEvent public static void onReload(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener((ResourceManagerReloadListener) manager -> clear(EndReason.RELOAD));
      }
   }
}
