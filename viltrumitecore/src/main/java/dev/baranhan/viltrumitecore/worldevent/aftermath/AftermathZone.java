package dev.baranhan.viltrumitecore.worldevent.aftermath;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * One ruined place on the server: a sphere, how bad it is, what did it, when it was last hit and a small
 * sample of the destroyed block positions ("seeds") the clients grow their smoke, dust and crumbs from.
 * Compact on purpose: a few dozen numbers, at most {@link AftermathRules#MAX_SEEDS} longs.
 */
public final class AftermathZone {
   private static final int MAX_EVENT_IDS = 8;

   public final int id;
   public double x;
   public double y;
   public double z;
   public float radius;
   /** Severity from destroyed block counts and declared events, 0..1. */
   public float severity;
   /** Highest severity a declared event gave (never lowered by merging small bursts). */
   public float declared;
   public int blocks;
   /** {@link AftermathCause} bits. */
   public int causes;
   /** {@link AftermathRules} FLAG_* bits. */
   public int flags;
   public long created;
   public long lastHit;
   /** Bumped on every change the clients must hear about. */
   public int version;
   public long[] seeds = new long[0];
   int seen;
   /** Focus box of a declared collapse (minX, minY, minZ, maxX, maxY, maxZ) or null. */
   public int[] focus;
   final Deque<String> eventIds = new ArrayDeque<>();

   public AftermathZone(int id, double x, double y, double z, float radius, long now) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.z = z;
      this.radius = Mth.clamp(radius, AftermathRules.MIN_RADIUS, AftermathRules.MAX_RADIUS);
      this.created = now;
      this.lastHit = now;
   }

   public long age(long now) {
      return Math.max(0L, now - this.lastHit);
   }

   public float factor(double scale) {
      return AftermathRules.durationFactor(this.severity, scale);
   }

   public AftermathStage stage(long now, double scale) {
      return AftermathRules.stage(this.age(now), this.factor(scale));
   }

   public double distanceSq(double px, double py, double pz) {
      double dx = px - this.x;
      double dy = py - this.y;
      double dz = pz - this.z;
      return dx * dx + dy * dy + dz * dz;
   }

   /** Remembers an event id; false when it was already seen (a repeat of the same event). */
   boolean addEventId(String eventId) {
      if (eventId == null || this.eventIds.contains(eventId)) {
         return eventId == null;
      }

      this.eventIds.addLast(eventId);
      while (this.eventIds.size() > MAX_EVENT_IDS) {
         this.eventIds.removeFirst();
      }

      return true;
   }

   public boolean hasEvent(String eventId) {
      return this.eventIds.contains(eventId);
   }

   /** Reservoir sample of destroyed positions; a hit after a quiet spell favours its own blocks. */
   void addSeed(long pos, RandomSource random) {
      this.seen++;
      if (this.seeds.length < AftermathRules.MAX_SEEDS) {
         this.seeds = Arrays.copyOf(this.seeds, this.seeds.length + 1);
         this.seeds[this.seeds.length - 1] = pos;
      } else {
         int j = random.nextInt(this.seen);
         if (j < this.seeds.length) {
            this.seeds[j] = pos;
         }
      }
   }

   void freshHit() {
      // the reservoir forgets most of the old weight: a new wave of damage shows up at once
      this.seen = Math.min(this.seen, AftermathRules.MAX_SEEDS);
   }

   /** Grows the sphere to cover another one (bounded by {@link AftermathRules#MAX_RADIUS}). */
   void cover(double ox, double oy, double oz, float r) {
      double d = Math.sqrt(this.distanceSq(ox, oy, oz));
      float[] b = AftermathRules.bound(d, this.radius, r);
      if (b[1] > 0.0F && d > 1.0E-4) {
         double k = b[1] / d;
         this.x += (ox - this.x) * k;
         this.y += (oy - this.y) * k;
         this.z += (oz - this.z) * k;
      }

      this.radius = Mth.clamp(b[0], AftermathRules.MIN_RADIUS, AftermathRules.MAX_RADIUS);
   }

   // ------------------------------------------------------------------ save

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putInt("id", this.id);
      t.putDouble("x", this.x);
      t.putDouble("y", this.y);
      t.putDouble("z", this.z);
      t.putFloat("radius", this.radius);
      t.putFloat("severity", this.severity);
      t.putFloat("declared", this.declared);
      t.putInt("blocks", this.blocks);
      t.putInt("causes", this.causes);
      t.putInt("flags", this.flags);
      t.putLong("created", this.created);
      t.putLong("lastHit", this.lastHit);
      t.putLongArray("seeds", this.seeds);
      t.putInt("seen", this.seen);
      if (this.focus != null) {
         t.putIntArray("focus", this.focus);
      }

      ListTag ids = new ListTag();
      for (String e : this.eventIds) {
         ids.add(StringTag.valueOf(e));
      }

      t.put("events", ids);
      return t;
   }

   public static AftermathZone load(CompoundTag t) {
      AftermathZone z = new AftermathZone(t.getInt("id"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("radius"), t.getLong("created"));
      z.severity = Mth.clamp(t.getFloat("severity"), 0.0F, 1.0F);
      z.declared = Mth.clamp(t.getFloat("declared"), 0.0F, 1.0F);
      z.blocks = Math.max(0, t.getInt("blocks"));
      z.causes = t.getInt("causes");
      z.flags = t.getInt("flags");
      z.lastHit = t.getLong("lastHit");
      long[] seeds = t.getLongArray("seeds");
      z.seeds = seeds.length > AftermathRules.MAX_SEEDS ? Arrays.copyOf(seeds, AftermathRules.MAX_SEEDS) : seeds;
      z.seen = Math.max(z.seeds.length, t.getInt("seen"));
      if (t.contains("focus", Tag.TAG_INT_ARRAY) && t.getIntArray("focus").length == 6) {
         z.focus = t.getIntArray("focus");
      }

      ListTag ids = t.getList("events", Tag.TAG_STRING);
      for (int i = 0; i < ids.size(); i++) {
         z.addEventId(ids.getString(i));
      }

      return z;
   }

   // ------------------------------------------------------------------ network

   public Snapshot snapshot(long now, double scale) {
      return new Snapshot(this.id, this.x, this.y, this.z, this.radius, this.severity, this.factor(scale), this.age(now), this.causes, this.flags,
         this.focus, this.seeds);
   }

   /**
    * What a client knows of a zone. {@code age} is the zone age when sent; the client keeps counting.
    * {@code factor} is the server's duration factor (config scale included), so both sides agree on stages.
    */
   public record Snapshot(int id, double x, double y, double z, float radius, float severity, float factor, long age, int causes, int flags,
      int[] focus, long[] seeds) {
      public void write(FriendlyByteBuf buf) {
         buf.writeVarInt(this.id);
         buf.writeDouble(this.x);
         buf.writeDouble(this.y);
         buf.writeDouble(this.z);
         buf.writeFloat(this.radius);
         buf.writeFloat(this.severity);
         buf.writeFloat(this.factor);
         buf.writeVarLong(Math.max(0L, this.age));
         buf.writeVarInt(this.causes);
         buf.writeVarInt(this.flags);
         buf.writeBoolean(this.focus != null);
         if (this.focus != null) {
            for (int i = 0; i < 6; i++) {
               buf.writeInt(this.focus[i]);
            }
         }

         int n = Math.min(AftermathRules.MAX_SEEDS, this.seeds.length);
         buf.writeVarInt(n);
         for (int i = 0; i < n; i++) {
            buf.writeLong(this.seeds[i]);
         }
      }

      public static Snapshot read(FriendlyByteBuf buf) {
         int id = buf.readVarInt();
         double x = buf.readDouble();
         double y = buf.readDouble();
         double z = buf.readDouble();
         float radius = Mth.clamp(buf.readFloat(), 1.0F, AftermathRules.MAX_RADIUS);
         float severity = Mth.clamp(buf.readFloat(), 0.0F, 1.0F);
         float factor = Mth.clamp(buf.readFloat(), 0.05F, 20.0F);
         long age = buf.readVarLong();
         int causes = buf.readVarInt();
         int flags = buf.readVarInt();
         int[] focus = null;
         if (buf.readBoolean()) {
            focus = new int[6];
            for (int i = 0; i < 6; i++) {
               focus[i] = buf.readInt();
            }
         }

         int n = Math.min(AftermathRules.MAX_SEEDS, Math.max(0, buf.readVarInt()));
         long[] seeds = new long[n];
         for (int i = 0; i < n; i++) {
            seeds[i] = buf.readLong();
         }

         return new Snapshot(id, x, y, z, radius, severity, factor, age, causes, flags, focus, seeds);
      }
   }
}
