package dev.baranhan.viltrumitecore.worldevent.aftermath;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the city disaster aftermath. All playback is client side ({@code client.aftermath.AftermathAudio});
 * no vanilla sounds and no voices. Files are CC0 Freesound recordings layered with original synthesis, recipe
 * {@code tools/sfx/city_aftermath.py}, sources {@code tools/assets/city_aftermath_sources.md}.
 * {@link #init()} runs from the mod constructor.
 */
public final class AftermathSounds {
   /** Deep explosions far away (a tank, a gas line, the fight going on elsewhere). */
   public static final RegistryObject<SoundEvent> EXPLOSION_FAR = reg("aftermath.explosion_far");
   /** A part of the ruin giving way: a heavy crumbling roll. */
   public static final RegistryObject<SoundEvent> COLLAPSE = reg("aftermath.collapse");
   /** Small crumbles: grit and pebbles trickling down. */
   public static final RegistryObject<SoundEvent> CRUMBLE = reg("aftermath.crumble");
   /** Falling rocks and chunks bouncing down the rubble. */
   public static final RegistryObject<SoundEvent> ROCKS = reg("aftermath.rocks");
   /** A heavy piece of debris hitting the ground. */
   public static final RegistryObject<SoundEvent> DEBRIS_IMPACT = reg("aftermath.debris_impact");
   /** Stressed steel creaking and groaning. */
   public static final RegistryObject<SoundEvent> METAL_CREAK = reg("aftermath.metal_creak");
   /** Torn wires arcing and spitting sparks. */
   public static final RegistryObject<SoundEvent> SPARKS = reg("aftermath.sparks");
   /** One crackle or pop of smouldering debris. */
   public static final RegistryObject<SoundEvent> FIRE_CRACKLE = reg("aftermath.fire_crackle");
   /** Residual fire in the ruin (loop). */
   public static final RegistryObject<SoundEvent> FIRE_BED = reg("aftermath.fire_bed");
   /** The low rumble of a fresh ruin settling (loop). */
   public static final RegistryObject<SoundEvent> RUMBLE = reg("aftermath.rumble");
   /** Wind moaning through the broken walls (loop). */
   public static final RegistryObject<SoundEvent> WIND = reg("aftermath.wind");
   /** Sirens far away in the city. */
   public static final RegistryObject<SoundEvent> SIREN_FAR = reg("aftermath.siren_far");

   private AftermathSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }
}
