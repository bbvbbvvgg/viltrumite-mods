package dev.baranhan.viltrumitecore.scp.scp106.block;

import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketPalette;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocks of the pocket dimension and of SCP-106's corrosion (own textures, tools/assets/scp106_blocks.py), and the
 * mapping of the pure painter's palette ids to block states. Pocket architecture is unbreakable for players in
 * survival (Scp106Pocket cancels it anyway); furniture, water, chains, doors, ladders and stairs are vanilla.
 */
public final class PocketBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "viltrumitecore");
   public static final RegistryObject<Block> CONCRETE = solid("pocket_concrete", MapColor.STONE, SoundType.STONE, 0);
   public static final RegistryObject<Block> MASS = solid("pocket_mass", MapColor.COLOR_BLACK, SoundType.DEEPSLATE, 0);
   public static final RegistryObject<Block> TILE = solid("pocket_tile", MapColor.QUARTZ, SoundType.STONE, 0);
   public static final RegistryObject<Block> ROT = solid("pocket_rot", MapColor.COLOR_BROWN, SoundType.MUD, 0);
   public static final RegistryObject<Block> METAL = solid("pocket_metal", MapColor.METAL, SoundType.METAL, 0);
   public static final RegistryObject<Block> GRATE = BLOCKS.register("pocket_grate",
      () -> new Block(pocket(MapColor.METAL, SoundType.CHAIN).noOcclusion()));
   public static final RegistryObject<Block> OOZE = BLOCKS.register("pocket_ooze",
      () -> new PocketOozeBlock(pocket(MapColor.COLOR_BLACK, SoundType.HONEY_BLOCK).speedFactor(0.45F).jumpFactor(0.6F)));
   public static final RegistryObject<Block> STAIN = BLOCKS.register("pocket_stain",
      () -> new PocketStainBlock(pocket(MapColor.COLOR_BLACK, SoundType.HONEY_BLOCK).noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)));
   public static final RegistryObject<Block> PILLAR = BLOCKS.register("pocket_pillar",
      () -> new net.minecraft.world.level.block.RotatedPillarBlock(pocket(MapColor.STONE, SoundType.STONE)));
   public static final RegistryObject<Block> LAMP = solid("pocket_lamp", MapColor.TERRACOTTA_YELLOW, SoundType.GLASS, 10);
   public static final RegistryObject<Block> LAMP_DEAD = solid("pocket_lamp_dead", MapColor.COLOR_GRAY, SoundType.GLASS, 0);
   public static final RegistryObject<Block> VOID_GLASS = BLOCKS.register("pocket_void_glass",
      () -> new Block(pocket(MapColor.COLOR_BLACK, SoundType.GLASS)));
   public static final RegistryObject<Block> THIN_WALL = BLOCKS.register("pocket_thin_wall",
      () -> new PocketThinWallBlock(pocket(MapColor.STONE, SoundType.STONE).noCollission().isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));
   public static final RegistryObject<Block> BREACH = BLOCKS.register("pocket_breach",
      () -> new Block(pocket(MapColor.COLOR_RED, SoundType.SCULK).lightLevel(s -> 9).emissiveRendering((s, l, p) -> true)));
   public static final RegistryObject<Block> PLASTER = solid("pocket_plaster", MapColor.COLOR_LIGHT_GREEN, SoundType.STONE, 0);
   public static final RegistryObject<Block> SIGIL = BLOCKS.register("pocket_sigil",
      () -> new Block(pocket(MapColor.COLOR_RED, SoundType.STONE).lightLevel(s -> 6).emissiveRendering((s, l, p) -> true)));
   public static final RegistryObject<Block> NOTE = BLOCKS.register("pocket_note",
      () -> new PocketNoteBlock(pocket(MapColor.SAND, SoundType.WOOL).noCollission().noOcclusion()));
   public static final RegistryObject<Block> SEAM = BLOCKS.register("pocket_seam",
      () -> new Block(pocket(MapColor.COLOR_BLACK, SoundType.STONE).noCollission().lightLevel(s -> 4)));
   public static final RegistryObject<Block> DAYLIGHT = BLOCKS.register("pocket_daylight",
      () -> new Block(pocket(MapColor.SNOW, SoundType.GLASS).lightLevel(s -> 15).emissiveRendering((s, l, p) -> true)));
   public static final RegistryObject<Block> ROCK = solid("pocket_rock", MapColor.STONE, SoundType.DEEPSLATE, 0);
   public static final RegistryObject<Block> ROCK_DARK = solid("pocket_rock_dark", MapColor.COLOR_BLACK, SoundType.DEEPSLATE, 0);
   public static final RegistryObject<Block> MOSAIC = solid("pocket_mosaic", MapColor.TERRACOTTA_RED, SoundType.STONE, 0);
   public static final RegistryObject<Block> ASPHALT = solid("pocket_asphalt", MapColor.COLOR_BLACK, SoundType.STONE, 0);
   /** What SCP-106 leaves on every surface it passes through (overworld): decays by itself after 6..10 minutes. */
   public static final RegistryObject<Block> CORROSION = BLOCKS.register("scp106_corrosion",
      () -> new Scp106CorrosionBlock(Properties.of().mapColor(MapColor.COLOR_BLACK).replaceable().noCollission().noOcclusion()
         .strength(0.2F).sound(SoundType.HONEY_BLOCK).pushReaction(PushReaction.DESTROY)));

   private static BlockState[] states;

   private PocketBlocks() {
   }

   public static void register(IEventBus modBus) {
      BLOCKS.register(modBus);
   }

   /** Unbreakable for survival players, tough for everything else (explosions included). */
   private static Properties pocket(MapColor color, SoundType sound) {
      return Properties.of().mapColor(color).sound(sound).strength(-1.0F, 3_600_000.0F).noLootTable();
   }

   private static RegistryObject<Block> solid(String name, MapColor color, SoundType sound, int light) {
      return BLOCKS.register(name, () -> new Block(pocket(color, sound).lightLevel(s -> light)));
   }

   /** Block state of a palette id (built lazily once the registries are frozen). */
   public static BlockState state(byte id) {
      BlockState[] s = states;
      if (s == null) {
         s = build();
         states = s;
      }
      return id >= 0 && id < s.length ? s[id] : s[0];
   }

   private static BlockState[] build() {
      BlockState[] s = new BlockState[PocketPalette.COUNT];
      Supplier<BlockState> air = Blocks.AIR::defaultBlockState;
      for (int i = 0; i < s.length; i++) {
         s[i] = air.get();
      }
      s[PocketPalette.CONCRETE] = CONCRETE.get().defaultBlockState();
      s[PocketPalette.MASS] = MASS.get().defaultBlockState();
      s[PocketPalette.TILE] = TILE.get().defaultBlockState();
      s[PocketPalette.CORRODED] = ROT.get().defaultBlockState();
      s[PocketPalette.METAL] = METAL.get().defaultBlockState();
      s[PocketPalette.GRATE] = GRATE.get().defaultBlockState();
      s[PocketPalette.OOZE] = OOZE.get().defaultBlockState();
      s[PocketPalette.OOZE_STAIN] = STAIN.get().defaultBlockState();
      s[PocketPalette.PILLAR] = PILLAR.get().defaultBlockState();
      s[PocketPalette.LIGHT] = LAMP.get().defaultBlockState();
      s[PocketPalette.LIGHT_DEAD] = LAMP_DEAD.get().defaultBlockState();
      s[PocketPalette.VOID_GLASS] = VOID_GLASS.get().defaultBlockState();
      s[PocketPalette.THIN_WALL] = THIN_WALL.get().defaultBlockState();
      s[PocketPalette.BREACH] = BREACH.get().defaultBlockState();
      s[PocketPalette.WATER] = Blocks.WATER.defaultBlockState();
      s[PocketPalette.PLASTER] = PLASTER.get().defaultBlockState();
      s[PocketPalette.PLANKS] = Blocks.DARK_OAK_PLANKS.defaultBlockState();
      s[PocketPalette.SIGIL] = SIGIL.get().defaultBlockState();
      s[PocketPalette.NOTE] = NOTE.get().defaultBlockState();
      s[PocketPalette.SEAM] = SEAM.get().defaultBlockState();
      s[PocketPalette.CHAIN] = Blocks.CHAIN.defaultBlockState();
      s[PocketPalette.BARS] = Blocks.IRON_BARS.defaultBlockState();
      s[PocketPalette.BOOKSHELF] = Blocks.BOOKSHELF.defaultBlockState();
      s[PocketPalette.FENCE] = Blocks.DARK_OAK_FENCE.defaultBlockState();
      s[PocketPalette.PLATE] = Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState();
      s[PocketPalette.CARPET] = Blocks.BROWN_CARPET.defaultBlockState();
      s[PocketPalette.POT] = Blocks.FLOWER_POT.defaultBlockState();
      s[PocketPalette.ROAD] = ASPHALT.get().defaultBlockState();
      s[PocketPalette.SIDEWALK] = Blocks.SMOOTH_STONE.defaultBlockState();
      s[PocketPalette.LANE] = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
      s[PocketPalette.FACADE] = Blocks.MUD_BRICKS.defaultBlockState();
      s[PocketPalette.CAULDRON] = Blocks.CAULDRON.defaultBlockState();
      Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
      for (int i = 0; i < 4; i++) {
         Direction d = sides[i];
         s[PocketPalette.STAIR + i] = Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d);
         // a chair's back is on the side its seat faces away from
         s[PocketPalette.CHAIR + i] = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d.getOpposite());
         s[PocketPalette.LADDER + i] = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, d);
         s[PocketPalette.DOOR_LOW + i] = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
         s[PocketPalette.DOOR_UP + i] = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
         s[PocketPalette.HOME_LOW + i] = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
         s[PocketPalette.HOME_UP + i] = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
      }
      s[PocketPalette.CHAIR_CEIL] = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP);
      s[PocketPalette.BONE] = Blocks.BONE_BLOCK.defaultBlockState();
      s[PocketPalette.SKULL] = Blocks.SKELETON_SKULL.defaultBlockState();
      s[PocketPalette.COBWEB] = Blocks.COBWEB.defaultBlockState();
      s[PocketPalette.DAYLIGHT] = DAYLIGHT.get().defaultBlockState();
      s[PocketPalette.LANTERN] = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
      s[PocketPalette.ROCK] = ROCK.get().defaultBlockState();
      s[PocketPalette.ROCK_DARK] = ROCK_DARK.get().defaultBlockState();
      s[PocketPalette.MOSAIC] = MOSAIC.get().defaultBlockState();
      return s;
   }
}
