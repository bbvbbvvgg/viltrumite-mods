package dev.baranhan.viltrumitecore.client.scp096;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * In-memory client pack of {@link Scp096OriginalSounds}: pack.mcmeta, a sounds.json that replaces the SCP-096
 * events (streamed: the loops are long, and streaming lets positional loops be folded to mono) and the found
 * .ogg files read straight from where they are. Nothing is copied. Client only.
 */
final class Scp096OriginalPack extends AbstractPackResources {
   static final String NS = "viltrumitecore";
   static final String PREFIX = "sounds/scp096/original/";
   private final Map<String, Path> files;
   private final byte[] meta;
   private final byte[] sounds;

   Scp096OriginalPack(String id, Map<String, Path> files) {
      super(id, true);
      this.files = files;
      this.meta = ("{\"pack\":{\"pack_format\":15,\"description\":{\"translate\":\"pack.viltrumitecore.scp096_original.desc\"}}}")
         .getBytes(StandardCharsets.UTF_8);
      this.sounds = soundsJson(files.keySet()).getBytes(StandardCharsets.UTF_8);
   }

   /** Pure: the sounds.json for the found originals (subtitles kept, attenuation per loop). */
   static String soundsJson(Set<String> names) {
      JsonObject root = new JsonObject();
      for (String[] f : Scp096OriginalSounds.FILES) {
         if (!names.contains(f[0])) {
            continue;
         }
         JsonObject ev = new JsonObject();
         ev.addProperty("replace", true);
         if (!"chase".equals(f[0])) {
            ev.addProperty("subtitle", "subtitles.viltrumitecore." + f[1]);
         }
         JsonObject s = new JsonObject();
         s.addProperty("name", NS + ":scp096/original/" + f[0]);
         s.addProperty("stream", true);
         int range = attenuation(f[0]);
         if (range > 0) {
            s.addProperty("attenuation_distance", range);
         }
         JsonArray list = new JsonArray();
         list.add(s);
         ev.add("sounds", list);
         root.add(f[1], ev);
      }
      return root.toString();
   }

   /** Pure: hearing range in blocks of a positional original (0 = played relative to the listener). */
   static int attenuation(String name) {
      return switch (name) {
         case "cry" -> 20;
         case "angered" -> 40;
         case "scream" -> 56;
         default -> 0;
      };
   }

   @Nullable
   @Override
   public IoSupplier<InputStream> getRootResource(String... path) {
      if (path.length == 1 && "pack.mcmeta".equals(path[0])) {
         return () -> new ByteArrayInputStream(this.meta);
      }
      return null;
   }

   @Nullable
   @Override
   public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
      if (type != PackType.CLIENT_RESOURCES || !NS.equals(location.getNamespace())) {
         return null;
      }
      String path = location.getPath();
      if ("sounds.json".equals(path)) {
         return () -> new ByteArrayInputStream(this.sounds);
      }
      Path file = this.file(path);
      return file == null ? null : () -> Files.newInputStream(file);
   }

   @Nullable
   private Path file(String path) {
      if (path.startsWith(PREFIX) && path.endsWith(".ogg")) {
         return this.files.get(path.substring(PREFIX.length(), path.length() - 4));
      }
      return null;
   }

   @Override
   public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
      if (type != PackType.CLIENT_RESOURCES || !NS.equals(namespace)) {
         return;
      }
      String dir = path.endsWith("/") ? path : path + "/";
      for (Map.Entry<String, Path> e : this.files.entrySet()) {
         String rp = PREFIX + e.getKey() + ".ogg";
         if (rp.startsWith(dir)) {
            Path file = e.getValue();
            output.accept(new ResourceLocation(NS, rp), () -> Files.newInputStream(file));
         }
      }
   }

   @Override
   public Set<String> getNamespaces(PackType type) {
      return type == PackType.CLIENT_RESOURCES ? Set.of(NS) : Set.of();
   }

   @Override
   public void close() {
   }
}
