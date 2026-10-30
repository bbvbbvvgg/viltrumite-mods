package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stopped time on the server: a frozen non-player entity skips its whole tick
 * (AI, brain, physics, lifetime, fuse), keeping velocity and timers for the
 * resume. Riders still get their own tick. Players are frozen in
 * {@link TimeFreezePlayerMixin} (ServerPlayer.tick keeps the connection-side work).
 */
@Mixin(ServerLevel.class)
public abstract class TimeFreezeLevelMixin {
   @Shadow
   protected abstract void tickPassenger(Entity vehicle, Entity passenger);

   @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozen(Entity entity, CallbackInfo ci) {
      if (!(entity instanceof Player) && TimeFreeze.isFrozen(entity)) {
         ci.cancel();
         for (Entity passenger : entity.getPassengers()) {
            this.tickPassenger(entity, passenger);
         }
      }
   }

   @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$timeFrozenPassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
      if (!(passenger instanceof Player) && passenger.getVehicle() == vehicle && TimeFreeze.isFrozen(passenger)) {
         ci.cancel();
      }
   }

   /*
    * A world pause stops the level's own clocks too (TimeFreeze.worldStopped): scheduled block and
    * fluid ticks stay queued (they run, overdue, on the first tick after the stop), block events
    * (pistons, note blocks) stay queued, and random ticks (fire, crops, leaves) wait. DIO's local
    * fields never stop a level, so their behaviour is unchanged.
    */
   @Redirect(method = "tick", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"))
   private void viltrumitecore$stoppedTicks(net.minecraft.world.ticks.LevelTicks<?> ticks, long time, int max,
      java.util.function.BiConsumer<net.minecraft.core.BlockPos, ?> runner) {
      if (!TimeFreeze.worldStopped((ServerLevel)(Object)this)) {
         viltrumitecore$run(ticks, time, max, runner);
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static void viltrumitecore$run(net.minecraft.world.ticks.LevelTicks ticks, long time, int max, java.util.function.BiConsumer runner) {
      ticks.tick(time, max, runner);
   }

   @Inject(method = "runBlockEvents", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedBlockEvents(CallbackInfo ci) {
      if (TimeFreeze.worldStopped((ServerLevel)(Object)this)) {
         ci.cancel();
      }
   }

   @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
   private void viltrumitecore$stoppedRandomTicks(net.minecraft.world.level.chunk.LevelChunk chunk, int speed, CallbackInfo ci) {
      if (TimeFreeze.worldStopped((ServerLevel)(Object)this)) {
         ci.cancel();
      }
   }
}
