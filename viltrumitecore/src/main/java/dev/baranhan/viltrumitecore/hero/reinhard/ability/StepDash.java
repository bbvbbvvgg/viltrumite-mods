package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardActions;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Step Dash (spec §6.1, Restrained slot 2): a flash step of
 * {@link ReinhardRules#STEP_DISTANCE} blocks along the movement input (else the
 * look). Client-predicted: {@code client.reinhard.StepDashClient} moves the
 * player on the key edge; the press reaches the server once, through the generic
 * {@code HeroInputC2SPacket}. Accepted → cooldown, action lock, FX, a server copy
 * of the velocity (no motion packet: the client moved already, like the stage 2
 * air step). Refused → nothing spent, no FX; the server's velocity is sent to
 * stop the predicted dash, the position the client reached stays.
 */
public final class StepDash implements AbilityHandler {
   /** Vanilla ground friction of a plain block: 0.6 slipperiness × 0.91. */
   public static final double GROUND_FRICTION = 0.546;
   /** Vanilla air drag on horizontal motion. */
   public static final double AIR_FRICTION = 0.91;

   /** What a press does on the server (pure; unit tested). */
   public record Response(boolean started, boolean fx, boolean stopMotion, boolean correctPosition) {
   }

   public static Response response(ReinhardActions.Refusal refusal) {
      boolean ok = refusal == ReinhardActions.Refusal.NONE;
      return new Response(ok, ok, !ok, false);
   }

   /** The state side of a response: cooldown and the action lock only when started. */
   public static void apply(ReinhardState state, Response response) {
      if (!response.started()) {
         return;
      }

      state.cooldowns[ReinhardAbility.STEP_DASH.ordinal()] = ReinhardRules.STEP_COOLDOWN;
      state.action.start(ReinhardAbility.STEP_DASH, ReinhardRules.STEP_ACTION_TICKS, -1);
   }

   /**
    * Per-tick horizontal speed re-applied for {@code ticks} ticks so the whole slide (with the
    * friction decay after the last push) covers {@code distance}: d / (ticks + f / (1 − f)).
    */
   public static double velocity(double distance, int ticks, double friction) {
      double f = Mth.clamp(friction, 0.0, 0.98);
      return distance / (ticks + f / (1.0 - f));
   }

   /**
    * World-space unit direction of the dash: the movement input (forward / left impulse, vanilla
    * {@code moveRelative} rotation by yaw), none → the horizontal look.
    */
   public static Vec3 direction(float forward, float left, float yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      double sin = Math.sin(yaw);
      double cos = Math.cos(yaw);
      if (Math.abs(forward) < 1.0E-4F && Math.abs(left) < 1.0E-4F) {
         return new Vec3(-sin, 0.0, cos);
      }

      Vec3 v = new Vec3(left * cos - forward * sin, 0.0, forward * cos + left * sin);
      return v.normalize();
   }

   /** Horizontal friction the entity slides with right now (ground block × 0.91, else air). */
   public static double friction(Entity entity) {
      if (!entity.onGround()) {
         return AIR_FRICTION;
      }

      BlockPos below = below(entity);
      return entity.level().getBlockState(below).getFriction(entity.level(), below, entity) * 0.91;
   }

   /** The block whose slipperiness the entity slides on (vanilla: half a block under the feet). */
   static BlockPos below(Entity entity) {
      return BlockPos.containing(entity.getX(), entity.getBoundingBox().minY - 0.5000001, entity.getZ());
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      Response response = response(ReinhardActions.Refusal.NONE);
      Vec3 feet = player.position();
      // The server has no movement input for a player: its copy follows the look.
      Vec3 dir = direction(0.0F, 0.0F, player.getYRot());
      double speed = velocity(ReinhardRules.STEP_DISTANCE, ReinhardRules.STEP_TICKS, friction(player));
      apply(state, response);
      Vec3 v = player.getDeltaMovement();
      player.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
      HeroFx.launch(player, feet, 0.25F, player.level().getBlockState(below(player)));
      afterimage(player, feet, dir);
      ReinhardSounds.playAt(player, feet, ReinhardSounds.STEP_DASH.get(), 0.8F, 1.6F);
      return true;
   }

   @Override
   public void refused(ServerPlayer player, ReinhardState state, ReinhardActions.Refusal refusal) {
      if (response(refusal).stopMotion() || refusal == ReinhardActions.Refusal.NONE) {
         player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));
      }
   }

   /** A short trail of white puffs along the first blocks of the path (everyone nearby sees it). */
   private static void afterimage(ServerPlayer player, Vec3 feet, Vec3 dir) {
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }

      for (int i = 0; i < 6; i++) {
         Vec3 at = feet.add(dir.scale(i * 0.6)).add(0.0, 0.9, 0.0);
         level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 1, 0.15, 0.4, 0.15, 0.0);
      }
   }
}
