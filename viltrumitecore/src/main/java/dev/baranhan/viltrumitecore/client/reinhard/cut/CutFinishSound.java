package dev.baranhan.viltrumitecore.client.reinhard.cut;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Loud original tail only after DONE; a separate quiet impact accents the open rupture. */
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
   private static final Gate GATE=new Gate(), PULSE_GATE=new Gate();
   private static Voice pulse,tail;
   private static final class Voice extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
      final float gain;final int duration;int age;float weight;boolean fading;
      Voice(net.minecraft.sounds.SoundEvent event,Vec3 at,float gain,float pitch,int duration) {
         super(event,SoundSource.PLAYERS,net.minecraft.util.RandomSource.create());
         this.gain=gain;this.duration=duration;this.pitch=pitch;this.x=at.x;this.y=at.y;this.z=at.z;this.volume=.001F;
      }
      @Override public boolean canStartSilent() {return true;}
      @Override public void tick() {
         age++;weight=fading?Math.max(0,weight-.25F):Math.min(1,weight+.25F);
         float release=Math.min(1,Math.max(0,(duration-age)/4F));volume=gain*weight*release;
         if(age>=duration || fading && weight==0)stop();
      }
      void end() {fading=true;}
      void clear() {stop();}
   }
   private CutFinishSound() {}
   static void clear() {GATE.clear();PULSE_GATE.clear();if(pulse!=null)pulse.clear();if(tail!=null)tail.clear();pulse=tail=null;}
   /** No preparation rumble, no pulse after DONE. 0.8 s excerpts cannot overlap the 2 s cadence. */
   static boolean pulseDue(long elapsed,boolean working) {return working && elapsed>=96 && (elapsed-96)%40==0;}
   static void pulse(Vec3 at,long now) {
      var world=Minecraft.getInstance().level;if(!PULSE_GATE.admit(world,now))return;
      if(pulse!=null)pulse.end();
      pulse=new Voice(ReinhardSounds.WORLD_CUT_BLAST.get(),at,.10F,1,16);
      Minecraft.getInstance().getSoundManager().play(pulse);
      if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,PULSE,{},{}",now,System.currentTimeMillis());
   }
   static void play(Vec3 at,long now) {
      var world=Minecraft.getInstance().level;if(!GATE.admit(world,now))return;
      if(dev.baranhan.viltrumitecore.hero.reinhard.cut.CutTelemetry.ENABLED)com.mojang.logging.LogUtils.getLogger().info("CUT_QA,SOUND,{},{}",now,System.currentTimeMillis());
      if(pulse!=null)pulse.end();if(tail!=null)tail.end();
      tail=new Voice(ReinhardSounds.WORLD_CUT_TAIL.get(),at,.9F,1,160);
      Minecraft.getInstance().getSoundManager().play(tail);
   }
}
