package dev.baranhan.viltrumitecore.client.reinhard.saw;

import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawChoreography;
import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawShell;
import dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** SAW_PREVIEW=1: exports the Saw Sphere geometry (camera-relative quads of both passes) for
 * tools/preview_saw_sphere.py. A CPU look at the shapes and colours, not an in-game check. */
class SawSpherePreviewTest {
   static final double CX = 0.5, Y0 = 64, CZ = 0.5;

   /** Flat ground at y0, a stone block house cutting the shell on +x and a low wall on -z. */
   static int occupancy(int ix, int iy, int iz) {
      double x = (ix + .5) * SawShell.CELL, y = (iy + .5) * SawShell.CELL, z = (iz + .5) * SawShell.CELL;
      if (y < Y0) return SawShell.FULL;
      if (x > 8 && x < 15 && z > -4 && z < 3 && y < Y0 + 6) return SawShell.FULL;
      if (z < -10 && z > -11.5 && x > -6 && x < 4 && y < Y0 + 2) return SawShell.FULL;
      return SawShell.FREE;
   }

   static SawSphereClient.Scene scene() {
      SawShell shell = SawShell.build(CX, Y0, CZ, SawChoreography.RADIUS, SawSpherePreviewTest::occupancy);
      return new SawSphereClient.Scene(new SawSphereS2CPacket(7, CX, Y0, CZ, 30F, 0, shell.cellCount(), shell.mask()));
   }

   @Test void export() throws Exception {
      var scene = scene();
      // the mesh always builds and has active quads
      var mesh = SawSphereRenderer.mesh(scene);
      int active = 0;
      for (byte q : mesh.quad) if (q != 0) active++;
      assertTrue(active > SawSphereRenderer.LAT * SawSphereRenderer.LON / 2, "most of the dome is active");
      if (System.getenv("SAW_PREVIEW") == null) return;
      Path dir = Path.of("build/diagnostics/saw-sphere");
      Files.createDirectories(dir);
      Vec3[] eyes = {new Vec3(CX - 26, Y0 + 7, CZ - 18), new Vec3(CX - 4, Y0 + 3.5, CZ + 3)};
      String[] names = {"out", "in"};
      for (int e = 0; e < eyes.length; e++)
         for (double t : new double[]{6, 14, 24, 60, 130, 233, 240, 247, 250, 255}) {
            StringBuilder sb = new StringBuilder();
            Vec3 eye = eyes[e];
            sb.append(String.format(Locale.ROOT, "{\"eye\":[%.3f,%.3f,%.3f],\"target\":[%.3f,%.3f,%.3f],\"fill\":[", eye.x, eye.y, eye.z, CX, Y0 + 4, CZ));
            StringBuilder glow = new StringBuilder();
            SawSphereRenderer.Out fill = rec(sb), add = rec(glow);
            SawSphereRenderer.drawScene(fill, add, eye, scene, t, false);
            trim(sb);
            sb.append("],\"glow\":[").append(glow);
            trim(sb);
            sb.append("]}");
            Files.writeString(dir.resolve(String.format(Locale.ROOT, "%s_%05.1f.json", names[e], t)), sb.toString());
         }
   }

   private static SawSphereRenderer.Out rec(StringBuilder sb) {
      return (x, y, z, r, g, b, a) -> sb.append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%d,%d,%d,%d,", x, y, z, r, g, b, a));
   }

   private static void trim(StringBuilder sb) {
      if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
   }
}
