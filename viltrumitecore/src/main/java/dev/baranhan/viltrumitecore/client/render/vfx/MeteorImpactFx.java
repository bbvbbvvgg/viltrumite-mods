package dev.baranhan.viltrumitecore.client.render.vfx;

import dev.baranhan.viltrumitecore.client.meteor.MeteorRainAudio;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The impact of a meteor in layers (client side, from the entity event): a low dust ring rolling out along
 * the ground, a hot fireball column that cools into a dark rising billow, ballistic debris sparks, ash that
 * drifts down for seconds, and a smouldering crater that keeps breathing thin smoke for half a minute. The
 * kept vanilla flash, emitter and {@link MeteorImpactVFXManager} ring / shake play beside it. Counts fall
 * with the camera distance; the crater emitters are bounded and cleared on a level change or logout.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class MeteorImpactFx {
   /** Smouldering craters kept at once (oldest dropped first). */
   public static final int MAX_CRATERS = 16;
   /** Ticks a crater smoulders. */
   public static final int CRATER_TICKS = 600;
   /** Farther than this an impact draws nothing (the far boom is audio only). */
   public static final double DRAW_RANGE = 360.0;

   private static final List<Crater> CRATERS = new ArrayList<>();
   private static ClientLevel lastLevel;

   private MeteorImpactFx() {
   }

   /** Pure: share of the full particle count drawn at a camera distance. */
   public static float detail(double distance) {
      if (distance <= 96.0) {
         return 1.0F;
      }
      if (distance <= 200.0) {
         return 0.5F;
      }
      return distance <= DRAW_RANGE ? 0.25F : 0.0F;
   }

   public static void play(Level level, Vec3 at, Vec3 motion) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || level != mc.level) {
         return;
      }

      Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
      double distance = at.distanceTo(camera);
      MeteorRainAudio.impact(at, distance);
      float detail = detail(distance);
      if (detail <= 0.0F) {
         return;
      }

      RandomSource random = level.getRandom();
      float grow = (float)Mth.clamp(1.0 + (distance - 60.0) / 140.0, 1.0, 2.2);

      // 1. dust ring: low, wide, earthy, rolling outward and slowing down
      int ring = count(34, detail);
      for (int i = 0; i < ring; i++) {
         double a = Mth.TWO_PI * (i + random.nextFloat() * 0.6) / ring;
         double r = 2.5 + random.nextDouble() * 3.0;
         double speed = 0.55 + random.nextDouble() * 0.55;
         Vec3 p = at.add(Math.cos(a) * r, 0.4 + random.nextDouble() * 1.2, Math.sin(a) * r);
         Vec3 v = new Vec3(Math.cos(a) * speed, 0.02 + random.nextDouble() * 0.06, Math.sin(a) * speed);
         spawn(mc, ViltrumiteParticles.METEOR_DUST.get(), p, v, (2.4F + random.nextFloat() * 1.2F) * grow);
      }

      // 2. the fireball: short-lived flames and flares pushed upward
      int fire = count(22, detail);
      for (int i = 0; i < fire; i++) {
         Vec3 p = at.add(MeteorTrailFx.sphere(random, 3.0)).add(0.0, 1.5 + random.nextDouble() * 3.0, 0.0);
         Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.3, 0.25 + random.nextDouble() * 0.45, (random.nextDouble() - 0.5) * 0.3);
         spawn(mc, i % 3 == 0 ? ViltrumiteParticles.FIRE_FLARE.get() : ViltrumiteParticles.METEOR_FLAME.get(), p, v, (3.0F + random.nextFloat() * 2.5F) * grow);
      }

      // 3. the billow: a column of puffs that start glowing and cool into dark smoke as they climb
      int billow = count(26, detail);
      for (int i = 0; i < billow; i++) {
         double h = random.nextDouble();
         Vec3 p = at.add(MeteorTrailFx.sphere(random, 3.5 - h * 1.5)).add(0.0, 2.0 + h * 7.0, 0.0);
         Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.18, 0.18 + h * 0.5 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.18);
         spawn(mc, ViltrumiteParticles.METEOR_BILLOW.get(), p, v, (1.7F + random.nextFloat() * 1.0F) * grow);
      }

      // 4. debris sparks on ballistic arcs
      int sparks = count(46, detail);
      for (int i = 0; i < sparks; i++) {
         double a = random.nextDouble() * Mth.TWO_PI;
         double up = 0.5 + random.nextDouble() * 0.9;
         double out = 0.35 + random.nextDouble() * 0.85;
         Vec3 v = new Vec3(Math.cos(a) * out, up, Math.sin(a) * out);
         spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), at.add(0.0, 1.0, 0.0).add(MeteorTrailFx.sphere(random, 1.5)), v,
            (1.6F + random.nextFloat() * 2.0F) * Math.min(1.6F, grow));
      }

      // 5. ash drifting down out of the column for seconds
      int ash = count(30, detail);
      for (int i = 0; i < ash; i++) {
         Vec3 p = at.add(MeteorTrailFx.sphere(random, 9.0)).add(0.0, 8.0 + random.nextDouble() * 12.0, 0.0);
         spawn(mc, ViltrumiteParticles.METEOR_ASH.get(), p, new Vec3(0.0, -0.02, 0.0), 1.4F + random.nextFloat());
      }

      if (distance <= 200.0) {
         if (CRATERS.size() >= MAX_CRATERS) {
            CRATERS.remove(0);
         }
         CRATERS.add(new Crater(at, CRATER_TICKS));
      }
   }

   /** A distant impact this client has no entity for: a far fireball and a dark column on the horizon. */
   public static void far(Vec3 at) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }

      double distance = at.distanceTo(mc.gameRenderer.getMainCamera().getPosition());
      if (distance > DRAW_RANGE + 120.0) {
         return;
      }

      RandomSource random = mc.level.getRandom();
      for (int i = 0; i < 6; i++) {
         Vec3 p = at.add(MeteorTrailFx.sphere(random, 4.0)).add(0.0, 3.0 + random.nextDouble() * 4.0, 0.0);
         spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), p, new Vec3(0.0, 0.3, 0.0), 7.0F + random.nextFloat() * 4.0F);
      }
      for (int i = 0; i < 10; i++) {
         double h = random.nextDouble();
         Vec3 p = at.add(MeteorTrailFx.sphere(random, 5.0)).add(0.0, 4.0 + h * 12.0, 0.0);
         spawn(mc, ViltrumiteParticles.METEOR_BILLOW.get(), p, new Vec3(0.0, 0.25 + h * 0.4, 0.0), 4.5F + random.nextFloat() * 2.0F);
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase != Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         CRATERS.clear();
      }
      if (mc.level == null || mc.isPaused() || CRATERS.isEmpty()) {
         return;
      }

      RandomSource random = mc.level.getRandom();
      Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
      Iterator<Crater> it = CRATERS.iterator();
      while (it.hasNext()) {
         Crater c = it.next();
         if (--c.ticks <= 0) {
            it.remove();
            continue;
         }

         if (c.at.distanceToSqr(camera) > 200.0 * 200.0) {
            continue;
         }

         // thinning out over the crater's life
         float life = c.ticks / (float)CRATER_TICKS;
         if (random.nextFloat() < 0.15F + 0.45F * life) {
            Vec3 p = c.at.add((random.nextDouble() - 0.5) * 6.0, 0.3 + random.nextDouble(), (random.nextDouble() - 0.5) * 6.0);
            spawn(mc, ViltrumiteParticles.FIRE_SMOKE.get(), p, new Vec3(0.0, 0.04, 0.0), 1.0F + life);
         }
         if (random.nextFloat() < 0.12F * life) {
            Vec3 p = c.at.add((random.nextDouble() - 0.5) * 5.0, 0.4, (random.nextDouble() - 0.5) * 5.0);
            spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), p, new Vec3(0.0, 0.25 + random.nextDouble() * 0.2, 0.0), 1.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      CRATERS.clear();
   }

   /** Craters smouldering now (debug and tests). */
   public static int craters() {
      return CRATERS.size();
   }

   private static int count(int full, float detail) {
      return Math.max(1, Math.round(full * detail));
   }

   private static void spawn(Minecraft mc, net.minecraft.core.particles.ParticleOptions type, Vec3 at, Vec3 v, float scale) {
      net.minecraft.client.particle.Particle particle = mc.particleEngine.createParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
      if (particle != null && scale != 1.0F) {
         particle.scale(scale);
      }
   }

   private static final class Crater {
      final Vec3 at;
      int ticks;

      Crater(Vec3 at, int ticks) {
         this.at = at;
         this.ticks = ticks;
      }
   }
}
