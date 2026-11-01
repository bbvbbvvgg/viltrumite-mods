package dev.baranhan.viltrumitecore.client.ultron;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.Glow;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.Pulse;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.Beam;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.Mark;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.Swirl;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.BeamEnd;
import dev.baranhan.viltrumitecore.client.ultron.UltronVfxParts.Piece;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.chitauri.GlowDraw;
import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.entity.ultron.UltronBolt;
import dev.baranhan.viltrumitecore.entity.ultron.UltronMissile;
import dev.baranhan.viltrumitecore.entity.ultron.UltronNodeEntity;
import dev.baranhan.viltrumitecore.entity.ultron.UltronUnit;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronCapture;
import dev.baranhan.viltrumitecore.worldevent.ultron.UltronNodeState;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronFxS2CPacket;
import dev.baranhan.viltrumitecore.worldevent.ultron.net.UltronStateS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client effects of the uprising ({@link UltronFxS2CPacket}, all 32 kinds) and the continuous world effects drawn from
 * the synced state: impacts, missile fireballs, deflector flashes, the machines breaking apart into their own model parts
 * (captured from the death frame, tumbling, bouncing, smoking and cooling), weak parts torn off, muzzle flashes and charge
 * gathering, slam and shock waves, the drone zap / scan / designator mark, welding sparks, data streams of hacks,
 * captures and the upload, node boot / shield hits / shield break / shutdown / destruction, fabrication, glitch pulses,
 * Ultron Prime's eye beam ends, impacts, transitions, assembly vortex and death, dash streaks and jet blasts. From the
 * state: the power links between discovered nodes, the finale tethers into Ultron Prime, signal pillars over far
 * discovered nodes, the red halo of captured machines; damaged machines smoke and spark, missiles leave smoke. Red /
 * orange palette (liberation is the only cold colour). Every list is capped and cleared on a level change. Built on the
 * shared VFX (HeroImpactFx recipes for dust, debris, ground rings, scorch, shake and screen flash). Render thread.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class UltronVfx {
   private static final int MAX_GLOWS = 640;
   private static final int MAX_PIECES = 110;
   private static final int MAX_PULSES = 28;
   private static final int MAX_BEAMS = 56;
   private static final int MAX_MARKS = 10;
   private static final int MAX_SWIRLS = 6;
   private static final double EFFECT_RANGE = 48.0;
   private static final List<Glow> GLOWS = new ArrayList<>();
   private static final List<Piece> PIECES = new ArrayList<>();
   private static final List<Pulse> PULSES = new ArrayList<>();
   private static final List<Beam> BEAMS = new ArrayList<>();
   private static final List<Mark> MARKS = new ArrayList<>();
   private static final List<Swirl> SWIRLS = new ArrayList<>();
   private static final Map<Integer, BeamEnd> BEAM_ENDS = new HashMap<>();
   static final Random RANDOM = new Random();
   private static ClientLevel lastLevel;
   private static int clock;

   static final int[] RED = {255, 45, 30};
   static final int[] HOT = {255, 214, 196};
   static final int[] ORANGE = {255, 128, 48};
   static final int[] DATA = {255, 62, 58};
   static final int[] COLD = {150, 220, 255};

   static final FxRecipe IMPACT = recipe("impact", 3101, b -> b.dust(0, 0.7F, 4));
   static final FxRecipe BLAST = recipe("blast", 3102, b -> b.dust(0, 2.2F, 16).debris(0, 10, 3.0F, 0.8F).shockwave(0, 12, 3.2F, 0.0F)
      .scorch(0, 400, 1.6F, 8).shake(0, 0.35F, 40.0F).screenFlash(0, 4, 0.1F, 18.0F));
   static final FxRecipe SLAM = recipe("slam", 3103, b -> b.dust(0, 3.0F, 24).debris(0, 14, 4.0F, 1.0F).shockwave(0, 14, 4.5F, 1.0F).shake(0, 0.5F, 40.0F));
   static final FxRecipe PRIME_SLAM = recipe("prime_slam", 3104, b -> b.dust(0, 4.5F, 40).debris(0, 24, 6.0F, 1.3F).shockwave(0, 18, 7.0F, 1.5F)
      .shake(0, 0.8F, 70.0F).screenFlash(0, 5, 0.12F, 30.0F));
   static final FxRecipe NODE_BREAK = recipe("node_break", 3105, b -> b.dust(0, 4.0F, 40).debris(0, 30, 5.0F, 1.2F).shockwave(0, 20, 6.0F, 1.0F)
      .shake(0, 0.7F, 64.0F).screenFlash(0, 6, 0.18F, 40.0F).dust(8, 6.0F, 30));
   static final FxRecipe PRIME_DEATH = recipe("prime_death", 3106, b -> b.dust(0, 6.0F, 60).debris(0, 30, 8.0F, 1.4F).shockwave(0, 30, 12.0F, 2.0F)
      .shake(0, 1.1F, 120.0F).screenFlash(0, 12, 0.35F, 90.0F).dust(12, 9.0F, 40).shake(14, 0.4F, 90.0F));
   static final FxRecipe LANDING = recipe("landing", 3107, b -> b.dust(0, 1.4F, 10).shake(0, 0.1F, 14.0F));
   static final FxRecipe HEAVY = recipe("heavy", 3108, b -> b.dust(0, 1.2F, 8).shake(0, 0.25F, 24.0F));
   static final FxRecipe BREAKUP = recipe("breakup", 3109, b -> b.dust(0, 1.6F, 10).debris(0, 8, 2.0F, 0.7F).shake(0, 0.2F, 24.0F));
   static final FxRecipe TRANSITION = recipe("transition", 3110, b -> b.shockwave(0, 20, 8.0F, 0.0F).shake(0, 0.6F, 90.0F).screenFlash(0, 8, 0.2F, 60.0F));

   private UltronVfx() {
   }

   private static FxRecipe recipe(String name, int net, UnaryOperator<FxRecipe.Builder> layers) {
      return layers.apply(FxRecipe.builder(new FxPreset("viltrumitecore:ultron_" + name, net))).build();
   }

   // ------------------------------------------------------------------ entry points

   public static void accept(UltronFxS2CPacket p) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return;
      }
      Vec3 at = p.at();
      Vec3 d = p.d();
      long seed = p.extra() * 0x9E3779B97F4A7C15L + p.kind() * 31L + clock;
      switch (p.kind()) {
         case UltronFxS2CPacket.BOLT_IMPACT -> boltImpact(level, at, d, p.power(), p.extra(), seed);
         case UltronFxS2CPacket.MISSILE_BLAST -> missileBlast(level, at, d, seed);
         case UltronFxS2CPacket.DEFLECT -> deflect(level, at, p.power(), p.extra());
         case UltronFxS2CPacket.UNIT_BREAKUP -> breakup(level, at, p.power(), seed);
         case UltronFxS2CPacket.MUZZLE -> muzzle(level, at, d, p.power(), p.extra());
         case UltronFxS2CPacket.SLAM_WAVE -> slamWave(level, at, p.power(), seed);
         case UltronFxS2CPacket.ZAP -> zap(level, at, d, p.extra(), seed);
         case UltronFxS2CPacket.SCAN -> scan(at, p.power());
         case UltronFxS2CPacket.MARK -> mark(level, at, d, p.power(), p.extra());
         case UltronFxS2CPacket.WELD -> weld(at, d, p.power());
         case UltronFxS2CPacket.HACK -> hack(level, at, d, p.power(), seed);
         case UltronFxS2CPacket.NODE_BOOT -> nodeBoot(at, p.power(), p.extra());
         case UltronFxS2CPacket.NODE_SHIELD_HIT -> shieldHit(level, at, p.power(), p.extra());
         case UltronFxS2CPacket.NODE_SHIELD_BREAK -> shieldBreak(at, p.extra());
         case UltronFxS2CPacket.NODE_DESTROYED -> nodeDestroyed(level, at, p.extra(), seed);
         case UltronFxS2CPacket.NODE_DISABLED -> nodeDisabled(level, at, seed);
         case UltronFxS2CPacket.FABRICATE -> fabricate(at, d, p.power(), p.extra());
         case UltronFxS2CPacket.GLITCH -> glitch(level, at, p.power(), p.extra());
         case UltronFxS2CPacket.PRIME_BEAM -> primeBeam(level, at, d, p.power(), p.extra(), seed);
         case UltronFxS2CPacket.PRIME_IMPACT -> primeImpact(level, at, d, p.power(), seed);
         case UltronFxS2CPacket.PRIME_SHOCKWAVE -> primeShockwave(level, at, p.power(), seed);
         case UltronFxS2CPacket.PRIME_TRANSITION -> primeTransition(level, at, p.power(), seed);
         case UltronFxS2CPacket.PRIME_ASSEMBLE -> primeAssemble(at, p.extra());
         case UltronFxS2CPacket.PRIME_DEATH -> primeDeath(level, at, seed);
         case UltronFxS2CPacket.CAPTURE -> capture(at, d, p.power(), p.extra());
         case UltronFxS2CPacket.RELEASE -> release(level, at);
         case UltronFxS2CPacket.DASH_TRAIL -> dashTrail(at, d);
         case UltronFxS2CPacket.UPLOAD -> upload(at, d, p.extra());
         case UltronFxS2CPacket.CHARGE -> charge(at, d, p.power());
         case UltronFxS2CPacket.JET -> jet(level, at, d, p.power(), seed);
         case UltronFxS2CPacket.NODE_HIT -> nodeHit(level, at, p.power(), seed);
         case UltronFxS2CPacket.SHUTDOWN -> shutdown(level, at, p.power());
         default -> {
         }
      }
   }

   /** Where Ultron Prime's eye beam currently ends (interpolated between the server's aim updates), or null. */
   @Nullable
   public static Vec3 beamEnd(int primeId, float partial) {
      BeamEnd b = BEAM_ENDS.get(primeId);
      if (b == null || clock - b.tick() > 8) {
         return null;
      }
      float k = Mth.clamp((clock - b.tick() + partial) / 5.0F, 0.0F, 1.0F);
      return b.prev().lerp(b.end(), k);
   }

   /** A weak part came off a machine at critical damage (the renderer's frame of the part). */
   static void partLost(UltronUnit e, UltronAssets.Model assets, String bone, Matrix4f m, Vec3 pos) {
      Vec3 c = pos.add(0.0, e.getBbHeight() * 0.6, 0.0);
      Piece piece = piece(assets, bone, Set.of(), m, pos, e.getDeltaMovement(), 1.4F);
      if (piece != null) {
         addPiece(piece);
      }
      flash(c, 0.9F, 4, Glow.FLASH);
      sparks(c, Vec3.ZERO, 14, 0.25, Glow.SPARK);
      fireball(c, 0.6F);
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         particles(level, ParticleTypes.LARGE_SMOKE, c, 4, 0.4, 0.05, true);
      }
   }

   // ------------------------------------------------------------------ handlers

   private static void boltImpact(ClientLevel level, Vec3 at, Vec3 d, float power, int extra, long seed) {
      boolean melee = (extra & 1) == 1;
      int style = melee ? -1 : Math.min(2, extra >> 1);
      float s = Mth.clamp(power, 0.4F, 2.2F);
      Vec3 back = d.lengthSqr() > 1.0E-6 ? d.normalize().scale(-1.0) : Vec3.ZERO;
      if (melee) {
         flash(at, 0.5F * s, 3, Glow.FLASH);
         sparks(at, back, (int)(8 * s), 0.22 * s, Glow.SPARK);
         pulse(at, back.lengthSqr() > 0 ? back : new Vec3(0, 1, 0), 0.1F, 0.9F * s, 6, 0.5F, ORANGE, 64, 0);
         return;
      }
      int[] col = style == UltronBolt.STYLE_LASER ? RED : style == UltronBolt.STYLE_PALM ? ORANGE : ORANGE;
      flash(at, 0.55F * s, 4, Glow.FLASH);
      glow(at, Vec3.ZERO, 0.4F * s, 1.1F * s, 6, Glow.RED, 0.8F, 0.0F, 0.0F);
      sparks(at, back, (int)(6 + 6 * s), 0.18 + 0.08 * s, Glow.SPARK);
      if (style == UltronBolt.STYLE_PALM) {
         pulse(at, back.lengthSqr() > 0 ? back : new Vec3(0, 1, 0), 0.2F, 2.2F, 9, 0.8F, col, 64, 0);
         particles(level, ParticleTypes.SMOKE, at, 4, 0.4, 0.03, true);
      }
      if (nearGround(level, at, 0.6)) {
         HeroImpactFx.play(IMPACT, at, at, s, null, seed);
      }
   }

   private static void missileBlast(ClientLevel level, Vec3 at, Vec3 d, long seed) {
      flash(at, 2.6F, 5, Glow.FLASH);
      glow(at, Vec3.ZERO, 1.2F, 3.4F, 8, Glow.RED, 0.8F, 0.0F, 0.0F);
      fireball(at, 1.4F);
      sparks(at, new Vec3(0, 0.3, 0), 22, 0.45, Glow.SPARK);
      pulse(at, new Vec3(0, 1, 0), 0.4F, 4.0F, 10, 0.8F, ORANGE, 64, 0);
      particles(level, ParticleTypes.LARGE_SMOKE, at, 10, 1.4, 0.06, true);
      particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 3, 0.8, 0.02, true);
      HeroImpactFx.play(BLAST, at, at, 1.0F, null, seed);
   }

   private static void deflect(ClientLevel level, Vec3 at, float power, int id) {
      Entity e = level.getEntity(id);
      Vec3 c = at;
      Vec3 n = new Vec3(0, 0, 1);
      if (e instanceof UltronUnit u) {
         Vec3 f = u.facing();
         c = u.position().add(f.scale(u.getBbWidth() * 0.6 + 0.35)).add(0.0, u.getBbHeight() * 0.55, 0.0);
         n = f;
      }
      float s = Mth.clamp(power, 0.5F, 2.0F);
      pulse(c, n, 0.2F, 1.2F * s, 7, 0.9F, RED, 6, 0);
      pulse(c, n, 0.1F, 0.7F * s, 5, 0.7F, HOT, 6, 1);
      sparks(c, n, (int)(8 * s), 0.2, Glow.SPARK);
      glow(c, Vec3.ZERO, 0.3F * s, 0.9F * s, 4, Glow.RED, 0.7F, 0.0F, 0.0F);
   }

   /** A machine's destruction clip ends: its body breaks into its own parts. */
   @SuppressWarnings("unchecked")
   private static void breakup(ClientLevel level, Vec3 at, float power, long seed) {
      float s = Mth.clamp((float)Math.sqrt(Math.max(0.1F, power)), 0.6F, 2.4F);
      UltronUnit unit = null;
      double best = 16.0;
      for (UltronUnit u : level.getEntitiesOfClass(UltronUnit.class, new AABB(at, at).inflate(4.0), u -> !u.isAlive() && !u.clientShattered)) {
         double dsq = u.position().add(0.0, u.getBbHeight() * 0.4, 0.0).distanceToSqr(at);
         if (dsq < best) {
            best = dsq;
            unit = u;
         }
      }
      if (unit != null) {
         EntityRenderer<?> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(unit);
         if (r instanceof UltronUnitRenderer<?> ur) {
            Map<String, Matrix4f> mats = ((UltronUnitRenderer<UltronUnit>)ur).captureBreakup(unit);
            Set<String> all = new HashSet<>(ur.pieces());
            if (unit.clientPartLost) {
               all.remove(null);
            }
            for (String name : ur.pieces()) {
               Matrix4f m = mats.get(name);
               if (m == null) {
                  continue;
               }
               Set<String> exclude = new HashSet<>(all);
               exclude.remove(name);
               Piece piece = piece(ur.assets(), name, exclude, m, unit.position(), unit.getDeltaMovement(), 1.0F);
               if (piece != null) {
                  addPiece(piece);
               }
            }
         }
         unit.clientShattered = true;
      }
      flash(at, 1.2F * s, 5, Glow.FLASH);
      glow(at, Vec3.ZERO, 0.6F * s, 2.0F * s, 7, Glow.RED, 0.7F, 0.0F, 0.0F);
      fireball(at, 0.7F * s);
      sparks(at, new Vec3(0, 0.2, 0), (int)(12 * s), 0.3 * s, Glow.SPARK);
      particles(level, ParticleTypes.LARGE_SMOKE, at, (int)(5 * s), 0.8 * s, 0.05, true);
      HeroImpactFx.play(BREAKUP, at, at, s, null, seed);
   }

   private static void muzzle(ClientLevel level, Vec3 at, Vec3 d, float power, int id) {
      Vec3 c = at;
      Entity e = level.getEntity(id);
      if (e instanceof UltronUnit u && u.clientAnchors[UltronUnitRenderer.A_RIGHT] != null && u.tickCount - u.clientAnchorTick < 3
         && u.clientAnchors[UltronUnitRenderer.A_RIGHT].distanceToSqr(at) < 2.25) {
         c = u.clientAnchors[UltronUnitRenderer.A_RIGHT];
      }
      Vec3 n = d.lengthSqr() > 1.0E-6 ? d.normalize() : Vec3.ZERO;
      float s = Mth.clamp(power, 0.4F, 2.0F);
      glow(c, Vec3.ZERO, 0.25F * s, 0.7F * s, 3, Glow.HOT, 1.0F, 0.0F, 0.0F);
      glow(c.add(n.scale(0.25)), n.scale(0.06), 0.4F * s, 1.1F * s, 3, Glow.RED, 0.8F, 0.0F, 0.0F);
      for (int i = 0; i < 4; i++) {
         glow(c, n.scale(0.25 + RANDOM.nextDouble() * 0.2).add(gauss(0.07)), 0.06F, 0.02F, 4 + RANDOM.nextInt(4), Glow.SPARK, 0.9F, 0.02F, 0.0F);
      }
      if (s >= 1.5F) {
         pulse(c.add(n.scale(0.4)), n, 0.1F, 1.0F * s, 6, 0.7F, ORANGE, 64, 0);
      }
   }

   private static void slamWave(ClientLevel level, Vec3 at, float radius, long seed) {
      float s = Mth.clamp(radius / 4.5F, 0.4F, 2.0F);
      Vec3 g = at.add(0.0, 0.15, 0.0);
      pulse(g, new Vec3(0, 1, 0), 0.3F, radius, 12, 0.9F, ORANGE, 64, 0);
      pulse(g, new Vec3(0, 1, 0), 0.2F, radius * 0.7F, 10, 0.6F, RED, 64, 2);
      flash(g, 1.0F * s, 4, Glow.FLASH);
      for (int i = 0; i < 16; i++) {
         double a = i / 16.0 * Math.PI * 2.0;
         Vec3 v = new Vec3(Math.cos(a), 0.0, Math.sin(a)).scale(0.35 * s);
         glow(g, v.add(0, 0.05, 0), 0.25F * s, 0.05F, 8 + RANDOM.nextInt(4), Glow.FIRE, 0.8F, 0.0F, 0.0F);
      }
      HeroImpactFx.play(SLAM, at, at, s, null, seed);
   }

   private static void zap(ClientLevel level, Vec3 eye, Vec3 end, int id, long seed) {
      Entity e = level.getEntity(id);
      Beam b = new Beam(eye, end, 5, 0.07F, DATA, 0.35F, seed);
      if (e instanceof UltronUnit u) {
         b.follow = u;
         b.anchor = UltronUnitRenderer.A_RIGHT;
      }
      addBeam(b);
      flash(end, 0.6F, 3, Glow.FLASH);
      sparks(end, Vec3.ZERO, 8, 0.2, Glow.SPARK);
      glow(end, Vec3.ZERO, 0.3F, 0.8F, 5, Glow.RED, 0.8F, 0.0F, 0.0F);
   }

   private static void scan(Vec3 at, float range) {
      pulse(at.add(0.0, 0.3, 0.0), new Vec3(0, 1, 0), 0.5F, range, 30, 0.45F, RED, 64, 0);
      pulse(at.add(0.0, 0.3, 0.0), new Vec3(0, 1, 0), 0.3F, range * 0.6F, 24, 0.3F, DATA, 64, 6);
   }

   private static void mark(ClientLevel level, Vec3 at, Vec3 eye, float power, int target) {
      if (power >= 2.5F) {
         addMark(new Mark(at, 3.0F, 70, null));
         return;
      }
      Entity e = target >= 0 ? level.getEntity(target) : null;
      addMark(new Mark(at, 1.0F, 50, e));
      if (eye.lengthSqr() > 0.0) {
         addBeam(new Beam(eye, at, 8, 0.04F, RED, 0.0F, 0L));
      }
   }

   private static void weld(Vec3 torch, Vec3 spot, float build) {
      addBeam(new Beam(torch, spot, 3, 0.05F, HOT, 0.12F, RANDOM.nextLong()));
      flash(spot, 0.35F, 3, Glow.HOT);
      for (int i = 0; i < 7; i++) {
         glow(spot, gauss(0.1).add(0, 0.08, 0), 0.07F, 0.02F, 10 + RANDOM.nextInt(10), Glow.SPARK, 1.0F, 0.035F, 0.0F);
      }
      if (build < 1.0F && RANDOM.nextInt(3) == 0) {
         pulse(spot, new Vec3(0, 1, 0), 0.1F, 0.8F, 6, 0.4F, ORANGE, 6, 0);
      }
   }

   private static void hack(ClientLevel level, Vec3 from, Vec3 to, float progress, long seed) {
      Beam b = new Beam(from, to, 12, 0.05F, DATA, 0.18F, seed);
      b.pulses = true;
      addBeam(b);
      glow(to, Vec3.ZERO, 0.2F + 0.4F * progress, 0.5F + 0.6F * progress, 8, Glow.RED, 0.6F, 0.0F, 0.0F);
      if (RANDOM.nextInt(3) == 0) {
         sparks(to, Vec3.ZERO, 3, 0.1, Glow.SPARK);
      }
   }

   private static void nodeBoot(Vec3 at, float power, int kind) {
      int[] col = kindColor(kind);
      float s = Mth.clamp(power, 0.3F, 1.6F);
      flash(at, 1.6F * s, 6, Glow.FLASH);
      for (int i = 0; i < 3; i++) {
         pulse(at.add(0.0, -1.5 + i * 1.2, 0.0), new Vec3(0, 1, 0), 0.4F, 3.0F * s + i * 0.6F, 14 + i * 3, 0.8F, col, 64, i * 3);
      }
      for (int i = 0; i < (int)(18 * s); i++) {
         glow(at.add(gauss(0.6)), new Vec3(0, 0.12 + RANDOM.nextDouble() * 0.15, 0), 0.18F, 0.04F, 16 + RANDOM.nextInt(10), Glow.RED, 0.8F, 0.0F, 0.0F);
      }
   }

   private static void shieldHit(ClientLevel level, Vec3 contact, float power, int kind) {
      UltronNodeEntity node = nearestNode(level, contact, 6.0);
      Vec3 n = node == null ? new Vec3(0, 0, 1) : contact.subtract(node.position().add(0.0, UltronNodeRenderer.DOME_Y, 0.0));
      float s = Mth.clamp(power, 0.3F, 2.0F);
      pulse(contact, n, 0.1F, 1.3F * s, 8, 0.9F, kindColor(kind), 6, 0);
      pulse(contact, n, 0.05F, 0.7F * s, 6, 0.6F, HOT, 6, 1);
      glow(contact, Vec3.ZERO, 0.3F * s, 0.9F * s, 5, Glow.RED, 0.8F, 0.0F, 0.0F);
      sparks(contact, n.lengthSqr() > 0 ? n.normalize() : Vec3.ZERO, (int)(5 * s), 0.15, Glow.SPARK);
   }

   private static void shieldBreak(Vec3 at, int kind) {
      int[] col = kindColor(kind);
      flash(at, 3.0F, 6, Glow.FLASH);
      pulse(at, new Vec3(0, 1, 0), 1.0F, 6.0F, 14, 1.0F, col, 6, 0);
      pulse(at, new Vec3(1, 0, 0), 1.0F, 5.5F, 14, 0.8F, col, 6, 1);
      pulse(at, new Vec3(0, 0, 1), 1.0F, 5.5F, 14, 0.8F, col, 6, 2);
      for (int i = 0; i < 40; i++) {
         Vec3 dir = gauss(1.0).normalize();
         glow(at.add(dir.scale(UltronNodeRenderer.DOME_R)), dir.scale(0.1 + RANDOM.nextDouble() * 0.15), 0.16F, 0.03F, 14 + RANDOM.nextInt(12), Glow.RED,
            0.9F, 0.02F, 0.0F);
      }
   }

   private static void nodeDestroyed(ClientLevel level, Vec3 at, int kind, long seed) {
      flash(at, 4.0F, 8, Glow.FLASH);
      glow(at, Vec3.ZERO, 2.0F, 5.0F, 10, Glow.RED, 0.8F, 0.0F, 0.0F);
      fireball(at, 2.0F);
      fireball(at.add(0.0, 1.5, 0.0), 1.4F);
      sparks(at, new Vec3(0, 0.4, 0), 40, 0.55, Glow.SPARK);
      pulse(at.add(0.0, -1.8, 0.0), new Vec3(0, 1, 0), 0.5F, 7.0F, 16, 1.0F, kindColor(kind), 64, 0);
      particles(level, ParticleTypes.LARGE_SMOKE, at, 20, 2.0, 0.07, true);
      particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 6, 1.2, 0.03, true);
      HeroImpactFx.play(NODE_BREAK, at.add(0.0, -2.0, 0.0), at, 1.0F, null, seed);
   }

   private static void nodeDisabled(ClientLevel level, Vec3 at, long seed) {
      for (int i = 0; i < 5; i++) {
         Vec3 a = at.add(gauss(0.8));
         Vec3 b = a.add(gauss(1.2));
         Beam beam = new Beam(a, b, 6 + i * 3, 0.05F, RED, 0.3F, seed + i);
         beam.delay = i * 3;
         addBeam(beam);
      }
      sparks(at, new Vec3(0, 0.2, 0), 18, 0.3, Glow.SPARK);
      particles(level, ParticleTypes.SMOKE, at, 10, 1.0, 0.04, true);
      pulse(at, new Vec3(0, 1, 0), 2.5F, 0.3F, 12, 0.7F, RED, 64, 0);
   }

   private static void nodeHit(ClientLevel level, Vec3 at, float power, long seed) {
      float s = Mth.clamp(power, 0.3F, 2.0F);
      flash(at, 0.6F * s, 3, Glow.FLASH);
      sparks(at, Vec3.ZERO, (int)(8 * s), 0.22, Glow.SPARK);
      glow(at, Vec3.ZERO, 0.3F * s, 0.8F * s, 5, Glow.RED, 0.6F, 0.0F, 0.0F);
      if (s > 1.0F) {
         particles(level, ParticleTypes.SMOKE, at, 3, 0.4, 0.03, true);
      }
   }

   private static void fabricate(Vec3 from, Vec3 to, float power, int kind) {
      int[] col = ORANGE;
      if (to.lengthSqr() > 1.0E-6) {
         Beam b = new Beam(from, to.add(0.0, 1.0, 0.0), 14, 0.08F, col, 0.08F, RANDOM.nextLong());
         b.pulses = true;
         addBeam(b);
         for (int i = 0; i < 3; i++) {
            pulse(to.add(0.0, 0.1 + i * 0.7, 0.0), new Vec3(0, 1, 0), 1.2F, 0.4F, 14, 0.8F, col, 6, i * 4);
         }
         flash(to.add(0.0, 1.0, 0.0), 1.2F, 6, Glow.HOT);
         for (int i = 0; i < 14; i++) {
            glow(to.add(gauss(0.6)).add(0.0, 1.0, 0.0), new Vec3(0, 0.05, 0), 0.12F, 0.03F, 12 + RANDOM.nextInt(8), Glow.RED, 0.8F, 0.0F, 0.0F);
         }
      } else {
         float s = Mth.clamp(power, 0.3F, 1.5F);
         flash(from, 0.8F * s, 4, Glow.HOT);
         sparks(from, new Vec3(0, 0.2, 0), (int)(6 * s), 0.15, Glow.SPARK);
         pulse(from, new Vec3(0, 1, 0), 0.2F, 1.5F * s, 8, 0.6F, col, 6, 0);
      }
   }

   private static void glitch(ClientLevel level, Vec3 at, float power, int extra) {
      Minecraft mc = Minecraft.getInstance();
      if (power > 20.0F) {
         double dist = mc.player == null ? 0.0 : mc.player.position().distanceTo(at);
         float strength = (float)Mth.clamp(1.0 - dist / (power * 1.4), 0.25, 1.0);
         UltronHudOverlay.glitch(strength * (extra >= 3 ? 0.6F : 1.0F), extra >= 3 ? 14 : 24);
         if (mc.player != null) {
            Vec3 p = mc.player.position().add(0.0, 1.0, 0.0);
            for (int i = 0; i < 30; i++) {
               Vec3 q = p.add(gauss(6.0));
               glow(q, Vec3.ZERO, 0.08F + RANDOM.nextFloat() * 0.12F, 0.0F, 3 + RANDOM.nextInt(6), Glow.RED, 0.9F, 0.0F, 0.0F);
            }
         }
         pulse(at, new Vec3(0, 1, 0), 2.0F, Math.min(power, 120.0F), 40, 0.5F, RED, 64, 0);
         return;
      }
      float r = Mth.clamp(power, 0.5F, 6.0F);
      for (int i = 0; i < (int)(10 * r); i++) {
         Vec3 q = at.add(gauss(r * 0.8));
         glow(q, Vec3.ZERO, 0.06F + RANDOM.nextFloat() * 0.14F, 0.0F, 2 + RANDOM.nextInt(6), RANDOM.nextInt(4) == 0 ? Glow.HOT : Glow.RED, 1.0F, 0.0F, 0.0F);
      }
      pulse(at, new Vec3(0, 1, 0), 0.5F, 2.5F * r, 10, 0.6F, RED, 6, 0);
      if (mc.player != null && mc.player.position().distanceTo(at) < 10.0 * r) {
         UltronHudOverlay.glitch(0.35F, 8);
      }
   }

   private static void primeBeam(ClientLevel level, Vec3 from, Vec3 to, float power, int id, long seed) {
      if (power < 0.75F) {
         // the palm's beam variant: a short-lived bolt of light from the palm
         addBeam(new Beam(from, to, 5, 0.22F, ORANGE, 0.0F, seed));
      } else {
         BeamEnd b = BEAM_ENDS.get(id);
         Vec3 prev = b == null || clock - b.tick() > 8 ? to : beamEnd(id, 0.0F);
         BEAM_ENDS.put(id, new BeamEnd(prev == null ? to : prev, to, clock));
      }
      flash(to, 1.2F, 4, Glow.FLASH);
      glow(to, Vec3.ZERO, 0.8F, 2.0F, 6, Glow.RED, 0.8F, 0.0F, 0.0F);
      sparks(to, new Vec3(0, 0.3, 0), 12, 0.35, Glow.SPARK);
      fireball(to, 0.6F);
      particles(level, ParticleTypes.SMOKE, to, 3, 0.5, 0.04, true);
      if (nearGround(level, to, 1.0)) {
         HeroImpactFx.play(IMPACT, to, to, 1.5F, null, seed);
      }
   }

   private static void primeImpact(ClientLevel level, Vec3 at, Vec3 d, float power, long seed) {
      float s = Mth.clamp(power, 0.5F, 2.5F);
      Vec3 n = d.lengthSqr() > 1.0E-6 ? d.normalize() : new Vec3(0, 1, 0);
      flash(at, 1.4F * s, 4, Glow.FLASH);
      glow(at, Vec3.ZERO, 0.6F * s, 1.8F * s, 6, Glow.RED, 0.8F, 0.0F, 0.0F);
      pulse(at, n, 0.2F, 1.8F * s, 8, 0.9F, ORANGE, 64, 0);
      pulse(at, n, 0.1F, 1.1F * s, 6, 0.7F, HOT, 64, 1);
      sparks(at, n, (int)(12 * s), 0.35 * s, Glow.SPARK);
      HeroImpactFx.play(HEAVY, at, at, s, null, seed);
   }

   private static void primeShockwave(ClientLevel level, Vec3 at, float radius, long seed) {
      float s = Mth.clamp(radius / 7.0F, 0.4F, 1.8F);
      Vec3 g = at.add(0.0, 0.15, 0.0);
      pulse(g, new Vec3(0, 1, 0), 0.5F, radius, 14, 1.0F, RED, 64, 0);
      pulse(g, new Vec3(0, 1, 0), 0.3F, radius * 0.75F, 12, 0.8F, ORANGE, 64, 2);
      pulse(g, new Vec3(0, 1, 0), 0.2F, radius * 0.45F, 10, 0.6F, HOT, 64, 4);
      flash(g, 2.0F * s, 5, Glow.FLASH);
      for (int i = 0; i < 24; i++) {
         double a = i / 24.0 * Math.PI * 2.0;
         Vec3 v = new Vec3(Math.cos(a), 0.0, Math.sin(a)).scale(0.5 * s);
         glow(g, v.add(0, 0.06, 0), 0.35F * s, 0.06F, 10 + RANDOM.nextInt(5), Glow.FIRE, 0.85F, 0.0F, 0.0F);
      }
      HeroImpactFx.play(PRIME_SLAM, at, at, s, null, seed);
   }

   private static void primeTransition(ClientLevel level, Vec3 at, float power, long seed) {
      float s = Mth.clamp(power, 0.6F, 2.5F);
      flash(at, 3.0F * s, 8, Glow.FLASH);
      addBeam(new Beam(at.add(0.0, -2.0, 0.0), at.add(0.0, 40.0, 0.0), 26, 0.9F * s, RED, 0.0F, seed));
      for (int i = 0; i < 4; i++) {
         pulse(at, new Vec3(0, 1, 0), 0.5F, (4.0F + i * 3.0F) * s, 16 + i * 4, 0.9F, i % 2 == 0 ? RED : ORANGE, 64, i * 3);
      }
      for (int i = 0; i < 40; i++) {
         glow(at.add(gauss(1.0)), gauss(0.25).add(0, 0.15, 0), 0.2F, 0.04F, 20 + RANDOM.nextInt(12), Glow.RED, 0.9F, 0.0F, 0.01F);
      }
      UltronHudOverlay.glitch(0.6F, 16);
      HeroImpactFx.play(TRANSITION, at.add(0.0, -1.5, 0.0), at, s, null, seed);
   }

   private static void primeAssemble(Vec3 centre, int id) {
      addSwirl(new Swirl(centre, 7.0F, 140, id));
      flash(centre, 2.0F, 10, Glow.HOT);
      pulse(centre, new Vec3(0, 1, 0), 8.0F, 0.5F, 40, 0.8F, RED, 64, 0);
   }

   private static void primeDeath(ClientLevel level, Vec3 at, long seed) {
      flash(at, 6.0F, 12, Glow.FLASH);
      glow(at, Vec3.ZERO, 3.0F, 8.0F, 14, Glow.RED, 0.9F, 0.0F, 0.0F);
      for (int i = 0; i < 4; i++) {
         fireball(at.add(gauss(1.0)), 2.0F);
      }
      sparks(at, new Vec3(0, 0.5, 0), 70, 0.8, Glow.SPARK);
      for (int i = 0; i < 5; i++) {
         pulse(at, new Vec3(0, 1, 0), 0.5F, 6.0F + i * 4.0F, 18 + i * 5, 1.0F, i % 2 == 0 ? RED : ORANGE, 64, i * 4);
      }
      particles(level, ParticleTypes.LARGE_SMOKE, at, 30, 3.0, 0.09, true);
      particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 10, 2.0, 0.04, true);
      UltronHudOverlay.glitch(1.0F, 30);
      HeroImpactFx.play(PRIME_DEATH, at, at, 1.0F, null, seed);
   }

   private static void capture(Vec3 from, Vec3 to, float power, int id) {
      Beam b = new Beam(from, to, power >= 0.9F ? 26 : 12, power >= 0.9F ? 0.12F : 0.06F, DATA, 0.25F, RANDOM.nextLong());
      b.pulses = true;
      addBeam(b);
      if (power >= 0.9F) {
         flash(to, 1.4F, 6, Glow.RED);
         for (int i = 0; i < 3; i++) {
            pulse(to.add(0.0, -1.0 + i * 0.8, 0.0), new Vec3(0, 1, 0), 2.0F, 0.4F, 14, 0.8F, RED, 6, i * 4);
         }
      }
   }

   private static void release(ClientLevel level, Vec3 at) {
      flash(at, 1.8F, 6, Glow.COLD);
      pulse(at, new Vec3(0, 1, 0), 0.3F, 3.5F, 14, 0.9F, COLD, 64, 0);
      for (int i = 0; i < 24; i++) {
         glow(at.add(gauss(0.5)), gauss(0.08).add(0, 0.1, 0), 0.14F, 0.03F, 16 + RANDOM.nextInt(10), Glow.COLD, 0.9F, 0.0F, 0.0F);
      }
      particles(level, ParticleTypes.ELECTRIC_SPARK, at, 16, 1.0, 0.2, true);
   }

   private static void dashTrail(Vec3 at, Vec3 dir) {
      Vec3 n = dir.lengthSqr() > 1.0E-6 ? dir.normalize() : Vec3.ZERO;
      addBeam(new Beam(at.subtract(n.scale(7.0)), at, 9, 0.55F, RED, 0.0F, 0L));
      for (int i = 0; i < 14; i++) {
         Vec3 p = at.subtract(n.scale(RANDOM.nextDouble() * 7.0)).add(gauss(0.4));
         glow(p, gauss(0.03), 0.3F, 0.05F, 8 + RANDOM.nextInt(6), Glow.RED, 0.7F, 0.0F, 0.0F);
      }
   }

   private static void upload(Vec3 from, Vec3 to, int id) {
      Beam b = new Beam(from, to, 20, 0.16F, RED, 0.06F, RANDOM.nextLong());
      b.pulses = true;
      addBeam(b);
      glow(from, Vec3.ZERO, 0.5F, 1.2F, 8, Glow.RED, 0.8F, 0.0F, 0.0F);
   }

   private static void charge(Vec3 at, Vec3 d, float power) {
      float s = Mth.clamp(power, 0.3F, 2.5F);
      int n = (int)(8 + 10 * s);
      for (int i = 0; i < n; i++) {
         Vec3 off = gauss(1.0).normalize().scale((0.6 + RANDOM.nextDouble() * 0.6) * s);
         int life = 8 + RANDOM.nextInt(6);
         glow(at.add(off), off.scale(-1.0 / life * 1.1), 0.08F, 0.16F, life, Glow.RED, 0.8F, 0.0F, 0.0F);
      }
      glow(at, Vec3.ZERO, 0.1F * s, 0.6F * s, 10, Glow.RED, 0.6F, 0.0F, 0.0F);
      if (d.lengthSqr() > 1.0E-6 && s >= 1.4F) {
         // the brute's charge: dust kicked back from the feet
         ClientLevel level = Minecraft.getInstance().level;
         if (level != null) {
            particles(level, ParticleTypes.POOF, at.subtract(0.0, 1.2, 0.0), 8, 0.8, 0.05, false);
         }
      }
   }

   private static void jet(ClientLevel level, Vec3 at, Vec3 d, float power, long seed) {
      Vec3 n = d.lengthSqr() > 1.0E-6 ? d.normalize() : new Vec3(0, -1, 0);
      float s = Mth.clamp(power, 0.5F, 2.5F);
      for (int i = 0; i < (int)(14 * s); i++) {
         glow(at, n.scale(0.25 + RANDOM.nextDouble() * 0.3).add(gauss(0.06)), 0.25F * s, 0.5F * s, 6 + RANDOM.nextInt(5), Glow.FIRE, 0.85F, 0.0F, 0.0F);
      }
      flash(at, 0.8F * s, 4, Glow.HOT);
      if (n.y < -0.5 && nearGround(level, at, 2.5)) {
         pulse(at.add(0.0, -0.1, 0.0), new Vec3(0, 1, 0), 0.3F, 2.5F * s, 10, 0.7F, ORANGE, 64, 0);
         HeroImpactFx.play(LANDING, at, at, s, null, seed);
      }
   }

   private static void shutdown(ClientLevel level, Vec3 at, float power) {
      float s = Mth.clamp(power, 0.5F, 3.0F);
      for (int i = 0; i < (int)(10 * s); i++) {
         glow(at.add(gauss(0.4 * s)), Vec3.ZERO, 0.05F + RANDOM.nextFloat() * 0.1F, 0.0F, 2 + RANDOM.nextInt(8), Glow.RED, 1.0F, 0.0F, 0.0F);
      }
      sparks(at, Vec3.ZERO, (int)(6 * s), 0.15, Glow.SPARK);
      particles(level, ParticleTypes.SMOKE, at, (int)(4 * s), 0.5, 0.03, true);
   }

   // ------------------------------------------------------------------ building blocks

   static void fireball(Vec3 at, float s) {
      for (int i = 0; i < (int)(8 + 8 * s); i++) {
         glow(at.add(gauss(0.25 * s)), gauss(0.07 * s).add(0, 0.03, 0), 0.4F * s, 0.9F * s, 12 + RANDOM.nextInt(10), Glow.FIRE, 0.9F, 0.0F, 0.012F);
      }
      for (int i = 0; i < (int)(5 * s); i++) {
         glow(at, gauss(0.22 * s).add(0, 0.12, 0), 0.08F, 0.02F, 18 + RANDOM.nextInt(14), Glow.SPARK, 1.0F, 0.025F, 0.0F);
      }
   }

   static void sparks(Vec3 at, Vec3 dir, int n, double speed, int kind) {
      Vec3 base = dir.lengthSqr() > 1.0E-6 ? dir.normalize() : Vec3.ZERO;
      for (int i = 0; i < n; i++) {
         Vec3 v = base.scale(speed * 0.6).add(gauss(speed * 0.6)).add(0, speed * 0.3, 0);
         glow(at, v, 0.07F, 0.02F, 8 + RANDOM.nextInt(10), kind, 1.0F, 0.03F, 0.0F);
      }
   }

   static void flash(Vec3 at, float size, int life, int kind) {
      glow(at, Vec3.ZERO, size * 0.4F, size, life, kind, 1.0F, 0.0F, 0.0F);
   }

   static void glow(Vec3 at, Vec3 v, float size0, float size1, int life, int kind, float alpha, float gravity, float lift) {
      if (GLOWS.size() >= MAX_GLOWS) {
         GLOWS.remove(0);
      }
      GLOWS.add(new Glow(at, v, size0, size1, life, kind, alpha, gravity, lift));
   }

   static void pulse(Vec3 centre, Vec3 normal, float from, float to, int life, float strength, int[] col, int segments, int delay) {
      if (PULSES.size() >= MAX_PULSES) {
         PULSES.remove(0);
      }
      PULSES.add(new Pulse(centre, normal, from, to, life, strength, col, segments, delay));
   }

   private static void addBeam(Beam b) {
      if (BEAMS.size() >= MAX_BEAMS) {
         BEAMS.remove(0);
      }
      BEAMS.add(b);
   }

   private static void addMark(Mark m) {
      if (MARKS.size() >= MAX_MARKS) {
         MARKS.remove(0);
      }
      MARKS.add(m);
   }

   private static void addSwirl(Swirl s) {
      if (SWIRLS.size() >= MAX_SWIRLS) {
         SWIRLS.remove(0);
      }
      SWIRLS.add(s);
   }

   private static void addPiece(Piece p) {
      if (PIECES.size() >= MAX_PIECES) {
         PIECES.remove(0);
      }
      PIECES.add(p);
   }

   /** A part flying off: its frame at the break, thrown outward from the body. */
   @Nullable
   private static Piece piece(UltronAssets.Model assets, String bone, Set<String> exclude, Matrix4f m, Vec3 origin, Vec3 inherit, float force) {
      BakedGeoModel model = AnimCache.model(assets.geo());
      GeoBone b = model == null ? null : model.getBone(bone);
      if (b == null) {
         return null;
      }
      Vector3f c = m.transformPosition(new Vector3f(b.pivotX / 16.0F, b.pivotY / 16.0F, b.pivotZ / 16.0F));
      Vec3 out = new Vec3(c.x, 0.0, c.z);
      out = out.lengthSqr() > 1.0E-4 ? out.normalize() : gauss(1.0).multiply(1.0, 0.0, 1.0).normalize();
      Vec3 vel = inherit.scale(0.6).add(out.scale((0.07 + RANDOM.nextDouble() * 0.12) * force)).add(0.0, (0.12 + RANDOM.nextDouble() * 0.2) * force, 0.0);
      Vector3f axis = new Vector3f((float)RANDOM.nextGaussian(), (float)RANDOM.nextGaussian(), (float)RANDOM.nextGaussian());
      if (axis.lengthSquared() < 1.0E-4F) {
         axis.set(0.0F, 1.0F, 0.0F);
      }
      axis.normalize();
      boolean core = bone.equals("chest") || bone.equals("body") || bone.equals("hips");
      float spin = (float)Math.toRadians(6.0 + RANDOM.nextDouble() * 18.0) * (core ? 0.4F : 1.0F);
      return new Piece(assets, bone, exclude, new Matrix4f(m), origin, c, vel, axis, spin, 150 + RANDOM.nextInt(60));
   }

   private static int[] kindColor(int kind) {
      return switch (kind) {
         case 1 -> new int[]{255, 80, 40};
         case 2 -> new int[]{255, 120, 40};
         case 3 -> new int[]{255, 168, 60};
         case 4 -> new int[]{255, 40, 90};
         case 5 -> new int[]{255, 96, 70};
         default -> RED;
      };
   }

   @Nullable
   private static UltronNodeEntity nearestNode(ClientLevel level, Vec3 at, double range) {
      UltronNodeEntity best = null;
      double d = range * range;
      for (UltronNodeEntity n : level.getEntitiesOfClass(UltronNodeEntity.class, new AABB(at, at).inflate(range))) {
         double dsq = n.position().distanceToSqr(at);
         if (dsq < d) {
            d = dsq;
            best = n;
         }
      }
      return best;
   }

   static void particles(ClientLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed, boolean always) {
      for (int i = 0; i < count; i++) {
         double x = at.x + RANDOM.nextGaussian() * spread * 0.5;
         double y = at.y + RANDOM.nextGaussian() * spread * 0.35;
         double z = at.z + RANDOM.nextGaussian() * spread * 0.5;
         double vx = RANDOM.nextGaussian() * speed;
         double vy = Math.abs(RANDOM.nextGaussian()) * speed;
         double vz = RANDOM.nextGaussian() * speed;
         if (always) {
            level.addAlwaysVisibleParticle(type, true, x, y, z, vx, vy, vz);
         } else {
            level.addParticle(type, x, y, z, vx, vy, vz);
         }
      }
   }

   static Vec3 gauss(double s) {
      return new Vec3(RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s);
   }

   static boolean nearGround(ClientLevel level, Vec3 at, double depth) {
      for (int i = 0; i <= (int)Math.ceil(depth); i++) {
         BlockPos pos = BlockPos.containing(at.x, at.y - i, at.z);
         if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return true;
         }
      }
      return false;
   }

   // ------------------------------------------------------------------ tick

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         clear();
      }
      if (mc.level == null || mc.isPaused()) {
         return;
      }
      clock++;
      for (Iterator<Glow> it = GLOWS.iterator(); it.hasNext();) {
         Glow g = it.next();
         g.px = g.x;
         g.py = g.y;
         g.pz = g.z;
         g.x += g.vx;
         g.y += g.vy;
         g.z += g.vz;
         g.vx *= 0.9;
         g.vy = g.vy * 0.9 - g.gravity + g.lift;
         g.vz *= 0.9;
         if (++g.age >= g.life) {
            it.remove();
         }
      }
      for (Iterator<Piece> it = PIECES.iterator(); it.hasNext();) {
         Piece p = it.next();
         p.tick(mc.level);
         if (p.age >= p.life) {
            it.remove();
         }
      }
      PULSES.removeIf(p -> ++p.age >= p.life + p.delay);
      BEAMS.removeIf(b -> ++b.age >= b.life + b.delay);
      MARKS.removeIf(m -> ++m.age >= m.life || m.follow != null && m.follow.isRemoved());
      for (Iterator<Swirl> it = SWIRLS.iterator(); it.hasNext();) {
         Swirl s = it.next();
         s.tick();
         if (s.age >= s.life) {
            it.remove();
         }
      }
      BEAM_ENDS.values().removeIf(b -> clock - b.tick() > 40);
      machines(mc);
   }

   /** Damaged machines smoke and spark, dying ones burn, missiles leave smoke; captured machines glow red. */
   private static void machines(Minecraft mc) {
      if (mc.player == null) {
         return;
      }
      ClientLevel level = mc.level;
      Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
      AABB box = new AABB(eye, eye).inflate(EFFECT_RANGE);
      for (UltronUnit u : level.getEntitiesOfClass(UltronUnit.class, box)) {
         Vec3 c = u.position().add(0.0, u.getBbHeight() * 0.6, 0.0);
         int t = u.tickCount + u.getId();
         if (!u.isAlive()) {
            if (!u.clientShattered && t % 2 == 0) {
               level.addParticle(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 0.0, 0.05, 0.0);
               if (RANDOM.nextInt(3) == 0) {
                  sparks(c, Vec3.ZERO, 2, 0.15, Glow.SPARK);
               }
            }
            continue;
         }
         if (u.flag(UltronUnit.FLAG_CRITICAL)) {
            if (t % 3 == 0) {
               level.addParticle(ParticleTypes.SMOKE, c.x + RANDOM.nextGaussian() * 0.2, c.y, c.z + RANDOM.nextGaussian() * 0.2, 0.0, 0.04, 0.0);
            }
            if (t % 9 == 0) {
               sparks(c.add(gauss(0.2)), Vec3.ZERO, 3, 0.12, Glow.SPARK);
            }
         } else if (u.flag(UltronUnit.FLAG_DAMAGED) && t % 10 == 0) {
            level.addParticle(ParticleTypes.SMOKE, c.x, c.y, c.z, 0.0, 0.03, 0.0);
         }
      }
      for (UltronMissile m : level.getEntitiesOfClass(UltronMissile.class, box.inflate(16.0))) {
         Vec3 v = m.getDeltaMovement();
         Vec3 back = v.lengthSqr() > 1.0E-6 ? m.position().subtract(v.normalize().scale(0.35)) : m.position();
         level.addParticle(ParticleTypes.SMOKE, back.x, back.y + 0.15, back.z, 0.0, 0.01, 0.0);
         if (m.tickCount % 2 == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, back.x, back.y + 0.15, back.z, 0.0, 0.005, 0.0);
         }
      }
      UltronStateS2CPacket s = UltronClientState.get();
      if (s != null && clock % 4 == 0) {
         for (int id : s.captured()) {
            Entity e = level.getEntity(id);
            if (e != null && e.distanceToSqr(eye) < EFFECT_RANGE * EFFECT_RANGE) {
               glow(e.position().add(gauss(0.3)).add(0.0, e.getBbHeight() * (0.3 + RANDOM.nextDouble() * 0.6), 0.0), new Vec3(0, 0.04, 0), 0.1F, 0.02F, 14,
                  Glow.RED, 0.8F, 0.0F, 0.0F);
            }
         }
      }
   }

   static void clear() {
      GLOWS.clear();
      PIECES.clear();
      PULSES.clear();
      BEAMS.clear();
      MARKS.clear();
      SWIRLS.clear();
      BEAM_ENDS.clear();
   }

   /** Effects alive right now (tests, the debug HUD). */
   public static int active() {
      return GLOWS.size() + PIECES.size() + PULSES.size() + BEAMS.size() + MARKS.size() + SWIRLS.size();
   }

   // ------------------------------------------------------------------ render

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_ENTITIES && !PIECES.isEmpty()) {
         renderPieces(event);
      } else if (event.getStage() == Stage.AFTER_LEVEL) {
         boolean state = UltronClientState.active();
         if (state || !GLOWS.isEmpty() || !PULSES.isEmpty() || !BEAMS.isEmpty() || !MARKS.isEmpty()) {
            renderGlows(event, state);
         }
      }
   }

   private static void renderPieces(RenderLevelStageEvent event) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return;
      }
      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      Set<RenderType> used = new HashSet<>();
      for (int pass = 0; pass < 2; pass++) {
         for (Piece p : PIECES) {
            BakedGeoModel model = AnimCache.model(p.assets.geo());
            GeoBone bone = model == null ? null : model.getBone(p.bone);
            if (bone == null) {
               continue;
            }
            float age = p.age + partial;
            float alpha = 1.0F - Mth.clamp((age - (p.life - 30)) / 30.0F, 0.0F, 1.0F);
            float heat = 1.0F - Mth.clamp(age / 50.0F, 0.0F, 1.0F);
            if (pass == 1 && heat <= 0.02F) {
               continue;
            }
            model.resetBones();
            for (String ex : p.exclude) {
               UltronUnitRenderer.hideTree(model.getBone(ex), true);
            }
            bone.rotX = bone.rotY = bone.rotZ = 0.0F;
            double x = Mth.lerp(partial, p.px, p.x);
            double y = Mth.lerp(partial, p.py, p.y);
            double z = Mth.lerp(partial, p.pz, p.z);
            stack.pushPose();
            stack.translate(x - cam.x, y - cam.y, z - cam.z);
            stack.mulPose(new Quaternionf(p.prev).slerp(p.rot, partial));
            stack.translate(-p.centre.x, -p.centre.y, -p.centre.z);
            stack.last().pose().mul(p.frame);
            stack.last().normal().mul(new Matrix3f(p.frame).normal());
            if (pass == 0) {
               int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.2, z));
               RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(p.assets.texture()) : RenderType.entityCutoutNoCull(p.assets.texture());
               used.add(type);
               float tint = 0.55F + 0.45F * (1.0F - heat);
               AnimRenderer.renderBone(bone, stack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, tint, tint * 0.95F, tint * 0.92F, alpha);
            } else {
               RenderType type = RenderType.eyes(p.assets.glow());
               used.add(type);
               float g = heat * alpha * (((int)(age * 1.3F) & 3) == 0 ? 0.4F : 1.0F);
               AnimRenderer.renderBone(bone, stack, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, g, g * 0.6F, g * 0.5F, 1.0F);
            }
            stack.popPose();
         }
      }
      for (RenderType type : used) {
         buffers.endBatch(type);
      }
   }

   private static void renderGlows(RenderLevelStageEvent event, boolean state) {
      float partial = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 eye = camera.getPosition();
      Vector3f left = camera.getLeftVector();
      Vector3f up = camera.getUpVector();
      PoseStack modelView = RenderSystem.getModelViewStack();
      modelView.pushPose();
      modelView.setIdentity();
      PixelVfx.levelView(modelView, event);
      RenderSystem.applyModelViewMatrix();
      RenderSystem.enableBlend();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      Tesselator tess = Tesselator.getInstance();
      BufferBuilder buf = tess.getBuilder();
      Matrix4f m = new Matrix4f();
      try {
         buf.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         Vector3f c = new Vector3f();
         for (Glow g : GLOWS) {
            float t = Mth.clamp((g.age + partial) / g.life, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - t) * (1.0F - t);
            float size = Mth.lerp(ease, g.size0, g.size1);
            float a = g.alpha * (float)Math.pow(1.0F - t, 1.4);
            int[] col = g.colour(t);
            c.set((float)(Mth.lerp(partial, g.px, g.x) - eye.x), (float)(Mth.lerp(partial, g.py, g.y) - eye.y), (float)(Mth.lerp(partial, g.pz, g.z) - eye.z));
            GlowDraw.billboard(buf, m, c, left, up, size, g.kind == Glow.FIRE ? 0.35F : 0.25F, col[0], col[1], col[2], (int)(255 * a));
         }
         for (Pulse p : PULSES) {
            float age = p.age + partial - p.delay;
            if (age < 0.0F) {
               continue;
            }
            float t = Mth.clamp(age / p.life, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float r = Mth.lerp(ease, p.from, p.to);
            int a = (int)(220 * p.strength * (1.0F - t) * Math.min(1.0F, age / 2.0F));
            c.set((float)(p.centre.x - eye.x), (float)(p.centre.y - eye.y), (float)(p.centre.z - eye.z));
            float w = Math.max(0.06F, Math.abs(p.to - p.from) * 0.08F);
            GlowDraw.ring(buf, m, c, p.a, p.b, Math.max(0.0F, r - w), r + w * 0.4F, 0.7F, p.segments, p.col[0], p.col[1], p.col[2], a);
            GlowDraw.ring(buf, m, c, p.a, p.b, Math.max(0.0F, r - w * 3.0F), r, 0.85F, p.segments, p.col[0], p.col[1] / 2, p.col[2] / 2, a / 3);
         }
         for (Beam b : BEAMS) {
            b.draw(buf, m, eye, partial);
         }
         for (Mark k : MARKS) {
            k.draw(buf, m, eye, partial);
         }
         if (state) {
            UltronWorldFx.draw(buf, m, eye, partial, clock);
         }
         tess.end();
      } finally {
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         modelView.popPose();
         RenderSystem.applyModelViewMatrix();
      }
   }
}
