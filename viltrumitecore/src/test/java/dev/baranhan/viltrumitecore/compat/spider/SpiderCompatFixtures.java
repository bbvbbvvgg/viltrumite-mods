package dev.baranhan.viltrumitecore.compat.spider;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Stub classes with the names and signatures of Falbiks Heroes 0.1.0 (read from the studied JAR, see
 * docs/venom/spiderman-compatibility.md §2), compiled at test time into a private class loader. Variants replace one
 * source to simulate a missing or changed member. No byte of the real JAR is used or copied.
 */
final class SpiderCompatFixtures {
   static final String P = "dev/falbiks/heroes/";

   private SpiderCompatFixtures() {
   }

   /** The 0.1.0 shapes. */
   static Map<String, String> current() {
      Map<String, String> m = new LinkedHashMap<>();
      m.put(P + "ability/AbilitySlot.java", """
         package dev.falbiks.heroes.ability;
         public enum AbilitySlot { ABILITY_1, ABILITY_2, ABILITY_3, ULTIMATE, SUMMON }
         """);
      m.put(P + "hero/HeroState.java", """
         package dev.falbiks.heroes.hero;
         public class HeroState {
            public long clock;
            public transient int revision;
            public final java.util.Map<String, Integer> cooldowns = new java.util.HashMap<>();
            public void startCooldown(String key, int ticks) { this.cooldowns.put(key, ticks); this.revision++; }
         }
         """);
      m.put(P + "hero/HeroData.java", """
         package dev.falbiks.heroes.hero;
         import dev.falbiks.heroes.ability.AbilitySlot;
         public final class HeroData {
            public static HeroData INSTANCE;
            public final transient java.util.EnumSet<AbilitySlot> held = java.util.EnumSet.noneOf(AbilitySlot.class);
            public net.minecraft.resources.ResourceLocation id;
            public HeroState state;
            public static HeroData get(net.minecraft.world.entity.player.Player p) { return INSTANCE; }
            public net.minecraft.resources.ResourceLocation heroId() { return this.id; }
            public HeroState state() { return this.state; }
         }
         """);
      m.put(P + "hero/HeroManager.java", """
         package dev.falbiks.heroes.hero;
         import dev.falbiks.heroes.ability.AbilitySlot;
         public final class HeroManager {
            public static final java.util.List<String> LOG = new java.util.ArrayList<>();
            public static void handleInput(net.minecraft.server.level.ServerPlayer p, AbilitySlot slot, boolean down) {
               LOG.add(slot + ":" + down);
               if (!down && HeroData.INSTANCE != null) HeroData.INSTANCE.held.remove(slot);
            }
            public static void handlePrimary(net.minecraft.server.level.ServerPlayer p, boolean down) { LOG.add("PRIMARY:" + down); }
         }
         """);
      m.put(P + "heroes/spider/SpiderState.java", """
         package dev.falbiks.heroes.heroes.spider;
         public final class SpiderState extends dev.falbiks.heroes.hero.HeroState {
            public boolean swinging;
            public double ax;
            public double ay;
            public double az;
            public float rope;
            public long swingClock;
            public long swingGame = -1000L;
            public long noFallUntil;
         }
         """);
      m.put(P + "heroes/spider/SpiderHero.java", """
         package dev.falbiks.heroes.heroes.spider;
         import net.minecraft.server.level.ServerPlayer;
         import net.minecraft.world.entity.LivingEntity;
         import net.minecraft.world.entity.player.Player;
         import net.minecraft.world.phys.Vec3;
         public class SpiderHero {
            public static final String WEB_TAG = "falbiksheroes_spider_web";
            public static final String COCOON_TAG = "falbiksheroes_spider_cocoon";
            public static int endSwingCalls;
            public static Vec3 hand(Player p) { return new Vec3(1.0, 2.0, 3.0); }
            static void endSwing(ServerPlayer p, SpiderState s, boolean airborne) {
               s.swinging = false; s.noFallUntil = s.clock + 60L; s.startCooldown("swing", 6); s.revision++; endSwingCalls++;
            }
            public static Vec3 wallNormal(Player p) { return null; }
            public static boolean cocooned(LivingEntity l) { return false; }
            public static int webStacks(LivingEntity l) { return 0; }
         }
         """);
      m.put(P + "heroes/spider/SpiderWeb.java", """
         package dev.falbiks.heroes.heroes.spider;
         public class SpiderWeb {
            public int kind;
            public int kind() { return this.kind; }
         }
         """);
      m.put(P + "net/FHNetwork.java", """
         package dev.falbiks.heroes.net;
         public final class FHNetwork { }
         """);
      m.put(P + "net/AbilityInputPacket.java", """
         package dev.falbiks.heroes.net;
         public record AbilityInputPacket(int slot, boolean down, int move) { }
         """);
      return m;
   }

   /** Compiles the sources into a fresh directory and returns a loader for them (parent: the test loader). */
   static URLClassLoader compile(Map<String, String> sources) throws IOException {
      Path dir = Files.createTempDirectory("falbiks-stub");
      List<String> files = new ArrayList<>();
      for (Map.Entry<String, String> e : sources.entrySet()) {
         Path f = dir.resolve("src").resolve(e.getKey());
         Files.createDirectories(f.getParent());
         Files.writeString(f, e.getValue());
         files.add(f.toString());
      }
      Path out = Files.createDirectories(dir.resolve("classes"));
      JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
      if (javac == null) {
         throw new IllegalStateException("tests need a JDK (javax.tools compiler)");
      }
      String cp = classPathOf(net.minecraft.server.level.ServerPlayer.class, net.minecraft.world.phys.Vec3.class,
         net.minecraft.resources.ResourceLocation.class, net.minecraft.world.entity.LivingEntity.class);
      List<String> args = new ArrayList<>(List.of("-proc:none", "-nowarn", "-d", out.toString(), "-cp", cp));
      args.addAll(files);
      int code = javac.run(null, null, null, args.toArray(String[]::new));
      if (code != 0) {
         throw new IllegalStateException("stub compilation failed (" + code + ")");
      }
      return new URLClassLoader(new URL[]{out.toUri().toURL()}, SpiderCompatFixtures.class.getClassLoader());
   }

   private static String classPathOf(Class<?>... types) {
      List<String> parts = new ArrayList<>();
      for (Class<?> t : types) {
         try {
            String p = Path.of(t.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            if (!parts.contains(p)) parts.add(p);
         } catch (Exception e) {
            throw new IllegalStateException("cannot locate " + t, e);
         }
      }
      String sys = System.getProperty("java.class.path", "");
      if (!sys.isEmpty()) parts.add(sys);
      return String.join(java.io.File.pathSeparator, parts);
   }
}
