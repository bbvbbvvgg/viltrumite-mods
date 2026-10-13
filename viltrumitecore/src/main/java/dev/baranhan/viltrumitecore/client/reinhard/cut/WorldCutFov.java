package dev.baranhan.viltrumitecore.client.reinhard.cut;

import dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutShape;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;

/** Allocation-free per-frame listener. Scene arrays refresh only on packet/tick boundaries. */
public final class WorldCutFov {
   private static double current=1;
   private static long lastNanos;
   private WorldCutFov() {}
   public static double factor(double distance) {
      double t=Math.max(0,Math.min(1,distance/64));
      return 1+.03*(1-t*t*(3-2*t));
   }
   public static double factor(double distance, boolean caster) { return caster ? 1 : factor(distance); }
   public static double distance(WorldCutShape shape,double x,double z) {
      double sx=shape.origin().x+shape.dirXZ().x*ReinhardRules.CUT_START_OFFSET, sz=shape.origin().z+shape.dirXZ().z*ReinhardRules.CUT_START_OFFSET;
      double a=Math.max(0,Math.min(ReinhardRules.CUT_LENGTH,(x-sx)*shape.dirXZ().x+(z-sz)*shape.dirXZ().z));
      return Math.hypot(x-sx-a*shape.dirXZ().x,z-sz-a*shape.dirXZ().z);
   }
   public static void reset() { current=1; lastNanos=0; }
   public static void onFov(ViewportEvent.ComputeFov event) {
      Minecraft mc=Minecraft.getInstance();
      if(mc.level==null || mc.player==null) { reset(); return; }
      double target=1;
      WorldCutScene.Active[] scenes=WorldCutScene.frames();
      for(int i=0;i<scenes.length;i++) {
         WorldCutScene.Active scene=scenes[i];
         if(scene.blastReceived() || !WorldCutScene.silence(WorldCutScene.elapsed(scene.packet.startTime(),mc.level.getGameTime(),event.getPartialTick()))) continue;
         target=Math.max(target,factor(distance(scene.packet.shape(),mc.player.getX(),mc.player.getZ()),scene.packet.casterId().equals(mc.player.getUUID())));
      }
      long now=System.nanoTime();
      double dt=lastNanos==0 ? 0 : Math.min(.1,(now-lastNanos)/1E9); lastNanos=now;
      current+=(target-current)*(1-Math.exp(-8*dt));
      event.setFOV(event.getFOV()*current);
   }
}
