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
