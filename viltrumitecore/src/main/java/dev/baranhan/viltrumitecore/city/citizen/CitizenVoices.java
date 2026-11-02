package dev.baranhan.viltrumitecore.city.citizen;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Voices of citizens in peril (#138): screams when the floor gives way, a long scream that falls with the body,
 * calls for help from a ledge or a trapped floor, the gasps of witnesses and a crowd panicking in the street. Male
 * and female takes are separate events (the look of the citizen picks one). Mixed from CC0 Freesound recordings by
 * {@code tools/sfx/city_citizens.py}, sources in {@code tools/assets/city_citizens_sources.md}; no vanilla sounds.
 * The server plays them on the citizen ({@code Level#playSound(Player, Entity, ...)}), so a falling scream moves with
 * the body in single player and multiplayer alike; the client muffles them behind walls
 * ({@code client.city.CitizenVoiceOcclusion}). {@link #init()} runs from the mod constructor.
 */
public final class CitizenVoices {
   public static final RegistryObject<SoundEvent> SCREAM_MALE = reg("citizen.scream.male");
   public static final RegistryObject<SoundEvent> SCREAM_FEMALE = reg("citizen.scream.female");
   public static final RegistryObject<SoundEvent> FALL_MALE = reg("citizen.fall.male");
   public static final RegistryObject<SoundEvent> FALL_FEMALE = reg("citizen.fall.female");
   public static final RegistryObject<SoundEvent> HELP_MALE = reg("citizen.help.male");
   public static final RegistryObject<SoundEvent> HELP_FEMALE = reg("citizen.help.female");
   public static final RegistryObject<SoundEvent> WITNESS_MALE = reg("citizen.witness.male");
   public static final RegistryObject<SoundEvent> WITNESS_FEMALE = reg("citizen.witness.female");
   public static final RegistryObject<SoundEvent> CROWD_PANIC = reg("citizen.crowd_panic");

   /** Kinds of a single citizen's voice (the crowd is played at a place, not by a citizen). */
   public enum Kind {
      SCREAM,
      FALL,
      HELP,
      WITNESS
   }

   private CitizenVoices() {
   }

   public static SoundEvent of(Kind kind, boolean female) {
      return (switch (kind) {
         case SCREAM -> female ? SCREAM_FEMALE : SCREAM_MALE;
         case FALL -> female ? FALL_FEMALE : FALL_MALE;
         case HELP -> female ? HELP_FEMALE : HELP_MALE;
         case WITNESS -> female ? WITNESS_FEMALE : WITNESS_MALE;
      }).get();
   }

   /** True for the sound events of this class (the client occlusion wraps exactly these). */
   public static boolean isVoice(ResourceLocation id) {
      return ViltrumiteCore.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("citizen.");
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ViltrumiteCore.MOD_ID, name)));
   }

   public static void init() {
   }
}
