package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * Contents of the containers a saw cut (overhaul item 19): taken out of the block entity
 * <em>before</em> the block is cleared (the quiet removal discards the block entity), merged
 * per container into full stacks, then dropped as a bounded number of item entities per tick.
 * Whatever is still pending when the scene ends or is interrupted is dropped at once, so no
 * item is lost and none is duplicated (every slot is emptied as it is read).
 */
public final class SawLoot {
   /** Item entities spawned per tick while the scene runs. */
   public static final int SPAWNS_PER_TICK = 4;
   private final ArrayDeque<Drop> pending = new ArrayDeque<>();

   public record Drop(BlockPos pos, ItemStack stack) {
   }

   public int pending() {
      return this.pending.size();
   }

   /** Merges same-item stacks (same tags) and splits them to the max stack size. Pure. */
   public static List<ItemStack> merge(List<ItemStack> stacks) {
      List<ItemStack> merged = new ArrayList<>();
      for (ItemStack stack : stacks) {
         if (stack.isEmpty()) continue;
         ItemStack rest = stack.copy();
         for (ItemStack into : merged) {
            if (rest.isEmpty()) break;
            if (!ItemStack.isSameItemSameTags(into, rest)) continue;
            int room = into.getMaxStackSize() - into.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, rest.getCount());
            into.grow(moved);
            rest.shrink(moved);
         }
         while (!rest.isEmpty()) {
            merged.add(rest.split(Math.max(1, rest.getMaxStackSize())));
         }
      }
      return merged;
   }

   /**
    * Empties the block entity at {@code pos} into the pending queue; true when it held items.
    * Vanilla containers first, then the Forge item capability (modded inventories), then the
    * two vanilla holders that are no {@link Container} (lectern book, campfire food).
    */
   public boolean extract(ServerLevel level, BlockPos pos) {
      BlockEntity be = level.getBlockEntity(pos);
      if (be == null) return false;
      List<ItemStack> taken = new ArrayList<>();
      if (be instanceof Container container) {
         for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.removeItemNoUpdate(i);
            if (!stack.isEmpty()) taken.add(stack);
         }
      } else if (be instanceof LecternBlockEntity lectern) {
         ItemStack book = lectern.getBook();
         if (!book.isEmpty()) {
            taken.add(book.copy());
            lectern.clearContent();
         }
      } else if (be instanceof CampfireBlockEntity campfire) {
         for (int i = 0; i < campfire.getItems().size(); i++) {
            ItemStack stack = campfire.getItems().get(i);
            if (!stack.isEmpty()) taken.add(stack.copy());
            campfire.getItems().set(i, ItemStack.EMPTY);
         }
      } else {
         IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
         if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
               for (int guard = 0; guard < 64; guard++) {
                  ItemStack slot = handler.getStackInSlot(i);
                  if (slot.isEmpty()) break;
                  ItemStack out = handler.extractItem(i, slot.getCount(), false);
                  if (out.isEmpty()) break;
                  taken.add(out);
               }
            }
         }
      }
      if (taken.isEmpty()) return false;
      be.setChanged();
      for (ItemStack stack : merge(taken)) this.pending.add(new Drop(pos.immutable(), stack));
      return true;
   }

   /** Drops up to {@code max} pending stacks; returns how many. */
   public int flush(ServerLevel level, int max) {
      int n = 0;
      while (n < max && !this.pending.isEmpty()) {
         spawn(level, this.pending.poll());
         n++;
      }
      return n;
   }

   /** The scene ends: every remaining stack drops now. */
   public void flushAll(ServerLevel level) {
      while (!this.pending.isEmpty()) spawn(level, this.pending.poll());
   }

   private static void spawn(ServerLevel level, Drop drop) {
      BlockPos p = drop.pos();
      ItemEntity item = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.25, p.getZ() + 0.5, drop.stack());
      item.setDefaultPickUpDelay();
      item.setDeltaMovement(level.random.nextGaussian() * 0.04, 0.12, level.random.nextGaussian() * 0.04);
      level.addFreshEntity(item);
   }
}
