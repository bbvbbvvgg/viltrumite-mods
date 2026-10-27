package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * §11: choosing Thor. A powerful lightning strike on the player (visual: no
 * fire, no damage, nothing broken), a short protected scene (~1–2 s) that
 * holds the scene lock, then control returns even if the hammer still
 * flies. Underground (№214) the bolt carves its way down to the player,
 * never through protected blocks ([ОТКРЫТО] protected roof: it stops there).
 * An existing hammer is called mid-scene: the arm goes out and the real
 * Mjölnir flies in.
 */
public final class ThorTransform {
   public static final String SCENE = "A:transform";
   private static final Map<UUID, Integer> ELAPSED = new HashMap<>();

   private ThorTransform() {
   }

   public static synchronized void begin(ServerPlayer player, ThorState state) {
      ELAPSED.put(player.getUUID(), 0);
      ThorBridge.lockScene(state, SCENE);
      ServerLevel level = player.serverLevel();
      BlockPos head = BlockPos.containing(player.getEyePosition());
      if (underground(level, head)) {
         carve(level, head);
      }

      LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
      if (bolt != null) {
         bolt.moveTo(player.getX(), player.getY(), player.getZ());
         bolt.setVisualOnly(true);
         level.addFreshEntity(bolt);
      }
   }

   /** Damage immunity during the scene (ThorHero.clampFinalDamage). */
   public static synchronized boolean shielded(ServerPlayer player) {
      return ELAPSED.containsKey(player.getUUID());
   }

   public static void tick(ServerPlayer player, ThorState state) {
      Integer elapsed;
      synchronized (ThorTransform.class) {
         elapsed = ELAPSED.get(player.getUUID());
         if (elapsed == null) {
            return;
         }

         ELAPSED.put(player.getUUID(), elapsed + 1);
      }

      if (elapsed + 1 == MjolnirTuning.TRANSFORM_RECALL_AT) {
         MjolnirWorld.recall(player);
      }

      if (elapsed + 1 >= MjolnirTuning.TRANSFORM_TICKS) {
         stop(player, state);
      }
   }

   public static synchronized void stop(ServerPlayer player, ThorState state) {
      ELAPSED.remove(player.getUUID());
      ThorBridge.unlockScene(state, SCENE);
   }

   /** PLACEHOLDER "underground": no sky above the head and enough solid blocks over it (a house roof is not a cave). */
   static boolean underground(ServerLevel level, BlockPos head) {
      if (level.dimensionType().hasCeiling() || level.canSeeSky(head)) {
         return false;
      }

      int solid = 0;
      int top = Math.min(level.getMaxBuildHeight(), head.getY() + MjolnirTuning.TRANSFORM_CARVE_MAX);
      for (int y = head.getY() + 1; y < top; y++) {
         BlockPos pos = new BlockPos(head.getX(), y, head.getZ());
         if (level.getBlockState(pos).isSolidRender(level, pos) && ++solid >= MjolnirTuning.UNDERGROUND_SOLID) {
            return true;
         }
      }

      return false;
   }

   /** №214: the lightning breaks a shaft from the surface down to the player; protected blocks stay. */
   static int carve(ServerLevel level, BlockPos head) {
      int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, head.getX(), head.getZ());
      int from = Math.min(surface, head.getY() + MjolnirTuning.TRANSFORM_CARVE_MAX);
      int carved = 0;
      for (int y = from; y > head.getY(); y--) {
         BlockPos pos = new BlockPos(head.getX(), y, head.getZ());
         if (level.getBlockState(pos).isAir()) {
            continue;
         }

         if (!HeroDestruction.canDestroy(level, pos) || !HeroDestruction.destroyBlock(level, pos)) {
            // A protected / unbreakable block (or griefing off): the shaft stops here, nothing below it is opened ([ОТКРЫТО] roof).
            break;
         }

         carved++;
      }

      return carved;
   }

   public static synchronized void clear() {
      ELAPSED.clear();
   }
}
