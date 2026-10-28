package dev.baranhan.viltrumitecore.hero.sinclair.ability;

import dev.baranhan.viltrumitecore.hero.SafeSpot;
import dev.baranhan.viltrumitecore.hero.sinclair.Ovation;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairCombat;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.Stagecraft;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.AssistantEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Spectral Assistant (spec §5.2): summon on the look ray, two swaps, a 30 s cooldown from her exit. */
public final class Assistant {
   private Assistant() {
   }

   public static void press(ServerPlayer player, SinclairState state) {
      AssistantEntity assistant = Stagecraft.assistant(player, state);
      if (assistant != null && assistant.onStage()) {
         swap(player, state, assistant);
         return;
      }

      if (assistant != null || state.assistCooldown > 0 || state.busy()) {
         SinclairHero.refuse(player, state);
         return;
      }

      Vec3 point = summonPoint(player);
      if (point == null) {
         SinclairHero.refuse(player, state);
         return;
      }

      state.summonPoint = point;
      state.startMove(SinclairMove.SUMMON);
   }

   /** §5.2: ray ≤ 15; a block backs off 0.6 toward the player; an entity puts her beside it; SafeSpot radius 2. */
   @Nullable
   public static Vec3 summonPoint(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 wanted;
      LivingEntity enemy = Stagecraft.rayEnemy(player, SinclairRules.ASSIST_RANGE);
      BlockHitResult block = Stagecraft.blockHit(player, SinclairRules.ASSIST_RANGE);
      if (enemy != null) {
         Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize().scale(enemy.getBbWidth() * 0.5 + 0.9);
         wanted = enemy.position().add(side);
      } else if (block != null) {
         Vec3 back = eye.subtract(block.getLocation()).normalize().scale(SinclairRules.ASSIST_BLOCK_BACKOFF);
         wanted = block.getLocation().add(back);
      } else {
         wanted = eye.add(look.scale(SinclairRules.ASSIST_RANGE));
      }

      wanted = groundUnder(player, wanted);
      Optional<Vec3> spot = SafeSpot.find(wanted, new SafeSpot.LevelProbe(player.level(), player), SinclairRules.ASSIST_SAFE_RADIUS, 0.5);
      return spot.orElse(null);
   }

   /** Drop a point onto the floor below it (≤ 6 blocks), keeping it when there is none. */
   private static Vec3 groundUnder(ServerPlayer player, Vec3 at) {
      net.minecraft.world.phys.BlockHitResult floor = player.level().clip(new net.minecraft.world.level.ClipContext(at.add(0.0, 0.2, 0.0), at.add(0.0, -SafeSpot.MAX_DROP, 0.0),
         net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
      return floor.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at : floor.getLocation();
   }

   public static void tickMove(ServerPlayer player, SinclairState state) {
      if (state.move == SinclairMove.SUMMON && state.moveTick == SinclairRules.ASSIST_APPEAR_TICK) {
         spawn(player, state);
      } else if (state.move == SinclairMove.SWAP && state.moveTick == SinclairRules.SWAP_TELEPORT_TICK) {
         teleport(player, state);
      }
   }

   private static void spawn(ServerPlayer player, SinclairState state) {
      Vec3 point = state.summonPoint;
      state.summonPoint = null;
      if (point == null || !SafeSpot.ok(point, new SafeSpot.LevelProbe(player.level(), player))) {
         Optional<Vec3> again = point == null ? Optional.empty() : SafeSpot.find(point, new SafeSpot.LevelProbe(player.level(), player), SinclairRules.ASSIST_SAFE_RADIUS, 0.5);
         if (again.isEmpty()) {
            return;
         }

         point = again.get();
      }

      AssistantEntity assistant = SinclairEntities.ASSISTANT.get().create(player.level());
      if (assistant == null) {
         return;
      }

      assistant.setup(player, point.add(0.0, SinclairRules.ASSIST_HOVER, 0.0));
      player.level().addFreshEntity(assistant);
      state.assistant = assistant.getUUID();
      state.assistantId = assistant.getId();
      state.swapsUsed = 0;
      state.lastSwapAge = -999;
      state.assistLeaving = false;
      SinclairSounds.play(player.level(), point.add(0.0, 1.0, 0.0), SinclairSounds.ASSISTANT_APPEAR, 1.0F, 1.0F);
   }

   private static void swap(ServerPlayer player, SinclairState state, AssistantEntity assistant) {
      if (state.busy() || !SinclairRules.swapAllowed(state.swapsUsed, assistant.life(), assistant.life() - state.lastSwapAge)
         || spots(player, assistant) == null) {
         SinclairHero.refuse(player, state);
         return;
      }

      state.startMove(SinclairMove.SWAP);
      assistant.setState(AssistantEntity.SWAP_OUT, SinclairRules.SWAP_TELEPORT_TICK);
      state.swapsUsed++;
      state.lastSwapAge = assistant.life();
      SinclairSounds.play(player.level(), player.position().add(0.0, 1.0, 0.0), SinclairSounds.SWAP, 1.0F, 1.0F);
   }

   /** Both landing spots (player at her floor, her over his), or null when either is unsafe. */
   @Nullable
   private static Vec3[] spots(ServerPlayer player, AssistantEntity assistant) {
      Vec3 herFloor = assistant.position().subtract(0.0, SinclairRules.ASSIST_HOVER, 0.0);
      Optional<Vec3> forPlayer = SafeSpot.find(herFloor, new SafeSpot.LevelProbe(player.level(), player), SinclairRules.ASSIST_SAFE_RADIUS, 0.5);
      Optional<Vec3> forHer = SafeSpot.find(player.position(), new SafeSpot.LevelProbe(player.level(), assistant), SinclairRules.ASSIST_SAFE_RADIUS, 0.5);
      return forPlayer.isPresent() && forHer.isPresent() ? new Vec3[]{forPlayer.get(), forHer.get()} : null;
   }

   private static void teleport(ServerPlayer player, SinclairState state) {
      AssistantEntity assistant = Stagecraft.assistant(player, state);
      if (assistant == null || assistant.leaving()) {
         return;
      }

      Vec3[] spots = spots(player, assistant);
      if (spots == null) {
         assistant.setState(AssistantEntity.SWAP_IN, SinclairRules.SWAP_TICKS - SinclairRules.SWAP_TELEPORT_TICK);
         return;
      }

      Vec3 from = player.position();
      player.teleportTo(spots[0].x, spots[0].y, spots[0].z);
      player.fallDistance = 0.0F;
      player.setDeltaMovement(Vec3.ZERO);
      player.hurtMarked = true;
      assistant.relocate(spots[1].add(0.0, SinclairRules.ASSIST_HOVER, 0.0));
      assistant.setState(AssistantEntity.SWAP_IN, SinclairRules.SWAP_TICKS - SinclairRules.SWAP_TELEPORT_TICK);
      Prestige.refill(state);
      SinclairSounds.play(player.level(), spots[0].add(0.0, 1.0, 0.0), SinclairSounds.SWAP, 0.8F, 1.3F);
      if (SinclairCombat.enemyNear(player, spots[0], SinclairRules.OVATION_RANGE) || SinclairCombat.enemyNear(player, from, SinclairRules.OVATION_RANGE)) {
         Ovation.grant(player);
      }
   }

   /** Server tick: her exit starts the cooldown; a vanished assistant clears the slot. */
   public static void tick(ServerPlayer player, SinclairState state) {
      if (state.assistant == null) {
         return;
      }

      AssistantEntity assistant = Stagecraft.assistant(player, state);
      if (assistant == null || assistant.leaving() || !assistant.isAlive()) {
         if (!state.assistLeaving) {
            state.assistLeaving = true;
            state.assistCooldown = SinclairRules.ASSIST_COOLDOWN;
         }

         if (assistant == null) {
            state.assistant = null;
            state.assistantId = -1;
            state.assistLeaving = false;
         }
      }
   }

   public static void dismiss(ServerPlayer player, SinclairState state, boolean instant) {
      AssistantEntity assistant = Stagecraft.assistant(player, state);
      if (assistant != null) {
         if (instant) {
            assistant.discard();
         } else {
            assistant.leave();
         }
      }

      if (instant) {
         state.assistant = null;
         state.assistantId = -1;
      }
   }
}
