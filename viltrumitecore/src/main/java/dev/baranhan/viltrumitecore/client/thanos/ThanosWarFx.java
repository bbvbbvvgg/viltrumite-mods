package dev.baranhan.viltrumitecore.client.thanos;

import static dev.baranhan.viltrumitecore.client.thanos.ThanosFxDraw.*;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.SkyTint;
import dev.baranhan.viltrumitecore.network.packet.ThanosFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.thanos.ThanosStone;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client effects of the Thanos war (kinds {@code SHIP_WARN}..{@code CLEAR_WAR} of
 * {@link ThanosFxS2CPacket}): the Sanctuary II's warnings and lances, the new stone moves and combos,
 * the Black Order's telekinesis and brawling, the army's portals and the debris. Own work: quads and
 * pixels through the {@link ThanosFxDraw} helpers in the same two passes as {@link ThanosFx} (alpha,
 * then additive glow); motes go to the shared {@link ThanosFx} pool. Render thread only.
 */
final class ThanosWarFx {
   private static final double RANGE = 220.0;
   private static final double MOTE_RANGE = 96.0;
   private static final int MAX_LIVE = 700;
   private static final List<W> LIVE = new ArrayList<>();
   private static final Map<Integer, W> SHARDS = new HashMap<>();
   private static final Map<Integer, W> PHANTOMS = new HashMap<>();
   private static final Object TIME_TINT = new Object();
   private static final int CYAN = 0x7FE8FF;
   private static final int TK = 0x9FB8FF;
   private static final int EMBER = 0xFF7A2A;
   private static final int ROCK = 0x5E5A55;
   private static final int CHAIN = 0x3A3A40;
   private static int timeStop;

   private ThanosWarFx() {
   }

   /** One live war effect: the packet, its age and, for movers, where it is now. */
   static final class W {
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
      Vec3 at;
      Vec3 last;
      Vec3 vel = Vec3.ZERO;

      W(ThanosFxS2CPacket p, int life) {
         this.kind = p.kind();
         this.a = p.a();
         this.b = p.b();
         this.pos = new Vec3(p.x(), p.y(), p.z());
         this.dir = new Vec3(p.dx(), p.dy(), p.dz());
         this.power = p.power();
         this.extra = p.extra();
         this.seed = ThanosFx.RNG.nextLong();
         this.life = Math.max(1, life);
         this.at = this.pos;
         this.last = this.pos;
      }

      float t(float partial) {
         return Mth.clamp((this.age + partial) / this.life, 0.0F, 1.0F);
      }

      float time(float partial) {
         return this.age + partial;
      }

      Vec3 now(float partial) {
         return this.last.add(this.at.subtract(this.last).scale(partial));
      }

      float env(float partial, float in, float out) {
         float t = this.age + partial;
         return Mth.clamp(Math.min(t / Math.max(1.0F, in), (this.life - t) / Math.max(1.0F, out)), 0.0F, 1.0F);
      }
   }

   // ---- packets ---------------------------------------------------------------------------------

   static void accept(ThanosFxS2CPacket p, ClientLevel level, Vec3 at, Vec3 dir) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }

      Vec3 me = mc.player.position();
      double dist = me.distanceTo(at);
      switch (p.kind()) {
         case ThanosFxS2CPacket.SHIP_WARN -> add(p, p.extra() + 6);
         case ThanosFxS2CPacket.SHIP_LANCE -> {
            LIVE.removeIf(w -> w.kind == ThanosFxS2CPacket.SHIP_WARN && w.a == p.a());
            add(p, Math.max(10, p.extra()));
            dev.baranhan.viltrumitecore.client.thanos.war.ThanosWarClient.gunFlash();
            if (dist < MOTE_RANGE) {
               ThanosFx.burst(at.add(0.0, 0.4, 0.0), 40, 0.8, EMBER, 0.1F, 18, true, 0.4);
               ThanosFx.burst(at.add(0.0, 0.4, 0.0), 18, 0.5, ThanosFx.HOT, 0.08F, 10, true, 0.2);
               dust(level, at, p.power() * 1.6, 46);
               rubble(level, at, 26, 0.7);
            }

            CameraShake.addAt(at, 1.8F, 90.0);
            if (dist < p.power() * 3.0) {
               FlashOverlay.flash(0.45F, 8);
            }
         }
         case ThanosFxS2CPacket.RUPTURE -> {
            add(p, Math.max(10, p.extra()));
            if (dist < MOTE_RANGE) {
               ThanosFx.burst(at.add(0.0, 0.3, 0.0), 14, 0.45, ThanosFx.PURPLE, 0.08F, 14, true, 0.5);
               dust(level, at, 2.2, 14);
               rubble(level, at, 8, 0.5);
            }

            CameraShake.addAt(at, 0.7F, 30.0);
         }
         case ThanosFxS2CPacket.SHARD -> {
            if (p.b() == 0) {
               W w = add(p, Math.max(4, (int)p.power() + 4));
               if (w != null) {
                  w.vel = dir;
                  SHARDS.put(p.a(), w);
               }
            } else {
               W w = SHARDS.remove(p.a());
               if (w != null) {
                  w.life = w.age + 1;
               }

               if (dist < MOTE_RANGE) {
                  ThanosFx.burst(at, 16, 0.35, stoneRgb(p.extra()), 0.07F, 12, true, 0.1);
                  ThanosFx.burst(at, 6, 0.25, ThanosFx.HOT, 0.05F, 8, true, 0.0);
               }

               CameraShake.addAt(at, 0.35F, 16.0);
            }
         }
         case ThanosFxS2CPacket.REWRITE -> {
            add(p, Math.max(10, p.extra()));
            if (p.b() == 2) {
               if (dist < MOTE_RANGE) {
                  ThanosFx.burst(at.add(0.0, 0.5, 0.0), 40, 0.7, ThanosFx.RED, 0.09F, 16, true, 0.2);
                  dust(level, at, p.power(), 40);
                  rubble(level, at, 20, 0.6);
               }

               CameraShake.addAt(at, 1.3F, 40.0);
            }
         }
         case ThanosFxS2CPacket.ANOMALY_MARK -> add(p, p.extra() + 4);
         case ThanosFxS2CPacket.ANOMALY_SNAP -> {
            LIVE.removeIf(w -> w.kind == ThanosFxS2CPacket.ANOMALY_MARK && w.b == p.b());
            add(p, Math.max(8, p.extra()));
            ThanosFx.burst(at.add(0.0, 1.0, 0.0), 24, 0.35, ThanosFx.GREEN, 0.07F, 14, true, 0.1);
            if (p.b() == mc.player.getId()) {
               FlashOverlay.flash(0.3F, 6);
            }
         }
         case ThanosFxS2CPacket.COMPULSION -> add(p, Math.max(10, p.extra()));
         case ThanosFxS2CPacket.PHANTOM -> {
            W w = add(p, p.extra() + 10);
            if (w != null) {
               PHANTOMS.put(p.a(), w);
            }
         }
         case ThanosFxS2CPacket.PHANTOM_POP -> {
            W w = PHANTOMS.remove(p.a());
            if (w != null) {
               w.life = w.age + 1;
            }

            if (dist < MOTE_RANGE) {
               ThanosFx.burst(at, p.b() == 1 ? 26 : 10, p.b() == 1 ? 0.45 : 0.25, ThanosFx.ORANGE, 0.07F, 14, true, 0.3);
            }
         }
         case ThanosFxS2CPacket.TIME_STOP -> {
            add(p, Math.max(20, p.extra()));
            timeStop = Math.max(timeStop, p.extra());
            SkyTint.INSTANCE.push(TIME_TINT, 130, () -> new SkyTint.Tint(0.72F, 0.95F, 0.78F, 0.18F, 0.4F, 0.55F, 1.0F, 0.7F, 0.45F));
            CameraShake.addAt(at, 0.8F, p.power() * 2.0);
            if (dist < p.power()) {
               FlashOverlay.flash(0.22F, 6);
            }
         }
         case ThanosFxS2CPacket.MOON_PORTAL -> {
            add(p, Math.max(20, p.extra()));
            CameraShake.addAt(at, 0.6F, 120.0);
         }
         case ThanosFxS2CPacket.MOON_WARN -> add(p, p.extra() + 2);
         case ThanosFxS2CPacket.INFINITY_STAGE -> {
            add(p, p.b() < 0 ? Math.max(10, p.extra()) : 20);
            if (p.b() >= 0) {
               int rgb = stoneRgb(p.b());
               if (dist < MOTE_RANGE) {
                  ThanosFx.burst(at, 46, 0.8, rgb, 0.1F, 18, true, 0.1);
                  ThanosFx.burst(at, 16, 0.4, ThanosFx.HOT, 0.07F, 10, true, 0.0);
               }

               CameraShake.addAt(at, 1.0F + 0.15F * p.b(), 60.0);
               if (dist < 40.0) {
                  FlashOverlay.flash(0.18F, 5);
               }
            }
         }
         case ThanosFxS2CPacket.GAUNTLET_ABSORB -> {
            add(p, 10);
            if (dist < MOTE_RANGE) {
               ThanosFx.burst(at, 10, 0.3, ThanosFx.YELLOW, 0.06F, 10, true, 0.0);
               ThanosFx.burst(at, 6, 0.2, ThanosFx.PURPLE, 0.05F, 8, true, 0.0);
            }
         }
         case ThanosFxS2CPacket.COMMAND -> {
            add(p, Math.max(10, p.extra()));
            if (p.b() >= 0) {
               CameraShake.addAt(at, 0.9F, 96.0);
               ThanosFx.burst(at, 30, 0.6, ThanosFx.HOT, 0.08F, 14, true, 1.0);
            }
         }
         case ThanosFxS2CPacket.BEAM -> {
            add(p, Math.max(8, p.extra()));
            CameraShake.addAt(at, 0.5F, 48.0);
         }
         case ThanosFxS2CPacket.ARMY_PORTAL -> {
            add(p, Math.max(20, p.extra()));
            CameraShake.addAt(at, p.b() == 1 ? 0.6F : 0.35F, 64.0);
         }
         case ThanosFxS2CPacket.TK_GRAB, ThanosFxS2CPacket.TK_LIFT, ThanosFxS2CPacket.CULL_CHAIN, ThanosFxS2CPacket.CULL_ROAR -> {
            add(p, Math.max(8, p.extra()));
            if (p.kind() == ThanosFxS2CPacket.CULL_ROAR) {
               CameraShake.addAt(at, 0.6F * p.power(), 36.0);
            }
         }
         case ThanosFxS2CPacket.TK_BARRIER -> add(p, Math.max(10, p.extra()));
         case ThanosFxS2CPacket.TK_CATCH -> {
            add(p, 10);
            if (dist < MOTE_RANGE) {
               ThanosFx.burst(at, 12, 0.25, CYAN, 0.06F, 10, true, 0.0);
            }
         }
         case ThanosFxS2CPacket.CULL_SLAM -> {
            if (p.b() == 0) {
               add(p, p.extra() + 2);
            } else {
               add(p, 22);
               if (dist < MOTE_RANGE) {
                  dust(level, at, p.power(), 56);
                  rubble(level, at, 28, 0.75);
                  ThanosFx.burst(at.add(0.0, 0.3, 0.0), 18, 0.5, EMBER, 0.07F, 12, true, 0.3);
               }

               CameraShake.addAt(at, 1.5F, 44.0);
            }
         }
         case ThanosFxS2CPacket.CULL_CHARGE -> add(p, p.extra() + 30);
         case ThanosFxS2CPacket.WALL_BREAK -> {
            add(p, 12);
            if (dist < MOTE_RANGE) {
               Vec3 d = dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize();
               for (int i = 0; i < 18 + 6 * (int)p.power(); i++) {
                  ThanosFx.Mote m = ThanosFx.mote(at.add(gauss(0.6)), ThanosFx.shade(ThanosFx.groundColour(level, at), 0.6F + ThanosFx.RNG.nextFloat() * 0.5F),
                     0.09F + ThanosFx.RNG.nextFloat() * 0.08F, 0.06F, 22 + ThanosFx.RNG.nextInt(14), false);
                  if (m == null) {
                     break;
                  }

                  Vec3 v = d.scale(0.4 + ThanosFx.RNG.nextDouble() * 0.5).add(gauss(0.18)).add(0.0, 0.12, 0.0);
                  m.vx = v.x;
                  m.vy = v.y;
                  m.vz = v.z;
                  m.gravity = 0.04F;
                  m.drag = 0.96F;
                  m.alpha = 240;
               }

               dust(level, at.subtract(0.0, 1.0, 0.0), 2.5, 20);
            }

            CameraShake.addAt(at, 0.4F + 0.25F * p.power(), 32.0);
         }
         case ThanosFxS2CPacket.DEBRIS_IMPACT -> {
            BlockState state = Block.stateById(p.extra());
            boolean rock = p.b() == 2;
            if (dist < MOTE_RANGE) {
               if (!state.isAir()) {
                  BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
                  int n = rock ? 40 : 10 + (int)(p.power() * 8.0F);
                  for (int i = 0; i < n; i++) {
                     Vec3 o = gauss(0.6 * Math.max(1.0F, p.power()));
                     level.addParticle(opt, at.x + o.x, at.y + Math.abs(o.y) * 0.5, at.z + o.z, o.x * 0.2, 0.2 + Math.abs(o.y) * 0.2, o.z * 0.2);
                  }
               }

               dust(level, at, rock ? 6.0 : 1.2 + p.power(), rock ? 60 : 14);
               if (rock) {
                  rubble(level, at, 34, 0.9);
                  ThanosFx.burst(at.add(0.0, 0.5, 0.0), 36, 0.8, EMBER, 0.1F, 18, true, 0.5);
               }
            }

            if (rock) {
               add(p, 26);
               CameraShake.addAt(at, 1.7F, 80.0);
               if (dist < 10.0) {
                  FlashOverlay.flash(0.35F, 8);
               }
            } else {
               CameraShake.addAt(at, 0.25F + 0.2F * p.power(), 20.0);
            }
         }
         case ThanosFxS2CPacket.CLASH -> {
            add(p, 12);
            if (dist < MOTE_RANGE) {
               ThanosFx.burst(at, 30, 0.6, ThanosFx.HOT, 0.08F, 10, true, 0.0);
               ThanosFx.burst(at, 20, 0.45, EMBER, 0.07F, 14, true, 0.1);
            }

            CameraShake.addAt(at, 1.1F, 40.0);
         }
         case ThanosFxS2CPacket.CLEAR_WAR -> {
            if (p.b() == 1) {
               LIVE.removeIf(w -> w.a == p.a() && w.kind != ThanosFxS2CPacket.SHARD && w.kind != ThanosFxS2CPacket.PHANTOM);
            } else {
               clear();
            }
         }
         default -> {
         }
      }
   }

   @Nullable
   private static W add(ThanosFxS2CPacket p, int life) {
      if (LIVE.size() >= MAX_LIVE) {
         return null;
      }

      W w = new W(p, life);
      LIVE.add(w);
      return w;
   }

   // ---- ticking ---------------------------------------------------------------------------------

   static void tick(ClientLevel level) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      if (timeStop > 0 && --timeStop == 0) {
         SkyTint.INSTANCE.remove(TIME_TINT);
      }

      for (Iterator<W> it = LIVE.iterator(); it.hasNext(); ) {
         W w = it.next();
         if (++w.age >= w.life) {
            it.remove();
            if (w.kind == ThanosFxS2CPacket.SHARD) {
               SHARDS.remove(w.a, w);
            } else if (w.kind == ThanosFxS2CPacket.PHANTOM) {
               PHANTOMS.remove(w.a, w);
            }

            continue;
         }

         w.last = w.at;
         move(level, w);
         if (w.at.distanceToSqr(cam) < MOTE_RANGE * MOTE_RANGE) {
            emit(level, w);
         }
      }
   }

   private static void move(ClientLevel level, W w) {
      switch (w.kind) {
         case ThanosFxS2CPacket.SHARD -> w.at = w.at.add(w.vel);
         case ThanosFxS2CPacket.PHANTOM -> {
            Entity e = level.getEntity(w.b);
            if (e != null) {
               Vec3 to = e.position().add(0.0, e.getBbHeight() * 0.55, 0.0).subtract(w.at);
               double len = to.length();
               if (len > 1.0E-3) {
                  w.at = w.at.add(to.scale(Math.min(len, 0.34) / len));
               }
            }
         }
         case ThanosFxS2CPacket.MOON_WARN -> {
            float f = Mth.clamp((float)w.age / Math.max(1, w.extra), 0.0F, 1.0F);
            w.at = w.pos.add(w.dir.scale(1.0 - f * f));
         }
         default -> {
         }
      }
   }

   private static void emit(ClientLevel level, W w) {
      RandomSource r = ThanosFx.RNG;
      switch (w.kind) {
         case ThanosFxS2CPacket.SHIP_WARN -> {
            if (w.age % 3 == 0) {
               double ang = r.nextDouble() * Math.PI * 2.0;
               Vec3 p = w.pos.add(Math.cos(ang) * w.power, 0.15, Math.sin(ang) * w.power);
               ThanosFx.Mote m = ThanosFx.mote(p, EMBER, 0.08F, 0.02F, 14, true);
               if (m != null) {
                  m.vy = 0.08;
               }
            }
         }
         case ThanosFxS2CPacket.SHIP_LANCE -> {
            if (w.age < 10) {
               ThanosFx.burst(w.pos.add(gauss(w.power * 0.4)).add(0.0, 0.2, 0.0), 3, 0.5, w.age % 2 == 0 ? EMBER : ThanosFx.HOT, 0.07F, 12, true, 0.8);
            }
         }
         case ThanosFxS2CPacket.SHARD -> {
            ThanosFx.Mote m = ThanosFx.mote(w.at, stoneRgb(w.extra), 0.06F, 0.01F, 8, true);
            if (m != null) {
               m.drag = 0.8F;
            }
         }
         case ThanosFxS2CPacket.PHANTOM -> {
            ThanosFx.Mote m = ThanosFx.mote(w.at.add(gauss(0.25)), ThanosFx.ORANGE, 0.08F, 0.01F, 14, true);
            if (m != null) {
               m.vy = 0.03;
            }
         }
         case ThanosFxS2CPacket.MOON_WARN -> {
            if (w.b == 0) {
               double s = Math.max(1.0, w.power * 0.4);
               for (int i = 0; i < 3; i++) {
                  ThanosFx.Mote m = ThanosFx.mote(w.at.add(gauss(s * 0.6)), i == 0 ? ThanosFx.HOT : EMBER, 0.16F, 0.04F, 16, true);
                  if (m != null) {
                     m.vy = 0.05;
                     m.drag = 0.9F;
                  }
               }

               ThanosFx.Mote smoke = ThanosFx.mote(w.at.add(gauss(s * 0.5)), 0x2A2624, 0.3F, 0.6F, 30, false);
               if (smoke != null) {
                  smoke.alpha = 140;
                  smoke.drag = 0.95F;
               }
            }
         }
         case ThanosFxS2CPacket.BEAM -> {
            for (int i = 0; i < 2; i++) {
               double ang = r.nextDouble() * Math.PI * 2.0;
               double rr = w.power * (0.3 + r.nextDouble() * 0.6);
               ThanosFx.Mote m = ThanosFx.mote(w.pos.add(Math.cos(ang) * rr, r.nextDouble() * 2.0, Math.sin(ang) * rr), 0xFFC46A, 0.06F, 0.01F, 20, true);
               if (m != null) {
                  m.vy = 0.18 + r.nextDouble() * 0.1;
                  m.drag = 1.0F;
               }
            }
         }
         case ThanosFxS2CPacket.ARMY_PORTAL -> {
            if (w.age % 2 == 0) {
               Vec3 n = w.dir.lengthSqr() < 1.0E-6 ? UP : w.dir.normalize();
               Vec3 p = ringPoint(w.pos, n, w.power * (0.9 + r.nextDouble() * 0.2), r.nextDouble() * Math.PI * 2.0);
               ThanosFx.Mote m = ThanosFx.mote(p, r.nextBoolean() ? ThanosFx.BLUE : 0xDDEBFF, 0.07F, 0.01F, 16, true);
               if (m != null) {
                  Vec3 v = w.pos.subtract(p).scale(0.05);
                  m.vx = v.x;
                  m.vy = v.y;
                  m.vz = v.z;
               }
            }
         }
         case ThanosFxS2CPacket.TK_LIFT -> {
            Vec3 c = w.pos;
            double ang = w.age * 0.6 + r.nextDouble();
            double rr = 0.6 + w.power * 0.5;
            ThanosFx.Mote m = ThanosFx.mote(c.add(Math.cos(ang) * rr, r.nextDouble() * 0.6, Math.sin(ang) * rr), TK, 0.06F, 0.01F, 16, true);
            if (m != null) {
               m.vy = 0.1;
               m.vx = -Math.sin(ang) * 0.08;
               m.vz = Math.cos(ang) * 0.08;
            }
         }
         case ThanosFxS2CPacket.TK_GRAB, ThanosFxS2CPacket.COMPULSION -> {
            Entity e = level.getEntity(w.b);
            if (e != null && w.age % 2 == 0) {
               Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
               ThanosFx.Mote m = ThanosFx.mote(c.add(gauss(0.6)), w.kind == ThanosFxS2CPacket.TK_GRAB ? TK : ThanosFx.YELLOW, 0.05F, 0.01F, 12, true);
               if (m != null) {
                  m.vy = 0.02;
               }
            }
         }
         case ThanosFxS2CPacket.CULL_CHARGE -> {
            Entity e = level.getEntity(w.a);
            if (e != null && w.age > w.extra && w.age < w.extra + 30 && e.getDeltaMovement().horizontalDistanceSqr() > 0.04) {
               dust(level, e.position(), 1.4, 4);
            }
         }
         case ThanosFxS2CPacket.COMMAND -> {
            ThanosFx.Mote m = ThanosFx.mote(w.pos.add(gauss(0.3)), w.age % 2 == 0 ? ThanosFx.PURPLE : ThanosFx.HOT, 0.06F, 0.01F, 18, true);
            if (m != null) {
               m.vy = 0.45;
               m.drag = 1.0F;
            }
         }
         case ThanosFxS2CPacket.INFINITY_STAGE -> {
            if (w.b < 0) {
               ThanosFx.implode(w.pos, 2, 2.4, stoneRgb(w.age % 6), 12);
            }
         }
         case ThanosFxS2CPacket.RUPTURE -> {
            if (w.age < 8) {
               ThanosFx.Mote m = ThanosFx.mote(w.pos.add(gauss(0.8)), ThanosFx.PURPLE, 0.07F, 0.01F, 14, true);
               if (m != null) {
                  m.vy = 0.2;
               }
            }
         }
         default -> {
         }
      }
   }

   // ---- drawing ---------------------------------------------------------------------------------

   static boolean idle() {
      return LIVE.isEmpty();
   }

   static void draw(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, float partial, boolean glow) {
      for (W w : LIVE) {
         if (w.pos.distanceToSqr(cam) > RANGE * RANGE && w.at.distanceToSqr(cam) > RANGE * RANGE) {
            continue;
         }

         switch (w.kind) {
            case ThanosFxS2CPacket.SHIP_WARN -> shipWarn(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.SHIP_LANCE -> lance(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.RUPTURE -> rupture(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.SHARD -> shard(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.REWRITE -> rewrite(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.ANOMALY_MARK -> anomalyMark(buf, cam, camera, level, w, partial, glow);
            case ThanosFxS2CPacket.ANOMALY_SNAP -> anomalySnap(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.COMPULSION -> tether(buf, cam, camera, level, w, partial, glow, ThanosFx.YELLOW, 0.12);
            case ThanosFxS2CPacket.PHANTOM -> phantom(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.TIME_STOP -> timeStop(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.MOON_PORTAL -> moonPortal(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.MOON_WARN -> moonWarn(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.INFINITY_STAGE -> infinity(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.GAUNTLET_ABSORB -> absorb(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.COMMAND -> command(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.BEAM -> beam(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.ARMY_PORTAL -> armyPortal(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.TK_GRAB -> tether(buf, cam, camera, level, w, partial, glow, TK, w.power > 0.0F ? 0.16 : 0.06);
            case ThanosFxS2CPacket.TK_LIFT -> lift(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.TK_BARRIER -> barrier(buf, cam, camera, level, w, partial, glow);
            case ThanosFxS2CPacket.TK_CATCH -> flash(buf, cam, camera, w, partial, glow, CYAN, 0.9);
            case ThanosFxS2CPacket.CULL_SLAM -> slam(buf, cam, camera, level, w, partial, glow);
            case ThanosFxS2CPacket.CULL_CHARGE -> charge(buf, cam, camera, level, w, partial, glow);
            case ThanosFxS2CPacket.CULL_CHAIN -> chain(buf, cam, camera, level, w, partial, glow);
            case ThanosFxS2CPacket.WALL_BREAK -> flash(buf, cam, camera, w, partial, glow, 0xFFD9A0, 1.2);
            case ThanosFxS2CPacket.CULL_ROAR -> roar(buf, cam, camera, w, partial, glow);
            case ThanosFxS2CPacket.DEBRIS_IMPACT -> shock(buf, cam, camera, level, w, partial, glow, EMBER, 7.0);
            case ThanosFxS2CPacket.CLASH -> flash(buf, cam, camera, w, partial, glow, ThanosFx.HOT, 2.0);
            default -> {
            }
         }
      }
   }

   private static void shipWarn(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float time = w.time(partial);
      float warn = Mth.clamp(time / Math.max(1, w.extra), 0.0F, 1.0F);
      float pulse = 0.6F + 0.4F * Mth.sin(time * (0.4F + warn * 0.9F));
      Vec3 c = w.pos.add(0.0, 0.08, 0.0);
      int a = (int)(220.0F * pulse * w.env(partial, 4.0F, 3.0F));
      dashes(buf, cam, camera, c, w.power, 0.14F, 0xFF3A20, a, time * 0.05);
      ring(buf, cam, c, UP, w.power * (1.0 - warn) + 0.3, 0.12, 40, 0xFFB050, a, 0.0);
      flatRing(buf, cam, c, w.power * 0.15, w.power * 0.22, 24, 0xFF3A20, a / 2, 0);
      // the targeting column from the sky, thin while it aims, thick before the shot
      Vec3 top = c.add(0.0, 90.0, 0.0);
      ribbon(buf, cam, c, top, 0.08 + warn * 0.35, 0xFF6A3A, (int)(120.0F * warn * pulse), 0);
   }

   private static void lance(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float k = w.env(partial, 1.0F, 8.0F);
      Vec3 ground = w.pos;
      Vec3 gun = w.pos.add(w.dir);
      double width = w.power * 0.55 * (0.6 + 0.4 * k);
      if (!glow) {
         // scorched ring under the beam
         flatRing(buf, cam, ground.add(0.0, 0.06, 0.0), 0.0, w.power * 1.2, 28, 0x1A1410, (int)(150.0F * k), 0);
         return;
      }

      ribbon(buf, cam, gun, ground, width, EMBER, (int)(160.0F * k), (int)(230.0F * k));
      ribbon(buf, cam, gun, ground, width * 0.45, 0xFFD9A0, (int)(220.0F * k), (int)(255.0F * k));
      ribbon(buf, cam, gun, ground, width * 0.18, 0xFFFFFF, (int)(255.0F * k), (int)(255.0F * k));
      float t = w.t(partial);
      ring(buf, cam, ground.add(0.0, 0.2, 0.0), UP, w.power * (0.4 + t * 2.2), 0.4, 40, EMBER, (int)(200.0F * (1.0F - t)), 0.0);
      ring(buf, cam, ground.add(0.0, 0.5, 0.0), UP, w.power * (0.2 + t * 1.2), 0.25, 32, ThanosFx.HOT, (int)(220.0F * (1.0F - t)), 0.0);
      pixel(buf, cam, camera, gun, (float)(w.power * 0.6), 0xFFD9A0, (int)(220.0F * k));
      pixel(buf, cam, camera, ground.add(0.0, 0.5, 0.0), (float)(w.power * 0.9), EMBER, (int)(180.0F * k));
   }

   private static void rupture(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float k = w.env(partial, 2.0F, 8.0F);
      Vec3 c = w.pos.add(0.0, 0.07, 0.0);
      Vec3 d = new Vec3(w.dir.x, 0.0, w.dir.z);
      d = d.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : d.normalize();
      Vec3 side = new Vec3(-d.z, 0.0, d.x);
      if (!glow) {
         flatRing(buf, cam, c, 0.0, w.power * 1.4, 18, 0x140A1C, (int)(170.0F * k), 0);
         return;
      }

      RandomSource r = RandomSource.create(w.seed);
      for (int i = 0; i < 5; i++) {
         double off = (r.nextDouble() - 0.5) * w.power;
         Vec3 from = c.add(side.scale(off)).add(d.scale(-w.power));
         Vec3 to = c.add(side.scale(off * 0.4)).add(d.scale(w.power));
         groundStrip(buf, cam, from.x, from.y, from.z, to.x, to.y, to.z, 0.18, ThanosFx.PURPLE, (int)(230.0F * k));
      }

      float t = w.t(partial);
      for (int i = 0; i < 4; i++) {
         Vec3 base = c.add(side.scale((r.nextDouble() - 0.5) * w.power)).add(d.scale((r.nextDouble() - 0.5) * w.power));
         double h = (1.2 + r.nextDouble() * 1.8) * Math.sin(Math.PI * Math.min(1.0, t * 1.6));
         ribbon(buf, cam, base, base.add(0.0, h, 0.0), 0.35, ThanosFx.PURPLE, (int)(220.0F * k), 0);
      }

      ring(buf, cam, c.add(0.0, 0.2, 0.0), UP, w.power * (0.5 + t), 0.18, 24, 0xD9B0FF, (int)(160.0F * (1.0F - t)), 0.0);
   }

   private static void shard(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      Vec3 p = w.now(partial);
      Vec3 tail = p.subtract(w.vel.scale(2.6));
      int rgb = stoneRgb(w.extra);
      if (!glow) {
         ribbon(buf, cam, tail, p, 0.16, 0x0A1630, 120, 200);
         return;
      }

      ribbon(buf, cam, tail, p, 0.28, rgb, 0, 220);
      ribbon(buf, cam, p.subtract(w.vel.scale(1.2)), p, 0.12, 0xE8F2FF, 0, 255);
      pixel(buf, cam, camera, p, 0.34F, rgb, 220);
   }

   private static void rewrite(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float k = w.env(partial, 4.0F, 6.0F);
      float time = w.time(partial);
      Vec3 c = w.pos.add(0.0, 0.06, 0.0);
      int shape = w.b == 2 ? 0 : (int)(w.seed & 1L) + 1;
      if (w.b == 2) {
         float t = w.t(partial);
         if (glow) {
            ring(buf, cam, c.add(0.0, 0.3, 0.0), UP, w.power * (0.3 + t * 1.6), 0.4, 48, ThanosFx.RED, (int)(220.0F * (1.0F - t)), 0.0);
            wall(buf, cam, c, w.power * (0.4 + t * 1.2), 2.5 * (1.0F - t), 40, ThanosFx.RED, (int)(150.0F * (1.0F - t)), 0);
         }

         return;
      }

      if (!glow) {
         flatRing(buf, cam, c, 0.0, w.power, 40, 0x2A0408, (int)(110.0F * k), shape);
         return;
      }

      flatRing(buf, cam, c, w.power * 0.93, w.power, 48, ThanosFx.RED, (int)(220.0F * k), shape);
      wall(buf, cam, c, w.power, 3.0 + Mth.sin(time * 0.3F), 48, ThanosFx.RED, (int)(110.0F * k), shape);
      RandomSource r = RandomSource.create(w.seed + (long)(time / 3.0F));
      for (int i = 0; i < 10; i++) {
         double ang = r.nextDouble() * Math.PI * 2.0;
         double rr = w.power * Math.sqrt(r.nextDouble());
         double y = w.b == 1 ? 3.0 - (time * 0.25 + i) % 3.0 : (time * 0.25 + i) % 3.0;
         pixel(buf, cam, camera, c.add(Math.cos(ang) * rr, y, Math.sin(ang) * rr), 0.12F, i % 3 == 0 ? 0x000000 : ThanosFx.RED, (int)(200.0F * k));
      }
   }

   private static void anomalyMark(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float k = w.env(partial, 4.0F, 2.0F);
      float time = w.time(partial);
      float left = 1.0F - Mth.clamp(time / Math.max(1, w.extra), 0.0F, 1.0F);
      Vec3 c = w.pos.add(0.0, 0.08, 0.0);
      ring(buf, cam, c, UP, 1.4, 0.08, 32, ThanosFx.GREEN, (int)(220.0F * k), 0.0);
      ring(buf, cam, c, UP, 1.4 * left + 0.1, 0.12, 32, 0xC8FFD8, (int)(200.0F * k), 0.0);
      clockFace(buf, cam, c, 1.3, (int)(180.0F * k));
      // the hand that winds back
      double ang = -time * 0.25;
      groundStrip(buf, cam, c.x, c.y + 0.01, c.z, c.x + Math.cos(ang) * 1.2, c.y + 0.01, c.z + Math.sin(ang) * 1.2, 0.08, 0xC8FFD8, (int)(230.0F * k));
      // a green afterimage column where he will be put back
      ribbon(buf, cam, c, c.add(0.0, 1.9, 0.0), 0.7, ThanosFx.GREEN, (int)(90.0F * k), (int)(20.0F * k));
      Entity e = level.getEntity(w.b);
      if (e != null) {
         Vec3 p = e.getPosition(partial).add(0.0, e.getBbHeight() * 0.5, 0.0);
         ribbon(buf, cam, c.add(0.0, 1.0, 0.0), p, 0.05, ThanosFx.GREEN, (int)(140.0F * k), (int)(60.0F * k));
      }
   }

   private static void anomalySnap(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float k = 1.0F - w.t(partial);
      Vec3 to = w.pos.add(0.0, 1.0, 0.0);
      Vec3 from = to.add(w.dir);
      ribbon(buf, cam, from, to, 0.6 * k, ThanosFx.GREEN, (int)(60.0F * k), (int)(230.0F * k));
      ribbon(buf, cam, from, to, 0.2 * k, 0xE8FFF0, 0, (int)(255.0F * k));
      ring(buf, cam, w.pos.add(0.0, 0.1, 0.0), UP, 1.0 + w.t(partial) * 2.0, 0.12, 28, ThanosFx.GREEN, (int)(200.0F * k), 0.0);
   }

   /** A beam of the stone (or of Maw's telekinesis) from the caster's hand to the held hero. */
   private static void tether(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow, int rgb, double width) {
      if (!glow) {
         return;
      }

      Entity e = level.getEntity(w.b);
      if (e == null) {
         return;
      }

      Entity caster = level.getEntity(w.a);
      Vec3 from = w.pos;
      if (caster != null) {
         from = caster.getPosition(partial).add(w.pos.subtract(caster.position()));
      }

      float k = w.env(partial, 3.0F, 4.0F);
      float time = w.time(partial);
      Vec3 to = e.getPosition(partial).add(0.0, e.getBbHeight() * 0.55, 0.0);
      Vec3 d = to.subtract(from);
      Vec3 prev = from;
      for (int i = 1; i <= 10; i++) {
         double f = i / 10.0;
         Vec3 wob = new Vec3(Math.sin(time * 0.7 + i * 1.3), Math.cos(time * 0.9 + i), Math.sin(time * 0.5 + i * 2.1)).scale(0.25 * Math.sin(Math.PI * f));
         Vec3 next = from.add(d.scale(f)).add(wob);
         ribbon(buf, cam, prev, next, width, rgb, (int)(200.0F * k), (int)(200.0F * k));
         prev = next;
      }

      double r = Math.max(0.6, e.getBbWidth() * 0.9);
      ring(buf, cam, to, UP, r, 0.06, 24, rgb, (int)(180.0F * k), time * 0.15);
      ring(buf, cam, to.add(0.0, 0.4 * Math.sin(time * 0.3), 0.0), new Vec3(0.3, 1.0, 0.0), r * 1.15, 0.05, 24, rgb, (int)(130.0F * k), -time * 0.1);
   }

   private static void phantom(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      Vec3 p = w.now(partial);
      float k = w.env(partial, 6.0F, 4.0F);
      float time = w.time(partial);
      if (!glow) {
         pixel(buf, cam, camera, p, 0.7F, 0x1A0E04, (int)(120.0F * k));
         return;
      }

      // a hooded shape of soul fire: a column of flickering pixels, eyes in the head
      for (int i = 0; i < 7; i++) {
         double y = -0.9 + i * 0.3;
         double wob = 0.08 * Math.sin(time * 0.5 + i);
         float size = 0.42F - Math.abs(i - 3) * 0.05F;
         pixel(buf, cam, camera, p.add(wob, y, -wob), size, i % 2 == 0 ? ThanosFx.ORANGE : 0xFFB45A, (int)(150.0F * k));
      }

      pixel(buf, cam, camera, p.add(0.0, 0.95, 0.0), 0.36F, 0xFFD9A0, (int)(200.0F * k));
      pixel(buf, cam, camera, p.add(0.0, 0.6, 0.0), 0.9F, ThanosFx.ORANGE, (int)(60.0F * k));
   }

   private static void timeStop(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float t = w.t(partial);
      float open = Mth.clamp(w.time(partial) / 6.0F, 0.0F, 1.0F);
      float k = w.env(partial, 2.0F, 6.0F);
      double r = w.power * open;
      Vec3 c = w.pos.add(0.0, 0.08, 0.0);
      if (!glow) {
         flatRing(buf, cam, c, 0.0, r, 48, 0x062010, (int)(60.0F * k), 0);
         return;
      }

      wall(buf, cam, c, r, 6.0 + 10.0 * (1.0F - t), 64, ThanosFx.GREEN, (int)(90.0F * k), 0);
      flatRing(buf, cam, c, r * 0.97, r, 64, ThanosFx.GREEN, (int)(220.0F * k), 0);
      clockFace(buf, cam, c, Math.min(r, 6.0), (int)(200.0F * k));
      for (int i = 0; i < 3; i++) {
         ring(buf, cam, c.add(0.0, 2.0 + i * 3.0, 0.0), UP, r * (0.9 - i * 0.12), 0.12, 64, 0xC8FFD8, (int)(110.0F * k), i);
      }
   }

   private static void moonPortal(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float open = w.env(partial, 14.0F, 12.0F);
      Vec3 n = w.dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, -1.0, 0.0) : w.dir.normalize();
      portalDisc(buf, cam, camera, w.pos, n, w.power * open, open, w.time(partial), w.seed, glow);
      if (!glow) {
         // the shattered moon behind the gate: a grey disc with dark craters
         disc(buf, cam, w.pos.add(n.scale(-0.3)), n, w.power * 0.7 * open, 28, 0x77736C, (int)(200.0F * open), (int)(170.0F * open));
         RandomSource r = RandomSource.create(w.seed);
         for (int i = 0; i < 7; i++) {
            Vec3 p = ringPoint(w.pos.add(n.scale(-0.35)), n, w.power * 0.6 * open * Math.sqrt(r.nextDouble()), r.nextDouble() * Math.PI * 2.0);
            pixel(buf, cam, camera, p, (float)(w.power * 0.08 * (0.6 + r.nextDouble())), 0x3A3834, (int)(200.0F * open));
         }
      } else {
         ring(buf, cam, w.pos, n, w.power * open * 1.18, 0.5, 48, ThanosFx.PURPLE, (int)(120.0F * open), w.time(partial) * 0.02);
      }
   }

   private static void moonWarn(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float time = w.time(partial);
      float f = Mth.clamp(time / Math.max(1, w.extra), 0.0F, 1.0F);
      Vec3 c = w.pos.add(0.0, 0.08, 0.0);
      if (glow) {
         float pulse = 0.6F + 0.4F * Mth.sin(time * (0.5F + f));
         dashes(buf, cam, camera, c, w.power, 0.14F, w.b == 1 ? 0x7FA8FF : 0xFF4020, (int)(220.0F * pulse), time * 0.04);
         ring(buf, cam, c, UP, w.power * (1.0 - f) + 0.2, 0.1, 32, 0xFFB050, (int)(180.0F * pulse), 0.0);
      }

      if (w.b != 0) {
         return;
      }

      Vec3 p = w.now(partial);
      double s = Math.max(1.2, w.power * 0.55);
      if (!glow) {
         RandomSource r = RandomSource.create(w.seed);
         for (int i = 0; i < 9; i++) {
            Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(s * 0.35);
            pixel(buf, cam, camera, p.add(o), (float)(s * (0.5 + r.nextDouble() * 0.4)), i % 3 == 0 ? 0x4A4642 : ROCK, 255);
         }

         return;
      }

      Vec3 trail = w.dir.lengthSqr() < 1.0E-6 ? UP : w.dir.normalize();
      ribbon(buf, cam, p, p.add(trail.scale(s * 7.0)), s * 1.4, EMBER, 200, 0);
      ribbon(buf, cam, p, p.add(trail.scale(s * 3.0)), s * 0.8, 0xFFD9A0, 220, 0);
      pixel(buf, cam, camera, p, (float)(s * 1.7), EMBER, 90);
   }

   private static void infinity(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float time = w.time(partial);
      if (w.b < 0) {
         float k = w.env(partial, 6.0F, 3.0F);
         for (int i = 0; i < 6; i++) {
            double ang = time * 0.18 + i * Math.PI / 3.0;
            Vec3 p = w.pos.add(Math.cos(ang) * 1.3, 0.3 * Math.sin(time * 0.2 + i), Math.sin(ang) * 1.3);
            pixel(buf, cam, camera, p, 0.3F, stoneRgb(i), (int)(230.0F * k));
            ribbon(buf, cam, p, w.pos, 0.05, stoneRgb(i), (int)(150.0F * k), 0);
         }

         ring(buf, cam, w.pos, UP, 1.3, 0.05, 32, ThanosFx.HOT, (int)(120.0F * k), time * 0.1);
         return;
      }

      float t = w.t(partial);
      int rgb = stoneRgb(w.b);
      Vec3 ground = w.pos.add(0.0, -1.5, 0.0);
      ring(buf, cam, ground, UP, 2.0 + t * 26.0, 0.6, 64, rgb, (int)(230.0F * (1.0F - t)), 0.0);
      ring(buf, cam, w.pos, UP, 1.0 + t * 8.0, 0.3, 48, ThanosFx.HOT, (int)(200.0F * (1.0F - t)), 0.0);
      ribbon(buf, cam, w.pos, w.pos.add(0.0, 30.0 * (1.0F - t * 0.5F), 0.0), 1.4 * (1.0F - t), rgb, (int)(200.0F * (1.0F - t)), 0);
      pixel(buf, cam, camera, w.pos, 2.5F * (1.0F - t), rgb, (int)(200.0F * (1.0F - t)));
   }

   private static void absorb(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float t = w.t(partial);
      float k = 1.0F - t;
      Vec3 c = w.pos;
      Vec3 toCam = cam.subtract(c);
      Vec3 n = toCam.lengthSqr() < 1.0E-6 ? UP : toCam.normalize();
      // a hexagonal flare: the gauntlet drinks the blow
      for (int i = 0; i < 6; i++) {
         Vec3 p0 = ringPoint(c, n, 0.6 + t * 0.8, i * Math.PI / 3.0);
         Vec3 p1 = ringPoint(c, n, 0.6 + t * 0.8, (i + 1) * Math.PI / 3.0);
         ribbon(buf, cam, p0, p1, 0.07, ThanosFx.YELLOW, (int)(230.0F * k), (int)(230.0F * k));
      }

      pixel(buf, cam, camera, c, 0.9F * k, ThanosFx.PURPLE, (int)(160.0F * k));
   }

   private static void command(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float k = w.env(partial, 4.0F, 6.0F);
      float time = w.time(partial);
      boolean fire = w.b >= 0;
      Vec3 top = w.pos.add(0.0, fire ? 140.0 : 6.0 + time * 0.3, 0.0);
      ribbon(buf, cam, w.pos, top, fire ? 0.9 : 0.25, ThanosFx.PURPLE, (int)(220.0F * k), fire ? (int)(60.0F * k) : 0);
      ribbon(buf, cam, w.pos, top, fire ? 0.3 : 0.1, 0xF2E0FF, (int)(255.0F * k), fire ? (int)(80.0F * k) : 0);
      if (fire) {
         float t = w.t(partial);
         ring(buf, cam, w.pos.add(0.0, -1.2, 0.0), UP, 1.0 + t * 18.0, 0.5, 48, ThanosFx.PURPLE, (int)(200.0F * (1.0F - t)), 0.0);
      }

      pixel(buf, cam, camera, w.pos, 0.8F + 0.2F * Mth.sin(time * 0.6F), ThanosFx.HOT, (int)(220.0F * k));
   }

   /** The Sanctuary II's transport beam: a wide gold column from the ship to the ground. */
   private static void beam(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float k = w.env(partial, 5.0F, 8.0F);
      float time = w.time(partial);
      Vec3 ground = w.pos;
      Vec3 from = w.pos.add(w.dir);
      double width = w.power * 1.6 * (0.7 + 0.3 * Mth.sin(time * 0.4F));
      if (!glow) {
         flatRing(buf, cam, ground.add(0.0, 0.06, 0.0), 0.0, w.power * 1.3, 24, 0x2A1E08, (int)(90.0F * k), 0);
         return;
      }

      ribbon(buf, cam, from, ground, width, 0xFFB040, (int)(70.0F * k), (int)(170.0F * k));
      ribbon(buf, cam, from, ground, width * 0.4, 0xFFE8B0, (int)(110.0F * k), (int)(230.0F * k));
      for (int i = 0; i < 3; i++) {
         double y = (time * 0.6 + i * 2.0) % 6.0;
         ring(buf, cam, ground.add(0.0, y, 0.0), UP, w.power * (1.1 - y * 0.08), 0.08, 28, 0xFFD27A, (int)(200.0F * k * (1.0 - y / 6.0)), time * 0.1);
      }

      flatRing(buf, cam, ground.add(0.0, 0.08, 0.0), w.power * 1.1, w.power * 1.3, 28, 0xFFB040, (int)(200.0F * k), 0);
   }

   private static void armyPortal(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      float open = w.env(partial, 12.0F, 14.0F);
      Vec3 n = w.dir.lengthSqr() < 1.0E-6 ? UP : w.dir.normalize();
      double r = w.power * open;
      float time = w.time(partial);
      if (!glow) {
         disc(buf, cam, w.pos, n, r * 0.95, 32, 0x040814, (int)(230.0F * open), (int)(190.0F * open));
         return;
      }

      // a Chitauri breach: white-blue vortex arms round a black eye
      ring(buf, cam, w.pos, n, r, 0.12 + r * 0.08, 48, 0xCFE4FF, (int)(230.0F * open), time * 0.06);
      ring(buf, cam, w.pos, n, r * 1.15, 0.08, 48, ThanosFx.BLUE, (int)(140.0F * open), -time * 0.04);
      for (int arm = 0; arm < 4; arm++) {
         Vec3 prev = null;
         for (int i = 0; i <= 10; i++) {
            double f = i / 10.0;
            Vec3 p = ringPoint(w.pos, n, r * (0.15 + 0.85 * f), time * 0.12 + arm * Math.PI / 2.0 + f * 2.4);
            if (prev != null) {
               ribbon(buf, cam, prev, p, 0.06 + r * 0.03 * f, 0xBFD8FF, (int)(170.0F * open * (1.0 - f * 0.5)), (int)(170.0F * open * (1.0 - f * 0.5)));
            }

            prev = p;
         }
      }
   }

   private static void lift(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float k = w.env(partial, 3.0F, 4.0F);
      float time = w.time(partial);
      Vec3 c = w.pos;
      double r = 0.7 + w.power * 0.6;
      ring(buf, cam, c.add(0.0, -0.4, 0.0), UP, r, 0.07, 28, TK, (int)(200.0F * k), time * 0.2);
      ring(buf, cam, c.add(0.0, 0.2 + 0.3 * Mth.sin(time * 0.3F), 0.0), UP, r * 0.8, 0.05, 24, CYAN, (int)(150.0F * k), -time * 0.25);
      if (w.dir.lengthSqr() > 0.01) {
         ribbon(buf, cam, c, c.add(w.dir), 0.12 * w.power, TK, (int)(170.0F * k), 0);
      }
   }

   private static void barrier(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow) {
      Entity guard = level.getEntity(w.a);
      Vec3 c = guard != null ? guard.getPosition(partial).add(0.0, guard.getBbHeight() * 0.5, 0.0) : w.pos;
      float k = w.env(partial, 4.0F, 6.0F);
      float time = w.time(partial);
      double r = w.power;
      if (!glow) {
         return;
      }

      // a turning cage of telekinetic rings round the guarded one
      for (int i = 0; i < 4; i++) {
         double tilt = i * Math.PI / 4.0 + time * 0.02;
         Vec3 n = new Vec3(Math.cos(tilt), 0.35, Math.sin(tilt)).normalize();
         ring(buf, cam, c, n, r, 0.05, 36, i % 2 == 0 ? TK : CYAN, (int)(120.0F * k), time * 0.1 * (i % 2 == 0 ? 1 : -1));
      }

      ring(buf, cam, c, UP, r, 0.06, 40, CYAN, (int)(150.0F * k), time * 0.05);
      RandomSource rs = RandomSource.create(w.seed);
      for (int i = 0; i < 16; i++) {
         Vec3 d = new Vec3(rs.nextGaussian(), rs.nextGaussian(), rs.nextGaussian()).normalize();
         float tw = 0.5F + 0.5F * Mth.sin(time * 0.4F + i);
         pixel(buf, cam, camera, c.add(d.scale(r)), 0.08F, CYAN, (int)(200.0F * k * tw));
      }
   }

   private static void flash(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow, int rgb, double size) {
      if (!glow) {
         return;
      }

      float t = w.t(partial);
      float k = 1.0F - t;
      pixel(buf, cam, camera, w.pos, (float)(size * (0.6 + t)), rgb, (int)(220.0F * k));
      Vec3 toCam = cam.subtract(w.pos);
      Vec3 n = toCam.lengthSqr() < 1.0E-6 ? UP : toCam.normalize();
      ring(buf, cam, w.pos, n, size * (0.4 + t * 1.6), 0.08, 24, rgb, (int)(200.0F * k), 0.0);
   }

   private static void slam(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow) {
      Vec3 c = new Vec3(w.pos.x, ThanosFx.groundY(level, w.pos.x, w.pos.z, w.pos.y) + 0.07, w.pos.z);
      float time = w.time(partial);
      if (w.b == 0) {
         if (glow) {
            float f = Mth.clamp(time / Math.max(1, w.extra), 0.0F, 1.0F);
            dashes(buf, cam, camera, c, w.power, 0.13F, 0xFF4020, (int)(200.0F * (0.6F + 0.4F * Mth.sin(time * 0.8F))), 0.0);
            flatRing(buf, cam, c, 0.0, w.power * f, 32, 0xFF4020, (int)(60.0F * f), 0);
         }

         return;
      }

      shock(buf, cam, camera, level, w, partial, glow, EMBER, w.power * 1.8);
   }

   /** Ground shockwave: an expanding ring of dust and heat with cracks. */
   private static void shock(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow, int rgb, double radius) {
      if (w.kind == ThanosFxS2CPacket.DEBRIS_IMPACT && w.b != 2) {
         return;
      }

      float t = w.t(partial);
      Vec3 c = new Vec3(w.pos.x, ThanosFx.groundY(level, w.pos.x, w.pos.z, w.pos.y) + 0.07, w.pos.z);
      double r = radius * (0.2 + 0.8 * Math.sqrt(t));
      if (!glow) {
         flatRing(buf, cam, c, 0.0, radius * 0.45, 28, 0x16120E, (int)(170.0F * (1.0F - t * 0.6F)), 0);
         wall(buf, cam, c, r, 1.6 * (1.0F - t), 48, ThanosFx.shade(ThanosFx.groundColour(level, c), 0.8F), (int)(150.0F * (1.0F - t)), 0);
         return;
      }

      ring(buf, cam, c.add(0.0, 0.2, 0.0), UP, r, 0.3, 48, rgb, (int)(200.0F * (1.0F - t)), 0.0);
      RandomSource rs = RandomSource.create(w.seed);
      for (int i = 0; i < 8; i++) {
         double ang = rs.nextDouble() * Math.PI * 2.0;
         double len = radius * 0.6 * (0.5 + rs.nextDouble() * 0.5);
         groundStrip(buf, cam, c.x, c.y, c.z, c.x + Math.cos(ang) * len, c.y, c.z + Math.sin(ang) * len, 0.16, rgb, (int)(200.0F * (1.0F - t)));
      }
   }

   private static void charge(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow) {
      if (!glow || w.age > w.extra + 4) {
         return;
      }

      float time = w.time(partial);
      Vec3 d = new Vec3(w.dir.x, 0.0, w.dir.z);
      if (d.lengthSqr() < 1.0E-6) {
         return;
      }

      d = d.normalize();
      Vec3 c = new Vec3(w.pos.x, ThanosFx.groundY(level, w.pos.x, w.pos.z, w.pos.y) + 0.07, w.pos.z);
      Vec3 end = c.add(d.scale(w.power));
      float pulse = 0.6F + 0.4F * Mth.sin(time * 0.9F);
      groundStrip(buf, cam, c.x, c.y, c.z, end.x, c.y, end.z, 2.4, 0xFF3020, (int)(70.0F * pulse));
      Vec3 side = new Vec3(-d.z, 0.0, d.x).scale(1.3);
      groundStrip(buf, cam, c.x + side.x, c.y, c.z + side.z, end.x + side.x, c.y, end.z + side.z, 0.12, 0xFF6040, (int)(220.0F * pulse));
      groundStrip(buf, cam, c.x - side.x, c.y, c.z - side.z, end.x - side.x, c.y, end.z - side.z, 0.12, 0xFF6040, (int)(220.0F * pulse));
      for (int i = 1; i < 6; i++) {
         double f = (i / 6.0 + time * 0.03) % 1.0;
         Vec3 p = c.add(d.scale(w.power * f));
         Vec3 l = p.subtract(d.scale(0.8)).add(side);
         Vec3 rr = p.subtract(d.scale(0.8)).subtract(side);
         groundStrip(buf, cam, l.x, c.y, l.z, p.x, c.y, p.z, 0.14, 0xFFB050, (int)(200.0F * pulse));
         groundStrip(buf, cam, rr.x, c.y, rr.z, p.x, c.y, p.z, 0.14, 0xFFB050, (int)(200.0F * pulse));
      }
   }

   /** Cull Obsidian's chain: dark links from his arm to the hero, glinting. */
   private static void chain(BufferBuilder buf, Vec3 cam, Camera camera, ClientLevel level, W w, float partial, boolean glow) {
      Entity cull = level.getEntity(w.a);
      Entity target = level.getEntity(w.b);
      Vec3 from = cull != null ? cull.getPosition(partial).add(w.pos.subtract(cull.position())) : w.pos;
      Vec3 to = target != null ? target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0) : w.pos.add(w.dir);
      float k = w.env(partial, 2.0F, 3.0F);
      Vec3 d = to.subtract(from);
      double len = d.length();
      if (len < 0.1) {
         return;
      }

      int links = Mth.clamp((int)(len / 0.35), 4, 80);
      for (int i = 0; i < links; i++) {
         double f0 = (double)i / links;
         double f1 = (i + 0.8) / links;
         double sag0 = Math.sin(Math.PI * f0) * Math.min(1.2, len * 0.05);
         double sag1 = Math.sin(Math.PI * f1) * Math.min(1.2, len * 0.05);
         Vec3 a = from.add(d.scale(f0)).subtract(0.0, sag0, 0.0);
         Vec3 b = from.add(d.scale(f1)).subtract(0.0, sag1, 0.0);
         if (!glow) {
            ribbon(buf, cam, a, b, i % 2 == 0 ? 0.16 : 0.09, CHAIN, (int)(255.0F * k), (int)(255.0F * k));
         } else if (i % 5 == (int)(w.time(partial) * 0.5F) % 5) {
            pixel(buf, cam, camera, a, 0.1F, 0xFFC890, (int)(200.0F * k));
         }
      }
   }

   private static void roar(BufferBuilder buf, Vec3 cam, Camera camera, W w, float partial, boolean glow) {
      if (!glow) {
         return;
      }

      float time = w.time(partial);
      Vec3 d = w.dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : w.dir.normalize();
      for (int i = 0; i < 3; i++) {
         float t = ((time + i * 5.0F) % 15.0F) / 15.0F;
         Vec3 c = w.pos.add(d.scale(0.5 + t * 6.0 * w.power));
         ring(buf, cam, c, d, 0.5 + t * 3.0 * w.power, 0.08, 28, 0xFFE0C0, (int)(120.0F * (1.0F - t) * w.env(partial, 2.0F, 4.0F)), 0.0);
      }
   }

   // ---- helpers ---------------------------------------------------------------------------------

   static int stoneRgb(int ordinal) {
      ThanosStone[] all = ThanosStone.values();
      return all[Math.floorMod(ordinal, all.length)].rgb;
   }

   private static Vec3 gauss(double s) {
      RandomSource r = ThanosFx.RNG;
      return new Vec3(r.nextGaussian() * s, r.nextGaussian() * s, r.nextGaussian() * s);
   }

   /** Ground-coloured dust rolling out along the ground. */
   static void dust(ClientLevel level, Vec3 at, double radius, int count) {
      Vec3 g = new Vec3(at.x, ThanosFx.groundY(level, at.x, at.z, at.y) + 0.1, at.z);
      int rgb = ThanosFx.groundColour(level, g);
      RandomSource r = ThanosFx.RNG;
      for (int i = 0; i < count; i++) {
         double ang = r.nextDouble() * Math.PI * 2.0;
         double rr = radius * (0.3 + r.nextDouble() * 0.7);
         ThanosFx.Mote m = ThanosFx.mote(g.add(Math.cos(ang) * rr * 0.3, 0.1, Math.sin(ang) * rr * 0.3), ThanosFx.shade(rgb, 0.7F + r.nextFloat() * 0.35F),
            0.16F, 0.4F, 24 + r.nextInt(20), false);
         if (m == null) {
            return;
         }

         m.alpha = 150;
         m.vx = Math.cos(ang) * rr * 0.06;
         m.vz = Math.sin(ang) * rr * 0.06;
         m.vy = 0.02 + r.nextFloat() * 0.06;
         m.drag = 0.88F;
      }
   }

   /** Chunks of the ground thrown up. */
   static void rubble(ClientLevel level, Vec3 at, int count, double speed) {
      int rgb = ThanosFx.groundColour(level, at);
      RandomSource r = ThanosFx.RNG;
      for (int i = 0; i < count; i++) {
         ThanosFx.Mote m = ThanosFx.mote(at.add(0.0, 0.3, 0.0), ThanosFx.shade(rgb, 0.55F + r.nextFloat() * 0.4F), 0.09F + r.nextFloat() * 0.08F, 0.06F,
            26 + r.nextInt(16), false);
         if (m == null) {
            return;
         }

         double ang = r.nextDouble() * Math.PI * 2.0;
         double s = speed * (0.4 + r.nextDouble() * 0.6);
         m.vx = Math.cos(ang) * s * 0.5;
         m.vz = Math.sin(ang) * s * 0.5;
         m.vy = s * (0.6 + r.nextDouble() * 0.7);
         m.gravity = 0.045F;
         m.drag = 0.97F;
         m.alpha = 240;
      }
   }

   static void clear() {
      LIVE.clear();
      SHARDS.clear();
      PHANTOMS.clear();
      if (timeStop > 0) {
         timeStop = 0;
         SkyTint.INSTANCE.remove(TIME_TINT);
      }
   }

}
