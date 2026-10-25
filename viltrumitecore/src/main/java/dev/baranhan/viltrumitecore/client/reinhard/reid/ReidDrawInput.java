package dev.baranhan.viltrumitecore.client.reinhard.reid;

import dev.baranhan.viltrumitecore.client.reinhard.ReinhardPoses;
import dev.baranhan.viltrumitecore.client.reinhard.ReinhardView;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import net.minecraft.client.player.Input;
import net.minecraftforge.client.event.MovementInputUpdateEvent;

/** While the local Reinhard draws Reid (6 s) he stands still: movement and jump input are cleared (the server refuses jumps too). */
public final class ReidDrawInput {
   private ReidDrawInput() {
   }

   public static void onMovementInput(MovementInputUpdateEvent event) {
      HeroPublicSnapshot snapshot = ReinhardView.of(event.getEntity());
      if (snapshot == null || (ReinhardView.reid(snapshot) != ReinhardPoses.ReidPhase.DRAWING && snapshot.actionId() != dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.WORLD_ERASURE.ordinal()
         && snapshot.actionId() != dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.WORLD_CUT.ordinal()
         && snapshot.actionId() != dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility.DIAGONAL_WORLD_CUT.ordinal())) {
         return;
      }

      Input input = event.getInput();
      input.forwardImpulse = 0.0F;
      input.leftImpulse = 0.0F;
      input.up = false;
      input.down = false;
      input.left = false;
      input.right = false;
      input.jumping = false;
   }
}
