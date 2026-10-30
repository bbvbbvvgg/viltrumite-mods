package dev.baranhan.viltrumitecore.client.mixin;

import dev.baranhan.viltrumitecore.client.hero.TimeFreezeView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server position/rotation correction becomes the stopped-time view lock (no tug of war). */
@Mixin(ClientPacketListener.class)
public abstract class TimeFreezeTeleportMixin {
   @Inject(method = "handleMovePlayer", at = @At("TAIL"))
   private void viltrumitecore$serverView(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      var player = Minecraft.getInstance().player;
      if (player != null && Minecraft.getInstance().isSameThread()) {
         TimeFreezeView.acceptServerView(player.getYRot(), player.getXRot());
      }
   }
}
