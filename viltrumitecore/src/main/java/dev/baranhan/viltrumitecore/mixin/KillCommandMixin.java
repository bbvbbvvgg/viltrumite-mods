package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.AdminKill;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** {@code /kill} is an admin tool: each kill runs inside {@link AdminKill}, so hero HP floors do not apply. */
@Mixin(KillCommand.class)
public abstract class KillCommandMixin {
   @Unique
   @Redirect(
      method = "kill(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;kill()V")
   )
   private static void viltrumitecore$adminKill(Entity entity) {
      AdminKill.run(entity::kill);
   }
}
