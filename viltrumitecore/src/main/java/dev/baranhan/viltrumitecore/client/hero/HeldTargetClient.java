package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Predict the local held body using the same neck anchor as the server. No C2S hand coordinates. */
@Mod.EventBusSubscriber(modid="viltrumitecore", value=Dist.CLIENT)
public final class HeldTargetClient {
   private HeldTargetClient() {}
   @SubscribeEvent public static void movement(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
      if(HeroHeldTargets.holder(event.getEntity())==null) return;
      var input=event.getInput();input.forwardImpulse=0;input.leftImpulse=0;
      input.up=false;input.down=false;input.left=false;input.right=false;input.jumping=false;
   }
   @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase != TickEvent.Phase.END || mc.player == null || mc.isPaused() || WorldPauseClient.active()) return;
      Player holder = HeroHeldTargets.holder(mc.player);
      if (holder == null) return;
      Vec3 at = HeroRegistry.get(holder).heldTargetPosition(holder, mc.player, 1);
      if (at == null) return;
      mc.player.setPos(at.x, at.y, at.z);
      mc.player.setDeltaMovement(Vec3.ZERO);
      mc.player.resetFallDistance();
   }
}
