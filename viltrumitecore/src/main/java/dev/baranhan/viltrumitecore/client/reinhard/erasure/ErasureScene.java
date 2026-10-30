package dev.baranhan.viltrumitecore.client.reinhard.erasure;

import static dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules.*;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.hero.CinematicHud;
import dev.baranhan.viltrumitecore.client.hero.WorldPauseClient;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import dev.baranhan.viltrumitecore.client.render.scene.SceneEntityHide;
import dev.baranhan.viltrumitecore.client.render.vfx.ScreenProjector;
import dev.baranhan.viltrumitecore.client.render.vfx.SoundDuck;
import dev.baranhan.viltrumitecore.config.ViltrumitePostProcessingConfig;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.network.packet.ErasureS2CPacket;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

/**
 * World Erasure, client side: ONE scene clock for everything (0 = the N press, server age + local
 * unpaused client ticks, never a dimension's game time), see ReinhardRules for the beats:
 * stop + line (0..20) -> slow crack growth (20..80) -> critical rupture (80..90) -> shatter and the
 * void, the target alone in its frozen pose (90..) -> white light (92..) -> dissolve (112..150, the
 * server removes the target at 150) -> reality pulls back (160..) -> six rings of shards snap home
 * with a growing shake, the last one the final impact (168..192) -> cracks close, shake settles (..205).
 * Geometry: {@link ErasureGlass}/{@link ErasureGlassRenderer}; the target: {@link ErasureDissolve}/
 * {@link ErasureDissolveRenderer}; grade: {@link ErasureUniforms}; shake: {@link ErasureShake}.
 * The HUD is hidden for the whole scene through {@link CinematicHud}.
 */
public final class ErasureScene {
   public enum EndReason { NORMAL, CANCEL, LOGOUT, LEVEL_UNLOAD, RELOAD, WATCHDOG }
   public enum Cue { TENSION, CRACK, SHATTER, DISSOLVE, INHALE, SNAP, RESTORE }
   public record Beat(int tick, Cue cue, float pitch) {}
   /** Every sound beat, in order, on the scene clock. */
   public static final List<Beat> BEATS = beats();
   /** A beat crossed more than this many ticks late (catch-up, late join) is skipped, never replayed. */
   public static final int LATE = 3;
   public static final int DUCK_FADE = 6, RELEASE_FADE = 13;

   public interface SoundEnvironment {
      void play(Cue cue, float pitch);
      void duck(int fadeTicks);
      void release(int fadeTicks);
      /** Is this scene's duck still registered; a client level change clears every duck. */
      default boolean ducking() {return true;}
      /** Cut the scene's own sounds (an aborted scene must not keep playing its beats). */
      default void stop() {}
   }

   static List<Beat> beats() {
      List<Beat> out = new ArrayList<>();
      out.add(new Beat(ERASE_CRACK_START, Cue.TENSION, 1));
      out.add(new Beat(ERASE_SHATTER_START, Cue.CRACK, 1));
      out.add(new Beat(ERASE_SHATTER_START, Cue.SHATTER, 1));
      out.add(new Beat(ERASE_DISSOLVE_START, Cue.DISSOLVE, 1));
      out.add(new Beat(ERASE_DARK_END, Cue.INHALE, 1));
      for (int i = 0; i < ErasureGlass.SNAPS.length - 1; i++) out.add(new Beat(Math.round(ErasureGlass.SNAPS[i]), Cue.SNAP, .82F + .07F * i));
      out.add(new Beat(ERASE_REBUILD_END, Cue.RESTORE, 1));
      return List.copyOf(out);
   }

   /** Sound/duck state machine on the scene clock (pure: no Minecraft classes). */
   public static final class Timeline {
      private int age, next; private boolean ducked, released, ended;
      private final SoundEnvironment sound;
      public Timeline(int initialAge, SoundEnvironment sound) {
         this.age = Math.max(0, initialAge); this.sound = sound;
         if (age >= ERASE_LENGTH) {ended = true; return;}
         if (age < ERASE_REBUILD_END) {sound.duck(Math.max(0, DUCK_FADE - age)); ducked = true;}
         fire();
      }
      public int age() {return age;}
      public boolean ended() {return ended;}
      public ErasureUniforms uniforms(float partialTick) {return ended ? ErasureUniforms.ZERO : ErasureUniforms.at(age, partialTick);}
      public void advance(int tick) {
         if (ended) return;
         age = Math.max(age, tick);
         // A dimension change mid-scene dropped the duck: register it again, already at depth.
         if (ducked && !released && age < ERASE_REBUILD_END && !sound.ducking()) sound.duck(0);
         fire();
         if (age >= ERASE_REBUILD_END && ducked && !released) {sound.release(RELEASE_FADE); released = true;}
         if (age >= ERASE_LENGTH) end(EndReason.NORMAL);
      }
      private void fire() {
         while (next < BEATS.size() && BEATS.get(next).tick() <= age) {
            Beat beat = BEATS.get(next++);
            if (age - beat.tick() <= LATE) sound.play(beat.cue(), beat.pitch());
         }
      }
      public void end(EndReason reason) {
         if (ended) return;
         ended = true;
         if (reason != EndReason.NORMAL) sound.stop();
         if (ducked && !released) {sound.release(reason == EndReason.NORMAL ? RELEASE_FADE : DUCK_FADE); released = true;}
      }
   }

   /** A re-sent packet rebases once, preserving monotonic age without waiting for the old receipt. */
   public static final class Clock {
      private int serverAge; private long receipt;
      public Clock(int age, long now) {serverAge = Math.max(0, age); receipt = now;}
      public int at(long now) {return elapsed(serverAge, receipt, now);}
      public void sync(int age, long now) {serverAge = Math.max(at(now), Math.max(0, age)); receipt = now;}
   }

   private static final List<String> SOUND_NAMES = List.of("erase_tension", "erase_crack", "erase_shatter", "erase_dissolve", "erase_inhale", "erase_snap", "erase_restore");
   private static final Set<ResourceLocation> OWN_SOUNDS = ownSounds();
   private static Set<ResourceLocation> ownSounds() {
      Set<ResourceLocation> out = new HashSet<>();
      for (String name : SOUND_NAMES) out.add(new ResourceLocation("viltrumitecore", "reinhard." + name));
      return Set.copyOf(out);
   }
   private static Active scene; private static long clientTicks; private static boolean initialized;
   private static final Matrix4f VIEW = new Matrix4f();
   private ErasureScene() {}

   public static void init() {
      if (initialized) return; initialized = true;
      ErasurePost.bind(ErasureScene::active, ErasureScene::uniforms);
      // The server pause ends with the scene; an early end is a cancel (owner left, admin stop).
      WorldPauseClient.onEnd(() -> clear(scene != null && scene.timeline.age() < ERASE_LENGTH - 5 ? EndReason.CANCEL : EndReason.NORMAL));
      SceneEntityHide.register(ErasureScene::hidesFromLevel);
      CinematicHud.hideWhile(ErasureScene::active, ErasureScene::renderHud);
   }

   private static final class Active {
      final ErasureS2CPacket packet; final Clock clock; final Timeline timeline; final float seed; final long seedBits;
      ErasureGlass glass; float originX = Float.NaN, originY; boolean renderFailed;
      Active(ErasureS2CPacket packet) {
         this.packet = packet; this.clock = new Clock(packet.age(), clientTicks);
         seedBits = packet.pauseId().getMostSignificantBits() ^ packet.pauseId().getLeastSignificantBits();
         seed = (seedBits & 65535) / 65535F;
         timeline = new Timeline(packet.age(), new SoundEnvironment() {
            public void play(Cue cue, float pitch) {
               Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event(cue), pitch, 1));
            }
            public void duck(int fade) {SoundDuck.INSTANCE.duck(Active.this, SoundSource.MASTER, 0, fade, OWN_SOUNDS);}
            public void release(int fade) {SoundDuck.INSTANCE.release(Active.this, fade);}
            public boolean ducking() {return SoundDuck.INSTANCE.holds(Active.this);}
            public void stop() {for (ResourceLocation id : OWN_SOUNDS) Minecraft.getInstance().getSoundManager().stop(id, SoundSource.MASTER);}
         });
      }
      boolean sameLevel(Minecraft mc) {return mc.level != null && mc.level.dimension().equals(packet.targetLevel());}
   }

   static SoundEvent event(Cue cue) {
      return switch (cue) {
         case TENSION -> ReinhardSounds.ERASE_TENSION.get();
         case CRACK -> ReinhardSounds.ERASE_CRACK.get();
         case SHATTER -> ReinhardSounds.ERASE_SHATTER.get();
         case DISSOLVE -> ReinhardSounds.ERASE_DISSOLVE.get();
         case INHALE -> ReinhardSounds.ERASE_INHALE.get();
         case SNAP -> ReinhardSounds.ERASE_SNAP.get();
         case RESTORE -> ReinhardSounds.ERASE_RESTORE.get();
      };
   }

   public static int elapsed(int serverAge, long received, long now) {
      long delta = now >= received ? now - received : 0;
      return delta >= Integer.MAX_VALUE - Math.max(0, serverAge) ? Integer.MAX_VALUE : Math.max(0, serverAge) + (int)delta;
   }
   public static boolean active() {return scene != null && !scene.timeline.ended() && scene.timeline.age() < ERASE_LENGTH;}
   public static ErasureUniforms uniforms(float partialTick) {return active() ? scene.timeline.uniforms(partialTick).withSeed(scene.seed) : ErasureUniforms.ZERO;}

   public static void onPacket(ErasureS2CPacket packet) {
      if (scene != null && scene.packet.pauseId().equals(packet.pauseId())) {
         // Same scene re-sent (dimension change, late join): rebase, never restart.
         scene.clock.sync(packet.age(), clientTicks); scene.timeline.advance(scene.clock.at(clientTicks)); return;
      }
      clear(EndReason.CANCEL); scene = new Active(packet);
   }
   public static void clear(EndReason reason) {
      if (scene != null) scene.timeline.end(reason);
      scene = null;
      ErasureGlassRenderer.release();
      ErasureDissolveRenderer.reset();
   }
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {if (scene != null) clear(EndReason.LOGOUT); return;}
      if (mc.isPaused()) return;
      clientTicks++;
      if (scene == null) return;
      scene.timeline.advance(scene.clock.at(clientTicks));
      if (scene.timeline.ended()) clear(EndReason.NORMAL);
   }

   private static float time(Active s, float partialTick) {return s.timeline.age() + Math.max(0, Math.min(1, partialTick));}

   /** Rendered-camera shake only (the real yaw/pitch stay locked). */
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      Active s = scene;
      if (s == null || !active()) return;
      ErasureShake.Offset o = ErasureShake.at(time(s, (float)event.getPartialTick()), ViltrumitePostProcessingConfig.INSTANCE.reinhardWorldCutShake);
      if (o == ErasureShake.Offset.ZERO) return;
      event.setPitch(event.getPitch() + o.pitch());
      event.setYaw(event.getYaw() + o.yaw());
      event.setRoll(event.getRoll() + o.roll());
   }

   /** Level projection and view of the main frame, for the target's own draw after the post pass. */
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || scene == null) return;
      Minecraft mc = Minecraft.getInstance();
      if (ShaderCompat.isShadowPass() || event.getCamera() != mc.gameRenderer.getMainCamera()) return;
      ErasureDissolveRenderer.captureProjection(event.getProjectionMatrix());
      VIEW.set(ScreenProjector.viewRotation(ScreenProjector.roll(), event.getCamera().getXRot(), event.getCamera().getYRot()));
   }

   /** Is the local view the target's own first-person eyes (its model must not be drawn over the camera)? */
   private static boolean selfView(Active s, Minecraft mc) {
      Entity eye = mc.getCameraEntity();
      return eye != null && eye.getId() == s.packet.targetId() && s.sameLevel(mc) && mc.options.getCameraType().isFirstPerson();
   }

   /** The level pass skips the target while the scene draws it (frozen pose, white light, dissolve). */
   static boolean hidesFromLevel(Entity entity) {
      Active s = scene;
      if (s == null || s.renderFailed || !active() || entity.getId() != s.packet.targetId()) return false;
      Minecraft mc = Minecraft.getInstance();
      return s.sameLevel(mc) && ErasureDissolve.sceneOwnsTarget(time(s, mc.getFrameTime()), s.packet.erase());
   }

   /** Pure: glass origin in plane units from the target's GUI point (centre when unknown/off screen). */
   public static float[] origin(float x, float y, int width, int height, float aspect, boolean onScreen) {
      if (!onScreen || width <= 0 || height <= 0 || !Float.isFinite(x) || !Float.isFinite(y)) return new float[]{0, 0};
      float nx = Math.max(-.8F, Math.min(.8F, 2 * x / width - 1)), ny = Math.max(-.8F, Math.min(.8F, 1 - 2 * y / height));
      return new float[]{nx * aspect, ny};
   }

   /** Called from the renderLevel TAIL seam (after the dash_impact grade). */
   public static void renderSeam(float partialTick) {
      Active s = scene;
      if (s == null || !active()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      float t = time(s, partialTick);
      var main = mc.getMainRenderTarget();
      if (main.width <= 0 || main.height <= 0) return;
      float aspect = main.width / (float)main.height;
      try {
         if (Float.isNaN(s.originX) && t >= 1) {
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            var p = s.packet;
            var point = s.sameLevel(mc) ? ScreenProjector.project(p.targetPos().add(0, p.bbHeight() * .5, 0), w, h) : null;
            float[] o = origin(point == null ? Float.NaN : point.x(), point == null ? Float.NaN : point.y(), w, h, 1, point != null && point.onScreen(w, h));
            s.originX = o[0]; s.originY = o[1];
         }
         if (Float.isNaN(s.originX)) return;
         if (s.glass == null || Math.abs(s.glass.aspect() - aspect) > 1e-3F) s.glass = ErasureGlass.of(aspect, s.originX * aspect, s.originY, s.seedBits);
         ErasureShake.Offset o = ErasureShake.at(t, ViltrumitePostProcessingConfig.INSTANCE.reinhardWorldCutShake);
         main.bindWrite(true);
         ErasureGlassRenderer.render(s.glass, t, o.roll() * .35F, o.dx(), o.dy());
         if (!s.renderFailed && s.sameLevel(mc) && ErasureDissolve.sceneOwnsTarget(t, s.packet.erase()) && !selfView(s, mc)) {
            var p = s.packet;
            LivingEntity target = mc.level.getEntity(p.targetId()) instanceof LivingEntity living ? living : null;
            PoseStack view = new PoseStack();
            view.last().pose().set(VIEW);
            ErasureDissolveRenderer.render(target, p.targetId(), p.targetPos().add(0, p.bbHeight() * .5, 0), Math.max(p.bbWidth(), p.bbHeight()), t, p.erase(), s.seedBits, view);
         }
      } catch (RuntimeException error) {
         if (!s.renderFailed) com.mojang.logging.LogUtils.getLogger().warn("World Erasure scene render failed; grade only from here", error);
         s.renderFailed = true;
      }
   }

   /** The target's own screen: white light closing in (its model is not drawn over its eyes). */
   static void renderHud(GuiGraphics g, float partialTick, int width, int height) {
      Active s = scene;
      Minecraft mc = Minecraft.getInstance();
      if (s == null || !selfView(s, mc)) return;
      float v = ErasureDissolve.selfVignette(time(s, partialTick), s.packet.erase());
      if (v <= .005F) return;
      int centre = Math.round(255 * .75F * v * v), edge = Math.round(255 * Math.min(1, v * 1.1F));
      g.fill(0, 0, width, height, centre << 24 | 0xFFFFFF);
      int band = Math.max(1, Math.round(height * .38F));
      g.fillGradient(0, 0, width, band, edge << 24 | 0xFFFFFF, 0x00FFFFFF);
      g.fillGradient(0, height - band, width, height, 0x00FFFFFF, edge << 24 | 0xFFFFFF);
   }

   public static void onUnload(LevelEvent.Unload event) {if (event.getLevel().isClientSide()) clear(EndReason.LEVEL_UNLOAD);}
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {clear(EndReason.LOGOUT);}
   @EventBusSubscriber(modid = "viltrumitecore", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Registrar {
      @SubscribeEvent public static void onReload(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener((ResourceManagerReloadListener) manager -> clear(EndReason.RELOAD));
      }
   }
}
