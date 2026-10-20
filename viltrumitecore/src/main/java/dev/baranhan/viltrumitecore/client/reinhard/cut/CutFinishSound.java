package dev.baranhan.viltrumitecore.client.reinhard.cut;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** One mastered finishing pair across both scene types, never one tail per cast. */
final class CutFinishSound {
   static final class Gate {
      private Object level; private long last=Long.MIN_VALUE;
      boolean admit(Object world,long now) {
         if(world==null)return false;
         if(level!=world) {level=world;last=Long.MIN_VALUE;}
         if(last!=Long.MIN_VALUE && now>=last && now-last<12)return false;
         last=now;return true;
      }
      void clear() {level=null;last=Long.MIN_VALUE;}
   }
   private static final Gate GATE=new Gate();
   private static Voice impact,tail;
   private static final class Voice extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
      final float gain;final int duration;int age;float weight;boolean fading;
      Voice(net.minecraft.sounds.SoundEvent event,Vec3 at,float gain,float pitch,int duration) {
         super(event,SoundSource.PLAYERS,net.minecraft.util.RandomSource.create());
         this.gain=gain;this.duration=duration;this.pitch=pitch;this.x=at.x;this.y=at.y;this.z=at.z;this.volume=.001F;
      }
      @Override public boolean canStartSilent() {return true;}
      @Override public void tick() {
         age++;weight=fading?Math.max(0,weight-.25F):Math.min(1,weight+.25F);volume=gain*weight;
         if(age>=duration || fading && weight==0)stop();
      }
      void end() {fading=true;}
      void clear() {stop();}
   }
   private CutFinishSound() {}
   static void clear() {GATE.clear();if(impact!=null)impact.clear();if(tail!=null)tail.clear();impact=tail=null;}
   static void play(Vec3 at,long now) {
      var world=Minecraft.getInstance().level;if(!GATE.admit(world,now))return;
      if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,SOUND,{},{}",now,System.currentTimeMillis());
      if(impact!=null)impact.end();if(tail!=null)tail.end();
      impact=new Voice(ReinhardSounds.WORLD_CUT_BLAST.get(),at,.9F,.92F,76);
      tail=new Voice(ReinhardSounds.WORLD_CUT_TAIL.get(),at,.08F,1,160);
      var manager=Minecraft.getInstance().getSoundManager();manager.play(impact);manager.play(tail);
   }
}
