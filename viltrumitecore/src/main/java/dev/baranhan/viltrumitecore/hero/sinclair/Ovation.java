package dev.baranhan.viltrumitecore.hero.sinclair;

import net.minecraft.server.level.ServerPlayer;

/**
 * §4 "Ovation": every trick that lands (bolt hit, at least one rabbit, decoy reveal or swap next to an
 * enemy) takes 3 s off Audience Participation's cooldown. The snapshot pulse lets the HUD and the
 * client applause react.
 */
public final class Ovation {
   private Ovation() {
   }

   public static void grant(ServerPlayer owner) {
      SinclairState state = SinclairState.of(owner);
      if (state == null) {
         return;
      }

      state.ultCooldown = SinclairRules.ovation(state.ultCooldown);
      state.ovationPulse = 12;
      state.ovations++;
      SinclairSounds.play(owner.level(), owner.position().add(0.0, 1.6, 0.0), SinclairSounds.OVATION, 0.45F, 1.0F + owner.getRandom().nextFloat() * 0.1F);
   }
}
