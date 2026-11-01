package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of Living City 3.0 (sirens, the grid failing and coming back). All playback is client side
 * ({@code client.city.power.CityPowerAudio}): the server sends state and cues, never sounds. Files are mixed from
 * CC0 Freesound recordings by {@code tools/sfx/city_power.py}; sources in {@code tools/assets/city_power_sources.md}.
 * No vanilla sounds. {@link #init()} runs from the mod constructor.
 */
public final class CityPowerSounds {
   public static final RegistryObject<SoundEvent> SIREN_WARNING = reg("city_power.siren_warning");
   public static final RegistryObject<SoundEvent> SIREN_WARNING_FAR = reg("city_power.siren_warning_far");
   public static final RegistryObject<SoundEvent> SIREN_ATTACK = reg("city_power.siren_attack");
   public static final RegistryObject<SoundEvent> SIREN_ATTACK_FAR = reg("city_power.siren_attack_far");
   public static final RegistryObject<SoundEvent> SIREN_CRITICAL = reg("city_power.siren_critical");
   public static final RegistryObject<SoundEvent> SIREN_CRITICAL_FAR = reg("city_power.siren_critical_far");
   public static final RegistryObject<SoundEvent> SIREN_ALL_CLEAR = reg("city_power.siren_all_clear");
   public static final RegistryObject<SoundEvent> KLAXON = reg("city_power.klaxon");
   public static final RegistryObject<SoundEvent> ALARM_BEEPS = reg("city_power.alarm_beeps");
   public static final RegistryObject<SoundEvent> ARC = reg("city_power.arc");
   public static final RegistryObject<SoundEvent> TRANSFORMER_BLOW = reg("city_power.transformer_blow");
   public static final RegistryObject<SoundEvent> TRANSFORMER_BLOW_FAR = reg("city_power.transformer_blow_far");
   public static final RegistryObject<SoundEvent> SPARK_LOOP = reg("city_power.spark_loop");
   public static final RegistryObject<SoundEvent> LINE_HUM = reg("city_power.line_hum");
   public static final RegistryObject<SoundEvent> POWER_DOWN = reg("city_power.power_down");
   public static final RegistryObject<SoundEvent> POWER_UP = reg("city_power.power_up");
   public static final RegistryObject<SoundEvent> SURGE = reg("city_power.surge");
   public static final RegistryObject<SoundEvent> CASCADE = reg("city_power.cascade");
   public static final RegistryObject<SoundEvent> METAL_GROAN = reg("city_power.metal_groan");
   public static final RegistryObject<SoundEvent> LOW_IMPACT = reg("city_power.low_impact");
   public static final RegistryObject<SoundEvent> DISTANT_DESTRUCTION = reg("city_power.distant_destruction");
   public static final RegistryObject<SoundEvent> DISASTER_BED = reg("city_power.disaster_bed");

   private CityPowerSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }
}
