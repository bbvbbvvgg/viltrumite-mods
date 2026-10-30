package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Regulus Reborn sound events (spec §11). Every file is original synthesis
 * ({@code tools/sfx/regulus_reborn.py}, CC0 own work); no vanilla sound and no old Regulus file is
 * used. Variants are several entries of one event. Registered on ViltrumiteCore.SOUND_EVENTS;
 * {@link #init()} runs from the mod constructor. The old {@code regulus_*} events stay registered
 * because other heroes reuse them.
 */
public final class RegulusSounds {
   public static final RegistryObject<SoundEvent> LION_ON = reg("regulus.lion_on");
   public static final RegistryObject<SoundEvent> LION_OFF = reg("regulus.lion_off");
   public static final RegistryObject<SoundEvent> LION_DENY = reg("regulus.lion_deny");
   public static final RegistryObject<SoundEvent> RECOIL = reg("regulus.recoil");
   public static final RegistryObject<SoundEvent> PROJECTILE_STOP = reg("regulus.projectile_stop");
   public static final RegistryObject<SoundEvent> HEARTBEAT = reg("regulus.heartbeat");
   public static final RegistryObject<SoundEvent> STRAIN = reg("regulus.strain");
   public static final RegistryObject<SoundEvent> SEIZURE = reg("regulus.seizure");
   public static final RegistryObject<SoundEvent> TOUCH_SWISH = reg("regulus.touch_swish");
   public static final RegistryObject<SoundEvent> TOUCH_HIT = reg("regulus.touch_hit");
   public static final RegistryObject<SoundEvent> RUPTURE = reg("regulus.rupture");
   public static final RegistryObject<SoundEvent> SAND_SCOOP = reg("regulus.sand_scoop");
   public static final RegistryObject<SoundEvent> SAND_RELEASE = reg("regulus.sand_release");
   public static final RegistryObject<SoundEvent> GRAIN_HIT = reg("regulus.grain_hit");
   public static final RegistryObject<SoundEvent> GRAIN_STOP = reg("regulus.grain_stop");
   public static final RegistryObject<SoundEvent> SEVER_CHARGE = reg("regulus.sever_charge");
   public static final RegistryObject<SoundEvent> SEVER_RELEASE = reg("regulus.sever_release");
   public static final RegistryObject<SoundEvent> SEVER_CUT = reg("regulus.sever_cut");
   public static final RegistryObject<SoundEvent> PROCESSION_STEP = reg("regulus.procession_step");
   public static final RegistryObject<SoundEvent> PROCESSION_RAM = reg("regulus.procession_ram");
   public static final RegistryObject<SoundEvent> REPLY_STANCE = reg("regulus.reply_stance");
   public static final RegistryObject<SoundEvent> REPLY_STRIKE = reg("regulus.reply_strike");
   public static final RegistryObject<SoundEvent> KING_BIND = reg("regulus.king_bind");
   public static final RegistryObject<SoundEvent> KING_HOP = reg("regulus.king_hop");
   public static final RegistryObject<SoundEvent> KING_RELEASE = reg("regulus.king_release");
   public static final RegistryObject<SoundEvent> REFUSE = reg("regulus.refuse");
   public static final RegistryObject<SoundEvent> ULT_ACCUSE = reg("regulus.ult_accuse");
   public static final RegistryObject<SoundEvent> ULT_SCOOP = reg("regulus.ult_scoop");
   public static final RegistryObject<SoundEvent> ULT_WAVE = reg("regulus.ult_wave");
   public static final RegistryObject<SoundEvent> ULT_FLICK = reg("regulus.ult_flick");
   public static final RegistryObject<SoundEvent> ULT_RESOLVE = reg("regulus.ult_resolve");
   public static final RegistryObject<SoundEvent> STEP = reg("regulus.step");

   /** Every event name (sounds.json and the synthesis script must match it). */
   public static final List<String> NAMES = List.of(
      "lion_on", "lion_off", "lion_deny", "recoil", "projectile_stop", "heartbeat", "strain", "seizure", "touch_swish", "touch_hit",
      "rupture", "sand_scoop", "sand_release", "grain_hit", "grain_stop", "sever_charge", "sever_release", "sever_cut",
      "procession_step", "procession_ram", "reply_stance", "reply_strike", "king_bind", "king_hop", "king_release", "refuse",
      "ult_accuse", "ult_scoop", "ult_wave", "ult_flick", "ult_resolve", "step");

   private RegulusSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Loads the holders (class init). */
   public static void init() {
   }

   /** Server: positional, everyone nearby hears it, the caster included. */
   public static void play(Level level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      if (!level.isClientSide && sound.isPresent()) {
         level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
      }
   }
}
