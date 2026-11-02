package dev.baranhan.viltrumitecore.worldevent.godzilla;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events of the kaiju. All playback is client side ({@code client.godzilla.GodzillaAudio}): cues are placed at
 * their source with our own distance curve and a speed-of-sound delay, driven by the synced move start and the FX
 * packets, so the server plays nothing and a far player hears the far layers only. No vanilla sounds: fully original
 * synthesis by {@code tools/sfx/godzilla.py} (CC0), ledger {@code tools/assets/godzilla_sources.md}.
 */
public final class GodzillaSounds {
   /** Signature roar, near layer (throat, bellow and the metallic screech). */
   public static final RegistryObject<SoundEvent> ROAR = reg("godzilla.roar");
   /** The same roar heard across the city: low, rolling, with a long echo off the towers. */
   public static final RegistryObject<SoundEvent> ROAR_FAR = reg("godzilla.roar_far");
   /** Short threat growl (looking at a hero, between moves). */
   public static final RegistryObject<SoundEvent> GROWL = reg("godzilla.growl");
   /** A footfall up close: the crushing slam and the ground thud. */
   public static final RegistryObject<SoundEvent> STEP = reg("godzilla.step");
   /** A footfall far away: a felt sub boom. */
   public static final RegistryObject<SoundEvent> STEP_FAR = reg("godzilla.step_far");
   /** Stomp: the deliberate slam and the shockwave crack running out. */
   public static final RegistryObject<SoundEvent> STOMP = reg("godzilla.stomp");
   public static final RegistryObject<SoundEvent> TAIL_SWISH = reg("godzilla.tail_swish");
   public static final RegistryObject<SoundEvent> TAIL_HIT = reg("godzilla.tail_hit");
   public static final RegistryObject<SoundEvent> CLAW_SWISH = reg("godzilla.claw_swish");
   public static final RegistryObject<SoundEvent> CLAW_HIT = reg("godzilla.claw_hit");
   /** Breath charge: the plates igniting one by one, the rising hum and the crackle in the throat. */
   public static final RegistryObject<SoundEvent> CHARGE = reg("godzilla.charge");
   /** Breath release: the blast as the beam leaves the mouth. */
   public static final RegistryObject<SoundEvent> BEAM_START = reg("godzilla.beam_start");
   /** The beam itself (loop, follows the head). */
   public static final RegistryObject<SoundEvent> BEAM_LOOP = reg("godzilla.beam_loop");
   /** Where the beam hits (loop, follows the impact point): roaring fire and melting stone. */
   public static final RegistryObject<SoundEvent> BEAM_IMPACT = reg("godzilla.beam_impact");
   /** The beam dying out: a falling whine and the plates cooling. */
   public static final RegistryObject<SoundEvent> BEAM_END = reg("godzilla.beam_end");
   /** The body ploughing through a building. */
   public static final RegistryObject<SoundEvent> SMASH = reg("godzilla.smash");
   /** A heavy hit lands on the kaiju: a pained bellow. */
   public static final RegistryObject<SoundEvent> HURT = reg("godzilla.hurt");
   /** The body hitting the ground (fall and death). */
   public static final RegistryObject<SoundEvent> FALL = reg("godzilla.fall");
   /** Getting up: a strained growl, debris sliding off. */
   public static final RegistryObject<SoundEvent> RISE = reg("godzilla.rise");
   /** The last roar, breaking off. */
   public static final RegistryObject<SoundEvent> DEATH = reg("godzilla.death");
   /** The arrival: the ground trembling and a far roar. */
   public static final RegistryObject<SoundEvent> ARRIVE = reg("godzilla.arrive");
   /** The final retreat: a long call as it turns away. */
   public static final RegistryObject<SoundEvent> RETREAT = reg("godzilla.retreat");

   /** Every cue in registry order (assets test: one sound file, one subtitle and one ledger line each). */
   public static final List<String> NAMES = List.of("roar", "roar_far", "growl", "step", "step_far", "stomp", "tail_swish", "tail_hit",
      "claw_swish", "claw_hit", "charge", "beam_start", "beam_loop", "beam_impact", "beam_end", "smash", "hurt", "fall", "rise", "death",
      "arrive", "retreat");

   private GodzillaSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   public static void init() {
   }
}
