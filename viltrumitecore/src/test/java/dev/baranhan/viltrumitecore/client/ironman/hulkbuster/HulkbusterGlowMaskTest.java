package dev.baranhan.viltrumitecore.client.ironman.hulkbuster;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The emissive mask lights only emitter faces (plan H1, the two chest squares of the 2026-10-28 playtest):
 * RenderType.eyes draws every lit texel at full brightness, so a lit texel inside the UV of any plain face is a
 * glowing square on the armour. The face rects follow BakedGeoModel / tools/assets/rig_hulkbuster.py java_rects
 * exactly (box UV and per-face UV, east/west swap, mirror); the whitelist is the rig's EMISSIVE table.
 */
class HulkbusterGlowMaskTest {
   static final Path RES = Path.of("src/main/resources/assets/viltrumitecore");
   static final Path BIN = Path.of("src/main/binassets/assets/viltrumitecore");
   /** Bone (exact, or prefix when it ends with '_') → glowing faces (null = all), as in rig_hulkbuster.EMISSIVE. */
   static final Map<String, Set<String>> EMISSIVE = new LinkedHashMap<>();

   static {
      EMISSIVE.put("visor_p0", Set.of("north"));
      EMISSIVE.put("reactor", null);
      EMISSIVE.put("foot_thruster_", Set.of("down"));
      EMISSIVE.put("hand_l_shell", Set.of("north"));
      EMISSIVE.put("fingers_l_shell", Set.of("north"));
      EMISSIVE.put("thumb_l_shell", Set.of("north"));
   }

   @Test
   void noLitTexelOutsideTheEmitterFaces() throws IOException {
      BufferedImage glow = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(
         Files.readString(BIN.resolve("textures/entity/ironman/hulkbuster_glow.png.b64")).trim())));
      JsonObject geo = JsonParser.parseString(Files.readString(RES.resolve("geo/ironman/hulkbuster/mark48.geo.json"))).getAsJsonObject()
         .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      Set<Long> allowed = new HashSet<>();
      Set<Long> plain = new HashSet<>();
      int emitters = 0;
      for (JsonElement element : geo.getAsJsonArray("bones")) {
         JsonObject bone = element.getAsJsonObject();
         String name = bone.get("name").getAsString();
         if (name.startsWith(HulkbusterAssets.FLAME_PREFIX) || !bone.has("cubes")) {
            // Flames sample the separate repulsor flame texture.
            continue;
         }

         boolean emitter = false;
         Set<String> faces = null;
         for (Map.Entry<String, Set<String>> rule : EMISSIVE.entrySet()) {
            String prefix = rule.getKey();
            if (name.equals(prefix) || prefix.endsWith("_") && name.startsWith(prefix)) {
               emitter = true;
               faces = rule.getValue();
            }
         }

         for (JsonElement c : bone.getAsJsonArray("cubes")) {
            for (Map.Entry<String, double[]> face : rects(c.getAsJsonObject()).entrySet()) {
               boolean glows = emitter && (faces == null || faces.contains(face.getKey()));
               cells(face.getValue(), glows ? allowed : plain);
               emitters += glows ? 1 : 0;
            }
         }
      }

      assertTrue(emitters > 0, "the whitelist matched no face");
      List<String> squares = new ArrayList<>();
      int lit = 0;
      for (int x = 0; x < glow.getWidth(); x++) {
         for (int y = 0; y < glow.getHeight(); y++) {
            int argb = glow.getRGB(x, y);
            boolean on = (argb >>> 24) != 0 && (argb & 0xFFFFFF) != 0;
            if (!on) {
               continue;
            }

            lit++;
            long key = key(x, y);
            if (!allowed.contains(key) || plain.contains(key)) {
               squares.add(x + "," + y);
            }
         }
      }

      assertTrue(lit > 0, "the mask lights nothing: visor and reactor must glow");
      assertTrue(squares.isEmpty(), "lit texels outside emitter faces (glowing squares): " + squares);
   }

   static long key(int x, int y) {
      return (long)x << 32 | (y & 0xFFFFFFFFL);
   }

   static void cells(double[] r, Set<Long> out) {
      int u0 = (int)Math.floor(Math.min(r[0], r[2]));
      int u1 = (int)Math.ceil(Math.max(r[0], r[2]));
      int v0 = (int)Math.floor(Math.min(r[1], r[3]));
      int v1 = (int)Math.ceil(Math.max(r[1], r[3]));
      for (int x = u0; x < u1; x++) {
         for (int y = v0; y < v1; y++) {
            out.add(key(x, y));
         }
      }
   }

   /** Face rects in texture px after the box / per-face path, the east-west swap and the mirror (rig java_rects). */
   static Map<String, double[]> rects(JsonObject cube) {
      JsonArray size = cube.getAsJsonArray("size");
      double sx = size.get(0).getAsDouble(), sy = size.get(1).getAsDouble(), sz = size.get(2).getAsDouble();
      Map<String, double[]> r = new HashMap<>();
      JsonElement uv = cube.get("uv");
      if (uv != null && uv.isJsonObject()) {
         JsonObject faces = uv.getAsJsonObject();
         for (String face : List.of("north", "south", "up", "down", "east", "west")) {
            String source = face.equals("east") ? "west" : face.equals("west") ? "east" : face;
            if (faces.has(source)) {
               JsonObject f = faces.getAsJsonObject(source);
               double u = f.getAsJsonArray("uv").get(0).getAsDouble(), v = f.getAsJsonArray("uv").get(1).getAsDouble();
               double su = 0, sv = 0;
               if (f.has("uv_size")) {
                  su = f.getAsJsonArray("uv_size").get(0).getAsDouble();
                  sv = f.getAsJsonArray("uv_size").get(1).getAsDouble();
               }

               r.put(face, new double[]{u, v, u + su, v + sv});
            }
         }
      } else {
         double u = uv == null ? 0 : uv.getAsJsonArray().get(0).getAsDouble();
         double v = uv == null ? 0 : uv.getAsJsonArray().get(1).getAsDouble();
         r.put("down", new double[]{u + sz, v, u + sz + sx, v + sz});
         r.put("up", new double[]{u + sz + sx, v + sz, u + sz + sx + sx, v});
         r.put("north", new double[]{u + sz, v + sz, u + sz + sx, v + sz + sy});
         r.put("south", new double[]{u + sz + sx + sz, v + sz, u + sz + sx + sz + sx, v + sz + sy});
         r.put("east", new double[]{u, v + sz, u + sz, v + sz + sy});
         r.put("west", new double[]{u + sz + sx, v + sz, u + sz + sx + sz, v + sz + sy});
      }

      if (cube.has("mirror") && cube.get("mirror").getAsBoolean()) {
         Map<String, double[]> m = new HashMap<>();
         for (Map.Entry<String, double[]> e : r.entrySet()) {
            double[] x = e.getValue();
            m.put(e.getKey(), new double[]{x[2], x[1], x[0], x[3]});
         }

         double[] east = m.get("east");
         m.put("east", m.get("west"));
         m.put("west", east);
         m.values().removeIf(java.util.Objects::isNull);
         r = m;
      }

      return r;
   }
}
