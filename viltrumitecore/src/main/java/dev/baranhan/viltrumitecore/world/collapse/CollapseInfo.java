package dev.baranhan.viltrumitecore.world.collapse;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Public, immutable description of one structural collapse: the contract that other systems read from
 * {@link CityCollapseEvent} (infrastructure blackouts, aftermath fires and smoke, restoration, AI danger
 * zones, a future Ultron attack). Documented in {@code docs/city-collapse-integration.md}; fields are
 * append-only.
 *
 * @param eventId        unique per server run (positive, increasing); the same id in every event of one collapse
 * @param dimension      level the collapse happens in
 * @param center         centre of the collapsing section (block position)
 * @param radius         horizontal radius of the danger zone in blocks (section footprint + fall spread)
 * @param severity       size class; {@link CollapseSeverity#strength()} gives 0..1
 * @param cause          what damaged the structure ({@link CollapseCause#id()} is stable)
 * @param affectedBlocks positions changed by the collapse. In {@code Started} the planned section; in
 *                       {@code Collapsed} and {@code Finished} only blocks that were really changed
 *                       (removed section blocks, placed rubble, broken weak blocks), in change order
 * @param originalStates state of every affected position right before the collapse changed it
 *                       (empty in {@code Started})
 * @param timestamp      game time ({@code level.getGameTime()}) of the event
 * @param variant        visual family (append-only enum)
 * @param landing        ground zone where the section lands (impact damage, dust, rubble, aftermath)
 * @param truncated      true when the section was cut to the block limit (the structure keeps the rest)
 */
public record CollapseInfo(long eventId, ResourceKey<Level> dimension, BlockPos center, float radius, CollapseSeverity severity, CollapseCause cause,
   List<BlockPos> affectedBlocks, Map<BlockPos, BlockState> originalStates, long timestamp, CollapseVariant variant, AABB landing, boolean truncated) {
   public CollapseInfo {
      affectedBlocks = List.copyOf(affectedBlocks);
      originalStates = Map.copyOf(originalStates);
   }

   /** Same collapse with another block list, original states and timestamp (later phases). */
   public CollapseInfo with(List<BlockPos> blocks, Map<BlockPos, BlockState> originals, long time) {
      return new CollapseInfo(this.eventId, this.dimension, this.center, this.radius, this.severity, this.cause, blocks, originals, time, this.variant,
         this.landing, this.truncated);
   }

   /** True when a position is inside the danger zone (horizontal radius, any height of the landing box). */
   public boolean inZone(double x, double y, double z) {
      double dx = x - (this.center.getX() + 0.5), dz = z - (this.center.getZ() + 0.5);
      return dx * dx + dz * dz <= (double)this.radius * this.radius || this.landing.contains(x, y, z);
   }
}
