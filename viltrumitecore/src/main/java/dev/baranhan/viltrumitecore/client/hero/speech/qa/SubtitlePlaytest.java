package dev.baranhan.viltrumitecore.client.hero.speech.qa;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.client.AbilityInputManager;
import dev.baranhan.viltrumitecore.client.gui.RaceSelectionScreen;
import dev.baranhan.viltrumitecore.client.hero.speech.HeroSubtitles;
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
 * singleplayer world {@code subtitle_playtest}), {@code close} (closes any screen).
 * With {@code -Dviltrumitecore.subtitles.playtest.world=true} the title screen opens that world by itself.
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
         case "state" -> LOG.info("SUBPT t={} state level={} player={} hp={} screen={} fps={}", ticks, mc.level != null, mc.player != null,
            mc.player == null ? -1.0F : mc.player.getHealth(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), mc.getFps());
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
