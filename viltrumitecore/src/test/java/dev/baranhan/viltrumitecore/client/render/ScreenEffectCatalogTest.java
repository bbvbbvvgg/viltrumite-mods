package dev.baranhan.viltrumitecore.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps the animation skill's screen-effect uniform list in sync with the real dash_impact shader. */
class ScreenEffectCatalogTest {
   private static final Path PROGRAM = Path.of("src/main/resources/assets/minecraft/shaders/program/dash_impact.json");
   private static final Path POST = Path.of("src/main/resources/assets/viltrumitecore/shaders/post/dash_impact.json");
   private static final Path CATALOG = Path.of("../.agents/skills/animation-system/references/system-catalog.md");

   private static List<String> names(String json) {
      List<String> out = new ArrayList<>();
      Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z]+)\"").matcher(json);
      while (m.find()) out.add(m.group(1));
      return out;
   }

   /** The {@code "uniforms"} array only (samplers and attributes also carry names). */
   private static String uniformsArray(String json) {
      int key = json.indexOf("\"uniforms\"");
      assertTrue(key >= 0, "program declares uniforms");
      int open = json.indexOf('[', key), depth = 0;
      for (int i = open; i < json.length(); i++) {
         char c = json.charAt(i);
         if (c == '[') depth++;
         else if (c == ']' && --depth == 0) return json.substring(open, i + 1);
      }
      throw new AssertionError("unterminated uniforms array");
   }

   @Test
   void catalogListsEveryProgramUniformAndPostPassSetsIt() throws Exception {
      List<String> program = names(uniformsArray(Files.readString(PROGRAM)));
      program.removeIf(n -> n.equals("ProjMat") || n.equals("OutSize"));
      assertTrue(program.size() > 10, "program uniforms parsed: " + program);
      String post = Files.readString(POST);
      String catalog = Files.readString(CATALOG);
      Matcher line = Pattern.compile("(?m)^Current uniforms[^:\\n]*:\\s*`([^`]+)`").matcher(catalog);
      assertTrue(line.find(), "system-catalog §9 'Current uniforms' line");
      List<String> listed = List.of(line.group(1).split(",\\s*"));
      assertEquals(program, listed, "system-catalog §9 must list the program uniforms in order");
      for (String uniform : program) assertTrue(post.contains("\"" + uniform + "\""), "post pass sets " + uniform);
   }
}
