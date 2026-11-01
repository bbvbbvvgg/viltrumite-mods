package dev.baranhan.viltrumitecore.world.collapse;

/**
 * Visual and timing family of a collapse, chosen by the analyzer from the damaged geometry. The client
 * adds seeded sub-styles (fragment size, order, tilt), so two collapses of one variant still differ.
 * Ordinals go over the network: append only.
 */
public enum CollapseVariant {
   /** A severed tower top tips over as a few huge slabs and falls toward the damaged side. */
   TOPPLE,
   /** The damaged face shears off: facade slabs peel outward and tumble into the street. */
   FACADE_SHEAR,
   /** Floors above an interior hole pancake down into it, staggered from the bottom up. */
   PANCAKE,
   /** Safe local scenario: loose blocks above the hole crumble; the structure stays. */
   CRUMBLE;

   public static CollapseVariant byOrdinal(int i) {
      CollapseVariant[] v = values();
      return v[Math.max(0, Math.min(v.length - 1, i))];
   }
}
