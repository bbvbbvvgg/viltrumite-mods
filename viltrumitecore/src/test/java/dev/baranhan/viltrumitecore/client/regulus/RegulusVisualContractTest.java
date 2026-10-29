package dev.baranhan.viltrumitecore.client.regulus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * File-level contract for the Regulus presentation layer (plan Task 6):
 * shader uniform parity, mixin registration, HUD/ability texture existence
 * and dims, and lang key parity across all 12 locale files.
 */
class RegulusVisualContractTest {

   @Test
   void domePixelsHaveVerticalExtentRatherThanFloatingFloorPanels() {
      com.mojang.blaze3d.vertex.BufferBuilder builder = new com.mojang.blaze3d.vertex.BufferBuilder(8192);
      builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
      dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx.domeShell(builder, net.minecraft.world.phys.Vec3.ZERO,
         new net.minecraft.client.Camera(), net.minecraft.world.phys.Vec3.ZERO, 4.0F, 0.0F, 255, 255, 255, 100);
      com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer rendered = builder.end();
      java.nio.ByteBuffer vertices = rendered.vertexBuffer().order(java.nio.ByteOrder.nativeOrder());
      try {
         float bottom = vertices.getFloat(4);
         float top = vertices.getFloat(2 * 16 + 4);
         assertTrue(Math.abs(top - bottom) > 0.1F, "floating pixels must face the camera, not lie on XZ");
      } finally {
         rendered.release();
      }
   }

   private static final Path MAIN = Path.of("src/main");

   @Test
   void cameraRotationTransformsWorldOffsetsOnce() {
      com.mojang.blaze3d.vertex.PoseStack stack = new com.mojang.blaze3d.vertex.PoseStack();
      dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx.rotateCamera(stack, 0.0F, 0.0F);
      org.joml.Vector3f point = stack.last().pose().transformPosition(new org.joml.Vector3f(2.0F, 3.0F, 4.0F));
      assertEquals(-2.0F, point.x, 0.00001F);
      assertEquals(3.0F, point.y, 0.00001F);
      assertEquals(-4.0F, point.z, 0.00001F);
      stack = new com.mojang.blaze3d.vertex.PoseStack();
      dev.baranhan.viltrumitecore.client.render.vfx.PixelVfx.rotateCamera(stack, 90.0F, 0.0F);
      point = stack.last().pose().transformPosition(new org.joml.Vector3f(0.0F, 1.0F, 0.0F));
      assertEquals(0.0F, point.y, 0.00001F);
      assertEquals(1.0F, point.z, 0.00001F);
   }

   private static String resource(String path) {
      InputStream stream = RegulusVisualContractTest.class.getResourceAsStream("/" + path);
      assertNotNull(stream, "missing classpath resource: " + path);
      try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
         StringBuilder builder = new StringBuilder();
         char[] chunk = new char[4096];
         int read;
         while ((read = reader.read(chunk)) > 0) {
            builder.append(chunk, 0, read);
         }
         return builder.toString();
      } catch (IOException exception) {
         return fail("cannot read resource " + path + ": " + exception);
      }
   }

   private static JsonObject resourceJson(String path) {
      return JsonParser.parseString(resource(path)).getAsJsonObject();
   }

   private static Set<String> programUniformNames(JsonObject program) {
      Set<String> names = new TreeSet<>();
      for (JsonElement element : program.getAsJsonArray("uniforms")) {
         String name = element.getAsJsonObject().get("name").getAsString();
         // ProjMat/OutSize are engine builtins — not part of the effect contract.
         if (!"ProjMat".equals(name) && !"OutSize".equals(name)) {
            names.add(name);
         }
      }
      return names;
   }

   private static Set<String> postPassUniformNames(JsonObject post) {
      Set<String> names = new TreeSet<>();
      for (JsonElement pass : post.getAsJsonArray("passes")) {
         JsonArray uniforms = pass.getAsJsonObject().getAsJsonArray("uniforms");
         if (uniforms != null) {
            for (JsonElement uniform : uniforms) {
               names.add(uniform.getAsJsonObject().get("name").getAsString());
            }
         }
      }
      return names;
   }

   private static Set<String> fshUniformNames(String fsh) {
      Set<String> names = new TreeSet<>();
      Matcher matcher = Pattern.compile("uniform\\s+(?:float|int|vec\\d|sampler2D)\\s+(\\w+)\\s*;").matcher(fsh);
      while (matcher.find()) {
         String name = matcher.group(1);
         if (!"DiffuseSampler".equals(name) && !"PrevSampler".equals(name)) {
            names.add(name);
         }
      }
      return names;
   }

   @Test
   void dashImpactUniformsStayInParity() throws IOException {
      Set<String> program = programUniformNames(
         resourceJson("assets/minecraft/shaders/program/dash_impact.json")
      );
      Set<String> postPass = postPassUniformNames(
         resourceJson("assets/viltrumitecore/shaders/post/dash_impact.json")
      );
      Set<String> fsh = fshUniformNames(
         resource("assets/minecraft/shaders/program/dash_impact.fsh")
      );

      Set<String> mixin = new TreeSet<>();
      String source = Files.readString(
         MAIN.resolve("java/dev/baranhan/viltrumitecore/client/mixin/GameRendererDashMixin.java")
      );
      Matcher matcher = Pattern.compile("getUniform\\(\"(\\w+)\"\\)").matcher(source);
      while (matcher.find()) {
         mixin.add(matcher.group(1));
      }

      assertEquals(postPass, program, "program/post uniform sets diverged");
      assertEquals(postPass, fsh, "fragment shader must declare every pass uniform");
      assertEquals(postPass, mixin, "GameRendererDashMixin must set every pass uniform");
      // The Regulus screen-FX trio must be part of the contract (spec 14).
      assertTrue(postPass.contains("RegulusOverheat"), "missing RegulusOverheat uniform");
      assertTrue(postPass.contains("RegulusHeartFlash"), "missing RegulusHeartFlash uniform");
      assertTrue(postPass.contains("RegulusMadness"), "missing RegulusMadness uniform");
   }

   @Test
   void regulusMixinsAreRegistered() {
      JsonObject mixins = resourceJson("viltrumitecore.client.mixins.json");
      Set<String> registered = new HashSet<>();
      for (JsonElement element : mixins.getAsJsonArray("client")) {
         registered.add(element.getAsString());
      }

      List<String> expected = List.of(
         "RegulusMadnessModelMixin",
         "RegulusRitualModelMixin",
         "RegulusLionModelMixin",
         "RegulusManiaModelMixin",
         "RegulusEmbraceModelMixin",
         "RegulusKickModelMixin",
         "RegulusCounterModelMixin",
         "RegulusKickRendererCoreMixin",
         "RegulusCounterRendererCoreMixin",
         "FirstPersonRegulusLionMixin",
         "FirstPersonRegulusKickMixin",
         "FirstPersonRegulusManiaMixin",
         "FirstPersonRegulusEmbraceMixin",
         "FirstPersonRegulusCounterMixin",
         "FirstPersonRegulusRitualMixin",
         "RegulusHudMixin",
         "RegulusFovMixin"
      );
      for (String name : expected) {
         assertTrue(registered.contains(name), "viltrumitecore.client.mixins.json missing " + name);
      }
      assertFalse(registered.contains("RegulusModelMixin"), "old all-in-one Regulus model mixin must be gone");
      assertFalse(registered.contains("FirstPersonRegulusMixin"), "old all-in-one Regulus first-person mixin must be gone");
   }

   @Test
   void regulusHudTexturesExist() throws IOException {
      assertTextureSize("assets/viltrumitecore/textures/gui/hero/regulus_heart.png", 9, 9);
      assertTextureSize("assets/viltrumitecore/textures/gui/hero/regulus_lion_bar.png", 64, 9);
      assertTextureSize("assets/viltrumitecore/textures/gui/hero/regulus_blood_0.png", 96, 96);
      assertTextureSize("assets/viltrumitecore/textures/gui/hero/regulus_blood_1.png", 96, 96);
      assertTextureSize("assets/viltrumitecore/textures/gui/hero/regulus_drop.png", 9, 9);
   }

   /** House-style icons (tools/assets/make_hero_icons.py): 32x32 art saved 2x, like every hero's panel icons. */
   @Test
   void regulusAbilityIconsAre64x64() throws IOException {
      String[] names = {"lions_heart", "debris_kick", "mania", "greeds_embrace", "counter", "punch"};
      for (String name : names) {
         assertTextureSize("assets/viltrumitecore/textures/gui/ability/regulus/" + name + ".png", 64, 64);
      }
   }

   private static void assertTextureSize(String path, int width, int height) throws IOException {
      InputStream stream = RegulusVisualContractTest.class.getResourceAsStream("/" + path);
      assertNotNull(stream, "missing texture: " + path);
      java.awt.image.BufferedImage image = ImageIO.read(stream);
      assertNotNull(image, "unreadable texture: " + path);
      assertEquals(width, image.getWidth(), path + " width");
      assertEquals(height, image.getHeight(), path + " height");
   }

   @Test
   void allLangFilesCarryRegulusKeys() throws IOException {
      Path langDir = Path.of("src/main/resources/assets/viltrumitecore/lang");
      Path[] files;
      try (var listing = Files.list(langDir)) {
         files = listing.filter(path -> path.toString().endsWith(".json")).sorted().toArray(Path[]::new);
      }
      assertEquals(12, files.length, "expected 12 lang files");

      String[] requiredKeys = {
         "ability.viltrumitecore.lions_heart.name",
         "ability.viltrumitecore.debris_kick.name",
         "ability.viltrumitecore.mania.name",
         "ability.viltrumitecore.greeds_embrace.name",
         "ability.viltrumitecore.counter.name",
         "item.viltrumitecore.evangelium",
         "message.viltrumitecore.counter.no_target"
      };
      for (Path file : files) {
         JsonObject lang = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
         for (String key : requiredKeys) {
            assertTrue(lang.has(key), file.getFileName() + " missing lang key " + key);
         }
      }
   }
}
