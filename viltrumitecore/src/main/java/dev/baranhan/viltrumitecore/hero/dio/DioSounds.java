package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * DIO sound events. Every file is original procedural audio (CC0, tools/sfx/dio.py,
 * ledger tools/assets/dio_sources.md); no vanilla sound plays for a DIO action. Cues are
 * loudness-matched by role in the generator; hits rotate 2-3 variants (sounds.json).
 * {@link #init()} runs from the mod constructor so the holders exist before registration.
 */
public final class DioSounds {
   public static final RegistryObject<SoundEvent> STAND_SUMMON = reg("dio.stand_summon");
   public static final RegistryObject<SoundEvent> STAND_DISMISS = reg("dio.stand_dismiss");
   public static final RegistryObject<SoundEvent> STAND_WHOOSH = reg("dio.stand_whoosh");
   public static final RegistryObject<SoundEvent> STAND_HIT = reg("dio.stand_hit");
   public static final RegistryObject<SoundEvent> STAND_HOOK = reg("dio.stand_hook");
   public static final RegistryObject<SoundEvent> BARRAGE_HIT = reg("dio.barrage_hit");
   public static final RegistryObject<SoundEvent> BARRAGE_RUSH = reg("dio.barrage_rush");
   public static final RegistryObject<SoundEvent> FINISHER = reg("dio.finisher");
   public static final RegistryObject<SoundEvent> KNIFE_DRAW = reg("dio.knife_draw");
   public static final RegistryObject<SoundEvent> KNIFE_THROW = reg("dio.knife_throw");
   public static final RegistryObject<SoundEvent> KNIFE_HIT = reg("dio.knife_hit");
   public static final RegistryObject<SoundEvent> KNIFE_STOP = reg("dio.knife_stop");
   public static final RegistryObject<SoundEvent> TIME_STOP = reg("dio.time_stop");
   public static final RegistryObject<SoundEvent> TIME_TICK = reg("dio.time_tick");
   public static final RegistryObject<SoundEvent> TIME_RESUME = reg("dio.time_resume");
   public static final RegistryObject<SoundEvent> LEDGER_BURST = reg("dio.ledger_burst");
   public static final RegistryObject<SoundEvent> DRAIN_GRAB = reg("dio.drain_grab");
   public static final RegistryObject<SoundEvent> DRAIN_PULSE = reg("dio.drain_pulse");
   public static final RegistryObject<SoundEvent> ROLLER_APPEAR = reg("dio.roller_appear");
   public static final RegistryObject<SoundEvent> ROLLER_SLAM = reg("dio.roller_slam");
   public static final RegistryObject<SoundEvent> ROLLER_PUNCH = reg("dio.roller_punch");
   public static final RegistryObject<SoundEvent> ROLLER_EXPLODE = reg("dio.roller_explode");
   public static final RegistryObject<SoundEvent> ROLLER_LEAP = reg("dio.roller_leap");
   public static final RegistryObject<SoundEvent> ROLLER_FALL = reg("dio.roller_fall");
   public static final RegistryObject<SoundEvent> ROLLER_LAND = reg("dio.roller_land");
   public static final RegistryObject<SoundEvent> WRYYY = reg("dio.wryyy");
   public static final RegistryObject<SoundEvent> GUARD = reg("dio.guard");
   public static final RegistryObject<SoundEvent> DEFLECT = reg("dio.deflect");
   public static final RegistryObject<SoundEvent> REFUSE = reg("dio.refuse");
   public static final RegistryObject<SoundEvent> SUN_SIZZLE = reg("dio.sun_sizzle");

   private DioSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Class-load hook: the static holders above register on first touch. */
   public static void init() {
   }

   /**
    * Positional one-shot sent to every player within {@code range} blocks, independent of the
    * volume: vanilla only sends a variable-range event to 16 x volume blocks, which left frozen
    * players at the edge of the 40-block field without the stop and resume cues.
    */
   public static void playWide(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch, double range) {
      net.minecraft.core.Holder<SoundEvent> holder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.get());
      level.getServer().getPlayerList().broadcast(null, at.x, at.y, at.z, Math.max(range, 16.0 * Math.max(volume, 1.0F)), level.dimension(),
         new net.minecraft.network.protocol.game.ClientboundSoundPacket(holder, SoundSource.PLAYERS, at.x, at.y, at.z, volume, pitch, level.getRandom().nextLong()));
   }

   /** Positional one-shot for everyone nearby (caster included). */
   public static void play(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
   }
}
