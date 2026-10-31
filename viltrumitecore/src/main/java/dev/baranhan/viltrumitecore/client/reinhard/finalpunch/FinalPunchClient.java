package dev.baranhan.viltrumitecore.client.reinhard.finalpunch;

import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchRules;
import dev.baranhan.viltrumitecore.hero.reinhard.finalpunch.FinalPunchTargets;
import dev.baranhan.viltrumitecore.network.packet.ReinhardFinalPunchS2CPacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Client side of THE FINAL PUNCH (Reinhard's secret, Shift + N). The server owns the timeline
 * (FinalPunch, 480 ticks) and sends START / IMPACT / CANCEL to the caster and every tracking
 * player. Each client runs the scene on its own clock from START, re-synchronised by IMPACT:
 * the caster's cinematic camera ({@link FinalPunchCamera}), the body clip
 * ({@code client.reinhard.FinalPunchPose}), the burning fist and the chaos ({@link FinalPunchVfx}),
 * the sounds ({@link FinalPunchAudio}), the screen grade and bars ({@link FinalPunchScreen}) and
 * the knocked-off jaw ({@link FinalPunchJaw}).
 * <p>Everything here is temporary and owned by the scene: a CANCEL, the local player's death,
 * a level change (dimension, world exit), a logout / server stop, a lost caster (watchdog) or
 * the end of the timeline all run {@link #finish}, which stops every sound, releases the
 * ducking, drops the effects and gives the camera and the controls back.
 */
public final class FinalPunchClient {
   /** Caster missing from the level this long (left tracking, removed) ends the scene. */
   static final int WATCHDOG = 40;

   /** One running scene (one per caster). */
   public static final class Scene {
      public final int casterId;
      public final int targetId;
      public final FinalPunchTargets.Jaw jaw;
      public final FinalPunchTargets.Kind kind;
      public final float yaw;
      public final Vec3 jawAt;
      public final Vec3 dir;
      public final long seed;
      final ClientLevel level;
      int ticks;
      int cancelledAt = -1;
      boolean impacted;
      boolean landed;
      Vec3 impactAt;
      int missing;
      Vec3 feet;
      @Nullable
      Vec3 fist;
      Vec3 fistSmooth;
      /** The target's centre when last seen: the camera keeps following a launched body after it is removed. */
      @Nullable
      Vec3 lastTarget;
      long fistFrame = -1;
      final List<Vec3> trail = new ArrayList<>();
      final FinalPunchAudio.Voices audio = new FinalPunchAudio.Voices();
      final FinalPunchVfx.State vfx = new FinalPunchVfx.State();

      Scene(ReinhardFinalPunchS2CPacket p, ClientLevel level, int startTick) {
         this.casterId = p.casterId();
         this.targetId = p.targetId();
         FinalPunchTargets.Jaw[] jaws = FinalPunchTargets.Jaw.values();
         FinalPunchTargets.Kind[] kinds = FinalPunchTargets.Kind.values();
         this.jaw = jaws[Math.max(0, Math.min(jaws.length - 1, p.jaw()))];
         this.kind = kinds[Math.max(0, Math.min(kinds.length - 1, p.kind()))];
         this.yaw = p.yaw();
         this.jawAt = p.at();
         this.dir = p.dir();
         this.seed = ((long) p.casterId() << 32) ^ (p.targetId() & 0xFFFFFFFFL) ^ Float.floatToIntBits(p.yaw());
         this.level = level;
         this.ticks = startTick;
         Vec3 flat = Vec3.directionFromRotation(0, this.yaw);
         Vec3 right = new Vec3(-flat.z, 0, flat.x);
         // The caster's feet, back from the jaw point (FinalPunch.anchor / jawPoint).
         this.feet = this.jawAt.subtract(flat.scale(0.95)).subtract(right.scale(0.13)).subtract(0, headHeight(this.kind, this.jaw), 0);
         this.fistSmooth = FinalPunchFist.estimate(this, startTick);
      }

      /** Scene time (ticks + partial), clamped to the client tail. */
      public double time(float partial) {
         return Math.min(FinalPunchRules.LENGTH + FinalPunchCues.TAIL, this.ticks + (double) partial);
      }

      public boolean cancelled() {
         return this.cancelledAt >= 0;
      }

      /** 1 while the scene runs; a cancel fades every layer out. */
      public float fade(float partial) {
         if (this.cancelledAt < 0) return 1.0F;
         return (float) Math.max(0.0, 1.0 - (this.ticks - this.cancelledAt + partial) / FinalPunchCues.CANCEL_FADE);
      }

      public boolean impacted() {
         return this.impacted;
      }

      public boolean landed() {
         return this.landed;
      }

      public Vec3 feet() {
         return this.feet;
      }

      public boolean isLocalCaster() {
         Minecraft mc = Minecraft.getInstance();
         return mc.player != null && mc.player.getId() == this.casterId;
      }

      @Nullable
      public Entity caster() {
         return this.level.getEntity(this.casterId);
      }

      @Nullable
      public LivingEntity target() {
         return this.level.getEntity(this.targetId) instanceof LivingEntity living && !living.isRemoved() ? living : null;
      }

      /** The fist this frame: the rendered hand when the body was drawn recently, else the clip estimate. */
      public Vec3 fist(float partial) {
         long frame = FinalPunchClient.frame;
         if (this.fist != null && frame - this.fistFrame <= 2) return this.fist;
         return FinalPunchFist.estimate(this, this.time(partial));
      }

      /** A low-passed fist for the camera (the trembling hand must not shake the lens). */
      public Vec3 fistSmooth() {
         return this.fistSmooth;
      }

      public List<Vec3> trail() {
         return this.trail;
      }

      /** The target's centre (0.6 of its height) this frame, or where it was last seen once it is gone after the blow. */
      @Nullable
      public Vec3 targetCentre(float partial) {
         LivingEntity target = this.target();
         if (target != null) return target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0);
         return this.impacted ? this.lastTarget : null;
      }
   }

   private static final Map<Integer, Scene> SCENES = new HashMap<>();
   /** Render frame counter (advanced by the level render), for the fist freshness check. */
   static long frame;

   private FinalPunchClient() {
   }

   /** The target's jaw height above the feet (FinalPunchTargets: golem, Avengers, people). */
   static float headHeight(FinalPunchTargets.Kind kind, FinalPunchTargets.Jaw jaw) {
      if (kind == FinalPunchTargets.Kind.GOLEM) return 2.0625F;
      return jaw == FinalPunchTargets.Jaw.GENERIC ? 1.5F : 1.406F;
   }

   // ------------------------------------------------------------------ packets

   public static void receive(ReinhardFinalPunchS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) return;
      switch (packet.type()) {
         case ReinhardFinalPunchS2CPacket.START -> {
            Scene old = SCENES.remove(packet.casterId());
            if (old != null) finish(old);
            Scene scene = new Scene(packet, level, 0);
            SCENES.put(scene.casterId, scene);
            FinalPunchAudio.start(scene);
         }
         case ReinhardFinalPunchS2CPacket.IMPACT -> {
            Scene scene = SCENES.get(packet.casterId());
            if (scene == null || scene.cancelled()) {
               // Joined late (tracking began mid-scene) or the victim far away: the impact still plays.
               if (scene != null) finish(SCENES.remove(packet.casterId()));
               scene = new Scene(packet, level, FinalPunchRules.CONTACT);
               SCENES.put(scene.casterId, scene);
            }
            // Re-synchronise to the server's contact tick.
            scene.ticks = FinalPunchRules.CONTACT;
            scene.impacted = true;
            scene.landed = packet.landed();
            scene.impactAt = packet.at();
            FinalPunchAudio.impact(scene);
            FinalPunchVfx.impact(scene);
            if (packet.landed()) FinalPunchJaw.spawn(scene, scene.target(), packet.at(), packet.dir());
         }
         case ReinhardFinalPunchS2CPacket.CANCEL -> {
            Scene scene = SCENES.get(packet.casterId());
            if (scene != null) cancel(scene);
         }
         default -> {
         }
      }
   }

   /** Begins the short fade-out of a cancelled scene (sounds stop now). */
   static void cancel(Scene scene) {
      if (scene.cancelled()) return;
      scene.cancelledAt = scene.ticks;
      FinalPunchAudio.stop(scene);
   }

   /** Ends a scene for good: sounds, ducking, effects; the camera and controls follow (no scene). */
   static void finish(@Nullable Scene scene) {
      if (scene == null) return;
      FinalPunchAudio.stop(scene);
      scene.vfx.clear();
      scene.trail.clear();
   }

   // ------------------------------------------------------------------ queries

   @Nullable
   public static Scene of(int casterId) {
      return SCENES.get(casterId);
   }

   public static Collection<Scene> all() {
      return Collections.unmodifiableCollection(SCENES.values());
   }

   public static boolean any() {
      return !SCENES.isEmpty();
   }

   /** The scene the local player casts, null when none. */
   @Nullable
   public static Scene local() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? null : SCENES.get(mc.player.getId());
   }

   /** The local caster's look and movement belong to the scene (mouse, WASD, jump, sneak, clicks). */
   public static boolean locksLocal() {
      Scene scene = local();
      return scene != null && scene.ticks < FinalPunchRules.LENGTH && scene.fade(0) > 0.0F;
   }

   /** The cinematic view (bars, clean HUD) is up for the local caster. */
   public static boolean cinematic() {
      Scene scene = local();
      return scene != null && scene.ticks < FinalPunchRules.LENGTH && scene.fade(0) > 0.0F;
   }

   /** Renderer hook: the hand's world position this frame (third person or the scene camera). */
   public static void recordFist(int casterId, Vec3 world) {
      Scene scene = SCENES.get(casterId);
      if (scene == null || !Double.isFinite(world.x + world.y + world.z)) return;
      scene.fist = world;
      scene.fistFrame = frame;
   }

   static void nextFrame() {
      frame++;
   }

   // ------------------------------------------------------------------ lifecycle

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         clearAll();
         return;
      }

      FinalPunchJaw.tick(mc.level, mc.isPaused());
      if (mc.isPaused() || SCENES.isEmpty()) return;
      List<Integer> done = new ArrayList<>();
      for (Scene scene : SCENES.values()) {
         if (scene.level != mc.level) {
            // Dimension change / new world: the old scene is gone with its level.
            done.add(scene.casterId);
            continue;
         }

         scene.ticks++;
         Entity caster = scene.caster();
         if (caster == null || caster.isRemoved()) {
            if (++scene.missing > WATCHDOG) done.add(scene.casterId);
         } else {
            scene.missing = 0;
            scene.feet = caster.position();
            if (caster instanceof LivingEntity living && living.isDeadOrDying()) cancel(scene);
         }

         if (scene.isLocalCaster() && mc.player != null && mc.player.isDeadOrDying()) cancel(scene);
         LivingEntity seen = scene.target();
         if (seen != null) scene.lastTarget = seen.position().add(0, seen.getBbHeight() * 0.6, 0);
         Vec3 fist = scene.fist(0.0F);
         scene.fistSmooth = scene.fistSmooth == null ? fist : scene.fistSmooth.lerp(fist, 0.35);
         if (scene.ticks >= FinalPunchRules.STRIKE - 2 && scene.ticks <= FinalPunchRules.CONTACT + 4) {
            scene.trail.add(fist);
            while (scene.trail.size() > 14) scene.trail.remove(0);
         }

         if (!scene.cancelled()) FinalPunchAudio.tick(scene);
         FinalPunchVfx.tick(scene, mc.level);
         boolean over = scene.ticks > FinalPunchRules.LENGTH + FinalPunchCues.TAIL;
         boolean faded = scene.cancelled() && scene.ticks - scene.cancelledAt > FinalPunchCues.CANCEL_FADE;
         if (over || faded) done.add(scene.casterId);
      }

      for (Integer id : done) finish(SCENES.remove(id));
   }

   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clearAll();
      FinalPunchJaw.clear();
   }

   public static void onUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         clearAll();
         FinalPunchJaw.clear();
      }
   }

   /** Every scene ends now (world exit, server stop, disconnect, reload). */
   // ------------------------------------------------------------------ the launched body

   /** Is this entity the target of a running scene (its freeze is the scene's, not a stun). */
   public static boolean isTarget(int entityId) {
      for (Scene scene : SCENES.values()) {
         if (scene.targetId == entityId) return true;
      }
      return false;
   }

   /** Death ticks hidden from the renderer for this frame (entity id -> real value). */
   private static final Map<Integer, Integer> HELD_DEATH = new HashMap<>();

   /** After the blow the scene's target keeps its pose (client visual): a dying body flies upright, never tips over into the lens. */
   public static boolean holdsPose(LivingEntity entity) {
      for (Scene scene : SCENES.values()) {
         if (scene.targetId == entity.getId() && scene.impacted && scene.level == entity.level()) return true;
      }
      return false;
   }

   /** Lowest priority: a render another listener cancelled never reaches here (and never needs the restore). */
   public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
      LivingEntity entity = event.getEntity();
      if (entity.deathTime <= 0 || SCENES.isEmpty() || !holdsPose(entity)) return;
      HELD_DEATH.put(entity.getId(), entity.deathTime);
      entity.deathTime = 0;
   }

   public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
      Integer death = HELD_DEATH.remove(event.getEntity().getId());
      if (death != null) event.getEntity().deathTime = death;
   }

   public static void clearAll() {
      HELD_DEATH.clear();
      if (SCENES.isEmpty()) return;
      List<Scene> scenes = new ArrayList<>(SCENES.values());
      SCENES.clear();
      for (Scene scene : scenes) finish(scene);
      FinalPunchAudio.releaseAll();
   }
}
