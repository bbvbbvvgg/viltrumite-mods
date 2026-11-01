package dev.baranhan.viltrumitecore.city.power.api;

import net.minecraft.core.BlockPos;

/**
 * Power state of one district: {@code darkCells} of its {@code cells} grid cells are without power
 * ({@code damagedCells} of them with a blown transformer that takes longer to repair); {@code center} is the
 * middle of the dark area (or of the district when it is fully powered).
 */
public record OutageInfo(String district, int cells, int darkCells, int damagedCells, BlockPos center) {
   public boolean dark() {
      return this.darkCells > 0;
   }

   public float darkFraction() {
      return this.cells <= 0 ? 0.0F : (float)this.darkCells / this.cells;
   }
}
