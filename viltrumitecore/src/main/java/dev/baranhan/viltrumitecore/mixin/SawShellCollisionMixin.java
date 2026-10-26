package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.reinhard.saw.SawShells;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Saw Sphere barrier (overhaul item 19): the intact shell cells join the entity collision list
 * that {@code Entity.collide} builds once per move (used by both the plain sweep and the step-up),
 * so the barrier is ordinary collision on both sides — no blocks are placed, nothing teleports.
 * Idle cost: one empty-map check per move.
 */
@Mixin(Entity.class)
public abstract class SawShellCollisionMixin {
   @Unique
   private Vec3 viltrumitecore$sawMove;

   @Inject(method = "collide", at = @At("HEAD"))
   private void viltrumitecore$rememberSawMove(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
      this.viltrumitecore$sawMove = movement;
   }

   @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
   private List<VoxelShape> viltrumitecore$sawShell(List<VoxelShape> shapes) {
      return SawShells.withShells((Entity) (Object) this, shapes, this.viltrumitecore$sawMove);
   }
}
