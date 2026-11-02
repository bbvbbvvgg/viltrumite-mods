package dev.baranhan.viltrumitecore.client.hero.speech.qa;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.gui.RaceSelectionScreen;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroSubtitles;
import dev.baranhan.viltrumitecore.client.reinhard.finalpunch.FinalPunchClient;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
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
 * Opt-in subtitle playtest driver (global subtitles expansion): {@code -Dviltrumitecore.subtitles.playtest=true}
 * only, nothing is registered otherwise. It logs every hero line the client receives, stops, clears and
 * actually draws ({@code SUBPT} lines, see {@link HeroSubtitles#qaLog}) and executes commands written to
 * {@code <gameDir>/subtitle_qa.txt} (one per line, the file is emptied once read), so a shell can drive a real
 * client: {@code press <1-6> [ticks]} (the real ability key mappings), {@code key attack|use|pick|jump|sneak <ticks>},
 * {@code cmd <command>} (sent as a chat command), {@code shot <name>}, {@code cam fp|back|front},
 * {@code look <yaw> <pitch>}, {@code leave} (to the title screen), {@code world} (create or reopen the
 * singleplayer world {@code subtitle_playtest}), {@code close} (closes any screen), {@code button <n>} (presses the n-th button of the open
 * screen), {@code respawn} (from the death screen). {@code key} also takes {@code swap} (ability page).
 * With {@code -Dviltrumitecore.subtitles.playtest.world=true} the title screen opens that world by itself.
 * {@code fpshots <prefix> <t1,t2,...>} takes screenshots on the Final Punch scene clock (first scene on the client)
 * and logs the scene every 20 ticks while one exists ({@code SUBPT ... fp}). {@code collapsestate} logs the
 * structural-collapse scenes (count, drawn fragments, render ms) and the fps; {@code godzillastate} the kaiju's last
 * drawn LOD, camera distance, body build time, hot zones and particles; {@code godzillalod <n>} forces a LOD (-1 = by
 * distance).
 */
public final class SubtitlePlaytest {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.subtitles.playtest");
   private static final boolean AUTO_WORLD = Boolean.getBoolean("viltrumitecore.subtitles.playtest.world");
   private static final Logger LOG = LogUtils.getLogger();
   private static final String WORLD = "subtitle_playtest";
   private static final List<Release> RELEASES = new ArrayList<>();
   private static int ticks;
   private static boolean worldRequested;
   private static String pendingShot;
   private static int shotDelayFrames;
   private static final List<Integer> SCENE_SHOTS = new ArrayList<>();
   private static String sceneShotPrefix = "fp";

   private record Release(KeyMapping key, int at) {
   }

   private SubtitlePlaytest() {
   }

   public static void init() {
      if (!ENABLED) {
         return;
      }

      HeroSubtitles.qaLog = message -> LOG.info("SUBPT t={} {}", ticks, message);
      MinecraftForge.EVENT_BUS.addListener(SubtitlePlaytest::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(SubtitlePlaytest::onRenderTick);
      MinecraftForge.EVENT_BUS.addListener(SubtitlePlaytest::onPlaySound);
      LOG.info("SUBPT enabled (command file {})", commandFile().getAbsolutePath());
   }

   private static File commandFile() {
      return new File(Minecraft.getInstance().gameDirectory, "subtitle_qa.txt");
   }

   private static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      mc.setWindowActive(true);
      ticks++;
      if (AUTO_WORLD && !worldRequested && mc.level == null && mc.screen instanceof TitleScreen) {
         worldRequested = true;
         openWorld(mc);
      }

      if (mc.screen instanceof RaceSelectionScreen && mc.level != null) {
         // The hero is set by the operator command (/subtitleqa hero), not the one-time choice screen.
         mc.setScreen(null);
      }

      RELEASES.removeIf(r -> {
         if (ticks >= r.at()) {
            KeyMapping.set(r.key().getKey(), false);
            return true;
         }

         return false;
      });
      if (ticks % 5 == 0) {
         readCommands(mc);
      }

      if (ticks % 20 == 0 && FinalPunchClient.any()) {
         for (FinalPunchClient.Scene scene : FinalPunchClient.all()) {
            var target = scene.target();
            var caster = scene.caster();
            LOG.info("SUBPT t={} fp scene={} caster={} target={} targetPos={} casterPos={} impacted={} cancelled={} cam={}", ticks,
               String.format(java.util.Locale.ROOT, "%.0f", scene.time(0.0F)), scene.casterId, scene.targetId,
               target == null ? "-" : target.position(), caster == null ? "-" : caster.position(), scene.impacted(), scene.cancelled(),
               mc.options.getCameraType());
         }
      }
   }

   private static void readCommands(Minecraft mc) {
      File file = commandFile();
      if (!file.isFile() || file.length() == 0L) {
         return;
      }

      List<String> lines;
      try {
         lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
         Files.writeString(file.toPath(), "", StandardCharsets.UTF_8);
      } catch (IOException e) {
         LOG.warn("SUBPT cannot read {}", file, e);
         return;
      }

      for (String line : lines) {
         String trimmed = line.trim();
         if (!trimmed.isEmpty()) {
            LOG.info("SUBPT t={} exec '{}'", ticks, trimmed);
            try {
               exec(mc, trimmed);
            } catch (RuntimeException e) {
               LOG.error("SUBPT command '{}' failed", trimmed, e);
            }
         }
      }
   }

   private static void exec(Minecraft mc, String line) {
      String[] parts = line.split(" ", 2);
      String arg = parts.length > 1 ? parts[1].trim() : "";
      switch (parts[0]) {
         case "press" -> {
            String[] a = arg.split(" ");
            KeyMapping key = abilityKey(Integer.parseInt(a[0]));
            hold(key, a.length > 1 ? Integer.parseInt(a[1]) : 3);
         }
         case "key" -> {
            String[] a = arg.split(" ");
            KeyMapping key = switch (a[0]) {
               case "attack" -> mc.options.keyAttack;
               case "use" -> mc.options.keyUse;
               case "pick" -> mc.options.keyPickItem;
               case "jump" -> mc.options.keyJump;
               case "sneak" -> mc.options.keyShift;
               case "forward" -> mc.options.keyUp;
               case "swap" -> AbilityInputManager.abilitySwapKey;
               default -> throw new IllegalArgumentException("unknown key " + a[0]);
            };
            hold(key, a.length > 1 ? Integer.parseInt(a[1]) : 3);
         }
         case "cmd" -> {
            if (mc.player != null) {
               mc.player.connection.sendCommand(arg.startsWith("/") ? arg.substring(1) : arg);
            }
         }
         case "shot" -> {
            pendingShot = arg;
            shotDelayFrames = 2;
         }
         case "cam" -> mc.options.setCameraType(switch (arg) {
            case "back" -> CameraType.THIRD_PERSON_BACK;
            case "front" -> CameraType.THIRD_PERSON_FRONT;
            default -> CameraType.FIRST_PERSON;
         });
         case "look" -> {
            String[] a = arg.split(" ");
            if (mc.player != null) {
               mc.player.setYRot(Float.parseFloat(a[0]));
               mc.player.setXRot(Float.parseFloat(a[1]));
            }
         }
         case "leave" -> {
            if (mc.level != null) {
               mc.level.disconnect();
               mc.clearLevel(new TitleScreen());
            }
         }
         case "world" -> openWorld(mc);
         case "close" -> mc.setScreen(null);
         case "button" -> {
            // Presses the n-th button (0-based) of the open screen, e.g. "Continue" on the experimental-world warning.
            if (mc.screen != null) {
               List<net.minecraft.client.gui.components.Button> buttons = new ArrayList<>();
               for (var child : mc.screen.children()) {
                  if (child instanceof net.minecraft.client.gui.components.Button button) {
                     buttons.add(button);
                  }
               }

               int index = arg.isEmpty() ? 0 : Integer.parseInt(arg);
               if (index < buttons.size()) {
                  LOG.info("SUBPT t={} button {} '{}'", ticks, index, buttons.get(index).getMessage().getString());
                  buttons.get(index).onPress();
               }
            }
         }
         case "respawn" -> {
            if (mc.player != null) {
               mc.player.respawn();
            }
         }
         case "fpshots" -> {
            String[] a = arg.split(" ");
            sceneShotPrefix = a[0];
            SCENE_SHOTS.clear();
            for (String t : a[1].split(",")) {
               SCENE_SHOTS.add(Integer.parseInt(t.trim()));
            }

            SCENE_SHOTS.sort(Integer::compare);
         }
         case "state" -> LOG.info("SUBPT t={} state level={} player={} hp={} screen={} fps={}", ticks, mc.level != null, mc.player != null,
            mc.player == null ? -1.0F : mc.player.getHealth(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), mc.getFps());
         // encyclopedia QA (hero gallery): open the wish screen, drive the GUI, resize, switch language
         case "collapsestate" -> LOG.info("SUBPT t={} collapse {} fps={}", ticks,
            dev.baranhan.viltrumitecore.client.collapse.CollapseClient.qaSummary(), mc.getFps());
         case "godzillalod" -> dev.baranhan.viltrumitecore.client.godzilla.GodzillaRenderer.qaForceLod = Integer.parseInt(arg);
         case "godzillastate" -> LOG.info("SUBPT t={} godzilla {} fps={}", ticks,
            dev.baranhan.viltrumitecore.client.godzilla.GodzillaClient.qaSummary(), mc.getFps());
         case "wish" -> mc.setScreen(new dev.baranhan.viltrumitecore.client.wish.WishScreen());
         case "click" -> {
            String[] a = arg.split(" ");
            if (mc.screen != null) {
               double x = Double.parseDouble(a[0]);
               double y = Double.parseDouble(a[1]);
               mc.screen.mouseMoved(x, y);
               boolean handled = mc.screen.mouseClicked(x, y, 0);
               if (mc.screen != null) {
                  mc.screen.mouseReleased(x, y, 0);
               }
               LOG.info("SUBPT t={} click {} {} handled={} screen={}", ticks, x, y, handled, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            }
         }
         case "mouse" -> {
            String[] a = arg.split(" ");
            double scale = mc.getWindow().getGuiScale();
            double wx = Double.parseDouble(a[0]) * scale;
            double wy = Double.parseDouble(a[1]) * scale;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), wx, wy);
            // Xvfb sends no cursor callback for a warp: set the handler's position too (first two doubles: xpos, ypos)
            try {
               int n = 0;
               for (java.lang.reflect.Field f : net.minecraft.client.MouseHandler.class.getDeclaredFields()) {
                  if (f.getType() == double.class && n < 2) {
                     f.setAccessible(true);
                     f.setDouble(mc.mouseHandler, n++ == 0 ? wx : wy);
                  }
               }
            } catch (ReflectiveOperationException e) {
               LOG.warn("SUBPT mouse: {}", e.toString());
            }
         }
         case "scroll" -> {
            String[] a = arg.split(" ");
            if (mc.screen != null) {
               mc.screen.mouseScrolled(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
            }
         }
         case "keypress" -> {
            if (mc.screen != null) {
               int key = switch (arg) {
                  case "esc" -> org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
                  case "down" -> org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
                  case "up" -> org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
                  case "left" -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
                  case "right" -> org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
                  case "pgdn" -> org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
                  default -> Integer.parseInt(arg);
               };
               mc.screen.keyPressed(key, 0, 0);
            }
         }
         case "codex" -> {
            String[] a = arg.split(" ");
            if (mc.screen instanceof dev.baranhan.viltrumitecore.client.codex.CodexScreen codex) {
               codex.qaSelect(Integer.parseInt(a[0]), a.length > 1 ? Integer.parseInt(a[1]) : 0);
               if (a.length > 2) {
                  codex.qaScroll(Float.parseFloat(a[2]));
               }
            }
         }
         case "guiscale" -> {
            mc.options.guiScale().set(Integer.parseInt(arg));
            mc.resizeDisplay();
         }
         case "window" -> {
            String[] a = arg.split(" ");
            mc.getWindow().setWindowed(Integer.parseInt(a[0]), Integer.parseInt(a[1]));
         }
         case "lang" -> {
            mc.options.languageCode = arg;
            mc.getLanguageManager().setSelected(arg);
            mc.reloadResourcePacks();
         }
         case "codexstate" -> {
            dev.baranhan.viltrumitecore.codex.CodexRecord r = dev.baranhan.viltrumitecore.client.codex.CodexClientState.snapshot();
            LOG.info("SUBPT t={} codex heroes={} interactions={} screen={} gui={}x{} scale={}", ticks, r.heroes(), r.interactions(),
               mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
               mc.getWindow().getGuiScale());
         }
         default -> LOG.warn("SUBPT unknown command '{}'", line);
      }
   }

   private static KeyMapping abilityKey(int slot) {
      return switch (slot) {
         case 1 -> AbilityInputManager.abilityKey1;
         case 2 -> AbilityInputManager.abilityKey2;
         case 3 -> AbilityInputManager.abilityKey3;
         case 4 -> AbilityInputManager.abilityKey4;
         case 5 -> AbilityInputManager.abilityKey5;
         case 6 -> AbilityInputManager.abilityKey6;
         default -> throw new IllegalArgumentException("slot 1..6: " + slot);
      };
   }

   /** A real key press held for {@code holdTicks} (hero keys are edge-tracked on isDown). */
   private static void hold(KeyMapping key, int holdTicks) {
      KeyMapping.set(key.getKey(), true);
      KeyMapping.click(key.getKey());
      RELEASES.add(new Release(key, ticks + Math.max(1, holdTicks)));
   }

   /** Mod sounds the client engine was asked to play, with the client tick (line vs audio timing). */
   private static void onPlaySound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
      if (event.getSound() != null && !"minecraft".equals(event.getSound().getLocation().getNamespace())) {
         LOG.info("SUBPT t={} sound {}", ticks, event.getSound().getLocation());
      }
   }

   private static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (event.phase == TickEvent.Phase.END && pendingShot == null && !SCENE_SHOTS.isEmpty() && FinalPunchClient.any()) {
         double time = FinalPunchClient.all().iterator().next().time(event.renderTickTime);
         if (time >= SCENE_SHOTS.get(0)) {
            int at = SCENE_SHOTS.remove(0);
            FinalPunchClient.Scene scene = FinalPunchClient.all().iterator().next();
            Minecraft mc = Minecraft.getInstance();
            var camera = mc.gameRenderer.getMainCamera();
            var target = scene.target();
            var caster = scene.caster();
            LOG.info("SUBPT t={} fp shot at scene {} (clock {}) cam={} yaw={} pitch={} toTarget={} toCaster={} fov={}", ticks, at,
               String.format(java.util.Locale.ROOT, "%.2f", time), camera.getPosition(), camera.getYRot(), camera.getXRot(),
               target == null ? -1.0 : camera.getPosition().distanceTo(target.position().add(0, target.getBbHeight() * 0.5, 0)),
               caster == null ? -1.0 : camera.getPosition().distanceTo(caster.position().add(0, 1.0, 0)), mc.options.fov().get());
            if (target != null) logParticlesNear(mc, target.position());
            pendingShot = sceneShotPrefix + "_" + at;
            shotDelayFrames = 0;
         }
      }

      if (event.phase != TickEvent.Phase.END || pendingShot == null) {
         return;
      }

      if (shotDelayFrames-- > 0) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      String name = pendingShot + ".png";
      pendingShot = null;
      Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> LOG.info("SUBPT shot {} -> {}", name, message.getString()));
   }

   /** QA: vanilla particles within 3 blocks of a point, by class (reflection on the engine's private map). */
   private static void logParticlesNear(Minecraft mc, net.minecraft.world.phys.Vec3 at) {
      try {
         java.lang.reflect.Field field = null;
         for (java.lang.reflect.Field f : net.minecraft.client.particle.ParticleEngine.class.getDeclaredFields()) {
            if (java.util.Map.class.isAssignableFrom(f.getType())) {
               f.setAccessible(true);
               Object value = f.get(mc.particleEngine);
               if (value instanceof java.util.Map<?, ?> map && !map.isEmpty() && map.values().iterator().next() instanceof java.util.Collection<?>) {
                  field = f;
                  break;
               }
            }
         }
         if (field == null) return;
         java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
         for (Object queue : ((java.util.Map<?, ?>) field.get(mc.particleEngine)).values()) {
            for (Object o : (java.util.Collection<?>) queue) {
               if (o instanceof net.minecraft.client.particle.Particle particle && particle.getBoundingBox().getCenter().distanceTo(at) < 4.0) {
                  counts.merge(o.getClass().getSimpleName(), 1, Integer::sum);
               }
            }
         }
         LOG.info("SUBPT particles near target {}", counts);
         if (mc.level != null) {
            List<String> near = new ArrayList<>();
            for (net.minecraft.world.entity.Entity e : mc.level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(at, at).inflate(4.0), e -> true)) {
               near.add(e.getType().toShortString() + "#" + e.getId() + "@" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", e.getX() - at.x, e.getY() - at.y, e.getZ() - at.z));
            }
            LOG.info("SUBPT entities near target {}", near);
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         LOG.info("SUBPT particles n/a {}", e.toString());
      }
   }

   private static void openWorld(Minecraft mc) {
      try {
         if (new File(mc.gameDirectory, "saves/" + WORLD).isDirectory()) {
            mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
            return;
         }

         LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
         mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261031L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
      } catch (RuntimeException e) {
         LOG.error("SUBPT could not open the world", e);
      }
   }
}
