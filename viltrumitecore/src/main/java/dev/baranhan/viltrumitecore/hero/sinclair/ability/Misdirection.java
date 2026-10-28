package dev.baranhan.viltrumitecore.hero.sinclair.ability;

import dev.baranhan.viltrumitecore.hero.sinclair.SinclairHero;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairMove;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairRules;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairSounds;
import dev.baranhan.viltrumitecore.hero.sinclair.SinclairState;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.DecoyEntity;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairEntities;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Misdirection (spec §5.5): a snap on tick 4 of an 8 t cast; a running decoy keeps the course, Sinclair
 * gets a 50 t veil. The first attack or ability lifts the veil; the first hit from it is "Ta-da!".
 */
public final class Misdirection {
   /** Ticks after the veil lifts by an attack in which the first landed hit still counts as "Ta-da!". */
   public static final int TADA_GRACE = 20;

   private Misdirection() {
   }

   public static void press(ServerPlayer player, SinclairState state) {
      if (state.misCooldown > 0 || state.busy()) {
         SinclairHero.refuse(player, state);
         return;
      }

      state.startMove(SinclairMove.SNAP);
   }

   public static void tickMove(ServerPlayer player, SinclairState state) {
      if (state.moveTick != SinclairRules.MIS_SNAP_TICK) {
         return;
      }

      state.misCooldown = SinclairRules.MIS_COOLDOWN;
      SinclairSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), SinclairSounds.SNAP, 1.0F, 1.0F);
      discardDecoy(player, state);
      DecoyEntity decoy = SinclairEntities.DECOY.get().create(player.level());
      if (decoy != null) {
         Vec3 motion = player.getDeltaMovement();
         double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
         float course = speed > 0.02 ? (float) Math.toDegrees(Math.atan2(-motion.x, motion.z)) : player.getYRot();
         decoy.setup(player, course);
         player.level().addFreshEntity(decoy);
         state.decoy = decoy.getUUID();
         redirectAggro(player, decoy);
      }

      state.veil = SinclairRules.VEIL_TICKS;
      state.veilStrikeReady = false;
   }

   /** §5.5: mobs that targeted him within 24 turn to the decoy. */
   private static void redirectAggro(ServerPlayer player, DecoyEntity decoy) {
      AABB box = player.getBoundingBox().inflate(SinclairRules.VEIL_AGGRO_RANGE);
      for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == player)) {
         mob.setTarget(decoy);
      }
   }

   @Nullable
   public static DecoyEntity decoy(ServerPlayer player, SinclairState state) {
      if (state.decoy == null || !(player.level() instanceof ServerLevel level)) {
         return null;
      }

      Entity entity = level.getEntity(state.decoy);
      return entity instanceof DecoyEntity decoy && decoy.isAlive() && !decoy.revealed() ? decoy : null;
   }

   /** The first attack or ability from the veil lifts it and arms "Ta-da!" for the next landed hit. */
   public static void breakVeil(ServerPlayer player, SinclairState state, boolean attack) {
      if (state.veil > 0) {
         state.veil = 0;
         state.tadaWindow = TADA_GRACE;
      }
   }

   public static void tick(ServerPlayer player, SinclairState state) {
      if (state.veil > 0) {
         state.veil--;
      }

      if (state.tadaWindow > 0) {
         state.tadaWindow--;
      }

      if (state.decoy != null && decoy(player, state) == null && player.level() instanceof ServerLevel level && level.getEntity(state.decoy) == null) {
         state.decoy = null;
      }
   }

   public static void discardDecoy(ServerPlayer player, SinclairState state) {
      if (state.decoy != null && player.level() instanceof ServerLevel level) {
         Entity entity = level.getEntity(state.decoy);
         if (entity != null) {
            entity.discard();
         }
      }

      state.decoy = null;
   }
}
