package dev.baranhan.viltrumitecore.client.ironman;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.hero.ClientHeroData;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.ScorchRenderer;
import dev.baranhan.viltrumitecore.entity.MicroMissileEntity;
import dev.baranhan.viltrumitecore.entity.RepulsorBlastEntity;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.OwnerSection;
import dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManFlags;
import dev.baranhan.viltrumitecore.hero.ironman.IronManRules;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
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
 * Stage 2 combat pixels, client-only from the public snapshot and the synced
 * projectiles (animation-system §8): repulsor bolts and impacts with scorch
 * marks, the Unibeam (chest → snapshot actionTarget), core overheat smoke and
 * overdraft sparks, micro-missiles with smoke trails and the owner's lock
 * brackets (owner section MARKS).
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class IronManCombatVfx {
   private static final ScorchRenderer SCORCH = new ScorchRenderer(IronManRules.SCORCH_MAX, IronManRules.SCORCH_LIFETIME);
   private static final Map<UUID, BeamLoop> LOOPS = new HashMap<>();
   private static ClientLevel lastLevel;

   private IronManCombatVfx() {
   }

   /** Client side of a repulsor bolt hit (called by the entity on the client). */
   public static void repulsorImpact(HitResult hit, float power) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      Vec3 at = hit.getLocation();
      RandomSource random = level.random;
      int sparks = 4 + (int)(power * 10.0F);
      for (int i = 0; i < sparks; i++) {
         level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
            (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
      }

      level.addParticle(power >= 0.9F ? ParticleTypes.EXPLOSION : ParticleTypes.FLASH, at.x, at.y, at.z, 0.0, 0.0, 0.0);
      if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
         SCORCH.add(at, blockHit.getDirection(), level.getGameTime());
      }

      CameraShake.addAt(at, 0.15F + power * 0.5F, 12.0);
   }

   /** Chest (arc reactor) point of a suit, also the Unibeam origin. */
   public static Vec3 chest(Player player, float partialTick) {
      Vec3 look = player.getViewVector(partialTick);
      double height = player.isCrouching() ? 1.05 : 1.32;
      return player.getPosition(partialTick).add(0.0, height, 0.0).add(look.x * 0.3, 0.0, look.z * 0.3);
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
         LOOPS.values().forEach(BeamLoop::finish);
         LOOPS.clear();
      }

      if (level == null || client.isPaused()) {
         return;
      }

      long now = level.getGameTime();
      SCORCH.tick(level, now);
      RandomSource random = level.random;
      for (Player player : level.players()) {
         HeroPublicSnapshot snapshot = IronManView.of(player);
         int phase = snapshot == null ? 0 : IronManView.unibeamPhase(snapshot);
         Vec3 chest = chest(player, 1.0F);
         if (phase == 2) {
            Vec3 end = beamEnd(player, snapshot, 1.0F);
            BlockHitResult block = level.clip(new ClipContext(chest, end.add(end.subtract(chest).normalize().scale(0.3)),
               ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() == HitResult.Type.BLOCK) {
               SCORCH.addLine(player.getUUID(), block.getLocation(), block.getDirection(), now);
               Vec3 at = block.getLocation();
               level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
            } else {
               SCORCH.forget(player.getUUID());
            }

            LOOPS.computeIfAbsent(player.getUUID(), id -> {
               BeamLoop loop = new BeamLoop(player);
               client.getSoundManager().play(loop);
               return loop;
            });
         } else {
            SCORCH.forget(player.getUUID());
            BeamLoop loop = LOOPS.remove(player.getUUID());
            if (loop != null) {
               loop.finish();
            }
         }

         if (snapshot == null) {
            continue;
         }

         int flags = snapshot.heroFlags();
         if ((phase == 3 || IronManFlags.is(flags, IronManFlags.Field.OVERHEAT_LOCK)) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMOKE, chest.x, chest.y, chest.z, (random.nextDouble() - 0.5) * 0.04, 0.05, (random.nextDouble() - 0.5) * 0.04);
         }

         if (phase == 1 && random.nextInt(2) == 0) {
            Vec3 from = chest.add((random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2);
            Vec3 v = chest.subtract(from).scale(0.15);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, v.x, v.y, v.z);
         }

         if (IronManFlags.is(flags, IronManFlags.Field.OVERDRAFT)) {
            int n = IronManFlags.is(flags, IronManFlags.Field.OVERDRAFT_SPUTTER) ? 4 : 1;
            for (int i = 0; i < n; i++) {
               level.addParticle(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z,
                  (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.6);
            }
         }
      }

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof MicroMissileEntity missile && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMOKE, missile.getX(), missile.getY(), missile.getZ(), 0.0, 0.01, 0.0);
         } else if (entity instanceof dev.baranhan.viltrumitecore.entity.FlareEntity flare && flare.life() < IronManRules.FLARE_LIFE - 10) {
            level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE, flare.getX(), flare.getY(), flare.getZ(), 0.0, 0.02, 0.0);
         }
      }

      Iterator<Map.Entry<UUID, BeamLoop>> it = LOOPS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, BeamLoop> entry = it.next();
         if (level.getPlayerByUUID(entry.getKey()) == null) {
            entry.getValue().finish();
            it.remove();
         }
      }
   }

   private static Vec3 beamEnd(Player player, HeroPublicSnapshot snapshot, float partialTick) {
      Vec3 target = snapshot.actionTarget();
      if (target != null) {
         return dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.endpoint(target);
      }

      return chest(player, partialTick).add(player.getViewVector(partialTick).scale(IronManRules.UNIBEAM_RANGE));
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
      boolean any = SCORCH.size() > 0 || ClientHeroData.section(OwnerSection.MARKS).length > 0;
      if (!any) {
         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RepulsorBlastEntity || entity instanceof MicroMissileEntity || entity instanceof dev.baranhan.viltrumitecore.entity.FlareEntity) {
               any = true;
               break;
            }
         }
      }

      if (!any) {
         for (Player player : level.players()) {
            HeroPublicSnapshot snapshot = IronManView.of(player);
            if (snapshot != null && (IronManView.unibeamPhase(snapshot) != 0 || IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.OVERDRAFT))) {
               any = true;
               break;
            }
         }
      }

      if (!any) {
         return;
      }

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
         SCORCH.render(tessellator, cameraPos, now, partialTick);
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Player player : level.players()) {
            HeroPublicSnapshot snapshot = IronManView.of(player);
            if (snapshot != null) {
               drawSuit(buffer, cameraPos, camera, player, snapshot, partialTick, client);
            }
         }

         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RepulsorBlastEntity blast) {
               drawBlast(buffer, cameraPos, camera, blast, partialTick);
            } else if (entity instanceof MicroMissileEntity missile) {
               drawMissile(buffer, cameraPos, camera, missile, partialTick);
            } else if (entity instanceof dev.baranhan.viltrumitecore.entity.FlareEntity flare) {
               FlareRenderer.draw(buffer, cameraPos, camera, flare, partialTick);
            }
         }

         drawMarks(buffer, cameraPos, camera, level, partialTick);
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

   private static void drawSuit(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Player player, HeroPublicSnapshot snapshot, float partialTick, Minecraft client) {
      int phase = IronManView.unibeamPhase(snapshot);
      boolean localFirstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
      Vec3 chest = chest(player, partialTick);
      if (localFirstPerson) {
         chest = player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(0.6)).add(0.0, -0.35, 0.0);
      }

      float flicker = 0.85F + 0.15F * Mth.sin((player.tickCount + partialTick) * 1.9F);
      if (phase == 1) {
         float t = IronManView.progress(snapshot, HeroAction.UNIBEAM, partialTick);
         PixelVfx.crossGlow(buffer, cameraPos, camera, chest, 0.12F + 0.25F * t, 150, 220, 255, (int)(200 * flicker));
      } else if (phase == 2) {
         Vec3 end = beamEnd(player, snapshot, partialTick);
         boolean overdraft = IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.OVERDRAFT);
         boolean defended= snapshot.actionTarget()!=null && dev.baranhan.viltrumitecore.client.hero.HeroDefenseContacts.defended(snapshot.actionTarget());
         float width = defended ? .045F : overdraft ? 0.32F : 0.22F;
         PixelVfx.beamDots(buffer, cameraPos, camera, chest, end, 0.06F, width, 90, 170, 255, (int)(110 * flicker));
         PixelVfx.beamDots(buffer, cameraPos, camera, chest, end, 0.06F, width * 0.45F, 230, 250, 255, (int)(240 * flicker));
         PixelVfx.crossGlow(buffer, cameraPos, camera, chest, 0.35F, 170, 230, 255, 230);
         PixelVfx.crossGlow(buffer, cameraPos, camera, end, defended ? .065F : 0.3F + 0.08F * Mth.sin((player.tickCount + partialTick) * 2.3F), 150, 210, 255, 220);
      }

      if (IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.OVERDRAFT)) {
         boolean sputter = IronManFlags.is(snapshot.heroFlags(), IronManFlags.Field.OVERDRAFT_SPUTTER);
         int blink = sputter && (player.tickCount / 2) % 2 == 0 ? 255 : 160;
         PixelVfx.crossGlow(buffer, cameraPos, camera, chest, sputter ? 0.5F : 0.3F, 255, blink, 120, 220);
      }
   }

   private static void drawBlast(BufferBuilder buffer, Vec3 cameraPos, Camera camera, RepulsorBlastEntity blast, float partialTick) {
      Vec3 at = blast.getPosition(partialTick);
      Vec3 v = blast.getDeltaMovement();
      float power = blast.power();
      float size = 0.08F + power * 0.18F;
      Vec3 tail = v.lengthSqr() < 1.0E-6 ? at : at.subtract(v.normalize().scale(0.6 + power));
      PixelVfx.beamDots(buffer, cameraPos, camera, tail, at, 0.07F, size * 0.6F, 120, 200, 255, 120);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, at, size * 1.6F, 120, 200, 255, 140);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, at, size, 235, 250, 255, 255);
   }

   private static void drawMissile(BufferBuilder buffer, Vec3 cameraPos, Camera camera, MicroMissileEntity missile, float partialTick) {
      Vec3 at = missile.getPosition(partialTick);
      Vec3 prev = at;
      int i = 0;
      for (Vec3 p : missile.trail) {
         int alpha = Math.max(0, 150 - i * 10);
         PixelVfx.beamDots(buffer, cameraPos, camera, prev, p, 0.12F, 0.05F + i * 0.006F, 200, 200, 200, alpha / 2);
         prev = p;
         i++;
      }

      Vec3 v = missile.getDeltaMovement();
      Vec3 back = v.lengthSqr() < 1.0E-6 ? at : at.subtract(v.normalize().scale(0.25));
      PixelVfx.beamDots(buffer, cameraPos, camera, back, at, 0.06F, 0.06F, 90, 90, 100, 255);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, back, 0.09F, 255, 170, 60, 255);
   }

   /** Lock brackets over every marked target, seen by the owner only. */
   private static void drawMarks(BufferBuilder buffer, Vec3 cameraPos, Camera camera, ClientLevel level, float partialTick) {
      int[] marks = ClientHeroData.section(OwnerSection.MARKS);
      for (int id : marks) {
         Entity target = level.getEntity(id);
         if (target == null || target.isRemoved() || !target.isAlive()) {
            continue;
         }

         Vec3 offset = target.getPosition(partialTick).subtract(target.position());
         PixelVfx.boxOutline(buffer, cameraPos, camera, target.getBoundingBox().inflate(0.15).move(offset), 0.05F, 255, 70, 50, 220);
      }
   }

   /** Unibeam hum following the shooter until the beam stops. */
   private static final class BeamLoop extends AbstractTickableSoundInstance {
      private final Player player;

      BeamLoop(Player player) {
         super(IronManCombatSounds.UNIBEAM_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
         this.player = player;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.9F;
         this.x = player.getX();
         this.y = player.getY() + 1.3;
         this.z = player.getZ();
      }

      @Override
      public void tick() {
         if (this.player.isRemoved()) {
            this.stop();
            return;
         }

         this.x = this.player.getX();
         this.y = this.player.getY() + 1.3;
         this.z = this.player.getZ();
      }

      void finish() {
         this.stop();
      }
   }
}
