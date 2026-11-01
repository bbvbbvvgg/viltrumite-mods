package dev.baranhan.viltrumitecore.world.collapse;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of structural collapses. All playback is client side ({@code client.collapse.CollapseAudio},
 * scheduled on the server phase ticks with a speed-of-sound delay); the server plays nothing. No vanilla
 * sounds: CC0 Freesound recordings layered by {@code tools/sfx/city_collapse.py}, sources in
 * {@code tools/assets/city_collapse_sources.md}. {@link #init()} runs from the mod constructor.
 */
public final class CollapseSounds {
   /** Phase 1: the heavy hit that starts it (concrete break with a deep body). */
   public static final RegistryObject<SoundEvent> IMPACT = reg("collapse.impact");
   /** Phase 1: a burst of crumbling concrete right after the hit. */
   public static final RegistryObject<SoundEvent> CRUMBLE_BURST = reg("collapse.crumble_burst");
   /** Phase 2: steel frame groaning and bending. */
   public static final RegistryObject<SoundEvent> METAL_GROAN = reg("collapse.metal_groan");
   /** Phase 2: a sharp structural crack (concrete and timber splitting). */
   public static final RegistryObject<SoundEvent> CRACK = reg("collapse.crack");
   /** Phase 2: a facade piece breaking off and clattering down. */
   public static final RegistryObject<SoundEvent> FACADE_SHED = reg("collapse.facade_shed");
   /** Phase 3: the roar of the section coming down. */
   public static final RegistryObject<SoundEvent> ROAR = reg("collapse.roar");
   /** Phase 3 end: the section hits the ground (slam and boom). */
   public static final RegistryObject<SoundEvent> GROUND_IMPACT = reg("collapse.ground_impact");
   /** Phase 3 end: debris scattering across the street. */
   public static final RegistryObject<SoundEvent> DEBRIS_SCATTER = reg("collapse.debris_scatter");
   /** Low rumble felt under the slam (the sub layer). */
   public static final RegistryObject<SoundEvent> RUMBLE = reg("collapse.rumble");
   /** Phase 4: rubble settling, stones trickling. */
   public static final RegistryObject<SoundEvent> TAIL = reg("collapse.tail");
   /** Phase 4: gravel and dust pouring off the pile. */
   public static final RegistryObject<SoundEvent> DUST = reg("collapse.dust");
   /** The whole collapse heard from far away: a dull rolling boom. */
   public static final RegistryObject<SoundEvent> DISTANT = reg("collapse.distant");

   private CollapseSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }
}
