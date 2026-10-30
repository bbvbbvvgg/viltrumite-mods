package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of Captain America and the summoned Avengers. Every file is an original procedural synthesis
 * ({@code tools/sfx/captain.py}, CC0), never a film recording. {@link #init()} runs from the mod constructor.
 */
public final class CaptainSounds {
   public static final RegistryObject<SoundEvent> SHIELD_SWING = reg("captain.shield_swing");
   public static final RegistryObject<SoundEvent> SHIELD_HIT = reg("captain.shield_hit");
   public static final RegistryObject<SoundEvent> FIST_SWING = reg("captain.fist_swing");
   public static final RegistryObject<SoundEvent> FIST_HIT = reg("captain.fist_hit");
   public static final RegistryObject<SoundEvent> KICK = reg("captain.kick");
   public static final RegistryObject<SoundEvent> FINISHER = reg("captain.finisher");
   public static final RegistryObject<SoundEvent> COUNTER = reg("captain.counter");
   public static final RegistryObject<SoundEvent> GUARD_UP = reg("captain.guard_up");
   public static final RegistryObject<SoundEvent> BLOCK = reg("captain.block");
   public static final RegistryObject<SoundEvent> PARRY = reg("captain.parry");
   public static final RegistryObject<SoundEvent> THROW = reg("captain.throw");
   public static final RegistryObject<SoundEvent> SHIELD_FLY = reg("captain.shield_fly");
   public static final RegistryObject<SoundEvent> RICOCHET = reg("captain.ricochet");
   public static final RegistryObject<SoundEvent> CATCH = reg("captain.catch");
   public static final RegistryObject<SoundEvent> RECALL = reg("captain.recall");
   public static final RegistryObject<SoundEvent> RUSH = reg("captain.rush");
   public static final RegistryObject<SoundEvent> RUSH_HIT = reg("captain.rush_hit");
   public static final RegistryObject<SoundEvent> SLAM_LEAP = reg("captain.slam_leap");
   public static final RegistryObject<SoundEvent> SLAM_IMPACT = reg("captain.slam_impact");
   public static final RegistryObject<SoundEvent> SLAM_EMPOWERED = reg("captain.slam_empowered");
   public static final RegistryObject<SoundEvent> ROLL = reg("captain.roll");
   public static final RegistryObject<SoundEvent> LANDING = reg("captain.landing");
   public static final RegistryObject<SoundEvent> REFUSE = reg("captain.refuse");
   public static final RegistryObject<SoundEvent> CHARGED = reg("captain.charged");
   public static final RegistryObject<SoundEvent> ASSEMBLE = reg("captain.assemble");
   // Avengers
   public static final RegistryObject<SoundEvent> QUINJET = reg("avengers.quinjet_flyby");
   public static final RegistryObject<SoundEvent> QUINJET_RAMP = reg("avengers.quinjet_ramp");
   public static final RegistryObject<SoundEvent> IRON_THRUST = reg("avengers.ironman_thrust");
   public static final RegistryObject<SoundEvent> IRON_LAND = reg("avengers.ironman_land");
   public static final RegistryObject<SoundEvent> REPULSOR = reg("avengers.ironman_repulsor");
   public static final RegistryObject<SoundEvent> MISSILE = reg("avengers.ironman_missile");
   public static final RegistryObject<SoundEvent> EXPLOSION = reg("avengers.explosion");
   public static final RegistryObject<SoundEvent> UNIBEAM = reg("avengers.ironman_unibeam");
   public static final RegistryObject<SoundEvent> FACEPLATE = reg("avengers.ironman_faceplate");
   public static final RegistryObject<SoundEvent> HULK_ROAR = reg("avengers.hulk_roar");
   public static final RegistryObject<SoundEvent> HULK_SMASH = reg("avengers.hulk_smash");
   public static final RegistryObject<SoundEvent> HULK_PUNCH = reg("avengers.hulk_punch");
   public static final RegistryObject<SoundEvent> HULK_CHEST = reg("avengers.hulk_chest");
   public static final RegistryObject<SoundEvent> THUNDER = reg("avengers.thor_thunder");
   public static final RegistryObject<SoundEvent> HAMMER_SWING = reg("avengers.thor_hammer_swing");
   public static final RegistryObject<SoundEvent> HAMMER_HIT = reg("avengers.thor_hammer_hit");
   public static final RegistryObject<SoundEvent> HAMMER_SPIN = reg("avengers.thor_hammer_spin");
   public static final RegistryObject<SoundEvent> WIDOW_SHOT = reg("avengers.widow_shot");
   public static final RegistryObject<SoundEvent> WIDOW_BITE = reg("avengers.widow_bite");
   public static final RegistryObject<SoundEvent> WIDOW_STRIKE = reg("avengers.widow_strike");
   public static final RegistryObject<SoundEvent> ACROBAT = reg("avengers.acrobat");
   public static final RegistryObject<SoundEvent> BOW_DRAW = reg("avengers.hawkeye_draw");
   public static final RegistryObject<SoundEvent> BOW_RELEASE = reg("avengers.hawkeye_release");
   public static final RegistryObject<SoundEvent> ARROW_HIT = reg("avengers.hawkeye_arrow_hit");
   public static final RegistryObject<SoundEvent> SHOCK = reg("avengers.shock");
   public static final RegistryObject<SoundEvent> HEAVY_LAND = reg("avengers.heavy_land");
   public static final RegistryObject<SoundEvent> DEPART = reg("avengers.depart");

   private CaptainSounds() {
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
      return base + (e.getRandom().nextFloat() - 0.5F) * 0.12F;
   }
}
