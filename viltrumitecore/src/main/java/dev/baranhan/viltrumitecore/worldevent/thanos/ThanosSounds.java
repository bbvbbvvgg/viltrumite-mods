package dev.baranhan.viltrumitecore.worldevent.thanos;

import dev.baranhan.viltrumitecore.ViltrumiteCore;
import javax.annotation.Nullable;
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
 * Sound events of the Thanos event. Every file is original procedural audio (CC0,
 * {@code tools/sfx/thanos.py}, ledger {@code tools/assets/thanos_sources.md}); no vanilla sound
 * stands in for a stone. Events live in {@code src/main/sound-overlays/thanos.json}.
 */
public final class ThanosSounds {
   public static final RegistryObject<SoundEvent> ARRIVAL = reg("thanos.arrival");
   public static final RegistryObject<SoundEvent> STEP = reg("thanos.step");
   public static final RegistryObject<SoundEvent> SWING = reg("thanos.swing");
   public static final RegistryObject<SoundEvent> SLAM = reg("thanos.slam");
   public static final RegistryObject<SoundEvent> HIT = reg("thanos.hit");
   public static final RegistryObject<SoundEvent> HURT = reg("thanos.hurt");
   public static final RegistryObject<SoundEvent> STUN = reg("thanos.stun");
   public static final RegistryObject<SoundEvent> POWER_CHARGE = reg("thanos.power_charge");
   public static final RegistryObject<SoundEvent> POWER_IMPACT = reg("thanos.power_impact");
   public static final RegistryObject<SoundEvent> POWER_WAVE = reg("thanos.power_wave");
   public static final RegistryObject<SoundEvent> SPACE_OPEN = reg("thanos.space_open");
   public static final RegistryObject<SoundEvent> SPACE_STEP = reg("thanos.space_step");
   public static final RegistryObject<SoundEvent> SPACE_PULL = reg("thanos.space_pull");
   public static final RegistryObject<SoundEvent> SPACE_WARD = reg("thanos.space_ward");
   public static final RegistryObject<SoundEvent> REALITY_WARP = reg("thanos.reality_warp");
   public static final RegistryObject<SoundEvent> REALITY_MIRAGE = reg("thanos.reality_mirage");
   public static final RegistryObject<SoundEvent> TIME_DOME = reg("thanos.time_dome");
   public static final RegistryObject<SoundEvent> TIME_REWIND = reg("thanos.time_rewind");
   public static final RegistryObject<SoundEvent> MIND_GAZE = reg("thanos.mind_gaze");
   public static final RegistryObject<SoundEvent> MIND_PULSE = reg("thanos.mind_pulse");
   public static final RegistryObject<SoundEvent> SOUL_TETHER = reg("thanos.soul_tether");
   public static final RegistryObject<SoundEvent> SOUL_NOVA = reg("thanos.soul_nova");
   public static final RegistryObject<SoundEvent> STONES_FLARE = reg("thanos.stones_flare");
   public static final RegistryObject<SoundEvent> PHASE = reg("thanos.phase");
   public static final RegistryObject<SoundEvent> COMBO_PORTAL_PUNCH = reg("thanos.combo_portal_punch");
   public static final RegistryObject<SoundEvent> COMBO_TIME_CRUSH = reg("thanos.combo_time_crush");
   public static final RegistryObject<SoundEvent> COMBO_SOUL_MIRAGE = reg("thanos.combo_soul_mirage");
   public static final RegistryObject<SoundEvent> COMBO_MIND_STEP = reg("thanos.combo_mind_step");
   public static final RegistryObject<SoundEvent> COMBO_TRINITY = reg("thanos.combo_trinity");
   public static final RegistryObject<SoundEvent> COMBO_ETERNITY = reg("thanos.combo_eternity");
   public static final RegistryObject<SoundEvent> WORLD_STOP = reg("thanos.world_stop");
   public static final RegistryObject<SoundEvent> GAUNTLET_RAISE = reg("thanos.gauntlet_raise");
   public static final RegistryObject<SoundEvent> STONE_LIGHT = reg("thanos.stone_light");
   public static final RegistryObject<SoundEvent> FINGERS = reg("thanos.fingers");
   public static final RegistryObject<SoundEvent> SNAP_CLICK = reg("thanos.snap_click");
   public static final RegistryObject<SoundEvent> DISSOLVE = reg("thanos.dissolve");
   public static final RegistryObject<SoundEvent> VOID = reg("thanos.void");
   public static final RegistryObject<SoundEvent> OD_LAGNA_ARRIVE = reg("thanos.od_lagna_arrive");
   public static final RegistryObject<SoundEvent> OD_LAGNA_LINE = reg("thanos.od_lagna_line");
   public static final RegistryObject<SoundEvent> RESTORE = reg("thanos.restore");
   public static final RegistryObject<SoundEvent> VICTORY = reg("thanos.victory");
   public static final RegistryObject<SoundEvent> MUSIC_BATTLE = reg("thanos.music_battle");
   public static final RegistryObject<SoundEvent> MUSIC_FINAL = reg("thanos.music_final");
   public static final RegistryObject<SoundEvent> COUNTDOWN = reg("thanos.countdown");

   private ThanosSounds() {
   }

   /** Class-load hook from the mod constructor: the holders above register on first touch. */
   public static void init() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return ViltrumiteCore.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("viltrumitecore", name)));
   }

   /** The combo's own cue (both or all three stones in one sound), or null for a single-stone move. */
   @Nullable
   public static RegistryObject<SoundEvent> combo(ThanosMove move) {
      return switch (move) {
         case COMBO_PORTAL_PUNCH -> COMBO_PORTAL_PUNCH;
         case COMBO_TIME_CRUSH -> COMBO_TIME_CRUSH;
         case COMBO_SOUL_MIRAGE -> COMBO_SOUL_MIRAGE;
         case COMBO_MIND_STEP -> COMBO_MIND_STEP;
         case COMBO_TRINITY -> COMBO_TRINITY;
         case COMBO_ETERNITY -> COMBO_ETERNITY;
         default -> null;
      };
   }

   /**
    * Ticks before the active phase at which a combo cue starts, so its strike in the audio
    * (tools/sfx/thanos.py) lands with the hit: cue strike time minus the active tick of the hit
    * (portal fist 0.62 s / active 6, crush 1.3 s / 12, images 0.5 s / 0, blink 0.6 s / 4, first
    * trinity blast 1.55 s / 0, eternity nova 1.8 s / 20). Clamped to the telegraph start.
    */
   public static int comboLead(ThanosMove move) {
      return switch (move) {
         case COMBO_PORTAL_PUNCH -> 6;
         case COMBO_TIME_CRUSH -> 14;
         case COMBO_SOUL_MIRAGE -> 10;
         case COMBO_MIND_STEP -> 8;
         case COMBO_TRINITY -> 31;
         case COMBO_ETERNITY -> 16;
         default -> 0;
      };
   }

   /** Positional one-shot heard within {@code range} blocks regardless of the volume (a titan is loud). */
   public static void play(ServerLevel level, Vec3 at, RegistryObject<SoundEvent> sound, float volume, float pitch, double range) {
      Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound.get());
      level.getServer().getPlayerList().broadcast(null, at.x, at.y, at.z, Math.max(range, 16.0 * Math.max(volume, 1.0F)), level.dimension(),
         new ClientboundSoundPacket(holder, SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, level.getRandom().nextLong()));
   }
}
