package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.city.power.api.AlarmStage;
import dev.baranhan.viltrumitecore.city.power.api.InfrastructureEvent;
import dev.baranhan.viltrumitecore.city.profile.CityProfile;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/**
 * The city emergency (Living City 3.0): leases from threats (an invasion, a meteor shower, heavy damage, a
 * cascade, an add-on) hold the alarm at a stage until they expire; the strongest one sets the sirens. Stages
 * rise at once and fall through an all-clear (see {@link PowerRules#resolve}). Server side only: clients get
 * the stage, the siren masts and the foci in the grid's state packet and play the sirens themselves.
 */
public final class CityAlarm {
   public static final int MAX_LEASES = 32;
   public static final int MAX_SITES = 12;
   public static final int MAX_FOCI = 4;
   /** Ticks before the same alarm line may be shown again. */
   static final int LINE_COOLDOWN = 400;

   public record Lease(String source, AlarmStage stage, Vec3 at, float radius, long until) {
   }

   private final PowerGrid grid;
   private final LinkedHashMap<String, Lease> leases = new LinkedHashMap<>();
   private final Map<String, Long> lastLine = new HashMap<>();
   private AlarmStage stage = AlarmStage.NONE;
   private long stageSince;
   private String reason = "";
   private List<BlockPos> sites = List.of();
   private long sitesAt = -1;

   CityAlarm(PowerGrid grid) {
      this.grid = grid;
   }

   public AlarmStage stage() {
      return this.stage;
   }

   public long stageSince() {
      return this.stageSince;
   }

   public String reason() {
      return this.reason;
   }

   public List<Lease> leases() {
      return new ArrayList<>(this.leases.values());
   }

   public void lease(String source, AlarmStage stage, Vec3 at, float radius, int ticks) {
      if (stage == AlarmStage.NONE || stage == AlarmStage.ALL_CLEAR) {
         this.release(source);
         return;
      }
      long until = this.grid.now() + Math.max(20, Math.min(72_000, ticks));
      Lease old = this.leases.remove(source);
      if (old != null && old.stage().urgency() > stage.urgency() && old.until() > until) {
         // a weaker refresh never shortens a stronger lease of the same source
         this.leases.put(source, old);
         return;
      }
      if (this.leases.size() >= MAX_LEASES) {
         Iterator<String> it = this.leases.keySet().iterator();
         it.next();
         it.remove();
      }
      this.leases.put(source, new Lease(source, stage, at, radius, until));
   }

   public void release(String source) {
      this.leases.remove(source);
   }

   /** Ends every lease and silences the city at once (the city reset, an operator). */
   void clear() {
      this.leases.clear();
      if (this.stage != AlarmStage.NONE) {
         this.change(AlarmStage.NONE, "");
      }
   }

   void tick(long now) {
      Lease top = null;
      for (Iterator<Lease> it = this.leases.values().iterator(); it.hasNext();) {
         Lease l = it.next();
         if (l.until() <= now) {
            it.remove();
         } else if (top == null || l.stage().urgency() >= top.stage().urgency()) {
            top = l;
         }
      }
      AlarmStage next = PowerRules.resolve(top == null ? AlarmStage.NONE : top.stage(), this.stage, (int)Math.min(Integer.MAX_VALUE, now - this.stageSince));
      if (next != this.stage) {
         this.change(next, top == null ? this.reason : top.source());
      } else if (top != null && !top.source().equals(this.reason)) {
         this.reason = top.source();
         this.grid.markDirty();
      }
   }

   private void change(AlarmStage next, String why) {
      AlarmStage prev = this.stage;
      this.stage = next;
      this.stageSince = this.grid.now();
      this.reason = why;
      if (prev == AlarmStage.NONE && next != AlarmStage.NONE) {
         this.refreshSites();
      }
      this.grid.markDirty();
      this.grid.onAlarmChanged(prev, next);
      this.announce(prev, next);
      MinecraftForge.EVENT_BUS.post(new InfrastructureEvent.AlarmChanged(this.grid.level, prev, next, why));
   }

   /** warning / active lines name the threat: meteors, the Chitauri or a general emergency. */
   static String cause(String reason) {
      if (reason.contains("chitauri")) {
         return "chitauri";
      }
      if (reason.contains("meteor")) {
         return "meteor";
      }
      return "generic";
   }

   private void announce(AlarmStage prev, AlarmStage next) {
      String line = switch (next) {
         case WARNING -> prev.urgency() < AlarmStage.WARNING.urgency() ? "warning." + cause(this.reason) : null;
         case ACTIVE -> prev.urgency() < AlarmStage.ACTIVE.urgency() ? "active." + cause(this.reason) : null;
         case CRITICAL -> "critical";
         case ALL_CLEAR -> "all_clear";
         default -> null;
      };
      if (line == null) {
         return;
      }
      long now = this.grid.now();
      Long last = this.lastLine.get(line);
      if (last != null && now - last < LINE_COOLDOWN) {
         return;
      }
      this.lastLine.put(line, now);
      this.grid.say("alarm." + line, next == AlarmStage.CRITICAL || next == AlarmStage.ACTIVE);
   }

   // ------------------------------------------------------------------ sirens

   /** Siren masts: one per district (the largest first), on the roofs around its middle. */
   public List<BlockPos> sites() {
      if (this.sitesAt < 0) {
         this.refreshSites();
      }
      return this.sites;
   }

   private void refreshSites() {
      this.sitesAt = this.grid.now();
      CityProfile p = this.grid.city.profile();
      List<double[]> centres = new ArrayList<>();
      List<CityProfile.District> districts = new ArrayList<>(p.districts);
      districts.sort(Comparator.comparingLong(d -> -(long)(d.maxX() - d.minX()) * (d.maxZ() - d.minZ())));
      for (CityProfile.District d : districts) {
         if (centres.size() >= MAX_SITES) {
            break;
         }
         centres.add(new double[]{(d.minX() + d.maxX()) / 2.0, (d.minZ() + d.maxZ()) / 2.0});
      }
      if (centres.isEmpty()) {
         double cx = (p.minX + p.maxX) / 2.0, cz = (p.minZ + p.maxZ) / 2.0;
         double qx = (p.maxX - p.minX) / 4.0, qz = (p.maxZ - p.minZ) / 4.0;
         centres.add(new double[]{cx, cz});
         if (qx > 120 || qz > 120) {
            centres.add(new double[]{cx - qx, cz - qz});
            centres.add(new double[]{cx + qx, cz - qz});
            centres.add(new double[]{cx - qx, cz + qz});
            centres.add(new double[]{cx + qx, cz + qz});
         }
      }
      List<BlockPos> out = new ArrayList<>();
      for (double[] c : centres) {
         int x = (int)Math.floor(c[0]), z = (int)Math.floor(c[1]);
         int y;
         if (this.grid.level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
            y = this.grid.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 8;
         } else {
            int feet = this.grid.city.grid().feetY(x, z);
            y = feet != WalkGrid.NONE ? feet + 24 : p.minY + 90;
         }
         out.add(new BlockPos(x, Math.min(p.maxY + 16, y), z));
      }
      this.sites = List.copyOf(out);
   }

   /** The strongest leases with a place (klaxons at a portal, around a cascade), at most {@link #MAX_FOCI}. */
   List<Lease> foci() {
      List<Lease> all = new ArrayList<>(this.leases.values());
      all.sort(Comparator.comparingInt((Lease l) -> -l.stage().urgency()).thenComparingLong(l -> -l.until()));
      return all.size() > MAX_FOCI ? all.subList(0, MAX_FOCI) : all;
   }
}
