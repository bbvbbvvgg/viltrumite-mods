package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.client.render.vfx.AirStepVfx;
import dev.baranhan.viltrumitecore.hero.AirJumpRules;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.HeroInputC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Air jumps, client side (any hero with HeroDefinition.airJumps > 0): a jump-key
 * press in the air (not the ground jump vanilla just took) is predicted here —
 * vertical velocity set, local counter up, ring drawn — and sent once through
 * the generic {@link HeroInputC2SPacket}(AIR_STEP). The server (HeroAirJump)
 * keeps its own counter with the same {@link AirJumpRules}; a refusal comes back
 * as a motion correction plus {@link #onSync}. Client only.
 */
public final class AirJumpClient {
   private static int used;
   private static boolean jumpWasDown;
   private static boolean groundAtStart;

   private AirJumpClient() {
   }

   /** ClientTickEvent START: was the tick's jump a ground jump. */
   public static void beforeTick(Minecraft client) {
      LocalPlayer player = client.player;
      groundAtStart = player != null && player.onGround();
   }

   /** ClientTickEvent END, after vanilla movement consumed the ground jump. */
   public static void tick(Minecraft client) {
      LocalPlayer player = client.player;
      if (player == null) {
         used = 0;
         jumpWasDown = false;
         return;
      }

      boolean down = player.input.jumping;
      boolean pressed = down && !jumpWasDown;
      jumpWasDown = down;
      HeroDefinition hero = HeroRegistry.get(player);
      int max = hero.airJumps(player);
      boolean reset = AirJumpRules.resets(player.onGround(), player.isInWater(), player.isInLava(), player.onClimbable(), player.getAbilities().flying,
         player.isPassenger());
      if (max <= 0 || reset) {
         used = 0;
      }

      if (!pressed || max <= 0 || groundAtStart || player.isFallFlying()) {
         return;
      }

      boolean anchored = SuperJumpClient.anchored(player, player instanceof HeroPlayer heroPlayer ? heroPlayer.getHeroSnapshot() : null);
      boolean canAct = player.isAlive() && !player.isSpectator();
      if (!AirJumpRules.allow(used, max, reset, anchored, canAct)) {
         return;
      }

      Vec3 v = player.getDeltaMovement();
      player.setDeltaMovement(hero.airJumpMotion(player,v));
      player.resetFallDistance();
      used++;
      AirStepVfx.spawn(player);
      CoreMessages.sendToServer(new HeroInputC2SPacket(HeroAction.AIR_STEP, true));
   }

   /** AirJumpSyncS2CPacket: another player's accepted step (ring) or our counter after a refusal. */
   public static void onSync(int entityId, int serverUsed, boolean step) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         return;
      }

      if (step) {
         Entity entity = client.level.getEntity(entityId);
         if (entity != null && entity != client.player) {
            AirStepVfx.spawn(entity);
         }

         return;
      }

      if (client.player != null && client.player.getId() == entityId) {
         used = serverUsed;
      }
   }
}
