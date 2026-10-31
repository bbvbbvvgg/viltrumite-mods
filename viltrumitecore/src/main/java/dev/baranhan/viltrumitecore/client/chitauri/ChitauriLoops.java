package dev.baranhan.viltrumitecore.client.chitauri;

import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriChariot;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.worldevent.chitauri.ChitauriSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The continuous sounds of the invasion, all client side (the server only sends one-shots): the portal hum
 * (heard across the whole city, no distance roll-off, loudness from the opening and the listener's distance),
 * the engine whine of the nearest chariots (volume and pitch from speed, a pass-by whoosh at the closest
 * approach) and the deep breathing rumble of every Leviathan in range. Each loop fades toward a target that
 * is re-read every tick and stops itself once the target stays at zero, so a stop, an emergency end, a level
 * change or a logout can never leave a hum hanging; a loop the sound engine dropped on its own is replaced
 * after a short pause while it is still wanted (the HulkbusterLoops pattern).
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ChitauriLoops {
   public static final int MAX_ENGINES = 4;
   public static final double ENGINE_RANGE = 56.0;
   public static final double LEVIATHAN_RANGE = 150.0;
   public static final double FLYBY_RANGE = 14.0;
   public static final float FADE_UP = 0.06F;
   public static final float FADE_DOWN = 0.09F;
   static final int STALE_TICKS = 10;
   static final int RETRY_TICKS = 20;

   private static final Map<Integer, Loop> ENGINES = new HashMap<>();
   private static final Map<Integer, Loop> LEVIATHANS = new HashMap<>();
   private static final Map<Integer, Integer> RETRY_AT = new HashMap<>();
   private static final Map<Integer, Double> LAST_DIST = new HashMap<>();
   /** Distance rank (0 = nearest) of the chariots in engine range this tick. */
   private static final Map<Integer, Integer> RANK = new HashMap<>();
   private static final Map<Integer, Integer> FLYBY_AT = new HashMap<>();
   private static Loop portal;
   private static int portalRetry;
   private static int lastFlyby = -100;
   private static ClientLevel lastLevel;
   private static int clock;

   private ChitauriLoops() {
   }

   /** Pure: portal hum loudness from the opening (0..1) and the listener's distance to the centre. */
   public static float portalVolume(float open, double distance) {
      if (open <= 0.01F) {
         return 0.0F;
      }
      float near = (float)Mth.clamp(1.0 - (distance - 40.0) / 420.0, 0.18, 1.0);
      return Mth.clamp(open * near * 0.85F, 0.0F, 1.0F);
   }

   /** Pure: engine loudness from speed (blocks per tick) and the dying flag. */
   public static float engineVolume(double speed, boolean dying) {
      float s = (float)Mth.clamp(speed / 0.9, 0.0, 1.0);
      return dying ? 0.55F + 0.25F * s : 0.3F + 0.55F * s;
   }

   /** Pure: engine pitch; a dying chariot whines lower and wobbles. */
   public static float enginePitch(double speed, boolean dying, int tick) {
      float s = (float)Mth.clamp(speed / 0.9, 0.0, 1.0);
      float pitch = 0.8F + 0.4F * s;
      return dying ? pitch * 0.82F + 0.06F * Mth.sin(tick * 0.9F) : pitch;
   }

   /** Pure: Leviathan rumble loudness by state (falling keeps roaring, the corpse goes quiet). */
   public static float leviathanVolume(byte state) {
      return switch (state) {
         case ChitauriLeviathan.ALIVE -> 1.0F;
         case ChitauriLeviathan.FALLING -> 0.9F;
         default -> 0.0F;
      };
   }

   /** Pure: one fade step. */
   public static float approach(float current, float target) {
      return target > current ? Math.min(target, current + FADE_UP) : Math.max(target, current - FADE_DOWN);
   }

   /**
    * Pure: a closest-approach whoosh fires when a fast chariot inside the range stops getting closer.
    * {@code before} is the distance one tick ago (negative when unknown).
    */
   public static boolean flyby(double before, double now, double speed) {
      return before >= 0.0 && now <= FLYBY_RANGE && speed > 0.45 && now > before + 0.02 && before < FLYBY_RANGE;
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         clear();
      }
      if (mc.level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      clock++;
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      prune(mc, ENGINES);
      prune(mc, LEVIATHANS);
      RETRY_AT.values().removeIf(at -> at <= clock);
      portal(mc, eye);
      engines(mc, eye);
      leviathans(mc, eye);
   }

   private static void prune(Minecraft mc, Map<Integer, Loop> loops) {
      Iterator<Map.Entry<Integer, Loop>> it = loops.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Loop> e = it.next();
         Loop loop = e.getValue();
         if (loop.isStopped()) {
            it.remove();
         } else if (clock - loop.heartbeat > STALE_TICKS) {
            loop.end();
            mc.getSoundManager().stop(loop);
            it.remove();
            RETRY_AT.put(e.getKey(), clock + RETRY_TICKS);
         }
      }
   }

   private static void portal(Minecraft mc, Vec3 eye) {
      if (portal != null && (portal.isStopped() || clock - portal.heartbeat > STALE_TICKS)) {
         if (!portal.isStopped()) {
            portal.end();
            mc.getSoundManager().stop(portal);
            portalRetry = clock + RETRY_TICKS;
         }
         portal = null;
      }
      if (portal == null && clock >= portalRetry && portalTarget(eye) > 0.0F) {
         portal = new Loop(ChitauriSounds.PORTAL_LOOP.get(), SoundSource.AMBIENT, null, true) {
            @Override
            Target target() {
               Vec3 at = ChitauriPortalClient.centre();
               this.x = at.x;
               this.y = at.y;
               this.z = at.z;
               float open = ChitauriPortalClient.open(0.0F);
               return new Target(portalTarget(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()), 0.85F + 0.2F * open);
            }
         };
         mc.getSoundManager().play(portal);
      }
   }

   private static float portalTarget(Vec3 eye) {
      if (!ChitauriPortalClient.active() && ChitauriPortalClient.open(0.0F) <= 0.0F) {
         return 0.0F;
      }
      return portalVolume(ChitauriPortalClient.open(0.0F), eye.distanceTo(ChitauriPortalClient.centre()));
   }

   private static void engines(Minecraft mc, Vec3 eye) {
      AABB box = new AABB(eye, eye).inflate(ENGINE_RANGE);
      List<ChitauriChariot> near = new ArrayList<>(mc.level.getEntitiesOfClass(ChitauriChariot.class, box, c -> c.isAlive() || c.dying()));
      near.sort(Comparator.comparingDouble(c -> c.distanceToSqr(eye)));
      RANK.clear();
      for (int i = 0; i < near.size(); i++) {
         RANK.put(near.get(i).getId(), i);
      }
      for (ChitauriChariot chariot : near) {
         int id = chariot.getId();
         double d = Math.sqrt(chariot.distanceToSqr(eye));
         double speed = chariot.getDeltaMovement().length();
         Double before = LAST_DIST.put(id, d);
         if (flyby(before == null ? -1.0 : before, d, speed) && clock - lastFlyby > 12 && clock - FLYBY_AT.getOrDefault(id, -1000) > 60) {
            lastFlyby = clock;
            FLYBY_AT.put(id, clock);
            mc.getSoundManager().play(new SimpleSoundInstance(ChitauriSounds.CHARIOT_FLYBY.get(), SoundSource.HOSTILE, 1.0F,
               0.9F + (float)Math.min(0.25, speed * 0.15), SoundInstance.createUnseededRandom(), chariot.getX(), chariot.getY() + 0.6, chariot.getZ()));
         }
         if (startEngine(RANK.get(id), ENGINES.size()) && !ENGINES.containsKey(id) && !RETRY_AT.containsKey(id)) {
            Loop loop = new Loop(ChitauriSounds.CHARIOT_ENGINE.get(), SoundSource.HOSTILE, chariot, false) {
               @Override
               Target target() {
                  ChitauriChariot c = (ChitauriChariot)this.entity;
                  if (c.isRemoved() || !wantedEngine(c)) {
                     return Target.OFF;
                  }
                  double v = c.getDeltaMovement().length();
                  return new Target(engineVolume(v, c.dying()), enginePitch(v, c.dying(), c.tickCount));
               }
            };
            ENGINES.put(id, loop);
            mc.getSoundManager().play(loop);
         }
      }
      if (LAST_DIST.size() > 64) {
         LAST_DIST.keySet().removeIf(id -> mc.level.getEntity(id) == null);
         FLYBY_AT.keySet().removeIf(id -> mc.level.getEntity(id) == null);
      }
   }

   private static boolean wantedEngine(ChitauriChariot chariot) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      }
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      double d = chariot.distanceToSqr(eye);
      return d <= ENGINE_RANGE * ENGINE_RANGE && keepEngine(RANK.getOrDefault(chariot.getId(), Integer.MAX_VALUE));
   }

   /**
    * Pure: a new engine loop starts for one of the {@link #MAX_ENGINES} nearest chariots while fewer than
    * MAX_ENGINES + 1 loops are tracked (the spare slot lets a newcomer fade in while a farther one fades out).
    */
   public static boolean startEngine(@Nullable Integer rank, int tracked) {
      return rank != null && rank < MAX_ENGINES && tracked < MAX_ENGINES + 1;
   }

   /**
    * Pure: a running engine loop keeps playing while its chariot ranks inside MAX_ENGINES + 1 (one rank of
    * hysteresis): chariots swapping places at the edge of the set no longer restart their loops every few ticks.
    */
   public static boolean keepEngine(int rank) {
      return rank < MAX_ENGINES + 1;
   }

   private static void leviathans(Minecraft mc, Vec3 eye) {
      AABB box = new AABB(eye, eye).inflate(LEVIATHAN_RANGE);
      for (ChitauriLeviathan leviathan : mc.level.getEntitiesOfClass(ChitauriLeviathan.class, box, l -> !l.isRemoved())) {
         int id = leviathan.getId();
         if (!LEVIATHANS.containsKey(id) && !RETRY_AT.containsKey(id) && leviathanVolume(leviathan.state()) > 0.0F) {
            Loop loop = new Loop(ChitauriSounds.LEVIATHAN_LOOP.get(), SoundSource.HOSTILE, leviathan, false) {
               @Override
               Target target() {
                  ChitauriLeviathan l = (ChitauriLeviathan)this.entity;
                  if (l.isRemoved()) {
                     return Target.OFF;
                  }
                  Vec3 head = l.position().add(0.0, ChitauriLeviathan.CENTRE_Y, 0.0);
                  this.x = head.x;
                  this.y = head.y;
                  this.z = head.z;
                  double v = l.getDeltaMovement().length();
                  float pitch = l.state() == ChitauriLeviathan.FALLING ? 0.72F : 0.85F + (float)Math.min(0.15, v * 0.25);
                  return new Target(leviathanVolume(l.state()), pitch);
               }
            };
            LEVIATHANS.put(id, loop);
            mc.getSoundManager().play(loop);
         }
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   static void clear() {
      Minecraft mc = Minecraft.getInstance();
      List<Loop> all = new ArrayList<>(ENGINES.values());
      all.addAll(LEVIATHANS.values());
      if (portal != null) {
         all.add(portal);
      }
      for (Loop loop : all) {
         loop.end();
         if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(loop);
         }
      }
      ENGINES.clear();
      LEVIATHANS.clear();
      RETRY_AT.clear();
      RANK.clear();
      LAST_DIST.clear();
      FLYBY_AT.clear();
      portal = null;
      portalRetry = 0;
   }

   /** Loops currently tracked (tests and the debug HUD). */
   public static int playing() {
      return ENGINES.size() + LEVIATHANS.size() + (portal != null ? 1 : 0);
   }

   record Target(float volume, float pitch) {
      static final Target OFF = new Target(0.0F, 1.0F);
   }

   abstract static class Loop extends AbstractTickableSoundInstance {
      final Entity entity;
      private float level;
      private int silent;
      int heartbeat;

      Loop(SoundEvent event, SoundSource source, Entity entity, boolean global) {
         super(event, source, SoundInstance.createUnseededRandom());
         this.entity = entity;
         this.heartbeat = clock;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         if (global) {
            this.attenuation = SoundInstance.Attenuation.NONE;
            Vec3 at = ChitauriPortalClient.centre();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
         } else if (entity != null) {
            this.x = entity.getX();
            this.y = entity.getY() + 0.6;
            this.z = entity.getZ();
         }
      }

      abstract Target target();

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
         Target t = this.target();
         this.level = approach(this.level, t.volume());
         this.silent = t.volume() <= 0.0F && this.level <= 0.0F ? this.silent + 1 : 0;
         if (this.silent > 2) {
            this.stop();
            return;
         }
         // never exactly 0: the engine drops a channel whose volume reaches 0 (ReidDrawnHum note)
         this.volume = Math.max(0.001F, this.level);
         this.pitch += (t.pitch() - this.pitch) * 0.25F;
         if (this.entity != null && !(this.entity instanceof ChitauriLeviathan)) {
            this.x = this.entity.getX();
            this.y = this.entity.getY() + 0.6;
            this.z = this.entity.getZ();
         }
      }
   }
}
