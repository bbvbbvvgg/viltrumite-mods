package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.RegistryObject;

/**
 * Reinhard sound events. sounds.json owns licensed recordings, original
 * procedural cinematic layers and retained vanilla kit sounds.
 * Registered on ViltrumiteCore.SOUND_EVENTS; {@link #init()} runs from the
 * mod constructor so the holders exist before registration.
 */
public final class ReinhardSounds {
   public static final RegistryObject<SoundEvent> AIR_STEP = reg("reinhard.air_step");
   public static final RegistryObject<SoundEvent> MODE_RESTRAINED = reg("reinhard.mode_restrained");
   public static final RegistryObject<SoundEvent> MODE_KNIGHT = reg("reinhard.mode_knight");
   public static final RegistryObject<SoundEvent> MODE_HOLY_SWORD = reg("reinhard.mode_holy_sword");
   public static final RegistryObject<SoundEvent> REFUSE = reg("reinhard.refuse");
   public static final RegistryObject<SoundEvent> PHOENIX = reg("reinhard.phoenix");
   public static final RegistryObject<SoundEvent> PARRY = reg("reinhard.parry");
   public static final RegistryObject<SoundEvent> SUPER_JUMP = reg("reinhard.super_jump");
   // Stage 3 abilities (spec §6, §10.2: mod sounds where named, vanilla placeholders otherwise).
   public static final RegistryObject<SoundEvent> LIGHT_PUNCH = reg("reinhard.light_punch");
   public static final RegistryObject<SoundEvent> STEP_DASH = reg("reinhard.step_dash");
   public static final RegistryObject<SoundEvent> CATCH = reg("reinhard.catch");
   public static final RegistryObject<SoundEvent> COMBO_HIT = reg("reinhard.combo_hit");
   /** Overhaul: ordinary LMB strikes. Fist whoosh / body hit; Reid heavy swing, cut and finisher (three variants each). */
   public static final RegistryObject<SoundEvent> FIST_SWING = reg("reinhard.fist_swing");
   public static final RegistryObject<SoundEvent> FIST_HIT = reg("reinhard.fist_hit");
   public static final RegistryObject<SoundEvent> REID_SWING = reg("reinhard.reid_swing");
   public static final RegistryObject<SoundEvent> REID_HIT = reg("reinhard.reid_hit");
   public static final RegistryObject<SoundEvent> REID_HEAVY = reg("reinhard.reid_heavy");
   /** Overhaul: quiet loop while Reid is drawn and settled in the hand (client sound instance). */
   public static final RegistryObject<SoundEvent> REID_DRAWN_HUM = reg("reinhard.reid_drawn_hum");
   public static final RegistryObject<SoundEvent> COMBO_FINAL = reg("reinhard.combo_final");
   public static final RegistryObject<SoundEvent> KICK_TOSS = reg("reinhard.kick_toss");
   public static final RegistryObject<SoundEvent> SKY_KICK = reg("reinhard.sky_kick");
   public static final RegistryObject<SoundEvent> GRAB = reg("reinhard.grab");
   public static final RegistryObject<SoundEvent> THROW = reg("reinhard.throw");
   public static final RegistryObject<SoundEvent> DASH_BEHIND = reg("reinhard.dash_behind");
   public static final RegistryObject<SoundEvent> STOMP = reg("reinhard.stomp");
   public static final RegistryObject<SoundEvent> REFLECT = reg("reinhard.reflect");
   // Stage 4: the Reid draw scene.
   public static final RegistryObject<SoundEvent> REID_RUMBLE = reg("reinhard.reid_rumble");
   public static final RegistryObject<SoundEvent> REID_UNSHEATHE = reg("reinhard.reid_unsheathe");
   public static final RegistryObject<SoundEvent> REID_CHOIR = reg("reinhard.reid_choir");
   public static final RegistryObject<SoundEvent> REID_FAR = reg("reinhard.reid_far");
   /** REINHARD OVERHAUL item 15: the draw's own cues, one per phase, started on the scene clock by the client
    * ({@code ReidDrawAudio}; tools/sfx/reinhard_draw.py): the rising hum, the koiguchi, the long scrape with
    * the exit at the release, the vibrating air and the final accent. */
   public static final RegistryObject<SoundEvent> REID_DRAW_HUM = reg("reinhard.reid_draw_hum");
   public static final RegistryObject<SoundEvent> REID_DRAW_KOIGUCHI = reg("reinhard.reid_draw_koiguchi");
   public static final RegistryObject<SoundEvent> REID_DRAW_SCRAPE = reg("reinhard.reid_draw_scrape");
   public static final RegistryObject<SoundEvent> REID_DRAW_AIR = reg("reinhard.reid_draw_air");
   public static final RegistryObject<SoundEvent> REID_DRAW_RELEASE = reg("reinhard.reid_draw_release");
   /** The calm 1 s sheathe click (manual, auto-return, leaving Holy Sword). */
   public static final RegistryObject<SoundEvent> REID_SHEATHE = reg("reinhard.reid_sheathe");
   /** The sheathed-blade ring ({@code reinhard/sheath_ring}, Iridiuss CC0; the same file voices the World Cut blade accent). */
   public static final RegistryObject<SoundEvent> SHEATH_STRIKE = reg("reinhard.sheath_strike");
   /**
    * Saw Sphere (item 19). The swing reuses the cinematic sweep file ({@code reinhard/cut_cinematic_sweep});
    * hum, grind, finale and shatter are original synthesis (tools/sfx/reinhard_saw_sphere.py, CC0).
    */
   public static final RegistryObject<SoundEvent> SAW_HUM = reg("reinhard.saw_hum");
   public static final RegistryObject<SoundEvent> SAW_SWING = reg("reinhard.saw_swing");
   public static final RegistryObject<SoundEvent> SAW_GRIND = reg("reinhard.saw_grind");
   public static final RegistryObject<SoundEvent> SAW_FINALE = reg("reinhard.saw_finale");
   public static final RegistryObject<SoundEvent> SAW_SHATTER = reg("reinhard.saw_shatter");

   public static final RegistryObject<SoundEvent> REID_CINEMATIC_LAYER = reg("reinhard.reid_cinematic_layer");
   public static final RegistryObject<SoundEvent> CUT_RUMBLE = reg("reinhard.cut_rumble");
   public static final RegistryObject<SoundEvent> WORLD_CUT_SILENCE = reg("reinhard.world_cut_silence");
   public static final RegistryObject<SoundEvent> WORLD_CUT_BLAST = reg("reinhard.world_cut_blast");
   public static final RegistryObject<SoundEvent> WORLD_CUT_TAIL = reg("reinhard.world_cut_tail");
   /** Seamless 1.99 s loop derived from the owner-provided tail recording. */
   public static final RegistryObject<SoundEvent> WORLD_CUT_HUM = reg("reinhard.world_cut_hum");
   /** Short pitched-down swell for the periodic rupture pulse. */
   public static final RegistryObject<SoundEvent> WORLD_CUT_PULSE = reg("reinhard.world_cut_pulse");
   /** Short saturated hit for the first BLAST of the rupture. */
   public static final RegistryObject<SoundEvent> WORLD_CUT_IMPACT = reg("reinhard.world_cut_impact");
   /** Blade ring for the normal cut only; same file as sheath_ring, own id for duck exemption. */
   public static final RegistryObject<SoundEvent> WORLD_CUT_BLADE = reg("reinhard.world_cut_blade");
   /**
    * TEMPORARY test asset (REINHARD OVERHAUL item 12): the owner-confirmed strike on Theresia
    * (Re:Zero S3E14), played once at the BLAST of the normal World Cut instead of
    * {@link #WORLD_CUT_IMPACT}. Its license is NOT verified: replace it with a licensed or original
    * sound before a public release and keep the BLAST timing (tools/assets/reinhard_sources.md).
    */
   public static final RegistryObject<SoundEvent> WORLD_CUT_STRIKE_VOICE = reg("reinhard.world_cut_strike_voice");
   /**
    * The heavy explosion under the strike voice on the BLAST of the normal World Cut: low boom, roar and a
    * rolling decay, original synthesis (tools/sfx/reinhard_explosion.py, CC0). Mono, positioned at the nearest
    * point of the cut and started by the same {@code WorldCutAudio.strike} call as the voice.
    */
   public static final RegistryObject<SoundEvent> WORLD_CUT_EXPLOSION = reg("reinhard.world_cut_explosion");

   public static final RegistryObject<SoundEvent> ERASE_CRACK = reg("reinhard.erase_crack");
   public static final RegistryObject<SoundEvent> ERASE_INHALE = reg("reinhard.erase_inhale");
   /** World Erasure scene cues (original procedural, tools/sfx/reinhard_erasure.py), on the ErasureScene beats. */
   public static final RegistryObject<SoundEvent> ERASE_TENSION = reg("reinhard.erase_tension");
   public static final RegistryObject<SoundEvent> ERASE_SHATTER = reg("reinhard.erase_shatter");
   public static final RegistryObject<SoundEvent> ERASE_DISSOLVE = reg("reinhard.erase_dissolve");
   public static final RegistryObject<SoundEvent> ERASE_SNAP = reg("reinhard.erase_snap");
   public static final RegistryObject<SoundEvent> ERASE_RESTORE = reg("reinhard.erase_restore");
   public static final RegistryObject<SoundEvent> LIMBO_HEARTBEAT = reg("reinhard.limbo_heartbeat");
   /** Final Punch secret scene cues (original procedural CC0, tools/sfx/reinhard_final_punch.py; no voice). */
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_MUSIC = reg("reinhard.final_punch_music");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_WIND = reg("reinhard.final_punch_wind");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_HUM = reg("reinhard.final_punch_hum");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_IGNITE = reg("reinhard.final_punch_ignite");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_SPARK = reg("reinhard.final_punch_spark");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_RING = reg("reinhard.final_punch_ring");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_PRESSURE = reg("reinhard.final_punch_pressure");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_CRACKLE = reg("reinhard.final_punch_crackle");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_WHOOSH = reg("reinhard.final_punch_whoosh");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_IMPACT = reg("reinhard.final_punch_impact");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_JAW = reg("reinhard.final_punch_jaw");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_CLANG = reg("reinhard.final_punch_clang");
   public static final RegistryObject<SoundEvent> FINAL_PUNCH_SMOKE = reg("reinhard.final_punch_smoke");

   private ReinhardSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** Loads the holders (class init). */
   public static void init() {
   }

   /** The "something in the world changed" sound of a mode (spec §5). */
   public static SoundEvent mode(ReinhardMode mode) {
      return switch (mode) {
         case RESTRAINED -> MODE_RESTRAINED.get();
         case KNIGHT -> MODE_KNIGHT.get();
         case HOLY_SWORD -> MODE_HOLY_SWORD.get();
      };
   }

   /** Everyone nearby hears it, the player included. */
   public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
   }

   /** Same, at a point (impact, arrival). */
   public static void playAt(ServerPlayer player, net.minecraft.world.phys.Vec3 at, SoundEvent sound, float volume, float pitch) {
      player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
   }
}
