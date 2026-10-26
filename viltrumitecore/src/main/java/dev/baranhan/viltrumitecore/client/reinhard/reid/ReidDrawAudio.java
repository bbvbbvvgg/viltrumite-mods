package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * REINHARD OVERHAUL item 15: the sound of the Reid draw, started by each client on the scene clock
 * of {@link ReidDrawScene} (the same draw tick that poses the arms and lights the column), so the
 * sound follows the phases exactly and never doubles: one cue list per draw (keyed by the server
 * start time, so the START refreshes and the RELEASE packet change nothing), every cue started at
 * most once and at most {@link #LATE_TICKS} late (a player who walks into range mid-draw hears what
 * is still ahead, not a pile of late starts). The cues (tools/sfx/reinhard_draw.py renders them on
 * the same ticks): the low hum rising from the first motion to the release, the koiguchi pushed open
 * at {@link dev.baranhan.viltrumitecore.client.reinhard.ReidDrawChoreo#KOIGUCHI}, the long metallic
 * scrape shaped on the extraction speed whose loud exit lands on the release, the air vibrating
 * under the strain, and the final accent at {@link ReinhardRules#REID_RELEASE_TICK} with its natural
 * decay. An aborted draw (death, ability off, logout) fades its cues out over
 * {@link ReidDrawScene#ABORT_FADE_TICKS}; a level change stops them at once. Each voice follows the
 * caster (the packet centre when this client does not track him). The server plays nothing to the
 * players who see the scene (only the distant echo to the others). Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidDrawAudio {
   /** One cue: its sound event id (under {@code viltrumitecore:reinhard.}), its draw tick and its volume. */
   public record Cue(String name, int start, float volume, Supplier<SoundEvent> event) {
   }

   public static final List<Cue> CUES = List.of(
      new Cue("reid_draw_hum", 6, 1.0F, () -> ReinhardSounds.REID_DRAW_HUM.get()),
      new Cue("reid_draw_koiguchi", 33, 1.0F, () -> ReinhardSounds.REID_DRAW_KOIGUCHI.get()),
      new Cue("reid_draw_scrape", 36, 1.0F, () -> ReinhardSounds.REID_DRAW_SCRAPE.get()),
      new Cue("reid_draw_air", 56, 1.0F, () -> ReinhardSounds.REID_DRAW_AIR.get()),
      new Cue("reid_draw_release", ReinhardRules.REID_RELEASE_TICK, 1.0F, () -> ReinhardSounds.REID_DRAW_RELEASE.get()));
   /** A cue is never started more than this many ticks after its draw tick. */
   public static final int LATE_TICKS = 2;
   /** Height of the voices on the caster (fraction of his height): the scabbard at the hip, the chest. */
   public static final float HEIGHT = 0.6F;
   /** Draw state per caster: the start time of the draw and which cues were already due. */
   private static final Map<Integer, Playing> PLAYING = new HashMap<>();
   /** Every voice still sounding, by caster; a voice outlives its scene (the release decays past the tail). */
   private static final Map<Integer, List<Voice>> VOICES = new HashMap<>();
   private static ClientLevel lastLevel;

   private ReidDrawAudio() {
   }

   /** Pure: the cues due at draw tick d, each once: marks them, returns those still within {@link #LATE_TICKS}. */
   public static List<Cue> due(long d, boolean[] started) {
      List<Cue> out = new ArrayList<>();
      for (int i = 0; i < CUES.size(); i++) {
         Cue cue = CUES.get(i);
         if (!started[i] && d >= cue.start()) {
            started[i] = true;
            if (d <= cue.start() + LATE_TICKS) {
               out.add(cue);
            }
         }
      }

      return out;
   }

   /** Pure: the volume factor of a fading voice, from 1 at the abort down to 0 after {@link ReidDrawScene#ABORT_FADE_TICKS}. */
   public static float fadeOut(int ticksSinceAbort) {
      return Math.max(0.0F, Math.min(1.0F, 1.0F - (float)ticksSinceAbort / ReidDrawScene.ABORT_FADE_TICKS));
   }

   /** Pure: a new cue list for the draw that started at startGameTime. */
   static boolean[] fresh() {
      return new boolean[CUES.size()];
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         // Logout or dimension change: nothing of the old level keeps sounding. Its scenes are dropped by
         // ReidDrawSceneVfx on this same tick; nothing starts before the next one.
         lastLevel = level;
         VOICES.values().forEach(voices -> voices.forEach(Voice::end));
         VOICES.clear();
         PLAYING.clear();
         return;
      }

      if (level == null || client.player == null || client.isPaused()) {
         return;
      }

      long now = level.getGameTime();
      // A voice that played to its end is never stopped explicitly: ask the sound engine.
      VOICES.values().forEach(voices -> voices.removeIf(voice -> voice.isStopped() || !client.getSoundManager().isActive(voice)));
      VOICES.values().removeIf(List::isEmpty);
      Set<Integer> seen = new HashSet<>();
      for (ReidDrawScene.Active scene : ReidDrawScene.all()) {
         int id = scene.casterId();
         seen.add(id);
         Playing playing = PLAYING.get(id);
         if (playing == null || playing.start != scene.startGameTime()) {
            // A newer draw of the same caster: what is left of the old one makes room.
            fade(id, 0);
            playing = new Playing(scene.startGameTime());
            PLAYING.put(id, playing);
         }

         if (scene.aborted()) {
            if (!playing.faded) {
               playing.faded = true;
               fade(id, (int)Math.max(0L, now - scene.endedAt()));
            }

            continue;
         }

         List<Cue> cues = due(now - scene.startGameTime(), playing.started);
         if (cues.isEmpty() || !ReidDrawSceneVfx.inRange(client, scene)) {
            continue;
         }

         for (Cue cue : cues) {
            Voice voice = new Voice(cue, id, scene.center());
            VOICES.computeIfAbsent(id, key -> new ArrayList<>()).add(voice);
            client.getSoundManager().play(voice);
         }
      }

      // A scene past its tail is gone: its voices end by themselves.
      PLAYING.keySet().retainAll(seen);
   }

   /** Fades every voice of a caster out, already ticksSinceAbort into the fade. */
   private static void fade(int casterId, int ticksSinceAbort) {
      List<Voice> voices = VOICES.get(casterId);
      if (voices != null) {
         voices.forEach(voice -> voice.fadeFrom(ticksSinceAbort));
      }
   }

   private static final class Playing {
      final long start;
      final boolean[] started = fresh();
      boolean faded;

      Playing(long start) {
         this.start = start;
      }
   }

   /** One cue sounding: follows the caster, fades on an abort. */
   static final class Voice extends AbstractTickableSoundInstance {
      private final int casterId;
      private final float base;
      private Vec3 at;
      /** Ticks into the abort fade, -1 while not fading. */
      private int fading = -1;

      Voice(Cue cue, int casterId, Vec3 fallback) {
         super(cue.event().get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.casterId = casterId;
         this.base = cue.volume();
         this.volume = cue.volume();
         this.looping = false;
         this.delay = 0;
         this.at = fallback;
         this.follow();
      }

      void fadeFrom(int ticksSinceAbort) {
         if (this.fading < 0) {
            this.fading = ticksSinceAbort;
            this.volume = this.base * fadeOut(this.fading);
         }
      }

      void end() {
         this.stop();
      }

      private void follow() {
         ClientLevel level = Minecraft.getInstance().level;
         Entity caster = level == null ? null : level.getEntity(this.casterId);
         if (caster != null && !caster.isRemoved()) {
            this.at = caster.position().add(0.0, caster.getBbHeight() * HEIGHT, 0.0);
         }

         this.x = this.at.x;
         this.y = this.at.y;
         this.z = this.at.z;
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         if (this.fading >= 0) {
            this.fading++;
            float k = fadeOut(this.fading);
            if (k <= 0.0F) {
               this.stop();
               return;
            }

            this.volume = this.base * k;
         }

         this.follow();
      }
   }
}
