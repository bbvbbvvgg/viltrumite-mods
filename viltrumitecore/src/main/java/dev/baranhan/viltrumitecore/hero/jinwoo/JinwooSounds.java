package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Jinwoo's sound events (spec §10): every one is an original procedural synth
 * ({@code tools/sfx/jinwoo.py}), never a vanilla sound. {@link #init()} runs from
 * the mod constructor before {@code SOUND_EVENTS} registers.
 */
public final class JinwooSounds {
   public static final RegistryObject<SoundEvent> DAGGER_SWING = reg("jinwoo.dagger_swing");
   public static final RegistryObject<SoundEvent> DAGGER_HIT = reg("jinwoo.dagger_hit");
   public static final RegistryObject<SoundEvent> SPIN = reg("jinwoo.spin");
   public static final RegistryObject<SoundEvent> FINISHER = reg("jinwoo.finisher");
   public static final RegistryObject<SoundEvent> BLOCK = reg("jinwoo.block");
   public static final RegistryObject<SoundEvent> PARRY = reg("jinwoo.parry");
   public static final RegistryObject<SoundEvent> DAGGER_DRAW = reg("jinwoo.dagger_draw");
   public static final RegistryObject<SoundEvent> DAGGER_SHEATH = reg("jinwoo.dagger_sheath");
   public static final RegistryObject<SoundEvent> REFUSE = reg("jinwoo.refuse");
   public static final RegistryObject<SoundEvent> STEP = reg("jinwoo.step");
   public static final RegistryObject<SoundEvent> AIR_STEP = reg("jinwoo.air_step");
   public static final RegistryObject<SoundEvent> LANDING = reg("jinwoo.landing");
   public static final RegistryObject<SoundEvent> DIVE = reg("jinwoo.dive");
   public static final RegistryObject<SoundEvent> RUSH = reg("jinwoo.mutilation");
   public static final RegistryObject<SoundEvent> AUTHORITY_GRIP = reg("jinwoo.authority_grip");
   public static final RegistryObject<SoundEvent> AUTHORITY_SLAM = reg("jinwoo.authority_slam");
   public static final RegistryObject<SoundEvent> EXTRACT_CHANNEL = reg("jinwoo.extract_channel");
   public static final RegistryObject<SoundEvent> EXTRACT_RISE = reg("jinwoo.extract_rise");
   public static final RegistryObject<SoundEvent> EXTRACT_FAIL = reg("jinwoo.extract_fail");
   public static final RegistryObject<SoundEvent> EXCHANGE = reg("jinwoo.exchange");
   public static final RegistryObject<SoundEvent> SUMMON = reg("jinwoo.summon");
   public static final RegistryObject<SoundEvent> RECALL = reg("jinwoo.recall");
   public static final RegistryObject<SoundEvent> COMMAND_ATTACK = reg("jinwoo.command_attack");
   public static final RegistryObject<SoundEvent> COMMAND_GUARD = reg("jinwoo.command_guard");
   public static final RegistryObject<SoundEvent> COMMAND_SENTINEL = reg("jinwoo.command_sentinel");
   public static final RegistryObject<SoundEvent> CHARGE = reg("jinwoo.monarch_charge");
   public static final RegistryObject<SoundEvent> SHADOW_RISE = reg("jinwoo.shadow_rise");
   public static final RegistryObject<SoundEvent> SHADOW_DISSOLVE = reg("jinwoo.shadow_dissolve");
   public static final RegistryObject<SoundEvent> SHADOW_SLASH = reg("jinwoo.shadow_slash");
   public static final RegistryObject<SoundEvent> SHADOW_CLAW = reg("jinwoo.shadow_claw");
   public static final RegistryObject<SoundEvent> SHADOW_SLAM = reg("jinwoo.shadow_slam");
   public static final RegistryObject<SoundEvent> SHADOW_ROAR = reg("jinwoo.shadow_roar");
   public static final RegistryObject<SoundEvent> ULT_CHARGE = reg("jinwoo.ult_charge");
   public static final RegistryObject<SoundEvent> ULT_RISE = reg("jinwoo.ult_rise");
   public static final RegistryObject<SoundEvent> ULT_ROAR = reg("jinwoo.ult_roar");
   public static final RegistryObject<SoundEvent> ULT_WAVE = reg("jinwoo.ult_wave");

   private JinwooSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Called from the mod constructor: loads the holders. */
   public static void init() {
   }

   /** Heard by everyone near the source (players hostile sound category). */
   public static void play(Entity at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), SoundSource.PLAYERS, volume, pitch);
   }

   public static void playAt(Entity source, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      source.level().playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
   }
}
