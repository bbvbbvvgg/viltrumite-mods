package dev.baranhan.viltrumitecore.client.redhulk;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFlags;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkFx;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkMove;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkRules;
import dev.baranhan.viltrumitecore.hero.redhulk.RedHulkSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Vector3f;

/**
 * The heat-coloured half of the Red Hulk's VFX. Preset listener (RedHulkFx 700-712): fire, embers, lava and steam on
 * top of the neutral FxRecipe layers. State driven, every client tick for every Red Hulk in view: embers off the skin
 * that thicken with the heat tier, the heat aura ring (tier 2+), burning fists in Thermal Fury, steam in water or rain,
 * the overheat vent plume, the embers drawn into his chest while Critical Overheat charges; his heavy footsteps and the
 * heat hum loop. Render: the Heat Blast beam (from the server's pulses) and the ultimate's growing core. Client only.
 */
public final class RedHulkVfx {
   static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.08F), 1.0F);
   static final DustParticleOptions EMBER_HOT = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.3F), 1.2F);
   static final DustParticleOptions CINDER = new DustParticleOptions(new Vector3f(0.55F, 0.08F, 0.04F), 1.4F);
   static final DustParticleOptions WHITE_HOT = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.75F), 1.6F);
   private static final RandomSource RANDOM = RandomSource.create();
   private static final double GAIT_HALF = RedHulkPose.GAIT_CYCLE / 2.0;

   static final class Beam {
      final Vec3 from;
      final Vec3 to;
      int life;
      final int max;
      final float heat;

      Beam(Vec3 from, Vec3 to, int life, float heat) {
         this.from = from;
         this.to = to;
         this.life = life;
         this.max = life;
         this.heat = heat;
      }
   }

   private static final List<Beam> BEAMS = new ArrayList<>();
   private static final Map<UUID, Integer> STEPS = new HashMap<>();
   private static final Map<UUID, HeatHum> HUMS = new HashMap<>();
   private static boolean listening;

   private RedHulkVfx() {
   }

   static void init() {
      if (!listening) {
         listening = true;
         dev.baranhan.viltrumitecore.client.render.vfx.HeroImpactFx.addPresetListener(RedHulkVfx::onPreset);
      }
   }

   // ------------------------------------------------------------------ presets

   /** Pure: the radius a preset carries in its target point (RedHulkFx.radius); 0 when the target is not a radius. */
   static double radiusOf(Vec3 at, Vec3 target) {
      double dx = target.x - at.x;
      return Math.abs(target.y - at.y) < 1.0E-4 && Math.abs(target.z - at.z) < 1.0E-4 && dx > 0 ? dx : 0.0;
   }

   /** Heat 0-1 of the Red Hulk nearest to a preset (colour and density); cold when none is in range. */
   static float heatNear(ClientLevel level, Vec3 at) {
      float best = 0;
      double bestD = 24 * 24;
      for (AbstractClientPlayer p : level.players()) {
         HeroPublicSnapshot s = RedHulkView.of(p);
         double d = p.distanceToSqr(at);
         if (s != null && d < bestD) {
            bestD = d;
            best = RedHulkView.heatFraction(s);
         }
      }
      return best;
   }

   static void onPreset(FxPreset preset, Vec3 at, Vec3 target) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || preset.netId() < 700 || preset.netId() > 712) {
         return;
      }
      float h = heatNear(level, at);
      int n = 1 + Math.round(h * 2.0F);
      double r = radiusOf(at, target);
      if (preset == RedHulkFx.PUNCH) {
         burst(level, ParticleTypes.FLAME, at, 4 + 4 * n, 0.12);
         burst(level, h > 0.5F ? EMBER_HOT : EMBER, at, 6 * n, 0.25);
         if (h > 0.5F) {
            burst(level, ParticleTypes.LAVA, at, n, 0.1);
         }
      } else if (preset == RedHulkFx.HAMMER) {
         ring(level, ParticleTypes.FLAME, at.add(0, 0.1, 0), 1.4, 18 + 6 * n, 0.12);
         burst(level, ParticleTypes.LAVA, at, 2 + 2 * n, 0.2);
         burst(level, ParticleTypes.LARGE_SMOKE, at, 6, 0.06);
      } else if (preset == RedHulkFx.CLAP) {
         cone(level, at, target, 40 + 20 * n, 0.6);
         burst(level, WHITE_HOT, at, 12, 0.3);
      } else if (preset == RedHulkFx.SEISMIC || preset == RedHulkFx.LEAP_LAND) {
         double rad = r > 0 ? r : 5.0;
         for (int k = 1; k <= 3; k++) {
            ring(level, ParticleTypes.FLAME, at.add(0, 0.15, 0), rad * k / 3.0, (int)(10 * rad * k / 3.0) + 6 * n, 0.08);
         }
         scatter(level, ParticleTypes.LAVA, at, rad * 0.8, 4 + 4 * n);
         scatter(level, ParticleTypes.LARGE_SMOKE, at, rad * 0.6, 14);
         scatter(level, CINDER, at, rad, 30);
      } else if (preset == RedHulkFx.LEAP_LAUNCH) {
         ring(level, ParticleTypes.FLAME, at.add(0, 0.1, 0), 0.9, 16, 0.18);
         burst(level, ParticleTypes.LARGE_SMOKE, at, 8, 0.08);
      } else if (preset == RedHulkFx.THROW_IMPACT) {
         burst(level, ParticleTypes.LARGE_SMOKE, at, 8, 0.08);
         burst(level, EMBER, at, 10, 0.3);
      } else if (preset == RedHulkFx.BLAST) {
         BEAMS.add(new Beam(at, target, 3, h));
         trail(level, h > 0.6F ? EMBER_HOT : EMBER, at, target, (int)(at.distanceTo(target) * 1.5));
         burst(level, ParticleTypes.FLAME, target, 6 + 2 * n, 0.12);
         burst(level, ParticleTypes.SMOKE, target, 4, 0.05);
         if (RANDOM.nextInt(3) == 0) {
            burst(level, ParticleTypes.LAVA, target, 1, 0.1);
         }
      } else if (preset == RedHulkFx.FURY) {
         ring(level, ParticleTypes.FLAME, at, 0.8, 40, 0.35);
         ring(level, EMBER_HOT, at.add(0, -0.6, 0), 1.2, 30, 0.3);
         burst(level, ParticleTypes.LAVA, at, 4, 0.2);
      } else if (preset == RedHulkFx.VENT) {
         for (int i = 0; i < 40; i++) {
            level.addParticle(ParticleTypes.CLOUD, at.x + RANDOM.nextGaussian() * 0.4, at.y + RANDOM.nextDouble() * 0.6, at.z + RANDOM.nextGaussian() * 0.4,
               RANDOM.nextGaussian() * 0.08, 0.25 + RANDOM.nextDouble() * 0.25, RANDOM.nextGaussian() * 0.08);
         }
         burst(level, ParticleTypes.WHITE_ASH, at, 30, 0.2);
      } else if (preset == RedHulkFx.ULT_CHARGE) {
         ring(level, ParticleTypes.FLAME, at.add(0, -1.6, 0), 3.0, 40, -0.1);
      } else if (preset == RedHulkFx.ULT_RELEASE) {
         double rad = r > 0 ? r : RedHulkRules.ULT_RADIUS;
         ring(level, ParticleTypes.FLAME, at, 1.0, 90, 0.9);
         ring(level, WHITE_HOT, at, 0.8, 60, 0.7);
         for (int k = 1; k <= 4; k++) {
            ring(level, ParticleTypes.FLAME, at.add(0, -1.5, 0), rad * k / 4.0, (int)(8 * rad * k / 4.0), 0.05);
         }
         scatter(level, ParticleTypes.LAVA, at.add(0, -1.5, 0), rad * 0.7, 30);
         scatter(level, ParticleTypes.LARGE_SMOKE, at.add(0, -1.5, 0), rad * 0.6, 40);
         for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, at.x + RANDOM.nextGaussian() * 2, at.y - 1, at.z + RANDOM.nextGaussian() * 2, 0, 0, 0);
         }
      } else if (preset == RedHulkFx.LANDING) {
         ring(level, ParticleTypes.POOF, at.add(0, 0.1, 0), 1.0, 14, 0.1);
      }
   }

   // ------------------------------------------------------------------ state driven

   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.isPaused()) {
         return;
      }
      for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
         Beam b = it.next();
         if (--b.life <= 0) {
            it.remove();
         }
      }
      for (AbstractClientPlayer player : level.players()) {
         HeroPublicSnapshot s = RedHulkView.of(player);
         if (s == null || !player.isAlive()) {
            continue;
         }
         boolean self = player == mc.player && mc.options.getCameraType().isFirstPerson();
         tick(level, player, s, self);
         footsteps(level, player);
         hum(mc, player, s);
      }
      HUMS.values().removeIf(h -> h.isStopped());
   }

   static void tick(ClientLevel level, AbstractClientPlayer p, HeroPublicSnapshot s, boolean self) {
      int tier = RedHulkView.tier(s);
      float h = RedHulkView.heatFraction(s);
      double height = p.getBbHeight();
      Vec3 base = p.position();
      Vec3 chest = base.add(0, height * 0.68, 0);
      float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
      Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
      // embers off the skin: a few when warm, a stream when blazing (not in the own first-person view: no clutter)
      if (!self && tier >= 1 && RANDOM.nextInt(Math.max(1, 6 - tier * 2)) == 0) {
         Vec3 q = base.add((RANDOM.nextDouble() - 0.5) * p.getBbWidth(), height * (0.3 + RANDOM.nextDouble() * 0.6), (RANDOM.nextDouble() - 0.5) * p.getBbWidth());
         level.addParticle(tier >= 3 ? EMBER_HOT : EMBER, q.x, q.y, q.z, 0, 0.04, 0);
         if (tier >= 3 && RANDOM.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, q.x, q.y, q.z, 0, 0.02, 0);
         }
      }
      // shoulders smoke when blazing
      if (!self && tier >= 3 && RANDOM.nextInt(4) == 0) {
         Vec3 sh = chest.add(side.scale(RANDOM.nextBoolean() ? 0.55 : -0.55)).add(0, 0.35, 0);
         level.addParticle(ParticleTypes.SMOKE, sh.x, sh.y, sh.z, 0, 0.05, 0);
      }
      // the heat aura: a ring of embers and rising ash at its radius
      if (RedHulkView.flag(s, RedHulkFlags.AURA) && p.tickCount % 3 == 0) {
         double a = RANDOM.nextDouble() * Math.PI * 2;
         double rr = RedHulkRules.AURA_RADIUS * (0.85 + RANDOM.nextDouble() * 0.15);
         Vec3 q = base.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr);
         level.addParticle(tier >= 3 ? EMBER_HOT : EMBER, q.x, q.y, q.z, 0, 0.06, 0);
         level.addParticle(ParticleTypes.WHITE_ASH, q.x, q.y + 0.4, q.z, 0, 0.02, 0);
      }
      // Thermal Fury: burning fists
      if (RedHulkView.furious(s)) {
         for (int k = -1; k <= 1; k += 2) {
            Vec3 fist = base.add(side.scale(0.78 * k)).add(fwd.scale(0.2)).add(0, height * 0.36, 0);
            if (self) {
               continue;
            }
            level.addParticle(ParticleTypes.FLAME, fist.x + RANDOM.nextGaussian() * 0.08, fist.y, fist.z + RANDOM.nextGaussian() * 0.08, 0, 0.03, 0);
         }
      }
      // water or rain on a hot body: steam
      if (h > 0.1F && p.isInWaterRainOrBubble() && RANDOM.nextFloat() < 0.25F + h) {
         Vec3 q = base.add((RANDOM.nextDouble() - 0.5) * 1.2, height * RANDOM.nextDouble(), (RANDOM.nextDouble() - 0.5) * 1.2);
         level.addParticle(ParticleTypes.CLOUD, q.x, q.y, q.z, 0, 0.08 + 0.1 * h, 0);
      }
      // overheated: the plume from the back, dripping slag
      if (RedHulkView.overheated(s)) {
         Vec3 back = chest.add(fwd.scale(-0.35)).add(0, 0.3, 0);
         level.addParticle(ParticleTypes.CLOUD, back.x + RANDOM.nextGaussian() * 0.2, back.y, back.z + RANDOM.nextGaussian() * 0.2,
            RANDOM.nextGaussian() * 0.02, 0.18, RANDOM.nextGaussian() * 0.02);
         if (RANDOM.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, back.x, back.y, back.z, 0, 0.08, 0);
         }
         if (RANDOM.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.DRIPPING_LAVA, chest.x + RANDOM.nextGaussian() * 0.3, chest.y - 0.3, chest.z + RANDOM.nextGaussian() * 0.3, 0, 0, 0);
         }
      }
      RedHulkMove move = RedHulkView.move(s);
      // Critical Overheat charge: embers drawn into the chest from the radius, faster as it fills
      if (move == RedHulkMove.ULT_CHARGE) {
         float k = Math.min(1.0F, s.actionElapsed() / (float)RedHulkMove.ULT_CHARGE.length);
         int count = 2 + Math.round(k * 8);
         for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double rr = 2.5 + RANDOM.nextDouble() * 3.5;
            Vec3 q = chest.add(Math.cos(a) * rr, (RANDOM.nextDouble() - 0.4) * 2.0, Math.sin(a) * rr);
            Vec3 v = chest.subtract(q).scale(0.09 + 0.05 * k);
            level.addParticle(k > 0.6F ? EMBER_HOT : EMBER, q.x, q.y, q.z, v.x, v.y, v.z);
         }
         if (RANDOM.nextInt(3) == 0) {
            scatter(level, ParticleTypes.LAVA, base, 3.0, 1);
         }
      } else if (move == RedHulkMove.BLAST && s.actionElapsed() < RedHulkRules.BLAST_FIRE_START) {
         // the chest gathers heat before the beam
         Vec3 c = chest.add(fwd.scale(0.4));
         level.addParticle(EMBER_HOT, c.x + RANDOM.nextGaussian() * 0.3, c.y + RANDOM.nextGaussian() * 0.3, c.z + RANDOM.nextGaussian() * 0.3, 0, 0, 0);
      }
   }

   /** Heavy footfalls: one per half gait cycle on the ground, louder at a run. */
   static void footsteps(ClientLevel level, AbstractClientPlayer p) {
      if (!p.onGround() || p.isInWater() || p.isPassenger() || p.isCrouching()) {
         STEPS.remove(p.getUUID());
         return;
      }
      float speed = p.walkAnimation.speed();
      int step = (int)Math.floor(p.walkAnimation.position() / GAIT_HALF);
      Integer last = STEPS.put(p.getUUID(), step);
      if (last != null && last != step && speed > 0.15F) {
         float vol = 0.45F + Math.min(0.5F, speed * 0.45F);
         level.playLocalSound(p.getX(), p.getY(), p.getZ(), RedHulkSounds.FOOTSTEP.get(), SoundSource.PLAYERS, vol, 0.9F + RANDOM.nextFloat() * 0.15F,
            false);
         if (p.isSprinting()) {
            burst(level, ParticleTypes.POOF, p.position().add(0, 0.05, 0), 2, 0.03);
         }
      }
   }

   static void hum(Minecraft mc, AbstractClientPlayer p, HeroPublicSnapshot s) {
      if (RedHulkView.heat(s) < RedHulkRules.HOT && !RedHulkView.furious(s)) {
         return;
      }
      HeatHum hum = HUMS.get(p.getUUID());
      if (hum == null || hum.isStopped()) {
         hum = new HeatHum(p);
         HUMS.put(p.getUUID(), hum);
         mc.getSoundManager().play(hum);
      }
   }

   /** Pure: the hum volume (0 below hot, rising to 0.8 at full heat; fury keeps at least 0.4). */
   static float humVolume(int heat, boolean fury) {
      float v = Mth.clamp((heat - RedHulkRules.HOT + 100) / (float)(RedHulkRules.HEAT_MAX - RedHulkRules.HOT + 100), 0.0F, 1.0F) * 0.8F;
      return fury ? Math.max(0.4F, v) : v;
   }

   /** The loop of the heat hum that follows one Red Hulk and fades with his heat. */
   static final class HeatHum extends AbstractTickableSoundInstance {
      private final AbstractClientPlayer player;
      private float level;

      HeatHum(AbstractClientPlayer player) {
         super(RedHulkSounds.HEAT_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.player = player;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.x = player.getX();
         this.y = player.getY() + 1.6;
         this.z = player.getZ();
      }

      @Override
      public void tick() {
         HeroPublicSnapshot s = RedHulkView.of(this.player);
         float want = s == null || this.player.isRemoved() || !this.player.isAlive() ? 0.0F : humVolume(RedHulkView.heat(s), RedHulkView.furious(s));
         this.level += (want - this.level) * 0.1F;
         if (want <= 0.0F && this.level < 0.02F) {
            this.stop();
            return;
         }
         this.volume = Math.max(0.01F, this.level);
         this.pitch = s == null ? 1.0F : 0.85F + 0.3F * RedHulkView.heatFraction(s);
         this.x = this.player.getX();
         this.y = this.player.getY() + this.player.getBbHeight() * 0.6;
         this.z = this.player.getZ();
      }
   }

   // ------------------------------------------------------------------ render

   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      List<Vec3> cores = new ArrayList<>();
      List<Float> coreSize = new ArrayList<>();
      float partial = event.getPartialTick();
      for (AbstractClientPlayer p : level.players()) {
         HeroPublicSnapshot s = RedHulkView.of(p);
         if (s != null && RedHulkView.move(s) == RedHulkMove.ULT_CHARGE) {
            cores.add(p.getPosition(partial).add(0, p.getBbHeight() * 0.68, 0));
            coreSize.add(0.5F + 1.6F * Math.min(1.0F, (s.actionElapsed() + partial) / RedHulkMove.ULT_CHARGE.length));
         }
      }
      if (BEAMS.isEmpty() && cores.isEmpty()) {
         return;
      }
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
         for (Beam beam : BEAMS) {
            float f = Mth.clamp((beam.life - partial) / beam.max, 0.0F, 1.0F);
            int alpha = (int)(230 * f);
            float wobble = 1.0F + 0.12F * Mth.sin((level.getGameTime() + partial) * 1.7F);
            float outer = (0.32F + 0.16F * beam.heat) * wobble;
            PixelVfx.beamDots(buffer, cam, camera, beam.from, beam.to, outer * 0.6F, outer, 255, (int)(70 + 90 * beam.heat), 20, alpha / 2);
            PixelVfx.beamDots(buffer, cam, camera, beam.from, beam.to, outer * 0.45F, outer * 0.55F, 255, 190, 70, alpha);
            PixelVfx.beamDots(buffer, cam, camera, beam.from, beam.to, outer * 0.35F, outer * 0.25F, 255, 250, 220, alpha);
         }
         double seconds = (level.getGameTime() + partial) / 20.0;
         for (int i = 0; i < cores.size(); i++) {
            float size = coreSize.get(i);
            PixelVfx.sphereShell(buffer, cam, camera, cores.get(i), size, (float)seconds, 255, (int)(120 + 80 * size / 2.1F), 40, 150);
            PixelVfx.crossGlow(buffer, cam, camera, cores.get(i), size * 0.7F, 255, 230, 160, 200);
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

   public static void onLevelUnload(LevelEvent.Unload event) {
      if (event.getLevel().isClientSide()) {
         BEAMS.clear();
         STEPS.clear();
         HUMS.values().forEach(h -> Minecraft.getInstance().getSoundManager().stop(h));
         HUMS.clear();
      }
   }

   // ------------------------------------------------------------------ helpers

   static void burst(ClientLevel level, ParticleOptions p, Vec3 at, int n, double speed) {
      for (int i = 0; i < n; i++) {
         level.addParticle(p, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 2 * speed, (RANDOM.nextDouble() - 0.3) * 2 * speed,
            (RANDOM.nextDouble() - 0.5) * 2 * speed);
      }
   }

   /** A horizontal ring; {@code out} is the outward speed (negative = inward). */
   static void ring(ClientLevel level, ParticleOptions p, Vec3 at, double radius, int n, double out) {
      for (int i = 0; i < n; i++) {
         double ang = Math.PI * 2 * i / n + RANDOM.nextDouble() * 0.1;
         double cx = Math.cos(ang), cz = Math.sin(ang);
         level.addParticle(p, at.x + cx * radius, at.y, at.z + cz * radius, cx * out, 0.02, cz * out);
      }
   }

   static void scatter(ClientLevel level, ParticleOptions p, Vec3 at, double radius, int n) {
      for (int i = 0; i < n; i++) {
         double ang = RANDOM.nextDouble() * Math.PI * 2;
         double rr = Math.sqrt(RANDOM.nextDouble()) * radius;
         level.addParticle(p, at.x + Math.cos(ang) * rr, at.y + 0.1, at.z + Math.sin(ang) * rr, 0, 0.05 + RANDOM.nextDouble() * 0.05, 0);
      }
   }

   static void trail(ClientLevel level, ParticleOptions p, Vec3 from, Vec3 to, int n) {
      for (int i = 0; i <= n; i++) {
         Vec3 q = from.lerp(to, i / (double)Math.max(1, n));
         level.addParticle(p, q.x + RANDOM.nextGaussian() * 0.06, q.y + RANDOM.nextGaussian() * 0.06, q.z + RANDOM.nextGaussian() * 0.06, 0, 0.01, 0);
      }
   }

   /** The Thermal Clap fire cone: flames along the axis at->target, spread {@code spread} (radians) about it. */
   static void cone(ClientLevel level, Vec3 at, Vec3 target, int n, double spread) {
      Vec3 axis = target.subtract(at);
      double len = axis.length();
      if (len < 1.0E-3) {
         return;
      }
      Vec3 dir = axis.scale(1.0 / len);
      for (int i = 0; i < n; i++) {
         Vec3 d = dir.add(RANDOM.nextGaussian() * spread * 0.5, RANDOM.nextGaussian() * spread * 0.3, RANDOM.nextGaussian() * spread * 0.5).normalize();
         double speed = 0.35 + RANDOM.nextDouble() * 0.45;
         level.addParticle(i % 3 == 0 ? EMBER_HOT : ParticleTypes.FLAME, at.x, at.y, at.z, d.x * speed, d.y * speed, d.z * speed);
      }
   }
}
