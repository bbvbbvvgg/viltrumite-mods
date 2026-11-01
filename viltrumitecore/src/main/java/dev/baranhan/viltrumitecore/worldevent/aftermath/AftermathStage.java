package dev.baranhan.viltrumitecore.worldevent.aftermath;

/**
 * Life of a ruin, counted from its last hit (a new hit sends it back to {@link #IMPACT}).
 * <ul>
 * <li>{@link #IMPACT}: right after the destruction: a dust cloud, sparks, falling crumbs, thick smoke, hot glow.</li>
 * <li>{@link #SMOLDER}: the dust settles, smoke stays in places, spots smoulder, the ruin crackles and crumbles.</li>
 * <li>{@link #LATE}: the ruin stands, smoke is nearly gone, citizens come back.</li>
 * <li>{@link #SCAR}: only the wind in the ruins and a rare pebble; nothing dangerous any more.</li>
 * <li>{@link #GONE}: the zone is dropped (the broken blocks stay until {@code /city reset}).</li>
 * </ul>
 */
public enum AftermathStage {
   IMPACT,
   SMOLDER,
   LATE,
   SCAR,
   GONE;

   public final String id = this.name().toLowerCase(java.util.Locale.ROOT);
}
