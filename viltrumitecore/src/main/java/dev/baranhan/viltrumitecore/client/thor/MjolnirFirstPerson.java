package dev.baranhan.viltrumitecore.client.thor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.pose.PoseRig;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.HammerState;
import dev.baranhan.viltrumitecore.hero.thor.ThorFlags;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * First-person staging of the recall and the catch (§5.1): the arm goes out
 * early while the hammer returns (not in the catch pose at the last
 * instant), then the weight of the catch kicks the hand and forearm back.
 * Applied by FirstPersonThorMixin to the right arm only. Values are visual.
 */
public final class MjolnirFirstPerson {
   private static final float REACH_IN = 0.18F;
   private static final float REACH_OUT = 0.12F;
   private static final int KICK_TICKS = 8;
   private static float reach;
   private static boolean wasReturning;
   private static int kickAge = KICK_TICKS;
   private static int lastTick = -1;

   private MjolnirFirstPerson() {
   }

   public static void apply(AbstractClientPlayer player, float partialTick, PoseStack stack) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      if (snapshot == null) {
         reach = 0.0F;
         return;
      }

      HammerState hammer = ThorFlags.hammer(snapshot.heroFlags());
      boolean returning = hammer == HammerState.RETURNING;
      if (player.tickCount != lastTick) {
         lastTick = player.tickCount;
         reach = Mth.clamp(reach + (returning ? REACH_IN : -REACH_OUT), 0.0F, 1.0F);
         if (wasReturning && hammer == HammerState.IN_HAND) {
            kickAge = 0;
         } else if (kickAge < KICK_TICKS) {
            kickAge++;
         }

         wasReturning = returning;
      }

      float kick = kickAge >= KICK_TICKS ? 0.0F : 1.0F - (kickAge + partialTick) / KICK_TICKS;
      kick = Math.max(0.0F, kick) * Math.max(0.0F, kick);
      float r = reach * reach * (3.0F - 2.0F * reach);
      if (r < 0.001F && kick < 0.001F) {
         return;
      }

      // Out: forward and a little up, palm open towards the hammer. Kick: back and down.
      PoseRig.firstPersonRaw(stack, -22.0F * r + 16.0F * kick, -6.0F * r, 0.0F, -0.04F * r, 0.10F * r - 0.07F * kick, -0.16F * r + 0.10F * kick);
   }
}
