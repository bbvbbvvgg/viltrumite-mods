package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the Red Hulk. Every file is an original procedural synthesis ({@code tools/sfx/red_hulk.py},
 * CC0, own work): no recordings, no film or game audio, no vanilla sounds and no voice. The roars are non-verbal
 * formant growls; spoken lines are subtitles only (RedHulkLines). {@link #init()} runs from the mod constructor.
 */
public final class RedHulkSounds {
   public static final RegistryObject<SoundEvent> SWING = reg("red_hulk.swing");
   public static final RegistryObject<SoundEvent> SWING_HEAVY = reg("red_hulk.swing_heavy");
   public static final RegistryObject<SoundEvent> PUNCH = reg("red_hulk.punch");
   public static final RegistryObject<SoundEvent> PUNCH_HEAVY = reg("red_hulk.punch_heavy");
   public static final RegistryObject<SoundEvent> HAMMER = reg("red_hulk.hammer");
   public static final RegistryObject<SoundEvent> GRAB = reg("red_hulk.grab");
   public static final RegistryObject<SoundEvent> SQUEEZE = reg("red_hulk.squeeze");
   public static final RegistryObject<SoundEvent> THROW = reg("red_hulk.throw");
   public static final RegistryObject<SoundEvent> THROW_IMPACT = reg("red_hulk.throw_impact");
   public static final RegistryObject<SoundEvent> CLAP = reg("red_hulk.clap");
   public static final RegistryObject<SoundEvent> SEISMIC = reg("red_hulk.seismic");
   public static final RegistryObject<SoundEvent> LEAP_LAUNCH = reg("red_hulk.leap_launch");
   public static final RegistryObject<SoundEvent> LEAP_LAND = reg("red_hulk.leap_land");
   public static final RegistryObject<SoundEvent> BLAST_CHARGE = reg("red_hulk.blast_charge");
   public static final RegistryObject<SoundEvent> BLAST_FIRE = reg("red_hulk.blast_fire");
   public static final RegistryObject<SoundEvent> FURY_ROAR = reg("red_hulk.fury_roar");
   public static final RegistryObject<SoundEvent> OVERHEAT = reg("red_hulk.overheat");
   public static final RegistryObject<SoundEvent> VENT = reg("red_hulk.vent");
   public static final RegistryObject<SoundEvent> ULT_CHARGE = reg("red_hulk.ult_charge");
   public static final RegistryObject<SoundEvent> ULT_RELEASE = reg("red_hulk.ult_release");
   public static final RegistryObject<SoundEvent> ULT_RECOVER = reg("red_hulk.ult_recover");
   public static final RegistryObject<SoundEvent> LANDING = reg("red_hulk.landing");
   public static final RegistryObject<SoundEvent> FOOTSTEP = reg("red_hulk.footstep");
   public static final RegistryObject<SoundEvent> HEAT_UP = reg("red_hulk.heat_up");
   public static final RegistryObject<SoundEvent> HEAT_HUM = reg("red_hulk.heat_hum");
   public static final RegistryObject<SoundEvent> REFUSE = reg("red_hulk.refuse");
   public static final RegistryObject<SoundEvent> ENTER = reg("red_hulk.enter");
   public static final RegistryObject<SoundEvent> HURT = reg("red_hulk.hurt");

   private RedHulkSounds() {
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

   /** Pitch jitter for repeated cues. */
   public static float jitter(Entity e, float base) {
      return base + (e.level().random.nextFloat() - 0.5F) * 0.12F;
   }

   /** Heat makes every impact louder and a little lower (the body is a furnace). */
   public static float heatVolume(int heat, float base) {
      return base * (1.0F + 0.35F * RedHulkRules.clampHeat(heat) / RedHulkRules.HEAT_MAX);
   }

   public static float heatPitch(int heat, float base) {
      return base * (1.0F - 0.12F * RedHulkRules.clampHeat(heat) / RedHulkRules.HEAT_MAX);
   }
}
