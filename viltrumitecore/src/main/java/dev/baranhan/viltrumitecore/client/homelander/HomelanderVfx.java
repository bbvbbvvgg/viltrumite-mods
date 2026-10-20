package dev.baranhan.viltrumitecore.client.homelander;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.ScorchRenderer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.homelander.EyeLasers;
import dev.baranhan.viltrumitecore.hero.homelander.HomelanderRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Homelander laser feedback, purely client-side from the public snapshot:
 * glowing eyes, twin pixel beams to the shared EyeLasers.ray hit, hit sparks,
 * scorch decals on block faces (no blocks change), overheat smoke and the
 * laser loop sound per shooter.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class HomelanderVfx {
   private static final ScorchRenderer SCORCH = new ScorchRenderer(HomelanderRules.SCORCH_MAX, HomelanderRules.SCORCH_LIFETIME);
   private static final Map<UUID, LaserLoop> LOOPS = new HashMap<>();
   private static ClientLevel lastLevel;

   private HomelanderVfx() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         lastLevel = level;
         SCORCH.clear();
         LOOPS.values().forEach(LaserLoop::finish);
         LOOPS.clear();
      }

      if (level == null || client.isPaused()) {
         return;
      }

      long now = level.getGameTime();
      SCORCH.tick(level, now);
      RandomSource random = level.random;
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = HomelanderPoser.homelander(player);
         boolean laser = snapshot != null && HomelanderPoser.laserOn(snapshot);
         if (laser) {
            HitResult hit = EyeLasers.ray(player, HomelanderRules.LASER_RANGE);
            Vec3 at = hit.getLocation();
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
               SCORCH.addLine(player.getUUID(), at, blockHit.getDirection(), now);
               Vec3 normal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
               for (int i = 0; i < 2; i++) {
                  level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z,
                     normal.x * 0.05 + (random.nextDouble() - 0.5) * 0.08, normal.y * 0.05 + random.nextDouble() * 0.06, normal.z * 0.05 + (random.nextDouble() - 0.5) * 0.08);
               }

               if (random.nextInt(3) == 0) {
                  level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, normal.x * 0.02, 0.03, normal.z * 0.02);
               }
            } else {
               SCORCH.forget(player.getUUID());
               if (hit.getType() == HitResult.Type.ENTITY && random.nextInt(2) == 0) {
                  level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0.0, 0.0, 0.0);
               }
            }

            LOOPS.computeIfAbsent(player.getUUID(), id -> {
               LaserLoop loop = new LaserLoop(player);
               client.getSoundManager().play(loop);
               return loop;
            });
         } else {
            SCORCH.forget(player.getUUID());
            LaserLoop loop = LOOPS.remove(player.getUUID());
            if (loop != null) {
               loop.finish();
            }
         }

         if (snapshot != null && snapshot.resourceLocked() && random.nextInt(2) == 0) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            level.addParticle(ParticleTypes.SMOKE, eye.x + look.x * 0.3, eye.y + 0.05, eye.z + look.z * 0.3, 0.0, 0.04, 0.0);
         }
      }

      LOOPS.entrySet().removeIf(entry -> {
         if (level.getPlayerByUUID(entry.getKey()) == null) {
            entry.getValue().finish();
            return true;
         }
         return false;
      });
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
      long now = level.getGameTime();
      boolean anyLaser = false;
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = HomelanderPoser.homelander(player);
         if (snapshot != null && (HomelanderPoser.laserOn(snapshot) || HomelanderPoser.focusOn(snapshot))) {
            anyLaser = true;
            break;
         }
      }

      if (!anyLaser && SCORCH.size() == 0) {
         HomelanderEyesLayer.endFrame();
         return;
      }

      PoseStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushPose();
      modelViewStack.setIdentity();
      PixelVfx.rotateCamera(modelViewStack, camera.getXRot(), camera.getYRot());
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tessellator = Tesselator.getInstance();
      BufferBuilder buffer = tessellator.getBuilder();
      try {
         SCORCH.render(tessellator, cameraPos, now, partialTick);

         if (anyLaser) {
            RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
            buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Player player : level.players()) {
               HeroPublicSnapshot snapshot = HomelanderPoser.homelander(player);
               if (snapshot != null) {
                  drawEyes(buffer, cameraPos, camera, player, snapshot, partialTick, client);
               }
            }
            tessellator.end();
         }
      } finally {
         HomelanderEyesLayer.endFrame();
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelViewStack.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void drawEyes(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Player player, HeroPublicSnapshot snapshot, float partialTick, Minecraft client) {
      boolean laser = HomelanderPoser.laserOn(snapshot);
      boolean focus = HomelanderPoser.focusOn(snapshot);
      Vec3 eye = player.getEyePosition(partialTick);
      Vec3 look = player.getViewVector(partialTick);
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
      right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
      Vec3 forward = look.scale(0.26);
      Vec3 leftEye = eye.add(forward).add(right.scale(-0.11)).add(0.0, 0.02, 0.0);
      Vec3 rightEye = eye.add(forward).add(right.scale(0.11)).add(0.0, 0.02, 0.0);
      boolean localFirstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
      float flicker = 0.85F + 0.15F * Mth.sin((player.tickCount + partialTick) * 1.7F);
      // The glow itself is drawn on the face by HomelanderEyesLayer; beams start from the model's real eyes
      // (every pose and the flight transition included).
      Vec3[] modelEyes = HomelanderEyesLayer.eyes(player.getUUID());
      if (modelEyes != null && !localFirstPerson) {
         leftEye = modelEyes[0];
         rightEye = modelEyes[1];
      }

      if (!laser) {
         return;
      }

      HitResult hit = EyeLasers.ray(player, HomelanderRules.LASER_RANGE);
      Vec3 end = hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
         ? dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.endpoint(entityHit.getEntity(),hit.getLocation()) : hit.getLocation();
      if (localFirstPerson) {
         // Start below the view so the beams converge into the crosshair.
         leftEye = eye.add(look.scale(0.5)).add(right.scale(-0.12)).add(0.0, -0.09, 0.0);
         rightEye = eye.add(look.scale(0.5)).add(right.scale(0.12)).add(0.0, -0.09, 0.0);
      }

      for (Vec3 from : new Vec3[]{leftEye, rightEye}) {
         PixelVfx.beamDots(buffer, cameraPos, camera, from, end, 0.05F, 0.055F, 255, 40, 20, (int)(150 * flicker));
         PixelVfx.beamDots(buffer, cameraPos, camera, from, end, 0.05F, 0.022F, 255, 225, 190, (int)(240 * flicker));
      }

      if (hit.getType() != HitResult.Type.MISS) {
         float pulse = 0.22F + 0.06F * Mth.sin((player.tickCount + partialTick) * 2.3F);
         PixelVfx.crossGlow(buffer, cameraPos, camera, end, pulse, 255, 120, 40, 220);
         PixelVfx.billboardPixel(buffer, cameraPos, camera, end, 0.06F, 255, 240, 210, 255);
      }
   }

   /** Laser hum following the shooter until the beam stops. */
   private static final class LaserLoop extends AbstractTickableSoundInstance {
      private final Player player;

      LaserLoop(Player player) {
         super(ViltrumiteCore.HOMELANDER_LASER_LOOP.get(), SoundSource.PLAYERS, SoundInstanceRandom.RANDOM);
         this.player = player;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.8F;
         this.x = player.getX();
         this.y = player.getEyeY();
         this.z = player.getZ();
      }

      @Override
      public void tick() {
         if (this.player.isRemoved()) {
            this.stop();
            return;
         }

         this.x = this.player.getX();
         this.y = this.player.getEyeY();
         this.z = this.player.getZ();
      }

      void finish() {
         this.stop();
      }
   }

   private static final class SoundInstanceRandom {
      static final RandomSource RANDOM = RandomSource.create();
   }
}
