package dev.baranhan.viltrumitecore.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** PlayerRenderer.setModelProperties (crouching, arm poses, part visibility) for a body pose bridge. */
@Mixin({PlayerRenderer.class})
public interface PlayerRendererInvoker {
   @Invoker("setModelProperties")
   void viltrumitecore$setModelProperties(AbstractClientPlayer player);
}
