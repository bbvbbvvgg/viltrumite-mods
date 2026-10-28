package dev.baranhan.viltrumitecore.client.render.vfx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.thor.ThorFx;
import dev.baranhan.viltrumitecore.network.packet.HeroFxS2CPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * Client half of the shared hero FX packet (server API: hero.fx.HeroFx):
 * visible shard streaks with a whiz placed where a shard passes the listener,
 * expanding ground rings for landings and slams, blade streaks, flashes, and
 * a camera shake through {@link CameraShake}. Hero-neutral: the whiz sound
 * comes in the packet.
 */
@EventBusSubscriber(
   modid = "viltrumitecore",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class HeroImpactFx {
   private static final List<Shard> SHARDS = new ArrayList<>();
   private static final List<Ring> RINGS = new ArrayList<>();
   private static final List<Blade> BLADES = new ArrayList<>();
   private static final List<Flash> FLASHES = new ArrayList<>();
   private static final List<Whiz> WHIZZES = new ArrayList<>();
   /** Preset layers waiting for their delay (FxRecipe). */
   private static final List<PendingLayer> PENDING = new ArrayList<>();
   private static final int MAX_PENDING = 512;
   private static final int MAX_WHIZZES_PER_SPRAY = 3;
   private static final double WHIZ_RADIUS = 3.5;
   /** Blocks per tick of a drawn shard; server shards resolve instantly. */
   static final double SHARD_VISUAL_SPEED = 7.0;
   private static ClientLevel lastLevel;

   private HeroImpactFx() {
   }

   /** Ring-only entry point for timed scenes that own camera shake and dust separately. */
   public static void shockwaveVisual(Vec3 origin, float radius) {
      RINGS.add(new Ring(origin, radius, Blocks.DIRT.defaultBlockState(), 20, false));
      RINGS.add(new Ring(origin, radius * .6F, Blocks.DIRT.defaultBlockState(), 8, false));
   }

   public static void handle(HeroFxS2CPacket packet) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null) {
         return;
      }

      BlockState material = packet.blockStateId == 0 ? Blocks.DIRT.defaultBlockState() : Block.stateById(packet.blockStateId);
      Vec3 listener = client.gameRenderer.getMainCamera().getPosition();
      SoundEvent whiz = packet.soundId < 0 ? null : BuiltInRegistries.SOUND_EVENT.byId(packet.soundId);
      switch (packet.kind) {
         case HeroFxS2CPacket.SHARDS -> spray(level, listener, packet, material, whiz);
         case HeroFxS2CPacket.SHOCKWAVE -> {
            float radius = packet.points.length > 0 ? packet.points[0] : 6.0F;
            RINGS.add(new Ring(packet.origin, radius, material, 12 + (int)(8 * packet.power), false));
            RINGS.add(new Ring(packet.origin, radius * 0.6F, material, 8, false));
            dust(level, packet.origin, material, radius, 40 + (int)(60 * packet.power));
            addShake(listener, packet.origin, 0.45F + 0.9F * packet.power, 18.0 + radius * 2.0);
         }
         case HeroFxS2CPacket.SLAM -> {
            float radius = packet.points.length > 0 ? packet.points[0] : 8.0F;
            RINGS.add(new Ring(packet.origin, radius, material, 16, true));
            RINGS.add(new Ring(packet.origin, radius * 1.6F, material, 22, false));
            FLASHES.add(new Flash(packet.origin.add(0.0, 1.0, 0.0), 2.4F, 8));
            dust(level, packet.origin, material, radius, 140);
            addShake(listener, packet.origin, 1.6F, 40.0);
         }
         case HeroFxS2CPacket.BLADE -> {
            if (packet.points.length >= 3) {
               Vec3 end = new Vec3(packet.points[0], packet.points[1], packet.points[2]);
               BLADES.add(new Blade(packet.origin, end));
               scheduleWhiz(listener, packet.origin, end, 9.0, 1.0F, 0.75F, whiz);
               addShake(listener, packet.origin, 0.35F, 16.0);
            }
         }
         case HeroFxS2CPacket.LAUNCH -> {
            RINGS.add(new Ring(packet.origin, 2.5F + 3.0F * packet.power, material, 8, false));
            addShake(listener, packet.origin, 0.25F + 0.35F * packet.power, 10.0);
         }
         case HeroFxS2CPacket.FLASH -> {
            float power=Math.max(.1F,Math.min(2,packet.power));
            FLASHES.add(new Flash(packet.origin, 1.4F*power, 6));
            addShake(listener, packet.origin, 0.6F*power, 20.0);
         }
         case HeroFxS2CPacket.HIT_STOP -> {
            for (int id : dev.baranhan.viltrumitecore.hero.fx.HeroFx.decodeIds(packet.points)) {
               HitStop.trigger(id, Math.max(1, Math.round(packet.power)));
            }
         }
         case HeroFxS2CPacket.SONIC_RING -> {
            AirTrailVfx.ring(packet.origin);
            addShake(listener, packet.origin, 0.9F, 32.0);
         }
         case HeroFxS2CPacket.AIR_TRAIL -> {
            for (int id : dev.baranhan.viltrumitecore.hero.fx.HeroFx.decodeIds(packet.points)) {
               AirTrailVfx.trail(id);
            }
         }
         case HeroFxS2CPacket.ELECTRIC_ARC -> {
            if (packet.points.length >= 3) {
               play(ThorFx.ARC, packet.origin, new Vec3(packet.points[0], packet.points[1], packet.points[2]), 1.0F, null, level.random.nextLong());
            }
         }
         case HeroFxS2CPacket.ELECTRIC_RING -> {
            if (packet.points.length >= 1) {
               double radius = Math.min(64.0, Math.max(0.5, packet.points[0]));
               play(ThorFx.RING, packet.origin, packet.origin.add(radius, 0.0, 0.0), 1.0F, null, level.random.nextLong());
            }
         }
         case HeroFxS2CPacket.ELECTRIC_BOLT -> {
            float power = dev.baranhan.viltrumitecore.hero.fx.HeroFx.clampBoltPower(packet.power);
            electricBolt(level, packet.origin, power);
         }
         case HeroFxS2CPacket.ELECTRIC_CRACKLE -> {
            float scale = Mth.clamp(packet.power / 20.0F, 0.5F, 3.0F);
            play(ThorFx.CRACKLE, packet.origin, packet.origin, scale, null, level.random.nextLong());
         }
         case HeroFxS2CPacket.PRESET -> playPacket(level, packet, material);
         default -> {
         }
      }
   }

   /**
    * Thor's lightning bolt packet (B-m6): the layered {@link ThorFx#BOLT}
    * recipe (power >= 1.5 plays {@link ThorFx#BOLT_HEAVY}) and the local
    * thunder clap whose loudness falls off with distance (WEATHER category,
    * no world sky flash; sound unchanged until the Thor sound stage).
    */
   private static void electricBolt(ClientLevel level, Vec3 at, float power) {
      RandomSource random = level.random;
      boolean heavy = power >= ThorFx.HEAVY_BOLT_POWER;
      play(heavy ? ThorFx.BOLT_HEAVY : ThorFx.BOLT, at, at, heavy ? power / 2.0F : power, null, random.nextLong());
      level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F * power, 0.8F + random.nextFloat() * 0.2F, false);
      level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F * power, 0.8F + random.nextFloat() * 0.2F, true);
   }

   private static void playPacket(ClientLevel level, HeroFxS2CPacket packet, BlockState material) {
      if (packet.points.length < 6) {
         return;
      }

      FxRecipe recipe = FxRecipe.byNetId(Math.round(packet.points[0]));
      if (recipe == null) {
         return;
      }

      long seed = Math.round(packet.points[1]);
      int hitStop = Math.round(packet.points[2]);
      Vec3 target = new Vec3(packet.points[3], packet.points[4], packet.points[5]);
      if (hitStop > 0 && packet.points.length > 6) {
         float[] ids = java.util.Arrays.copyOfRange(packet.points, 6, packet.points.length);
         for (int id : dev.baranhan.viltrumitecore.hero.fx.HeroFx.decodeIds(ids)) {
            HitStop.trigger(id, Math.min(dev.baranhan.viltrumitecore.hero.fx.HeroFx.PRESET_MAX_HIT_STOP, hitStop));
         }
      }

      play(recipe, packet.origin, target, dev.baranhan.viltrumitecore.hero.fx.HeroFx.clampPresetScale(packet.power), packet.blockStateId == 0 ? null : material,
         seed * 0x5DEECE66DL + 11L);
      for (PresetListener listener : PRESET_LISTENERS) {
         listener.onPreset(recipe.preset(), packet.origin, target);
      }
   }

   private static final java.util.List<PresetListener> PRESET_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

   /** State-driven FX that follow a server preset (the WRATH storm tail moves to the strike point). */
   public static void addPresetListener(PresetListener listener) {
      PRESET_LISTENERS.add(listener);
   }

   @FunctionalInterface
   public interface PresetListener {
      void onPreset(FxPreset preset, Vec3 at, Vec3 target);
   }

   /** Client entry point: plays a preset locally (state-driven FX, a packet). Unknown recipes are ignored. */
   public static void play(FxPreset preset, Vec3 at, Vec3 target, float scale, @Nullable BlockState material, long seed) {
      FxRecipe recipe = FxRecipe.of(preset);
      if (recipe != null) {
         play(recipe, at, target, scale, material, seed);
      }
   }

   public static void play(FxRecipe recipe, Vec3 at, Vec3 target, float scale, @Nullable BlockState material, long seed) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (level == null || !Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) {
         return;
      }

      Vec3 to = Double.isFinite(target.x) && Double.isFinite(target.y) && Double.isFinite(target.z) && target.distanceToSqr(at) < 96.0 * 96.0 ? target : at;
      BlockState ground = material != null ? material : groundMaterial(level, at);
      List<FxRecipe.Layer> layers = recipe.layers();
      for (int i = 0; i < layers.size(); i++) {
         FxRecipe.Layer layer = layers.get(i);
         PendingLayer pending = new PendingLayer(layer, at, to, scale, ground, seed * 131L + i, seed, layer.delay());
         if (pending.delay <= 0) {
            spawn(level, pending);
         } else if (PENDING.size() < MAX_PENDING) {
            PENDING.add(pending);
         }
      }
   }

   private static BlockState groundMaterial(ClientLevel level, Vec3 at) {
      BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
      for (int i = 0; i < 3; i++) {
         BlockState state = level.getBlockState(pos);
         if (!state.isAir()) {
            return state;
         }

         pos = pos.below();
      }

      return Blocks.DIRT.defaultBlockState();
   }

   /** One recipe layer comes alive. Sizes scale with the preset scale, times do not. */
   private static void spawn(ClientLevel level, PendingLayer p) {
      FxRecipe.Layer layer = p.layer;
      float s = p.scale;
      Vec3 at = p.origin;
      Vec3 listener = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      java.util.Random random = new java.util.Random(p.seed);
      switch (layer.kind()) {
         case BOLT -> {
            Vec3 ground = at;
            if (layer.spread() > 0.0F) {
               double angle = random.nextDouble() * Math.PI * 2.0;
               double r = layer.spread() * s * (0.6 + random.nextDouble() * 0.6);
               ground = ElectricVfx.onGround(level, at.add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r), at.y);
            }

            float height = Math.min(dev.baranhan.viltrumitecore.hero.fx.HeroFx.BOLT_MAX_HEIGHT, layer.size());
            Vec3 top = ground.add((random.nextDouble() - 0.5) * height * 0.15, height, (random.nextDouble() - 0.5) * height * 0.15);
            ElectricVfx.bolt(top, ground, FxRecipe.BOLT_CORE_HALF_WIDTH * layer.width() * s, layer.count(), layer.life(), p.seed);
         }
         case ARC -> ElectricVfx.arc(at, p.target, layer.width() * s, layer.life(), layer.count(), p.seed);
         case RING -> {
            double radius = Math.hypot(p.target.x - at.x, p.target.z - at.z) * layer.size();
            ElectricVfx.ring(level, at, radius < 0.5 ? 3.0 * s : radius, layer.width() * s, layer.count(), layer.life(), p.seed);
         }
         case CRACKLE -> ElectricVfx.crackle(at, layer.size() * s, Math.round(layer.count() * Math.max(1.0F, s)), layer.life(), p.seed);
         case GROUND_CRAWL -> ElectricVfx.crawl(level, ElectricVfx.onGround(level, at, at.y), layer.size() * s, Math.round(layer.count() * (float)Math.sqrt(s)),
            layer.life(), layer.width() * 0.5F * s, p.seed);
         case SPARKS -> ElectricVfx.sparks(at.add(0.0, 0.1, 0.0), Math.round(layer.count() * s), layer.power() * (float)Math.sqrt(s), layer.life(), p.seed);
         case LIGHT_POOL -> ElectricVfx.lightPool(ElectricVfx.onGround(level, at, at.y), layer.size() * s, layer.power(), layer.life(), p.recipeSeed);
         case SKY_FLASH -> ElectricVfx.skyFlash(at.add(0.0, layer.size(), 0.0), layer.width() * s, layer.life(), p.recipeSeed);
         case SCREEN_FLASH -> FlashOverlay.flash(layer.power() * CameraShake.falloff(listener.distanceTo(at), layer.size() * s), layer.life());
         case SCORCH -> ElectricVfx.scorch(level, at, layer.size() * s, layer.count(), layer.life(), p.seed);
         case DUST -> dust(level, at, p.material, layer.size() * s, Math.round(layer.count() * s));
         case DEBRIS -> debris(level, at, p.material, Math.round(layer.count() * s), layer.size() * s, layer.power(), random);
         case SHOCKWAVE -> RINGS.add(new Ring(ElectricVfx.onGround(level, at, at.y), layer.size() * s, p.material, layer.life(), layer.power() > 1.0F));
         case FLASH -> FLASHES.add(new Flash(at, layer.size() * s, layer.life()));
         case SHAKE -> addShake(listener, at, layer.power(), layer.size() * s);
      }
   }

   /** Ground chunks thrown out of an impact (client-made shard ends). */
   private static void debris(ClientLevel level, Vec3 at, BlockState material, int count, float distance, float lift, java.util.Random random) {
      int color = material.getMapColor(level, BlockPos.containing(at)).col;
      for (int i = 0; i < Math.min(48, count); i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double d = distance * (0.4 + random.nextDouble() * 0.6);
         Vec3 end = at.add(Math.cos(angle) * d, lift * (0.5 + random.nextDouble() * 2.0), Math.sin(angle) * d);
         Vec3 start = at.add((random.nextDouble() - 0.5) * 0.4, 0.1, (random.nextDouble() - 0.5) * 0.4);
         SHARDS.add(new Shard(start, end, material, color, random.nextInt(3), 0.07F + random.nextFloat() * 0.06F));
      }
   }

   private static void spray(ClientLevel level, Vec3 listener, HeroFxS2CPacket packet, BlockState material, SoundEvent whiz) {
      int color = material.getMapColor(level, BlockPos.containing(packet.origin)).col;
      RandomSource random = level.random;
      List<double[]> passes = new ArrayList<>();
      for (int i = 0; i + 2 < packet.points.length; i += 3) {
         Vec3 end = new Vec3(packet.points[i], packet.points[i + 1], packet.points[i + 2]);
         Vec3 start = packet.origin.add((random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.25, (random.nextDouble() - 0.5) * 0.4);
         // A small per-shard delay makes the spray read as a burst, not one plane.
         SHARDS.add(new Shard(start, end, material, color, random.nextInt(2), 0.07F + random.nextFloat() * 0.05F));
         double[] pass = closestPass(listener, start, end);
         if (pass != null) {
            passes.add(new double[]{pass[0], pass[1], pass[2], pass[3], pass[4]});
         }
      }

      // Whiz the listener with the nearest few shards that actually pass by.
      passes.sort((a, b) -> Double.compare(a[3], b[3]));
      for (int i = 0; whiz != null && i < Math.min(MAX_WHIZZES_PER_SPRAY, passes.size()); i++) {
         double[] p = passes.get(i);
         float closeness = (float)(1.0 - p[3] / WHIZ_RADIUS);
         int delay = (int)Math.round(p[4] / SHARD_VISUAL_SPEED) + i;
         WHIZZES.add(new Whiz(new Vec3(p[0], p[1], p[2]), delay, 0.5F + 0.7F * closeness, 0.85F + random.nextFloat() * 0.35F, closeness, whiz));
      }

      addShake(listener, packet.origin, 0.3F * packet.power + 0.15F, 14.0);
   }

   private static void scheduleWhiz(Vec3 listener, Vec3 from, Vec3 to, double speed, float volume, float pitch, SoundEvent sound) {
      double[] p = sound == null ? null : closestPass(listener, from, to);
      if (p != null) {
         float closeness = (float)(1.0 - p[3] / WHIZ_RADIUS);
         WHIZZES.add(new Whiz(new Vec3(p[0], p[1], p[2]), (int)Math.round(p[4] / speed), volume, pitch, closeness, sound));
      }
   }

   /**
    * Closest approach of a segment to the listener: {x, y, z, distance,
    * distanceAlongSegment}, or null when it never comes within WHIZ_RADIUS or
    * the pass is at the shooter's own feet (the caster hears the kick, not a
    * dozen whizzes).
    */
   static double[] closestPass(Vec3 listener, Vec3 from, Vec3 to) {
      Vec3 seg = to.subtract(from);
      double len2 = seg.lengthSqr();
      if (len2 < 1.0E-6) {
         return null;
      }

      double t = Mth.clamp(listener.subtract(from).dot(seg) / len2, 0.0, 1.0);
      Vec3 closest = from.add(seg.scale(t));
      double distance = closest.distanceTo(listener);
      double along = Math.sqrt(len2) * t;
      if (distance > WHIZ_RADIUS || along < 2.5 || t >= 0.999) {
         return null;
      }

      return new double[]{closest.x, closest.y, closest.z, distance, along};
   }

   private static void dust(ClientLevel level, Vec3 center, BlockState material, float radius, int count) {
      RandomSource random = level.random;
      BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, material);
      for (int i = 0; i < count; i++) {
         double az = random.nextDouble() * Math.PI * 2.0;
         double r = random.nextDouble() * radius * 0.5;
         double x = center.x + Math.cos(az) * r;
         double z = center.z + Math.sin(az) * r;
         double speed = 0.15 + random.nextDouble() * 0.35;
         level.addParticle(debris, x, center.y + 0.15, z, Math.cos(az) * speed, 0.25 + random.nextDouble() * 0.5, Math.sin(az) * speed);
         if (i % 4 == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, center.y + 0.2, z, Math.cos(az) * speed * 0.3, 0.015, Math.sin(az) * speed * 0.3);
         }
      }
   }

   private static void addShake(Vec3 listener, Vec3 at, float power, double range) {
      CameraShake.add(CameraShake.falloff(listener.distanceTo(at), range) * power);
   }

   /** Deterministic 0..1 noise for ring wobble. */
   private static float hashOffset(int seed, int axis) {
      int h = seed * 0x9E3779B9 + axis * 0x85EBCA6B;
      h ^= h >>> 13;
      h *= 0xC2B2AE35;
      h ^= h >>> 16;
      return (h & 0xFFFF) / 65536.0F;
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
         SHARDS.clear();
         RINGS.clear();
         BLADES.clear();
         FLASHES.clear();
         WHIZZES.clear();
         PENDING.clear();
         ElectricVfx.clear();
      }
      if (level == null || client.isPaused()) {
         return;
      }

      for (Iterator<Shard> it = SHARDS.iterator(); it.hasNext();) {
         Shard shard = it.next();
         shard.age++;
         Vec3 head = shard.head(0.0F);
         if (shard.age > shard.delay && !shard.done() && level.random.nextFloat() < 0.6F) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, shard.material), head.x, head.y, head.z, shard.dir.x * 0.4, shard.dir.y * 0.4 + 0.05, shard.dir.z * 0.4);
         }
         if (shard.done() && !shard.burst) {
            shard.burst = true;
            for (int i = 0; i < 4; i++) {
               level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, shard.material), shard.to.x, shard.to.y, shard.to.z,
                  (level.random.nextDouble() - 0.5) * 0.3, level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.3);
            }
            level.addParticle(ParticleTypes.POOF, shard.to.x, shard.to.y, shard.to.z, 0.0, 0.02, 0.0);
         }
         if (shard.age > shard.delay + shard.flightTicks() + 4) {
            it.remove();
         }
      }

      for (Iterator<Whiz> it = WHIZZES.iterator(); it.hasNext();) {
         Whiz whiz = it.next();
         if (whiz.delay-- > 0) {
            continue;
         }

         level.playLocalSound(whiz.pos.x, whiz.pos.y, whiz.pos.z, whiz.sound, SoundSource.PLAYERS, whiz.volume, whiz.pitch, false);
         Vec3 listener = client.gameRenderer.getMainCamera().getPosition();
         addShake(listener, whiz.pos, 0.25F * whiz.closeness, WHIZ_RADIUS + 1.0);
         it.remove();
      }

      if (!PENDING.isEmpty()) {
         List<PendingLayer> due = new ArrayList<>();
         for (Iterator<PendingLayer> it = PENDING.iterator(); it.hasNext();) {
            PendingLayer pending = it.next();
            if (--pending.delay <= 0) {
               it.remove();
               due.add(pending);
            }
         }

         for (PendingLayer pending : due) {
            spawn(level, pending);
         }
      }

      ElectricVfx.tick(level);
      RINGS.removeIf(ring -> ++ring.age > ring.life);
      BLADES.removeIf(blade -> ++blade.age > 8);
      FLASHES.removeIf(flash -> ++flash.age > flash.life);
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL || (SHARDS.isEmpty() && RINGS.isEmpty() && BLADES.isEmpty() && FLASHES.isEmpty() && !ElectricVfx.active())) {
         return;
      }

      float partialTick = event.getPartialTick();
      Camera camera = event.getCamera();
      Vec3 cameraPos = camera.getPosition();
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
      ClientLevel renderLevel = Minecraft.getInstance().level;
      long gameTime = renderLevel == null ? 0L : renderLevel.getGameTime();
      try {
         // Pass 1: solid-looking debris (normal alpha) — rock chunks, ground dust rings.
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Shard shard : SHARDS) {
            drawShardBody(buffer, cameraPos, camera, shard, partialTick);
         }
         for (Ring ring : RINGS) {
            drawRing(buffer, cameraPos, camera, ring, partialTick, false);
         }
         ElectricVfx.renderSolid(buffer, cameraPos, camera, gameTime, partialTick);
         tessellator.end();

         // Pass 2: additive heat — hot streaks, ring edges, the blade, flashes.
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         for (Shard shard : SHARDS) {
            drawShardGlow(buffer, cameraPos, camera, shard, partialTick);
         }
         for (Ring ring : RINGS) {
            drawRing(buffer, cameraPos, camera, ring, partialTick, true);
         }
         for (Blade blade : BLADES) {
            drawBlade(buffer, cameraPos, camera, blade, partialTick);
         }
         ElectricVfx.renderGlow(buffer, cameraPos, camera, gameTime, partialTick);
         for (Flash flash : FLASHES) {
            float progress = (flash.age + partialTick) / flash.life;
            int alpha = (int)(255.0F * (1.0F - progress));
            if (alpha > 0) {
               PixelVfx.crossGlow(buffer, cameraPos, camera, flash.pos, flash.size * (0.4F + progress), 255, 250, 235, alpha);
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

   private static void drawShardBody(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Shard shard, float partialTick) {
      if (!shard.flying(partialTick)) {
         return;
      }

      Vec3 head = shard.head(partialTick);
      int r = (shard.color >> 16) & 0xFF;
      int g = (shard.color >> 8) & 0xFF;
      int b = shard.color & 0xFF;
      // A chunk of the kicked ground: a dark rim and the block color on top.
      PixelVfx.billboardPixel(buffer, cameraPos, camera, head, shard.size * 1.35F, r / 3, g / 3, b / 3, 235);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, head, shard.size, r, g, b, 255);
      for (int i = 1; i <= 6; i++) {
         Vec3 p = head.subtract(shard.dir.scale(i * 0.32));
         if (p.subtract(shard.from).dot(shard.dir) < 0.0) {
            break;
         }
         int alpha = 200 - i * 30;
         PixelVfx.billboardPixel(buffer, cameraPos, camera, p, shard.size * (0.8F - i * 0.1F), r, g, b, alpha);
      }
   }

   private static void drawShardGlow(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Shard shard, float partialTick) {
      if (!shard.flying(partialTick)) {
         return;
      }

      Vec3 head = shard.head(partialTick);
      PixelVfx.billboardPixel(buffer, cameraPos, camera, head, shard.size * 0.55F, 255, 245, 220, 200);
      // Hot air streak: long, thin, fading — what makes a shard read as "fast".
      for (int i = 1; i <= 12; i++) {
         Vec3 p = head.subtract(shard.dir.scale(i * 0.28));
         if (p.subtract(shard.from).dot(shard.dir) < 0.0) {
            break;
         }
         int alpha = 170 - i * 13;
         PixelVfx.billboardPixel(buffer, cameraPos, camera, p, 0.035F, 255, 225, 180, alpha);
      }
   }

   private static void drawRing(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Ring ring, float partialTick, boolean glow) {
      float progress = Mth.clamp((ring.age + partialTick) / ring.life, 0.0F, 1.0F);
      float ease = 1.0F - (1.0F - progress) * (1.0F - progress);
      float radius = 0.6F + (ring.radius - 0.6F) * ease;
      int count = Math.max(24, (int)(radius * Math.PI * 2.0 / 0.32));
      float fade = 1.0F - progress;
      for (int i = 0; i < count; i++) {
         double az = Math.PI * 2.0 * i / count;
         float wobble = (float)hashOffset(i, ring.seed) * 0.35F;
         double rr = radius + wobble;
         Vec3 p = ring.center.add(Math.cos(az) * rr, 0.12 + wobble * 0.4, Math.sin(az) * rr);
         if (glow) {
            PixelVfx.billboardPixel(buffer, cameraPos, camera, p.add(0.0, 0.1, 0.0), 0.05F, 255, 240, 215, (int)(150 * fade * fade));
         } else {
            int r = (ring.color >> 16) & 0xFF;
            int g = (ring.color >> 8) & 0xFF;
            int b = ring.color & 0xFF;
            // Dust wall: two stacked pixels, pale like a thrown-up cloud.
            int alpha = (int)(190 * fade);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, p, 0.16F, (r + 200) / 2, (g + 195) / 2, (b + 185) / 2, alpha);
            PixelVfx.billboardPixel(buffer, cameraPos, camera, p.add(0.0, 0.3 + wobble, 0.0), 0.12F, (r + 220) / 2, (g + 215) / 2, (b + 205) / 2, alpha / 2);
            if (ring.heavy && i % 2 == 0) {
               PixelVfx.billboardPixel(buffer, cameraPos, camera, p.add(0.0, 0.7 + wobble * 2.0, 0.0), 0.1F, r, g, b, alpha / 2);
            }
         }
      }
   }

   private static void drawBlade(BufferBuilder buffer, Vec3 cameraPos, Camera camera, Blade blade, float partialTick) {
      float progress = (blade.age + partialTick) / 8.0F;
      float fade = 1.0F - progress;
      if (fade <= 0.0F) {
         return;
      }

      Vec3 seg = blade.to.subtract(blade.from);
      double length = seg.length();
      Vec3 dir = seg.scale(1.0 / Math.max(1.0E-4, length));
      Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
      if (side.lengthSqr() < 1.0E-4) {
         side = new Vec3(1.0, 0.0, 0.0);
      }
      side = side.normalize();
      double front = Math.min(length, length * Math.min(1.0, progress * 3.0));
      for (double d = 0.0; d < front; d += 0.22) {
         float taper = (float)(1.0 - Math.abs(d / length - 0.5) * 1.6);
         double width = 0.9 * Math.max(0.15, taper);
         Vec3 c = blade.from.add(dir.scale(d));
         // A thin crescent of frozen air: bright core, cold fringe either side.
         PixelVfx.billboardPixel(buffer, cameraPos, camera, c, 0.05F, 255, 255, 255, (int)(230 * fade));
         PixelVfx.billboardPixel(buffer, cameraPos, camera, c.add(side.scale(width)), 0.035F, 190, 225, 255, (int)(150 * fade));
         PixelVfx.billboardPixel(buffer, cameraPos, camera, c.subtract(side.scale(width)), 0.035F, 190, 225, 255, (int)(150 * fade));
      }
   }

   private static final class Shard {
      final Vec3 from;
      final Vec3 to;
      final Vec3 dir;
      final double length;
      final BlockState material;
      final int color;
      final int delay;
      final float size;
      int age;
      boolean burst;

      Shard(Vec3 from, Vec3 to, BlockState material, int color, int delay, float size) {
         this.from = from;
         this.to = to;
         Vec3 seg = to.subtract(from);
         this.length = seg.length();
         this.dir = this.length < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : seg.scale(1.0 / this.length);
         this.material = material;
         this.color = color == 0 ? 0x806040 : color;
         this.delay = delay;
         this.size = size;
      }

      int flightTicks() {
         return (int)Math.ceil(this.length / SHARD_VISUAL_SPEED);
      }

      double travelled(float partialTick) {
         return Math.max(0.0, (this.age + partialTick - this.delay) * SHARD_VISUAL_SPEED);
      }

      boolean flying(float partialTick) {
         double t = this.travelled(partialTick);
         return t > 0.0 && t < this.length;
      }

      boolean done() {
         return this.travelled(0.0F) >= this.length;
      }

      Vec3 head(float partialTick) {
         return this.from.add(this.dir.scale(Math.min(this.length, this.travelled(partialTick))));
      }
   }

   private static final class Ring {
      final Vec3 center;
      final float radius;
      final int color;
      final int life;
      final boolean heavy;
      final int seed;
      int age;

      Ring(Vec3 center, float radius, BlockState material, int life, boolean heavy) {
         this.center = center;
         this.radius = radius;
         ClientLevel level = Minecraft.getInstance().level;
         int c = level == null ? 0 : material.getMapColor(level, BlockPos.containing(center)).col;
         this.color = c == 0 ? 0x8a7a66 : c;
         this.life = life;
         this.heavy = heavy;
         this.seed = (int)(center.x * 31 + center.z * 17) & 0xFF;
      }
   }

   private static final class Blade {
      final Vec3 from;
      final Vec3 to;
      int age;

      Blade(Vec3 from, Vec3 to) {
         this.from = from;
         this.to = to;
      }
   }

   private static final class Flash {
      final Vec3 pos;
      final float size;
      final int life;
      int age;

      Flash(Vec3 pos, float size, int life) {
         this.pos = pos;
         this.size = size;
         this.life = life;
      }
   }

   private static final class PendingLayer {
      final FxRecipe.Layer layer;
      final Vec3 origin;
      final Vec3 target;
      final float scale;
      final BlockState material;
      final long seed;
      final long recipeSeed;
      int delay;

      PendingLayer(FxRecipe.Layer layer, Vec3 origin, Vec3 target, float scale, BlockState material, long seed, long recipeSeed, int delay) {
         this.layer = layer;
         this.origin = origin;
         this.target = target;
         this.scale = scale;
         this.material = material;
         this.seed = seed;
         this.recipeSeed = recipeSeed;
         this.delay = delay;
      }
   }

   private static final class Whiz {
      final Vec3 pos;
      final float volume;
      final float pitch;
      final float closeness;
      final SoundEvent sound;
      int delay;

      Whiz(Vec3 pos, int delay, float volume, float pitch, float closeness, SoundEvent sound) {
         this.pos = pos;
         this.delay = delay;
         this.volume = volume;
         this.pitch = pitch;
         this.closeness = closeness;
         this.sound = sound;
      }
   }
}
