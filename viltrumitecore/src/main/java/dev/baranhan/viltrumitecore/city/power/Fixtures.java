package dev.baranhan.viltrumitecore.city.power;

import dev.baranhan.viltrumitecore.block.DarkFixtureBlock;
import dev.baranhan.viltrumitecore.block.DarkLanternBlock;
import dev.baranhan.viltrumitecore.block.ViltrumiteBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks of a city are electric lights and what they look like without power. Only real block states:
 * a redstone lamp turns off, an invisible light block drops to level 0, glowstone / sea lanterns / shroomlights /
 * froglights become {@link DarkFixtureBlock}, lanterns {@link DarkLanternBlock}, end rods the dark rod, and a jack
 * o'lantern its carved pumpkin. Torches, candles, campfires and fire are flames, not the grid: they stay lit.
 */
public final class Fixtures {
   private Fixtures() {
   }

   /** A light of the grid that is burning now. Used as the palette pre-check of the fixture scan, so it must be cheap. */
   public static boolean isLit(BlockState s) {
      Block b = s.getBlock();
      if (b == Blocks.REDSTONE_LAMP) {
         return s.getValue(RedstoneLampBlock.LIT);
      }
      if (b == Blocks.LIGHT) {
         return s.getValue(LightBlock.LEVEL) > 0;
      }
      return b == Blocks.GLOWSTONE || b == Blocks.SEA_LANTERN || b == Blocks.SHROOMLIGHT || b == Blocks.OCHRE_FROGLIGHT || b == Blocks.VERDANT_FROGLIGHT
         || b == Blocks.PEARLESCENT_FROGLIGHT || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.END_ROD || b == Blocks.JACK_O_LANTERN;
   }

   /** The dark state of a burning light, or null when {@code s} is not one. */
   @Nullable
   public static BlockState dark(BlockState s) {
      Block b = s.getBlock();
      if (b == Blocks.REDSTONE_LAMP) {
         return s.getValue(RedstoneLampBlock.LIT) ? s.setValue(RedstoneLampBlock.LIT, false) : null;
      }
      if (b == Blocks.LIGHT) {
         return s.getValue(LightBlock.LEVEL) > 0 ? s.setValue(LightBlock.LEVEL, 0) : null;
      }
      DarkFixtureBlock.Kind kind = b == Blocks.GLOWSTONE ? DarkFixtureBlock.Kind.GLOWSTONE
         : b == Blocks.SEA_LANTERN ? DarkFixtureBlock.Kind.SEA_LANTERN
         : b == Blocks.SHROOMLIGHT ? DarkFixtureBlock.Kind.SHROOMLIGHT
         : b == Blocks.OCHRE_FROGLIGHT ? DarkFixtureBlock.Kind.OCHRE_FROGLIGHT
         : b == Blocks.VERDANT_FROGLIGHT ? DarkFixtureBlock.Kind.VERDANT_FROGLIGHT
         : b == Blocks.PEARLESCENT_FROGLIGHT ? DarkFixtureBlock.Kind.PEARLESCENT_FROGLIGHT : null;
      if (kind != null) {
         Direction.Axis axis = s.hasProperty(RotatedPillarBlock.AXIS) ? s.getValue(RotatedPillarBlock.AXIS) : Direction.Axis.Y;
         return ViltrumiteBlocks.DARK_FIXTURE.get().defaultBlockState().setValue(DarkFixtureBlock.KIND, kind).setValue(DarkFixtureBlock.AXIS, axis);
      }
      if (b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN) {
         return ViltrumiteBlocks.DARK_LANTERN.get().defaultBlockState().setValue(DarkLanternBlock.SOUL, b == Blocks.SOUL_LANTERN)
            .setValue(LanternBlock.HANGING, s.getValue(LanternBlock.HANGING)).setValue(LanternBlock.WATERLOGGED, s.getValue(LanternBlock.WATERLOGGED));
      }
      if (b == Blocks.END_ROD) {
         return ViltrumiteBlocks.DARK_END_ROD.get().defaultBlockState().setValue(EndRodBlock.FACING, s.getValue(EndRodBlock.FACING));
      }
      if (b == Blocks.JACK_O_LANTERN) {
         return Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, s.getValue(CarvedPumpkinBlock.FACING));
      }
      return null;
   }

   /**
    * The lit state of a dark fixture when its original is unknown (a ledger lost in a crash): the inverse of
    * {@link #dark}. Null for anything else. A carved pumpkin is never relit this way (it may be a real one).
    */
   @Nullable
   public static BlockState relight(BlockState s) {
      Block b = s.getBlock();
      if (b == ViltrumiteBlocks.DARK_FIXTURE.get()) {
         Block lit = switch (s.getValue(DarkFixtureBlock.KIND)) {
            case GLOWSTONE -> Blocks.GLOWSTONE;
            case SEA_LANTERN -> Blocks.SEA_LANTERN;
            case SHROOMLIGHT -> Blocks.SHROOMLIGHT;
            case OCHRE_FROGLIGHT -> Blocks.OCHRE_FROGLIGHT;
            case VERDANT_FROGLIGHT -> Blocks.VERDANT_FROGLIGHT;
            case PEARLESCENT_FROGLIGHT -> Blocks.PEARLESCENT_FROGLIGHT;
         };
         BlockState out = lit.defaultBlockState();
         return out.hasProperty(RotatedPillarBlock.AXIS) ? out.setValue(RotatedPillarBlock.AXIS, s.getValue(DarkFixtureBlock.AXIS)) : out;
      }
      if (b == ViltrumiteBlocks.DARK_LANTERN.get()) {
         Block lit = s.getValue(DarkLanternBlock.SOUL) ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
         return lit.defaultBlockState().setValue(LanternBlock.HANGING, s.getValue(LanternBlock.HANGING))
            .setValue(LanternBlock.WATERLOGGED, s.getValue(LanternBlock.WATERLOGGED));
      }
      if (b == ViltrumiteBlocks.DARK_END_ROD.get()) {
         return Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, s.getValue(EndRodBlock.FACING));
      }
      return null;
   }

   /** One of the mod's own dark fixture blocks (never part of a map). */
   public static boolean isOwnDark(BlockState s) {
      Block b = s.getBlock();
      return b == ViltrumiteBlocks.DARK_FIXTURE.get() || b == ViltrumiteBlocks.DARK_LANTERN.get() || b == ViltrumiteBlocks.DARK_END_ROD.get();
   }
}
