package dev.baranhan.viltrumitecore.hero.thor.hammer;

import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.Tags;

/**
 * §11: choosing Thor. A powerful lightning strike on the player (visual: no
 * fire, no damage, nothing broken), a short protected scene (~1–2 s) that
 * holds the scene lock, then control returns even if the hammer still
 * flies. Underground (№214) the bolt carves its way down to the player,
 * only through natural ground: never through a build, a fluid or a
 * protected block (then nothing is carved, A-m9).
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
      if (underground(player, level, head)) {
         carve(player, level, head);
      }

      // The transformation bolt is a visual: drawn by the clients (no LightningBolt entity, so no copper
      // scraping, lightning rod, sculk event or world-wide thunder; same contract as session B's bolts).
      HeroFx.electricBolt(level, player.position(), 48.0F, 2.0F, ThorSounds.boltCrack());
      ThorSounds.bolt(level, player.position(), 2.0F);
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

   /** One block of the column above the head, as the underground rule sees it (pure input of {@link #shaft}). */
   enum Cell {
      /** Air or a replaceable plant: nothing to carve. */
      OPEN,
      /** Natural ground (stone, deepslate, dirt, sand, gravel, terracotta, ores): the only thing the bolt carves. */
      NATURAL,
      /** Anything else solid: a player build (planks, glass, cobblestone, bricks, ...) or a tree. */
      BUILT,
      /** Water or lava: an ocean, a lake, a water base. */
      FLUID,
      /** Unbreakable, mobGriefing off, spawn protection or a claim refuses the player. */
      PROTECTED
   }

   /**
    * Pure, №214 PLACEHOLDER made conservative (A-m9): the bolt opens a shaft
    * only through natural ground: at least {@code solid} natural blocks over
    * the head and not a single fluid, built or protected block in the column
    * (bottom-up). Otherwise nothing is carved at all (the bolt and the scene
    * stay the same): a house roof, a tall build, an ocean floor or a
    * protected area is never drilled or flooded. OPEN(owner): №214 heuristic.
    */
   static boolean shaft(List<Cell> column, int solid) {
      int natural = 0;
      for (Cell cell : column) {
         switch (cell) {
            case NATURAL -> natural++;
            case OPEN -> {
            }
            default -> {
               return false;
            }
         }
      }

      return natural >= solid;
   }

   /** PLACEHOLDER "underground": no sky above the head and a column of natural ground over it (see {@link #shaft}). */
   static boolean underground(ServerPlayer player, ServerLevel level, BlockPos head) {
      if (level.dimensionType().hasCeiling() || level.canSeeSky(head)) {
         return false;
      }

      return shaft(column(player, level, head), MjolnirTuning.UNDERGROUND_SOLID);
   }

   /** The column from just above the head up to the surface (at most TRANSFORM_CARVE_MAX blocks), bottom-up. */
   private static List<Cell> column(ServerPlayer player, ServerLevel level, BlockPos head) {
      List<Cell> out = new ArrayList<>();
      int top = top(level, head);
      for (int y = head.getY() + 1; y <= top; y++) {
         out.add(cell(player, level, new BlockPos(head.getX(), y, head.getZ())));
      }

      return out;
   }

   private static int top(ServerLevel level, BlockPos head) {
      int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, head.getX(), head.getZ());
      return Math.min(Math.min(surface, level.getMaxBuildHeight() - 1), head.getY() + MjolnirTuning.TRANSFORM_CARVE_MAX);
   }

   static Cell cell(ServerPlayer player, ServerLevel level, BlockPos pos) {
      if (!level.isLoaded(pos)) {
         return Cell.PROTECTED;
      }

      BlockState state = level.getBlockState(pos);
      if (!level.getFluidState(pos).isEmpty()) {
         return Cell.FLUID;
      }

      if (state.isAir() || state.canBeReplaced()) {
         return Cell.OPEN;
      }

      if (!HeroDestruction.canDestroy(level, pos) || player.server.isUnderSpawnProtection(level, pos, player) || !level.mayInteract(player, pos)) {
         return Cell.PROTECTED;
      }

      return natural(state) ? Cell.NATURAL : Cell.BUILT;
   }

   private static boolean natural(BlockState state) {
      return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
         || state.is(BlockTags.TERRACOTTA) || state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.GRAVEL) || state.is(Tags.Blocks.SANDSTONE)
         || state.is(Blocks.CLAY);
   }

   /** №214: the lightning breaks a shaft from the surface down to the player, only through natural ground (decided by {@link #underground}). */
   static int carve(ServerPlayer player, ServerLevel level, BlockPos head) {
      int carved = 0;
      for (int y = top(level, head); y > head.getY(); y--) {
         BlockPos pos = new BlockPos(head.getX(), y, head.getZ());
         if (cell(player, level, pos) == Cell.NATURAL && HeroDestruction.destroyBlock(level, pos)) {
            carved++;
         }
      }

      return carved;
   }

   public static synchronized void clear() {
      ELAPSED.clear();
   }
}
