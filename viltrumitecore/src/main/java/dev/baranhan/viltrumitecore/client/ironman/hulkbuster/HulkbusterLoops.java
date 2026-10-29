package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.combat.MovePhase;
import dev.baranhan.viltrumitecore.hero.ironman.IronManHulkbusterSounds;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkMove;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterLayer;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterMotion;
import dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.HulkbusterRules;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Hulkbuster client loops (rework spec §10, H6): servo hum while moving (volume and pitch from speed),
 * thrusters (jet charge, rise, hover, dash, ram, Jet Ram), the elbow / repulsor charge, and the sparks
 * of a damaged suit. One instance per player and kind; each instance re-reads its target from the
 * synced snapshot every tick, fades to it, and stops itself once the target stays at zero (a cancel,
 * death, suit exit, hero change, relog or level change all drop the target, so no loop can hang).
 * A loop the sound engine dropped on its own (full channel pool, /stopsound) is noticed by a missing
 * heartbeat and replaced while still wanted, the lesson of ReidDrawnHum.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class HulkbusterLoops {
   /** Beyond this distance no loop starts (the events attenuate at 16 blocks). */
   public static final double START_RANGE = 32.0;
   public static final int STALE_TICKS = 10;
   public static final int RETRY_TICKS = 20;
   /** Fade per tick toward the target (volume units). */
   public static final float FADE_UP = 0.08F;
   public static final float FADE_DOWN = 0.12F;

   public enum Kind {
      SERVO(() -> IronManHulkbusterSounds.SERVO_LOOP.get()),
      THRUSTER(() -> IronManHulkbusterSounds.THRUSTER_LOOP.get()),
      CHARGE(() -> IronManHulkbusterSounds.CHARGE_LOOP.get()),
      SPARK(() -> IronManHulkbusterSounds.SPARK_LOOP.get());

      /** Lazy: unit tests use the targets without a sound registry. */
      final Supplier<SoundEvent> event;

      Kind(Supplier<SoundEvent> event) {
         this.event = event;
      }
   }

   /** Target volume and pitch of one loop; volume 0 means "not wanted". */
   public record Target(float volume, float pitch) {
      public static final Target OFF = new Target(0.0F, 1.0F);
   }

   /** The facts a target is computed from (pure, testable without a client). */
   public record Facts(HulkbusterLayer.Phase phase, @Nullable HulkMove move, MovePhase movePhase, HulkbusterMotion.State motion,
         int motionTicks, int chargeLevel, int damageStage, double speed, boolean alive) {
   }

   private static final Map<Long, Loop> PLAYING = new HashMap<>();
   private static final Map<Long, Integer> RETRY_AT = new HashMap<>();
   private static ClientLevel lastLevel;
   private static int clock;

   private HulkbusterLoops() {
   }

   /** Pure: the target of one loop kind. */
   public static Target target(Kind kind, @Nullable Facts f) {
      if (f == null || !f.alive() || !HulkbusterView.big(f.phase())) {
         return Target.OFF;
      }

      return switch (kind) {
         case SERVO -> {
            float s = (float)Math.min(1.0, f.speed() / 0.28);
            boolean acting = f.move() != null;
            float v = Math.max(s, acting ? 0.55F : 0.0F);
            yield v < 0.04F ? new Target(0.06F, 0.85F) : new Target(0.08F + 0.32F * v, 0.85F + 0.3F * v);
         }
         case THRUSTER -> {
            if (f.move() == HulkMove.JET_RAM && f.movePhase() != MovePhase.FINISH) {
               yield new Target(1.0F, f.movePhase() == MovePhase.MAIN ? 1.25F : 0.9F);
            }

            yield switch (f.motion()) {
               case JET_CHARGE -> {
                  float c = Math.min(1.0F, f.motionTicks() / (float)HulkbusterRules.JET_CHARGE_MAX);
                  yield new Target(0.25F + 0.45F * c, 0.7F + 0.35F * c);
               }
               case JET_RISE -> new Target(0.95F, 1.2F);
               case HOVER -> new Target(0.75F, 1.0F);
               case DASH -> new Target(0.8F, 1.15F);
               case RAM -> new Target(0.6F, 0.95F);
               case DIVE -> new Target(0.7F, 1.35F);
               default -> Target.OFF;
            };
         }
         case CHARGE -> {
            boolean winding = f.movePhase() == MovePhase.INTRO
               && (f.move() == HulkMove.CHARGED || f.move() == HulkMove.HEAVY_REPULSORS);
            if (!winding) {
               yield Target.OFF;
            }

            float c = Math.min(1.0F, Math.max(0, f.chargeLevel()) / 3.0F);
            yield new Target(0.35F + 0.45F * c, 0.8F + 0.45F * c);
         }
         case SPARK -> f.damageStage() <= 0 ? Target.OFF : new Target(0.12F + 0.14F * Math.min(2, f.damageStage()), 1.0F);
      };
   }

   /** Pure: one fade step toward {@code target}. */
   public static float approach(float current, float target) {
      return target > current ? Math.min(target, current + FADE_UP) : Math.max(target, current - FADE_DOWN);
   }

   @Nullable
   static Facts facts(Player player) {
      HeroPublicSnapshot snapshot = IronManView.of(player);
      if (snapshot == null) {
         return null;
      }

      double dx = player.getX() - player.xo;
      double dz = player.getZ() - player.zo;
      return new Facts(HulkbusterView.phase(snapshot), HulkbusterView.move(snapshot), HulkbusterView.movePhase(snapshot),
         HulkbusterView.motion(snapshot), HulkbusterView.motionTicks(snapshot), HulkbusterView.chargeLevel(snapshot),
         HulkbusterView.damageStage(snapshot), Math.sqrt(dx * dx + dz * dz), player.isAlive() && !player.isRemoved());
   }

   private static long key(int player, Kind kind) {
      return ((long)player << 3) | kind.ordinal();
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (client.level != lastLevel) {
         lastLevel = client.level;
         clear();
      }

      if (client.level == null || client.player == null || client.isPaused()) {
         return;
      }

      clock++;
      Iterator<Map.Entry<Long, Loop>> it = PLAYING.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Long, Loop> entry = it.next();
         Loop loop = entry.getValue();
         if (loop.isStopped()) {
            it.remove();
         } else if (clock - loop.heartbeat > STALE_TICKS) {
            loop.end();
            client.getSoundManager().stop(loop);
            it.remove();
            RETRY_AT.put(entry.getKey(), clock + RETRY_TICKS);
         }
      }

      RETRY_AT.values().removeIf(at -> at <= clock);
      for (Player player : client.level.players()) {
         if (player.distanceTo(client.player) > START_RANGE) {
            continue;
         }

         Facts facts = facts(player);
         if (facts == null) {
            continue;
         }

         for (Kind kind : Kind.values()) {
            long key = key(player.getId(), kind);
            if (!PLAYING.containsKey(key) && !RETRY_AT.containsKey(key) && target(kind, facts).volume() > 0.0F) {
               Loop loop = new Loop(player, kind);
               PLAYING.put(key, loop);
               client.getSoundManager().play(loop);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   private static void clear() {
      Minecraft client = Minecraft.getInstance();
      for (Loop loop : PLAYING.values()) {
         loop.end();
         if (client.getSoundManager() != null) {
            client.getSoundManager().stop(loop);
         }
      }

      PLAYING.clear();
      RETRY_AT.clear();
   }

   /** Loops currently tracked (tests and the debug HUD). */
   public static int playing() {
      return PLAYING.size();
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final Player player;
      private final Kind kind;
      private float level;
      private int silent;
      int heartbeat;

      Loop(Player player, Kind kind) {
         super(kind.event.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.player = player;
         this.kind = kind;
         this.heartbeat = clock;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.follow();
      }

      private void follow() {
         this.x = this.player.getX();
         this.y = this.player.getY() + this.player.getBbHeight() * (this.kind == Kind.THRUSTER ? 0.55 : 0.4);
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
         Target target = target(this.kind, facts(this.player));
         this.level = approach(this.level, target.volume());
         this.silent = target.volume() <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 2 || this.player.isRemoved()) {
            this.stop();
            return;
         }

         // Never 0: the engine stops a channel whose volume reaches 0 (ReidDrawnHum note).
         this.volume = Math.max(0.001F, this.level);
         this.pitch += (target.pitch() - this.pitch) * 0.25F;
         this.follow();
      }
   }
}
