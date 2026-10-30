package dev.baranhan.viltrumitecore.mixin;

import dev.baranhan.viltrumitecore.hero.control.TimeFreeze;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server half of the stopped-time camera lock: a frozen player's move packets
 * cannot turn him. TAIL runs on the server thread only (the netty-thread pass
 * leaves through ensureRunningOnSameThread before it), so a modified client that
 * ignores the client lock is set back and told so, at most every 5 ticks.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TimeFreezeMoveMixin {
   @Shadow
   public ServerPlayer player;

   @Unique
   private int viltrumitecore$lastViewFix = -100;

   @Inject(method = "handleMovePlayer", at = @At("TAIL"))
   private void viltrumitecore$lockFrozenView(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      ServerPlayer self = this.player;
      if (self == null || !self.getServer().isSameThread()) {
         return;
      }

      float[] lock = TimeFreeze.lockedView(self);
      if (lock == null || !TimeFreeze.viewDrifted(self.getYRot(), self.getXRot(), lock[0], lock[1])) {
         return;
      }

      self.setYRot(lock[0]);
      self.setXRot(lock[1]);
      self.setYHeadRot(lock[0]);
      if (self.tickCount - this.viltrumitecore$lastViewFix >= 5 || self.tickCount < this.viltrumitecore$lastViewFix) {
         this.viltrumitecore$lastViewFix = self.tickCount;
         ((ServerGamePacketListenerImpl)(Object)this).teleport(self.getX(), self.getY(), self.getZ(), lock[0], lock[1]);
      }
   }
}
