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

   /** Client look + lock state, for the camera-lock checks (compare the yaw between two calls). */
   private static void look(int tick, String label) {
      at(tick, mc -> LOG.info("DIOQA look {} id={} yaw={} pitch={} clientFrozen={} locked={} alive={} level={}", label, mc.player.getId(),
         String.format(java.util.Locale.ROOT, "%.2f", mc.player.getYRot()), String.format(java.util.Locale.ROOT, "%.2f", mc.player.getXRot()),
         dev.baranhan.viltrumitecore.hero.control.TimeFreeze.clientFrozen(mc.player.getId()), dev.baranhan.viltrumitecore.client.hero.TimeFreezeView.locked(),
         mc.player.isAlive(), mc.level.dimension().location()));
   }

   /**
    * Real mouse path: feeds GLFW cursor positions into MouseHandler.onMove (the GLFW
    * callback) so the move goes through accumulation and turnPlayer like a hand on the mouse.
    */
   private static double cursorX = 400.0;

   private static void mouse(int tick, double dx) {
      at(tick, mc -> {
         try {
            if (!mc.mouseHandler.isMouseGrabbed()) {
               mc.mouseHandler.grabMouse();
            }

            java.lang.reflect.Method move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            move.setAccessible(true);
            cursorX += dx;
            move.invoke(mc.mouseHandler, mc.getWindow().getWindow(), cursorX, 300.0);
         } catch (ReflectiveOperationException e) {
            LOG.error("DIOQA mouse failed", e);
         }
      });
   }

   /** {@code -Dviltrumitecore.dio.qa.polish=true}: the DIO polish checks (foreign stop, camera lock, blood, roller). */
   private static void polishScript() {
      cmd(0, "gamerule doDaylightCycle false");
      cmd(1, "gamerule doMobSpawning false");
      cmd(2, "time set 18000");
      cmd(3, "weather clear");
      cmd(5, "effect give @s night_vision infinite 0 true");
      cmd(6, "gamemode survival");
      cmd(7, "difficulty easy");
      at(8, mc -> mc.setScreen(null));
      cmd(10, "tp @s ~ ~ ~ 0 0");
      String stand = " {Invisible:1b,NoGravity:1b,Invulnerable:1b,Rotation:[%sf,10f]}";
      cmd(12, "summon armor_stand ~9 ~2 ~3" + stand.formatted("90"));
      cmd(13, "summon armor_stand ~-10 ~1 ~4 {Invisible:1b,NoGravity:1b,Invulnerable:1b,Rotation:[-90f,-28f]}");
      cmd(14, "summon armor_stand ~ ~3 ~-9" + stand.formatted("0"));
      view(20, CameraType.FIRST_PERSON);

      // 1. Control: the scripted mouse turns an unfrozen player.
      look(30, "control_before");
      for (int i = 0; i < 5; i++) {
         mouse(31 + i, 40.0);
      }
      look(38, "control_after");
      cmd(40, "tp @s ~ ~ ~ 0 0");

      // 2. Another DIO's stopped time freezes the player, a Jin-Woo shadow, a zombie, TNT, an item, sand, arrow, snowball.
      cmd(50, "dioqa shadow");
      cmd(52, "summon zombie ~8 ~ ~-4");
      cmd(54, "summon tnt ~4 ~ ~6 {Fuse:80}");
      cmd(56, "summon item ~-2 ~3 ~2 {Item:{id:\"minecraft:gold_ingot\",Count:1b},Motion:[0.0d,0.4d,0.0d]}");
      cmd(58, "summon falling_block ~2 ~10 ~4 {BlockState:{Name:\"minecraft:sand\"},Time:1}");
      cmd(59, "summon arrow ~-6 ~2 ~5 {Motion:[0.5d,0.25d,0.0d]}");
      cmd(60, "summon snowball ~-6 ~3 ~6 {Motion:[0.4d,0.3d,0.0d]}");
      cmd(61, "dioqa probe pre");
      cmd(62, "dioqa foreignstop 140");
      shot(66, "fstop_fp_start");
      look(68, "frozen_before_mouse");
      for (int i = 0; i < 6; i++) {
         mouse(69 + i, 60.0);
      }
      look(77, "frozen_after_mouse");
      at(78, mc -> mc.player.setYRot(mc.player.getYRot() + 90.0F));
      look(80, "frozen_after_setyrot");
      cmd(82, "dioqa probe during1");
      cam(84, 9, 3);
      shot(88, "fstop_side");
      cam(92, -10, 4);
      shot(96, "fstop_wide");
      cam(98, 0, -9);
      shot(102, "fstop_back");
      view(106, CameraType.FIRST_PERSON);
      cmd(122, "dioqa probe during2");
      shot(126, "fstop_fp_late");
      cmd(150, "dioqa foreignstop 0");
      cmd(153, "dioqa probe after_end");
      look(155, "released_before_mouse");
      for (int i = 0; i < 5; i++) {
         mouse(156 + i, 40.0);
      }
      look(163, "released_after_mouse");
      cam(166, 9, 3);
      shot(170, "after_side");
      cmd(175, "dioqa probe after2");
      view(178, CameraType.FIRST_PERSON);

      // 3. Death while frozen: the lock must not survive the respawn.
      cmd(200, "kill @e[type=!player,type=!armor_stand]");
      cmd(204, "tp @s ~ ~ ~ 0 0");
      cmd(208, "dioqa foreignstop 300");
      look(214, "death_frozen");
      cmd(220, "kill @s");
      cmd(223, "dioqa probe after_kill");
      at(228, mc -> LOG.info("DIOQA screen after kill: {} dead={}", mc.screen == null ? null : mc.screen.getClass().getSimpleName(), mc.player.isDeadOrDying()));
      at(232, mc -> mc.player.respawn());
      at(240, mc -> mc.setScreen(null));
      cmd(242, "effect give @s night_vision infinite 0 true");
      look(246, "death_respawned");
      cmd(247, "dioqa probe respawned");
      for (int i = 0; i < 5; i++) {
         mouse(248 + i, 40.0);
      }
      look(256, "death_after_mouse");
      cmd(258, "dioqa foreignstop 0");

      // 4. Dimension change while frozen (The End spawn platform, slow falling).
      cmd(270, "effect give @s slow_falling 60 0 true");
      cmd(271, "effect give @s resistance 60 4 true");
      cmd(272, "tp @s ~ ~ ~ 0 0");
      cmd(274, "dioqa foreignstop 300");
      look(280, "dim_frozen");
      cmd(284, "execute in minecraft:the_end run tp @s 100 52 0");
      at(296, mc -> mc.setScreen(null));
      look(300, "dim_moved");
      for (int i = 0; i < 5; i++) {
         mouse(301 + i, 40.0);
      }
      look(308, "dim_after_mouse");
      cmd(310, "dioqa foreignstop 0");
      cmd(312, String.format(java.util.Locale.ROOT, "execute in minecraft:overworld run tp @s %.2f %.2f %.2f 0 0", origin.x, origin.y, origin.z));
      at(326, mc -> mc.setScreen(null));

      // 5. Blood debug commands (become DIO first).
      cmd(390, "dioqa become");
      cmd(432, "dioqa blood 7");
      cmd(434, "dioqa state");
      cmd(436, "dio blood fill");
      cmd(438, "dioqa state");
      cmd(440, "dio blood infinite on");
      view(442, CameraType.THIRD_PERSON_BACK);
      shot(450, "hud_infinite");
      cmd(453, "summon zombie ~8 ~ ~-4");
      cmd(454, "summon arrow ~-6 ~2 ~5 {Motion:[0.5d,0.25d,0.0d]}");
      cmd(455, "dioqa act time_stop");
      cmd(462, "dioqa state");
      cmd(482, "dioqa probe dio_ts1");
      shot(484, "dio_ts_f5");
      cmd(502, "dioqa probe dio_ts2");
      cmd(503, "dioqa state");
      cam(504, 9, 3);
      shot(508, "dio_ts_side");
      view(512, CameraType.THIRD_PERSON_BACK);
      cmd(690, "dioqa probe dio_ts_after");
      cmd(692, "dioqa state");
      cmd(694, "dio blood infinite off");
      cmd(696, "dioqa state");
      shot(700, "hud_normal");

      // 6. ROAD ROLLER: the local cinematic camera (first person view handed to RollerCinema).
      int r = 760;
      cmd(r - 12, "kill @e[type=!player,type=!armor_stand]");
      cmd(r - 8, "dioqa reset");
      cmd(r - 7, "dio blood fill");
      cmd(r - 6, "tp @s ~ ~ ~ 0 0");
      cmd(r - 4, "dioqa dummy 8");
      view(r - 2, CameraType.FIRST_PERSON);
      cmd(r, "dioqa act road_roller");
      int[] cine = {4, 12, 20, 26, 32, 40, 48, 56, 64, 72, 80, 90, 98, 110, 130, 150, 158, 164, 170, 176, 184};
      for (int m : cine) {
         shot(r + m, "roller_cine_" + (m < 10 ? "00" : m < 100 ? "0" : "") + m);
      }
      cmd(r + 100, "dioqa state");

      // 7. Second ROAD ROLLER from a fixed wide camera: the leap and the fall seen from outside.
      int q = r + 260;
      cmd(q - 12, "kill @e[type=zombie]");
      cmd(q - 8, "dioqa reset");
      cmd(q - 7, "dio blood fill");
      cmd(q - 4, "dioqa dummy 8");
      cam(q - 2, -10, 4);
      cmd(q, "dioqa act road_roller");
      int[] wide = {16, 26, 32, 40, 50, 60, 70, 84, 96, 120, 160, 176};
      for (int m : wide) {
         shot(q + m, "roller_wide_" + (m < 10 ? "00" : m < 100 ? "0" : "") + m);
      }

      at(q + 200, mc -> {
         LOG.info("DIOQA done");
         mc.stop();
      });
      STEPS.sort(Comparator.comparingInt(Step::tick));
   }

   private static void script() {
      if (Boolean.getBoolean("viltrumitecore.dio.qa.polish")) {
         polishScript();
         return;
      }

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
