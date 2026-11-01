package dev.baranhan.viltrumitecore.worldevent.meteor;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of Meteor Rain 2.0. All playback is client side ({@code client.meteor.MeteorRainAudio} and
 * {@code CityFireClient}); the server sends no meteor sounds of its own (the vanilla explosion of the impact
 * stays). Files are CC0 Freesound layers and original synthesis, recipe {@code tools/sfx/meteor_rain.py},
 * sources {@code tools/assets/meteor_rain_sources.md}. {@link #init()} runs from the mod constructor.
 */
public final class MeteorRainSounds {
   /** Far, high meteor: a low rolling rumble that grows as it comes down (loop). */
   public static final RegistryObject<SoundEvent> APPROACH = reg("meteor_rain.approach");
   /** Near meteor: the burning roar of the entry (loop). */
   public static final RegistryObject<SoundEvent> ROAR = reg("meteor_rain.roar");
   /** Closest approach of a meteor that falls near the listener. */
   public static final RegistryObject<SoundEvent> WHOOSH = reg("meteor_rain.whoosh");
   /** Impact heard close: crack and body. */
   public static final RegistryObject<SoundEvent> IMPACT_NEAR = reg("meteor_rain.impact_near");
   /** Impact heard from afar: the dull boom and its long tail. */
   public static final RegistryObject<SoundEvent> IMPACT_FAR = reg("meteor_rain.impact_far");
   /** Low shockwave thump of an impact. */
   public static final RegistryObject<SoundEvent> SHOCKWAVE = reg("meteor_rain.shockwave");
   /** Rocks and chunks raining down after a near impact. */
   public static final RegistryObject<SoundEvent> DEBRIS = reg("meteor_rain.debris");
   /** Rubble settling and crumbling, the tail of a near impact. */
   public static final RegistryObject<SoundEvent> RUBBLE = reg("meteor_rain.rubble");
   /** One crackle of a burning block. */
   public static final RegistryObject<SoundEvent> FIRE_CRACKLE = reg("meteor_rain.fire_crackle");
   /** The roar of a big burning zone (loop). */
   public static final RegistryObject<SoundEvent> FIRE_ROAR = reg("meteor_rain.fire_roar");
   /** A fire flare: a spot catching hard or a window bursting. */
   public static final RegistryObject<SoundEvent> FIRE_FLARE = reg("meteor_rain.fire_flare");
   /** A burning brand thrown across the street. */
   public static final RegistryObject<SoundEvent> BRAND = reg("meteor_rain.brand");

   private MeteorRainSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }
}
