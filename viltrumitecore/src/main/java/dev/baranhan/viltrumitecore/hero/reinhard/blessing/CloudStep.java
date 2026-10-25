package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import dev.baranhan.viltrumitecore.hero.HeroSuperJump;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import net.minecraft.server.level.ServerPlayer;

/**
 * Divine Protection of the Cloudy Sky (spec §4.4, overhaul item 8): unlimited steps
 * on the air (the shared air-jump seam: HeroAirJump / AirJumpClient; ground, water,
 * lava, a ladder, flying or a mount reset the count) steered by the movement input,
 * and the super jump. Mode-independent. Not flight: gravity always acts.
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

   /**
    * One air step (overhaul item 8): the vertical boost plus a bounded steer of the horizontal
    * momentum toward the movement input (WASD relative to the camera yaw). Speed along the input
    * builds over consecutive steps up to the cap (speed and inertia); the sideways rest of the
    * momentum is kept in part, so a turn bends over two steps instead of snapping; with no input
    * the body coasts on. The server has no movement input for a walking player: its copy only
    * coasts (it never sends this velocity back, the client's prediction moves the body).
    */
   public static net.minecraft.world.phys.Vec3 step(net.minecraft.world.phys.Vec3 current, float yaw, double vertical, float strafe, float forward) {
      net.minecraft.world.phys.Vec3 horizontal = new net.minecraft.world.phys.Vec3(current.x, 0.0, current.z);
      net.minecraft.world.phys.Vec3 input = new net.minecraft.world.phys.Vec3(strafe, 0.0, forward);
      if (input.lengthSqr() < 1.0E-4) {
         horizontal = horizontal.scale(ReinhardRules.AIR_STEP_COAST_KEEP);
      } else {
         // Entity.getInputVector: the input turned by the yaw.
         double sin = Math.sin(yaw * (Math.PI / 180.0)), cos = Math.cos(yaw * (Math.PI / 180.0));
         input = input.normalize();
         net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(input.x * cos - input.z * sin, 0.0, input.z * cos + input.x * sin);
         double along = horizontal.dot(dir);
         net.minecraft.world.phys.Vec3 lateral = horizontal.subtract(dir.scale(along)).scale(ReinhardRules.AIR_STEP_LATERAL_KEEP);
         // Against the momentum the step first brakes (part of it is kept), then accelerates.
         double base = along >= 0.0 ? along : along * ReinhardRules.AIR_STEP_REVERSE_KEEP;
         double speed = Math.min(base + ReinhardRules.AIR_STEP_ACCEL, ReinhardRules.AIR_STEP_MAX_SPEED);
         horizontal = dir.scale(speed).add(lateral);
      }
      if (horizontal.length() > ReinhardRules.AIR_STEP_MAX_SPEED) {
         horizontal = horizontal.normalize().scale(ReinhardRules.AIR_STEP_MAX_SPEED);
      }
      return new net.minecraft.world.phys.Vec3(horizontal.x, vertical, horizontal.z);
   }

   /** Air steps before touching ground again (unlimited). */
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
