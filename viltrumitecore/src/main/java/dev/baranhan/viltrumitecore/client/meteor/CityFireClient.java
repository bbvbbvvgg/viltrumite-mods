package dev.baranhan.viltrumitecore.client.meteor;

import dev.baranhan.viltrumitecore.client.render.vfx.MeteorTrailFx;
import dev.baranhan.viltrumitecore.network.packet.MeteorRainFxS2CPacket;
import dev.baranhan.viltrumitecore.particle.ViltrumiteParticles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
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
 * What the burning city looks and sounds like on this client. Single burning blocks ({@code MeteorFireBlock}
 * animate ticks) lick flames, puff smoke, spit embers and crackle (rate limited). The server's zone summary
 * ({@link MeteorRainFxS2CPacket#FIRE_ZONES}, every second) drives the big picture: a smoke column per zone
 * sized by its radius and intensity, lit orange at its foot, embers riding the updraft, and the fire roar
 * loops ({@link MeteorRainAudio}). Brands fly as small burning arcs; flares burst. Everything is bounded:
 * zones expire without updates, brands are capped and a per-tick particle budget applies.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class CityFireClient {
   /** Zones are dropped when no summary arrived for this long (the server sends one a second). */
   public static final int ZONE_STALE = 70;
   public static final int MAX_BRANDS = 32;
   /** Zone particles per tick, all zones together. */
   public static final int ZONE_BUDGET = 48;
   /** Zones farther than this draw no column. */
   public static final double ZONE_DRAW = 300.0;

   private static final List<float[]> ZONES = new ArrayList<>();
   private static final List<Brand> BRANDS = new ArrayList<>();
   private static int zonesAt = -1000;
   private static ClientLevel lastLevel;
   private static int clock;

   private CityFireClient() {
   }

   /** One animate tick of a burning block (called for blocks near the camera, a few times a second each). */
   public static void flameTick(Level level, BlockPos pos, RandomSource random) {
      Minecraft mc = Minecraft.getInstance();
      if (level != mc.level) {
         return;
      }
      double x = pos.getX() + 0.5;
      double y = pos.getY();
      double z = pos.getZ() + 0.5;
      if (random.nextInt(18) == 0) {
         MeteorRainAudio.crackle(x, y + 0.5, z);
      }
      if (random.nextInt(3) == 0) {
         spawn(mc, ViltrumiteParticles.METEOR_FLAME.get(), x + (random.nextDouble() - 0.5) * 0.8, y + 0.2 + random.nextDouble() * 0.6,
            z + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.03 + random.nextDouble() * 0.05, 0.0, 0.55F + random.nextFloat() * 0.5F);
      }
      if (random.nextInt(5) == 0) {
         spawn(mc, ViltrumiteParticles.FIRE_SMOKE.get(), x + (random.nextDouble() - 0.5) * 0.6, y + 0.9, z + (random.nextDouble() - 0.5) * 0.6,
            0.0, 0.05, 0.0, 0.45F + random.nextFloat() * 0.35F);
      }
      if (random.nextInt(9) == 0) {
         spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), x, y + 0.6, z, (random.nextDouble() - 0.5) * 0.08, 0.18 + random.nextDouble() * 0.2,
            (random.nextDouble() - 0.5) * 0.08, 0.8F);
      }
   }

   /** New zone summary from the server: (x, y, z, intensity, radius) groups; empty clears. */
   static void zones(float[] data) {
      ZONES.clear();
      int n = Math.min(MeteorRainFxS2CPacket.MAX_ZONES, data.length / MeteorRainFxS2CPacket.ZONE_STRIDE);
      for (int i = 0; i < n; i++) {
         float[] z = new float[MeteorRainFxS2CPacket.ZONE_STRIDE];
         System.arraycopy(data, i * MeteorRainFxS2CPacket.ZONE_STRIDE, z, 0, MeteorRainFxS2CPacket.ZONE_STRIDE);
         z[3] = Mth.clamp(z[3], 0.0F, 1.0F);
         z[4] = Mth.clamp(z[4], 1.0F, 48.0F);
         ZONES.add(z);
      }
      zonesAt = clock;
   }

   /** The {@code max} zones nearest to {@code at} by the distance to their edge. */
   public static List<float[]> nearestZones(Vec3 at, int max) {
      if (ZONES.isEmpty()) {
         return List.of();
      }
      List<float[]> sorted = new ArrayList<>(ZONES);
      sorted.sort(Comparator.comparingDouble(z -> edgeDistance(z, at)));
      return sorted.size() > max ? sorted.subList(0, max) : sorted;
   }

   private static double edgeDistance(float[] z, Vec3 at) {
      double dx = z[0] - at.x;
      double dy = z[1] - at.y;
      double dz = z[2] - at.z;
      return Math.max(0.0, Math.sqrt(dx * dx + dy * dy + dz * dz) - z[4]);
   }

   static void brand(Vec3 from, Vec3 to, int ticks) {
      if (BRANDS.size() >= MAX_BRANDS) {
         BRANDS.remove(0);
      }
      BRANDS.add(new Brand(from, to, Mth.clamp(ticks, 4, 80)));
      MeteorRainAudio.brand(from);
   }

   static void flare(Vec3 at, float power) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || at.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 160.0 * 160.0) {
         return;
      }
      RandomSource random = mc.level.getRandom();
      int n = 4 + Math.round(6 * Mth.clamp(power, 0.0F, 1.0F));
      for (int i = 0; i < n; i++) {
         Vec3 p = at.add(MeteorTrailFx.sphere(random, 1.2));
         spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.2, 0.12 + random.nextDouble() * 0.2,
            (random.nextDouble() - 0.5) * 0.2, 1.0F + power);
      }
      for (int i = 0; i < n; i++) {
         spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.5, 0.2 + random.nextDouble() * 0.4,
            (random.nextDouble() - 0.5) * 0.5, 1.0F);
      }
      MeteorRainAudio.flare(at, power);
   }

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
      if (!ZONES.isEmpty() && clock - zonesAt > ZONE_STALE) {
         ZONES.clear();
      }
      RandomSource random = mc.level.getRandom();
      Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
      columns(mc, random, camera);
      brands(mc, random);
   }

   /** Smoke columns over the zones: rate from intensity and size, fewer far away, within one budget. */
   private static void columns(Minecraft mc, RandomSource random, Vec3 camera) {
      int budget = ZONE_BUDGET;
      for (float[] z : ZONES) {
         double d = Math.sqrt(camera.distanceToSqr(z[0], z[1], z[2]));
         if (d > ZONE_DRAW || budget <= 0) {
            continue;
         }
         float rate = zoneRate(z[3], z[4], d);
         int n = (int)rate + (random.nextFloat() < rate - (int)rate ? 1 : 0);
         float grow = (float)Mth.clamp(1.0 + (d - 40.0) / 120.0, 1.0, 2.5);
         for (int i = 0; i < n && budget > 0; i++, budget--) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = Math.sqrt(random.nextDouble()) * z[4] * 0.8;
            double x = z[0] + Math.cos(a) * r;
            double zz = z[2] + Math.sin(a) * r;
            double y = z[1] + 1.0 + random.nextDouble() * 4.0;
            // the column: wide dark puffs climbing on the updraft, a little hotter the more intense the zone
            float size = (1.4F + z[4] * 0.12F + random.nextFloat()) * grow;
            spawn(mc, ViltrumiteParticles.FIRE_SMOKE.get(), x, y, zz, (random.nextDouble() - 0.5) * 0.04, 0.12 + 0.12 * z[3], (random.nextDouble() - 0.5) * 0.04, size);
            if (random.nextFloat() < 0.35F * z[3]) {
               // the orange glow at its foot
               spawn(mc, ViltrumiteParticles.FIRE_FLARE.get(), x, z[1] + 0.5 + random.nextDouble() * 2.0, zz, 0.0, 0.08, 0.0, (1.6F + z[3] * 1.4F) * grow);
            }
            if (d < 120.0 && random.nextFloat() < 0.4F * z[3]) {
               spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), x, z[1] + 1.0, zz, (random.nextDouble() - 0.5) * 0.15, 0.35 + random.nextDouble() * 0.3,
                  (random.nextDouble() - 0.5) * 0.15, 1.0F);
            }
         }
      }
   }

   /** Pure: smoke puffs per tick for a zone (intensity 0..1, radius, camera distance). */
   public static float zoneRate(float intensity, float radius, double distance) {
      float base = intensity * (0.4F + radius / 10.0F);
      float far = distance < 80.0 ? 1.0F : distance < 180.0 ? 0.6F : 0.35F;
      return Mth.clamp(base * far, 0.0F, 4.0F);
   }

   private static void brands(Minecraft mc, RandomSource random) {
      Iterator<Brand> it = BRANDS.iterator();
      while (it.hasNext()) {
         Brand b = it.next();
         b.age++;
         if (b.age > b.ticks) {
            it.remove();
            flare(b.to, 0.4F);
            continue;
         }
         Vec3 p = brandPos(b.from, b.to, b.age / (float)b.ticks);
         spawn(mc, ViltrumiteParticles.METEOR_FLAME.get(), p.x, p.y, p.z, 0.0, 0.01, 0.0, 0.8F + random.nextFloat() * 0.4F);
         if (b.age % 2 == 0) {
            spawn(mc, ViltrumiteParticles.FIRE_SMOKE.get(), p.x, p.y, p.z, 0.0, 0.03, 0.0, 0.5F);
         }
         if (random.nextInt(3) == 0) {
            spawn(mc, ViltrumiteParticles.METEOR_EMBER.get(), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1, 0.7F);
         }
      }
   }

   /** Pure: a brand's position along its arc at {@code t} 0..1 (a parabola peaking a quarter of the span up). */
   public static Vec3 brandPos(Vec3 from, Vec3 to, float t) {
      double span = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
      double lift = Math.max(2.0, span * 0.25) * 4.0 * t * (1.0 - t);
      return new Vec3(Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y) + lift, Mth.lerp(t, from.z, to.z));
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   static void clear() {
      ZONES.clear();
      BRANDS.clear();
      zonesAt = -1000;
   }

   /** Zones known now (debug and QA). */
   public static int zoneCount() {
      return ZONES.size();
   }

   private static void spawn(Minecraft mc, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz, float scale) {
      Particle particle = mc.particleEngine.createParticle(type, x, y, z, dx, dy, dz);
      if (particle != null && scale != 1.0F) {
         particle.scale(scale);
      }
   }

   private static final class Brand {
      final Vec3 from;
      final Vec3 to;
      final int ticks;
      int age;

      Brand(Vec3 from, Vec3 to, int ticks) {
         this.from = from;
         this.to = to;
         this.ticks = ticks;
      }
   }
}
