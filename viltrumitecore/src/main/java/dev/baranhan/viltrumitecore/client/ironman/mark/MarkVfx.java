package dev.baranhan.viltrumitecore.client.ironman.mark;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import dev.baranhan.viltrumitecore.client.ironman.HelmetAnim;
import dev.baranhan.viltrumitecore.client.ironman.IronManView;
import dev.baranhan.viltrumitecore.client.ironman.jarvis.JarvisVoice;
import dev.baranhan.viltrumitecore.client.ironman.veronica.PartFlightVisuals;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.EmptySuitEntity;
import dev.baranhan.viltrumitecore.entity.SuitDebrisEntity;
import dev.baranhan.viltrumitecore.entity.VeronicaPodEntity;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.mark.SuitPart;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Pixel-style effects and sounds of the mark lifecycle: the Veronica fire trail,
 * heat aura and landing dust, empty-suit thruster flames, debris sparks and the
 * sparks of flying parts. Motes age by client ticks (drawn with partialTick-free
 * fades per frame) and clear on logout.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class MarkVfx {
   private static final int MAX_MOTES = 700;
   private static final Random RANDOM = new Random();
   private static final List<Mote> MOTES = new ArrayList<>();
   private static final WeakHashMap<VeronicaPodEntity, VeronicaPodEntity.Phase> PODS = new WeakHashMap<>();
   private static final WeakHashMap<EmptySuitEntity, EmptySuitEntity.Phase> SUITS = new WeakHashMap<>();

   private MarkVfx() {
   }

   private static final class Mote {
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      final int r;
      final int g;
      final int b;
      final int life;
      int age;

      Mote(Vec3 pos, Vec3 vel, int r, int g, int b, int life) {
         this.x = pos.x;
         this.y = pos.y;
         this.z = pos.z;
         this.vx = vel.x;
         this.vy = vel.y;
         this.vz = vel.z;
         this.r = r;
         this.g = g;
         this.b = b;
         this.life = life;
      }
   }

   public static void spawn(Vec3 pos, Vec3 vel, int r, int g, int b, int life) {
      if (MOTES.size() < MAX_MOTES) {
         MOTES.add(new Mote(pos, vel, r, g, b, life));
      }
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || client.isPaused()) {
         return;
      }

      Iterator<Mote> motes = MOTES.iterator();
      while (motes.hasNext()) {
         Mote mote = motes.next();
         mote.x += mote.vx;
         mote.y += mote.vy;
         mote.z += mote.vz;
         mote.vx *= 0.92;
         mote.vy *= 0.92;
         mote.vz *= 0.92;
         if (++mote.age >= mote.life) {
            motes.remove();
         }
      }

      LocalPlayer me = client.player;
      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof VeronicaPodEntity pod) {
            tickPod(client, level, pod, me);
         } else if (entity instanceof EmptySuitEntity suit) {
            tickSuit(client, level, suit);
         } else if (entity instanceof SuitDebrisEntity debris) {
            tickDebris(debris);
         }
      }

      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         if (snapshot == null || (level.getGameTime() & 1L) != 0L) {
            continue;
         }

         MarkState state = MarkState.of(snapshot);
         if (!state.equipping() || !state.delivery() || state.mark() == null) {
            continue;
         }

         for (int i = 0; i < SuitPart.of(state.mark()).size(); i++) {
            Vec3 at = PartFlightVisuals.flyingPosition(player, state, i, 0.0F);
            if (at != null) {
               spawn(at, jitter(0.02), 120, 220, 255, 10);
            }
         }
      }
   }

   private static void tickPod(Minecraft client, ClientLevel level, VeronicaPodEntity pod, LocalPlayer me) {
      VeronicaPodEntity.Phase phase = pod.phase();
      VeronicaPodEntity.Phase before = PODS.put(pod, phase);
      Vec3 pos = pod.position();
      if (before == null) {
         if (phase == VeronicaPodEntity.Phase.FALLING) {
            playAt(level, pos, IronManMarkSounds.VERONICA_FALL.get());
         }
      } else if (before != phase) {
         if (phase == VeronicaPodEntity.Phase.LANDED) {
            playAt(level, pos, IronManMarkSounds.VERONICA_IMPACT.get());
            playAt(level, pos, IronManMarkSounds.VERONICA_OPEN.get());
            for (int i = 0; i < 16; i++) {
               double a = Math.PI * 2.0 * i / 16.0;
               spawn(pos.add(0.0, 0.1, 0.0), new Vec3(Math.cos(a) * 0.16, 0.02, Math.sin(a) * 0.16), 190, 180, 170, 22);
            }

            if (me != null && pod.ownedBy(me) && helmetClosed(me)) {
               JarvisVoice.onVeronicaLanded();
            }
         } else if (phase == VeronicaPodEntity.Phase.LEAVING) {
            playAt(level, pos, IronManMarkSounds.VERONICA_LEAVE.get());
         }
      }

      if (phase == VeronicaPodEntity.Phase.FALLING) {
         for (int i = 0; i < 3; i++) {
            spawn(pos.add(jitter(0.5).x, jitter(0.5).y + 1.0, jitter(0.5).z), new Vec3(RANDOM.nextGaussian() * 0.01, 0.05, RANDOM.nextGaussian() * 0.01), 255, 140 + RANDOM.nextInt(60), 40, 12);
         }
      } else if (phase == VeronicaPodEntity.Phase.LEAVING) {
         for (int i = 0; i < 2; i++) {
            spawn(pos.add(jitter(0.4).x, 0.2, jitter(0.4).z), new Vec3(0.0, -0.14, 0.0), 120, 220, 255, 10);
         }
      }
   }

   private static void tickSuit(Minecraft client, ClientLevel level, EmptySuitEntity suit) {
      EmptySuitEntity.Phase phase = suit.phase();
      EmptySuitEntity.Phase before = SUITS.put(suit, phase);
      Vec3 pos = suit.position();
      if (before != null && before != phase) {
         if (phase == EmptySuitEntity.Phase.OPENING) {
            playAt(level, pos, IronManMarkSounds.MARK_EXIT.get());
         } else if (phase == EmptySuitEntity.Phase.ENTERING) {
            // Plates swing open as Tony walks up.
            playAt(level, pos, IronManMarkSounds.MARK_EXIT.get());
         }
      }

      if (phase == EmptySuitEntity.Phase.ENTERING) {
         // One clamp per plate group as it shuts (legs, arms, chest), the helmet lock on the faceplate.
         int t = suit.clientPhaseAge();
         int[] from = EmptySuitEntity.ENTER_CLOSE_FROM;
         for (int group = 0; group < from.length; group++) {
            if (t == from[group] + EmptySuitEntity.ENTER_CLOSE_TICKS) {
               boolean face = group == EmptySuitEntity.GROUP_FACE;
               level.playLocalSound(pos.x, pos.y + (face ? 1.6 : 1.0), pos.z, face ? IronManMarkSounds.HELMET_LOCK.get() : IronManMarkSounds.PART_CLAMP.get(),
                  SoundSource.PLAYERS, 1.0F, 0.9F + group * 0.08F, false);
            }
         }

         if (t == EmptySuitEntity.ENTER_SEAL) {
            playAt(level, pos, IronManMarkSounds.MARK_ENTER.get());
         }
      }

      if (phase == EmptySuitEntity.Phase.LEAVING) {
         for (int i = 0; i < 2; i++) {
            spawn(pos.add(jitter(0.25).x, 0.1, jitter(0.25).z), new Vec3(0.0, -0.03, 0.0), 120, 200, 255, 8);
         }
      }
   }

   private static void tickDebris(SuitDebrisEntity debris) {
      if (debris.tickCount < 30 && debris.tickCount % 2 == 0) {
         spawn(debris.position().add(0.0, 0.2, 0.0), jitter(0.08), 255, 190, 80, 10);
      }
   }

   private static boolean helmetClosed(LocalPlayer me) {
      HeroPublicSnapshot snapshot = IronManView.of(me);
      return snapshot != null && HelmetAnim.closedFlag(snapshot);
   }

   private static Vec3 jitter(double amount) {
      return new Vec3((RANDOM.nextDouble() - 0.5) * amount, (RANDOM.nextDouble() - 0.5) * amount, (RANDOM.nextDouble() - 0.5) * amount);
   }

   private static void playAt(ClientLevel level, Vec3 pos, net.minecraft.sounds.SoundEvent sound) {
      level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, 1.0F, 1.0F, false);
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      List<VeronicaPodEntity> falling = new ArrayList<>();
      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof VeronicaPodEntity pod && pod.phase() == VeronicaPodEntity.Phase.FALLING) {
            falling.add(pod);
         }
      }

      if (MOTES.isEmpty() && falling.isEmpty()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      float time = (level.getGameTime() + event.getPartialTick()) / 20.0F;
      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.levelView(modelViewStack, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      try {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Mote mote : MOTES) {
            float fade = 1.0F - (float)mote.age / (float)mote.life;
            PixelVfx.billboardPixel(buffer, cameraPos, camera, new Vec3(mote.x, mote.y, mote.z), 0.05F + 0.03F * fade, mote.r, mote.g, mote.b, (int)(230 * fade));
         }

         for (VeronicaPodEntity pod : falling) {
            Vec3 centre = pod.position().add(0.0, 2.0, 0.0);
            PixelVfx.bodyShell(buffer, cameraPos, camera, centre, 1.4F, 2.2F, time, 255, 150, 70, 90);
         }

         tessellator.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      MOTES.clear();
      PODS.clear();
      SUITS.clear();
   }
}
