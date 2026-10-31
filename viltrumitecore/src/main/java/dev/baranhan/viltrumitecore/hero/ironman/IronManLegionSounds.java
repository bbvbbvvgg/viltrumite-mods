package dev.baranhan.viltrumitecore.hero.ironman;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Iron Man Stage 6 sounds: the Iron Legion (own synthesis,
 * tools/sfx/ironman_legion.sh). Registered on ViltrumiteCore.SOUND_EVENTS;
 * events come from src/main/sound-overlays/legion.json.
 */
public final class IronManLegionSounds {
   /** Far sky signal: the suits break through the clouds. */
   public static final RegistryObject<SoundEvent> SIGNAL = reg("ironman_legion_signal");
   /** Client loop per suit (LegionEngineSound): thruster hum, pitch and volume follow the throttle. */
   public static final RegistryObject<SoundEvent> ENGINE = reg("ironman_legion_engine");
   /** Doppler whoosh of a suit passing close at speed. */
   public static final RegistryObject<SoundEvent> FLYBY = reg("ironman_legion_flyby");
   /** Feet-first retro burn over the slot. */
   public static final RegistryObject<SoundEvent> BRAKE = reg("ironman_legion_brake");
   /** Touchdown / hover settle clank. */
   public static final RegistryObject<SoundEvent> LAND = reg("ironman_legion_land");
   /** Joint servos of the ready move and turns. */
   public static final RegistryObject<SoundEvent> SERVO = reg("ironman_legion_servo");
   /** A suit's palm repulsor (lighter than Tony's). */
   public static final RegistryObject<SoundEvent> REPULSOR = reg("ironman_legion_repulsor");
   /** Thrusters light for a take-off or a boost. */
   public static final RegistryObject<SoundEvent> IGNITE = reg("ironman_legion_ignite");
   /** Critical damage: sputtering engine, warning chirps. */
   public static final RegistryObject<SoundEvent> FAILURE = reg("ironman_legion_failure");
   /** A broken suit hits the ground. */
   public static final RegistryObject<SoundEvent> CRASH = reg("ironman_legion_crash");
   /** Order acknowledged (short HUD chirp, played to the owner). */
   public static final RegistryObject<SoundEvent> ORDER = reg("ironman_legion_order");
   /** Armour hit on a suit (metal clang). */
   public static final RegistryObject<SoundEvent> HIT = reg("ironman_legion_hit");

   private IronManLegionSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Loads the holders (class init). */
   public static void init() {
   }
}
