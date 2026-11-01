package dev.baranhan.viltrumitecore.client.chitauri;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.anim.AnimCache;
import dev.baranhan.viltrumitecore.client.anim.geo.BakedGeoModel;
import dev.baranhan.viltrumitecore.client.anim.geo.GeoBone;
import dev.baranhan.viltrumitecore.client.anim.render.AnimRenderer;
import dev.baranhan.viltrumitecore.client.render.vfx.CameraShake;
import dev.baranhan.viltrumitecore.client.render.vfx.ElectricVfx;
import dev.baranhan.viltrumitecore.client.render.vfx.FlashOverlay;
import dev.baranhan.viltrumitecore.client.render.vfx.FxRecipe;
import dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.worldevent.chitauri.net.ChitauriFxS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.UnaryOperator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client effects of the invasion ({@link ChitauriFxS2CPacket}): energy impacts, fireballs with embers and lingering
 * smoke, chariot wrecks (the model's own parts tumbling, burning, bouncing and fading), Leviathan hits, roars, smashes
 * and the crash, landings, portal strikes and the sever pulse. Built from the shared VFX (HeroImpactFx recipes,
 * ElectricVfx channels, CameraShake, FlashOverlay, vanilla particles) plus soft additive glows and pulse rings drawn
 * here. Every list is capped; a level change clears it. Render thread.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ChitauriVfx {
   private static final int MAX_GLOWS = 420;
   private static final int MAX_PIECES = 64;
   private static final int MAX_PULSES = 10;
   private static final List<Glow> GLOWS = new ArrayList<>();
   private static final List<Piece> PIECES = new ArrayList<>();
   private static final List<Pulse> PULSES = new ArrayList<>();
   private static final Random RANDOM = new Random();
   private static ClientLevel lastLevel;

   static final FxRecipe BOLT = recipe("bolt", 3001, b -> b.flash(0, 3, 0.7F).sparks(0, 8, 8, 0.35F).lightPool(0, 7, 2.2F, 0.8F));
   static final FxRecipe CANNON = recipe("cannon", 3002, b -> b.flash(0, 5, 2.0F).sparks(0, 10, 18, 0.6F).lightPool(0, 12, 4.0F, 1.1F)
      .shake(0, 0.22F, 26.0F));
   static final FxRecipe BLAST_GROUND = recipe("blast_ground", 3003, b -> b.dust(0, 2.4F, 18).debris(0, 12, 3.5F, 0.8F)
      .shockwave(0, 12, 3.5F, 0.0F).scorch(0, 500, 1.8F, 9));
   static final FxRecipe BLAST = recipe("blast", 3004, b -> b.flash(0, 6, 3.0F).sparks(0, 14, 26, 0.8F).lightPool(0, 14, 6.0F, 1.2F)
      .shake(0, 0.45F, 44.0F).screenFlash(0, 5, 0.18F, 22.0F));
   static final FxRecipe CRASH = recipe("crash", 3005, b -> b.flash(0, 8, 6.0F).shockwave(0, 26, 15.0F, 2.0F).dust(0, 9.0F, 90).debris(0, 48, 10.0F, 1.5F)
      .shake(0, 1.2F, 110.0F).screenFlash(0, 10, 0.3F, 60.0F).lightPool(0, 30, 12.0F, 1.0F).dust(10, 12.0F, 50).shake(12, 0.4F, 80.0F));
   static final FxRecipe SMASH = recipe("smash", 3006, b -> b.dust(0, 2.6F, 16).debris(0, 12, 3.5F, 0.9F).shake(0, 0.25F, 48.0F));
   static final FxRecipe SEGMENT = recipe("segment", 3007, b -> b.dust(0, 3.0F, 26).debris(0, 10, 3.0F, 0.7F).shockwave(0, 10, 4.0F, 1.0F)
      .shake(0, 0.5F, 64.0F));
   static final FxRecipe LANDING = recipe("landing", 3008, b -> b.dust(0, 1.3F, 10).shake(0, 0.06F, 10.0F));
   static final FxRecipe HIT = recipe("hit", 3009, b -> b.flash(0, 4, 1.6F).sparks(0, 10, 16, 0.6F).lightPool(0, 6, 3.0F, 0.6F));
   // city war units
   static final FxRecipe SLAM_FX = recipe("slam", 3010, b -> b.dust(0, 3.2F, 26).debris(0, 16, 4.0F, 1.0F).shockwave(0, 14, 6.0F, 0.0F)
      .shake(0, 0.5F, 40.0F).scorch(0, 400, 2.4F, 10));
   static final FxRecipe SIEGE_BLAST = recipe("siege_blast", 3011, b -> b.flash(0, 7, 4.5F).sparks(0, 16, 32, 1.0F).lightPool(0, 18, 8.0F, 1.3F)
      .shockwave(0, 18, 7.0F, 1.0F).dust(0, 4.5F, 40).debris(0, 26, 6.0F, 1.2F).shake(0, 0.8F, 70.0F).screenFlash(0, 6, 0.22F, 30.0F)
      .scorch(0, 700, 3.0F, 12));
   static final FxRecipe BEACON_BLAST = recipe("beacon_blast", 3012, b -> b.flash(0, 6, 3.0F).sparks(0, 14, 28, 0.8F).lightPool(0, 16, 6.0F, 1.0F)
      .debris(0, 18, 4.0F, 0.9F).shake(0, 0.35F, 48.0F));
   static final FxRecipe ARMOR_FX = recipe("armor_break", 3013, b -> b.flash(0, 4, 1.8F).sparks(0, 12, 22, 0.7F).debris(0, 10, 2.0F, 0.6F)
      .shake(0, 0.12F, 18.0F));

   private ChitauriVfx() {
   }

   private static FxRecipe recipe(String name, int net, UnaryOperator<FxRecipe.Builder> layers) {
      return layers.apply(FxRecipe.builder(new FxPreset("viltrumitecore:chitauri_" + name, net))).build();
   }

   // ------------------------------------------------------------------ entry points

   public static void accept(ChitauriFxS2CPacket p) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return;
      }
      Vec3 at = p.at();
      Vec3 d = p.d();
      long seed = p.extra() * 0x9E3779B97F4A7C15L + p.kind();
      switch (p.kind()) {
         case ChitauriFxS2CPacket.BOLT_IMPACT -> boltImpact(level, at, d, seed);
         case ChitauriFxS2CPacket.CANNON_IMPACT -> cannonImpact(level, at, d, seed);
         case ChitauriFxS2CPacket.EXPLOSION -> explosion(level, at, d, p.power(), seed);
         case ChitauriFxS2CPacket.CHARIOT_BREAKUP -> breakup(level, at, d, p.power(), Float.intBitsToFloat(p.extra()));
         case ChitauriFxS2CPacket.LEVIATHAN_HIT -> leviathanHit(level, at, p.power(), seed);
         case ChitauriFxS2CPacket.LEVIATHAN_ROAR -> roar(level, at, d, p.power(), seed);
         case ChitauriFxS2CPacket.LEVIATHAN_CRASH -> crash(level, at, d, seed);
         case ChitauriFxS2CPacket.LEVIATHAN_SMASH -> smash(level, at, d, p.power(), seed);
         case ChitauriFxS2CPacket.DROP_LANDING -> HeroImpactFx.play(LANDING, at, at, 1.0F, null, seed);
         case ChitauriFxS2CPacket.PORTAL_STRIKE -> strike(at, d, p.power(), p.extra(), seed);
         case ChitauriFxS2CPacket.SEVER -> sever(at, d);
         case ChitauriFxS2CPacket.MUZZLE -> muzzleFlash(at, d, Math.max(0.5F, p.power()));
         case ChitauriFxS2CPacket.SEGMENT_LAND -> segmentLand(level, at, p.power(), seed);
         case ChitauriFxS2CPacket.VANISH -> vanish(level, at, Math.max(1.0F, p.power()));
         case ChitauriFxS2CPacket.TETHER -> tether(level, at, d, p.power(), seed);
         case ChitauriFxS2CPacket.SLAM -> slam(level, at, d, seed);
         case ChitauriFxS2CPacket.SIEGE_CHARGE -> siegeCharge(at, d, p.power());
         case ChitauriFxS2CPacket.SIEGE_IMPACT -> siegeImpact(level, at, d, seed);
         case ChitauriFxS2CPacket.BEACON_DEPLOY -> beaconDeploy(level, at, p.power(), seed);
         case ChitauriFxS2CPacket.BEACON_DOWN -> beaconDown(level, at, p.power(), seed);
         case ChitauriFxS2CPacket.SIGNAL -> signal(at, seed);
         case ChitauriFxS2CPacket.RELEASE -> release(level, at, seed);
         case ChitauriFxS2CPacket.ARMOR_BREAK -> armorBreak(level, at, d, seed);
         case ChitauriFxS2CPacket.THRUST -> thrust(at, d, p.power());
         default -> {
         }
      }
   }

   /** Rifle shot at the muzzle: a white-blue flash, a spray of energy and a quick light. */
   public static void muzzleFlash(Vec3 at, Vec3 dir, float scale) {
      Vec3 n = dir.lengthSqr() > 1.0E-6 ? dir.normalize() : Vec3.ZERO;
      glow(at, Vec3.ZERO, 0.3F * scale, 0.75F * scale, 4, Glow.ENERGY_HOT, 1.0F, 0.0F, 0.0F);
      glow(at.add(n.scale(0.25)), n.scale(0.05), 0.5F * scale, 1.2F * scale, 3, Glow.ENERGY, 0.7F, 0.0F, 0.0F);
      for (int i = 0; i < 4; i++) {
         Vec3 v = n.scale(0.25 + RANDOM.nextDouble() * 0.2).add(gauss(0.08));
         glow(at, v, 0.06F, 0.02F, 5 + RANDOM.nextInt(4), Glow.ENERGY, 0.9F, 0.25F, 0.0F);
      }
   }

   /** A ring growing in the plane of {@code normal} (sever pulse, implosion). */
   public static void pulse(Vec3 centre, double[] normal, float from, float to, int life, float strength) {
      pulse(centre, normal, from, to, life, strength, 140, 200, 255);
   }

   /** A ring in the plane of {@code normal} with its own colour (unit effects). */
   public static void pulse(Vec3 centre, double[] normal, float from, float to, int life, float strength, int r, int g, int b) {
      if (PULSES.size() >= MAX_PULSES) {
         PULSES.remove(0);
      }
      Pulse p = new Pulse(centre, normal, from, to, life, strength);
      p.cr = r;
      p.cg = g;
      p.cb = b;
      PULSES.add(p);
   }

   // ------------------------------------------------------------------ kinds

   private static void boltImpact(ClientLevel level, Vec3 at, Vec3 d, long seed) {
      HeroImpactFx.play(BOLT, at, at, 1.0F, null, seed);
      glow(at, Vec3.ZERO, 0.4F, 1.1F, 5, Glow.ENERGY_HOT, 0.9F, 0.0F, 0.0F);
      for (int i = 0; i < 5; i++) {
         glow(at, d.scale(-0.12).add(gauss(0.14)), 0.07F, 0.02F, 6 + RANDOM.nextInt(6), Glow.ENERGY, 1.0F, 0.15F, 0.04F);
      }
      particles(level, ParticleTypes.ELECTRIC_SPARK, at, 5, 0.15, 0.25, false);
      particles(level, ParticleTypes.SMOKE, at, 2, 0.1, 0.02, false);
      if (solidAhead(level, at, d)) {
         ElectricVfx.scorch(level, at, 0.55F, 6, 260, seed);
      }
   }

   private static void cannonImpact(ClientLevel level, Vec3 at, Vec3 d, long seed) {
      HeroImpactFx.play(CANNON, at, at, 1.0F, null, seed);
      glow(at, Vec3.ZERO, 1.0F, 2.6F, 6, Glow.ENERGY_HOT, 1.0F, 0.0F, 0.0F);
      fireball(level, at, d.scale(-0.05), 0.55F, false);
      if (nearGround(level, at, 2.5)) {
         HeroImpactFx.play(BLAST_GROUND, at, at, 0.6F, null, seed);
      }
   }

   private static void explosion(ClientLevel level, Vec3 at, Vec3 v, float power, long seed) {
      float s = Mth.clamp(power, 0.3F, 2.0F);
      HeroImpactFx.play(BLAST, at, at, s, null, seed);
      if (nearGround(level, at, 3.0)) {
         HeroImpactFx.play(BLAST_GROUND, at, at, s, null, seed + 1);
      }
      fireball(level, at, v.scale(0.3), s, true);
   }

   /** Fireball: white-hot flash, rolling fire puffs, embers, then smoke that lingers and rises. */
   private static void fireball(ClientLevel level, Vec3 at, Vec3 drift, float s, boolean big) {
      glow(at, drift, 2.5F * s, 6.0F * s, 5, Glow.FLASH, 1.0F, 0.0F, 0.0F);
      int puffs = (int)(5 + 8 * s);
      for (int i = 0; i < puffs; i++) {
         Vec3 off = gauss(0.55 * s);
         glow(at.add(off), drift.add(off.scale(0.12)).add(0, 0.03, 0), 0.9F * s, (2.2F + RANDOM.nextFloat()) * s, 10 + RANDOM.nextInt(9), Glow.FIRE,
            0.9F, 0.0F, -0.004F);
      }
      int embers = (int)(6 + 12 * s);
      for (int i = 0; i < embers; i++) {
         Vec3 v = gauss(0.32 * s).add(0, 0.18 * s, 0).add(drift);
         glow(at, v, 0.16F, 0.05F, 18 + RANDOM.nextInt(18), Glow.FIRE, 1.0F, 0.04F, 0.035F);
      }
      particles(level, ParticleTypes.EXPLOSION, at, big ? (int)(2 + 3 * s) : 1, 0.8 * s, 0.0, true);
      particles(level, ParticleTypes.LARGE_SMOKE, at, (int)(8 + 10 * s), 0.9 * s, 0.06, true);
      particles(level, ParticleTypes.LAVA, at, (int)(2 + 3 * s), 0.3, 0.0, false);
      if (big) {
         particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 2, 0.6 * s, 0.03, true);
      }
   }

   /** The chariot comes apart: its own parts tumble out from where they sat, burning. */
   private static void breakup(ClientLevel level, Vec3 at, Vec3 v, float yaw, float roll) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.CHARIOT_GEO);
      if (model == null || model.topLevelBones().isEmpty()) {
         return;
      }
      model.resetBones();
      Quaternionf q = new Quaternionf().rotateY((float)Math.toRadians(180.0F - yaw)).rotateZ((float)Math.toRadians(-roll));
      Vec3 origin = at.subtract(0, 0.05, 0);
      for (GeoBone top : model.topLevelBones()) {
         for (GeoBone bone : top.children.isEmpty() ? List.of(top) : top.children) {
            if (PIECES.size() >= MAX_PIECES) {
               PIECES.remove(0);
            }
            Vector3f pivot = new Vector3f(bone.pivotX / 16.0F, bone.pivotY / 16.0F, bone.pivotZ / 16.0F);
            Vector3f world = q.transform(new Vector3f(pivot));
            Vec3 pos = origin.add(world.x, world.y, world.z);
            Vec3 out = new Vec3(world.x, world.y * 0.3, world.z);
            out = out.lengthSqr() > 1.0E-4 ? out.normalize() : gauss(1.0).normalize();
            Vec3 vel = v.scale(0.8).add(out.scale(0.12 + RANDOM.nextDouble() * 0.22)).add(0, 0.12 + RANDOM.nextDouble() * 0.22, 0);
            Vector3f axis = new Vector3f((float)RANDOM.nextGaussian(), (float)RANDOM.nextGaussian(), (float)RANDOM.nextGaussian());
            if (axis.lengthSquared() < 1.0E-4F) {
               axis.set(0, 1, 0);
            }
            axis.normalize();
            float spin = (float)Math.toRadians(8.0 + RANDOM.nextDouble() * 22.0) * (bone.name.equals("hull") ? 0.4F : 1.0F);
            PIECES.add(new Piece(bone.name, pos, vel, new Quaternionf(q), axis, spin, 170 + RANDOM.nextInt(60)));
         }
      }
   }

   private static void leviathanHit(ClientLevel level, Vec3 at, float dealt, long seed) {
      float s = Mth.clamp(dealt / 25.0F, 0.5F, 2.2F);
      HeroImpactFx.play(HIT, at, at, s, null, seed);
      glow(at, Vec3.ZERO, 0.8F * s, 2.2F * s, 5, Glow.FLASH, 0.9F, 0.0F, 0.0F);
      for (int i = 0; i < (int)(4 + 4 * s); i++) {
         glow(at, gauss(0.3 * s).add(0, 0.12, 0), 0.12F, 0.04F, 12 + RANDOM.nextInt(10), Glow.FIRE, 1.0F, 0.04F, 0.03F);
      }
      particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()), at, (int)(6 + 6 * s), 0.6, 0.25, true);
      particles(level, ParticleTypes.LARGE_SMOKE, at, (int)(2 + 3 * s), 0.4, 0.04, true);
      if (dealt >= 20.0F) {
         particles(level, ParticleTypes.EXPLOSION, at, 1, 0.3, 0.0, true);
      }
   }

   /** Roar: sound rings rolling out of the jaws, the air shaking, dust lifted below. */
   private static void roar(ClientLevel level, Vec3 snout, Vec3 heading, float power, long seed) {
      Vec3 h = heading.lengthSqr() > 1.0E-6 ? heading.normalize() : new Vec3(0, 0, 1);
      double[] n = {h.x, h.y, h.z};
      for (int k = 0; k < 4; k++) {
         Pulse p = new Pulse(snout.add(h.scale(1.5 + k * 1.2)), n, 1.0F + k * 0.6F, 9.0F + k * 3.0F, 22 + k * 4, 0.45F * power);
         p.drift = h.scale(0.45 + k * 0.08);
         p.delay = k * 4;
         if (PULSES.size() >= MAX_PULSES) {
            PULSES.remove(0);
         }
         PULSES.add(p);
      }
      CameraShake.addAt(snout, 0.55F * power, 70.0);
      Vec3 below = snout.add(h.scale(8.0));
      particles(level, ParticleTypes.POOF, below, 10, 3.0, 0.08, true);
   }

   private static void crash(ClientLevel level, Vec3 at, Vec3 v, long seed) {
      HeroImpactFx.play(CRASH, at, at, 1.0F, null, seed);
      fireball(level, at.add(0, 1.0, 0), v.scale(0.2), 2.0F, true);
      particles(level, ParticleTypes.EXPLOSION_EMITTER, at, 2, 2.0, 0.0, true);
      particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 10, 5.0, 0.04, true);
      particles(level, ParticleTypes.LARGE_SMOKE, at, 40, 6.0, 0.1, true);
      ElectricVfx.skyFlash(at.add(0, 20, 0), 30.0F, 10, seed);
   }

   private static void smash(ClientLevel level, Vec3 at, Vec3 heading, float power, long seed) {
      float s = Mth.clamp(power, 1.0F, 4.0F);
      HeroImpactFx.play(SMASH, at, at, s * 0.6F, null, seed);
      particles(level, ParticleTypes.LARGE_SMOKE, at, (int)(4 * s), 1.5 * s, 0.05, true);
      particles(level, ParticleTypes.POOF, at, (int)(5 * s), 1.2 * s, 0.1, true);
   }

   private static void segmentLand(ClientLevel level, Vec3 at, float scale, long seed) {
      float s = Mth.clamp(scale, 0.3F, 1.2F);
      HeroImpactFx.play(SEGMENT, at, at, s, null, seed);
      particles(level, ParticleTypes.LARGE_SMOKE, at, (int)(8 * s), 2.0 * s, 0.05, true);
      particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 1, 1.0, 0.02, true);
   }

   private static void strike(Vec3 at, Vec3 d, float power, int extra, long seed) {
      if (extra == 2) {
         // the beam ignites: a channel from the anchor into the portal
         ElectricVfx.bolt(at.add(d), at, 0.5F * power, 6, 14, seed);
         ElectricVfx.lightPool(at, 8.0F, 1.5F, 20, seed);
         glow(at.add(0, 1.0, 0), Vec3.ZERO, 3.0F, 9.0F, 8, Glow.ENERGY_HOT, 1.0F, 0.0F, 0.0F);
      } else {
         ElectricVfx.arc(at, at.add(d), 0.45F * Math.max(0.6F, power), 8, 2, seed);
         glow(at, Vec3.ZERO, 2.0F, 5.0F, 5, Glow.ENERGY, 0.8F, 0.0F, 0.0F);
      }
   }

   /** The hive link snaps: a blue shock ring sweeps out of the portal across the sky. */
   private static void sever(Vec3 portal, Vec3 normal) {
      Vec3 n = normal.lengthSqr() > 1.0E-6 ? normal.normalize() : new Vec3(0, -1, 0);
      double[] nn = {n.x, n.y, n.z};
      pulse(portal, nn, 2.0F, 260.0F, 50, 1.0F);
      pulse(portal, nn, 1.0F, 140.0F, 40, 0.6F);
      Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      FlashOverlay.flash(0.4F * CameraShake.falloff(eye.distanceTo(portal), 500.0), 12);
      ElectricVfx.skyFlash(portal, 60.0F, 18, 77L);
      CameraShake.addAt(portal, 0.45F, 480.0);
   }

   private static void vanish(ClientLevel level, Vec3 at, float radius) {
      particles(level, ParticleTypes.SOUL_FIRE_FLAME, at, (int)(8 * radius), radius, 0.05, true);
      particles(level, ParticleTypes.END_ROD, at, (int)(4 * radius), radius, 0.04, true);
   }

   // ------------------------------------------------------------------ city war kinds

   /** Captor snare: a beaded teal tether from the lance fork to the target, crackling, a flare on both ends. */
   private static void tether(ClientLevel level, Vec3 from, Vec3 to, float power, long seed) {
      Vec3 span = to.subtract(from);
      double len = span.length();
      if (len < 0.2 || len > 48.0) {
         return;
      }
      int beads = Mth.clamp((int)(len * 3.0), 4, 90);
      for (int i = 0; i <= beads; i++) {
         double f = i / (double)beads;
         double sag = Math.sin(f * Math.PI) * 0.18 * len / 8.0;
         Vec3 p = from.add(span.scale(f)).add(0, -sag, 0);
         glow(p, gauss(0.004), 0.16F, 0.05F, 8 + RANDOM.nextInt(4), Glow.TETHER, 0.9F * power, 0.0F, 0.0F);
      }
      ElectricVfx.arc(from, to, 0.05F, 6, 2, seed);
      glow(from, Vec3.ZERO, 0.3F, 0.9F, 5, Glow.TETHER, 1.0F, 0.0F, 0.0F);
      glow(to, Vec3.ZERO, 0.5F, 1.4F, 8, Glow.TETHER, 0.9F, 0.0F, 0.0F);
      for (int i = 0; i < 6; i++) {
         glow(to, gauss(0.09), 0.07F, 0.02F, 8 + RANDOM.nextInt(6), Glow.TETHER, 1.0F, 0.1F, 0.0F);
      }
      particles(level, ParticleTypes.ELECTRIC_SPARK, to, 4, 0.4, 0.1, false);
   }

   /** Heavy ground slam: a crater burst, a plasma shock ring along the street and flying debris. */
   private static void slam(ClientLevel level, Vec3 at, Vec3 fwd, long seed) {
      HeroImpactFx.play(SLAM_FX, at, at, 1.0F, null, seed);
      pulse(at.add(0, 0.15, 0), new double[]{0, 1, 0}, 0.5F, 7.0F, 14, 0.9F, 255, 150, 70);
      glow(at.add(0, 0.3, 0), Vec3.ZERO, 1.2F, 3.4F, 6, Glow.PLASMA, 1.0F, 0.0F, 0.0F);
      for (int i = 0; i < 14; i++) {
         double a = i / 14.0 * Math.PI * 2.0;
         glow(at.add(0, 0.2, 0), new Vec3(Math.cos(a) * 0.32, 0.04, Math.sin(a) * 0.32), 0.3F, 0.08F, 9 + RANDOM.nextInt(5), Glow.PLASMA, 0.8F, 0.0F, 0.0F);
      }
      BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
      if (!level.getBlockState(below).isAir()) {
         particles(level, new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), at.add(0, 0.3, 0), 30, 2.4, 0.3, false);
      }
   }

   /** Siege charge (sent every 4 ticks): energy drawn into the muzzle, the core swelling; a targeting line once locked. */
   private static void siegeCharge(Vec3 muzzle, Vec3 aim, float fraction) {
      float f = Mth.clamp(fraction, 0.0F, 1.0F);
      for (int i = 0; i < 3 + (int)(6 * f); i++) {
         Vec3 off = gauss(1.0).normalize().scale(1.6 + RANDOM.nextDouble() * 1.4);
         glow(muzzle.add(off), off.scale(-0.22), 0.12F + 0.1F * f, 0.04F, 5, f > 0.7F ? Glow.ENERGY_HOT : Glow.ENERGY, 0.9F, 0.0F, 0.0F);
      }
      glow(muzzle, Vec3.ZERO, 0.4F + 1.2F * f, 0.6F + 1.6F * f, 5, Glow.ENERGY_HOT, 0.5F + 0.5F * f, 0.0F, 0.0F);
      double len = aim.length();
      if (f >= (ChitauriSiegeRenderer.LOCK_FRACTION) && len > 1.0 && len < 64.0) {
         Vec3 n = aim.scale(1.0 / len);
         for (double s = 1.0; s < len; s += 0.8) {
            glow(muzzle.add(n.scale(s)), Vec3.ZERO, 0.08F, 0.05F, 5, Glow.ENERGY_HOT, 0.55F, 0.0F, 0.0F);
         }
         glow(muzzle.add(aim), Vec3.ZERO, 0.6F, 1.0F, 5, Glow.ENERGY_HOT, 0.8F, 0.0F, 0.0F);
      }
   }

   /** Siege shell impact: a heavy blast, a ground ring, a column of smoke. */
   private static void siegeImpact(ClientLevel level, Vec3 at, Vec3 d, long seed) {
      HeroImpactFx.play(SIEGE_BLAST, at, at, 1.0F, null, seed);
      fireball(level, at, d.scale(-0.04), 1.4F, true);
      pulse(at.add(0, 0.2, 0), new double[]{0, 1, 0}, 1.0F, 12.0F, 18, 1.0F, 150, 205, 255);
      ElectricVfx.crackle(at, 3.5F, 6, 10, seed);
   }

   /** A beacon lands and unfolds: a drop flash, a ground ring and a light column rising with the mast. */
   private static void beaconDeploy(ClientLevel level, Vec3 at, float power, long seed) {
      boolean containment = power > 1.2F;
      int kind = containment ? Glow.TETHER : Glow.ENERGY;
      HeroImpactFx.play(LANDING, at, at, 1.4F, null, seed);
      pulse(at.add(0, 0.1, 0), new double[]{0, 1, 0}, 0.5F, containment ? 6.0F : 4.5F, 22, 0.9F,
         containment ? 90 : 140, containment ? 240 : 200, containment ? 210 : 255);
      for (int i = 0; i < 18; i++) {
         glow(at.add(0, i * 0.18, 0), new Vec3(0, 0.12, 0).add(gauss(0.01)), 0.3F, 0.1F, 12 + i, kind, 0.7F, 0.0F, 0.0F);
      }
      ElectricVfx.lightPool(at, 4.0F, 1.0F, 30, seed);
      particles(level, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.5, 0), 12, 1.0, 0.2, false);
   }

   /** A beacon is destroyed: energy burst, the rings scatter as sparks, a falling shower. */
   private static void beaconDown(ClientLevel level, Vec3 at, float power, long seed) {
      HeroImpactFx.play(BEACON_BLAST, at, at, Mth.clamp(power, 1.0F, 1.6F), null, seed);
      int kind = power > 1.2F ? Glow.TETHER : Glow.ENERGY;
      glow(at.add(0, 1.0, 0), Vec3.ZERO, 1.5F, 4.5F, 7, Glow.ENERGY_HOT, 1.0F, 0.0F, 0.0F);
      for (int i = 0; i < 30; i++) {
         glow(at.add(0, 0.6 + RANDOM.nextDouble() * 2.0, 0), gauss(0.2).add(0, 0.12, 0), 0.12F, 0.03F, 14 + RANDOM.nextInt(12), kind, 1.0F, 0.6F, 0.0F);
      }
      pulse(at, new double[]{0, 1, 0}, 0.5F, 8.0F, 16, 0.9F, 150, 205, 255);
      ElectricVfx.crackle(at.add(0, 1.0, 0), 2.5F, 5, 12, seed);
      particles(level, ParticleTypes.LARGE_SMOKE, at.add(0, 1.0, 0), 8, 1.0, 0.05, false);
   }

   /** Commander signal: a violet column into the sky and a wide ring that tells every squad nearby. */
   private static void signal(Vec3 top, long seed) {
      for (int i = 0; i < 40; i++) {
         glow(top.add(0, i * 0.9, 0), new Vec3(0, 0.25, 0).add(gauss(0.01)), 0.5F - i * 0.006F, 0.15F, 16 + i / 2, Glow.VIOLET, 0.75F, 0.0F, 0.0F);
      }
      glow(top, Vec3.ZERO, 1.2F, 3.5F, 8, Glow.VIOLET, 1.0F, 0.0F, 0.0F);
      pulse(top, new double[]{0, 1, 0}, 1.0F, 40.0F, 30, 0.8F, 180, 130, 255);
      pulse(top.add(0, 12, 0), new double[]{0, 1, 0}, 0.5F, 18.0F, 24, 0.5F, 200, 160, 255);
      ElectricVfx.skyFlash(top.add(0, 20, 0), 20.0F, 10, seed);
   }

   /** A captive breaks free: the restraint field bursts in teal sparks and a small ring. */
   private static void release(ClientLevel level, Vec3 at, long seed) {
      glow(at, Vec3.ZERO, 0.6F, 2.0F, 7, Glow.TETHER, 1.0F, 0.0F, 0.0F);
      for (int i = 0; i < 18; i++) {
         glow(at, gauss(0.12).add(0, 0.05, 0), 0.08F, 0.02F, 10 + RANDOM.nextInt(10), Glow.TETHER, 1.0F, 0.2F, 0.02F);
      }
      pulse(at, new double[]{0, 1, 0}, 0.3F, 2.6F, 12, 0.8F, 90, 240, 210);
      particles(level, ParticleTypes.END_ROD, at, 8, 0.8, 0.06, false);
   }

   /** Heavy chest plate blown off: sparks, metal chunks and a plasma vent from the exposed core. */
   private static void armorBreak(ClientLevel level, Vec3 at, Vec3 look, long seed) {
      HeroImpactFx.play(ARMOR_FX, at, at, 1.0F, null, seed);
      Vec3 n = look.lengthSqr() > 1.0E-6 ? look.normalize() : new Vec3(0, 0, 1);
      for (int i = 0; i < 12; i++) {
         glow(at, n.scale(0.15 + RANDOM.nextDouble() * 0.15).add(gauss(0.08)), 0.2F, 0.05F, 10 + RANDOM.nextInt(8), Glow.PLASMA, 1.0F, 0.3F, 0.0F);
      }
      glow(at, Vec3.ZERO, 0.6F, 1.6F, 6, Glow.PLASMA, 1.0F, 0.0F, 0.0F);
      particles(level, ParticleTypes.CRIT, at, 12, 0.6, 0.3, false);
      particles(level, ParticleTypes.SMOKE, at, 6, 0.4, 0.05, false);
   }

   /** Engine burst (boosting interceptor): a hot cone of exhaust behind the nozzle. */
   static void thrust(Vec3 at, Vec3 back, float power) {
      Vec3 n = back.lengthSqr() > 1.0E-6 ? back.normalize() : new Vec3(0, -1, 0);
      float p = Mth.clamp(power, 0.2F, 2.0F);
      glow(at, n.scale(0.05), 0.35F * p, 0.9F * p, 3, Glow.ENERGY_HOT, 0.8F, 0.0F, 0.0F);
      for (int i = 0; i < 3; i++) {
         glow(at, n.scale(0.25 + RANDOM.nextDouble() * 0.2).add(gauss(0.03)), 0.18F * p, 0.04F, 6 + RANDOM.nextInt(4), Glow.ENERGY, 0.7F, 0.0F, 0.0F);
      }
   }

   /** Exhaust / spark helper for the unit renderers (one glow; kinds as Glow). */
   static void spark(Vec3 at, Vec3 v, float size, int life, int kind, float alpha, float gravity) {
      glow(at, v, size, size * 0.3F, life, kind, alpha, gravity, 0.0F);
   }

   // ------------------------------------------------------------------ helpers

   private static void particles(ClientLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed, boolean always) {
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

   private static Vec3 gauss(double s) {
      return new Vec3(RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s);
   }

   private static boolean nearGround(ClientLevel level, Vec3 at, double depth) {
      for (int i = 0; i <= (int)Math.ceil(depth); i++) {
         BlockPos pos = BlockPos.containing(at.x, at.y - i, at.z);
         if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return true;
         }
      }
      return false;
   }

   private static boolean solidAhead(ClientLevel level, Vec3 at, Vec3 d) {
      Vec3 p = at.add(d.lengthSqr() > 1.0E-6 ? d.normalize().scale(0.3) : Vec3.ZERO);
      BlockPos pos = BlockPos.containing(p);
      return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   private static void glow(Vec3 at, Vec3 v, float size0, float size1, int life, int kind, float alpha, float gravity, float lift) {
      if (GLOWS.size() >= MAX_GLOWS) {
         GLOWS.remove(0);
      }
      GLOWS.add(new Glow(at, v, size0, size1, life, kind, alpha, gravity, lift));
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
         GLOWS.clear();
         PIECES.clear();
         PULSES.clear();
      }
      if (mc.level == null || mc.isPaused()) {
         return;
      }
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
   }

   // ------------------------------------------------------------------ render

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_ENTITIES && !PIECES.isEmpty()) {
         renderPieces(event);
      } else if (event.getStage() == Stage.AFTER_LEVEL && (!GLOWS.isEmpty() || !PULSES.isEmpty())) {
         renderGlows(event);
      }
   }

   private static void renderPieces(RenderLevelStageEvent event) {
      BakedGeoModel model = AnimCache.model(ChitauriAssets.CHARIOT_GEO);
      ClientLevel level = Minecraft.getInstance().level;
      if (model == null || level == null) {
         return;
      }
      model.resetBones();
      float partial = event.getPartialTick();
      Vec3 cam = event.getCamera().getPosition();
      PoseStack stack = event.getPoseStack();
      MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
      RenderType solid = RenderType.entityCutoutNoCull(ChitauriAssets.CHARIOT_TEX);
      RenderType fading = RenderType.entityTranslucent(ChitauriAssets.CHARIOT_TEX);
      RenderType glow = RenderType.eyes(ChitauriAssets.CHARIOT_GLOW);
      for (int pass = 0; pass < 2; pass++) {
         for (Piece p : PIECES) {
            GeoBone bone = model.getBone(p.bone);
            if (bone == null) {
               continue;
            }
            float age = p.age + partial;
            float alpha = 1.0F - Mth.clamp((age - (p.life - 30)) / 30.0F, 0.0F, 1.0F);
            float heat = 1.0F - Mth.clamp(age / 45.0F, 0.0F, 1.0F);
            if (pass == 1 && heat <= 0.02F) {
               continue;
            }
            double x = Mth.lerp(partial, p.px, p.x);
            double y = Mth.lerp(partial, p.py, p.y);
            double z = Mth.lerp(partial, p.pz, p.z);
            stack.pushPose();
            stack.translate(x - cam.x, y - cam.y, z - cam.z);
            stack.mulPose(new Quaternionf(p.prev).slerp(p.rot, partial));
            stack.translate(-bone.pivotX / 16.0F, -bone.pivotY / 16.0F, -bone.pivotZ / 16.0F);
            if (pass == 0) {
               int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.3, z));
               AnimRenderer.renderBone(bone, stack, buffers.getBuffer(alpha < 0.999F ? fading : solid), light, OverlayTexture.NO_OVERLAY, 0.6F, 0.55F,
                  0.52F, alpha);
            } else {
               float g = heat * alpha;
               AnimRenderer.renderBone(bone, stack, buffers.getBuffer(glow), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, g, g * 0.7F, g * 0.5F, 1.0F);
            }
            stack.popPose();
         }
      }
      buffers.endBatch(solid);
      buffers.endBatch(fading);
      buffers.endBatch(glow);
   }

   private static void renderGlows(RenderLevelStageEvent event) {
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
            Vec3 centre = p.centre.add(p.drift.scale(age));
            c.set((float)(centre.x - eye.x), (float)(centre.y - eye.y), (float)(centre.z - eye.z));
            GlowDraw.ring(buf, m, c, p.a, p.b, r * 0.86F, r * 1.04F, 0.7F, 64, p.cr, p.cg, p.cb, a);
            GlowDraw.ring(buf, m, c, p.a, p.b, r * 0.6F, r * 1.0F, 0.85F, 48, p.cr * 3 / 7, p.cg * 11 / 20, p.cb, a / 3);
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

   // ------------------------------------------------------------------ types

   /** A soft additive glow: flash, fire puff, ember or energy spark. */
   static final class Glow {
      static final int ENERGY = 0, ENERGY_HOT = 1, FIRE = 2, FLASH = 3, TETHER = 4, PLASMA = 5, VIOLET = 6;
      double x, y, z, px, py, pz, vx, vy, vz;
      final float size0, size1, alpha, gravity, lift;
      final int life, kind;
      int age;

      Glow(Vec3 at, Vec3 v, float size0, float size1, int life, int kind, float alpha, float gravity, float lift) {
         this.x = this.px = at.x;
         this.y = this.py = at.y;
         this.z = this.pz = at.z;
         this.vx = v.x;
         this.vy = v.y;
         this.vz = v.z;
         this.size0 = size0;
         this.size1 = size1;
         this.life = Math.max(1, life);
         this.kind = kind;
         this.alpha = alpha;
         this.gravity = gravity;
         this.lift = lift;
      }

      /** Colour over the life 0..1 (fire cools from white-yellow through orange to a dull red). */
      int[] colour(float t) {
         return switch (this.kind) {
            case ENERGY -> new int[]{70, 140, 255};
            case ENERGY_HOT -> new int[]{200, 228, 255};
            case FLASH -> new int[]{255, 244, 220};
            case TETHER -> new int[]{80, 240, 210};
            case PLASMA -> t < 0.3F ? mix(255, 230, 180, 255, 140, 50, t / 0.3F) : new int[]{255, 140, 50};
            case VIOLET -> new int[]{175, 120, 255};
            default -> t < 0.25F ? mix(255, 246, 214, 255, 170, 70, t / 0.25F) : mix(255, 170, 70, 150, 45, 22, (t - 0.25F) / 0.75F);
         };
      }

      private static int[] mix(int r0, int g0, int b0, int r1, int g1, int b1, float f) {
         return new int[]{(int)Mth.lerp(f, r0, r1), (int)Mth.lerp(f, g0, g1), (int)Mth.lerp(f, b0, b1)};
      }
   }

   /** A ring growing in a plane, optionally drifting (roar) and delayed. */
   static final class Pulse {
      final Vec3 centre;
      final Vector3f a;
      final Vector3f b;
      final float from, to, strength;
      final int life;
      Vec3 drift = Vec3.ZERO;
      int cr = 140, cg = 200, cb = 255;
      int delay;
      int age;

      Pulse(Vec3 centre, double[] normal, float from, float to, int life, float strength) {
         this.centre = centre;
         double[][] basis = PortalGeometry.basis(PortalGeometry.normalize(normal));
         this.a = new Vector3f((float)basis[0][0], (float)basis[0][1], (float)basis[0][2]);
         this.b = new Vector3f((float)basis[1][0], (float)basis[1][1], (float)basis[1][2]);
         this.from = from;
         this.to = to;
         this.life = Math.max(1, life);
         this.strength = strength;
      }
   }

   /** One chariot part flying off the wreck: tumbles, bounces on the ground, smokes and fades. */
   static final class Piece {
      final String bone;
      double x, y, z, px, py, pz, vx, vy, vz;
      final Quaternionf rot;
      final Quaternionf prev;
      final Vector3f axis;
      float spin;
      final int life;
      int age;
      boolean resting;

      Piece(String bone, Vec3 pos, Vec3 v, Quaternionf rot, Vector3f axis, float spin, int life) {
         this.bone = bone;
         this.x = this.px = pos.x;
         this.y = this.py = pos.y;
         this.z = this.pz = pos.z;
         this.vx = v.x;
         this.vy = v.y;
         this.vz = v.z;
         this.rot = rot;
         this.prev = new Quaternionf(rot);
         this.axis = axis;
         this.spin = spin;
         this.life = life;
      }

      void tick(ClientLevel level) {
         this.age++;
         this.px = this.x;
         this.py = this.y;
         this.pz = this.z;
         this.prev.set(this.rot);
         if (!this.resting) {
            this.vy -= 0.045;
            this.vx *= 0.985;
            this.vz *= 0.985;
            double ny = this.y + this.vy;
            BlockPos below = BlockPos.containing(this.x + this.vx, ny - 0.15, this.z + this.vz);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && this.vy < 0.0) {
               this.y = Math.max(this.y, below.getY() + 1.15);
               this.vy = -this.vy * 0.3;
               this.vx *= 0.55;
               this.vz *= 0.55;
               this.spin *= 0.5F;
               if (Math.abs(this.vy) < 0.06 && this.vx * this.vx + this.vz * this.vz < 0.004) {
                  this.resting = true;
               }
            } else {
               this.y = ny;
            }
            BlockPos side = BlockPos.containing(this.x + this.vx, this.y, this.z + this.vz);
            if (!level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
               this.vx *= -0.3;
               this.vz *= -0.3;
            } else {
               this.x += this.vx;
               this.z += this.vz;
            }
            this.rot.rotateAxis(this.spin, this.axis.x, this.axis.y, this.axis.z);
         } else if (this.age > this.life - 30) {
            this.y -= 0.01;
         }
         if (this.age < 60 && (this.age < 30 || this.age % 3 == 0)) {
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, this.x, this.y + 0.2, this.z, 0.0, 0.03, 0.0);
         }
         if (this.age < 26 && !this.resting) {
            level.addParticle(ParticleTypes.FLAME, this.x, this.y, this.z, 0.0, 0.01, 0.0);
         }
         if (this.resting && this.age < this.life - 40 && this.age % 9 == 0) {
            level.addParticle(ParticleTypes.SMOKE, this.x, this.y + 0.3, this.z, 0.0, 0.03, 0.0);
         }
      }
   }
}
