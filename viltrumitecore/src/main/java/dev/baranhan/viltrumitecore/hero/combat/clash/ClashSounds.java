package dev.baranhan.viltrumitecore.hero.combat.clash;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the clashes (wave 1, session 23). The files are the mod's own procedural sounds (ffmpeg
 * synthesis, see the PR), events live in {@code src/main/sound-overlays/clash.json}, subtitles in
 * {@code lang-overlays/clash}.
 */
public final class ClashSounds {
   /** Two beams lock: a deep crack and a rising whine. */
   public static final RegistryObject<SoundEvent> BEAM_LOCK = reg("clash.beam_lock");
   /** The crackling roar at the clash point while the struggle runs (client loop). */
   public static final RegistryObject<SoundEvent> BEAM_HUM = reg("clash.beam_hum");
   /** A pulse on the beat: the beam surges forward. */
   public static final RegistryObject<SoundEvent> BEAM_SURGE = reg("clash.beam_surge");
   /** The winner's beam goes through / the draw blows up. */
   public static final RegistryObject<SoundEvent> BEAM_BLAST = reg("clash.beam_blast");

   private ClashSounds() {
   }

   /** Loads the holders before the register fires (ViltrumiteCore constructor). */
   public static void init() {
      ClashFx.init();
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }
}
