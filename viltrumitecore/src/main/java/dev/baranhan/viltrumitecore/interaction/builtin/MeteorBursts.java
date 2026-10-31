package dev.baranhan.viltrumitecore.interaction.builtin;

import dev.baranhan.viltrumitecore.entity.MeteorEntity;
import dev.baranhan.viltrumitecore.interaction.InteractionTargets;
import dev.baranhan.viltrumitecore.interaction.InteractionTuning;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Shared end of a meteor stopped by a hero before it reached the ground: no crater, no magma, the blast only throws. */
final class MeteorBursts {
   private MeteorBursts() {
   }

   /** {@code stopper} (nullable: a meteor stopped by no hero) and his side are spared the blast; the loot falls at his feet or at {@code at}. */
   static void burst(MeteorEntity meteor, @Nullable ServerPlayer stopper, Vec3 at) {
      for (LivingEntity e : InteractionTargets.around(meteor, at, InteractionTuning.METEOR_BLAST_RADIUS)) {
         if (InteractionTargets.spared(e, stopper)) {
            continue;
         }
         double fall = Math.max(0.3, 1.0 - Math.sqrt(e.distanceToSqr(at)) / InteractionTuning.METEOR_BLAST_RADIUS);
         InteractionTargets.push(e, InteractionTargets.away(at, e.position(), meteor.getDeltaMovement()).scale(InteractionTuning.METEOR_BLAST_PUSH * fall)
            .add(0, 0.4 * fall, 0));
      }
      if (meteor.dropsLoot()) {
         // The star iron the meteor carried falls at the hero's feet instead of a crater.
         Item piece = switch (meteor.level().random.nextInt(3)) {
            case 0 -> ViltrumiteItems.INFINITY_GUN_BASE.get();
            case 1 -> ViltrumiteItems.INFINITY_GUN_HANDLE.get();
            default -> ViltrumiteItems.INFINITY_GUN_BARREL.get();
         };
         Vec3 drop = stopper != null ? stopper.position() : at;
         ItemEntity item = new ItemEntity(meteor.level(), drop.x, drop.y + 1.0, drop.z, new ItemStack(piece));
         item.setDeltaMovement(0.0, 0.3, 0.0);
         item.setUnlimitedLifetime();
         meteor.level().addFreshEntity(item);
      }
      // Entity event 77 plays the meteor's own impact VFX on every tracking client, then it is gone.
      meteor.level().broadcastEntityEvent(meteor, (byte)77);
      meteor.discard();
   }
}
