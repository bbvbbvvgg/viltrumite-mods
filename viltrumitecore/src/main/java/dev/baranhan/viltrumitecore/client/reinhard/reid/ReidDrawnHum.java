package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * REINHARD OVERHAUL, Sword Drawn state: a quiet seamless hum that follows every Reinhard whose
 * Reid is out and settled in the hand (the same rule as the gold aura). One looping instance per
 * player, faded in over a second, faded out quickly on sheathing, removal or a level change.
 * Client only; the server state is the synced REID_DRAWN flag.
 *
 * <p>REINHARD 3b item 7: the sound engine can drop a channel without stopping the instance. World Erasure (N)
 * ducked every sound to 0 and {@code SoundEngine.updateCategoryVolume} stops a channel whose volume reaches 0;
 * {@code /stopsound}, a full channel pool or another mod can do the same. The instance never learnt it (its
 * {@code isStopped()} stayed false), so it stayed "playing" in this table forever: no hum until a level change,
 * not even after sheathing and drawing again. Now every loop reports a heartbeat from {@link Loop#tick}; a loop the
 * engine has not ticked for {@link #STALE_TICKS} unpaused client ticks is dropped and, while Reid is still drawn,
 * a new one fades in (at most one per {@link #RETRY_TICKS}). {@link Loops} keeps exactly one loop per player. The
 * duck itself no longer silences channels to 0 either ({@code SoundDuck#duck}).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ReidDrawnHum {
   public static final float VOLUME = 0.22F;
   public static final int FADE_IN = 20;
   public static final int FADE_OUT_STEP = 3;
   /** Beyond this distance no instance is started (the sound itself attenuates at 16 blocks). */
   public static final double START_RANGE = 24.0;
   /** Unpaused client ticks without a tick from the engine after which a loop counts as dropped. */
   public static final int STALE_TICKS = 10;
   /** Ticks between a dropped loop and its replacement (a sound engine that cannot play is not hammered). */
   public static final int RETRY_TICKS = 20;
   private static final Loops<Loop> LOOPS = new Loops<>();
   /** The players of this tick, by entity id. */
   private static final Map<Integer, Player> PLAYERS = new HashMap<>();
   private static ClientLevel lastLevel;
   /** Unpaused client ticks; the heartbeat clock. */
   private static int clock;

   private ReidDrawnHum() {
   }

   /** Pure: next fade level 0..{@link #FADE_IN}: one step up per tick while drawn, faster down otherwise. */
   public static int fade(int level, boolean drawn) {
      return Math.max(0, Math.min(FADE_IN, drawn ? level + 1 : level - FADE_OUT_STEP));
   }

   /** Pure: loop volume at a fade level. */
   public static float volume(int level) {
      return VOLUME * Math.max(0, Math.min(FADE_IN, level)) / FADE_IN;
   }

   /** Pure: a loop whose last engine tick was at {@code heartbeat} has been dropped by the engine at {@code now}. */
   public static boolean lost(int now, int heartbeat) {
      return now - heartbeat > STALE_TICKS;
   }

   static boolean drawn(Player player) {
      HeroPublicSnapshot snapshot = ReinhardView.of(player);
      return snapshot != null && !player.isRemoved() && ReidAura.active(snapshot.heroFlags());
   }

   /** What {@link Loops} needs from a sound engine; the client adapter is {@link #ENGINE}, tests use a fake. */
   public interface Engine<L> {
      L start(int player, int now);
      void stop(L loop);
      boolean stopped(L loop);
      int heartbeat(L loop);
   }

   /**
    * Pure lifecycle of the per-player loops: at most one loop per player id; a loop we stopped (faded out) is
    * forgotten, a loop the engine dropped is stopped, forgotten and replaced after {@link #RETRY_TICKS} while its
    * player is still wanted (drawn and in range).
    */
   public static final class Loops<L> {
      private final Map<Integer, L> playing = new HashMap<>();
      private final Map<Integer, Integer> retryAt = new HashMap<>();

      public void tick(int now, Iterable<Integer> players, IntPredicate wanted, Engine<L> engine) {
         Iterator<Map.Entry<Integer, L>> it = playing.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<Integer, L> entry = it.next();
            L loop = entry.getValue();
            if (engine.stopped(loop)) {
               it.remove();
            } else if (lost(now, engine.heartbeat(loop))) {
               engine.stop(loop);
               it.remove();
               retryAt.put(entry.getKey(), now + RETRY_TICKS);
            }
         }
         retryAt.values().removeIf(at -> at <= now);
         for (int id : players) {
            if (!playing.containsKey(id) && !retryAt.containsKey(id) && wanted.test(id)) playing.put(id, engine.start(id, now));
         }
      }

      public void clear(Engine<L> engine) {
         playing.values().forEach(engine::stop);
         playing.clear();
         retryAt.clear();
      }

      public int size() {
         return playing.size();
      }

      public boolean playing(int player) {
         return playing.containsKey(player);
      }
   }

   private static final Engine<Loop> ENGINE = new Engine<>() {
      public Loop start(int player, int now) {
         Loop loop = new Loop(PLAYERS.get(player), now);
         Minecraft.getInstance().getSoundManager().play(loop);
         return loop;
      }
      public void stop(Loop loop) {
         loop.end();
         SoundManager sounds = Minecraft.getInstance().getSoundManager();
         if (sounds != null) sounds.stop(loop);
      }
      public boolean stopped(Loop loop) { return loop.isStopped(); }
      public int heartbeat(Loop loop) { return loop.heartbeat; }
   };

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         // The sound engine drops every instance on a level change; forget ours too.
         lastLevel = client.level;
         LOOPS.clear(ENGINE);
      }
      // The engine does not tick sounds while the game is paused: neither does the heartbeat clock.
      if (client.level == null || client.player == null || client.isPaused()) {
         return;
      }
      clock++;
      PLAYERS.clear();
      for (Player player : client.level.players()) PLAYERS.put(player.getId(), player);
      LOOPS.tick(clock, PLAYERS.keySet(), id -> {
         Player player = PLAYERS.get(id);
         return player != null && drawn(player) && player.distanceTo(client.player) < START_RANGE;
      }, ENGINE);
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final Player player;
      private int level = 1;
      /** Clock of the engine's last tick of this instance. */
      int heartbeat;

      Loop(Player player, int now) {
         super(ReinhardSounds.REID_DRAWN_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.player = player;
         this.heartbeat = now;
         this.looping = true;
         this.delay = 0;
         this.volume = ReidDrawnHum.volume(this.level);
         this.follow();
      }

      private void follow() {
         this.x = this.player.getX();
         this.y = this.player.getY() + this.player.getBbHeight() * 0.5;
         this.z = this.player.getZ();
      }

      void end() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         this.heartbeat = clock;
         this.level = ReidDrawnHum.fade(this.level, drawn(this.player));
         if (this.level <= 0) {
            this.stop();
            return;
         }
         this.volume = ReidDrawnHum.volume(this.level);
         this.follow();
      }
   }
}
