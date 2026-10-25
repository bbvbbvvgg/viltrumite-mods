package dev.baranhan.viltrumitecore.client.reinhard.limbo;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One tickable pulse of the continuous 24-tick logical loop; sample length cannot change BPM. */
public final class LimboHeartbeat extends AbstractTickableSoundInstance {
   private int age;
   public LimboHeartbeat(){
      super(ReinhardSounds.LIMBO_HEARTBEAT.get(),SoundSource.MASTER,RandomSource.create());
      relative=true;attenuation=Attenuation.NONE;looping=false;delay=0;volume=.8F;pitch=1;
   }
   @Override public void tick(){if(!LimboOverlay.hearingHeartbeat() || ++age>=LimboOverlay.HEARTBEAT_TICKS) stop();}
   public void cancel(){stop();}
   @Override public boolean canStartSilent(){return true;}
}
