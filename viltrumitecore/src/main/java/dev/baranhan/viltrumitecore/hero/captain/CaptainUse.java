package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.VanillaUse;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click priority for Captain America (playtest: the shield block ate the Od Lagna wish). Items and
 * interactive things win over the block: any held item with its own use (wish, food, potion, bow, blocks,
 * pearls, ...), a tool aimed at a block, an interactive entity (villager, mount, pet, frame, ...) or an
 * interactive block (chest, door, lever, bed, ...). Empty hands or a plain weapon still block with the shield,
 * also while looking at the ground. Both sides (the pick is level-side), used by the client mouse claim.
 */
public final class CaptainUse {
   private CaptainUse() {
   }

   /** Pure: does this stack have a right-click use of its own (everything but a plain sword / tool)? */
   public static boolean usable(ItemStack stack, boolean aimsAtBlock) {
      if (stack.isEmpty()) {
         return false;
      }
      if (stack.getItem() instanceof TieredItem) {
         return aimsAtBlock || stack.getUseAnimation() != UseAnim.NONE; // swords block; tools strip / till / path
      }
      return true;
   }

   /** Does vanilla (item / block / entity) own this right click instead of the shield? */
   public static boolean yields(Player player) {
      HitResult hit = aim(player);
      boolean onBlock = hit != null && hit.getType() == HitResult.Type.BLOCK;
      if (usable(player.getMainHandItem(), onBlock) || usable(player.getOffhandItem(), onBlock)) {
         return true;
      }
      VanillaUse.Aim aim = VanillaUse.aim(player, hit);
      if (aim == VanillaUse.Aim.INTERACTIVE_ENTITY) {
         return true;
      }
      return onBlock && interactive(player, ((BlockHitResult)hit).getBlockPos());
   }

   /** A block with its own empty-hand use (menus, doors, switches, beds, ...). */
   public static boolean interactive(Player player, BlockPos pos) {
      BlockState state = player.level().getBlockState(pos);
      var block = state.getBlock();
      if (block instanceof BaseEntityBlock || state.getMenuProvider(player.level(), pos) != null) {
         return true;
      }
      return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof ButtonBlock
         || block instanceof LeverBlock || block instanceof BedBlock || block instanceof BellBlock || block instanceof NoteBlock
         || block instanceof RepeaterBlock || block instanceof ComparatorBlock || block instanceof CakeBlock || block instanceof ComposterBlock
         || block instanceof RespawnAnchorBlock;
   }

   /** What the crosshair is on: the nearer of the block and the entity pick (vanilla reach). */
   @Nullable
   static HitResult aim(Player player) {
      double reach = player.getBlockReach();
      HitResult block = player.pick(reach, 1.0F, false);
      double er = player.getEntityReach();
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getViewVector(1.0F);
      Vec3 end = eye.add(look.scale(er));
      EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(er)).inflate(1.0),
         e -> !e.isSpectator() && e.isPickable(), er * er);
      if (entity != null && (block == null || block.getType() == HitResult.Type.MISS
         || eye.distanceToSqr(entity.getLocation()) < eye.distanceToSqr(block.getLocation()))) {
         return entity;
      }
      return block;
   }
}
