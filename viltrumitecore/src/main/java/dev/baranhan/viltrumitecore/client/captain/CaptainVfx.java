package dev.baranhan.viltrumitecore.client.captain;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.captain.CaptainFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Vector3f;

/**
 * Captain / Avengers world VFX: vibranium sparks and star flashes, slam and landing rings, stun stars, repulsor
 * and unibeam beams, pistol tracers, Widow's Bite and shock arcs, grapple lines, explosions and thruster bursts.
 * Beams/arcs/lines are additive pixel billboards drawn after the particles; the rest are vanilla particles and
 * the shared {@link HeroImpactFx} ground rings. Camera shake falls off with distance. Client only.
 */
public final class CaptainVfx {
   static final DustParticleOptions STAR_BLUE = new DustParticleOptions(new Vector3f(0.35F, 0.55F, 1.0F), 1.2F);
   static final DustParticleOptions STAR_WHITE = new DustParticleOptions(new Vector3f(0.95F, 0.97F, 1.0F), 1.0F);
   static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85F, 0.12F, 0.15F), 1.0F);
   static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 0.9F);

   /** A straight or jagged additive line, optionally anchored to an entity at its start. */
   static final class Beam {
      Vec3 from;
      final Vec3 to;
      final int source;
      int life;
      final int max;
      final int r, g, b;
      final float size;
      final boolean jagged;
      final long seed;

      Beam(Vec3 from, Vec3 to, int source, int life, int r, int g, int b, float size, boolean jagged, long seed) {
         this.from = from;
         this.to = to;
         this.source = source;
         this.life = life;
         this.max = life;
         this.r = r;
         this.g = g;
         this.b = b;
         this.size = size;
         this.jagged = jagged;
         this.seed = seed;
      }
   }

   private static final List<Beam> BEAMS = new ArrayList<>();
   private static final Map<Integer, Integer> STUNNED = new HashMap<>();
   private static final Map<Integer, Integer> SHOCKED = new HashMap<>();
   private static final RandomSource RANDOM = RandomSource.create();

   private CaptainVfx() {
   }

   public static void play(ClientLevel level, int type, int sourceId, int arg, Vec3 a, Vec3 b, int[] ids) {
      Entity source = level.getEntity(sourceId);
      switch (type) {
         case CaptainFx.CONTACT -> contact(level, arg, a, sourceId);
         case CaptainFx.PARRY -> {
            burst(level, ParticleTypes.END_ROD, a, 14, 0.25);
            burst(level, STAR_WHITE, a, 10, 0.4);
            level.addParticle(ParticleTypes.FLASH, a.x, a.y, a.z, 0, 0, 0);
            shake(a, 0.5F, 12);
         }
         case CaptainFx.BLOCK -> {
            burst(level, ParticleTypes.CRIT, a, 8, 0.3);
            burst(level, ParticleTypes.ELECTRIC_SPARK, a, 4, 0.2);
            shake(a, 0.18F, 8);
         }
         case CaptainFx.RICOCHET -> {
            burst(level, ParticleTypes.ELECTRIC_SPARK, a, 10, 0.35);
            burst(level, ParticleTypes.CRIT, a, 8, 0.4);
            burst(level, STAR_BLUE, a, 4, 0.2);
         }
         case CaptainFx.CATCH -> {
            ring(level, STAR_WHITE, a, 0.6, 10);
            burst(level, ParticleTypes.CRIT, a, 4, 0.15);
         }
         case CaptainFx.SLAM -> {
            float r = (float)Math.max(2.0, b.x);
            HeroImpactFx.shockwaveVisual(a, r);
            dust(level, a, r, 40);
            level.addParticle(ParticleTypes.EXPLOSION, a.x, a.y + 0.3, a.z, 0, 0, 0);
            if (arg == 1) {
               ring(level, STAR_BLUE, a.add(0, 0.2, 0), r, 36);
               ring(level, ParticleTypes.ELECTRIC_SPARK, a.add(0, 0.2, 0), r * 0.7, 24);
               level.addParticle(ParticleTypes.FLASH, a.x, a.y + 0.5, a.z, 0, 0, 0);
            }
            shake(a, arg == 1 ? 1.4F : 1.0F, 24);
         }
         case CaptainFx.RUSH -> {
            burst(level, ParticleTypes.CLOUD, a, 3, 0.05);
            if (source != null) {
               trail(level, ParticleTypes.CRIT, source.position().add(0, 1.0, 0), a, 4);
            }
         }
         case CaptainFx.FINISHER -> {
            float r = (float)Math.max(1.5, b.x);
            HeroImpactFx.shockwaveVisual(a, r);
            dust(level, a, r, 24);
            ring(level, STAR_WHITE, a.add(0, 0.2, 0), r, 24);
            shake(a, 0.8F, 18);
         }
         case CaptainFx.ROLL -> trail(level, ParticleTypes.POOF, a, b, 6);
         case CaptainFx.CHARGED -> {
            Vec3 at = source != null ? source.position().add(0, 1.0, 0) : a;
            ring(level, STAR_BLUE, at, 0.9, 18);
            burst(level, ParticleTypes.END_ROD, at, 6, 0.08);
         }
         case CaptainFx.STUN -> {
            for (int id : ids) {
               STUNNED.merge(id, Math.max(1, arg), Math::max);
            }
         }
         case CaptainFx.ASSEMBLE -> {
            level.addParticle(ParticleTypes.FLASH, a.x, a.y + 1.5, a.z, 0, 0, 0);
            ring(level, STAR_WHITE, a.add(0, 0.1, 0), 3.0, 48);
            ring(level, STAR_BLUE, a.add(0, 0.1, 0), 2.2, 36);
            ring(level, RED, a.add(0, 0.1, 0), 1.4, 24);
            for (int i = 0; i < 24; i++) {
               level.addParticle(ParticleTypes.END_ROD, a.x + (RANDOM.nextDouble() - 0.5) * 0.4, a.y + 2.0, a.z + (RANDOM.nextDouble() - 0.5) * 0.4,
                  0, 0.15 + RANDOM.nextDouble() * 0.2, 0);
            }
            shake(a, 0.35F, 24);
         }
         case CaptainFx.THROW -> {
            level.addParticle(ParticleTypes.SWEEP_ATTACK, a.x, a.y, a.z, 0, 0, 0);
            burst(level, ParticleTypes.CLOUD, a, 2, 0.02);
         }
         case CaptainFx.REPULSOR -> {
            BEAMS.add(new Beam(a, b, -1, 4, 170, 225, 255, 0.07F, false, 0));
            if (arg == 1) {
               BEAMS.add(new Beam(a.add(0, 0.1, 0), b, -1, 4, 170, 225, 255, 0.07F, false, 0));
            }
            burst(level, ParticleTypes.END_ROD, b, 6, 0.1);
            burst(level, ParticleTypes.CRIT, b, 4, 0.2);
         }
         case CaptainFx.UNIBEAM -> {
            BEAMS.add(new Beam(a, b, -1, 3, 200, 240, 255, 0.18F, false, 0));
            burst(level, ParticleTypes.END_ROD, b, 4, 0.15);
            if (RANDOM.nextInt(3) == 0) {
               level.addParticle(ParticleTypes.FLASH, b.x, b.y, b.z, 0, 0, 0);
            }
         }
         case CaptainFx.EXPLOSION -> {
            float r = (float)Math.max(1.0, b.x);
            level.addParticle(r > 2.5F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, a.x, a.y, a.z, 0, 0, 0);
            burst(level, ParticleTypes.LARGE_SMOKE, a, 8, 0.12);
            burst(level, ParticleTypes.FLAME, a, 10, 0.18);
            shake(a, Math.min(1.2F, 0.35F * r), 28);
         }
         case CaptainFx.GROUND -> {
            float r = (float)Math.max(1.5, b.x);
            HeroImpactFx.shockwaveVisual(a, r);
            dust(level, a, r, arg == 0 ? 60 : 30);
            if (arg == 3) {
               ring(level, ParticleTypes.ELECTRIC_SPARK, a.add(0, 0.2, 0), r, 40);
               level.addParticle(ParticleTypes.FLASH, a.x, a.y + 0.5, a.z, 0, 0, 0);
            } else if (arg == 1) {
               ring(level, ParticleTypes.FLAME, a.add(0, 0.1, 0), r * 0.6, 16);
            }
            shake(a, arg == 0 ? 1.6F : 0.7F, arg == 0 ? 36 : 20);
         }
         case CaptainFx.BITE -> {
            for (int id : ids) {
               SHOCKED.merge(id, 12, Math::max);
            }
            if (source != null) {
               for (int id : ids) {
                  Entity t = level.getEntity(id);
                  if (t != null) {
                     BEAMS.add(new Beam(source.position().add(0, 1.1, 0), t.position().add(0, t.getBbHeight() * 0.5, 0), sourceId, 5,
                        120, 190, 255, 0.04F, true, RANDOM.nextLong()));
                  }
               }
            }
         }
         case CaptainFx.SHOT -> {
            BEAMS.add(new Beam(a, b, -1, 2, 255, 220, 140, 0.03F, false, 0));
            level.addParticle(ParticleTypes.SMOKE, a.x, a.y, a.z, 0, 0.02, 0);
            burst(level, ParticleTypes.CRIT, b, 3, 0.15);
         }
         case CaptainFx.GRAPPLE -> {
            Vec3 from = source != null ? source.position().add(0, 1.3, 0) : b;
            BEAMS.add(new Beam(from, a, sourceId, Math.max(2, arg), 60, 60, 66, 0.02F, false, 0));
         }
         case CaptainFx.SHOCK -> {
            BEAMS.add(new Beam(a, b, -1, 5, 140, 200, 255, 0.045F, true, RANDOM.nextLong()));
            burst(level, ParticleTypes.ELECTRIC_SPARK, b, 8, 0.25);
         }
         case CaptainFx.THRUST -> {
            burst(level, ParticleTypes.FLAME, a, 10, 0.12);
            burst(level, ParticleTypes.CLOUD, a, 6, 0.1);
            shake(a, 0.25F, 14);
         }
         default -> {
         }
      }
   }

   private static void contact(ClientLevel level, int kind, Vec3 a, int sourceId) {
      switch (kind) {
         case 0 -> { // shield: vibranium clang
            burst(level, ParticleTypes.CRIT, a, 10, 0.35);
            burst(level, ParticleTypes.ELECTRIC_SPARK, a, 5, 0.25);
            burst(level, STAR_WHITE, a, 3, 0.15);
            shake(a, 0.22F, 8);
         }
         case 1 -> burst(level, ParticleTypes.CRIT, a, 5, 0.2); // fist
         case 2 -> { // kick
            burst(level, ParticleTypes.CRIT, a, 7, 0.3);
            level.addParticle(ParticleTypes.SWEEP_ATTACK, a.x, a.y, a.z, 0, 0, 0);
            shake(a, 0.2F, 8);
         }
         case 3 -> { // counter / return strike
            level.addParticle(ParticleTypes.SWEEP_ATTACK, a.x, a.y, a.z, 0, 0, 0);
            burst(level, ParticleTypes.CRIT, a, 14, 0.45);
            burst(level, STAR_BLUE, a, 8, 0.3);
            level.addParticle(ParticleTypes.FLASH, a.x, a.y, a.z, 0, 0, 0);
            shake(a, 0.6F, 12);
         }
         default -> { // rush
            burst(level, ParticleTypes.CRIT, a, 10, 0.4);
            burst(level, ParticleTypes.CLOUD, a, 4, 0.1);
            shake(a, 0.45F, 12);
         }
      }
   }

   // --- particle helpers ---

   static void burst(ClientLevel level, ParticleOptions p, Vec3 at, int n, double speed) {
      for (int i = 0; i < n; i++) {
         double vx = (RANDOM.nextDouble() - 0.5) * 2 * speed;
         double vy = (RANDOM.nextDouble() - 0.3) * 2 * speed;
         double vz = (RANDOM.nextDouble() - 0.5) * 2 * speed;
         level.addParticle(p, at.x, at.y, at.z, vx, vy, vz);
      }
   }

   static void ring(ClientLevel level, ParticleOptions p, Vec3 at, double radius, int n) {
      for (int i = 0; i < n; i++) {
         double ang = Math.PI * 2 * i / n;
         double cx = Math.cos(ang), cz = Math.sin(ang);
         level.addParticle(p, at.x + cx * radius, at.y, at.z + cz * radius, cx * 0.08, 0.02, cz * 0.08);
      }
   }

   static void trail(ClientLevel level, ParticleOptions p, Vec3 from, Vec3 to, int n) {
      for (int i = 0; i <= n; i++) {
         Vec3 q = from.lerp(to, i / (double)n);
         level.addParticle(p, q.x, q.y, q.z, 0, 0.01, 0);
      }
   }

   /** Ground debris of the block under the point, thrown outward in a ring. */
   static void dust(ClientLevel level, Vec3 at, float radius, int n) {
      BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
      BlockState state = level.getBlockState(below);
      if (state.isAir()) {
         state = level.getBlockState(below.below());
      }
      ParticleOptions p = state.isAir() ? ParticleTypes.CLOUD : new BlockParticleOption(ParticleTypes.BLOCK, state);
      for (int i = 0; i < n; i++) {
         double ang = RANDOM.nextDouble() * Math.PI * 2;
         double d = RANDOM.nextDouble() * radius;
         double cx = Math.cos(ang), cz = Math.sin(ang);
         level.addParticle(p, at.x + cx * d, at.y + 0.1, at.z + cz * d, cx * 0.25, 0.25 + RANDOM.nextDouble() * 0.3, cz * 0.25);
      }
      for (int i = 0; i < n / 4; i++) {
         double ang = RANDOM.nextDouble() * Math.PI * 2;
         level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + Math.cos(ang) * radius * 0.6, at.y + 0.1, at.z + Math.sin(ang) * radius * 0.6,
            0, 0.02, 0);
      }
   }

   static void shake(Vec3 at, float power, double range) {
      CameraShake.addAt(at, power, range);
   }

   // --- tick / render ---

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      CaptainMotion.tick();
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.isPaused()) {
         return;
      }
      for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
         Beam beam = it.next();
         if (--beam.life <= 0) {
            it.remove();
         } else if (beam.source >= 0) {
            Entity e = level.getEntity(beam.source);
            if (e != null) {
               beam.from = e.position().add(0, e.getBbHeight() * 0.7, 0);
            }
         }
      }
      tickMarks(level, STUNNED, true);
      tickMarks(level, SHOCKED, false);
   }

   private static void tickMarks(ClientLevel level, Map<Integer, Integer> marks, boolean stars) {
      for (Iterator<Map.Entry<Integer, Integer>> it = marks.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<Integer, Integer> m = it.next();
         Entity e = level.getEntity(m.getKey());
         int left = m.getValue() - 1;
         if (e == null || !e.isAlive() || left <= 0) {
            it.remove();
            continue;
         }
         m.setValue(left);
         if (stars) {
            if (left % 3 == 0) {
               double ang = (e.tickCount % 20) / 20.0 * Math.PI * 2;
               for (int k = 0; k < 3; k++) {
                  double a = ang + k * Math.PI * 2 / 3;
                  level.addParticle(k == 0 ? GOLD : STAR_WHITE, e.getX() + Math.cos(a) * 0.45, e.getY() + e.getBbHeight() + 0.25, e.getZ() + Math.sin(a) * 0.45,
                     0, 0, 0);
               }
            }
         } else {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, e.getX() + (RANDOM.nextDouble() - 0.5) * e.getBbWidth(),
               e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + (RANDOM.nextDouble() - 0.5) * e.getBbWidth(), 0, 0, 0);
         }
      }
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         BEAMS.clear();
         STUNNED.clear();
         SHOCKED.clear();
         CaptainMotion.clear();
      }
   }

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      PoseStack mv = RenderSystem.getModelViewStack();
      mv.pushPose();
      mv.setIdentity();
      PixelVfx.levelView(mv, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buffer = tess.getBuilder();
      try {
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         float partial = event.getPartialTick();
         for (Beam beam : BEAMS) {
            float f = Mth.clamp((beam.life - partial) / beam.max, 0.0F, 1.0F);
            int alpha = (int)(220 * f);
            if (beam.jagged) {
               RandomSource rs = RandomSource.create(beam.seed + beam.life);
               Vec3 prev = beam.from;
               int seg = Math.max(3, (int)(beam.from.distanceTo(beam.to) * 3));
               for (int i = 1; i <= seg; i++) {
                  Vec3 next = beam.from.lerp(beam.to, i / (double)seg);
                  if (i < seg) {
                     next = next.add((rs.nextDouble() - 0.5) * 0.35, (rs.nextDouble() - 0.5) * 0.35, (rs.nextDouble() - 0.5) * 0.35);
                  }
                  PixelVfx.beamDots(buffer, cam, camera, prev, next, beam.size, beam.size, beam.r, beam.g, beam.b, alpha);
                  prev = next;
               }
            } else {
               PixelVfx.beamDots(buffer, cam, camera, beam.from, beam.to, beam.size * 0.8F, beam.size, beam.r, beam.g, beam.b, alpha);
               PixelVfx.beamDots(buffer, cam, camera, beam.from, beam.to, beam.size * 0.8F, beam.size * 0.45F, 255, 255, 255, alpha);
            }
         }
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         mv.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }
}
