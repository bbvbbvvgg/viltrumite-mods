package dev.baranhan.viltrumitecore.client.thor;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Audit v3 (item 8): the caster's camera in the ultimate. Through the build-up the view narrows slowly (the
 * world closes in on the raised hammer), on the strike it kicks wide and settles back over the aftershocks.
 * Only the local Thor's own view, only the FOV (no forced turn: the crosshair still aims the strike).
 */
public final class WrathCamera {
   /** Narrowest view at the end of the build-up (FOV multiplier). */
   static final float NARROW = 0.86F;
   /** Kick on the strike (FOV multiplier), back to 1 over {@link #SETTLE} ticks. */
   static final float KICK = 1.14F;
   static final float SETTLE = 14.0F;

   private WrathCamera() {
   }

   public static void init() {
      MinecraftForge.EVENT_BUS.addListener(WrathCamera::onFov);
   }

   /** Pure: the FOV multiplier {@code elapsed} ticks into a WRATH whose strike is on {@code contact}. */
   static float fov(float elapsed, int contact) {
      if (elapsed < 0.0F) {
         return 1.0F;
      } else if (elapsed < contact) {
         float t = elapsed / Math.max(1, contact);
         float s = t * t * (3.0F - 2.0F * t);
         return 1.0F + (NARROW - 1.0F) * s;
      }

      float since = elapsed - contact;
      if (since < 2.0F) {
         // Two ticks from the narrowest to the widest.
         return NARROW + (KICK - NARROW) * (since / 2.0F);
      }

      float back = Math.min(1.0F, (since - 2.0F) / SETTLE);
      return KICK + (1.0F - KICK) * (1.0F - (1.0F - back) * (1.0F - back));
   }

   private static void onFov(ComputeFovModifierEvent event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (event.getPlayer() != minecraft.player) {
         return;
      }

      HeroPublicSnapshot snapshot = ThorView.of(event.getPlayer());
      if (snapshot == null || ThorView.move(snapshot) != ThorMove.WRATH) {
         return;
      }

      int contact = ThorAttacks.envelope(ThorMove.WRATH).phases().windup();
      float elapsed = snapshot.actionElapsed() + minecraft.getFrameTime();
      event.setNewFovModifier(event.getNewFovModifier() * fov(elapsed, contact));
   }
}
