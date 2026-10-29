package dev.baranhan.viltrumitecore.client.dio.qa;

import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in client QA script ({@code -Dviltrumitecore.dio.qa.script=true}, together
 * with {@code -Dviltrumitecore.dio.qa=true} for the server commands): creates a
 * flat world, becomes DIO, runs every ability through {@code /dioqa act} and
 * saves screenshots (front / side / back cameras, F5 and first person) into
 * {@code run/screenshots/dioqa_*.png}, then quits. Absent in a normal game.
 */
@EventBusSubscriber(modid = "viltrumitecore", value = Dist.CLIENT)
public final class DioQaScript {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.dio.qa.script");
   private static final Logger LOG = LoggerFactory.getLogger("DIOQA");
   private static final int SETTLE = 120;
   private static final List<Step> STEPS = new ArrayList<>();
   private static final List<String> PENDING = new ArrayList<>();
   private static boolean worldRequested;
   private static int inWorld = -1;
   private static int cursor;
   private static Vec3 origin;

   private record Step(int tick, Consumer<Minecraft> run) {
   }

   private DioQaScript() {
   }

   private static void at(int tick, Consumer<Minecraft> run) {
      STEPS.add(new Step(tick, run));
   }

   private static void cmd(int tick, String command) {
      at(tick, mc -> {
         LOG.info("DIOQA t={} /{}", tick, command);
         mc.player.connection.sendCommand(command);
      });
   }

   /** Camera from the player itself. */
   private static void view(int tick, CameraType type) {
      at(tick, mc -> {
         mc.setCameraEntity(mc.player);
         mc.options.setCameraType(type);
         mc.gameRenderer.setRenderHand(true);
      });
   }

   /** Camera from an invisible armor stand summoned at an offset from the start point. */
   private static void cam(int tick, double dx, double dz) {
      at(tick, mc -> {
         // Horizontal match: the stands may be summoned above the ground.
         Vec3 want = origin.add(dx, 0.0, dz);
         Entity best = null;
         for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand && flat(e.position(), want) < 2.0 && (best == null || flat(e.position(), want) < flat(best.position(), want))) {
               best = e;
            }
         }

         if (best == null) {
            LOG.warn("DIOQA no camera at {}", want);
            return;
         }

         mc.options.setCameraType(CameraType.FIRST_PERSON);
         mc.setCameraEntity(best);
         mc.gameRenderer.setRenderHand(false);
      });
   }

   private static double flat(Vec3 a, Vec3 b) {
      return (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z);
   }

   private static void shot(int tick, String name) {
      at(tick, mc -> {
         mc.gui.getChat().clearMessages(false);
         var snapshot = dev.baranhan.viltrumitecore.client.dio.DioView.of(mc.player);
         LOG.info("DIOQA state {} action={} elapsed={} screen={}", name, snapshot == null ? null : dev.baranhan.viltrumitecore.client.dio.DioView.action(snapshot),
            snapshot == null ? -1 : snapshot.actionElapsed(), java.util.Arrays.toString(dev.baranhan.viltrumitecore.client.dio.TimeStopClient.screen(0.0F)));
         PENDING.add(name);
      });
   }

   /** Shots are grabbed right after the next frame, so the image shows the state of the tick that asked for it. */
   @SubscribeEvent
   public static void frame(TickEvent.RenderTickEvent event) {
      if (!ENABLED || event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      for (String name : PENDING) {
         Screenshot.grab(mc.gameDirectory, "dioqa_" + name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("DIOQA shot {} -> {}", name, msg.getString()));
      }

      PENDING.clear();
   }

   private static void script() {
      // Setup: night + night vision (DIO burns in the sun), no spawns, survival.
      cmd(0, "gamerule doDaylightCycle false");
      cmd(1, "gamerule doMobSpawning false");
      cmd(2, "time set 18000");
      cmd(3, "weather clear");
      cmd(4, "dioqa become");
      cmd(5, "effect give @s night_vision infinite 0 true");
      cmd(6, "gamemode survival");
      at(8, mc -> mc.setScreen(null));
      // Camera stands (yaw 0 = +Z): front, side, back, wide combat side, roller wide.
      String stand = " {Invisible:1b,NoGravity:1b,Invulnerable:1b,Rotation:[%sf,0f]}";
      cmd(12, "summon armor_stand ~ ~ ~5" + stand.formatted("180"));
      cmd(13, "summon armor_stand ~5 ~ ~1" + stand.formatted("90"));
      cmd(14, "summon armor_stand ~ ~ ~-5" + stand.formatted("0"));
      cmd(15, "summon armor_stand ~7 ~ ~3" + stand.formatted("90"));
      cmd(18, "summon armor_stand ~3 ~ ~3" + stand.formatted("90"));
      cmd(16, "summon armor_stand ~-12 ~3 ~2" + stand.formatted("-90"));
      at(17, mc -> mc.setScreen(null));

      // Stand summon + idle from every side.
      view(25, CameraType.THIRD_PERSON_BACK);
      cmd(30, "dioqa act the_world");
      shot(34, "summon_f5back");
      shot(40, "summon_f5back_late");
      cam(60, 0, 5);
      shot(66, "idle_front");
      cam(75, 5, 1);
      shot(81, "idle_side");
      cam(90, 0, -5);
      shot(96, "idle_back");
      view(105, CameraType.THIRD_PERSON_FRONT);
      shot(111, "idle_f5front");
      view(120, CameraType.FIRST_PERSON);
      shot(126, "idle_fp");

      // Combo against a still zombie: wide side camera, then first person.
      cmd(135, "dioqa dummy");
      cam(150, 7, 3);
      int t = 160;
      for (int i = 0; i < 3; i++) {
         cmd(t, "dioqa act primary_attack");
         shot(t + 3, "combo" + (i + 1) + "_side");
         shot(t + 5, "combo" + (i + 1) + "_side_b");
         t += DioRules.COMBO_LENGTH[i] + 1;
      }

      view(t + 10, CameraType.FIRST_PERSON);
      cmd(t + 15, "dioqa act primary_attack");
      shot(t + 18, "combo1_fp");

      // Held MUDA barrage, then the finisher on release.
      t += 40;
      cmd(t - 2, "dioqa reset");
      cam(t, 7, 3);
      cmd(t + 2, "dioqa act muda_barrage true");
      shot(t + 12, "barrage_side");
      shot(t + 16, "barrage_side_b");
      view(t + 20, CameraType.FIRST_PERSON);
      shot(t + 26, "barrage_fp");
      view(t + 30, CameraType.THIRD_PERSON_BACK);
      shot(t + 36, "barrage_f5back");
      cam(t + 40, 7, 3);
      cmd(t + 42, "dioqa act muda_barrage false");
      shot(t + 47, "finisher_side");
      shot(t + 52, "finisher_side_b");

      // Knives in flight.
      t += 75;
      cmd(t - 4, "dioqa dummy");
      cmd(t - 2, "dioqa reset");
      cmd(t, "dioqa act knife_throw");
      shot(t + DioRules.KNIFE_WINDUP + 2, "knives_side");
      shot(t + DioRules.KNIFE_WINDUP + 4, "knives_side_b");

      // ZA WARUDO: cast inversion, stopped grey, knives hanging, a ledgered punch, early resume.
      t += 40;
      cmd(t - 3, "dioqa reset");
      cmd(t - 2, "dioqa blood 100");
      view(t - 1, CameraType.THIRD_PERSON_BACK);
      cmd(t, "dioqa act time_stop");
      for (int c = 2; c <= 20; c += 2) {
         shot(t + c, "timestop_cast_" + (c < 10 ? "0" : "") + c);
      }
      shot(t + 30, "timestop_grey_f5");
      cam(t + 34, 7, 3);
      shot(t + 38, "timestop_grey_side");
      cmd(t + 40, "dioqa reset");
      cmd(t + 41, "dioqa act knife_throw");
      shot(t + 41 + DioRules.KNIFE_WINDUP + 10, "timestop_knives_side");
      cam(t + 60, 3, 3);
      shot(t + 64, "timestop_knives_close");
      cam(t + 66, 7, 3);
      cmd(t + 70, "dioqa act primary_attack");
      shot(t + 74, "timestop_punch_side");
      view(t + 80, CameraType.FIRST_PERSON);
      shot(t + 84, "timestop_fp");
      cmd(t + 90, "dioqa act time_stop");
      cam(t + 91, 7, 3);
      shot(t + 93, "resume_side");
      shot(t + 98, "resume_side_b");
      shot(t + 106, "resume_side_c");

      // Vampire drain (stand away).
      t += 140;
      cmd(t - 10, "dioqa act the_world");
      cmd(t - 4, "kill @e[type=zombie]");
      cmd(t - 2, "dioqa dummy 2.2");
      cmd(t - 1, "dioqa reset");
      cmd(t + 4, "dioqa act vampire_drain");
      cam(t + 6, 3, 3);
      shot(t + 12, "drain_close");
      cam(t + 14, 5, 1);
      shot(t + 18, "drain_side");
      view(t + 20, CameraType.FIRST_PERSON);
      shot(t + 26, "drain_fp");
      view(t + 30, CameraType.THIRD_PERSON_BACK);
      shot(t + 36, "drain_f5back");

      // Guard.
      t += 60;
      cmd(t, "dioqa act the_world");
      cam(t + 2, 5, 1);
      cmd(t + 16, "dioqa act guard true");
      shot(t + 24, "guard_side");
      cmd(t + 30, "dioqa act guard false");

      // ROAD ROLLER DA.
      t += 45;
      cmd(t - 4, "dioqa reset");
      cmd(t - 3, "dioqa blood 100");
      cmd(t - 2, "dioqa dummy");
      cam(t - 1, -12, 2);
      cmd(t, "dioqa act road_roller");
      int[] marks = {6, 14, 22, 30, 38, 46, 56, 70, 84, 98, 106, 112, 118};
      for (int m : marks) {
         shot(t + m, "roller_" + (m < 10 ? "0" : "") + m);
      }

      view(t + 60, CameraType.THIRD_PERSON_BACK);
      shot(t + 64, "roller_f5back");
      cam(t + 66, -12, 2);

      // Sun weakness: daylight smoke on DIO.
      t += 150;
      cmd(t, "time set 6000");
      view(t + 2, CameraType.THIRD_PERSON_FRONT);
      shot(t + 40, "sun_f5front");
      cmd(t + 44, "dioqa state");
      at(t + 60, mc -> {
         LOG.info("DIOQA done");
         mc.stop();
      });
      STEPS.sort(Comparator.comparingInt(Step::tick));
   }

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent event) {
      if (!ENABLED || event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         if (mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.setScreen(new TitleScreen(true));
         } else if (mc.screen instanceof TitleScreen && !worldRequested) {
            worldRequested = true;
            createWorld(mc);
         }

         return;
      }

      inWorld++;
      if (inWorld < SETTLE) {
         return;
      }

      if (STEPS.isEmpty()) {
         origin = mc.player.position();
         script();
      }

      int now = inWorld - SETTLE;
      while (cursor < STEPS.size() && STEPS.get(cursor).tick() <= now) {
         try {
            STEPS.get(cursor).run().accept(mc);
         } catch (RuntimeException e) {
            LOG.error("DIOQA step {} failed", STEPS.get(cursor).tick(), e);
         }

         cursor++;
      }
   }

   private static void createWorld(Minecraft mc) {
      String name = "dioqa-" + System.currentTimeMillis();
      LOG.info("DIOQA creating world {}", name);
      LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(), WorldDataConfiguration.DEFAULT);
      mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261028L, false, false),
         access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
   }
}
