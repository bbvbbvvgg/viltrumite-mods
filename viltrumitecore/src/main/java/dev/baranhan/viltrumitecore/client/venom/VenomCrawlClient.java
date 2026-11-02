package dev.baranhan.viltrumitecore.client.venom;

import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.venom.VenomFlags;
import dev.baranhan.viltrumitecore.hero.venom.VenomRules;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Owner-side wall movement of Venom (vanilla player movement is client-driven; the server only grants it through
 * the CRAWL flag and marks WALL itself for everyone else). In the air against a sturdy face: forward climbs
 * ({@link VenomRules#WALL_CLIMB_SPEED}), sprint along the facade runs without gravity
 * ({@link VenomRules#WALL_RUN_SPEED}), sneak hangs still, nothing pressed slides down slowly, jump kicks off the
 * wall. Server-side the touching-wall test and the fall-damage reset run on their own (VenomSkills.tick). Client only.
 */
@EventBusSubscriber(modid = "viltrumitecore", bus = Bus.FORGE, value = {Dist.CLIENT})
public final class VenomCrawlClient {
   static final double SLIDE = -0.07;
   static final double KICK_OUT = 0.55;
   static final double KICK_UP = 0.62;
   private static boolean clinging;
   private static boolean running;
   private static boolean jumpWasDown;
   private static int kickCooldown;

   private VenomCrawlClient() {
   }

   /** Clinging to a wall this tick (the local player only). */
   public static boolean clinging() {
      return clinging;
   }

   /** Running along a facade this tick. */
   public static boolean running() {
      return running;
   }

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase != TickEvent.Phase.START || event.side != LogicalSide.CLIENT) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (event.player != mc.player || !(event.player instanceof LocalPlayer player)) {
         return;
      }
      if (kickCooldown > 0) {
         kickCooldown--;
      }
      HeroPublicSnapshot s = VenomView.of(player);
      boolean allowed = s != null && VenomView.flag(s, VenomFlags.FORM) && VenomView.flag(s, VenomFlags.CRAWL)
         && !VenomView.flag(s, VenomFlags.POSSESSING) && !player.getAbilities().flying && !player.isPassenger()
         && !player.isInWater() && !player.isInLava() && !player.isSpectator() && !player.onGround() && kickCooldown == 0;
      Direction wall = allowed ? wallSide(player.level(), player.position(), player.getBbWidth(), player.horizontalCollision, player.getDeltaMovement())
         : null;
      boolean jumpDown = player.input != null && player.input.jumping;
      if (wall == null) {
         clinging = false;
         running = false;
         jumpWasDown = jumpDown;
         return;
      }
      Vec3 normal = Vec3.atLowerCornerOf(wall.getOpposite().getNormal()); // out of the wall
      float forward = player.input == null ? 0.0F : player.input.forwardImpulse;
      float strafe = player.input == null ? 0.0F : player.input.leftImpulse;
      boolean sneak = player.input != null && player.input.shiftKeyDown;
      Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
      Vec3 v = player.getDeltaMovement();
      clinging = true;
      running = false;
      if (jumpDown && !jumpWasDown) {
         // kick off the wall: out and up, a short window before re-clinging
         Vec3 out = normal.scale(KICK_OUT).add(look.subtract(normal.scale(look.dot(normal))).scale(0.25));
         player.setDeltaMovement(out.x, KICK_UP, out.z);
         kickCooldown = 6;
         clinging = false;
         jumpWasDown = true;
         return;
      }
      jumpWasDown = jumpDown;
      Vec3 next = motion(normal, look, forward, strafe, player.isSprinting() || mc.options.keySprint.isDown(), sneak, v);
      running = next.y > -0.01 && next.y < 0.01 && next.horizontalDistanceSqr() > 0.05;
      player.setDeltaMovement(next);
      player.fallDistance = 0.0F;
   }

   /**
    * The next velocity on a wall (pure, VenomCrawlTest): {@code normal} points out of the wall, {@code look} is the
    * horizontal facing.
    */
   static Vec3 motion(Vec3 normal, Vec3 look, float forward, float strafe, boolean sprint, boolean sneak, Vec3 v) {
      Vec3 tangent = new Vec3(-normal.z, 0.0, normal.x); // along the facade
      double along = look.dot(tangent);
      double into = -look.dot(normal);
      Vec3 hug = normal.scale(-0.04); // keep the contact
      if (sprint && forward > 0.1F && Math.abs(along) > 0.45) {
         // wall run: along the facade in the facing's direction, no gravity
         Vec3 dir = tangent.scale(Math.signum(along));
         Vec3 h = dir.scale(VenomRules.WALL_RUN_SPEED).add(hug);
         return new Vec3(h.x, 0.0, h.z);
      }
      Vec3 left = new Vec3(look.z, 0.0, -look.x); // vanilla: a positive left impulse moves this way
      Vec3 sideways = tangent.scale(left.dot(tangent) * strafe * 0.12); // A/D: crab-walk along the wall
      if (forward > 0.1F && into > -0.2) {
         return new Vec3(sideways.x + hug.x, VenomRules.WALL_CLIMB_SPEED, sideways.z + hug.z);
      }
      if (forward > 0.1F) {
         return new Vec3(v.x, Math.max(v.y, SLIDE), v.z); // facing away: walk off the wall
      }
      if (forward < -0.1F) {
         return new Vec3(sideways.x + hug.x, -VenomRules.WALL_CLIMB_SPEED, sideways.z + hug.z);
      }
      if (sneak) {
         return new Vec3(sideways.x + hug.x, 0.0, sideways.z + hug.z);
      }
      return new Vec3(sideways.x + hug.x, Math.max(v.y, SLIDE), sideways.z + hug.z);
   }

   /** The side with a sturdy face right at the body (the collision side first). */
   @Nullable
   static Direction wallSide(Level level, Vec3 pos, float width, boolean collided, Vec3 v) {
      Direction best = null;
      double bestGap = Double.MAX_VALUE;
      BlockPos feet = BlockPos.containing(pos);
      for (Direction d : Direction.Plane.HORIZONTAL) {
         for (int dy = 0; dy <= 1; dy++) {
            BlockPos p = feet.above(dy).relative(d);
            if (!level.getBlockState(p).isFaceSturdy(level, p, d.getOpposite())) {
               continue;
            }
            Vec3 c = Vec3.atCenterOf(p);
            double gap = Math.abs(d.getAxis().choose(c.x, 0, c.z) - d.getAxis().choose(pos.x, 0, pos.z)) - 0.5 - width * 0.5;
            if (gap < bestGap) {
               bestGap = gap;
               best = d;
            }
         }
      }
      double reach = collided ? 0.2 : 0.09;
      return best != null && bestGap <= reach ? best : null;
   }

   public static void clear() {
      clinging = false;
      running = false;
      kickCooldown = 0;
   }
}
