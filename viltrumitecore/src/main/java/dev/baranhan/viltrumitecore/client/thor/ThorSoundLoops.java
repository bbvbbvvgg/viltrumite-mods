package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import dev.baranhan.viltrumitecore.hero.thor.ThorHands;
import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirEntity;
import dev.baranhan.viltrumitecore.hero.thor.hammer.MjolnirPhase;
import dev.baranhan.viltrumiteflight.util.FlightState;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * R5 (spec §8): Thor's client loops, the only Thor code that plays client sound instances. Per Thor player:
 * the electric hum ({@link ThorLoopRules.Kind#ELECTRIC}), the hammer spin and the flight wind; one more instance
 * follows each flying world hammer ({@link #hammerFlight}). Rules and lifecycle are pure ({@link ThorLoopRules});
 * every loop fades in, fades out when its condition ends and is dropped on a level change.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ThorSoundLoops {
   private static final ThorLoopRules.Loops<Loop> LOOPS = new ThorLoopRules.Loops<>();
   private static final Map<Integer, Player> PLAYERS = new HashMap<>();
   private static ClientLevel lastLevel;
   private static int clock;

   private ThorSoundLoops() {
   }

   static ThorLoopRules.View view(Player player) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      FlightState flight = player instanceof ViltrumiteFlightPlayer flying ? flying.getFlightState() : null;
      if (snapshot == null) {
         return new ThorLoopRules.View(false, player.isRemoved(), null, false, flight, null, null, 0.0);
      }

      int flags = snapshot.heroFlags();
      return new ThorLoopRules.View(true, player.isRemoved(), ThorFlags.mode(flags), ThorHands.holds(ThorFlags.hammer(flags), ThorHands.free(player)),
         flight, ThorView.move(snapshot), ThorView.phase(snapshot), player.getDeltaMovement().length());
   }

   private static final ThorLoopRules.Engine<Loop> ENGINE = new ThorLoopRules.Engine<>() {
      @Override
      public Loop start(long key, int now) {
         Loop loop = new Loop(PLAYERS.get((int)(key >> 4)), ThorLoopRules.Kind.values()[(int)(key & 15)], now);
         Minecraft.getInstance().getSoundManager().play(loop);
         return loop;
      }

      @Override
      public void stop(Loop loop) {
         loop.end();
         SoundManager sounds = Minecraft.getInstance().getSoundManager();
         if (sounds != null) {
            sounds.stop(loop);
         }
      }

      @Override
      public boolean stopped(Loop loop) {
         return loop.isStopped();
      }

      @Override
      public int heartbeat(Loop loop) {
         return loop.heartbeat;
      }
   };

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         // The engine drops every instance on a level change; forget ours too.
         lastLevel = client.level;
         LOOPS.clear(ENGINE);
      }

      if (client.level == null || client.player == null || client.isPaused()) {
         return;
      }

      clock++;
      PLAYERS.clear();
      List<Long> keys = new ArrayList<>();
      for (Player player : client.level.players()) {
         PLAYERS.put(player.getId(), player);
         for (ThorLoopRules.Kind kind : ThorLoopRules.Kind.values()) {
            keys.add(ThorLoopRules.key(player.getId(), kind));
         }
      }

      Player listener = client.player;
      LOOPS.tick(clock, keys, key -> {
         Player player = PLAYERS.get((int)(key >> 4));
         return player != null && player.distanceTo(listener) < ThorLoopRules.START_RANGE
            && ThorLoopRules.wanted(ThorLoopRules.Kind.values()[(int)(key & 15)], view(player));
      }, ENGINE);
   }

   /** Called by MjolnirClient on the hammer's first client tick: its flight hum (was the vanilla elytra). */
   public static void hammerFlight(MjolnirEntity hammer) {
      Minecraft.getInstance().getSoundManager().play(new HammerFlight(hammer));
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final Player player;
      private final ThorLoopRules.Kind kind;
      private int level = 1;
      int heartbeat;

      Loop(Player player, ThorLoopRules.Kind kind, int now) {
         super(ThorSounds.event(kind.cue().layers().get(0).event()), kind.cue().source(), SoundInstance.createUnseededRandom());
         this.player = player;
         this.kind = kind;
         this.heartbeat = now;
         this.looping = true;
         this.delay = 0;
         ThorLoopRules.View view = view(player);
         this.volume = ThorLoopRules.faded(ThorLoopRules.volume(kind, view), this.level);
         this.pitch = ThorLoopRules.pitch(kind, view);
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
         Minecraft client = Minecraft.getInstance();
         ThorLoopRules.View view = view(this.player);
         boolean wanted = this.player.level() == client.level && client.player != null
            && this.player.distanceTo(client.player) < ThorLoopRules.START_RANGE + 8.0 && ThorLoopRules.wanted(this.kind, view);
         this.level = ThorLoopRules.fade(this.level, wanted);
         if (this.level <= 0) {
            this.stop();
            return;
         }

         this.volume = ThorLoopRules.faded(ThorLoopRules.volume(this.kind, view), this.level);
         this.pitch = Mth.lerp(0.2F, this.pitch, ThorLoopRules.pitch(this.kind, view));
         this.follow();
      }
   }

   /**
    * №162 / §5.2: positional hum bound to the real hammer: distance attenuation only, a low far hum turning into
    * a rising whistle as it nears and speeds up (moved from MjolnirClient.FlightSound, now Thor's own loop).
    */
   static final class HammerFlight extends AbstractTickableSoundInstance {
      private final MjolnirEntity hammer;

      HammerFlight(MjolnirEntity hammer) {
         super(ThorSounds.event(ThorCue.LOOP_HAMMER_FLY.layers().get(0).event()), ThorCue.LOOP_HAMMER_FLY.source(), SoundInstance.createUnseededRandom());
         this.hammer = hammer;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.attenuation = SoundInstance.Attenuation.LINEAR;
         this.x = hammer.getX();
         this.y = hammer.getY();
         this.z = hammer.getZ();
      }

      @Override
      public void tick() {
         if (this.hammer.isRemoved()) {
            this.stop();
            return;
         }

         this.x = this.hammer.getX();
         this.y = this.hammer.getY();
         this.z = this.hammer.getZ();
         MjolnirPhase phase = this.hammer.phase();
         boolean flying = phase == MjolnirPhase.THROWN || phase == MjolnirPhase.RETURNING || phase == MjolnirPhase.DEATH_IMPACT
            || phase == MjolnirPhase.WORLD_FALLING;
         float speed = (float)this.hammer.getDeltaMovement().length();
         // Volume above 1 widens the hearing range (LINEAR: 16 blocks per unit); loudness is capped at 1.
         this.volume = flying ? Mth.clamp(speed / 2.0F, 0.2F, 1.0F) * 4.0F * ThorCue.LOOP_HAMMER_FLY.layers().get(0).volume() : 0.0F;
         this.pitch = Mth.clamp(0.6F + speed * 0.15F, 0.6F, 1.5F);
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
