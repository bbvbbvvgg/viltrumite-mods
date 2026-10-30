package dev.baranhan.viltrumitecore.client.sinclair.qa;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.gui.RaceSelectionScreen;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.client.sinclair.SinclairView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.act.ActShape;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

/**
 * Opt-in scripted playtest (plan step 10): {@code -Dviltrumitecore.sinclair.playtest=true} only; nothing is
 * registered otherwise. It creates a flat world, becomes Sinclair and drives every move through the real input
 * path (vanilla key mappings → the mod's packets → server abilities), taking screenshots into
 * {@code run/screenshots/sinclair_*.png}. Only the act shape of a capture is forced (a mob always yields the
 * spare act) so that all six acts can be filmed; everything else is production code.
 */
public final class SinclairPlaytest {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.sinclair.playtest");
   private static final boolean ULT_ONLY = Boolean.getBoolean("viltrumitecore.sinclair.playtest.ultOnly");
   private static final Logger LOG = LogUtils.getLogger();
   private static final String WORLD = "sinclair_playtest";
   private static final List<Step> STEPS = new ArrayList<>();
   private static int stepIndex;
   private static int wait;
   private static int ticks;
   private static boolean worldRequested;
   private static boolean camWanted;
   private static String pendingShot;
   private static int shotDelayFrames;

   private record Step(int delay, String label, Runnable action, @javax.annotation.Nullable java.util.function.BooleanSupplier ready) {
      Step(int delay, String label, Runnable action) {
         this(delay, label, action, null);
      }
   }

   private SinclairPlaytest() {
   }

   public static void init() {
      if (!ENABLED) {
         return;
      }

      script();
      MinecraftForge.EVENT_BUS.addListener(SinclairPlaytest::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(SinclairPlaytest::onRenderTick);
      MinecraftForge.EVENT_BUS.addListener(SinclairPlaytest::onPlaySound);
      LOG.info("SINCLAIR_PT enabled, {} steps", STEPS.size());
   }

   // ---------------------------------------------------------------- script

   private static void at(int delay, String label, Runnable action) {
      STEPS.add(new Step(delay, label, action));
   }

   private static void shot(int delay, String name) {
      at(delay, "shot " + name, () -> {
         pendingShot = name;
         shotDelayFrames = 2;
      });
   }

   private static void cmd(String command) {
      server(player -> player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command));
   }

   private static void server(Consumer<ServerPlayer> action) {
      Minecraft mc = Minecraft.getInstance();
      MinecraftServer server = mc.getSingleplayerServer();
      if (server == null || mc.player == null) {
         return;
      }

      java.util.UUID id = mc.player.getUUID();
      server.execute(() -> {
         ServerPlayer player = server.getPlayerList().getPlayer(id);
         if (player != null) {
            action.accept(player);
         }
      });
   }

   private static void resetCooldowns() {
      server(player -> {
         SinclairState state = SinclairState.ensure(player);
         state.boltCooldown = 0;
         state.assistCooldown = 0;
         state.hexCooldown = 0;
         state.misCooldown = 0;
         state.ultCooldown = 0;
         state.magazine = 12;
         state.reload = 0;
         player.setHealth(player.getMaxHealth());
      });
   }

   private static void camera(CameraType type) {
      Minecraft.getInstance().options.setCameraType(type);
   }

   /** A real key press: held for three ticks (hero keys are edge-tracked on isDown, not click counts). */
   private static void press(KeyMapping key) {
      KeyMapping.set(key.getKey(), true);
      KeyMapping.click(key.getKey());
      RELEASES.add(new Release(key, ticks + 3));
   }

   private record Release(KeyMapping key, int at) {
   }

   private static final List<Release> RELEASES = new ArrayList<>();

   private static void hold(KeyMapping key, boolean down) {
      KeyMapping.set(key.getKey(), down);
   }

   /** Stand at the stage centre facing south (+z) with the given pitch. */
   private static void stage(float pitch) {
      cmd("tp @s 0.5 -60 0.5 0 " + pitch);
   }

   private static void volunteer(double z) {
      cmd("kill @e[type=minecraft:husk]");
      cmd("summon minecraft:husk 0.5 -60 " + z + " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f],"
         + "Attributes:[{Name:\"generic.max_health\",Base:1000d}],Health:1000f}");
   }

   /** Cinematic camera: an invisible marker stand at (x,y,z) looking at (tx,ty,tz); the client looks through it. */
   private static void cam(double x, double y, double z, double tx, double ty, double tz) {
      double dx = tx - x;
      double dy = ty - y;
      double dz = tz - z;
      float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
      float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
      cmd("kill @e[type=minecraft:armor_stand]");
      cmd(String.format(java.util.Locale.ROOT, "summon minecraft:armor_stand %.2f %.2f %.2f {CustomName:'\"pt_cam\"',Invisible:1b,Marker:1b,NoGravity:1b,Rotation:[%.1ff,%.1ff]}",
         x, y, z, yaw, pitch));
      camWanted = true;
      camera(CameraType.FIRST_PERSON);
   }

   /** Frames the hexed mob nearest to the player from 2.6 blocks in front of it (hexed husks keep walking). */
   private static void camOnHexed() {
      Minecraft mc = Minecraft.getInstance();
      net.minecraft.world.entity.Entity best = null;
      for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
         if (e != mc.player && dev.baranhan.viltrumitecore.hero.sinclair.HexMarks.markOf(e) != null
            && (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
            best = e;
         }
      }
      if (best == null) {
         LOG.info("SINCLAIR_PT no hexed entity found for the close-up");
         return;
      }
      double x = best.getX();
      double z = best.getZ();
      double dx = mc.player.getX() - x;
      double dz = mc.player.getZ() - z;
      double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
      cam(x + dx / len * 2.6 + 0.8, best.getY() + 0.9, z + dz / len * 2.6, x, best.getY() + 0.25, z);
   }

   private static void playerCam() {
      camWanted = false;
      Minecraft.getInstance().setCameraEntity(Minecraft.getInstance().player);
   }

   /** Sounds the engine was asked to play, by event id (audio check of the report). */
   private static final java.util.Map<String, Integer> SOUNDS = new java.util.TreeMap<>();

   private static void onPlaySound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
      if (event.getSound() != null) {
         SOUNDS.merge(event.getSound().getLocation().toString(), 1, Integer::sum);
      }
   }

   private static void killMobs() {
      // Everything but the player and the camera stand: pass 4 found a stray slime that killed the
      // performer mid-FINALE (superflat slime chunk).
      cmd("kill @e[type=!minecraft:player,type=!minecraft:armor_stand]");
   }

   /** Logs what is near the viewer, to name any unexpected shape in a shot. */
   private static void dumpNear() {
      Minecraft mc = Minecraft.getInstance();
      for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
         if (e != mc.player && e.distanceTo(mc.player) < 10.0F) {
            LOG.info("SINCLAIR_PT near {} at {}", net.minecraft.world.entity.EntityType.getKey(e.getType()), e.position());
         }
      }
   }

   private static void script() {
      at(0, "setup", () -> {
         cmd("gamerule doDaylightCycle false");
         cmd("gamerule doWeatherCycle false");
         cmd("gamerule doMobSpawning false");
         cmd("gamerule doMobLoot false");
         cmd("effect give @s minecraft:resistance infinite 4 true");
         cmd("gamerule sendCommandFeedback false");
         cmd("time set 6000");
         cmd("weather clear");
         cmd("difficulty normal");
         cmd("gamemode survival");
         cmd("fill -12 -61 -6 12 -61 30 minecraft:polished_andesite");
         cmd("fill -12 -60 31 12 -56 31 minecraft:dark_oak_planks");
         server(player -> HeroRegistry.changeHero(player, HeroId.SINCLAIR));
         stage(0.0F);
      });
      int skipFrom = STEPS.size();
      at(40, "race screen", () -> Minecraft.getInstance().setScreen(new RaceSelectionScreen()));
      shot(20, "sinclair_01_race_selection");
      at(10, "close", () -> Minecraft.getInstance().setScreen(null));

      at(10, "front", () -> camera(CameraType.THIRD_PERSON_FRONT));
      shot(30, "sinclair_02_idle_front");
      at(2, "close cam", () -> cam(2.2, -58.6, 2.6, 0.5, -59.1, 0.5));
      shot(12, "sinclair_03_idle_closeup");
      at(2, "back", () -> {
         playerCam();
         camera(CameraType.THIRD_PERSON_BACK);
      });
      shot(10, "sinclair_04_idle_back");
      at(5, "walk", () -> {
         cam(-4.0, -58.8, 5.0, 0.5, -59.2, 4.0);
         hold(Minecraft.getInstance().options.keyUp, true);
      });
      shot(14, "sinclair_05_walk_side");
      at(4, "sprint", () -> hold(Minecraft.getInstance().options.keySprint, true));
      shot(10, "sinclair_06_run_side");
      at(2, "stop", () -> {
         hold(Minecraft.getInstance().options.keyUp, false);
         hold(Minecraft.getInstance().options.keySprint, false);
         playerCam();
         stage(0.0F);
      });

      // Prestige (§5.0): first person, hold LMB, reload.
      at(10, "fp", () -> camera(CameraType.FIRST_PERSON));
      shot(10, "sinclair_07_fp_idle");
      at(2, "fire", () -> hold(Minecraft.getInstance().options.keyAttack, true));
      shot(3, "sinclair_08_fp_fire");
      at(4, "side fire", () -> cam(-3.0, -58.6, 3.0, 0.5, -58.8, 2.5));
      shot(4, "sinclair_09_fire_side");
      at(1, "magazine 1", () -> {
         playerCam();
         server(player -> SinclairState.ensure(player).magazine = 1);
      });
      at(14, "fp reload", () -> camera(CameraType.FIRST_PERSON));
      shot(10, "sinclair_10_fp_reload");
      at(2, "release", () -> hold(Minecraft.getInstance().options.keyAttack, false));

      // Vexing Bolt (§5.1).
      at(30, "bolt", () -> {
         resetCooldowns();
         press(AbilityInputManager.abilityKey1);
      });
      shot(4, "sinclair_11_fp_bolt_windup");
      at(2, "side", () -> cam(-3.5, -58.4, 4.0, 0.5, -58.6, 3.0));
      shot(12, "sinclair_12_bolt_flight");
      at(2, "redirect", () -> press(AbilityInputManager.abilityKey1));
      shot(4, "sinclair_13_bolt_snap");

      // Spectral Assistant (§5.2/§5.3): aimed right-front so she stands clear of Sinclair.
      at(30, "summon", () -> {
         resetCooldowns();
         playerCam();
         cmd("tp @s 0.5 -60 0.5 -35 28");
         cam(-3.5, -58.6, 6.0, 1.2, -59.0, 2.0);
      });
      at(6, "Y", () -> press(AbilityInputManager.abilityKey2));
      shot(6, "sinclair_14_savannah_summon_gesture");
      shot(8, "sinclair_15_savannah_appear");
      at(2, "aim ahead", () -> cmd("tp @s 0.5 -60 0.5 0 2"));
      shot(30, "sinclair_16_savannah_idle");
      at(2, "fire with her", () -> hold(Minecraft.getInstance().options.keyAttack, true));
      shot(4, "sinclair_17_savannah_attack");
      at(4, "release", () -> hold(Minecraft.getInstance().options.keyAttack, false));
      at(10, "bolt with her", () -> {
         resetCooldowns();
         press(AbilityInputManager.abilityKey1);
      });
      shot(7, "sinclair_18_savannah_cast");
      at(30, "swap", () -> press(AbilityInputManager.abilityKey2));
      shot(2, "sinclair_19_swap_card");
      shot(20, "sinclair_20_after_swap");
      at(2, "hurt her", () -> cmd("damage @e[type=viltrumitecore:sinclair_assistant,limit=1] 1 minecraft:generic"));
      shot(2, "sinclair_21_savannah_hurt");
      at(2, "kill her", () -> cmd("kill @e[type=viltrumitecore:sinclair_assistant]"));
      shot(6, "sinclair_22_savannah_death");
      at(30, "summon again", () -> {
         resetCooldowns();
         cmd("tp @s 0.5 -60 0.5 -35 28");
      });
      at(4, "Y", () -> press(AbilityInputManager.abilityKey2));
      at(40, "let her go", () -> server(player -> {
         net.minecraft.world.entity.Entity e = player.level().getEntity(SinclairState.ensure(player).assistantId);
         if (e instanceof dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity assistant) {
            assistant.leave(); // the same call as the 10 s expiry
         }
      }));
      shot(8, "sinclair_23_savannah_leave");

      // Rabbit Hex (§5.4) on three husks and a pig 8 blocks ahead.
      at(40, "hex setup", () -> {
         resetCooldowns();
         cmd("kill @e[type=viltrumitecore:sinclair_assistant]");
         killMobs();
         for (int i = -1; i <= 1; i++) {
            cmd("summon minecraft:husk " + (0.5 + i * 1.6) + " -60 8.5 {PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}");
         }

         cmd("summon minecraft:pig 0.5 -60 10 {PersistenceRequired:1b}");
         stage(30.0F);
         cam(-6.0, -57.6, 3.5, 0.5, -59.5, 7.0);
      });
      at(20, "Z", () -> press(AbilityInputManager.abilityKey3));
      shot(5, "sinclair_24_hex_cast");
      shot(10, "sinclair_25_hex_telegraph");
      shot(10, "sinclair_26_hex_poof");
      at(1, "close", SinclairPlaytest::camOnHexed);
      shot(10, "sinclair_27_hex_rabbits_close");
      at(6, "close again", SinclairPlaytest::camOnHexed);
      shot(10, "sinclair_28_hex_rabbits_hop");

      // Misdirection (§5.5): stand still, the double runs on along the look.
      at(60, "mis setup", () -> {
         resetCooldowns();
         killMobs();
         stage(10.0F);
         cam(-6.0, -58.4, 5.0, 0.5, -59.0, 4.0);
      });
      at(10, "V", () -> press(AbilityInputManager.abilityKey4));
      shot(4, "sinclair_29_snap");
      shot(14, "sinclair_30_decoy_run_veil");
      at(1, "self view", () -> {
         playerCam();
         camera(CameraType.THIRD_PERSON_BACK);
      });
      shot(6, "sinclair_31_veil_self_view");
      at(1, "side", () -> cam(-6.0, -58.4, 8.0, 0.5, -59.0, 9.0));
      shot(26, "sinclair_32_decoy_reveal");

      if (ULT_ONLY) {
         // -Dviltrumitecore.sinclair.playtest.ultOnly=true: only the ultimate, the copy cinematic and the acts.
         STEPS.subList(skipFrom, STEPS.size()).clear();
         at(40, "close the race screen", () -> Minecraft.getInstance().setScreen(null));
      }

      // Audience Participation (§5.6) and the copy cinematic (design 2026-11-02 §2): capture, the cinematic
      // seen by the performer and by a bystander, the window, then all six reworked acts.
      at(40, "ult setup", () -> {
         resetCooldowns();
         killMobs();
         volunteer(7.5);
         stage(0.0F);
         cam(-7.0, -57.6, 4.0, 0.5, -59.0, 4.0);
      });
      at(20, "B capture", () -> press(AbilityInputManager.abilityKey5));
      shot(8, "sinclair_33_capture_spotlight");
      shot(8, "sinclair_34_capture_card");
      at(1, "copy own view", () -> {
         playerCam();
         camera(CameraType.FIRST_PERSON);
      });
      shotAt(SinclairMove.COPY, 4, "sinclair_copy_01_camera_in");
      shotAt(SinclairMove.COPY, 12, "sinclair_copy_02_unfold");
      at(0, "try to walk", () -> {
         START = Minecraft.getInstance().player.position();
         hold(Minecraft.getInstance().options.keyUp, true);
      });
      shotAt(SinclairMove.COPY, 24, "sinclair_copy_03_card_hold");
      at(0, "side", () -> cam(-5.0, -58.2, 4.0, 0.5, -59.0, 4.0));
      shotAt(SinclairMove.COPY, 34, "sinclair_copy_04_bystander");
      at(0, "own again", () -> {
         hold(Minecraft.getInstance().options.keyUp, false);
         LOG.info("SINCLAIR_PT copy freeze: moved {} blocks while W was held", String.format(java.util.Locale.ROOT, "%.3f",
            Minecraft.getInstance().player.position().distanceTo(START)));
         playerCam();
         camera(CameraType.FIRST_PERSON);
      });
      shotAt(SinclairMove.COPY, 47, "sinclair_copy_05_fold");
      shotAt(SinclairMove.COPY, 56, "sinclair_copy_06_return");
      at(0, "wait copy end", () -> { });
      untilIdle();
      shot(4, "sinclair_35_fp_card_window");

      ActShape[] shapes = {ActShape.BEAM, ActShape.NOVA, ActShape.CUT, ActShape.BARRAGE, ActShape.AEGIS, ActShape.FINALE};
      for (int i = 0; i < shapes.length; i++) {
         ActShape shape = shapes[i];
         ActScript script = ActScript.of(shape);
         String key = shape.key();
         boolean first = i == 0;
         if (!first) {
            at(70, "act " + key + " setup", () -> {
               resetCooldowns();
               killMobs();
               volunteer(7.5);
               stage(0.0F);
               playerCam();
               camera(CameraType.FIRST_PERSON);
            });
            at(20, "B capture " + key, () -> press(AbilityInputManager.abilityKey5));
            // The card names the number on the card (forced shape, as below) while the cinematic runs.
            untilMove(SinclairMove.COPY, 2);
            at(0, "force " + key, () -> force(shape));
            shotAt(SinclairMove.COPY, 26, "sinclair_copy_card_" + key);
            untilIdle();
         } else {
            at(2, "force " + key, () -> force(shape));
         }

         at(2, "side " + key, () -> cam(-7.0, -57.6, 4.0, 0.5, -59.0, 4.0));
         at(4, "B perform " + key, () -> press(AbilityInputManager.abilityKey5));
         String p = "sinclair_act_" + key + "_";
         shotAct(script.main() / 2, p + "1_tension");
         shotAct((script.main() + script.hushStart()) / 2, p + "2_main");
         ownView(true);
         shotAct((script.main() + script.hushStart()) / 2 + 4, p + "2_main_own_view");
         ownView(false);
         // Shots land ~5 ticks after their gate at llvmpipe frame rates; the hush one aims early.
         shotAct(script.hushStart() - 2, p + "3_hush");
         shotAct(script.reveal() + 2, p + "4_reveal");
         ownView(true);
         shotAct(script.reveal() + 5, p + "4_reveal_own_view");
         ownView(false);
         shotAct((script.pauseStart() + script.pauseEnd()) / 2 + 2, p + "5_pause");
         shotAct(script.bowStart() + 12, p + "6_bow");
         at(0, "act end " + key, () -> { });
         untilIdle();
      }

      // The switch: with the cinematic off the capture opens the window at once (no freeze, no card scene).
      at(40, "cinematic off", () -> {
         cmd("sinclair cinematic off");
         resetCooldowns();
         killMobs();
         volunteer(7.5);
         stage(0.0F);
         playerCam();
         camera(CameraType.FIRST_PERSON);
      });
      at(20, "B capture (off)", () -> press(AbilityInputManager.abilityKey5));
      at(30, "check off", () -> {
         HeroPublicSnapshot s = SinclairView.of(Minecraft.getInstance().player);
         LOG.info("SINCLAIR_PT cinematic off: move after capture = {}", s == null ? null : SinclairView.move(s));
      });
      shot(0, "sinclair_copy_07_off_window");

      // Safe abort: the volunteer dies mid-scene; both must be let go at once.
      at(260, "cinematic on", () -> {
         cmd("sinclair cinematic on");
         resetCooldowns();
         killMobs();
         volunteer(7.5);
         stage(0.0F);
      });
      at(20, "B capture (abort)", () -> press(AbilityInputManager.abilityKey5));
      untilMove(SinclairMove.COPY, 20);
      // A frozen body queues its damage until the hold ends (shared control rule), so /kill cannot end the
      // scene early; removing the volunteer (unload, logout, despawn) is the path that must abort it.
      at(0, "remove volunteer", () -> server(player -> player.serverLevel()
         .getEntities(net.minecraft.world.entity.EntityType.HUSK, e -> true).forEach(net.minecraft.world.entity.Entity::discard)));
      // The server may run both queued tasks before its next tick, so the check waits a few ticks.
      at(8, "check abort", () -> server(player -> {
         SinclairState state = SinclairState.ensure(player);
         boolean held = ControlManager.get(player.serverLevel()).isControlled(player);
         long alive = player.serverLevel().getEntities(net.minecraft.world.entity.EntityType.HUSK, e -> e.isAlive()).size();
         LOG.info("SINCLAIR_PT abort: move {} ult {} window {} controlled {} husks alive {}", state.move, state.ult, state.window, held, alive);
      }));
      shot(1, "sinclair_copy_08_abort");

      at(60, "done", () -> {
         LOG.info("SINCLAIR_PT done after {} ticks", ticks);
         SOUNDS.forEach((id, count) -> LOG.info("SINCLAIR_PT sound {} x{}", id, count));
         Minecraft.getInstance().stop();
      });
   }

   private static net.minecraft.world.phys.Vec3 START = net.minecraft.world.phys.Vec3.ZERO;

   private static void force(ActShape shape) {
      server(player -> {
         SinclairState state = SinclairState.ensure(player);
         state.captured = SinclairRepertoire.standard(shape);
         state.capturedFrom = SinclairRepertoire.Source.REPERTOIRE;
      });
   }

   private static void ownView(boolean own) {
      at(0, own ? "own view" : "side again", () -> {
         if (own) {
            playerCam();
            camera(CameraType.FIRST_PERSON);
         } else {
            cam(-7.0, -57.6, 4.0, 0.5, -59.0, 4.0);
         }
      });
   }

   /** Synced move and elapsed ticks of the local Sinclair (null move when idle). */
   @javax.annotation.Nullable
   private static SinclairMove localMove() {
      Minecraft mc = Minecraft.getInstance();
      HeroPublicSnapshot s = mc.player == null ? null : SinclairView.of(mc.player);
      return s == null ? null : SinclairView.move(s);
   }

   private static int localElapsed() {
      Minecraft mc = Minecraft.getInstance();
      HeroPublicSnapshot s = mc.player == null ? null : SinclairView.of(mc.player);
      return s == null || s.actionId() < 0 ? -1 : s.actionElapsed();
   }

   /** Blocks the script until the given move reaches the elapsed tick (timeout 300 ticks). */
   private static void untilMove(SinclairMove move, int tick) {
      STEPS.add(new Step(0, "until " + move + " " + tick, () -> { }, () -> localMove() == move && localElapsed() >= tick));
   }

   private static void untilIdle() {
      STEPS.add(new Step(2, "until idle", () -> { }, () -> {
         SinclairMove m = localMove();
         return m == null || !(m.isAct() || m == SinclairMove.CAPTURE || m == SinclairMove.COPY);
      }));
   }

   private static void shotAt(SinclairMove move, int tick, String name) {
      untilMove(move, tick);
      shot(0, name);
   }

   private static void shotAct(int tick, String name) {
      STEPS.add(new Step(0, "until act " + tick, () -> { }, () -> {
         SinclairMove m = localMove();
         return m != null && m.isAct() && localElapsed() >= tick;
      }));
      shot(0, name);
   }

   // ---------------------------------------------------------------- driver

   private static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      mc.setWindowActive(true);
      if (mc.level == null || mc.player == null) {
         if (!worldRequested && mc.screen instanceof TitleScreen) {
            worldRequested = true;
            openWorld(mc);
         }

         return;
      }

      ticks++;
      if (camWanted && !(mc.getCameraEntity() instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
         for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand && e.hasCustomName() && "pt_cam".equals(e.getCustomName().getString())) {
               mc.setCameraEntity(e);
               break;
            }
         }
      } else if (camWanted && mc.getCameraEntity() instanceof net.minecraft.world.entity.decoration.ArmorStand stand && stand.isRemoved()) {
         mc.setCameraEntity(mc.player);
      }

      RELEASES.removeIf(r -> {
         if (ticks >= r.at()) {
            KeyMapping.set(r.key().getKey(), false);
            return true;
         }

         return false;
      });
      if (ticks < 80 || pendingShot != null || stepIndex >= STEPS.size()) {
         return;
      }

      Step next = STEPS.get(stepIndex);
      if (wait < next.delay() || next.ready() != null && !next.ready().getAsBoolean() && wait < next.delay() + 300) {
         if (next.ready() != null && wait == next.delay() + 299) {
            LOG.warn("SINCLAIR_PT step '{}' timed out (move {} elapsed {})", next.label(), localMove(), localElapsed());
         }

         wait++;
         return;
      }

      Step step = STEPS.get(stepIndex++);
      wait = 0;
      LOG.info("SINCLAIR_PT step {} '{}' at tick {}", stepIndex, step.label(), ticks);
      try {
         step.action().run();
      } catch (RuntimeException e) {
         LOG.error("SINCLAIR_PT step '{}' failed", step.label(), e);
      }
   }

   private static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (event.phase != TickEvent.Phase.END || pendingShot == null) {
         return;
      }

      if (shotDelayFrames-- > 0) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      String name = pendingShot + ".png";
      pendingShot = null;
      Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> LOG.info("SINCLAIR_PT shot {} -> {}", name, message.getString()));
      LOG.info("SINCLAIR_PT fps {} at shot {}", mc.getFps(), name);
   }

   private static void openWorld(Minecraft mc) {
      try {
         if (new File(mc.gameDirectory, "saves/" + WORLD).isDirectory()) {
            mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
            return;
         }

         GameRules rules = new GameRules();
         LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
         mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261028L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
      } catch (RuntimeException e) {
         LOG.error("SINCLAIR_PT could not open the world", e);
      }
   }
}
