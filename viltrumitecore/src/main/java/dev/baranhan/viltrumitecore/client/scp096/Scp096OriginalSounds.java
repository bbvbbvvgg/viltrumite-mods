package dev.baranhan.viltrumitecore.client.scp096;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * The original SCP: Containment Breach sounds of SCP-096, taken from the player's own copy of the game. The mod
 * does not ship them (see tools/assets/scp096_sources.md for why): a built-in, always-on client pack serves the
 * files found on this computer under the mod's sound events and replaces the vanilla fallbacks of
 * src/main/sound-overlays/scp096.json. Searched in order, the first valid Ogg file per sound wins: the folder in
 * the config ({@code scp096OriginalSoundsPath}), {@code <game dir>/viltrumitecore/scp096}, then SCP: CB install
 * folders next to the usual places (Desktop, Downloads, Games, Program Files, Steam libraries). Each folder may be
 * the game root ({@code SFX/Music/096.ogg} ...) or hold the files directly ({@code 096.ogg} ...). The scan runs
 * whenever the resource packs are rebuilt (start-up, F3+T). Client only.
 */
public final class Scp096OriginalSounds {
   public static final String PACK_ID = "viltrumitecore_scp096_original";
   private static final Logger LOGGER = LogUtils.getLogger();
   /** {served name, sound event, path inside an SCP: CB install}. */
   static final String[][] FILES = {
      {"cry", "scp096.cry", "SFX/Music/096.ogg"},
      {"angered", "scp096.angered", "SFX/Music/096Angered.ogg"},
      {"scream", "scp096.scream", "SFX/SCP/096/Scream.ogg"},
      {"triggered", "scp096.triggered", "SFX/SCP/096/Triggered.ogg"},
      {"chase", "scp096.chase_music", "SFX/Music/096Chase.ogg"},
   };
   static final long MAX_BYTES = 32L << 20;
   private static volatile Map<String, Path> found = Map.of();

   private Scp096OriginalSounds() {
   }

   /** True when the original of this sound ("cry", "angered", "scream", "triggered", "chase") is in use. */
   public static boolean has(String name) {
      return found.containsKey(name);
   }

   public static int count() {
      return found.size();
   }

   /** Mod bus: the pack source for client resources. */
   public static void onAddPackFinders(AddPackFindersEvent event) {
      if (event.getPackType() != PackType.CLIENT_RESOURCES) {
         return;
      }
      event.addRepositorySource(consumer -> {
         Map<String, Path> files = locate(roots());
         found = files;
         if (files.isEmpty()) {
            LOGGER.info("SCP-096: original SCP: Containment Breach sounds not found; vanilla fallbacks in use"
               + " (put the game folder path into scp096OriginalSoundsPath or copy the files to viltrumitecore/scp096)");
            return;
         }
         LOGGER.info("SCP-096: using {} original SCP: Containment Breach sound(s) from this computer: {}", files.size(), files.keySet());
         Pack pack = Pack.readMetaAndCreate(PACK_ID, Component.translatable("pack.viltrumitecore.scp096_original.title"), true,
            id -> new Scp096OriginalPack(id, files), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
         if (pack != null) {
            consumer.accept(pack);
         }
      });
   }

   /** Folders to search, most specific first. */
   static List<Path> roots() {
      List<Path> out = new ArrayList<>();
      String configured = ViltrumiteCoreConfig.INSTANCE.scp096OriginalSoundsPath;
      if (configured != null && !configured.isBlank()) {
         try {
            out.add(Path.of(configured.trim()));
         } catch (RuntimeException ignored) {
         }
      }
      out.add(FMLPaths.GAMEDIR.get().resolve("viltrumitecore").resolve("scp096"));
      List<Path> bases = new ArrayList<>();
      String home = System.getProperty("user.home", "");
      if (!home.isEmpty()) {
         Path h = Path.of(home);
         for (String sub : new String[]{"Desktop", "Downloads", "Games", "Documents", ""}) {
            bases.add(sub.isEmpty() ? h : h.resolve(sub));
         }
         bases.add(h.resolve(".steam/steam/steamapps/common"));
         bases.add(h.resolve(".local/share/Steam/steamapps/common"));
      }
      for (String drive : new String[]{"C:/", "D:/"}) {
         bases.add(Path.of(drive + "Program Files (x86)"));
         bases.add(Path.of(drive + "Program Files"));
         bases.add(Path.of(drive + "Games"));
         bases.add(Path.of(drive + "Program Files (x86)/Steam/steamapps/common"));
         bases.add(Path.of(drive + "SteamLibrary/steamapps/common"));
      }
      for (Path base : bases) {
         out.addAll(gameFolders(base));
      }
      return out;
   }

   /** Children of base that look like an SCP: CB install (one directory listing, no recursion). */
   static List<Path> gameFolders(Path base) {
      List<Path> out = new ArrayList<>();
      if (!Files.isDirectory(base)) {
         return out;
      }
      try (DirectoryStream<Path> dir = Files.newDirectoryStream(base)) {
         for (Path p : dir) {
            if (looksLikeGame(p.getFileName().toString()) && Files.isDirectory(p)) {
               out.add(p);
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }
      return out;
   }

   /** Pure: folder names of SCP: CB installs ("SCP - Containment Breach v1.3.11", "scpcb", ...). */
   static boolean looksLikeGame(String name) {
      String n = name.toLowerCase(Locale.ROOT);
      return n.contains("containment breach") || n.contains("containment_breach") || n.startsWith("scpcb") || n.startsWith("scp-cb")
         || n.startsWith("scp_cb");
   }

   /** Served name → file, first valid hit per sound over the roots. */
   static Map<String, Path> locate(List<Path> roots) {
      Map<String, Path> out = new LinkedHashMap<>();
      for (Path root : roots) {
         if (out.size() == FILES.length) {
            break;
         }
         if (!Files.isDirectory(root)) {
            continue;
         }
         for (String[] f : FILES) {
            if (out.containsKey(f[0])) {
               continue;
            }
            for (String rel : candidates(f[2])) {
               Path p = root.resolve(rel);
               if (validOgg(p)) {
                  out.put(f[0], p);
                  break;
               }
            }
         }
      }
      return out;
   }

   /** Pure: where one file may sit under a root: the game tree, flat, or flattened with underscores. */
   static List<String> candidates(String gamePath) {
      String flat = gamePath.substring(gamePath.lastIndexOf('/') + 1);
      return List.of(gamePath, flat, gamePath.replace('/', '_'));
   }

   static boolean validOgg(Path p) {
      try {
         if (!Files.isRegularFile(p)) {
            return false;
         }
         long size = Files.size(p);
         if (size < 1024 || size > MAX_BYTES) {
            return false;
         }
         try (InputStream in = Files.newInputStream(p)) {
            return isOgg(in.readNBytes(4));
         }
      } catch (IOException | RuntimeException e) {
         return false;
      }
   }

   /** Pure: the Ogg capture pattern "OggS". */
   static boolean isOgg(@Nullable byte[] head) {
      return head != null && head.length >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S';
   }
}
