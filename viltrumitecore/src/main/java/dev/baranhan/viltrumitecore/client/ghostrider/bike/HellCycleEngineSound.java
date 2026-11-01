package dev.baranhan.viltrumitecore.client.ghostrider.bike;

import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderSounds;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleRules;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * One looping layer of the Hell Cycle's voice, on every client near the bike: three engine loops cross-faded by the
 * revs (idle, low, high, each pitched by the revs, so gear shifts saw the pitch), the tyre skid while drifting or
 * locking the rear, and the roar of the hellfire that grows with the speed. Each loop stops itself when the bike is
 * gone, burnt out or out of earshot, so none can stick.
 */
public final class HellCycleEngineSound extends AbstractTickableSoundInstance {
   public enum Layer {
      IDLE,
      LOW,
      HIGH,
      SKID,
      ROAR
   }

   /** Beyond this distance (blocks) a loop stops; HellCycleClient restarts it when the listener comes back. */
   static final double EARSHOT = 56.0;
   private final HellCycleEntity bike;
   private final Layer layer;
   private float level;

   HellCycleEngineSound(HellCycleEntity bike, Layer layer) {
      super(event(layer), SoundSource.NEUTRAL, RandomSource.create(bike.getId() * 31L + layer.ordinal()));
      this.bike = bike;
      this.layer = layer;
      this.looping = true;
      this.delay = 0;
      this.volume = 0.0001F;
      this.pitch = 1.0F;
      this.x = bike.getX();
      this.y = bike.getY() + 0.5;
      this.z = bike.getZ();
   }

   static SoundEvent event(Layer layer) {
      return switch (layer) {
         case IDLE -> GhostRiderSounds.ENGINE_IDLE.get();
         case LOW -> GhostRiderSounds.ENGINE_LOW.get();
         case HIGH -> GhostRiderSounds.ENGINE_HIGH.get();
         case SKID -> GhostRiderSounds.SKID.get();
         case ROAR -> GhostRiderSounds.FLAME_ROAR.get();
      };
   }

   /** Pure: loudness of a layer (before the life fade) for revs 0-1, speed fraction, skid and whether he rides. */
   public static float targetVolume(Layer layer, float rpm, float speedF, boolean skid, boolean ridden) {
      float r = Mth.clamp(rpm, 0.0F, 1.0F);
      return switch (layer) {
         case IDLE -> (ridden ? 0.8F : 0.45F) * (1.0F - smooth((r - 0.12F) / 0.33F));
         case LOW -> 0.9F * Math.max(0.0F, 1.0F - Math.abs(r - 0.45F) / 0.3F);
         case HIGH -> smooth((r - 0.55F) / 0.33F);
         case SKID -> skid ? 0.85F : 0.0F;
         case ROAR -> 0.1F + 0.55F * Mth.clamp(speedF, 0.0F, 1.2F);
      };
   }

   /** Pure: pitch of a layer (clamped to the engine's 0.5 - 2 range). */
   public static float targetPitch(Layer layer, float rpm, float speedF, boolean boost) {
      float r = Mth.clamp(rpm, 0.0F, 1.0F);
      float p = switch (layer) {
         case IDLE -> 0.9F + 0.6F * r;
         case LOW -> 0.7F + 0.7F * r;
         case HIGH -> 0.75F + 0.55F * r + (boost ? 0.08F : 0.0F);
         case SKID -> 0.85F + 0.3F * Mth.clamp(speedF, 0.0F, 1.0F);
         case ROAR -> 0.8F + 0.4F * Mth.clamp(speedF, 0.0F, 1.2F);
      };
      return Mth.clamp(p, 0.5F, 2.0F);
   }

   /** Pure: 0 while forming starts, 1 alive, fading to 0 as it burns out. */
   public static float life(float form, float burn) {
      return Mth.clamp(form, 0.0F, 1.0F) * (1.0F - Mth.clamp(burn, 0.0F, 1.0F));
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   boolean follows(HellCycleEntity e) {
      return this.bike == e;
   }

   Layer layer() {
      return this.layer;
   }

   @Override
   public boolean canStartSilent() {
      return true;
   }

   @Override
   public void tick() {
      HellCycleEntity b = this.bike;
      float burn = b.burning() ? b.burnProgress(0.0F) : 0.0F;
      if (b.isRemoved() || burn >= 1.0F || HellCycleClient.listenerDistanceSqr(b) > EARSHOT * EARSHOT) {
         this.stop();
         return;
      }
      this.x = b.getX();
      this.y = b.getY() + 0.5;
      this.z = b.getZ();
      float speedF = (float)(b.visualSpeed / HellCycleRules.MAX_SPEED);
      boolean skid = b.isVehicle() && (b.flag(HellCycleEntity.FLAG_SKID) || b.flag(HellCycleEntity.FLAG_DRIFT)) && !b.flag(HellCycleEntity.FLAG_AIR);
      float target = targetVolume(this.layer, b.rpm, speedF, skid, b.isVehicle()) * life(b.formProgress(0.0F), burn);
      this.level += (target - this.level) * (target > this.level ? 0.35F : 0.2F);
      this.volume = Math.max(0.0001F, this.level);
      float pitchTarget = targetPitch(this.layer, b.rpm, speedF, b.dashing());
      this.pitch += (pitchTarget - this.pitch) * 0.5F;
   }
}
