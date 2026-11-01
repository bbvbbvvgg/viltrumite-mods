package dev.baranhan.viltrumitecore.worldevent.chitauri.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure sector layout. Inside Atheria every district of the city profile becomes one sector (its real streets);
 * elsewhere rings of {@link WarRules#CELL} cells cover the war radius around the anchor. Only geometry here: the
 * server marks a sector known once its centre column is loaded (no chunk is ever loaded for it).
 */
public final class SectorPlanner {
   /** A district of the city profile, already reduced to what the planner needs. */
   public record District(String name, double minX, double minZ, double maxX, double maxZ) {
   }

   private SectorPlanner() {
   }

   /** Ring cells: one centre sector, then rings with as many cells as fit their circumference. */
   public static List<WarSector> rings(double ax, double az, double radius) {
      List<WarSector> out = new ArrayList<>();
      double r = Math.min(WarRules.MAX_RADIUS, Math.max(WarRules.CELL * 1.5, radius));
      out.add(new WarSector(0, null, ax, az, WarRules.CELL * 0.75, 0));
      int ring = 1;
      for (double rr = WarRules.CELL * 1.25; rr <= r && out.size() < WarRules.MAX_SECTORS; rr += WarRules.CELL, ring++) {
         int n = Math.max(6, (int)Math.round(2.0 * Math.PI * rr / WarRules.CELL));
         double offset = ring % 2 == 0 ? Math.PI / n : 0.0;
         for (int i = 0; i < n && out.size() < WarRules.MAX_SECTORS; i++) {
            double a = offset + 2.0 * Math.PI * i / n;
            out.add(new WarSector(out.size(), null, ax + Math.cos(a) * rr, az + Math.sin(a) * rr, WarRules.CELL * 0.62, ring));
         }
      }
      return out;
   }

   /**
    * City districts as sectors, nearest first (ring = distance band from the anchor). Districts beyond the war radius are
    * left out; when no district is near (anchor outside the city) the ring layout is used instead.
    */
   public static List<WarSector> districts(List<District> districts, double ax, double az, double radius) {
      List<District> near = new ArrayList<>();
      double limit = Math.min(WarRules.MAX_RADIUS, radius) + WarRules.CELL;
      for (District d : districts) {
         double cx = (d.minX + d.maxX) / 2.0;
         double cz = (d.minZ + d.maxZ) / 2.0;
         if (Math.hypot(cx - ax, cz - az) <= limit) {
            near.add(d);
         }
      }
      if (near.size() < 3) {
         return rings(ax, az, radius);
      }
      near.sort(Comparator.comparingDouble(d -> Math.hypot((d.minX + d.maxX) / 2.0 - ax, (d.minZ + d.maxZ) / 2.0 - az)));
      List<WarSector> out = new ArrayList<>();
      for (District d : near) {
         if (out.size() >= WarRules.MAX_SECTORS) {
            break;
         }
         double cx = (d.minX + d.maxX) / 2.0;
         double cz = (d.minZ + d.maxZ) / 2.0;
         double half = Math.max(12.0, Math.min(48.0, Math.min(d.maxX - d.minX, d.maxZ - d.minZ) / 2.0));
         int ring = (int)(Math.hypot(cx - ax, cz - az) / WarRules.CELL);
         out.add(new WarSector(out.size(), d.name(), cx, cz, half, ring));
      }
      return out;
   }

   /** The sector containing a point, else the nearest one within twice its radius, else -1. */
   public static int locate(List<WarSector> sectors, double x, double z) {
      int best = -1;
      double bestD = Double.MAX_VALUE;
      for (WarSector s : sectors) {
         double d = s.distanceSq(x, z);
         if (s.contains(x, z)) {
            d *= 0.25;
         } else if (d > 4.0 * s.radius * s.radius) {
            continue;
         }
         if (d < bestD) {
            bestD = d;
            best = s.id;
         }
      }
      return best;
   }
}
