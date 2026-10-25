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

   /** Bounded reorientation, not unbounded addition. Looking straight up keeps a stable yaw heading. */
   public static net.minecraft.world.phys.Vec3 motion(net.minecraft.world.phys.Vec3 current, net.minecraft.world.phys.Vec3 look,
      float yaw, double vertical, boolean groundLaunch) {
      net.minecraft.world.phys.Vec3 heading=new net.minecraft.world.phys.Vec3(look.x,0,look.z);
      if(heading.lengthSqr()<1E-6) heading=net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw);
      heading=heading.normalize();
      double cap=groundLaunch?1.5:1.35, boost=groundLaunch?1.2:1.05;
      net.minecraft.world.phys.Vec3 horizontal=new net.minecraft.world.phys.Vec3(current.x,0,current.z).scale(.25).add(heading.scale(boost));
      if(horizontal.length()>cap) horizontal=horizontal.normalize().scale(cap);
      return new net.minecraft.world.phys.Vec3(horizontal.x,vertical,horizontal.z);
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
