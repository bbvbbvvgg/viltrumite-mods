package dev.baranhan.viltrumitecore.client.ironman.legion;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.baranhan.viltrumitecore.client.ironman.mark.MarkVfx;
import dev.baranhan.viltrumitecore.client.ironman.mark.sig.GunRounds;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionMove;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionPhase;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionProfile;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionRules;
import dev.baranhan.viltrumitecore.hero.ironman.legion.LegionSteering;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * World effects of the Iron Legion (design §7), in the mod's paint-pixel
 * style: the far glint of the sky signal, energy trails, repulsor muzzle
 * flashes on each synced shot, the Mark 7 laser, the Mark 17 pulse beam and
 * War Machine tracers at the synced aim target, sparks and smoke from a
 * critical suit, the crash. Beams start at the palm/reactor anchors of the
 * posed body of the same frame ({@link LegionSuitRenderer#anchors}). Damage
 * is the server's; this is only light. Every list is capped and cleared on
 * level change and logout.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class LegionVfx {
   private static final int MAX_FLASHES = 48;
   private static final Map<Integer, Track> TRACKS = new HashMap<>();
   private static final List<Flash> FLASHES = new ArrayList<>();
   @Nullable
   private static ClientLevel lastLevel;

   private LegionVfx() {
   }

   /** What this client already showed of one suit. */
   private static final class Track {
      int moveStart = Integer.MIN_VALUE;
      LegionPhase phase;
      int seen;
      @Nullable
      LegionEngineSound engine;
   }

   private record Flash(Vec3 pos, float size, int r, int g, int b, int life, int[] age) {
   }

   static void clear() {
      for (Track track : TRACKS.values()) {
         if (track.engine != null) {
            Minecraft.getInstance().getSoundManager().stop(track.engine);
         }
      }

      TRACKS.clear();
      FLASHES.clear();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level != lastLevel) {
         lastLevel = level;
         clear();
         LegionSuitRenderer.clear();
      }

      if (level == null || client.isPaused()) {
         return;
      }

      int now = (int)level.getGameTime();
      RandomSource random = level.random;
      for (Entity entity : level.entitiesForRendering()) {
         if (!(entity instanceof LegionSuitEntity suit) || suit.mark() == null) {
            continue;
         }

         Track track = TRACKS.computeIfAbsent(suit.getId(), id -> new Track());
         track.seen = now;
         LegionPhase phase = suit.phase();
         LegionProfile profile = LegionProfile.of(suit.mark());
         boolean silent = profile != null && profile.silent();
         if (!silent && phase != LegionPhase.CRASH && (track.engine == null || track.engine.isStopped())) {
            track.engine = new LegionEngineSound(suit);
            client.getSoundManager().play(track.engine);
         }

         Points points = points(suit);
         if (suit.clientMoveStart != track.moveStart) {
            boolean fresh = track.moveStart != Integer.MIN_VALUE && suit.clientMoveStart > 0;
            track.moveStart = suit.clientMoveStart;
            if (fresh) {
               onMove(level, suit, suit.clientMove, points, random);
            }
         }

         if (phase != track.phase) {
            if (track.phase != null) {
               onPhase(level, suit, phase, points, random);
            }

            track.phase = phase;
         }

         trail(suit, phase, points, silent, random);
         if (suit.flag(LegionSuitEntity.FLAG_CRITICAL) || phase == LegionPhase.CRASH) {
            damaged(level, suit, phase, points, random);
         }

         if (suit.flag(LegionSuitEntity.FLAG_GUN) && suit.tickCount % 2 == 0) {
            Vec3 target = aimPoint(level, suit, 1.0F);
            if (target != null) {
               Vec3 to = target.subtract(points.shoulder);
               if (to.lengthSqr() > 1.0) {
                  GunRounds.fire(level, suit, points.shoulder.add(to.normalize().scale(0.7)), target);
               }
            }
         }
      }

      Iterator<Map.Entry<Integer, Track>> tracks = TRACKS.entrySet().iterator();
      while (tracks.hasNext()) {
         Map.Entry<Integer, Track> entry = tracks.next();
         if (entry.getValue().seen != now) {
            if (entry.getValue().engine != null) {
               client.getSoundManager().stop(entry.getValue().engine);
            }

            LegionSuitRenderer.forget(entry.getKey());
            tracks.remove();
         }
      }

      FLASHES.removeIf(flash -> ++flash.age()[0] >= flash.life());
   }

   /** Muzzle, launch, hit and dodge beats at the start of each synced move. */
   private static void onMove(ClientLevel level, LegionSuitEntity suit, LegionMove move, Points points, RandomSource random) {
      switch (move) {
         case FIRE_RIGHT -> muzzle(points.rightPalm, random);
         case FIRE_LEFT -> muzzle(points.leftPalm, random);
         case FIRE_BOTH -> {
            muzzle(points.rightPalm, random);
            muzzle(points.leftPalm, random);
         }
         case MISSILES -> {
            for (int i = 0; i < 6; i++) {
               MarkVfx.spawn(points.shoulder, jitter(random, 0.08).add(0.0, 0.05, 0.0), 230, 230, 235, 10 + random.nextInt(6));
            }

            flash(points.shoulder, 0.3F, 255, 190, 110, 3);
         }
         case SIGNATURE -> flash(points.chest, 0.5F, 170, 230, 255, 4);
         case HIT -> {
            for (int i = 0; i < 8; i++) {
               MarkVfx.spawn(points.chest, jitter(random, 0.25), 255, 200, 90, 6 + random.nextInt(6));
            }

            level.addParticle(ParticleTypes.CRIT, points.chest.x, points.chest.y, points.chest.z, 0.0, 0.1, 0.0);
         }
         case DODGE_LEFT, DODGE_RIGHT -> {
            Vec3 feet = suit.position().add(0.0, 0.2, 0.0);
            for (int i = 0; i < 6; i++) {
               MarkVfx.spawn(feet, jitter(random, 0.12), 150, 210, 255, 8);
            }
         }
         case LAND -> {
            for (int i = 0; i < 10; i++) {
               double a = i / 10.0 * Math.PI * 2.0;
               level.addParticle(ParticleTypes.POOF, suit.getX(), suit.getY() + 0.1, suit.getZ(), Math.cos(a) * 0.12, 0.01, Math.sin(a) * 0.12);
            }
         }
         default -> {
         }
      }
   }

   /** Brake: retro-blast of the palms and feet; crash: a burst of sparks as the engines fail. */
   private static void onPhase(ClientLevel level, LegionSuitEntity suit, LegionPhase phase, Points points, RandomSource random) {
      switch (phase) {
         case BRAKE -> {
            flash(points.rightPalm, 0.45F, 170, 225, 255, 4);
            flash(points.leftPalm, 0.45F, 170, 225, 255, 4);
            Vec3 feet = suit.position().add(0.0, 0.1, 0.0);
            flash(feet, 0.6F, 170, 225, 255, 4);
            for (int i = 0; i < 14; i++) {
               MarkVfx.spawn(feet, jitter(random, 0.3), 160, 215, 255, 10 + random.nextInt(6));
            }
         }
         case READY -> flash(points.chest, 0.35F, 190, 240, 255, 5);
         case CRASH -> {
            for (int i = 0; i < 18; i++) {
               MarkVfx.spawn(points.chest, jitter(random, 0.35), 255, 170 + random.nextInt(60), 60, 8 + random.nextInt(10));
            }

            level.addParticle(ParticleTypes.EXPLOSION, points.chest.x, points.chest.y, points.chest.z, 0.0, 0.0, 0.0);
         }
         default -> {
         }
      }
   }

   private static void muzzle(Vec3 palm, RandomSource random) {
      flash(palm, 0.32F, 160, 220, 255, 3);
      for (int i = 0; i < 3; i++) {
         MarkVfx.spawn(palm, jitter(random, 0.07), 200, 240, 255, 5 + random.nextInt(3));
      }
   }

   /** Energy trail from the boots while the suit really moves; a long bright streak in the arrival and the climb out. */
   private static void trail(LegionSuitEntity suit, LegionPhase phase, Points points, boolean silent, RandomSource random) {
      if (silent || suit.flag(LegionSuitEntity.FLAG_CAMO)) {
         return;
      }

      Vec3 velocity = new Vec3(suit.getX() - suit.xo, suit.getY() - suit.yo, suit.getZ() - suit.zo);
      double speed = velocity.length();
      boolean streak = phase == LegionPhase.APPROACH || phase == LegionPhase.LEAVE || suit.flag(LegionSuitEntity.FLAG_BOOST);
      if (!streak && speed < 0.3) {
         return;
      }

      Vec3 feet = suit.position().add(0.0, 0.15, 0.0);
      int count = streak ? 3 : 1;
      for (int i = 0; i < count; i++) {
         Vec3 at = feet.subtract(velocity.scale(i / (double)count));
         MarkVfx.spawn(at.add(jitter(random, 0.05)), velocity.scale(-0.08), 150, 205, 255, streak ? 16 : 9);
      }
   }

   private static void damaged(ClientLevel level, LegionSuitEntity suit, LegionPhase phase, Points points, RandomSource random) {
      boolean crash = phase == LegionPhase.CRASH;
      if (suit.tickCount % (crash ? 1 : 3) == 0) {
         level.addParticle(crash ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, points.chest.x, points.chest.y, points.chest.z,
            (random.nextDouble() - 0.5) * 0.04, 0.05, (random.nextDouble() - 0.5) * 0.04);
      }

      if (random.nextInt(crash ? 2 : 6) == 0) {
         Vec3 at = random.nextBoolean() ? points.rightPalm : points.chest;
         for (int i = 0; i < 3; i++) {
            MarkVfx.spawn(at, jitter(random, 0.18).add(0.0, 0.06, 0.0), 255, 190 + random.nextInt(50), 80, 5 + random.nextInt(5));
         }

         level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0.0, 0.05, 0.0);
      }

      if (crash && suit.tickCount % 2 == 0) {
         level.addParticle(ParticleTypes.FLAME, suit.getX(), suit.getY() + 0.3, suit.getZ(), 0.0, 0.02, 0.0);
      }
   }

   private static void flash(Vec3 pos, float size, int r, int g, int b, int life) {
      if (FLASHES.size() < MAX_FLASHES) {
         FLASHES.add(new Flash(pos, size, r, g, b, life, new int[1]));
      }
   }

   private static Vec3 jitter(RandomSource random, double size) {
      return new Vec3((random.nextDouble() - 0.5) * size, (random.nextDouble() - 0.5) * size, (random.nextDouble() - 0.5) * size);
   }

   // --- anchors ---

   private record Points(Vec3 rightPalm, Vec3 leftPalm, Vec3 chest, Vec3 shoulder) {
   }

   /** Posed anchors from the renderer when it drew this suit, else the body points from the interpolated yaw. */
   private static Points points(LegionSuitEntity suit) {
      LegionSuitRenderer.Anchors anchors = LegionSuitRenderer.anchors(suit);
      if (anchors != null) {
         return new Points(anchors.rightPalm(), anchors.leftPalm(), anchors.chest(), anchors.shoulder());
      }

      Vec3 fwd = LegionSteering.forward(suit.getYRot());
      Vec3 side = LegionSteering.right(fwd);
      Vec3 base = suit.position();
      return new Points(
         base.add(0.0, 1.0, 0.0).add(side.scale(0.38)).add(fwd.scale(0.1)),
         base.add(0.0, 1.0, 0.0).add(side.scale(-0.38)).add(fwd.scale(0.1)),
         base.add(0.0, 1.28, 0.0).add(fwd.scale(0.18)),
         base.add(0.0, 1.55, 0.0).add(side.scale(0.25)));
   }

   /** The synced aim target's centre, cut at the first block on the way. */
   @Nullable
   private static Vec3 aimPoint(ClientLevel level, LegionSuitEntity suit, float partialTick) {
      Entity target = suit.aimId() >= 0 ? level.getEntity(suit.aimId()) : null;
      if (target == null) {
         return null;
      }

      return target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
   }

   private static Vec3 blocked(ClientLevel level, LegionSuitEntity suit, Vec3 from, Vec3 to) {
      BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, suit));
      return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
   }

   // --- drawing ---

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || TRACKS.isEmpty() && FLASHES.isEmpty()) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
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
         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LegionSuitEntity suit && suit.mark() != null) {
               drawSuit(buffer, cameraPos, camera, level, suit, partialTick);
            }
         }

         for (Flash flash : FLASHES) {
            float fade = 1.0F - (flash.age()[0] + partialTick) / flash.life();
            if (fade > 0.0F) {
               PixelVfx.crossGlow(buffer, cameraPos, camera, flash.pos(), flash.size() * (0.6F + 0.4F * fade), flash.r(), flash.g(), flash.b(), (int)(230 * fade));
               PixelVfx.billboardPixel(buffer, cameraPos, camera, flash.pos(), flash.size() * 0.35F, 245, 252, 255, (int)(255 * fade));
            }
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

   private static void drawSuit(BufferBuilder buffer, Vec3 cameraPos, Camera camera, ClientLevel level, LegionSuitEntity suit, float partialTick) {
      LegionPhase phase = suit.phase();
      Vec3 body = suit.getPosition(partialTick).add(0.0, 1.0, 0.0);
      if (phase == LegionPhase.APPROACH && suit.clientPhaseAge() < LegionRules.SIGNAL_TICKS + 20) {
         // Sky signal: a star-like glint that stays readable at distance, brightening as the suit breaks in.
         double distance = body.distanceTo(cameraPos);
         float pulse = 0.7F + 0.3F * Mth.sin((suit.tickCount + partialTick) * 0.9F + suit.getId());
         float size = (float)Mth.clamp(distance * 0.018, 0.25, 2.2) * pulse;
         PixelVfx.crossGlow(buffer, cameraPos, camera, body, size, 170, 225, 255, 200);
         PixelVfx.billboardPixel(buffer, cameraPos, camera, body, size * 0.3F, 255, 255, 255, 255);
      }

      MarkId mark = suit.mark();
      Vec3 target = aimPoint(level, suit, partialTick);
      if (target == null) {
         return;
      }

      Points points = points(suit);
      if (suit.flag(LegionSuitEntity.FLAG_LASER) && mark == MarkId.MARK_7) {
         Vec3 end = blocked(level, suit, points.rightPalm, target);
         PixelVfx.beamDots(buffer, cameraPos, camera, points.rightPalm, end, 0.05F, 0.05F, 255, 60, 60, 200);
         PixelVfx.beamDots(buffer, cameraPos, camera, points.rightPalm.add(0.0, 0.03, 0.0), end, 0.08F, 0.025F, 120, 240, 255, 180);
         PixelVfx.billboardPixel(buffer, cameraPos, camera, end, 0.14F, 255, 90, 90, 220);
      }

      if (suit.flag(LegionSuitEntity.FLAG_PULSE)) {
         Vec3 end = blocked(level, suit, points.chest, target);
         float flicker = 0.85F + 0.15F * Mth.sin((suit.tickCount + partialTick) * 1.9F);
         PixelVfx.beamDots(buffer, cameraPos, camera, points.chest, end, 0.06F, 0.28F, 90, 170, 255, (int)(110 * flicker));
         PixelVfx.beamDots(buffer, cameraPos, camera, points.chest, end, 0.06F, 0.12F, 230, 250, 255, (int)(240 * flicker));
         PixelVfx.crossGlow(buffer, cameraPos, camera, points.chest, 0.35F, 170, 230, 255, 230);
         PixelVfx.crossGlow(buffer, cameraPos, camera, end, 0.42F, 170, 230, 255, 230);
      }
   }
}
