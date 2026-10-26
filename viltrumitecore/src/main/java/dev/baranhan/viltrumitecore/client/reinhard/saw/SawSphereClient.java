package dev.baranhan.viltrumitecore.client.reinhard.saw;

import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography;
import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawShell;
import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawShells;
import dev.baranhan.viltrumitecore.network.packet.SawSphereEndS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Client half of the Saw Sphere (overhaul item 19): scenes from the start packet, clocked by
 * client ticks from the packet's age; the barrier joins the client collision registry so the local
 * player predicts the same walls the server enforces. For the caster: movement and use keys are
 * cleared, he is held on the hover curve, and the camera is forced to the calm third person back
 * view and restored to exactly the previous mode on any end (packet, timeout, level change,
 * logout). The camera never turns with the model. Sounds are layered and rate-limited here.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class SawSphereClient {
   /** A scene the server never ended is dropped this long after its natural end. */
   static final int TIMEOUT = 40;
   static final int GRIND_GAP = 3;
   private static final Map<Integer, Scene> SCENES = new HashMap<>();
   private static ClientLevel level;
   @Nullable
   private static CameraType savedCamera;
   private static final RandomSource RANDOM = RandomSource.create();

   private SawSphereClient() {
   }

   /** One client scene; {@link SawShells.Barrier} for the local collision. */
   public static final class Scene implements SawShells.Barrier {
      public final int casterId;
      public final double cx;
      public final double y0;
      public final double cz;
      public final float anchorYaw;
      public final SawShell shell;
      final long seed;
      int ticks;
      boolean ended;
      int lastProcessed = -1;
      int lastGrind = -100;
      @Nullable
      SawHumSound hum;
      /** Per swing: the disc it launched or drove and the tick (for the tether), -1 none. */
      int tetherSaw = -1;
      int tetherTick = -100;
      @Nullable
      Object renderCache;

      Scene(SawSphereS2CPacket p) {
         this.casterId = p.casterId();
         this.cx = p.x();
         this.y0 = p.y0();
         this.cz = p.z();
         this.anchorYaw = p.anchorYaw();
         this.ticks = p.age();
         this.lastProcessed = p.age();
         this.shell = SawShell.fromMask(p.x(), p.y0(), p.z(), SawChoreography.RADIUS, p.mask());
         this.seed = Double.doubleToLongBits(p.x()) * 31L + Double.doubleToLongBits(p.z()) + p.casterId();
      }

      /** Scene age with the frame's partial tick. */
      public double age(float partial) {
         return Math.min(SawChoreography.LENGTH, this.ticks + partial);
      }

      public int ageTicks() {
         return this.ticks;
      }

      @Override
      public SawShell shell() {
         return this.shell;
      }

      @Override
      public boolean solid() {
         return !this.ended && SawChoreography.shellSolid(this.ticks);
      }

      @Override
      public int casterId() {
         return this.casterId;
      }

      public long seed() {
         return this.seed;
      }

      public int tetherSaw() {
         return this.tetherSaw;
      }

      public int tetherTick() {
         return this.tetherTick;
      }
   }

   public static Collection<Scene> scenes() {
      return SCENES.values();
   }

   @Nullable
   public static Scene of(int casterId) {
      return SCENES.get(casterId);
   }

   /** Model yaw (degrees) of a spinning caster, NaN when the entity spins no Saw Sphere. */
   public static float modelYaw(Entity entity, float partial) {
      Scene scene = SCENES.get(entity.getId());
      if (scene == null || scene.ended || entity.level() != level) return Float.NaN;
      return SawChoreography.modelYaw(scene.anchorYaw, scene.age(partial));
   }

   public static void start(SawSphereS2CPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      sync(mc.level);
      Scene old = SCENES.remove(packet.casterId());
      if (old != null) drop(old);
      if (SawShell.enumerate(packet.x(), packet.y0(), packet.z(), SawChoreography.RADIUS).length != packet.cellCount()) {
         // The two sides disagree on the shell cells: render nothing rather than a wrong barrier.
         return;
      }
      Scene scene = new Scene(packet);
      SCENES.put(scene.casterId, scene);
      SawShells.add(mc.level, scene);
      if (scene.ticks < SawChoreography.SHATTER) {
         scene.hum = new SawHumSound(ReinhardSounds.SAW_HUM.get(), scene);
         mc.getSoundManager().play(scene.hum);
      }
   }

   public static void end(SawSphereEndS2CPacket packet) {
      Scene scene = SCENES.remove(packet.casterId());
      if (scene != null) drop(scene);
      camera(Minecraft.getInstance());
   }

   private static void drop(Scene scene) {
      scene.ended = true;
      if (scene.hum != null) scene.hum.stopNow();
      if (level != null) SawShells.remove(level, scene);
   }

   private static void clear() {
      for (Scene scene : new ArrayList<>(SCENES.values())) drop(scene);
      SCENES.clear();
      SawShells.clearClient();
   }

   private static void sync(@Nullable ClientLevel now) {
      if (now != level) {
         clear();
         level = now;
      }
   }

   @Nullable
   static Scene local() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return null;
      Scene scene = SCENES.get(mc.player.getId());
      return scene != null && !scene.ended ? scene : null;
   }

   @SubscribeEvent
   public static void onTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      sync(mc.level);
      if (mc.level == null) {
         camera(mc);
         return;
      }

      if (!mc.isPaused()) {
         List<Integer> done = new ArrayList<>();
         for (Scene scene : SCENES.values()) {
            scene.ticks++;
            if (scene.ticks > SawChoreography.LENGTH + TIMEOUT) {
               done.add(scene.casterId);
               continue;
            }
            for (int t = scene.lastProcessed + 1; t <= Math.min(scene.ticks, SawChoreography.LENGTH); t++) cues(mc, scene, t);
            scene.lastProcessed = Math.max(scene.lastProcessed, scene.ticks);
            if (scene.ticks < SawChoreography.CUT_END) contacts(mc, scene);
         }
         for (int id : done) drop(SCENES.remove(id));
      }

      Scene mine = local();
      if (mine != null && mc.player != null && mine.ticks < SawChoreography.LENGTH) {
         // Held on the hover curve: zero motion, the server only corrects a large drift.
         mc.player.setPos(mine.cx, mine.y0 + SawChoreography.hover(mine.ticks), mine.cz);
         mc.player.setDeltaMovement(Vec3.ZERO);
         mc.player.fallDistance = 0.0F;
      }
      camera(mc);
   }

   /** Third person back while the local caster's scene runs; the saved mode comes back on every end. */
   private static void camera(Minecraft mc) {
      boolean forced = local() != null && mc.level != null;
      if (forced) {
         if (savedCamera == null) savedCamera = mc.options.getCameraType();
         if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      } else if (savedCamera != null) {
         mc.options.setCameraType(savedCamera);
         savedCamera = null;
      }
   }

   /** Pure camera policy: the mode to restore after the scene (the saved one, else unchanged). */
   public static CameraType restored(@Nullable CameraType saved, CameraType current) {
      return saved == null ? current : saved;
   }

   private static Vec3 casterAt(Minecraft mc, Scene scene, int t) {
      Entity caster = mc.level == null ? null : mc.level.getEntity(scene.casterId);
      return caster != null ? caster.position().add(0.0, 1.2, 0.0) : new Vec3(scene.cx, scene.y0 + SawChoreography.hover(t) + 1.2, scene.cz);
   }

   /** Timeline cues of tick {@code t}: swing whooshes (one per swing), the final accent, the shatter. */
   private static void cues(Minecraft mc, Scene scene, int t) {
      int swing = SawChoreography.swingAt(t);
      if (swing >= 0) {
         scene.tetherSaw = SawChoreography.sawOfSwing(swing);
         scene.tetherTick = t;
         play(mc, ReinhardSounds.SAW_SWING.get(), casterAt(mc, scene, t), 0.55F, (swing & 1) == 0 ? 1.12F : 0.96F);
      }
      if (t == SawChoreography.FINAL_SWING) {
         play(mc, ReinhardSounds.SAW_SWING.get(), casterAt(mc, scene, t), 1.0F, 0.78F);
         play(mc, ReinhardSounds.SAW_FINALE.get(), new Vec3(scene.cx, scene.y0 + 4.0, scene.cz), 1.0F, 1.0F);
      }
      if (t == SawChoreography.SHATTER) {
         play(mc, ReinhardSounds.SAW_SHATTER.get(), new Vec3(scene.cx, scene.y0 + 6.0, scene.cz), 1.0F, 1.0F);
         CameraShake.cinematicAt(new Vec3(scene.cx, scene.y0, scene.cz), 0.9F, 40.0);
      }
   }

   private static void play(Minecraft mc, SoundEvent event, Vec3 at, float volume, float pitch) {
      mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, volume, pitch, RANDOM, at.x, at.y, at.z));
   }

   /**
    * Where the discs bite into real (client) blocks right now: debris and sparks under the particle
    * setting, and at most one metallic grind voice per {@link #GRIND_GAP} ticks per scene, louder with
    * more contacts. The server decides what is cut; this only reads the client world.
    */
   private static void contacts(Minecraft mc, Scene scene) {
      int t = scene.ticks;
      int budget = switch (mc.options.particles().get()) {
         case ALL -> 40;
         case DECREASED -> 14;
         case MINIMAL -> 4;
      };
      double[] p = new double[3];
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int contacts = 0;
      double gx = 0, gy = 0, gz = 0;
      for (int s = 0; s < SawChoreography.COUNT; s++) {
         if (!SawChoreography.cutting(s, t)) continue;
         SawChoreography.position(s, t, scene.cx, scene.y0, scene.cz, scene.anchorYaw, p);
         for (int k = 0; k < 2; k++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0, r = SawChoreography.SAW_RADIUS * Math.sqrt(0.25 + 0.75 * RANDOM.nextDouble());
            double x = p[0] + Math.cos(a) * r, y = p[1] - SawChoreography.HALF_THICKNESS - 0.15, z = p[2] + Math.sin(a) * r;
            if (y <= scene.y0) continue;
            pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            contacts++;
            gx += x;
            gy += y;
            gz += z;
            if (budget > 0) {
               budget--;
               double tx = -Math.sin(a), tz = Math.cos(a);
               mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y + 0.2, z, tx * 0.35, 0.18, tz * 0.35);
               if (budget > 0 && (k & 1) == 0) {
                  budget--;
                  mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + 0.3, z, tx * 0.6, 0.12, tz * 0.6);
               }
            }
         }
      }
      if (contacts > 0 && t - scene.lastGrind >= GRIND_GAP) {
         scene.lastGrind = t;
         float volume = Math.min(1.0F, 0.35F + 0.06F * contacts);
         play(mc, ReinhardSounds.SAW_GRIND.get(), new Vec3(gx / contacts, gy / contacts, gz / contacts), volume, 0.9F + RANDOM.nextFloat() * 0.25F);
      }
   }

   /** The caster stands still: no walking, jumping or sneaking out of the hover. */
   @SubscribeEvent
   public static void onMovement(MovementInputUpdateEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getEntity() != mc.player || local() == null) return;
      Input input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }

   /** No attacks, item use or pick while the scene holds him (the server refuses abilities as busy). */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
      if (local() == null) return;
      event.setSwingHand(false);
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
      level = null;
      camera(Minecraft.getInstance());
   }
}
