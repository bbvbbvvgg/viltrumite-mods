package dev.baranhan.viltrumitecore.client.dio;

import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.dio.DioRules;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * The local DIO's Road Roller arc. The player owns his position in Minecraft,
 * so the client moves him along DioRules.rollerHeight/rollerTravel from the
 * point where the ult started to the synced landing point; the server only
 * keeps him airborne (sustainsAirborne) and owns every hit. Input is held
 * for the whole ult; a cancelled ult (snapshot action gone) releases at once.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class RollerLeapClient {
   @Nullable
   private static Vec3 start;
   private static double clock = -1.0;

   private RollerLeapClient() {
   }

   /** Take-off point of the local DIO's running ult, or null. */
   @Nullable
   public static Vec3 start() {
      return start;
   }

   /** Local ult clock in ticks (reconciled with the snapshot), or -1. */
   public static double clock() {
      return clock;
   }

   private static boolean ulting(@Nullable LocalPlayer player) {
      HeroPublicSnapshot snapshot = DioView.of(player);
      return snapshot != null && DioView.is(snapshot, HeroAction.ROAD_ROLLER) && snapshot.actionTarget() != null;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (client.isPaused()) {
         return;
      }

      if (!ulting(player)) {
         start = null;
         clock = -1.0;
         return;
      }

      HeroPublicSnapshot snapshot = DioView.of(player);
      int elapsed = snapshot.actionElapsed();
      if (start == null) {
         start = player.position();
         clock = elapsed;
      } else {
         clock += 1.0;
         if (Math.abs(clock - elapsed) > 3.0) {
            clock = elapsed;
         }
      }

      if (clock <= DioRules.ROLLER_LEAP) {
         player.setDeltaMovement(Vec3.ZERO);
         return;
      }

      Vec3 at = RollerChoreo.dioFeet(Math.min(clock, DioRules.ROLLER_LENGTH), start, snapshot.actionTarget());
      player.setPos(at.x, at.y, at.z);
      player.setDeltaMovement(Vec3.ZERO);
      player.fallDistance = 0.0F;
   }

   @SubscribeEvent
   public static void onMovement(MovementInputUpdateEvent event) {
      if (event.getEntity() != Minecraft.getInstance().player || !ulting(Minecraft.getInstance().player)) {
         return;
      }

      var input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
      input.shiftKeyDown = false;
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      start = null;
      clock = -1.0;
   }
}
