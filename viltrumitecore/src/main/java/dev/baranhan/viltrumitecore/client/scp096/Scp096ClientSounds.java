package dev.baranhan.viltrumitecore.client.scp096;

import dev.baranhan.viltrumitecore.network.packet.Scp096S2CPacket;
import dev.baranhan.viltrumitecore.scp.scp096.Scp096Entity;
import dev.baranhan.viltrumitecore.scp.scp096.Scp096Sounds;
import dev.baranhan.viltrumitecore.scp.scp096.Scp096State;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Everything SCP-096 sounds like, client side (the server sends no sound at all): per entity in range the sobbing
 * while it is calm (SCP:CB 096.ogg), the wailing from the moment its face was seen through the hysteria
 * (096Angered.ogg) and the screaming of the chase (Scream.ogg), all positional; for the local player only, the
 * sting when he saw the face (Triggered.ogg) with a short camera zoom kick, and the chase music while he is hunted
 * (096Chase.ogg, louder as it closes in, never below 60% — the SCP:CB rule) with a slow zoom pulse. With the
 * originals found ({@link Scp096OriginalSounds}) the loops stream continuously; with the vanilla fallbacks the
 * positional sounds are spaced one-shots instead. Every loop fades toward a target re-read each tick and stops
 * itself, so a despawn, a level change or a logout can never leave a sound hanging. Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class Scp096ClientSounds {
   public static final double RANGE = 72.0;
   static final int RETRY_TICKS = 20;
   private static final Map<Integer, Track> TRACKS = new HashMap<>();
   /** Entities hunting the local player (from {@link Scp096S2CPacket}). */
   private static final Set<Integer> HUNTED = new HashSet<>();
   private static final RandomSource RANDOM = RandomSource.create();
   @Nullable
   private static Scp096LoopSound music;
   private static int musicRetry;
   private static float kick;
   private static float kickO;
   private static float pulse;
   @Nullable
   private static ClientLevel lastLevel;
   // QA
   static int stings;
   static float lastMusicTarget;

   private Scp096ClientSounds() {
   }

   private static final class Track {
      final Scp096Entity entity;
      final Scp096LoopSound[] loops = new Scp096LoopSound[3];
      final int[] retry = new int[3];
      int nextShot;
      int seen;

      Track(Scp096Entity entity) {
         this.entity = entity;
      }
   }

   /** Packet handler (main thread). */
   public static void onPacket(byte kind, int entityId) {
      Minecraft mc = Minecraft.getInstance();
      if (kind == Scp096S2CPacket.RELEASED) {
         HUNTED.remove(entityId);
         return;
      }
      HUNTED.add(entityId);
      if (kind == Scp096S2CPacket.TRIGGERED && mc.player != null) {
         stings++;
         kick = 1.0F;
         kickO = 1.0F;
         mc.getSoundManager().play(new SimpleSoundInstance(Scp096Sounds.TRIGGERED.get().getLocation(), SoundSource.HOSTILE, 1.0F, 1.0F,
            SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
      }
   }

   /** Pure: loop index (0 sobbing, 1 wailing, 2 screaming) wanted in a state, -1 for none. */
   static int loopFor(Scp096State s) {
      return switch (s) {
         case SIT, STAND, WANDER, CALMING -> 0;
         case TRIGGERED, HYSTERIA -> 1;
         case CHASE -> 2;
      };
   }

   /** Pure: loudness of a loop: the sobbing swells back in while it calms down. */
   static float loopVolume(int loop, Scp096State s, int stateTicks) {
      if (loop == 0) {
         return s == Scp096State.CALMING ? 0.85F * Mth.clamp(stateTicks / 60.0F, 0.0F, 1.0F) : 0.85F;
      }
      return 1.0F;
   }

   /** Pure: chase music loudness by the distance to the hunter (SCP:CB: min(max(8 - d, 0.6), 1)); far or unseen = 0.6. */
   static float musicVolume(double distance) {
      return (float)Mth.clamp(1.0 - (distance - 12.0) / 80.0, 0.6, 1.0);
   }

   /** Pure: spacing in ticks of the fallback one-shots of a loop. */
   static int shotGap(int loop, RandomSource random) {
      return switch (loop) {
         case 0 -> 70 + random.nextInt(70);
         case 1 -> 38 + random.nextInt(24);
         default -> 26 + random.nextInt(16);
      };
   }

   private static SoundEvent event(int loop) {
      return switch (loop) {
         case 0 -> Scp096Sounds.CRY.get();
         case 1 -> Scp096Sounds.ANGERED.get();
         default -> Scp096Sounds.SCREAM.get();
      };
   }

   private static String original(int loop) {
      return switch (loop) {
         case 0 -> "cry";
         case 1 -> "angered";
         default -> "scream";
      };
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != lastLevel) {
         stopAll();
         lastLevel = level;
      }
      Player player = mc.player;
      if (level == null || player == null || mc.isPaused()) {
         return;
      }
      kickO = kick;
      kick = Math.max(0.0F, kick - 0.045F);
      int now = player.tickCount;
      AABB box = player.getBoundingBox().inflate(RANGE);
      for (Scp096Entity e : level.getEntitiesOfClass(Scp096Entity.class, box, Entity::isAlive)) {
         Track t = TRACKS.computeIfAbsent(e.getId(), id -> new Track(e));
         t.seen = now;
      }
      Iterator<Map.Entry<Integer, Track>> it = TRACKS.entrySet().iterator();
      while (it.hasNext()) {
         Track t = it.next().getValue();
         boolean gone = t.seen != now || t.entity.isRemoved() || !t.entity.isAlive();
         if (gone && quiet(t)) {
            it.remove();
            continue;
         }
         tickTrack(mc, t, gone);
      }
      tickMusic(mc, level, player);
   }

   private static boolean quiet(Track t) {
      for (Scp096LoopSound l : t.loops) {
         if (l != null && !l.isStopped()) {
            l.target = 0.0F;
            return false;
         }
      }
      return true;
   }

   private static void tickTrack(Minecraft mc, Track t, boolean gone) {
      Scp096Entity e = t.entity;
      Scp096State s = e.state();
      int want = gone ? -1 : loopFor(s);
      int stateTicks = e.tickCount - e.clientStateStart;
      for (int i = 0; i < 3; i++) {
         float target = i == want ? loopVolume(i, s, stateTicks) : 0.0F;
         Scp096LoopSound loop = t.loops[i];
         if (!Scp096OriginalSounds.has(original(i))) {
            // vanilla fallback: spaced positional one-shots instead of a continuous loop
            if (i == want && --t.nextShot <= 0) {
               t.nextShot = shotGap(i, RANDOM);
               mc.level.playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.85, e.getZ(), event(i), SoundSource.HOSTILE, target,
                  0.95F + RANDOM.nextFloat() * 0.1F, false);
            }
            continue;
         }
         if (loop != null && !loop.isStopped()) {
            loop.target = target;
         } else if (target > 0.02F && --t.retry[i] <= 0) {
            loop = new Scp096LoopSound(event(i), SoundSource.HOSTILE, e, true, i == 0 ? 0.03F : 0.12F, i == 0 ? 0.05F : 0.08F);
            loop.target = target;
            t.loops[i] = loop;
            t.retry[i] = RETRY_TICKS;
            mc.getSoundManager().play(loop);
         }
      }
   }

   private static void tickMusic(Minecraft mc, ClientLevel level, Player player) {
      float target = 0.0F;
      float pulseTarget = 0.0F;
      Iterator<Integer> it = HUNTED.iterator();
      while (it.hasNext()) {
         Entity ent = level.getEntity(it.next());
         if (ent instanceof Scp096Entity e) {
            Scp096State s = e.state();
            if (!e.isAlive() || s.calm()) {
               it.remove();
               continue;
            }
            if (s == Scp096State.CHASE) {
               double d = e.distanceTo(player);
               target = Math.max(target, musicVolume(d));
               pulseTarget = Math.max(pulseTarget, (float)Mth.clamp(1.0 - d / 48.0, 0.0, 1.0));
            }
         } else {
            // out of tracking range but still coming (the server says so): the music keeps its floor
            target = Math.max(target, 0.6F);
         }
      }
      lastMusicTarget = target;
      pulse += (pulseTarget - pulse) * 0.08F;
      if (music != null && !music.isStopped()) {
         music.target = target;
      } else if (target > 0.02F && --musicRetry <= 0) {
         music = new Scp096LoopSound(Scp096Sounds.CHASE_MUSIC.get(), SoundSource.HOSTILE, null, true, 0.08F, 0.02F);
         music.target = target;
         musicRetry = RETRY_TICKS;
         mc.getMusicManager().stopPlaying();
         mc.getSoundManager().play(music);
      }
      if (target > 0.02F && mc.level.getGameTime() % 40 == 0) {
         mc.getMusicManager().stopPlaying();
      }
   }

   /** The camera: a zoom kick on the sting, a slow pulse while it closes in (FOV effects option respected). */
   @SubscribeEvent
   public static void onFov(ViewportEvent.ComputeFov event) {
      if (!event.usedConfiguredFov()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      float scale = mc.options.fovEffectScale().get().floatValue();
      if (scale <= 0.0F || kick <= 0.0F && pulse <= 0.01F) {
         return;
      }
      float k = Mth.lerp((float)event.getPartialTick(), kickO, kick);
      double seconds = (mc.level == null ? 0L : mc.level.getGameTime()) / 20.0 + event.getPartialTick() / 20.0;
      float zoom = fovZoom(k, pulse, seconds);
      event.setFOV(event.getFOV() * (1.0 - zoom * scale));
   }

   /** Pure: FOV reduction (0..~0.09) from the sting kick (0..1, eased) and the chase pulse weight (7.2 s period, SCP:CB). */
   static float fovZoom(float kick, float pulse, double seconds) {
      float k = kick * kick * (3.0F - 2.0F * kick);
      float wave = 0.5F + 0.5F * (float)Math.sin(seconds * Math.PI * 2.0 / 7.2);
      return 0.07F * k + 0.025F * pulse * wave;
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      stopAll();
      HUNTED.clear();
   }

   static void stopAll() {
      for (Track t : TRACKS.values()) {
         for (Scp096LoopSound l : t.loops) {
            if (l != null) {
               l.halt();
            }
         }
      }
      TRACKS.clear();
      if (music != null) {
         music.halt();
         music = null;
      }
      kick = 0.0F;
      kickO = 0.0F;
      pulse = 0.0F;
   }

   /** QA line: loops alive, hunted ids, music target, stings, originals in use. */
   public static String describe() {
      int loops = 0;
      for (Track t : TRACKS.values()) {
         for (Scp096LoopSound l : t.loops) {
            if (l != null && !l.isStopped()) {
               loops++;
            }
         }
      }
      return "tracks=" + TRACKS.size() + " loops=" + loops + " hunted=" + HUNTED + " music=" + (music != null && !music.isStopped())
         + " musicTarget=" + lastMusicTarget + " stings=" + stings + " originals=" + Scp096OriginalSounds.count();
   }
}
