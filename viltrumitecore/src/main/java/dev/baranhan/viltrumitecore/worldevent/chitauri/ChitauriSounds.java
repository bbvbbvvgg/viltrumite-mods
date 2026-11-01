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
   public static final RegistryObject<SoundEvent> CHARIOT_HIT = reg("chitauri.chariot_hit");
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
   // ---- city war (docs/plans/2026-11-04-chitauri-evolution.md; tools/sfx/chitauri_units.py) ----
   public static final RegistryObject<SoundEvent> CAPTOR_GRAB = reg("chitauri.captor_grab");
   public static final RegistryObject<SoundEvent> CAPTOR_TETHER = reg("chitauri.captor_tether");
   public static final RegistryObject<SoundEvent> CAPTOR_GROWL = reg("chitauri.captor_growl");
   public static final RegistryObject<SoundEvent> HEAVY_CANNON = reg("chitauri.heavy_cannon");
   public static final RegistryObject<SoundEvent> HEAVY_VENT = reg("chitauri.heavy_vent");
   public static final RegistryObject<SoundEvent> ARMOR_BREAK = reg("chitauri.armor_break");
   public static final RegistryObject<SoundEvent> HEAVY_SLAM = reg("chitauri.heavy_slam");
   public static final RegistryObject<SoundEvent> HEAVY_WINDUP = reg("chitauri.heavy_windup");
   public static final RegistryObject<SoundEvent> HEAVY_HURT = reg("chitauri.heavy_hurt");
   public static final RegistryObject<SoundEvent> HEAVY_DEATH = reg("chitauri.heavy_death");
   public static final RegistryObject<SoundEvent> HEAVY_GROWL = reg("chitauri.heavy_growl");
   public static final RegistryObject<SoundEvent> HEAVY_STEP = reg("chitauri.heavy_step");
   public static final RegistryObject<SoundEvent> COMMANDER_STAFF = reg("chitauri.commander_staff");
   public static final RegistryObject<SoundEvent> COMMANDER_SWEEP = reg("chitauri.commander_sweep");
   public static final RegistryObject<SoundEvent> COMMANDER_ORDER = reg("chitauri.commander_order");
   public static final RegistryObject<SoundEvent> COMMANDER_SIGNAL = reg("chitauri.commander_signal");
   public static final RegistryObject<SoundEvent> COMMANDER_GROWL = reg("chitauri.commander_growl");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_ENGINE = reg("chitauri.interceptor_engine");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_BOOST = reg("chitauri.interceptor_boost");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_FIRE = reg("chitauri.interceptor_fire");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_DOWN = reg("chitauri.interceptor_down");
   public static final RegistryObject<SoundEvent> SIEGE_HUM = reg("chitauri.siege_hum");
   public static final RegistryObject<SoundEvent> SIEGE_CHARGE = reg("chitauri.siege_charge");
   public static final RegistryObject<SoundEvent> SIEGE_FIRE = reg("chitauri.siege_fire");
   public static final RegistryObject<SoundEvent> SIEGE_IMPACT = reg("chitauri.siege_impact");
   public static final RegistryObject<SoundEvent> BEACON_DEPLOY = reg("chitauri.beacon_deploy");
   public static final RegistryObject<SoundEvent> BEACON_HUM = reg("chitauri.beacon_hum");
   public static final RegistryObject<SoundEvent> BEACON_DOWN = reg("chitauri.beacon_down");
   public static final RegistryObject<SoundEvent> CAPTIVE_FREED = reg("chitauri.captive_freed");
   public static final RegistryObject<SoundEvent> SECTOR_SEIZED = reg("chitauri.sector_seized");
   public static final RegistryObject<SoundEvent> SECTOR_LIBERATED = reg("chitauri.sector_liberated");

   /** Every registered name (tests: registry, sounds.json, files and subtitles stay in step). */
   public static final List<String> NAMES = List.of("rifle_fire", "rifle_charge", "bolt_impact", "cannon_fire", "explosion", "chariot_engine",
      "chariot_flyby", "chariot_hit", "chariot_explode", "soldier_chatter", "soldier_hurt", "soldier_death", "soldier_step", "soldier_melee", "leviathan_roar",
      "leviathan_loop", "leviathan_hit", "leviathan_smash", "leviathan_crash", "leviathan_death", "portal_open", "portal_loop", "portal_thunder",
      "portal_close", "debris", "phase_horn", "sever",
      "captor_grab", "captor_tether", "captor_growl", "heavy_cannon", "heavy_vent", "armor_break", "heavy_slam", "heavy_windup", "heavy_hurt",
      "heavy_death", "heavy_growl", "heavy_step", "commander_staff", "commander_sweep", "commander_order", "commander_signal", "commander_growl",
      "interceptor_engine", "interceptor_boost", "interceptor_fire", "interceptor_down", "siege_hum", "siege_charge", "siege_fire", "siege_impact",
      "beacon_deploy", "beacon_hum", "beacon_down", "captive_freed", "sector_seized", "sector_liberated");

   private ChitauriSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Class-load hook: the static holders above register on first touch (mod constructor). */
   public static void init() {
   }

   /**
    * Positional one-shot for everyone nearby. The server sends it within 16 x max(volume, 1) blocks and the client
    * rolls it off over attenuation_distance x max(volume, 1): a volume above 1 only widens the range.
    */
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
