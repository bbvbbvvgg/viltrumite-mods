package dev.baranhan.viltrumitecore.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ViltrumiteBlocks {
   public static final String MOD_ID = "viltrumitecore";
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "viltrumitecore");
   public static final RegistryObject<Block> BLOOD_STAIN = BLOCKS.register(
      "viltrumite_blood_stain",
      () -> new BloodStainBlock(
            Properties.of()
               .mapColor(MapColor.COLOR_RED)
               .replaceable()
               .noCollission()
               .instabreak()
               .noOcclusion()
               .pushReaction(PushReaction.DESTROY)
               .sound(SoundType.WET_GRASS)
         )
   );

   /** Meteor Rain 2.0 city fire flame: clings to any solid face, kept alive only by a live fire spot. */
   public static final RegistryObject<Block> METEOR_FIRE = BLOCKS.register(
      "meteor_fire",
      () -> new MeteorFireBlock(
            Properties.of()
               .mapColor(MapColor.FIRE)
               .replaceable()
               .noCollission()
               .instabreak()
               .lightLevel(state -> 15)
               .sound(SoundType.WOOL)
               .pushReaction(PushReaction.DESTROY)
               .noLootTable()
         )
   );

   /** Living City 3.0 blackout: glowstone, sea lantern, shroomlight and froglights with their power cut. */
   public static final RegistryObject<Block> DARK_FIXTURE = BLOCKS.register(
      "dark_fixture",
      () -> new DarkFixtureBlock(
            Properties.of()
               .mapColor(MapColor.TERRACOTTA_GRAY)
               .strength(0.3F)
               .sound(SoundType.GLASS)
               .noLootTable()
         )
   );

   /** Living City 3.0 blackout: a lantern or soul lantern with its power cut. */
   public static final RegistryObject<Block> DARK_LANTERN = BLOCKS.register(
      "dark_lantern",
      () -> new DarkLanternBlock(
            Properties.of()
               .mapColor(MapColor.METAL)
               .forceSolidOn()
               .strength(3.5F)
               .sound(SoundType.LANTERN)
               .noOcclusion()
               .pushReaction(PushReaction.DESTROY)
               .noLootTable()
         )
   );

   /** Living City 3.0 blackout: an end rod light with its power cut. */
   public static final RegistryObject<Block> DARK_END_ROD = BLOCKS.register(
      "dark_end_rod",
      () -> new DarkEndRodBlock(
            Properties.of()
               .forceSolidOff()
               .instabreak()
               .sound(SoundType.WOOD)
               .noOcclusion()
               .noLootTable()
         )
   );

   public static void register(IEventBus modEventBus) {
      BLOCKS.register(modEventBus);
   }

   private ViltrumiteBlocks() {
   }
}
