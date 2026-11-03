package dev.baranhan.viltrumitecore.compat.spider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** SpiderCompat without the mod, with the 0.1.0 shapes, and with missing or changed members (compat report §4.4). */
class SpiderCompatTest {
   private static final Set<SpiderFeature> ALL = EnumSet.allOf(SpiderFeature.class);

   // ---- the mod is absent ---------------------------------------------------------------------------------------

   @Test
   void withoutTheModTheBridgeIsNeutralAndNeverThrows() {
      SpiderBridge b = SpiderCompat.bind(new URLClassLoader(new URL[0], null), "0.1.0");
      assertInstanceOf(NoSpiderBridge.class, b);
      assertTrue(b.features().isEmpty());
      assertFalse(b.diagnostics().isEmpty(), "the log must say why");
      assertTrue(b.diagnostics().get(0).contains("bound nothing"), b.diagnostics().get(0));
      assertDoesNotThrow(() -> {
         assertFalse(b.isSpider(null));
         assertTrue(b.line(null).isEmpty());
         assertFalse(b.cutLine(null));
         assertFalse(b.isWebProjectile(null));
         assertEquals(WebKind.UNKNOWN, b.webKind(null));
         assertEquals(0, b.webStacks(null));
         assertFalse(b.cocooned(null));
         assertFalse(b.tearWeb(null));
         b.releaseInputs(null);
         assertFalse(b.drive(null, SpiderAbility.SWING, true));
         assertFalse(b.drivePrimary(null, true));
         assertNull(b.wallNormal(null));
         assertNull(b.inputChannel());
      });
   }

   @Test
   void facadeWithoutTheModAnswersNeutrally() {
      SpiderCompat.use(new NoSpiderBridge("test"));
      assertFalse(SpiderCompat.present());
      for (SpiderFeature f : SpiderFeature.values()) {
         assertFalse(SpiderCompat.has(f));
      }
      assertFalse(SpiderCompat.isSpider(null));
      assertTrue(SpiderCompat.line(null).isEmpty());
      assertFalse(SpiderCompat.cutLine(null));
      assertNull(SpiderCompat.inputChannel());
      assertTrue(SpiderCompat.describe().get(0).startsWith("adapter=none"));
   }

   // ---- the studied JAR (0.1.0 shapes) ----------------------------------------------------------------------------

   @Test
   void currentShapesBindEveryFeature() throws Exception {
      try (URLClassLoader loader = SpiderCompatFixtures.compile(SpiderCompatFixtures.current())) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         FalbiksSpiderV0 v0 = assertInstanceOf(FalbiksSpiderV0.class, b);
         assertEquals(ALL, b.features(), "diagnostics: " + b.diagnostics());
         assertTrue(b.diagnostics().isEmpty(), b.diagnostics().toString());
         assertFalse(v0.cutsByFields());
         assertEquals(FalbiksSpiderV0.CHANNEL, b.inputChannel());
      }
   }

   @Test
   void currentShapesReadTheLineAndCutItThroughEndSwing() throws Exception {
      try (URLClassLoader loader = SpiderCompatFixtures.compile(SpiderCompatFixtures.current())) {
         FalbiksSpiderV0 v0 = (FalbiksSpiderV0)SpiderCompat.bind(loader, "0.1.0");
         Object state = spiderData(loader, true);
         Object data = v0.dataOf(null);
         assertTrue(v0.isSpiderData(data));
         assertNotNull(v0.spiderStateOf(data));
         SpiderLine line = v0.lineOf(state, new Vec3(0, 0, 0));
         assertNotNull(line);
         assertEquals(new Vec3(4, 70, -2), line.anchor());
         assertEquals(18.0F, line.rope());
         int before = (Integer)get(state, "revision");
         assertTrue(v0.cutState(null, state, 500L));
         assertFalse((Boolean)get(state, "swinging"));
         assertTrue((Integer)get(state, "revision") > before, "the cut must bump the sync revision");
         assertEquals(1, loader.loadClass("dev.falbiks.heroes.heroes.spider.SpiderHero").getField("endSwingCalls").getInt(null));
         assertNull(v0.lineOf(state, Vec3.ZERO), "no line once cut");
         assertFalse(v0.cutState(null, state, 501L), "cutting twice is a no-op");
      }
   }

   @Test
   void currentShapesReleaseHeldKeysAndDriveAbilities() throws Exception {
      try (URLClassLoader loader = SpiderCompatFixtures.compile(SpiderCompatFixtures.current())) {
         FalbiksSpiderV0 v0 = (FalbiksSpiderV0)SpiderCompat.bind(loader, "0.1.0");
         spiderData(loader, true);
         Object data = v0.dataOf(null);
         @SuppressWarnings("unchecked")
         Set<Object> held = (Set<Object>)data.getClass().getField("held").get(data);
         Class<?> slot = loader.loadClass("dev.falbiks.heroes.ability.AbilitySlot");
         held.add(slot.getEnumConstants()[0]);
         held.add(slot.getEnumConstants()[2]);
         v0.releaseData(null, data);
         List<?> log = (List<?>)loader.loadClass("dev.falbiks.heroes.hero.HeroManager").getField("LOG").get(null);
         assertEquals(List.of("ABILITY_1:false", "ABILITY_3:false", "PRIMARY:false"), log);
         assertTrue(held.isEmpty());
         log.clear();
         assertTrue(v0.driveRaw(null, SpiderAbility.NET, true));
         assertTrue(v0.driveRaw(null, SpiderAbility.FLIP, false));
         assertEquals(List.of("ABILITY_2:true", "SUMMON:false"), log);
      }
   }

   @Test
   void currentShapesReadTheWebKind() throws Exception {
      try (URLClassLoader loader = SpiderCompatFixtures.compile(SpiderCompatFixtures.current())) {
         FalbiksSpiderV0 v0 = (FalbiksSpiderV0)SpiderCompat.bind(loader, "0.1.0");
         Object web = loader.loadClass("dev.falbiks.heroes.heroes.spider.SpiderWeb").getConstructor().newInstance();
         web.getClass().getField("kind").setInt(web, 1);
         assertEquals(WebKind.NET, WebKind.of(v0.kindOf(web)));
         web.getClass().getField("kind").setInt(web, 0);
         assertEquals(WebKind.PELLET, WebKind.of(v0.kindOf(web)));
         assertEquals(WebKind.UNKNOWN, WebKind.of(7));
         assertEquals(List.of("falbiksheroes_spider_web", "falbiksheroes_spider_cocoon"), List.of(v0.tags()));
      }
   }

   @Test
   void anUnknownNewerVersionStillBindsWhatMatches() throws Exception {
      try (URLClassLoader loader = SpiderCompatFixtures.compile(SpiderCompatFixtures.current())) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.4.2");
         assertEquals(ALL, b.features());
      }
   }

   // ---- simulated missing or changed members ----------------------------------------------------------------------

   @Test
   void withoutEndSwingTheCutFallsBackToFieldWrites() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.compute(SpiderCompatFixtures.P + "heroes/spider/SpiderHero.java", (k, v) -> v.replace("static void endSwing(", "static void endSwingRenamed("));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         FalbiksSpiderV0 v0 = (FalbiksSpiderV0)SpiderCompat.bind(loader, "0.1.0");
         assertTrue(v0.features().contains(SpiderFeature.CUT_LINE));
         assertTrue(v0.cutsByFields());
         assertTrue(v0.diagnostics().stream().anyMatch(d -> d.contains("endSwing") && d.contains("missing")), v0.diagnostics().toString());
         Object state = spiderData(loader, true);
         set(state, "clock", 100L);
         int before = (Integer)get(state, "revision");
         assertTrue(v0.cutState(null, state, 900L));
         assertFalse((Boolean)get(state, "swinging"));
         assertEquals(900L, get(state, "swingGame"));
         assertEquals(160L, get(state, "noFallUntil"));
         assertTrue((Integer)get(state, "revision") >= before + 2, "cooldown + explicit bump");
      }
   }

   @Test
   void aChangedFieldTypeSwitchesOffOnlyTheLine() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.compute(SpiderCompatFixtures.P + "heroes/spider/SpiderState.java", (k, v) -> v.replace("public float rope;", "public double rope;"));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         assertFalse(b.features().contains(SpiderFeature.LINE_STATE));
         assertFalse(b.features().contains(SpiderFeature.CUT_LINE), "cutting needs the line");
         assertTrue(b.features().contains(SpiderFeature.DETECT_HERO));
         assertTrue(b.features().contains(SpiderFeature.DRIVE_ABILITIES));
         assertTrue(b.features().contains(SpiderFeature.WEB_PROJECTILE));
         assertTrue(b.diagnostics().stream().anyMatch(d -> d.contains("rope") && d.contains("expected float")), b.diagnostics().toString());
      }
   }

   @Test
   void withoutHandleInputPossessionFallsBackToBaseMode() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.compute(SpiderCompatFixtures.P + "hero/HeroManager.java", (k, v) -> v.replace("public static void handleInput(", "public static void onInput("));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         assertFalse(b.features().contains(SpiderFeature.DRIVE_ABILITIES));
         assertFalse(b.features().contains(SpiderFeature.INPUT_RELEASE));
         assertTrue(b.features().contains(SpiderFeature.CUT_LINE));
         assertTrue(b.diagnostics().stream().anyMatch(d -> d.contains("handleInput(ServerPlayer,AbilitySlot,boolean) missing")), b.diagnostics().toString());
      }
   }

   @Test
   void aRenamedSlotDisablesDrivingButNotReleasing() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.compute(SpiderCompatFixtures.P + "ability/AbilitySlot.java", (k, v) -> v.replace("SUMMON", "MOBILITY"));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         assertFalse(b.features().contains(SpiderFeature.DRIVE_ABILITIES));
         assertTrue(b.features().contains(SpiderFeature.INPUT_RELEASE));
         assertTrue(b.diagnostics().stream().anyMatch(d -> d.contains("AbilitySlot.SUMMON missing")));
      }
   }

   @Test
   void aStaticMethodThatBecameAnInstanceMethodIsRefused() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.compute(SpiderCompatFixtures.P + "heroes/spider/SpiderHero.java", (k, v) -> v.replace("public static Vec3 wallNormal(", "public Vec3 wallNormal("));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         assertFalse(b.features().contains(SpiderFeature.WALL_STATE));
         assertTrue(b.diagnostics().stream().anyMatch(d -> d.contains("wallNormal") && d.contains("no longer static")));
      }
   }

   @Test
   void withoutTheHeroCapabilityNothingHeroRelatedBinds() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.remove(SpiderCompatFixtures.P + "hero/HeroData.java");
      src.compute(SpiderCompatFixtures.P + "hero/HeroManager.java", (k, v) -> v.replace(
         "if (!down && HeroData.INSTANCE != null) HeroData.INSTANCE.held.remove(slot);", ""));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         SpiderBridge b = SpiderCompat.bind(loader, "0.1.0");
         assertEquals(EnumSet.of(SpiderFeature.WEB_PROJECTILE, SpiderFeature.WEB_STATUS, SpiderFeature.TEAR_WEB, SpiderFeature.INPUT_GATE,
            SpiderFeature.WALL_STATE), b.features());
         assertTrue(b.diagnostics().stream().anyMatch(d -> d.contains("class dev.falbiks.heroes.hero.HeroData missing")));
      }
   }

   @Test
   void aThrowingCallSwitchesItsFeatureOffOnce() throws Exception {
      Map<String, String> src = SpiderCompatFixtures.current();
      src.compute(SpiderCompatFixtures.P + "hero/HeroManager.java", (k, v) -> v.replace("LOG.add(slot + \":\" + down);",
         "LOG.add(slot + \":\" + down); if (down) throw new IllegalStateException(\"boom\");"));
      try (URLClassLoader loader = SpiderCompatFixtures.compile(src)) {
         FalbiksSpiderV0 v0 = (FalbiksSpiderV0)SpiderCompat.bind(loader, "0.1.0");
         spiderData(loader, true);
         try {
            v0.driveRaw(null, SpiderAbility.SWING, true);
         } catch (java.lang.reflect.InvocationTargetException e) {
            v0.fail(SpiderFeature.DRIVE_ABILITIES, e);
         }
         assertFalse(v0.features().contains(SpiderFeature.DRIVE_ABILITIES));
         assertTrue(v0.features().contains(SpiderFeature.CUT_LINE));
         long lines = v0.diagnostics().stream().filter(d -> d.startsWith("DRIVE_ABILITIES")).count();
         v0.fail(SpiderFeature.DRIVE_ABILITIES, new RuntimeException("again"));
         assertEquals(lines, v0.diagnostics().stream().filter(d -> d.startsWith("DRIVE_ABILITIES")).count(), "no spam");
         assertTrue(v0.diagnostics().stream().anyMatch(d -> d.contains("boom")));
      }
   }

   // ---- helpers ---------------------------------------------------------------------------------------------

   /** Puts a Spider (swinging toward 4,70,-2 on an 18-block rope) into the stub HeroData.INSTANCE; returns the state. */
   static Object spiderData(ClassLoader loader, boolean swinging) throws Exception {
      Class<?> dataClass = loader.loadClass("dev.falbiks.heroes.hero.HeroData");
      Object data = dataClass.getConstructor().newInstance();
      dataClass.getField("id").set(data, new ResourceLocation("falbiksheroes", "spider"));
      Object state = loader.loadClass("dev.falbiks.heroes.heroes.spider.SpiderState").getConstructor().newInstance();
      set(state, "swinging", swinging);
      set(state, "ax", 4.0);
      set(state, "ay", 70.0);
      set(state, "az", -2.0);
      set(state, "rope", 18.0F);
      dataClass.getField("state").set(data, state);
      dataClass.getField("INSTANCE").set(null, data);
      return state;
   }

   static Object get(Object o, String name) throws Exception {
      return o.getClass().getField(name).get(o);
   }

   static void set(Object o, String name, Object value) throws Exception {
      Field f = o.getClass().getField(name);
      f.set(o, value);
   }
}
