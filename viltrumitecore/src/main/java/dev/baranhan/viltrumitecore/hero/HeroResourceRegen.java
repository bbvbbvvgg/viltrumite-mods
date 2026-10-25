package dev.baranhan.viltrumitecore.hero;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Resource regen seam: how fast a hero's own resource refills (Iron Man energy,
 * Homelander eye-heat cooling) when other players slow it
 * ({@link HeroDefinition#foreignResourceRegenFactor}). The strongest source wins
 * (minimum), so two drainers do not stack. Spend and drain are never scaled.
 *
 * <p>Every drained pair is also an owner-section link ({@link OwnerSection#MANA_LINKS}):
 * the drained player gets the drainers' ids and each drainer the drained
 * players' ids, so only those two clients draw the feedback. Server only.
 */
public final class HeroResourceRegen {
   /** No source reaches farther than this (blocks). */
   public static final double MAX_RADIUS = 64.0;
   private static final Map<UUID, Cached> CACHE = new HashMap<>();
   /** Links of the current tick: player → linked entity ids. */
   private static final Map<UUID, Set<Integer>> LINKS = new HashMap<>();

   private HeroResourceRegen() {
   }

   /** Pure: the strongest (lowest) factor, clamped to 0..1; none = 1. */
   public static float combine(float[] factors) {
      float scale = 1.0F;
      for (float factor : factors) {
         scale = Math.min(scale, factor);
      }

      return Math.max(0.0F, Math.min(1.0F, scale));
   }

   /** Regen scale of a player this tick (cached per tick). */
   public static float scale(ServerPlayer player) {
      long now = player.level().getGameTime();
      Cached cached = CACHE.get(player.getUUID());
      if (cached != null && cached.tick == now) {
         return cached.scale;
      }

      float scale = 1.0F;
      double maxSqr = MAX_RADIUS * MAX_RADIUS;
      for (ServerPlayer other : player.serverLevel().players()) {
         if (other == player || !other.isAlive() || other.isSpectator() || other.distanceToSqr(player) > maxSqr) {
            continue;
         }

         float factor = HeroRegistry.get(other).foreignResourceRegenFactor(other, player);
         if (factor < 1.0F) {
            scale = Math.min(scale, factor);
            LINKS.computeIfAbsent(player.getUUID(), id -> new HashSet<>()).add(other.getId());
            LINKS.computeIfAbsent(other.getUUID(), id -> new HashSet<>()).add(player.getId());
         }
      }

      scale = combine(new float[]{scale});
      CACHE.put(player.getUUID(), new Cached(now, scale));
      return scale;
   }

   /** End of a level tick: this tick's links to the owner sections (dedup by pushOwnerSection), then forget them. */
   public static void flush(ServerLevel level) {
      for (ServerPlayer player : level.players()) {
         Set<Integer> links = LINKS.remove(player.getUUID());
         int[] ids = links == null ? new int[0] : links.stream().mapToInt(Integer::intValue).sorted().toArray();
         HeroRegistry.pushOwnerSection(player, OwnerSection.MANA_LINKS, HeroOwnerSnapshot.Section.of(ids));
      }
   }

   public static void forget(ServerPlayer player) {
      CACHE.remove(player.getUUID());
      LINKS.remove(player.getUUID());
   }

   private record Cached(long tick, float scale) {
   }
}
