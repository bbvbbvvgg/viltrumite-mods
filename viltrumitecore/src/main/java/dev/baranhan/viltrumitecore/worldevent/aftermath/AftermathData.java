package dev.baranhan.viltrumitecore.worldevent.aftermath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The aftermath of one level, saved with the world ({@code data/viltrumitecore_aftermath.dat}): the ruin
 * zones and the power outages other systems reported. Ages are game ticks, so a ruin keeps smoking where it
 * left off after a restart and nothing ages while the server is off.
 */
public final class AftermathData extends SavedData {
   public static final String NAME = "viltrumitecore_aftermath";
   static final int MAX_OUTAGES = 64;

   final List<AftermathZone> zones = new ArrayList<>();
   final Map<String, Outage> outages = new LinkedHashMap<>();
   int nextId = 1;

   /** A region without power ({@link CityAftermath#setPower}). */
   public record Outage(String source, double x, double y, double z, float radius) {
      boolean covers(AftermathZone zone) {
         double dx = zone.x - this.x;
         double dy = zone.y - this.y;
         double dz = zone.z - this.z;
         double r = this.radius + zone.radius * 0.5;
         return dx * dx + dy * dy + dz * dz <= r * r;
      }
   }

   public static AftermathData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(AftermathData::load, AftermathData::new, NAME);
   }

   public List<AftermathZone> zones() {
      return this.zones;
   }

   public static AftermathData load(CompoundTag tag) {
      AftermathData d = new AftermathData();
      d.nextId = Math.max(1, tag.getInt("nextId"));
      ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && d.zones.size() < AftermathRules.MAX_ZONES; i++) {
         AftermathZone z = AftermathZone.load(list.getCompound(i));
         d.zones.add(z);
         d.nextId = Math.max(d.nextId, z.id + 1);
      }

      ListTag out = tag.getList("outages", Tag.TAG_COMPOUND);
      for (int i = 0; i < out.size() && d.outages.size() < MAX_OUTAGES; i++) {
         CompoundTag o = out.getCompound(i);
         Outage outage = new Outage(o.getString("source"), o.getDouble("x"), o.getDouble("y"), o.getDouble("z"), o.getFloat("radius"));
         d.outages.put(outage.source(), outage);
      }

      return d;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.putInt("format", 1);
      tag.putInt("nextId", this.nextId);
      ListTag list = new ListTag();
      for (AftermathZone z : this.zones) {
         list.add(z.save());
      }

      tag.put("zones", list);
      ListTag out = new ListTag();
      for (Outage o : this.outages.values()) {
         CompoundTag t = new CompoundTag();
         t.putString("source", o.source());
         t.putDouble("x", o.x());
         t.putDouble("y", o.y());
         t.putDouble("z", o.z());
         t.putFloat("radius", o.radius());
         out.add(t);
      }

      tag.put("outages", out);
      return tag;
   }
}
