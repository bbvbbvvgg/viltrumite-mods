package dev.baranhan.viltrumitecore.worldevent.chitauri;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Chitauri sound events. Every file is original procedural audio (CC0, tools/sfx/chitauri.py,
 * ledger tools/assets/chitauri_sources.md); no vanilla sound plays for a Chitauri action.
 * Events live in src/main/sound-overlays/chitauri.json with subtitles in the chitauri lang overlay.
 */
public final class ChitauriSounds {
   public static final RegistryObject<SoundEvent> RIFLE_FIRE = reg("chitauri.rifle_fire");
   public static final RegistryObject<SoundEvent> RIFLE_CHARGE = reg("chitauri.rifle_charge");
   public static final RegistryObject<SoundEvent> BOLT_IMPACT = reg("chitauri.bolt_impact");
   public static final RegistryObject<SoundEvent> CANNON_FIRE = reg("chitauri.cannon_fire");
   public static final RegistryObject<SoundEvent> EXPLOSION = reg("chitauri.explosion");
   public static final RegistryObject<SoundEvent> CHARIOT_ENGINE = reg("chitauri.chariot_engine");
   public static final RegistryObject<SoundEvent> CHARIOT_FLYBY = reg("chitauri.chariot_flyby");
   public static final RegistryObject<SoundEvent> CHARIOT_EXPLODE = reg("chitauri.chariot_explode");
   public static final RegistryObject<SoundEvent> SOLDIER_CHATTER = reg("chitauri.soldier_chatter");
   public static final RegistryObject<SoundEvent> SOLDIER_HURT = reg("chitauri.soldier_hurt");
   public static final RegistryObject<SoundEvent> SOLDIER_DEATH = reg("chitauri.soldier_death");
   public static final RegistryObject<SoundEvent> SOLDIER_STEP = reg("chitauri.soldier_step");
   public static final RegistryObject<SoundEvent> SOLDIER_MELEE = reg("chitauri.soldier_melee");
   public static final RegistryObject<SoundEvent> LEVIATHAN_ROAR = reg("chitauri.leviathan_roar");
   public static final RegistryObject<SoundEvent> LEVIATHAN_LOOP = reg("chitauri.leviathan_loop");
   public static final RegistryObject<SoundEvent> LEVIATHAN_HIT = reg("chitauri.leviathan_hit");
   public static final RegistryObject<SoundEvent> LEVIATHAN_SMASH = reg("chitauri.leviathan_smash");
   public static final RegistryObject<SoundEvent> LEVIATHAN_CRASH = reg("chitauri.leviathan_crash");
   public static final RegistryObject<SoundEvent> LEVIATHAN_DEATH = reg("chitauri.leviathan_death");
   public static final RegistryObject<SoundEvent> PORTAL_OPEN = reg("chitauri.portal_open");
   public static final RegistryObject<SoundEvent> PORTAL_LOOP = reg("chitauri.portal_loop");
   public static final RegistryObject<SoundEvent> PORTAL_THUNDER = reg("chitauri.portal_thunder");
   public static final RegistryObject<SoundEvent> PORTAL_CLOSE = reg("chitauri.portal_close");
   public static final RegistryObject<SoundEvent> DEBRIS = reg("chitauri.debris");
   public static final RegistryObject<SoundEvent> PHASE_HORN = reg("chitauri.phase_horn");
   public static final RegistryObject<SoundEvent> SEVER = reg("chitauri.sever");

   /** Every registered name (tests: registry, sounds.json, files and subtitles stay in step). */
   public static final List<String> NAMES = List.of("rifle_fire", "rifle_charge", "bolt_impact", "cannon_fire", "explosion", "chariot_engine",
      "chariot_flyby", "chariot_explode", "soldier_chatter", "soldier_hurt", "soldier_death", "soldier_step", "soldier_melee", "leviathan_roar",
      "leviathan_loop", "leviathan_hit", "leviathan_smash", "leviathan_crash", "leviathan_death", "portal_open", "portal_loop", "portal_thunder",
      "portal_close", "debris", "phase_horn", "sever");

   private ChitauriSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Class-load hook: the static holders above register on first touch (mod constructor). */
   public static void init() {
   }

   /** Positional one-shot for everyone nearby (vanilla range: 16 x volume). */
   public static void play(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.HOSTILE, volume, pitch);
   }

   /** Positional one-shot sent to every player within {@code range} blocks (portal, Leviathan, big blasts). */
   public static void playWide(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch, double range) {
      Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.get());
      level.getServer().getPlayerList().broadcast(null, at.x, at.y, at.z, Math.max(range, 16.0 * Math.max(volume, 1.0F)), level.dimension(),
         new ClientboundSoundPacket(holder, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, level.getRandom().nextLong()));
   }
}
