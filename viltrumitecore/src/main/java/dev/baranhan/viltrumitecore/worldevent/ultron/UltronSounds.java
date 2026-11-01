package dev.baranhan.viltrumitecore.worldevent.ultron;

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
 * Ultron uprising sound events. Every file is our own layered audio (procedural synthesis plus CC0
 * recordings, tools/sfx/ultron.py, ledger tools/assets/ultron_sources.md); no vanilla sound plays for a
 * machine, a node or Ultron. No voice and no speech synthesis: Ultron talks only through the subtitles
 * ({@link UltronVoice}); a line only gets a short transmission blip. Events live in
 * src/main/sound-overlays/ultron.json with subtitles in the ultron lang overlay.
 */
public final class UltronSounds {
   // scout drone
   public static final RegistryObject<SoundEvent> DRONE_HOVER = reg("ultron.drone_hover");
   public static final RegistryObject<SoundEvent> DRONE_ZAP = reg("ultron.drone_zap");
   public static final RegistryObject<SoundEvent> DRONE_SCAN = reg("ultron.drone_scan");
   public static final RegistryObject<SoundEvent> DRONE_MARK = reg("ultron.drone_mark");
   public static final RegistryObject<SoundEvent> DRONE_HURT = reg("ultron.drone_hurt");
   public static final RegistryObject<SoundEvent> DRONE_DEATH = reg("ultron.drone_death");
   // combat robot
   public static final RegistryObject<SoundEvent> SENTRY_STEP = reg("ultron.sentry_step");
   public static final RegistryObject<SoundEvent> SENTRY_SERVO = reg("ultron.sentry_servo");
   public static final RegistryObject<SoundEvent> SENTRY_SWING = reg("ultron.sentry_swing");
   public static final RegistryObject<SoundEvent> SENTRY_STRIKE = reg("ultron.sentry_strike");
   public static final RegistryObject<SoundEvent> SENTRY_BLAST = reg("ultron.sentry_blast");
   public static final RegistryObject<SoundEvent> SENTRY_HURT = reg("ultron.sentry_hurt");
   public static final RegistryObject<SoundEvent> SENTRY_DEATH = reg("ultron.sentry_death");
   // heavy assault robot
   public static final RegistryObject<SoundEvent> BRUTE_STEP = reg("ultron.brute_step");
   public static final RegistryObject<SoundEvent> BRUTE_SERVO = reg("ultron.brute_servo");
   public static final RegistryObject<SoundEvent> BRUTE_SLAM = reg("ultron.brute_slam");
   public static final RegistryObject<SoundEvent> BRUTE_SWING = reg("ultron.brute_swing");
   public static final RegistryObject<SoundEvent> BRUTE_MISSILES = reg("ultron.brute_missiles");
   public static final RegistryObject<SoundEvent> BRUTE_CHARGE = reg("ultron.brute_charge");
   public static final RegistryObject<SoundEvent> BRUTE_SHIELD = reg("ultron.brute_shield");
   public static final RegistryObject<SoundEvent> BRUTE_HURT = reg("ultron.brute_hurt");
   public static final RegistryObject<SoundEvent> BRUTE_DEATH = reg("ultron.brute_death");
   // flying interceptor
   public static final RegistryObject<SoundEvent> INTERCEPTOR_JET = reg("ultron.interceptor_jet");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_BURST = reg("ultron.interceptor_burst");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_DIVE = reg("ultron.interceptor_dive");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_BOOST = reg("ultron.interceptor_boost");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_HURT = reg("ultron.interceptor_hurt");
   public static final RegistryObject<SoundEvent> INTERCEPTOR_DEATH = reg("ultron.interceptor_death");
   // engineer
   public static final RegistryObject<SoundEvent> ENGINEER_STEP = reg("ultron.engineer_step");
   public static final RegistryObject<SoundEvent> ENGINEER_WELD = reg("ultron.engineer_weld");
   public static final RegistryObject<SoundEvent> ENGINEER_HACK = reg("ultron.engineer_hack");
   public static final RegistryObject<SoundEvent> ENGINEER_PLANT = reg("ultron.engineer_plant");
   public static final RegistryObject<SoundEvent> ENGINEER_HURT = reg("ultron.engineer_hurt");
   public static final RegistryObject<SoundEvent> ENGINEER_DEATH = reg("ultron.engineer_death");
   // Ultron Prime
   public static final RegistryObject<SoundEvent> PRIME_ASSEMBLE = reg("ultron.prime_assemble");
   public static final RegistryObject<SoundEvent> PRIME_STEP = reg("ultron.prime_step");
   public static final RegistryObject<SoundEvent> PRIME_WHOOSH = reg("ultron.prime_whoosh");
   public static final RegistryObject<SoundEvent> PRIME_IMPACT = reg("ultron.prime_impact");
   public static final RegistryObject<SoundEvent> PRIME_PALM = reg("ultron.prime_palm");
   public static final RegistryObject<SoundEvent> PRIME_BEAM_CHARGE = reg("ultron.prime_beam_charge");
   public static final RegistryObject<SoundEvent> PRIME_BEAM = reg("ultron.prime_beam");
   public static final RegistryObject<SoundEvent> PRIME_SLAM = reg("ultron.prime_slam");
   public static final RegistryObject<SoundEvent> PRIME_DASH = reg("ultron.prime_dash");
   public static final RegistryObject<SoundEvent> PRIME_TRANSITION = reg("ultron.prime_transition");
   public static final RegistryObject<SoundEvent> PRIME_HURT = reg("ultron.prime_hurt");
   public static final RegistryObject<SoundEvent> PRIME_DEATH = reg("ultron.prime_death");
   public static final RegistryObject<SoundEvent> PRIME_ASCEND = reg("ultron.prime_ascend");
   public static final RegistryObject<SoundEvent> PRIME_SUMMON = reg("ultron.prime_summon");
   public static final RegistryObject<SoundEvent> PRIME_ABSORB = reg("ultron.prime_absorb");
   public static final RegistryObject<SoundEvent> PRIME_JET = reg("ultron.prime_jet");
   // network nodes
   public static final RegistryObject<SoundEvent> NODE_HUM = reg("ultron.node_hum");
   public static final RegistryObject<SoundEvent> NODE_BUILD = reg("ultron.node_build");
   public static final RegistryObject<SoundEvent> NODE_ONLINE = reg("ultron.node_online");
   public static final RegistryObject<SoundEvent> NODE_SHIELD_HIT = reg("ultron.node_shield_hit");
   public static final RegistryObject<SoundEvent> NODE_SHIELD_BREAK = reg("ultron.node_shield_break");
   public static final RegistryObject<SoundEvent> NODE_DISABLED = reg("ultron.node_disabled");
   public static final RegistryObject<SoundEvent> NODE_REBOOT = reg("ultron.node_reboot");
   public static final RegistryObject<SoundEvent> NODE_DESTROYED = reg("ultron.node_destroyed");
   public static final RegistryObject<SoundEvent> NODE_HACK = reg("ultron.node_hack");
   public static final RegistryObject<SoundEvent> NODE_TETHER = reg("ultron.node_tether");
   // projectiles and shared
   public static final RegistryObject<SoundEvent> BOLT_IMPACT = reg("ultron.bolt_impact");
   public static final RegistryObject<SoundEvent> MISSILE_FLIGHT = reg("ultron.missile_flight");
   public static final RegistryObject<SoundEvent> MISSILE_EXPLODE = reg("ultron.missile_explode");
   public static final RegistryObject<SoundEvent> DEFLECT = reg("ultron.deflect");
   public static final RegistryObject<SoundEvent> DEBRIS = reg("ultron.debris");
   public static final RegistryObject<SoundEvent> SPLASH = reg("ultron.splash");
   public static final RegistryObject<SoundEvent> FABRICATE = reg("ultron.fabricate");
   // event cues
   public static final RegistryObject<SoundEvent> EVENT_START = reg("ultron.event_start");
   public static final RegistryObject<SoundEvent> PHASE_CHANGE = reg("ultron.phase_change");
   public static final RegistryObject<SoundEvent> PROTOCOL = reg("ultron.protocol");
   public static final RegistryObject<SoundEvent> TRANSMISSION = reg("ultron.transmission");
   public static final RegistryObject<SoundEvent> CAPTURE = reg("ultron.capture");
   public static final RegistryObject<SoundEvent> RELEASE = reg("ultron.release");
   public static final RegistryObject<SoundEvent> DISCOVER = reg("ultron.discover");
   public static final RegistryObject<SoundEvent> UPLOAD_ALARM = reg("ultron.upload_alarm");
   public static final RegistryObject<SoundEvent> VICTORY = reg("ultron.victory");
   public static final RegistryObject<SoundEvent> DEFEAT = reg("ultron.defeat");

   /** Every registered name (tests: registry, sounds overlay, files and subtitles stay in step). */
   public static final List<String> NAMES = List.of("drone_hover", "drone_zap", "drone_scan", "drone_mark", "drone_hurt", "drone_death", "sentry_step",
      "sentry_servo", "sentry_swing", "sentry_strike", "sentry_blast", "sentry_hurt", "sentry_death", "brute_step", "brute_servo", "brute_slam",
      "brute_swing", "brute_missiles", "brute_charge", "brute_shield", "brute_hurt", "brute_death", "interceptor_jet", "interceptor_burst",
      "interceptor_dive", "interceptor_boost", "interceptor_hurt", "interceptor_death", "engineer_step", "engineer_weld", "engineer_hack",
      "engineer_plant", "engineer_hurt", "engineer_death", "prime_assemble", "prime_step", "prime_whoosh", "prime_impact", "prime_palm",
      "prime_beam_charge", "prime_beam", "prime_slam", "prime_dash", "prime_transition", "prime_hurt", "prime_death", "prime_ascend", "prime_summon",
      "prime_absorb", "prime_jet", "node_hum", "node_build", "node_online", "node_shield_hit", "node_shield_break", "node_disabled", "node_reboot",
      "node_destroyed", "node_hack", "node_tether", "bolt_impact", "missile_flight", "missile_explode", "deflect", "debris", "splash", "fabricate",
      "event_start", "phase_change", "protocol", "transmission", "capture", "release", "discover", "upload_alarm", "victory", "defeat");

   private UltronSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Class-load hook: the static holders above register on first touch (mod constructor). */
   public static void init() {
   }

   /** Positional one-shot for everyone nearby (16 x max(volume, 1) blocks). */
   public static void play(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch) {
      level.playSound(null, at.x, at.y, at.z, sound.get(), SoundSource.HOSTILE, volume, pitch);
   }

   /** Positional one-shot sent to every player of the level within {@code range} blocks (phase cues, node losses, the boss). */
   public static void playWide(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch, double range) {
      Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.get());
      level.getServer().getPlayerList().broadcast(null, at.x, at.y, at.z, Math.max(range, 16.0 * Math.max(volume, 1.0F)), level.dimension(),
         new ClientboundSoundPacket(holder, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, level.getRandom().nextLong()));
   }
}
