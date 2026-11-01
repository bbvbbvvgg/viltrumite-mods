package dev.baranhan.viltrumitecore.scp.scp096;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of SCP-096 (src/main/sound-overlays/scp096.json). The mod ships no SCP:CB audio: the base events
 * fall back to vanilla sounds, and the client's built-in pack (client/scp096/Scp096OriginalSounds) replaces them
 * with the original SCP: Containment Breach files from the player's own copy of the game when it finds them.
 */
public final class Scp096Sounds {
   /** Sobbing loop while calm (SCP:CB 096.ogg), positional. */
   public static final RegistryObject<SoundEvent> CRY = reg("scp096.cry");
   /** Wailing loop during the hysteria (SCP:CB 096Angered.ogg), positional. */
   public static final RegistryObject<SoundEvent> ANGERED = reg("scp096.angered");
   /** Screaming loop during the chase (SCP:CB Scream.ogg), positional. */
   public static final RegistryObject<SoundEvent> SCREAM = reg("scp096.scream");
   /** The sting for the one who saw the face (SCP:CB Triggered.ogg), not positional. */
   public static final RegistryObject<SoundEvent> TRIGGERED = reg("scp096.triggered");
   /** Chase music for a hunted player (SCP:CB 096Chase.ogg), not positional. */
   public static final RegistryObject<SoundEvent> CHASE_MUSIC = reg("scp096.chase_music");

   private Scp096Sounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Class-load hook: the holders above register on first touch (mod constructor). */
   public static void init() {
   }
}
