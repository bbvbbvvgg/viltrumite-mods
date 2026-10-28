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
 * Sinclair sound events (spec §5, §10). Every file is original synthesis ({@code tools/sfx/sinclair.py}, CC0),
 * listed in {@code tools/assets/sinclair_sources.md}. No vanilla sound is used by any ability.
 * Registered on ViltrumiteCore.SOUND_EVENTS; {@link #init()} runs from the mod constructor.
 */
public final class SinclairSounds {
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

   /** Every Sinclair event name (sounds.json and the synthesis script must match it). */
   public static final List<String> NAMES = List.of(
      "wand_shot", "spark_hit", "wand_reload", "bolt_cast", "bolt_snap", "bolt_hit", "assistant_appear", "swap",
      "assistant_leave", "hex_cast", "hex_poof", "hex_end", "snap", "decoy_reveal", "tada", "refuse", "ovation",
      "ult_capture", "ult_card", "act_drumroll", "act_beam", "act_nova_whirl", "act_nova_burst", "act_cut_saw", "card_hit",
      "act_card_volley", "act_curtain", "act_curtain_open", "act_chest", "act_sword", "act_bow");

   private SinclairSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
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
