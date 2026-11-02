package dev.baranhan.viltrumitecore.scp.scp106;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of SCP-106 and its pocket dimension (src/main/sound-overlays/scp106.json). Every file is CC0 from
 * Freesound via Openverse, edited for the mod; sources, authors and licences: tools/assets/scp106_sources.md.
 */
public final class Scp106Sounds {
   public static final RegistryObject<SoundEvent> BREATH = reg("scp106.breath");
   public static final RegistryObject<SoundEvent> STEP = reg("scp106.step");
   public static final RegistryObject<SoundEvent> EMERGE = reg("scp106.emerge");
   public static final RegistryObject<SoundEvent> SINK = reg("scp106.sink");
   public static final RegistryObject<SoundEvent> CAPTURE = reg("scp106.capture");
   public static final RegistryObject<SoundEvent> LAUGH = reg("scp106.laugh");
   public static final RegistryObject<SoundEvent> CORRODE = reg("scp106.corrode");
   public static final RegistryObject<SoundEvent> PHASE = reg("scp106.phase");
   public static final RegistryObject<SoundEvent> HUM = reg("scp106.pocket.hum");
   public static final RegistryObject<SoundEvent> VOID_DRONE = reg("scp106.pocket.void");
   public static final RegistryObject<SoundEvent> WATER = reg("scp106.pocket.water");
   public static final RegistryObject<SoundEvent> FAR_STEPS = reg("scp106.pocket.far_steps");
   public static final RegistryObject<SoundEvent> CREAK = reg("scp106.pocket.creak");
   public static final RegistryObject<SoundEvent> METAL = reg("scp106.pocket.metal");
   public static final RegistryObject<SoundEvent> DRIP = reg("scp106.pocket.drip");
   public static final RegistryObject<SoundEvent> WHISPER = reg("scp106.pocket.whisper");
   public static final RegistryObject<SoundEvent> KNOCK = reg("scp106.pocket.knock");
   public static final RegistryObject<SoundEvent> DRAFT = reg("scp106.pocket.draft");
   public static final RegistryObject<SoundEvent> SIGIL = reg("scp106.pocket.sigil");
   public static final RegistryObject<SoundEvent> BREACH = reg("scp106.pocket.breach");
   public static final RegistryObject<SoundEvent> SCARE = reg("scp106.pocket.scare");
   public static final RegistryObject<SoundEvent> ESCAPE = reg("scp106.pocket.escape");
   public static final RegistryObject<SoundEvent> LOCKED = reg("scp106.pocket.locked");
   public static final RegistryObject<SoundEvent> KEY = reg("scp106.pocket.key");
   public static final RegistryObject<SoundEvent> SHIFT = reg("scp106.pocket.shift");

   private Scp106Sounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }
}
