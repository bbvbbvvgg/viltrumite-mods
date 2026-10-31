package dev.baranhan.viltrumitecore.client.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.hero.ironman.IronManLegionSounds;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Engine loop of one Legion suit (every client that tracks it): volume and
 * pitch follow the synced throttle, a damaged suit's engine coughs in the
 * same rhythm as its flames, and the loop stops itself when the suit is
 * gone or crashing, so no loop can stick.
 */
public final class LegionEngineSound extends AbstractTickableSoundInstance {
   private final LegionSuitEntity suit;

   LegionEngineSound(LegionSuitEntity suit) {
      super(IronManLegionSounds.ENGINE.get(), SoundSource.NEUTRAL, RandomSource.create(suit.getId()));
      this.suit = suit;
      this.looping = true;
      this.delay = 0;
      this.volume = 0.001F;
      this.pitch = 0.9F;
      this.x = suit.getX();
      this.y = suit.getY() + 1.0;
      this.z = suit.getZ();
   }

   /** Target loop volume for a phase and throttle. Pure for tests. */
   public static float targetVolume(LegionPhase phase, float throttle, boolean critical, boolean cough) {
      float t = Mth.clamp(throttle, 0.0F, 1.0F);
      float base = switch (phase) {
         case APPROACH, LEAVE -> 0.75F + 0.25F * t;
         case BRAKE -> 0.9F;
         case READY -> 0.45F;
         case ACTIVE -> 0.4F + 0.45F * t;
         case CRASH -> 0.0F;
      };
      return critical && cough ? base * 0.2F : base;
   }

   @Override
   public void tick() {
      LegionPhase phase = this.suit.phase();
      if (this.suit.isRemoved() || !this.suit.isAlive() || phase == LegionPhase.CRASH) {
         this.stop();
         return;
      }

      this.x = this.suit.getX();
      this.y = this.suit.getY() + 1.0;
      this.z = this.suit.getZ();
      float throttle = this.suit.clientThrottle;
      boolean critical = this.suit.flag(LegionSuitEntity.FLAG_CRITICAL);
      boolean cough = !LegionSuitRenderer.sputter(this.suit, this.suit.tickCount, 0.75F);
      float target = targetVolume(phase, throttle, critical, cough);
      float pitch = 0.8F + 0.45F * throttle + (phase == LegionPhase.BRAKE ? 0.15F : 0.0F) - (critical ? 0.08F : 0.0F);
      this.volume = Mth.lerp(target < this.volume ? 0.3F : 0.15F, this.volume, Math.max(0.001F, target));
      this.pitch = Mth.lerp(0.2F, this.pitch, pitch);
   }

   @Override
   public boolean canStartSilent() {
      return true;
   }
}
