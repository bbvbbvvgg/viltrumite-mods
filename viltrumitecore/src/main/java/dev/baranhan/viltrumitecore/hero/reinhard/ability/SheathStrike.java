package dev.baranhan.viltrumitecore.hero.reinhard.ability;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ActionTimeline;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardSounds;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sheath Strike (spec §7, Holy Sword slot 3): only while Reid is sheathed
 * (the gate is {@code Reid.allows}). The sword, still in its sheath, swings in
 * a flat arc; on tick {@link ReinhardRules#SHEATH_HIT_TICK} everything Reinhard
 * may hit within {@link ReinhardRules#SHEATH_RADIUS} takes
 * {@link ReinhardRules#SHEATH_DAMAGE}, and those that accept external control
 * are stunned ({@link ControlKind#FREEZE}) for {@link ReinhardRules#SHEATH_STUN}
 * ticks: one fresh effect id per target, the game-time expiry releases it.
 * Pure parts are unit tested; the client draws white pixel stars over the
 * stunned heads from the control sync ({@link #showsStars}).
 */
public final class SheathStrike implements AbilityHandler {
   public static final ControlKind STUN_KIND = ControlKind.FREEZE;

   /** Offset of a target's feet from his feet: inside the sphere. */
   public static boolean inArea(double dx, double dy, double dz) {
      double r = ReinhardRules.SHEATH_RADIUS;
      return dx * dx + dy * dy + dz * dz <= r * r + 1.0E-9;
   }

   /** Game time the stun ends at. */
   public static long stunUntil(long gameTime) {
      return gameTime + ReinhardRules.SHEATH_STUN;
   }

   /** A target that refuses external freeze (e.g. a hero immune to control) only takes the damage. */
   public static boolean stuns(boolean allowsFreeze) {
      return allowsFreeze;
   }

   /** Client: stars only over a freeze cast by a Reinhard (Regulus' freeze keeps its own outline). */
   public static boolean showsStars(int kindOrdinal, boolean casterIsReinhard) {
      return casterIsReinhard && kindOrdinal == STUN_KIND.ordinal();
   }

   public static void start(ReinhardState state) {
      state.cooldowns[ReinhardAbility.SHEATH_STRIKE.ordinal()] = ReinhardRules.SHEATH_COOLDOWN;
      state.action.start(ReinhardAbility.SHEATH_STRIKE, ReinhardRules.SHEATH_TICKS, -1);
   }

   public static boolean hitNow(ActionTimeline action) {
      return action.is(ReinhardAbility.SHEATH_STRIKE) && action.elapsed() == ReinhardRules.SHEATH_HIT_TICK;
   }

   @Override
   public boolean tryStart(ServerPlayer player, ReinhardState state) {
      start(state);
      return true;
   }

   @Override
   public void tick(ServerPlayer player, ReinhardState state) {
      if (hitNow(state.action)) {
         strike(player);
      }
   }

   private static void strike(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      Vec3 feet = player.position();
      double r = ReinhardRules.SHEATH_RADIUS;
      ControlManager controls = ControlManager.get(level);
      long until = stunUntil(level.getGameTime());
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(r),
         e -> inArea(e.getX() - feet.x, e.getY() - feet.y, e.getZ() - feet.z) && ReinhardTargets.canHit(player, e))) {
         if (Strikes.hit(player, e, ReinhardRules.SHEATH_DAMAGE) && e.isAlive()
            && stuns(HeroRegistry.allowsExternalControl(e, STUN_KIND))) {
            controls.tryAcquire(e, player.getUUID(), UUID.randomUUID(), STUN_KIND, until);
         }
      }

      BlockPos ground = FistCombo.groundUnder(level, feet);
      BlockState material = ground != null ? level.getBlockState(ground) : null;
      HeroFx.slam(player, ground != null ? Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0) : feet, material, (float) r);
      ReinhardSounds.playAt(player, feet, ReinhardSounds.SHEATH_STRIKE.get(), 1.4F, 1.6F);
   }
}
