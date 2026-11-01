package dev.baranhan.viltrumitecore.worldevent.aftermath;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * One destruction (or collapse) reported to the aftermath: the shared event contract of the city disaster
 * branches ({@code docs/city-aftermath-integration.md}). The eight fields are the same in every branch:
 * {@code eventId, dimension, center, radius, severity, cause, affectedBlocks, timestamp}. Only vanilla
 * types, so a sender needs nothing but this record, or the same fields in a {@link CompoundTag}
 * ({@link #toTag()} / {@link #fromTag}).
 *
 * @param eventId        unique per event (repeats of one id are merged, not counted twice), e.g. "collapse:1f3a"
 * @param dimension      level id, e.g. "minecraft:overworld"
 * @param center         centre of the damage
 * @param radius         blocks, clamped to 1..48 here
 * @param severity       0..1 (0.2 a broken facade, 0.5 a gutted house, 1 a fallen tower)
 * @param cause          contract id of {@link AftermathCause} ("meteor", "collapse", "infrastructure", ...)
 * @param affectedBlocks positions that were destroyed (any number; a sample of {@link AftermathRules#MAX_SEEDS} is kept)
 * @param timestamp      game time of the event ({@code level.getGameTime()})
 */
public record AftermathEvent(String eventId, String dimension, BlockPos center, float radius, float severity, String cause, List<BlockPos> affectedBlocks,
   long timestamp) {
   public AftermathEvent {
      eventId = eventId == null || eventId.isBlank() ? "anon:" + Long.toHexString(System.nanoTime()) : eventId;
      dimension = dimension == null ? "minecraft:overworld" : dimension;
      center = center == null ? BlockPos.ZERO : center.immutable();
      radius = Float.isFinite(radius) ? Mth.clamp(radius, 1.0F, AftermathRules.MAX_RADIUS) : AftermathRules.MIN_RADIUS;
      severity = Float.isFinite(severity) ? Mth.clamp(severity, 0.0F, 1.0F) : 0.3F;
      cause = AftermathCause.of(cause).id;
      affectedBlocks = affectedBlocks == null ? List.of() : List.copyOf(affectedBlocks);
   }

   /** An event in {@code level} now. */
   public static AftermathEvent of(ServerLevel level, String eventId, BlockPos center, float radius, float severity, AftermathCause cause,
      List<BlockPos> affectedBlocks) {
      return new AftermathEvent(eventId, level.dimension().location().toString(), center, radius, severity, cause.id, affectedBlocks, level.getGameTime());
   }

   public AftermathCause causeType() {
      return AftermathCause.of(this.cause);
   }

   public CompoundTag toTag() {
      CompoundTag t = new CompoundTag();
      t.putString("eventId", this.eventId);
      t.putString("dimension", this.dimension);
      t.putIntArray("center", new int[]{this.center.getX(), this.center.getY(), this.center.getZ()});
      t.putFloat("radius", this.radius);
      t.putFloat("severity", this.severity);
      t.putString("cause", this.cause);
      long[] blocks = new long[this.affectedBlocks.size()];
      for (int i = 0; i < blocks.length; i++) {
         blocks[i] = this.affectedBlocks.get(i).asLong();
      }

      t.put("affectedBlocks", new LongArrayTag(blocks));
      t.putLong("timestamp", this.timestamp);
      return t;
   }

   /** Reads the contract tag; missing fields get safe defaults ({@code center} may also be x/y/z ints). */
   public static AftermathEvent fromTag(CompoundTag t) {
      BlockPos center;
      int[] c = t.getIntArray("center");
      if (c.length >= 3) {
         center = new BlockPos(c[0], c[1], c[2]);
      } else {
         center = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
      }

      List<BlockPos> blocks = new ArrayList<>();
      if (t.contains("affectedBlocks", Tag.TAG_LONG_ARRAY)) {
         for (long l : t.getLongArray("affectedBlocks")) {
            blocks.add(BlockPos.of(l));
         }
      }

      return new AftermathEvent(t.getString("eventId"), t.contains("dimension") ? t.getString("dimension") : null, center,
         t.contains("radius") ? t.getFloat("radius") : AftermathRules.MIN_RADIUS, t.contains("severity") ? t.getFloat("severity") : 0.3F,
         t.getString("cause"), blocks, t.getLong("timestamp"));
   }
}
