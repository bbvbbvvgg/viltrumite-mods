package dev.baranhan.viltrumitecore.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFlags;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderFx;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderMove;
import dev.baranhan.viltrumitecore.hero.ghostrider.GhostRiderRules;
import dev.baranhan.viltrumitecore.hero.ghostrider.bike.HellCycleEntity;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * World VFX of the Ghost Rider (client): the hellfire on his skull (volumetric: a crown of overlapping flame sprites
 * swept back by speed, never the vanilla fire overlay), burning fists, hellfire on bodies he set alight, the Hell Chain
 * (GhostRiderChains), the Penance Stare's twin streams, and the preset bursts (transformation, lash, hook, slam, wave,
 * verdict, ram, dash) as short-lived flame fields. Drawn after the particles. Render thread only.
 */
public final class GhostRiderVfx {
   /** Entity id -> client tick until which it burns with hellfire. */
   static final Int2IntOpenHashMap HELLFIRE = new Int2IntOpenHashMap();
   static int clientTicks;
   private static final List<Flame> FLAMES = new ArrayList<>();
   private static final int MAX_FLAMES = 600;
   private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
   private static final Vector3f AXIS = new Vector3f();
   private static final RandomSource RNG = RandomSource.create();
   private static boolean listening;

   private GhostRiderVfx() {
   }

   /** One transient flame of a burst: rises, drifts, flares and dies. */
   static final class Flame {
      double x, y, z;
      final double vx, vy, vz;
      final float size;
      final int born, life, phase, row;
      final int tint;

      Flame(double x, double y, double z, double vx, double vy, double vz, float size, int life, int row, int tint) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.size = size;
         this.born = clientTicks;
         this.life = life;
         this.phase = RNG.nextInt(64);
         this.row = row;
         this.tint = tint;
      }
   }

   public static void init() {
      if (!listening) {
         listening = true;
         HeroImpactFx.addPresetListener(GhostRiderVfx::onPreset);
      }
   }

   public static void receiveHellfire(int entityId, int ticks) {
      if (Minecraft.getInstance().level == null) {
         return;
      }
      if (ticks <= 0) {
         HELLFIRE.remove(entityId);
      } else {
         HELLFIRE.put(entityId, clientTicks + ticks);
      }
   }

   public static boolean burning(Entity entity) {
      return HELLFIRE.get(entity.getId()) > clientTicks;
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.isPaused()) {
         return;
      }
      clientTicks++;
      // moving flames, lifetimes
      Iterator<Flame> it = FLAMES.iterator();
      while (it.hasNext()) {
         Flame f = it.next();
         if (clientTicks - f.born > f.life) {
            it.remove();
         } else {
            f.x += f.vx;
            f.y += f.vy;
            f.z += f.vz;
         }
      }
      if (!HELLFIRE.isEmpty()) {
         HELLFIRE.int2IntEntrySet().removeIf(e -> e.getIntValue() <= clientTicks);
      }
      // smoke and embers off burning bodies, the Rider's skull and the transformation
      ClientLevel level = mc.level;
      if (!HELLFIRE.isEmpty() && clientTicks % 3 == 0) {
         for (Int2IntMap.Entry e : HELLFIRE.int2IntEntrySet()) {
            Entity entity = level.getEntity(e.getIntKey());
            if (entity != null && entity.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 48 * 48) {
               AABB box = entity.getBoundingBox();
               level.addParticle(ParticleTypes.SMOKE, Mth.lerp(RNG.nextDouble(), box.minX, box.maxX), box.maxY + 0.1,
                  Mth.lerp(RNG.nextDouble(), box.minZ, box.maxZ), 0.0, 0.03, 0.0);
            }
         }
      }
      for (AbstractClientPlayer p : level.players()) {
         HeroPublicSnapshot s = GhostRiderView.of(p);
         if (s == null || p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 64 * 64) {
            continue;
         }
         GhostRiderMove move = GhostRiderView.move(s);
         if (move == GhostRiderMove.TRANSFORM) {
            int t = s.actionElapsed();
            if (t >= 6 && t < GhostRiderRules.TRANSFORM_FLARE_TICK) {
               // the flesh smokes and spits embers before it catches
               for (int i = 0; i < 2; i++) {
                  level.addParticle(RNG.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, p.getX() + (RNG.nextDouble() - 0.5) * 0.6,
                     p.getY() + 0.6 + RNG.nextDouble() * 1.2, p.getZ() + (RNG.nextDouble() - 0.5) * 0.6, 0.0, 0.04, 0.0);
               }
            }
         } else if (GhostRiderView.rider(s) && clientTicks % 9 == 0 && !p.isPassenger() && p.getDeltaMovement().horizontalDistanceSqr() < 0.02
            && !(p == mc.player && mc.options.getCameraType().isFirstPerson())) {
            // a thin wisp off the top of the flames (sparse: dense vanilla smoke reads as black specks)
            Vec3 eye = p.getEyePosition();
            level.addParticle(ParticleTypes.SMOKE, eye.x + (RNG.nextDouble() - 0.5) * 0.2, eye.y + 0.95, eye.z + (RNG.nextDouble() - 0.5) * 0.2,
               0.0, 0.03, 0.0);
         }
      }
   }

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         HELLFIRE.clear();
         FLAMES.clear();
         GhostRiderAnchors.clear();
      }
   }

   // ------------------------------------------------------------------------------------------------ presets
   static void onPreset(FxPreset preset, Vec3 at, Vec3 target) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || preset.netId() < 720 || preset.netId() > 729) {
         return;
      }
      if (preset == GhostRiderFx.TRANSFORM) {
         // a column of hellfire round the body, a ring along the ground
         for (int i = 0; i < 26; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double r = 0.2 + RNG.nextDouble() * 0.6;
            add(at.x + Math.cos(a) * r, at.y - 0.9 + RNG.nextDouble() * 1.6, at.z + Math.sin(a) * r, Math.cos(a) * 0.04, 0.09 + RNG.nextDouble() * 0.06,
               Math.sin(a) * 0.04, 0.5F + RNG.nextFloat() * 0.5F, 10 + RNG.nextInt(8), HellfireDraw.BLAZE, 0);
         }
         ring(at.add(0, -1.0, 0), 1.0, 0.22, 22, 0.55F, 14);
      } else if (preset == GhostRiderFx.LASH || preset == GhostRiderFx.HOOK) {
         int n = preset == GhostRiderFx.LASH ? 8 : 6;
         for (int i = 0; i < n; i++) {
            add(at.x + (RNG.nextDouble() - 0.5) * 0.5, at.y + (RNG.nextDouble() - 0.5) * 0.6, at.z + (RNG.nextDouble() - 0.5) * 0.5,
               (RNG.nextDouble() - 0.5) * 0.08, 0.05 + RNG.nextDouble() * 0.05, (RNG.nextDouble() - 0.5) * 0.08, 0.3F + RNG.nextFloat() * 0.3F,
               6 + RNG.nextInt(5), HellfireDraw.TONGUE, 0);
         }
      } else if (preset == GhostRiderFx.SLAM) {
         double radius = Math.max(2.0, target.distanceTo(at));
         ring(at, 0.4, radius / 9.0, 30, 0.9F, 16);
         ring(at, 0.2, radius / 14.0, 16, 0.6F, 20);
         for (int i = 0; i < 10; i++) {
            add(at.x + (RNG.nextDouble() - 0.5), at.y, at.z + (RNG.nextDouble() - 0.5), 0.0, 0.12 + RNG.nextDouble() * 0.08, 0.0,
               0.6F + RNG.nextFloat() * 0.4F, 10, HellfireDraw.BLAZE, 0);
         }
      } else if (preset == GhostRiderFx.WAVE) {
         wave(at, target);
      } else if (preset == GhostRiderFx.VERDICT) {
         boolean innocent = innocentNear(mc.level, target);
         int n = innocent ? 6 : 24;
         for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            add(at.x + Math.cos(a) * 0.3, at.y - 0.2 - RNG.nextDouble() * 1.4, at.z + Math.sin(a) * 0.3, Math.cos(a) * 0.03, 0.08 + RNG.nextDouble() * 0.05,
               Math.sin(a) * 0.03, innocent ? 0.25F : 0.55F + RNG.nextFloat() * 0.4F, 12 + RNG.nextInt(8), HellfireDraw.BLAZE, innocent ? 2 : 0);
         }
      } else if (preset == GhostRiderFx.RAM) {
         for (int i = 0; i < 14; i++) {
            add(at.x + (RNG.nextDouble() - 0.5) * 0.8, at.y + RNG.nextDouble() * 1.2, at.z + (RNG.nextDouble() - 0.5) * 0.8,
               (RNG.nextDouble() - 0.5) * 0.2, 0.08 + RNG.nextDouble() * 0.08, (RNG.nextDouble() - 0.5) * 0.2, 0.45F + RNG.nextFloat() * 0.35F,
               8 + RNG.nextInt(6), HellfireDraw.BLAZE, 0);
         }
      } else if (preset == GhostRiderFx.BIKE_FORM) {
         ring(at, 0.6, 0.18, 26, 0.7F, 18);
         for (int i = 0; i < 18; i++) {
            double z = (RNG.nextDouble() - 0.5) * 2.0;
            add(at.x + (RNG.nextDouble() - 0.5) * 0.6, at.y + RNG.nextDouble() * 0.6, at.z + z, 0.0, 0.1 + RNG.nextDouble() * 0.05, 0.0,
               0.5F + RNG.nextFloat() * 0.4F, 10 + RNG.nextInt(8), HellfireDraw.BLAZE, 0);
         }
      } else if (preset == GhostRiderFx.DASH) {
         Vec3 d = target.subtract(at);
         int n = (int)Math.min(30, 6 + d.length() * 3);
         for (int i = 0; i < n; i++) {
            double k = i / (double)n;
            add(at.x + d.x * k + (RNG.nextDouble() - 0.5) * 0.4, at.y + d.y * k, at.z + d.z * k + (RNG.nextDouble() - 0.5) * 0.4, 0.0, 0.03, 0.0,
               0.45F + RNG.nextFloat() * 0.3F, 14 + RNG.nextInt(8) + (int)(k * 6), HellfireDraw.TONGUE, 0);
         }
      }
   }

   /** The verdict of the Rider whose eyes are at {@code eyes} (the preset carries no scale for listeners). */
   static boolean innocentNear(ClientLevel level, Vec3 eyes) {
      AbstractClientPlayer best = null;
      double bestD = 6.0 * 6.0;
      for (AbstractClientPlayer p : level.players()) {
         HeroPublicSnapshot s = GhostRiderView.of(p);
         double d = p.getEyePosition().distanceToSqr(eyes);
         if (s != null && d < bestD) {
            bestD = d;
            best = p;
         }
      }
      HeroPublicSnapshot s = best == null ? null : GhostRiderView.of(best);
      return s != null && GhostRiderView.flag(s, GhostRiderFlags.INNOCENT);
   }

   /** The Hellfire Wave: a cone of flame rolling out from the palms (the server's front speed and arc). */
   static void wave(Vec3 at, Vec3 target) {
      Vec3 axis = target.subtract(at);
      double reach = Math.max(1.0, axis.length());
      Vec3 dir = axis.scale(1.0 / reach);
      double half = Math.toRadians(GhostRiderRules.WAVE_ARC * 0.5);
      int rows = (int)Math.ceil(reach / 0.9);
      for (int r = 0; r < rows; r++) {
         double dist = 0.6 + r * 0.9;
         int delay = (int)(dist / GhostRiderRules.WAVE_SPEED);
         int across = 3 + (int)(dist * 1.1);
         for (int i = 0; i < across; i++) {
            double ang = (i / (double)(across - 1) - 0.5) * 2 * half + (RNG.nextDouble() - 0.5) * 0.08;
            double c = Math.cos(ang);
            double s = Math.sin(ang);
            double dx = dir.x * c - dir.z * s;
            double dz = dir.x * s + dir.z * c;
            Flame f = new Flame(at.x + dx * dist, at.y + dir.y * dist, at.z + dz * dist, dx * 0.05, 0.02, dz * 0.05,
               0.7F + RNG.nextFloat() * 0.5F + (float)(dist * 0.03), 14 + RNG.nextInt(8) + delay, HellfireDraw.BLAZE, 0);
            DELAY.add(new Delayed(f, delay));
         }
      }
   }

   record Delayed(Flame flame, int delay) {
   }

   private static final List<Delayed> DELAY = new ArrayList<>();

   static void ring(Vec3 c, double r0, double speed, int n, float size, int life) {
      for (int i = 0; i < n; i++) {
         double a = i * Math.PI * 2 / n + RNG.nextDouble() * 0.1;
         add(c.x + Math.cos(a) * r0, c.y + 0.05, c.z + Math.sin(a) * r0, Math.cos(a) * speed, 0.01, Math.sin(a) * speed, size * (0.8F + RNG.nextFloat() * 0.4F),
            life + RNG.nextInt(5), HellfireDraw.TONGUE, 0);
      }
   }

   static void add(double x, double y, double z, double vx, double vy, double vz, float size, int life, int row, int tint) {
      if (FLAMES.size() < MAX_FLAMES) {
         FLAMES.add(new Flame(x, y, z, vx, vy, vz, size, life, row, tint));
      }
   }

   // ------------------------------------------------------------------------------------------------ drawing
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      float partial = event.getPartialTick();
      float age = clientTicks + partial;
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      Matrix4f m = stack.last().pose();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      // release delayed wave flames whose front has arrived
      if (!DELAY.isEmpty()) {
         Iterator<Delayed> it = DELAY.iterator();
         while (it.hasNext()) {
            Delayed d = it.next();
            if (clientTicks - d.flame.born >= d.delay) {
               if (FLAMES.size() < MAX_FLAMES) {
                  FLAMES.add(d.flame);
               }
               it.remove();
            }
         }
      }
      GhostRiderChains.draw(level, cam, stack, buffers, partial);
      VertexConsumer vc = buffers.getBuffer(HellfireDraw.type());
      for (AbstractClientPlayer p : level.players()) {
         HeroPublicSnapshot s = GhostRiderView.of(p);
         if (s == null || p.distanceToSqr(cam) > 96 * 96 || p.isInvisible()) {
            continue;
         }
         float fire = GhostRiderView.fire(s, partial);
         boolean fp = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
         if (fire > 0.0F && !fp) {
            skull(vc, m, cam, p, s, fire, partial, age);
            fists(vc, m, cam, p, fire, partial, age);
         }
         if (GhostRiderView.flag(s, GhostRiderFlags.STARE)) {
            stare(vc, m, cam, level, p, s, partial, age);
         }
      }
      burningBodies(vc, m, cam, level, partial, age);
      bursts(vc, m, cam, partial, age);
      buffers.endBatch(HellfireDraw.type());
   }

   /** The skull's crown of hellfire: 14 roots, tall blazes on top, tongues at the back, swept back by speed. */
   static void skull(VertexConsumer vc, Matrix4f m, Vec3 cam, AbstractClientPlayer p, HeroPublicSnapshot s, float fire, float partial, float age) {
      Vec3 vel = velocity(p, partial);
      float speed = (float)vel.length();
      AXIS.set((float)(-vel.x * 2.4), 1.0F, (float)(-vel.z * 2.4)).normalize();
      Vec3[] roots = GhostRiderAnchors.flameRoots(p);
      Vec3 head = p.getEyePosition(partial).add(0.0, 0.18, 0.0);
      float stretch = 1.0F + Math.min(1.2F, speed * 1.4F);
      boolean stare = GhostRiderView.flag(s, GhostRiderFlags.STARE);
      float rage = stare || GhostRiderView.move(s) == GhostRiderMove.WAVE ? 1.35F : 1.0F;
      int n = GhostRiderAnchors.FLAME_ROOTS.length;
      for (int i = 0; i < n; i++) {
         Vec3 r;
         if (roots != null) {
            r = roots[i];
         } else {
            float[] fr = GhostRiderAnchors.FLAME_ROOTS[i];
            double yaw = Math.toRadians(Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot));
            double lx = -fr[0] / 16.0;
            double lz = fr[2] / 16.0;
            double cx = lx * Math.cos(yaw) - lz * Math.sin(yaw);
            double cz = lx * Math.sin(yaw) + lz * Math.cos(yaw);
            r = new Vec3(p.getX() + cx, p.getY() + (fr[1] - 24.0) / 16.0 + p.getEyeHeight() - 0.12, p.getZ() + cz);
         }
         float h0 = hash(i);
         boolean crown = i < 6 || i == 13;
         float h = (crown ? 0.62F : 0.42F) * (0.8F + 0.45F * h0) * HellfireDraw.flicker(age, i * 5) * fire * stretch * rage;
         float w = (crown ? 0.42F : 0.3F) * (0.85F + 0.3F * hash(i + 31)) * (0.75F + 0.25F * fire);
         int a = (int)(235 * fire);
         flame(vc, m, cam, r, AXIS, w, h, HellfireDraw.frame(age, i * 3), crown ? HellfireDraw.BLAZE : HellfireDraw.TONGUE, 255, 255, 255, a);
      }
      // a white-hot core in the middle of the crown and a dimmer outer shell: depth in the fire
      Vec3 core = roots != null ? roots[0] : head.add(0, 0.3, 0);
      flame(vc, m, cam, core, AXIS, 0.3F * fire, 0.5F * fire * stretch, HellfireDraw.frame(age, 7), HellfireDraw.TONGUE, 255, 255, 200, (int)(255 * fire));
      flame(vc, m, cam, core.add(0, -0.12, 0), AXIS, 0.85F * fire, 0.95F * fire * stretch * rage, HellfireDraw.frame(age, 11), HellfireDraw.BLAZE, 255, 120, 60,
         (int)(120 * fire));
   }

   /** Small tongues licking up from both fists. */
   static void fists(VertexConsumer vc, Matrix4f m, Vec3 cam, AbstractClientPlayer p, float fire, float partial, float age) {
      for (int side = 0; side < 2; side++) {
         Vec3 hnd = GhostRiderAnchors.hand(p, side == 0, partial);
         for (int k = 0; k < 2; k++) {
            float h = (0.22F + 0.08F * k) * HellfireDraw.flicker(age, side * 7 + k * 3) * fire;
            flame(vc, m, cam, hnd.add(0, -0.05 + k * 0.04, 0), UP, 0.16F + 0.04F * k, h, HellfireDraw.frame(age, side * 5 + k * 2), HellfireDraw.TONGUE, 255, 255,
               255, (int)(220 * fire));
         }
      }
   }

   /** The Penance Stare: twin streams of hellfire from his sockets into the soul's eyes; the soul's eyes burn. */
   static void stare(VertexConsumer vc, Matrix4f m, Vec3 cam, ClientLevel level, AbstractClientPlayer p, HeroPublicSnapshot s, float partial, float age) {
      Entity victim = level.getEntity(s.controlTargetId());
      if (victim == null) {
         return;
      }
      float t = GhostRiderView.elapsed(s, partial);
      float grow = GhostRiderView.clamp01((t - GhostRiderRules.STARE_LOCK * 0.5F) / 8.0F);
      if (grow <= 0.0F) {
         return;
      }
      Vec3 dst = victim.getEyePosition(partial);
      for (int side = 0; side < 2; side++) {
         Vec3 src = GhostRiderAnchors.eye(p, side == 0, partial);
         Vec3 d = dst.subtract(src);
         float len = (float)d.length();
         if (len < 0.2F) {
            continue;
         }
         AXIS.set((float)(d.x / len), (float)(d.y / len), (float)(d.z / len));
         float pulse = 0.85F + 0.15F * Mth.sin(age * 1.7F + side);
         flame(vc, m, cam, src, AXIS, 0.12F * pulse, len * grow, HellfireDraw.frame(age, side * 4), HellfireDraw.TONGUE, 255, 200, 140, (int)(230 * grow));
         flame(vc, m, cam, src, AXIS, 0.05F, len * grow, HellfireDraw.frame(age, side * 4 + 2), HellfireDraw.TONGUE, 255, 255, 230, (int)(255 * grow));
      }
      if (grow >= 1.0F) {
         for (int side = 0; side < 2; side++) {
            Vec3 look = victim.getViewVector(partial);
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize().scale(side == 0 ? 0.12 : -0.12);
            Vec3 eye = dst.add(look.scale(0.25)).add(right);
            flame(vc, m, cam, eye, UP, 0.14F, 0.22F * HellfireDraw.flicker(age, side * 9), HellfireDraw.frame(age, side * 3 + 1), HellfireDraw.TONGUE, 255, 255,
               255, 230);
         }
      }
   }

   /** Hellfire on burning bodies: tongues round the bounding box, bigger at the feet. */
   static void burningBodies(VertexConsumer vc, Matrix4f m, Vec3 cam, ClientLevel level, float partial, float age) {
      if (HELLFIRE.isEmpty()) {
         return;
      }
      for (Int2IntMap.Entry e : HELLFIRE.int2IntEntrySet()) {
         Entity entity = level.getEntity(e.getIntKey());
         if (entity == null || entity.distanceToSqr(cam) > 64 * 64) {
            continue;
         }
         int left = e.getIntValue() - clientTicks;
         float fade = GhostRiderView.clamp01(left / 10.0F);
         Vec3 pos = entity.getPosition(partial);
         float w = entity.getBbWidth();
         float hgt = entity.getBbHeight();
         int n = Mth.clamp((int)(4 + w * 6), 4, 12);
         int id = entity.getId();
         for (int i = 0; i < n; i++) {
            double a = (i / (double)n) * Math.PI * 2 + id;
            double rr = w * 0.42;
            float lift = (i % 3) / 3.0F * hgt * 0.55F;
            Vec3 b = pos.add(Math.cos(a) * rr, lift, Math.sin(a) * rr);
            float h = (0.35F + hgt * 0.35F) * (0.8F + 0.4F * hash(id + i)) * HellfireDraw.flicker(age, id + i * 3) * fade;
            flame(vc, m, cam, b, UP, 0.28F + w * 0.25F, h, HellfireDraw.frame(age, id + i * 2), i % 2 == 0 ? HellfireDraw.TONGUE : HellfireDraw.BLAZE, 255, 255,
               255, (int)(220 * fade));
         }
      }
   }

   static void bursts(VertexConsumer vc, Matrix4f m, Vec3 cam, float partial, float age) {
      for (Flame f : FLAMES) {
         float a = (clientTicks - f.born) + partial;
         float k = a / f.life;
         if (k >= 1.0F) {
            continue;
         }
         float grow = Math.min(1.0F, a / 3.0F + 0.3F) * (1.0F - k * k);
         float h = f.size * grow * HellfireDraw.flicker(age, f.phase);
         Vec3 b = new Vec3(f.x + f.vx * partial, f.y + f.vy * partial, f.z + f.vz * partial);
         int r = 255;
         int g = f.tint == 2 ? 230 : 255;
         int bl = f.tint == 2 ? 200 : 255;
         flame(vc, m, cam, b, UP, f.size * 0.7F, h, HellfireDraw.frame(age, f.phase), f.row, r, g, bl, (int)(230 * Math.min(1.0F, (1.0F - k) * 2.0F)));
      }
   }

   static void flame(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 base, Vector3f axis, float w, float h, int frame, int row, int r, int g, int b, int a) {
      float x = (float)(base.x - cam.x);
      float y = (float)(base.y - cam.y);
      float z = (float)(base.z - cam.z);
      HellfireDraw.flame(vc, m, x, y, z, axis, -x, -y, -z, w, h, frame, row, r, g, b, a);
   }

   static Vec3 velocity(Entity p, float partial) {
      Entity v = p.getVehicle() instanceof HellCycleEntity bike ? bike : p;
      return new Vec3(v.getX() - v.xo, v.getY() - v.yo, v.getZ() - v.zo);
   }

   /** Pure: stable 0-1 value per index. */
   static float hash(int i) {
      int h = i * 0x9E3779B1;
      h ^= h >>> 15;
      h *= 0x85EBCA77;
      h ^= h >>> 13;
      return (h & 0xFFFF) / 65535.0F;
   }
}
