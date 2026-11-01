package dev.baranhan.viltrumitecore.client.thanos;

import static dev.baranhan.viltrumitecore.client.thanos.ThanosFxDraw.*;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosMove;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosPhase;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSceneTimeline;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosSounds;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Mind;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Power;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Reality;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStoneRules.Time;
import dev.baranhan.viltrumiteflight.client.util.ShaderCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Vector3f;

/**
 * Client effects of the Thanos event, drawn from the server's {@link ThanosFxS2CPacket}s with the
 * shared pixel vocabulary (PR #45 approach: POSITION_COLOR billboards and flat quads, dust first,
 * then everything hot additively; mirages and afterimages are ghosts of his own rig). Also the
 * presentation the server does not send: footfalls in step with his walk cycle, his hurt voice,
 * stone embers and bloom at the gauntlet sockets, ash from his defeat, the local afflictions
 * (Mind sway and false targets, Soul tether, Time dome, Reality zone tints).
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class ThanosFx {
   static final int PURPLE = ThanosStone.POWER.rgb;
   static final int BLUE = ThanosStone.SPACE.rgb;
   static final int RED = ThanosStone.REALITY.rgb;
   static final int GREEN = ThanosStone.TIME.rgb;
   static final int ORANGE = ThanosStone.SOUL.rgb;
   static final int YELLOW = ThanosStone.MIND.rgb;
   static final int HOT = 0xFFF2E6;
   private static final int MAX_MOTES = 2200;
   private static final double RANGE = 128.0;
   private static final List<Fx> FX = new ArrayList<>();
   private static final List<Mote> MOTES = new ArrayList<>();
   static final Map<Integer, Telegraph> TELEGRAPHS = new HashMap<>();
   private static final Map<Integer, Walker> WALKERS = new HashMap<>();
   static final RandomSource RNG = RandomSource.create();
   static long ticks;
   private static int disorient;
   private static int disorientMax = 1;
   private static int tether;
   private static int tetherMax = 1;
   private static boolean tetherReal;
   private static int falseLeft;
   private static int falseMax = 1;
   private static int falseCount;
   private static long falseSeed;
   private static int falseThanos = -1;
   private static int resist;

   private ThanosFx() {
   }

   /** One live effect from a packet. */
   static final class Fx {
      final int kind;
      final int a;
      final int b;
      final Vec3 pos;
      final Vec3 dir;
      final float power;
      final int extra;
      final long seed;
      int age;
      int life;
      @Nullable
      List<float[]> lines;

      Fx(ThanosFxS2CPacket p, int life) {
         this.kind = p.kind();
         this.a = p.a();
         this.b = p.b();
         this.pos = new Vec3(p.x(), p.y(), p.z());
         this.dir = new Vec3(p.dx(), p.dy(), p.dz());
         this.power = p.power();
         this.extra = p.extra();
         this.seed = RNG.nextLong();
         this.life = Math.max(1, life);
      }

      float t(float partial) {
         return Mth.clamp((this.age + partial) / this.life, 0.0F, 1.0F);
      }

      float time(float partial) {
         return this.age + partial;
      }
   }

   /** A small world particle: billboard pixel with velocity, drag, gravity and a size ramp. */
   static final class Mote {
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      float size0;
      float size1;
      int rgb;
      int alpha;
      int age;
      int life;
      boolean glow;
      float gravity;
      float drag = 0.92F;
   }

   /** A move being charged: what, at whom, for how long. */
   record Telegraph(ThanosMove move, int target, Vec3 aim, int ticks, long start) {
   }

   /** Per-Thanos presentation bookkeeping: footfalls and hurt voice. */
   static final class Walker {
      long step = Long.MIN_VALUE;
      int react = Integer.MIN_VALUE;
      long lastHurt = -100L;
      boolean left;
   }

   // ---- packets ---------------------------------------------------------------------------------

   public static void accept(ThanosFxS2CPacket p) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null) {
         return;
      }

      Vec3 at = new Vec3(p.x(), p.y(), p.z());
      Vec3 dir = new Vec3(p.dx(), p.dy(), p.dz());
      boolean mine = p.b() == mc.player.getId();
      switch (p.kind()) {
         case ThanosFxS2CPacket.TELEGRAPH -> {
            ThanosMove move = ThanosMove.byId(p.extra());
            if (move != null) {
               TELEGRAPHS.put(p.a(), new Telegraph(move, p.b(), at, Math.max(1, (int)p.power()), ticks));
            }
         }
         case ThanosFxS2CPacket.MELEE_HIT -> {
            add(p, 10);
            if (p.power() > 0.0F) {
               burst(at, 18, 0.45, HOT, 0.08F, 10, true, 0.1);
               burst(at, 10, 0.3, PURPLE, 0.06F, 12, true, 0.0);
               groundDust(level, at.subtract(0.0, 1.0, 0.0), 1.4, 12);
               CameraShake.addAt(at, 0.55F + 0.15F * Math.min(3.0F, p.power()), 22.0);
            }
         }
         case ThanosFxS2CPacket.SLAM -> {
            add(p, 24);
            Vec3 ground = new Vec3(at.x, groundY(level, at.x, at.z, at.y) + 0.05, at.z);
            groundDust(level, ground, p.power() * 0.7, 40);
            debris(level, ground, 18, 0.55);
            burst(ground.add(0.0, 0.3, 0.0), 16, 0.5, p.extra() == 1 ? PURPLE : HOT, 0.07F, 12, true, 0.2);
            CameraShake.addAt(ground, p.extra() == 1 ? 1.5F : 1.0F, 34.0);
         }
         case ThanosFxS2CPacket.POWER_PUNCH -> {
            add(p, 16);
            burst(at, 40, 0.7, PURPLE, 0.09F, 14, true, 0.0);
            burst(at, 18, 0.5, HOT, 0.07F, 8, true, 0.0);
            groundDust(level, new Vec3(at.x, groundY(level, at.x, at.z, at.y) + 0.05, at.z), 3.0, 30);
            if (p.extra() == 1) {
               debris(level, at, 26, 0.7);
            }

            CameraShake.addAt(at, 1.6F, 40.0);
            if (mc.player.position().distanceTo(at) < 6.0) {
               FlashOverlay.flash(0.2F, 5);
            }
         }
         case ThanosFxS2CPacket.POWER_WAVE -> add(p, ringLife(p.power(), Power.WAVE_SPEED) + 8);
         case ThanosFxS2CPacket.FISSURES -> {
            Fx f = add(p, Power.FISSURE_LIFE);
            f.lines = fissures(level, at, dir, p.power(), p.extra());
         }
         case ThanosFxS2CPacket.SPACE_PORTAL -> add(p, Math.max(10, p.extra()));
         case ThanosFxS2CPacket.SPACE_STEP -> {
            add(p, 14);
            Vec3 c = at.add(0.0, 1.9, 0.0);
            if (p.extra() == 0) {
               implode(c, 30, 2.4, BLUE, 10);
            } else {
               burst(c, 34, 0.55, BLUE, 0.07F, 14, true, 0.0);
               burst(c, 12, 0.35, HOT, 0.05F, 8, true, 0.0);
               CameraShake.addAt(c, 0.6F, 24.0);
            }
         }
         case ThanosFxS2CPacket.SPACE_PULL -> add(p, Math.max(6, p.extra()));
         case ThanosFxS2CPacket.SPACE_WARD -> add(p, Math.max(10, p.extra()));
         case ThanosFxS2CPacket.SPACE_REDIRECT -> {
            add(p, 12);
            burst(at.add(0.0, 1.0, 0.0), 14, 0.35, BLUE, 0.05F, 10, true, 0.0);
         }
         case ThanosFxS2CPacket.REALITY_ZONE -> add(p, Math.max(8, p.extra()));
         case ThanosFxS2CPacket.REALITY_MIRAGE -> add(p, Math.max(10, p.extra()));
         case ThanosFxS2CPacket.MIRAGE_POP -> {
            add(p, 14);
            popMirage(at);
            boolean big = p.power() > 2.0F;
            burst(at.add(0.0, 1.8, 0.0), big ? 50 : 26, big ? 0.7 : 0.45, RED, 0.08F, 14, true, 0.05);
            burst(at.add(0.0, 1.8, 0.0), 12, 0.3, 0x2A0006, 0.1F, 18, false, 0.0);
            if (big) {
               CameraShake.addAt(at, 1.0F, 26.0);
            }
         }
         case ThanosFxS2CPacket.REALITY_SHARD -> {
            add(p, 12);
            burst(at.add(dir), 12, 0.35, RED, 0.06F, 10, true, 0.0);
         }
         case ThanosFxS2CPacket.TIME_DOME -> add(p, Math.max(8, p.extra()));
         case ThanosFxS2CPacket.TIME_REWIND -> {
            add(p, 22);
            implode(at.add(0.0, 1.9, 0.0), 24, 2.0, GREEN, 12);
            burst(at.add(dir).add(0.0, 1.9, 0.0), 24, 0.4, GREEN, 0.06F, 14, true, 0.0);
         }
         case ThanosFxS2CPacket.MIND_GAZE_CHARGE -> add(p, Math.max(6, p.extra()));
         case ThanosFxS2CPacket.MIND_GAZE_HIT -> {
            if (p.b() < 0) {
               add(p, 10);
            } else if (mine) {
               disorient = Math.max(disorient, p.extra());
               disorientMax = Math.max(1, disorient);
               FlashOverlay.flash(0.18F, 6);
            }
         }
         case ThanosFxS2CPacket.MIND_RESIST -> {
            add(p, 12);
            if (mine) {
               resist = 12;
            }
         }
         case ThanosFxS2CPacket.MIND_PULSE -> add(p, ringLife(p.power(), Mind.RING_SPEED) + Mind.RING_GAP * Math.max(0, p.extra() - 1) + 6);
         case ThanosFxS2CPacket.MIND_FALSE_TARGETS -> {
            if (mine) {
               falseLeft = Math.max(falseLeft, p.extra());
               falseMax = Math.max(1, falseLeft);
               falseCount = Math.max(1, (int)p.power());
               falseSeed = RNG.nextLong();
               falseThanos = p.a();
            }
         }
         case ThanosFxS2CPacket.SOUL_TETHER -> {
            add(p, Math.max(6, p.extra()));
            if (mine) {
               tether = Math.max(tether, p.extra());
               tetherMax = Math.max(1, tether);
               tetherReal = p.power() > 0.5F;
            }
         }
         case ThanosFxS2CPacket.SOUL_TETHER_BREAK -> {
            for (Fx f : FX) {
               if (f.kind == ThanosFxS2CPacket.SOUL_TETHER && f.b == p.b() && f.age < f.life - 6) {
                  f.life = f.age + 6;
               }
            }

            if (mine) {
               tether = Math.min(tether, 6);
            }

            add(p, 12);
            burst(at.add(0.0, 1.2, 0.0), 22, 0.4, ORANGE, 0.06F, 12, true, 0.0);
         }
         case ThanosFxS2CPacket.SOUL_DRAIN -> drain(level, p, at, dir);
         case ThanosFxS2CPacket.SOUL_NOVA -> {
            add(p, 30);
            burst(at.add(0.0, 1.5, 0.0), 40, 0.6, ORANGE, 0.08F, 18, true, 0.15);
            burst(at.add(0.0, 1.5, 0.0), 16, 0.35, HOT, 0.06F, 10, true, 0.0);
            CameraShake.addAt(at, 1.2F, 32.0);
            if (mc.player.position().distanceTo(at) < p.power()) {
               FlashOverlay.flash(0.25F, 8);
            }
         }
         case ThanosFxS2CPacket.ARRIVAL -> {
            add(p, Math.max(20, p.extra()));
            CameraShake.addAt(at, 1.2F, 64.0);
         }
         case ThanosFxS2CPacket.STUN -> {
            add(p, Math.max(10, (int)p.power()));
            burst(at, 20, 0.35, HOT, 0.06F, 10, true, 0.2);
         }
         case ThanosFxS2CPacket.CLEAR -> {
            FX.removeIf(f -> f.a == p.a() || f.kind == ThanosFxS2CPacket.REALITY_MIRAGE);
            TELEGRAPHS.remove(p.a());
         }
         default -> ThanosWarFx.accept(p, level, at, dir);
      }
   }

   private static Fx add(ThanosFxS2CPacket p, int life) {
      Fx f = new Fx(p, life);
      FX.add(f);
      return f;
   }

   static int ringLife(float max, double speed) {
      return Mth.clamp(Mth.ceil(max / Math.max(0.05, speed)), 4, 120);
   }

   private static void popMirage(Vec3 at) {
      Fx best = null;
      double bestD = 9.0;
      for (Fx f : FX) {
         if (f.kind == ThanosFxS2CPacket.REALITY_MIRAGE && f.age < f.life - 4) {
            double d = f.pos.distanceToSqr(at);
            if (d < bestD) {
               bestD = d;
               best = f;
            }
         }
      }

      if (best != null) {
         best.life = best.age + 4;
      }
   }

   private static void drain(ClientLevel level, ThanosFxS2CPacket p, Vec3 at, Vec3 dir) {
      Vec3 to = at.add(dir);
      ThanosRenderer.Anchors anchors = ThanosRenderer.fresh(p.b());
      if (anchors != null && anchors.stones()[ThanosStone.SOUL.ordinal()] != null) {
         to = anchors.stones()[ThanosStone.SOUL.ordinal()];
      }

      boolean real = p.power() > 0.5F;
      for (int i = 0; i < (real ? 14 : 6); i++) {
         Mote m = mote(at.add(RNG.nextGaussian() * 0.3, RNG.nextGaussian() * 0.4, RNG.nextGaussian() * 0.3), real ? ORANGE : 0xFFC890, 0.05F, 0.02F,
            12 + RNG.nextInt(6), true);
         if (m == null) {
            break;
         }

         Vec3 v = to.subtract(m.x, m.y, m.z).scale(1.0 / (m.life * 0.62));
         m.vx = v.x;
         m.vy = v.y;
         m.vz = v.z;
         m.drag = 1.0F;
      }
   }

   // ---- ticking ---------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null) {
         if (!FX.isEmpty() || !MOTES.isEmpty()) {
            clear();
         }

         return;
      }

      if (mc.isPaused() || dev.baranhan.viltrumitecore.client.hero.WorldPauseClient.active()) {
         return; // the world is stopped: so are his effects
      }

      ticks++;
      disorient = Math.max(0, disorient - 1);
      tether = Math.max(0, tether - 1);
      falseLeft = Math.max(0, falseLeft - 1);
      resist = Math.max(0, resist - 1);
      for (Iterator<Fx> it = FX.iterator(); it.hasNext(); ) {
         Fx f = it.next();
         if (++f.age >= f.life) {
            it.remove();
         } else {
            emit(level, f);
         }
      }

      ThanosWarFx.tick(level);

      TELEGRAPHS.values().removeIf(t -> ticks - t.start() > t.ticks() + 4);
      for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
         Mote m = it.next();
         if (++m.age >= m.life) {
            it.remove();
            continue;
         }

         m.x += m.vx;
         m.y += m.vy;
         m.z += m.vz;
         m.vx *= m.drag;
         m.vy = m.vy * m.drag - m.gravity;
         m.vz *= m.drag;
      }

      Vec3 eye = mc.player.getEyePosition();
      for (Entity e : level.entitiesForRendering()) {
         if (e instanceof ThanosEntity t && !t.isRemoved() && t.position().distanceToSqr(eye) < RANGE * RANGE) {
            presence(level, t);
         }
      }

      if ((ticks & 127) == 0) {
         WALKERS.keySet().removeIf(id -> !(level.getEntity(id) instanceof ThanosEntity));
      }
   }

   /** Footfalls, hurt voice, stone embers, charge motes and ash of one Thanos. */
   private static void presence(ClientLevel level, ThanosEntity t) {
      Walker w = WALKERS.computeIfAbsent(t.getId(), id -> new Walker());
      float walk = t.clientWalk;
      long step = (long)Math.floor(t.clientGait * 2.0);
      if (w.step == Long.MIN_VALUE) {
         w.step = step;
      }

      if (step != w.step) {
         w.step = step;
         if (walk > 0.12F && t.state() == ThanosEntity.STATE_FIGHT) {
            footfall(level, t, walk, w.left);
            w.left = !w.left;
         }
      }

      if (w.react == Integer.MIN_VALUE) {
         w.react = t.clientReactStart;
      }

      if (t.clientReactStart != w.react) {
         w.react = t.clientReactStart;
         if (t.reaction() == ThanosEntity.REACT_HURT && ticks - w.lastHurt > 9L) {
            w.lastHurt = ticks;
            level.playLocalSound(t.getX(), t.getY() + 2.6, t.getZ(), ThanosSounds.HURT.get(), SoundSource.HOSTILE, 1.3F,
               0.9F + RNG.nextFloat() * 0.15F, false);
         }
      }

      ThanosRenderer.Anchors anchors = ThanosRenderer.fresh(t.getId());
      if (anchors == null) {
         return;
      }

      boolean late = t.phase() == ThanosPhase.GAUNTLET || ThanosClient.finalMinute();
      for (int i = 0; i < anchors.stones().length; i++) {
         Vec3 s = anchors.stones()[i];
         float glow = anchors.glow()[i];
         if (s == null || glow < 0.35F) {
            continue;
         }

         float chance = 0.08F * glow * (late ? 2.2F : 1.0F);
         if (RNG.nextFloat() < chance) {
            Mote m = mote(s.add(RNG.nextGaussian() * 0.06, RNG.nextGaussian() * 0.06, RNG.nextGaussian() * 0.06), ThanosStone.values()[i].rgb,
               0.035F, 0.0F, 10 + RNG.nextInt(10), true);
            if (m != null) {
               m.vy = 0.02 + RNG.nextFloat() * 0.03;
               m.vx = RNG.nextGaussian() * 0.01;
               m.vz = RNG.nextGaussian() * 0.01;
            }
         }
      }

      Telegraph tel = TELEGRAPHS.get(t.getId());
      if (tel != null && tel.move().usesStones()) {
         ThanosStone lead = tel.move().lead();
         float k = Mth.clamp((ticks - tel.start()) / (float)tel.ticks(), 0.0F, 1.0F);
         Vec3 to = anchors.stones()[lead.ordinal()] != null ? anchors.stones()[lead.ordinal()] : anchors.gauntlet();
         for (ThanosStone s : ThanosStone.values()) {
            if (tel.move().uses(s) && RNG.nextFloat() < 0.5F + 0.5F * k) {
               Vec3 from = to.add(RNG.nextGaussian() * 1.4, RNG.nextGaussian() * 1.1, RNG.nextGaussian() * 1.4);
               Mote m = mote(from, s.rgb, 0.05F, 0.015F, 8, true);
               if (m != null) {
                  Vec3 v = to.subtract(from).scale(1.0 / 7.0);
                  m.vx = v.x;
                  m.vy = v.y;
                  m.vz = v.z;
                  m.drag = 1.0F;
               }
            }
         }
      }

      if (t.state() == ThanosEntity.STATE_VICTORY) {
         float time = ThanosClient.victoryTime(t, 0.0F);
         float front = ThanosSceneTimeline.victoryCrumble(time) * 1.1F;
         List<ThanosCells.Mote> cells = ThanosRenderer.motes(t.getId());
         if (front > 0.0F && !cells.isEmpty()) {
            for (int i = 0; i < 26; i++) {
               ThanosCells.Mote c = cells.get(RNG.nextInt(cells.size()));
               if (c.threshold() < front - 0.14F) {
                  continue;
               }

               Mote m = mote(new Vec3(c.x(), c.y(), c.z()), RNG.nextFloat() < 0.3F ? 0xFF7A30 : 0x3A3236, 0.04F, 0.015F, 30 + RNG.nextInt(30),
                  RNG.nextFloat() < 0.3F);
               if (m != null) {
                  m.vx = 0.03 + c.drift() * 0.05;
                  m.vy = 0.02 + RNG.nextFloat() * 0.03;
                  m.vz = c.spin() * 0.05;
                  m.drag = 0.98F;
               }
            }
         }
      }
   }

   private static void footfall(ClientLevel level, ThanosEntity t, float walk, boolean left) {
      float yaw = t.yBodyRot * Mth.DEG_TO_RAD;
      double side = left ? 0.45 : -0.45;
      double x = t.getX() + Math.cos(yaw) * side;
      double z = t.getZ() + Math.sin(yaw) * side;
      double y = groundY(level, x, z, t.getY()) + 0.02;
      Vec3 at = new Vec3(x, y, z);
      level.playLocalSound(x, y, z, ThanosSounds.STEP.get(), SoundSource.HOSTILE, 0.8F + walk * 0.6F, 0.82F + RNG.nextFloat() * 0.12F, false);
      groundDust(level, at, 0.7, 6 + (int)(walk * 6));
      CameraShake.addAt(at, 0.22F * walk, 18.0);
   }

   /** Per-tick emission of the long effects. */
   private static void emit(ClientLevel level, Fx f) {
      switch (f.kind) {
         case ThanosFxS2CPacket.POWER_WAVE -> {
            double r = Power.waveRadius(f.age);
            if (r < Power.WAVE_MAX) {
               for (int i = 0; i < 10; i++) {
                  double ang = RNG.nextDouble() * Math.PI * 2.0;
                  double rr = r * Reality.shapeScale(ang, f.extra);
                  Vec3 p = new Vec3(f.pos.x + Math.cos(ang) * rr, f.pos.y, f.pos.z + Math.sin(ang) * rr);
                  groundDust(level, new Vec3(p.x, groundY(level, p.x, p.z, f.pos.y) + 0.05, p.z), 0.2, 1);
               }
            }
         }
         case ThanosFxS2CPacket.REALITY_ZONE -> {
            if (f.power > 0.0F && RNG.nextFloat() < 0.7F) {
               float scale = Reality.zoneScale(f.age);
               double ang = RNG.nextDouble() * Math.PI * 2.0;
               double rr = Math.sqrt(RNG.nextDouble()) * f.power * scale;
               Mote m = mote(f.pos.add(Math.cos(ang) * rr, 0.1, Math.sin(ang) * rr), RNG.nextBoolean() ? RED : 0x7A0012, 0.06F, 0.0F, 20 + RNG.nextInt(16),
                  true);
               if (m != null) {
                  m.vy = 0.03 + RNG.nextFloat() * 0.04;
                  m.drag = 0.97F;
               }
            }
         }
         case ThanosFxS2CPacket.TIME_DOME -> {
            if (f.power > 0.0F && RNG.nextFloat() < 0.6F) {
               Vec3 p = f.pos.add(RNG.nextGaussian() * f.power * 0.4, RNG.nextFloat() * f.power * 0.6, RNG.nextGaussian() * f.power * 0.4);
               Mote m = mote(p, GREEN, 0.04F, 0.04F, 30, true);
               if (m != null) {
                  m.vy = 0.004;
                  m.drag = 0.99F;
               }
            }
         }
         case ThanosFxS2CPacket.SOUL_TETHER -> {
            Vec3[] ends = tetherEnds(level, f, 0.0F);
            if (ends != null && RNG.nextFloat() < (f.power > 0.5F ? 0.8F : 0.3F)) {
               Vec3 from = ends[1];
               Mote m = mote(from, ORANGE, 0.045F, 0.02F, 14, true);
               if (m != null) {
                  Vec3 v = ends[0].subtract(from).scale(1.0 / 13.0);
                  m.vx = v.x;
                  m.vy = v.y;
                  m.vz = v.z;
                  m.drag = 1.0F;
               }
            }
         }
         case ThanosFxS2CPacket.ARRIVAL -> {
            if (f.age < 60) {
               Vec3 c = arrivalCenter(f);
               double ang = RNG.nextDouble() * Math.PI * 2.0;
               Vec3 p = ringPoint(c, f.dir, 2.8 * arrivalOpen(f.age), ang);
               Mote m = mote(p, RNG.nextBoolean() ? BLUE : 0xBFD8FF, 0.06F, 0.01F, 14, true);
               if (m != null) {
                  m.vx = (c.x - p.x) * 0.04;
                  m.vy = (c.y - p.y) * 0.04;
                  m.vz = (c.z - p.z) * 0.04;
               }
            }

            if (f.age == 36) {
               Vec3 ground = new Vec3(f.pos.x, groundY(level, f.pos.x, f.pos.z, f.pos.y) + 0.05, f.pos.z);
               groundDust(level, ground, 4.0, 50);
               debris(level, ground, 20, 0.5);
               CameraShake.addAt(ground, 1.4F, 48.0);
            }
         }
         case ThanosFxS2CPacket.STUN -> {
            if (RNG.nextFloat() < 0.3F) {
               burst(f.pos.add(0.0, 1.3, 0.0), 2, 0.12, ThanosStone.values()[RNG.nextInt(6)].rgb, 0.04F, 8, true, 0.0);
            }
         }
         case ThanosFxS2CPacket.FISSURES -> {
            if (f.lines != null && !f.lines.isEmpty() && RNG.nextFloat() < 0.35F * (1.0F - f.t(0.0F))) {
               float[] s = f.lines.get(RNG.nextInt(f.lines.size()));
               Mote m = mote(new Vec3(s[0], s[1] + 0.05, s[2]), PURPLE, 0.04F, 0.0F, 16, true);
               if (m != null) {
                  m.vy = 0.05;
               }
            }
         }
         default -> {
         }
      }
   }

   // ---- spawners --------------------------------------------------------------------------------

   @Nullable
   static Mote mote(Vec3 at, int rgb, float size0, float size1, int life, boolean glow) {
      if (MOTES.size() >= MAX_MOTES) {
         return null;
      }

      Mote m = new Mote();
      m.x = at.x;
      m.y = at.y;
      m.z = at.z;
      m.rgb = rgb;
      m.size0 = size0;
      m.size1 = size1;
      m.life = life;
      m.alpha = glow ? 230 : 200;
      m.glow = glow;
      MOTES.add(m);
      return m;
   }

   static void burst(Vec3 at, int count, double speed, int rgb, float size, int life, boolean glow, double up) {
      for (int i = 0; i < count; i++) {
         Mote m = mote(at, rgb, size, size * 0.2F, life / 2 + RNG.nextInt(Math.max(1, life)), glow);
         if (m == null) {
            return;
         }

         Vec3 v = new Vec3(RNG.nextGaussian(), RNG.nextGaussian() + up * 3.0, RNG.nextGaussian()).normalize().scale(speed * (0.4 + RNG.nextFloat() * 0.6));
         m.vx = v.x;
         m.vy = v.y;
         m.vz = v.z;
         m.drag = 0.86F;
      }
   }

   static void implode(Vec3 at, int count, double radius, int rgb, int life) {
      for (int i = 0; i < count; i++) {
         Vec3 d = new Vec3(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).normalize().scale(radius * (0.6 + RNG.nextFloat() * 0.4));
         Mote m = mote(at.add(d), rgb, 0.06F, 0.01F, life, true);
         if (m == null) {
            return;
         }

         m.vx = -d.x / life;
         m.vy = -d.y / life;
         m.vz = -d.z / life;
         m.drag = 1.0F;
      }
   }

   /** Ground-coloured dust thrown out along the ground from {@code at}. */
   private static void groundDust(ClientLevel level, Vec3 at, double radius, int count) {
      int rgb = groundColour(level, at);
      for (int i = 0; i < count; i++) {
         double ang = RNG.nextDouble() * Math.PI * 2.0;
         double r = radius * (0.3 + RNG.nextDouble() * 0.7);
         Mote m = mote(at.add(Math.cos(ang) * r * 0.4, 0.1, Math.sin(ang) * r * 0.4), shade(rgb, 0.75F + RNG.nextFloat() * 0.35F), 0.12F, 0.26F,
            18 + RNG.nextInt(16), false);
         if (m == null) {
            return;
         }

         m.alpha = 150;
         m.vx = Math.cos(ang) * r * 0.06;
         m.vz = Math.sin(ang) * r * 0.06;
         m.vy = 0.02 + RNG.nextFloat() * 0.05;
         m.drag = 0.88F;
      }
   }

   /** Chunks of the ground kicked up. */
   private static void debris(ClientLevel level, Vec3 at, int count, double speed) {
      int rgb = groundColour(level, at);
      for (int i = 0; i < count; i++) {
         Mote m = mote(at.add(0.0, 0.2, 0.0), shade(rgb, 0.6F + RNG.nextFloat() * 0.4F), 0.07F + RNG.nextFloat() * 0.05F, 0.05F, 24 + RNG.nextInt(14),
            false);
         if (m == null) {
            return;
         }

         double ang = RNG.nextDouble() * Math.PI * 2.0;
         double s = speed * (0.4 + RNG.nextDouble() * 0.6);
         m.vx = Math.cos(ang) * s * 0.5;
         m.vz = Math.sin(ang) * s * 0.5;
         m.vy = s * (0.6 + RNG.nextDouble() * 0.6);
         m.gravity = 0.045F;
         m.drag = 0.97F;
         m.alpha = 240;
      }
   }

   static int groundColour(ClientLevel level, Vec3 at) {
      BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
      for (int i = 0; i < 3; i++) {
         BlockState s = level.getBlockState(pos);
         if (!s.isAir()) {
            MapColor c = s.getMapColor(level, pos);
            if (c != null && c != MapColor.NONE) {
               return c.col;
            }
         }

         pos = pos.below();
      }

      return 0x8A8274;
   }

   /** Top of the ground near {@code refY} at x/z (a short scan, not the heightmap: the city has roofs). */
   static double groundY(ClientLevel level, double x, double z, double refY) {
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(refY + 0.5), Mth.floor(z));
      for (int i = 0; i < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
         pos.move(0, 1, 0);
      }

      for (int i = 0; i < 6; i++) {
         BlockPos below = pos.below();
         if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return pos.getY();
         }

         pos.move(0, -1, 0);
      }

      return refY;
   }

   static int shade(int rgb, float k) {
      int r = Mth.clamp((int)((rgb >> 16 & 255) * k), 0, 255);
      int g = Mth.clamp((int)((rgb >> 8 & 255) * k), 0, 255);
      int b = Mth.clamp((int)((rgb & 255) * k), 0, 255);
      return r << 16 | g << 8 | b;
   }

   /** Crack polylines {x0,y0,z0,x1,y1,z1} along the ground: one line along {@code dir}, or radial. */
   static List<float[]> fissures(ClientLevel level, Vec3 at, Vec3 dir, float length, int seed) {
      RandomSource r = RandomSource.create(seed);
      List<float[]> out = new ArrayList<>();
      double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
      int cracks = flat > 0.01 ? 1 : 7;
      double base = flat > 0.01 ? Math.atan2(dir.z, dir.x) : r.nextDouble() * Math.PI * 2.0;
      for (int c = 0; c < cracks; c++) {
         double ang = base + (cracks == 1 ? 0.0 : c * Math.PI * 2.0 / cracks + (r.nextDouble() - 0.5) * 0.5);
         double len = length * (cracks == 1 ? 1.0 : 0.6 + r.nextDouble() * 0.4);
         crack(level, out, r, at.x, at.z, at.y, ang, len, 2);
      }

      return out;
   }

   private static void crack(ClientLevel level, List<float[]> out, RandomSource r, double x, double z, double y, double ang, double len, int depth) {
      double gone = 0.0;
      double py = groundY(level, x, z, y) + 0.04;
      while (gone < len && out.size() < 90) {
         double step = 0.6 + r.nextDouble() * 0.5;
         ang += (r.nextDouble() - 0.5) * 0.7;
         double nx = x + Math.cos(ang) * step;
         double nz = z + Math.sin(ang) * step;
         double ny = groundY(level, nx, nz, py) + 0.04;
         out.add(new float[]{(float)x, (float)py, (float)z, (float)nx, (float)ny, (float)nz, (float)(1.0 - gone / len)});
         if (depth > 0 && r.nextFloat() < 0.18F) {
            crack(level, out, r, nx, nz, ny, ang + (r.nextBoolean() ? 0.9 : -0.9), (len - gone) * 0.45, depth - 1);
         }

         x = nx;
         z = nz;
         py = ny;
         gone += step;
      }
   }

   // ---- rendering -------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (ShaderCompat.isShadowPass()) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }

      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
         ghosts(event, level, event.getPartialTick());
         return;
      }

      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
         return;
      }

      ThanosRenderer.newFrame();
      boolean thanos = ThanosClient.active() || !WALKERS.isEmpty();
      if (FX.isEmpty() && MOTES.isEmpty() && TELEGRAPHS.isEmpty() && !thanos && ThanosWarFx.idle()) {
         return;
      }

      Camera camera = event.getCamera();
      Vec3 cam = camera.getPosition();
      float partial = event.getPartialTick();
      PoseStack view = RenderSystem.getModelViewStack();
      view.pushPose();
      view.setIdentity();
      PixelVfx.levelView(view, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buf = tess.getBuilder();
      try {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         draw(buf, cam, camera, level, partial, false);
         tess.end();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         draw(buf, cam, camera, level, partial, true);
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         view.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }

   private static void draw(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, float partial, boolean glow) {
      for (Fx f : FX) {
         if (f.pos.distanceToSqr(cam) < RANGE * RANGE * 1.5) {
            drawFx(buf, cam, camera, level, f, partial, glow);
         }
      }

      ThanosWarFx.draw(buf, cam, camera, level, partial, glow);

      if (glow) {
         telegraphs(buf, cam, camera, level, partial);
         for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ThanosEntity t) {
               ThanosRenderer.Anchors a = ThanosRenderer.fresh(t.getId());
               if (a != null) {
                  bloom(buf, cam, camera, a.stones(), a.glow());
               }
            }
         }
      }

      for (Mote m : MOTES) {
         if (m.glow != glow) {
            continue;
         }

         double x = m.x + m.vx * partial;
         double y = m.y + m.vy * partial;
         double z = m.z + m.vz * partial;
         double dx = x - cam.x;
         double dy = y - cam.y;
         double dz = z - cam.z;
         if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
            continue;
         }

         float t = (m.age + partial) / m.life;
         int alpha = (int)(m.alpha * (1.0F - t * t));
         pixel(buf, cam, camera, new Vec3(x, y, z), Mth.lerp(t, m.size0, m.size1), m.rgb, alpha);
      }
   }

   /** Light at the gauntlet sockets on top of the emissive pass: a soft halo, a flare when a stone blazes. */
   static void bloom(BufferBuilder buf, Vec3 cam, Camera camera, Vec3[] stones, float[] glow) {
      for (int i = 0; i < stones.length; i++) {
         Vec3 s = stones[i];
         float g = glow[i];
         if (s == null || g < 0.05F) {
            continue;
         }

         int rgb = ThanosStone.values()[i].rgb;
         float k = Math.min(g, 1.6F);
         pixel(buf, cam, camera, s, 0.06F + 0.08F * k, rgb, (int)(70.0F * k));
         pixel(buf, cam, camera, s, 0.025F + 0.02F * k, HOT, (int)(120.0F * Math.min(1.0F, k)));
         if (g > 1.0F) {
            float over = Math.min(1.5F, g - 1.0F);
            pixel(buf, cam, camera, s, 0.18F + 0.3F * over, rgb, (int)(55.0F * over));
            PixelVfx.crossGlow(buf, cam, camera, s, 0.05F + 0.06F * over, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, (int)(120.0F * over));
         }
      }
   }

   private static void telegraphs(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, float partial) {
      for (Map.Entry<Integer, Telegraph> en : TELEGRAPHS.entrySet()) {
         if (!(level.getEntity(en.getKey()) instanceof ThanosEntity t)) {
            continue;
         }

         Telegraph tel = en.getValue();
         float k = Mth.clamp((ticks - tel.start() + partial) / tel.ticks(), 0.0F, 1.0F);
         int rgb = tel.move().usesStones() ? tel.move().lead().rgb : HOT;
         Vec3 feet = t.getPosition(partial).add(0.0, 0.06, 0.0);
         int alpha = (int)(60.0F + 150.0F * k);
         double spin = (ticks + partial) * 0.02;
         switch (tel.move().kind) {
            case AREA -> {
               float radius = areaRadius(tel.move());
               if (radius > 0.0F) {
                  dashes(buf, cam, camera, feet, radius, 0.08F, rgb, (int)(alpha * 0.8F), spin);
                  flatRing(buf, cam, feet, Math.max(0.0, radius * k - 0.35), radius * k, 64, rgb, (int)(alpha * 0.55F), 0);
               }
            }
            case MELEE -> {
               Vec3 aim = tel.aim().subtract(feet);
               double base = Math.atan2(aim.z, aim.x);
               double reach = tel.move().maxRange;
               if (tel.move() == ThanosMove.POWER_PUNCH) {
                  for (int i = 1; i <= 14; i++) {
                     double d = reach * i / 14.0;
                     if (i / 14.0 <= k + 0.05) {
                        pixel(buf, cam, camera, feet.add(Math.cos(base) * d, 0.04, Math.sin(base) * d), 0.09F, rgb, alpha);
                     }
                  }
               } else {
                  for (int i = 0; i <= 16; i++) {
                     double ang = base - 0.9 + 1.8 * i / 16.0;
                     pixel(buf, cam, camera, feet.add(Math.cos(ang) * reach * 0.8, 0.04, Math.sin(ang) * reach * 0.8), 0.07F, rgb, (int)(alpha * k));
                  }
               }
            }
            case RANGED, MOBILITY -> {
               Entity target = tel.target() >= 0 ? level.getEntity(tel.target()) : null;
               Vec3 to = target != null ? target.getPosition(partial) : tel.aim();
               dashes(buf, cam, camera, to, 1.3 - 0.5 * k, 0.07F, rgb, alpha, -spin * 3.0);
               ThanosRenderer.Anchors a = ThanosRenderer.fresh(t.getId());
               if (a != null && target != null && ((ticks >> 1) & 1) == 0) {
                  PixelVfx.beamDots(buf, cam, camera, a.gauntlet(), to.add(0.0, target.getBbHeight() * 0.5, 0.0), 0.7F, 0.035F, rgb >> 16 & 255,
                     rgb >> 8 & 255, rgb & 255, (int)(alpha * 0.45F));
               }
            }
            case DEFENSE -> ring(buf, cam, feet.add(0.0, 0.04, 0.0), new Vec3(0.0, 1.0, 0.0), 1.2 + 1.4 * k, 0.1, 32, rgb, (int)(alpha * 0.7F), spin);
            default -> {
            }
         }
      }
   }

   // ---- ghosts: mirages, rewind afterimages, false targets ----------------------------------------

   private static void ghosts(RenderLevelStageEvent event, ClientLevel level, float partial) {
      boolean any = falseLeft > 0;
      for (Fx f : FX) {
         if (f.kind == ThanosFxS2CPacket.REALITY_MIRAGE || f.kind == ThanosFxS2CPacket.TIME_REWIND) {
            any = true;
            break;
         }
      }

      if (!any) {
         return;
      }

      BakedGeoModel model = AnimCache.model(ThanosView.GEO);
      if (model == null) {
         return;
      }

      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      Map<Integer, ThanosRenderer.Look> posed = new HashMap<>();
      int posedId = Integer.MIN_VALUE;
      for (Fx f : FX) {
         if (f.kind != ThanosFxS2CPacket.REALITY_MIRAGE && f.kind != ThanosFxS2CPacket.TIME_REWIND) {
            continue;
         }

         if (!(level.getEntity(f.a) instanceof ThanosEntity t) || f.pos.distanceToSqr(cam) > RANGE * RANGE) {
            continue;
         }

         ThanosRenderer.Look look = posed.get(t.getId());
         if (look == null || posedId != t.getId()) {
            look = ThanosRenderer.pose(model, t, partial);
            posed.put(t.getId(), look);
            posedId = t.getId();
         }

         if (f.kind == ThanosFxS2CPacket.REALITY_MIRAGE) {
            float env = envelope(f, partial, 6.0F, 6.0F);
            float glitch = ((ticks + f.seed) % 23L) < 2L ? 0.35F : 1.0F;
            float yaw = (float)(Mth.atan2(f.dir.z, f.dir.x) * Mth.RAD_TO_DEG) - 90.0F;
            ghost(stack, buffers, model, cam, f.pos, yaw, look, 0.62F * env * glitch, f.power > 0.5F ? ORANGE : 0xFF8A90);
         } else {
            float t01 = f.t(partial);
            float yaw = Mth.rotLerp(partial, t.yBodyRotO, t.yBodyRot);
            for (int i = 1; i <= 4; i++) {
               Vec3 p = f.pos.add(f.dir.scale(i / 5.0));
               ghost(stack, buffers, model, cam, p, yaw, look, 0.34F * (1.0F - t01) * (1.0F - i / 6.0F), GREEN);
            }
         }
      }

      if (falseLeft > 0 && level.getEntity(falseThanos) instanceof ThanosEntity t) {
         ThanosRenderer.Look look = ThanosRenderer.pose(model, t, partial);
         float env = Mth.clamp(Math.min((falseMax - falseLeft + partial) / 6.0F, (falseLeft - partial) / 10.0F), 0.0F, 1.0F);
         Vec3 at = t.getPosition(partial);
         Vec3 me = Minecraft.getInstance().player.getPosition(partial);
         RandomSource r = RandomSource.create(falseSeed);
         double base = r.nextDouble() * Math.PI * 2.0;
         for (int i = 0; i < falseCount; i++) {
            double ang = base + i * Math.PI * 2.0 / falseCount + Math.sin((ticks + partial) * 0.03 + i) * 0.25;
            double rad = 4.5 + (i % 2) * 2.0;
            Vec3 p = at.add(Math.cos(ang) * rad, 0.0, Math.sin(ang) * rad);
            Vec3 d = me.subtract(p);
            float yaw = (float)(Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
            float glitch = ((ticks + i * 7L) % 17L) < 1L ? 0.4F : 1.0F;
            ghost(stack, buffers, model, cam, p, yaw, look, 0.72F * env * glitch, 0xF4ECFF);
         }
      }

      buffers.endBatch();
   }

   private static void ghost(PoseStack stack, MultiBufferSource buffers, BakedGeoModel model, Vec3 cam, Vec3 at, float yaw, ThanosRenderer.Look look,
      float alpha, int rgb) {
      if (alpha <= 0.02F) {
         return;
      }

      stack.pushPose();
      stack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      ThanosRenderer.drawGhost(model, stack, buffers, alpha, rgb, look);
      stack.popPose();
   }

   // ---- local afflictions -----------------------------------------------------------------------

   /** Screen tints of what is happening to this player: Mind sway, Soul tether, inside a dome or zone. */
   public static void renderHud(GuiGraphics g, float partial, int width, int height) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }

      dev.baranhan.viltrumitecore.client.thanos.war.ThanosWarClient.renderHud(g, partial, width, height);

      float time = ticks + partial;
      if (disorient > 0) {
         float k = Mth.clamp(Math.min(disorient / 12.0F, (disorientMax - disorient + 1) / 4.0F), 0.0F, 1.0F);
         vignette(g, width, height, YELLOW, (int)(80.0F * k * (0.7F + 0.3F * (float)Math.sin(time * 0.3))));
      }

      if (tether > 0) {
         float k = Mth.clamp(tether / 10.0F, 0.0F, 1.0F);
         float beat = (float)Math.pow(Math.max(0.0, Math.sin(time * 0.28)), 6.0);
         vignette(g, width, height, ORANGE, (int)((tetherReal ? 70.0F : 40.0F) * k * (0.5F + 0.5F * beat)));
      }

      if (resist > 0) {
         vignette(g, width, height, 0xFFFFFF, (int)(60.0F * resist / 12.0F));
      }

      Vec3 me = mc.player.position();
      for (Fx f : FX) {
         if (f.kind == ThanosFxS2CPacket.TIME_DOME && f.power > 0.0F && me.distanceTo(f.pos) < f.power) {
            float env = envelope(f, partial, 6.0F, 10.0F);
            g.fill(0, 0, width, height, (int)(22.0F * env) << 24 | GREEN);
            vignette(g, width, height, GREEN, (int)(60.0F * env));
            break;
         }
      }

      for (Fx f : FX) {
         if (f.kind == ThanosFxS2CPacket.REALITY_ZONE && f.power > 0.0F) {
            double dx = me.x - f.pos.x;
            double dz = me.z - f.pos.z;
            float scale = Reality.zoneScale(f.age);
            if (Reality.inZone(dx, me.y - f.pos.y, dz, scale)) {
               RandomSource r = RandomSource.create(ticks / 2);
               for (int i = 0; i < 6; i++) {
                  int y = r.nextInt(Math.max(1, height));
                  g.fill(0, y, width, y + 1 + r.nextInt(3), (int)(60.0F * scale) << 24 | RED);
               }

               vignette(g, width, height, RED, (int)(55.0F * scale));
               break;
            }
         }
      }
   }

   private static void vignette(GuiGraphics g, int width, int height, int rgb, int alpha) {
      if (alpha <= 2) {
         return;
      }

      int a = Math.min(255, alpha) << 24 | rgb & 0xFFFFFF;
      int band = Math.max(8, height / 5);
      g.fillGradient(0, 0, width, band, a, rgb & 0xFFFFFF);
      g.fillGradient(0, height - band, width, height, rgb & 0xFFFFFF, a);
      int side = Math.max(8, width / 8);
      int steps = 8;
      for (int i = 0; i < steps; i++) {
         int aa = (int)(Math.min(255, alpha) * (1.0F - i / (float)steps)) << 24 | rgb & 0xFFFFFF;
         int x0 = side * i / steps;
         int x1 = side * (i + 1) / steps;
         g.fill(x0, 0, x1, height, aa);
         g.fill(width - x1, 0, width - x0, height, aa);
      }
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      if (disorient <= 0) {
         return;
      }

      float k = Mth.clamp(Math.min(disorient / 14.0F, (disorientMax - disorient + 1) / 6.0F), 0.0F, 1.0F);
      double t = ticks + event.getPartialTick();
      event.setRoll(event.getRoll() + (float)Math.sin(t * 0.11) * 7.0F * k);
      event.setYaw(event.getYaw() + (float)Math.sin(t * 0.07) * 4.0F * k);
      event.setPitch(event.getPitch() + (float)Math.cos(t * 0.09) * 2.5F * k);
   }

   /** Live effects (tests, scene cleanup). */
   public static int live() {
      return FX.size();
   }

   public static void clear() {
      FX.clear();
      ThanosWarFx.clear();
      MOTES.clear();
      TELEGRAPHS.clear();
      WALKERS.clear();
      disorient = 0;
      tether = 0;
      falseLeft = 0;
      resist = 0;
      falseThanos = -1;
   }
}
