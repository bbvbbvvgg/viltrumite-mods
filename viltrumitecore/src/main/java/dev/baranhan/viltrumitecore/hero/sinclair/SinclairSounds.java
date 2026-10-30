package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sinclair sound events (spec §5, §10). Every file is original synthesis or a CC0 recording layered with it
 * ({@code tools/sfx/sinclair.py}, {@code tools/sfx/sinclair_cc0.py}),
 * listed in {@code tools/assets/sinclair_sources.md}. No vanilla sound is used by any ability.
 * Registered on ViltrumiteCore.SOUND_EVENTS; {@link #init()} runs from the mod constructor.
 */
public final class SinclairSounds {
   /** Resource namespace of Sinclair's own sound definitions (shared with his lang pack). */
   public static final String OWN_NAMESPACE = "viltrumitecore_sinclair";
   /** Events defined in {@code assets/viltrumitecore_sinclair/sounds.json} (see {@link #stage}). */
   public static final java.util.Set<String> OWN_SOUNDS = java.util.Set.of(
      "copy_sting", "copy_unfold", "copy_fold", "copy_return", "act_footlights", "act_beam_charge", "act_beam_burst", "act_hat_spin",
      "act_saw_rasp", "act_cut_slash", "act_card_fan", "act_card_finale", "act_curtain_rustle", "act_chest_rattle");
   // Prestige (§5.0)
   public static final RegistryObject<SoundEvent> WAND_SHOT = reg("sinclair.wand_shot");
   public static final RegistryObject<SoundEvent> SPARK_HIT = reg("sinclair.spark_hit");
   public static final RegistryObject<SoundEvent> WAND_RELOAD = reg("sinclair.wand_reload");
   // Vexing Bolt (§5.1)
   public static final RegistryObject<SoundEvent> BOLT_CAST = reg("sinclair.bolt_cast");
   public static final RegistryObject<SoundEvent> BOLT_SNAP = reg("sinclair.bolt_snap");
   public static final RegistryObject<SoundEvent> BOLT_HIT = reg("sinclair.bolt_hit");
   // Spectral Assistant (§5.2)
   public static final RegistryObject<SoundEvent> ASSISTANT_APPEAR = reg("sinclair.assistant_appear");
   public static final RegistryObject<SoundEvent> SWAP = reg("sinclair.swap");
   public static final RegistryObject<SoundEvent> ASSISTANT_LEAVE = reg("sinclair.assistant_leave");
   // Rabbit Hex (§5.4)
   public static final RegistryObject<SoundEvent> HEX_CAST = reg("sinclair.hex_cast");
   public static final RegistryObject<SoundEvent> HEX_POOF = reg("sinclair.hex_poof");
   public static final RegistryObject<SoundEvent> HEX_END = reg("sinclair.hex_end");
   // Misdirection (§5.5)
   public static final RegistryObject<SoundEvent> SNAP = reg("sinclair.snap");
   public static final RegistryObject<SoundEvent> DECOY_REVEAL = reg("sinclair.decoy_reveal");
   public static final RegistryObject<SoundEvent> TADA = reg("sinclair.tada");
   // Shared feedback
   public static final RegistryObject<SoundEvent> REFUSE = reg("sinclair.refuse");
   public static final RegistryObject<SoundEvent> OVATION = reg("sinclair.ovation");
   // Audience Participation (§5.6, §6.1)
   public static final RegistryObject<SoundEvent> ULT_CAPTURE = reg("sinclair.ult_capture");
   public static final RegistryObject<SoundEvent> ULT_CARD = reg("sinclair.ult_card");
   public static final RegistryObject<SoundEvent> ACT_DRUMROLL = reg("sinclair.act_drumroll");
   public static final RegistryObject<SoundEvent> ACT_BEAM = reg("sinclair.act_beam");
   public static final RegistryObject<SoundEvent> ACT_NOVA_WHIRL = reg("sinclair.act_nova_whirl");
   public static final RegistryObject<SoundEvent> ACT_NOVA_BURST = reg("sinclair.act_nova_burst");
   public static final RegistryObject<SoundEvent> ACT_CUT_SAW = reg("sinclair.act_cut_saw");
   public static final RegistryObject<SoundEvent> CARD_HIT = reg("sinclair.card_hit");
   public static final RegistryObject<SoundEvent> ACT_CARD_VOLLEY = reg("sinclair.act_card_volley");
   public static final RegistryObject<SoundEvent> ACT_CURTAIN = reg("sinclair.act_curtain");
   public static final RegistryObject<SoundEvent> ACT_CURTAIN_OPEN = reg("sinclair.act_curtain_open");
   public static final RegistryObject<SoundEvent> ACT_CHEST = reg("sinclair.act_chest");
   public static final RegistryObject<SoundEvent> ACT_SWORD = reg("sinclair.act_sword");
   public static final RegistryObject<SoundEvent> ACT_BOW = reg("sinclair.act_bow");
   // Copy cinematic and the four-part acts (design 2026-11-02 §2, §3)
   public static final RegistryObject<SoundEvent> COPY_STING = stage("copy_sting");
   public static final RegistryObject<SoundEvent> COPY_UNFOLD = stage("copy_unfold");
   public static final RegistryObject<SoundEvent> COPY_FOLD = stage("copy_fold");
   public static final RegistryObject<SoundEvent> COPY_RETURN = stage("copy_return");
   public static final RegistryObject<SoundEvent> ACT_FOOTLIGHTS = stage("act_footlights");
   public static final RegistryObject<SoundEvent> ACT_BEAM_CHARGE = stage("act_beam_charge");
   public static final RegistryObject<SoundEvent> ACT_BEAM_BURST = stage("act_beam_burst");
   public static final RegistryObject<SoundEvent> ACT_HAT_SPIN = stage("act_hat_spin");
   public static final RegistryObject<SoundEvent> ACT_SAW_RASP = stage("act_saw_rasp");
   public static final RegistryObject<SoundEvent> ACT_CUT_SLASH = stage("act_cut_slash");
   public static final RegistryObject<SoundEvent> ACT_CARD_FAN = stage("act_card_fan");
   public static final RegistryObject<SoundEvent> ACT_CARD_FINALE = stage("act_card_finale");
   public static final RegistryObject<SoundEvent> ACT_CURTAIN_RUSTLE = stage("act_curtain_rustle");
   public static final RegistryObject<SoundEvent> ACT_CHEST_RATTLE = stage("act_chest_rattle");

   /** Every Sinclair event name (sounds.json and the synthesis script must match it). */
   public static final List<String> NAMES = List.of(
      "wand_shot", "spark_hit", "wand_reload", "bolt_cast", "bolt_snap", "bolt_hit", "assistant_appear", "swap",
      "assistant_leave", "hex_cast", "hex_poof", "hex_end", "snap", "decoy_reveal", "tada", "refuse", "ovation",
      "ult_capture", "ult_card", "act_drumroll", "act_beam", "act_nova_whirl", "act_nova_burst", "act_cut_saw", "card_hit",
      "act_card_volley", "act_curtain", "act_curtain_open", "act_chest", "act_sword", "act_bow",
      "copy_sting", "copy_unfold", "copy_fold", "copy_return", "act_footlights", "act_beam_charge", "act_beam_burst", "act_hat_spin",
      "act_saw_rasp", "act_cut_slash", "act_card_fan", "act_card_finale", "act_curtain_rustle", "act_chest_rattle");

   private SinclairSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /**
    * Sound pass 2 events (2026-11-02): registry id {@code viltrumitecore:sinclair.<name>} like the others, but the
    * sound definition lives in Sinclair's own resource namespace ({@code assets/viltrumitecore_sinclair/sounds.json}),
    * so the shared 72 KB sounds.json is not edited (connector push limit, parallel hero branches). Audio files stay in
    * {@code assets/viltrumitecore/sounds/sinclair/}.
    */
   private static RegistryObject<SoundEvent> stage(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register("sinclair." + name, () -> SoundEvent.createVariableRangeEvent(location(name)));
   }

   /** Resource location a client sound instance of the event carries (SoundDuck exemptions). */
   public static ResourceLocation location(String name) {
      return OWN_SOUNDS.contains(name) ? new ResourceLocation(OWN_NAMESPACE, "sinclair." + name) : new ResourceLocation("viltrumitecore", "sinclair." + name);
   }

   /** Loads the holders (class init). */
   public static void init() {
   }

   /** Server: everyone nearby hears it, the caster included. */
   public static void play(Level level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      if (!level.isClientSide && sound.isPresent()) {
         level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
      }
   }
}
