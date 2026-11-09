package dev.baranhan.viltrumitecore.client.perf;

import dev.baranhan.viltrumitecore.config.ViltrumiteClientConfig;
import dev.baranhan.viltrumitecore.perf.Degrade;
import dev.baranhan.viltrumitecore.perf.PerfPriority;
import dev.baranhan.viltrumitecore.perf.PerfRegistry;
import dev.baranhan.viltrumitecore.perf.TickBudget;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The one gate every particle passes ({@code ParticleEngineBudgetMixin} on {@code ParticleEngine.add}): a live cap,
 * a per-tick spawn budget with priority reserves, and LOD/quality/load thinning. At HIGH under normal load (frame
 * under 33 ms, under the caps) every particle passes untouched. Emitters may set the priority of what they spawn
 * with {@link #run}/{@link #push}; without a scope block-break dust is LOW, smoke HIGH (its volume is the look),
 * everything else NORMAL. Kill switch: -Dviltrumitecore.perf.particles=false. Render thread only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class ParticleBudget {
   public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("viltrumitecore.perf.particles"));
   public static final boolean ADAPTIVE = !"false".equalsIgnoreCase(System.getProperty("viltrumitecore.perf.adaptive"));
   public static final int BASE_RATE = Integer.getInteger("viltrumitecore.perf.particlesPerTick", 1500);
   public static final int BASE_LIVE = Integer.getInteger("viltrumitecore.perf.liveParticles", 8000);
   private static final PerfPriority[] PRIORITIES = PerfPriority.values();
   private static final int PRETHINNED = 0x100;
   private static final TickBudget BUDGET = PerfRegistry.register(new TickBudget("particles", BASE_RATE));
   private static final Degrade.Thinner THIN = new Degrade.Thinner(0x5EEDF00DL);
   private static final ClassValue<PerfPriority> DEFAULTS = new ClassValue<PerfPriority>() {
      @Override
      protected PerfPriority computeValue(Class<?> type) {
         return defaultFor(type);
      }
   };
   private static final int[] SCOPES = new int[32];
   private static int depth;
   private static EffectQuality quality = EffectQuality.HIGH;
   private static double pressure;
   private static int live;
   private static int liveLimit = BASE_LIVE;
   private static int ticks;
   private static int admitted;
   private static int thinned;
   private static int denied;
   private static int capped;
   private static int admittedPerSecond;
   private static int thinnedPerSecond;
   private static int deniedPerSecond;
   private static int cappedPerSecond;

   private ParticleBudget() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.START) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      quality = EffectQuality.parse(ViltrumiteClientConfig.INSTANCE.effectQuality, EffectQuality.HIGH);
      pressure = ADAPTIVE && quality.adaptive ? ClientFrameStats.pressure() : 0.0;
      live = mc.level == null ? 0 : digits(mc.particleEngine.countParticles());
      liveLimit = ParticleLod.liveLimit(BASE_LIVE, quality, pressure);
      BUDGET.reset(ParticleLod.rateLimit(BASE_RATE, quality, pressure));
      if (++ticks >= 20) {
         ticks = 0;
         admittedPerSecond = admitted;
         thinnedPerSecond = thinned;
         deniedPerSecond = denied;
         cappedPerSecond = capped;
         admitted = 0;
         thinned = 0;
         denied = 0;
         capped = 0;
      }
   }

   /** False: the particle is dropped before it is ever ticked or drawn. */
   public static boolean admit(Particle particle) {
      if (!ENABLED) {
         return true;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !mc.isSameThread()) {
         return true;
      }

      int scope = depth > 0 ? SCOPES[Math.min(depth, SCOPES.length) - 1] : -1;
      PerfPriority priority = scope >= 0 ? PRIORITIES[scope & 0xFF] : DEFAULTS.get(particle.getClass());
      if (priority.critical()) {
         admitted++;
         live++;
         return true;
      }

      if (live >= (int)(liveLimit * priority.share)) {
         capped++;
         return false;
      }

      boolean prethinned = scope >= 0 && (scope & PRETHINNED) != 0;
      if (!prethinned) {
         double keep = ParticleLod.keep(quality, priority, distanceSq(mc, particle), pressure);
         if (keep < 1.0 && !THIN.keepOne(keep)) {
            thinned++;
            return false;
         }
      }

      if (!BUDGET.tryTake(priority)) {
         denied++;
         return false;
      }

      admitted++;
      live++;
      return true;
   }

   /**
    * For emitters that build many particles in a loop: how many of {@code wanted} to build at this point (quality,
    * distance and load). Spawn them inside {@link #pushPrethinned} so they are not thinned twice.
    */
   public static int allow(PerfPriority priority, double x, double y, double z, int wanted) {
      if (!ENABLED || wanted <= 0 || priority.critical()) {
         return Math.max(0, wanted);
      }

      double keep = ParticleLod.keep(quality, priority, distanceSq(Minecraft.getInstance(), x, y, z), pressure);
      int n = keep >= 1.0 ? wanted : THIN.keep(wanted, keep);
      thinned += wanted - n;
      return n;
   }

   public static void push(PerfPriority priority) {
      pushScope(priority.ordinal());
   }

   public static void pushPrethinned(PerfPriority priority) {
      pushScope(priority.ordinal() | PRETHINNED);
   }

   public static void pop() {
      if (depth > 0) {
         depth--;
      }
   }

   public static void run(PerfPriority priority, Runnable body) {
      push(priority);
      try {
         body.run();
      } finally {
         pop();
      }
   }

   public static EffectQuality quality() {
      return quality;
   }

   public static double pressure() {
      return pressure;
   }

   public static boolean adaptive() {
      return ADAPTIVE && quality.adaptive;
   }

   public static int live() {
      return live;
   }

   public static int liveLimit() {
      return liveLimit;
   }

   public static int admittedPerSecond() {
      return admittedPerSecond;
   }

   public static int thinnedPerSecond() {
      return thinnedPerSecond;
   }

   /** Dropped by the spawn budget or the live cap during the last second. */
   public static int overBudgetPerSecond() {
      return deniedPerSecond + cappedPerSecond;
   }

   static PerfPriority defaultFor(Class<?> type) {
      if (TerrainParticle.class.isAssignableFrom(type)) {
         return PerfPriority.LOW;
      }

      return type.getName().toLowerCase(Locale.ROOT).contains("smoke") ? PerfPriority.HIGH : PerfPriority.NORMAL;
   }

   private static void pushScope(int value) {
      if (depth < SCOPES.length) {
         SCOPES[depth] = value;
      }

      depth++;
   }

   private static double distanceSq(Minecraft mc, Particle particle) {
      AABB box = particle.getBoundingBox();
      return distanceSq(mc, (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
   }

   private static double distanceSq(Minecraft mc, double x, double y, double z) {
      Camera camera = mc.gameRenderer.getMainCamera();
      if (!camera.isInitialized()) {
         return 0.0;
      }

      Vec3 eye = camera.getPosition();
      double dx = x - eye.x;
      double dy = y - eye.y;
      double dz = z - eye.z;
      return dx * dx + dy * dy + dz * dz;
   }

   private static int digits(String text) {
      int value = 0;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= '0' && c <= '9') {
            value = value * 10 + (c - '0');
         }
      }

      return value;
   }
}
