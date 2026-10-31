package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.entity.chitauri.LeviathanPartQueries;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A box query that touches a Leviathan segment finds the Leviathan (see {@link LeviathanPartQueries}). */
@Mixin(Level.class)
public abstract class LevelPartQueryMixin {
   @Inject(
      method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
      at = @At("TAIL")
   )
   private <T extends Entity> void viltrumite$leviathanPartsTyped(EntityTypeTest<Entity, T> test, AABB box, Predicate<? super T> predicate,
      List<? super T> out, int limit, CallbackInfo ci) {
      LeviathanPartQueries.addTyped((Level)(Object)this, test, box, predicate, out, limit);
   }

   @Inject(
      method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
      at = @At("RETURN")
   )
   private void viltrumite$leviathanPartsUntyped(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate,
      CallbackInfoReturnable<List<Entity>> cir) {
      LeviathanPartQueries.addUntyped((Level)(Object)this, except, box, predicate, cir.getReturnValue());
   }
}
