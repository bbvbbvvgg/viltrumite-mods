package dev.baranhan.viltrumitecore.interaction;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the contextual interactions. Files are derived from the mod's own CC0 sound bank by
 * {@code tools/sfx/interactions.py} (ledger {@code tools/assets/interaction_sources.md}); events live in
 * {@code src/main/sound-overlays/interactions.json}, subtitles in {@code lang-overlays/interactions}.
 */
public final class InteractionSounds {
   public static final RegistryObject<SoundEvent> SHIELD_SHATTER = reg("interaction.shield_shatter");
   public static final RegistryObject<SoundEvent> STORM_SHIELD = reg("interaction.storm_shield");
   public static final RegistryObject<SoundEvent> METEOR_BURST = reg("interaction.meteor_burst");
   public static final RegistryObject<SoundEvent> METEOR_SPLIT = reg("interaction.meteor_split");
   public static final RegistryObject<SoundEvent> OVERCHARGE = reg("interaction.overcharge");
   public static final RegistryObject<SoundEvent> DIVINE_CLASH = reg("interaction.divine_clash");
   public static final RegistryObject<SoundEvent> TITAN_FLING = reg("interaction.titan_fling");
   public static final RegistryObject<SoundEvent> STORM_REBOUND = reg("interaction.storm_rebound");
   public static final RegistryObject<SoundEvent> VIBRANIUM_RING = reg("interaction.vibranium_ring");
   public static final RegistryObject<SoundEvent> REPULSOR_SPLIT = reg("interaction.repulsor_split");
   public static final RegistryObject<SoundEvent> TIME_REWIND = reg("interaction.time_rewind");
   public static final RegistryObject<SoundEvent> LION_STRAIN = reg("interaction.lion_strain");
   public static final RegistryObject<SoundEvent> SHIELD_CRUSH = reg("interaction.shield_crush");
   public static final RegistryObject<SoundEvent> METEOR_CLEAVE = reg("interaction.meteor_cleave");
   public static final RegistryObject<SoundEvent> METEOR_HURL = reg("interaction.meteor_hurl");
   public static final RegistryObject<SoundEvent> SHADOW_EXCHANGE = reg("interaction.shadow_exchange");
   public static final RegistryObject<SoundEvent> LEVIATHAN_IMPACT = reg("interaction.leviathan_impact");
   public static final RegistryObject<SoundEvent> LEGION_DISCHARGE = reg("interaction.legion_discharge");
   public static final RegistryObject<SoundEvent> SPINE_LIGHTNING = reg("interaction.spine_lightning");
   public static final RegistryObject<SoundEvent> LEVIATHAN_HALT = reg("interaction.leviathan_halt");

   private InteractionSounds() {
   }

   public static void init() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** One positional one-shot heard by everyone in range (server). */
   public static void playAt(Entity source, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      source.level().playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
   }
}
