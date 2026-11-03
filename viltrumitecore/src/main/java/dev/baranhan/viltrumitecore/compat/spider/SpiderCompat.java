package dev.baranhan.viltrumitecore.compat.spider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one entry point to another mod's Spider-Man (docs/venom/spiderman-compatibility.md). Detects the mod, picks the
 * versioned adapter, logs what it bound, and hands every call to it. Without the mod (or with the layer switched off)
 * the bridge is {@link NoSpiderBridge} and Venom plays its base mode. No hard dependency: their classes are touched
 * only by reflection inside an adapter, and only after {@link ModList} says the mod is there.
 */
public final class SpiderCompat {
   static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/SpiderCompat");
   public static final String MOD_ID = "falbiksheroes";

   /** A versioned adapter factory. Newest first; {@code accepts} is a version-range test. */
   record Adapter(String id, java.util.function.Predicate<String> accepts, java.util.function.BiFunction<ClassLoader, String, SpiderBridge> make) {
   }

   static final List<Adapter> ADAPTERS = List.of(
      new Adapter(FalbiksSpiderV0.ID, v -> v.startsWith("0.1") || v.equals("0.1"), FalbiksSpiderV0::new)
   );

   private static volatile SpiderBridge bridge = new NoSpiderBridge("not initialised yet");
   private static volatile boolean enabled = true;

   private SpiderCompat() {
   }

   /** Common setup: detect, bind, log. Safe to call again (a /reload of the switch). */
   public static synchronized void init(boolean enabledByConfig) {
      enabled = enabledByConfig;
      if (!enabledByConfig) {
         bridge = new NoSpiderBridge("switched off (spiderCompatEnabled=false)");
         LOG.info("[SpiderCompat] switched off by config; Venom runs in base mode");
         return;
      }
      ModList mods = ModList.get();
      if (mods == null || !mods.isLoaded(MOD_ID)) {
         bridge = new NoSpiderBridge(MOD_ID + " not installed");
         LOG.info("[SpiderCompat] {} not installed; Venom runs in base mode", MOD_ID);
         return;
      }
      String version = mods.getModContainerById(MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
      bridge = bind(SpiderCompat.class.getClassLoader(), version);
      report(bridge, version);
   }

   /** Pure of Forge: picks and binds the adapter for a version in a class loader (tests use stub loaders). */
   static SpiderBridge bind(ClassLoader loader, String version) {
      Adapter chosen = null;
      for (Adapter a : ADAPTERS) {
         if (a.accepts().test(version)) {
            chosen = a;
            break;
         }
      }
      boolean guessed = chosen == null;
      if (chosen == null) {
         chosen = ADAPTERS.get(0); // unknown version: try the newest adapter, every member is checked anyway
      }
      try {
         SpiderBridge b = chosen.make().apply(loader, version);
         if (b.features().isEmpty()) {
            List<String> why = new ArrayList<>(b.diagnostics());
            why.add(0, "adapter " + chosen.id() + " bound nothing for version " + version);
            return new NoSpiderBridge(String.join("; ", why));
         }
         if (guessed) {
            LOG.warn("[SpiderCompat] {} {} is newer than every adapter; trying {} (each member checked)", MOD_ID, version, chosen.id());
         }
         return b;
      } catch (RuntimeException | LinkageError e) {
         LOG.error("[SpiderCompat] adapter {} failed to bind; Venom runs in base mode", chosen.id(), e);
         return new NoSpiderBridge("adapter " + chosen.id() + " failed: " + e);
      }
   }

   private static void report(SpiderBridge b, String version) {
      LOG.info("[SpiderCompat] {} {} -> adapter {}: {}/{} features {}", MOD_ID, version, b.id(), b.features().size(),
         SpiderFeature.values().length, b.features());
      for (String line : b.diagnostics()) {
         LOG.warn("[SpiderCompat]   {}", line);
      }
   }

   /** Lines for {@code /viltrumite compat spider}. */
   public static List<String> describe() {
      SpiderBridge b = bridge;
      List<String> out = new ArrayList<>();
      out.add("adapter=" + b.id() + " enabled=" + enabled);
      for (SpiderFeature f : SpiderFeature.values()) {
         out.add(f + "=" + (b.features().contains(f) ? "on" : "off"));
      }
      out.addAll(b.diagnostics());
      return out;
   }

   public static SpiderBridge bridge() {
      return bridge;
   }

   /** Test hook. */
   static void use(SpiderBridge b) {
      bridge = b;
   }

   public static boolean present() {
      return !(bridge instanceof NoSpiderBridge);
   }

   public static boolean has(SpiderFeature f) {
      return bridge.features().contains(f);
   }

   public static boolean isSpider(@Nullable Player p) {
      return p != null && bridge.isSpider(p);
   }

   public static Optional<SpiderLine> line(@Nullable Player p) {
      return p == null ? Optional.empty() : bridge.line(p);
   }

   public static boolean cutLine(@Nullable ServerPlayer p) {
      return p != null && bridge.cutLine(p);
   }

   public static boolean isWebProjectile(@Nullable Entity e) {
      return e != null && present() && bridge.isWebProjectile(e);
   }

   public static WebKind webKind(@Nullable Entity e) {
      return e == null ? WebKind.UNKNOWN : bridge.webKind(e);
   }

   public static int webStacks(@Nullable LivingEntity e) {
      return e == null ? 0 : bridge.webStacks(e);
   }

   public static boolean cocooned(@Nullable LivingEntity e) {
      return e != null && bridge.cocooned(e);
   }

   public static boolean tearWeb(@Nullable LivingEntity e) {
      return e != null && bridge.tearWeb(e);
   }

   public static void releaseInputs(@Nullable ServerPlayer p) {
      if (p != null) {
         bridge.releaseInputs(p);
      }
   }

   public static boolean drive(@Nullable ServerPlayer p, SpiderAbility a, boolean down) {
      return p != null && bridge.drive(p, a, down);
   }

   public static boolean drivePrimary(@Nullable ServerPlayer p, boolean down) {
      return p != null && bridge.drivePrimary(p, down);
   }

   @Nullable
   public static Vec3 wallNormal(@Nullable Player p) {
      return p == null ? null : bridge.wallNormal(p);
   }

   @Nullable
   public static String inputChannel() {
      return bridge.inputChannel();
   }
}
