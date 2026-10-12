package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.HeroSuperJump;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.server.level.ServerPlayer;

/**
 * Divine Protection of the Cloudy Sky (spec §4.4): up to five steps on the air
 * in a row (the shared air-jump seam: HeroAirJump / AirJumpClient, refilled by
 * ground, water, lava, a ladder, flying or a mount) and the super jump.
 * Mode-independent.
 */
public final class CloudStep {
   private CloudStep() {
   }

   /** Air steps before touching ground again. */
   public static int steps() {
      return ReinhardRules.AIR_STEPS;
   }

   /** Height gained by one step from a standstill in the air (vanilla gravity and drag). */
   public static double stepHeight() {
      return HeroSuperJump.apex(ReinhardRules.AIR_STEP_VELOCITY);
   }

   /** Height of the super jump. */
   public static double superJumpHeight() {
      return HeroSuperJump.apex(ReinhardRules.SUPER_JUMP_VELOCITY);
   }

   /** Server, accepted step: the step sound (the seam reset the fall distance and draws the ring). */
   public static void onStep(ServerPlayer player) {
      player.resetFallDistance();
      ReinhardSounds.play(player, ReinhardSounds.AIR_STEP.get(), 0.7F, 1.3F);
   }

   public static void onSuperJump(ServerPlayer player) {
      ReinhardSounds.play(player, ReinhardSounds.SUPER_JUMP.get(), 1.0F, 1.0F);
   }
}
