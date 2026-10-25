package dev.baranhan.viltrumitecore.hero;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Tests the callback boundary used by the LevelChunk redirect, not live fluid/TPS behaviour. */
class QuietBlockRemovalTest {
   static boolean calls(String type,String method,String callee) throws Exception {
      var node=new org.objectweb.asm.tree.ClassNode();
      try(var input=QuietBlockRemovalTest.class.getResourceAsStream("/net/minecraft/world/level/"+type+".class")) {
         assertNotNull(input); new org.objectweb.asm.ClassReader(input).accept(node,0);
      }
      return node.methods.stream().filter(m -> m.name.equals(method)).anyMatch(m -> {
         for(var insn:m.instructions) if(insn instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals(callee)) return true;
         return false;
      });
   }
   @Test void inventoryRemovalDoesNotDropContentsOrExperience() throws Exception {
      assertTrue(calls("block/ChestBlock","onRemove","dropContents"),"actual chest callback can drop inventory");
      assertTrue(calls("block/AbstractFurnaceBlock","onRemove","dropContents"),"actual furnace callback can drop inventory");
      int[] effects=new int[3];
      QuietBlockRemoval.run(() -> {
         QuietBlockRemoval.onRemove(() -> { effects[0]++; effects[1]++; }, () -> effects[2]++); return true;
      });
      assertArrayEquals(new int[]{0,0,1},effects,"discard inventory block entity, no onRemove drops/XP");
   }
   @Test void poweredRedstoneRemovalCannotScheduleAdjacentFluidThroughCallback() throws Exception {
      assertTrue(calls("block/RedstoneTorchBlock","onRemove","updateNeighborsAt"));
      assertTrue(calls("block/LiquidBlock","neighborChanged","scheduleTick"));
      int[] neighbourAndFluid=new int[2];
      QuietBlockRemoval.run(() -> {
         QuietBlockRemoval.onRemove(() -> { neighbourAndFluid[0]++; neighbourAndFluid[1]++; }, () -> {}); return true;
      });
      assertArrayEquals(new int[]{0,0},neighbourAndFluid);
   }
   @Test void ordinaryRemovalStillRunsCallback() {
      int[] effects=new int[2]; QuietBlockRemoval.onRemove(() -> effects[0]++, () -> effects[1]++);
      assertArrayEquals(new int[]{1,0},effects);
   }
   @Test void scopeNestsAndClosesOnException() {
      assertFalse(QuietBlockRemoval.active());
      QuietBlockRemoval.run(() -> {
         assertTrue(QuietBlockRemoval.active());
         assertThrows(IllegalStateException.class,() -> QuietBlockRemoval.run(() -> { throw new IllegalStateException(); }));
         assertTrue(QuietBlockRemoval.active()); return true;
      });
      assertFalse(QuietBlockRemoval.active());
   }
   @Test void commonMixinAndBothRemovalPathsUseScope() throws Exception {
      assertTrue(calls("chunk/LevelChunk","setBlockState","onRemove"),"redirect target exists in mapped bytecode");
      String mixin=Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/mixin/QuietBlockRemovalMixin.java"));
      assertTrue(mixin.contains("QuietBlockRemoval.onRemove")); assertTrue(mixin.contains("BlockState;onRemove("));
      assertTrue(Files.readString(Path.of("src/main/resources/viltrumitecore.mixins.json")).contains("QuietBlockRemovalMixin"));
      assertTrue(Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/HeroDestruction.java")).contains("QuietBlockRemoval.run"));
      assertTrue(Files.readString(Path.of("src/main/java/dev/baranhan/viltrumitecore/hero/HeroDebris.java")).contains("HeroDestruction.setAir"));
   }
}
