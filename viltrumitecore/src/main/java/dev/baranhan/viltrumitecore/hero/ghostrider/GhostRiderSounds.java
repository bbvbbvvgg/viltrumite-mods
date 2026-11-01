package dev.baranhan.viltrumitecore.hero.ghostrider;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the Ghost Rider and the Hell Cycle. Every file is an original procedural synthesis
 * ({@code tools/sfx/ghost_rider.py}, CC0, own work): no recordings, no film or game audio, no vanilla sounds and no
 * voice. Spoken lines are subtitles only (GhostRiderLines); every event has its own subtitle. {@link #init()} runs
 * from the mod constructor.
 */
public final class GhostRiderSounds {
   // the Hell Cycle (engine loops are driven by the client, HellCycleEngineSound)
   public static final RegistryObject<SoundEvent> ENGINE_IDLE = reg("ghost_rider.engine_idle");
   public static final RegistryObject<SoundEvent> ENGINE_LOW = reg("ghost_rider.engine_low");
   public static final RegistryObject<SoundEvent> ENGINE_HIGH = reg("ghost_rider.engine_high");
   public static final RegistryObject<SoundEvent> SKID = reg("ghost_rider.skid");
   public static final RegistryObject<SoundEvent> FLAME_ROAR = reg("ghost_rider.flame_roar");
   public static final RegistryObject<SoundEvent> BIKE_FORM = reg("ghost_rider.bike_form");
   public static final RegistryObject<SoundEvent> BIKE_BURNOUT = reg("ghost_rider.bike_burnout");
   public static final RegistryObject<SoundEvent> IGNITION = reg("ghost_rider.ignition");
   public static final RegistryObject<SoundEvent> BACKFIRE = reg("ghost_rider.backfire");
   public static final RegistryObject<SoundEvent> SHIFT = reg("ghost_rider.shift");
   public static final RegistryObject<SoundEvent> BOOST = reg("ghost_rider.boost");
   public static final RegistryObject<SoundEvent> BIKE_JUMP = reg("ghost_rider.bike_jump");
   public static final RegistryObject<SoundEvent> BIKE_LAND = reg("ghost_rider.bike_land");
   public static final RegistryObject<SoundEvent> BIKE_CRASH = reg("ghost_rider.bike_crash");
   public static final RegistryObject<SoundEvent> HARD_STOP = reg("ghost_rider.hard_stop");
   public static final RegistryObject<SoundEvent> MOUNT = reg("ghost_rider.mount");
   public static final RegistryObject<SoundEvent> RAM = reg("ghost_rider.ram");
   // the chain
   public static final RegistryObject<SoundEvent> CHAIN_LASH = reg("ghost_rider.chain_lash");
   public static final RegistryObject<SoundEvent> CHAIN_HIT = reg("ghost_rider.chain_hit");
   public static final RegistryObject<SoundEvent> CHAIN_THROW = reg("ghost_rider.chain_throw");
   public static final RegistryObject<SoundEvent> CHAIN_WHIRL = reg("ghost_rider.chain_whirl");
   public static final RegistryObject<SoundEvent> CHAIN_SLAM = reg("ghost_rider.chain_slam");
   // hellfire
   public static final RegistryObject<SoundEvent> HELLFIRE_IGNITE = reg("ghost_rider.hellfire_ignite");
   public static final RegistryObject<SoundEvent> HELLFIRE_WAVE = reg("ghost_rider.hellfire_wave");
   public static final RegistryObject<SoundEvent> HELLFIRE_BURN = reg("ghost_rider.hellfire_burn");
   public static final RegistryObject<SoundEvent> DASH = reg("ghost_rider.dash");
   // the Rider
   public static final RegistryObject<SoundEvent> TRANSFORM = reg("ghost_rider.transform");
   public static final RegistryObject<SoundEvent> REVERT = reg("ghost_rider.revert");
   public static final RegistryObject<SoundEvent> SKULL_FIRE = reg("ghost_rider.skull_fire");
   public static final RegistryObject<SoundEvent> STARE = reg("ghost_rider.stare");
   public static final RegistryObject<SoundEvent> VERDICT = reg("ghost_rider.verdict");
   public static final RegistryObject<SoundEvent> INNOCENT = reg("ghost_rider.innocent");
   public static final RegistryObject<SoundEvent> REFUSE = reg("ghost_rider.refuse");

   private GhostRiderSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }

   public static void play(Entity at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), SoundSource.PLAYERS, volume, pitch);
   }

   public static void playAt(Entity source, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      source.level().playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
   }

   /** For everybody except {@code except} (the rider's client plays its own copy without the network delay). */
   public static void playExcept(Entity except, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      net.minecraft.world.entity.player.Player p = except instanceof net.minecraft.world.entity.player.Player pl ? pl : null;
      except.level().playSound(p, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
   }

   public static float jitter(Entity e, float base) {
      return base + (e.level().random.nextFloat() - 0.5F) * 0.12F;
   }
}
