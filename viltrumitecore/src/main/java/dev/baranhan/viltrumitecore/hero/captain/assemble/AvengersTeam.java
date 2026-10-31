package dev.baranhan.viltrumitecore.hero.captain.assemble;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.QuinjetEntity;
import dev.baranhan.viltrumitecore.hero.captain.CaptainState;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Entry points of the ultimate's team (spec §5.7, §7): starting the scene, ticking it from Steve's hero tick,
 * dismissing it on death / logout / dimension change / hero change, and the "is this one of Steve's team"
 * question used by the no-friendly-fire rules. Server only.
 */
public final class AvengersTeam {
   private AvengersTeam() {
   }

   /** Starts «Avengers, assemble!»; false when it cannot start here (refused by the caller). */
   public static boolean assemble(ServerPlayer player, CaptainState state) {
      if (state.scene != null || !(player.level() instanceof ServerLevel)) {
         return false;
      }
      state.scene = new AssembleScene(player, state);
      state.scene.start();
      return true;
   }

   public static void tick(ServerPlayer player, CaptainState state) {
      AssembleScene scene = state.scene;
      if (scene == null) {
         return;
      }
      if (scene.owner() != player || player.level() != scene.level()) {
         scene.dispose(false);
         state.scene = null;
         return;
      }
      if (dev.baranhan.viltrumitecore.hero.control.TimeFreeze.worldStopped(player.level())) {
         // A world pause / time stop (Reinhard's erasure, DIO) stops the team like every other clock: the
         // Avengers and the Quinjet are frozen entities, so the scene's own schedule waits for them too.
         return;
      }
      scene.tick();
      if (scene.done()) {
         scene.dispose(false);
         state.scene = null;
         if (state.move != null && state.move.ceremony()) {
            state.endMove();
         }
      }
   }

   /** Removes the team now ({@code graceful}: play the departure instead). */
   public static void dismiss(ServerPlayer player, CaptainState state, boolean graceful) {
      AssembleScene scene = state.scene;
      if (scene == null) {
         return;
      }
      if (graceful) {
         scene.departNow();
         return;
      }
      state.scene = null;
      scene.dispose(false);
      if (state.move != null && state.move.ceremony()) {
         state.endMove();
      }
   }

   /** True when {@code e} belongs to {@code owner}'s team (an Avenger or the Quinjet). */
   public static boolean ally(@Nullable Entity owner, @Nullable Entity e) {
      if (owner == null || e == null) {
         return false;
      }
      if (e instanceof AvengerEntity a) {
         return a.ownerUuid != null ? a.ownerUuid.equals(owner.getUUID()) : a.ownerId() == owner.getId();
      }
      return e instanceof QuinjetEntity q && q.ownerId() == owner.getId();
   }

   /** The scene an Avenger or Quinjet belongs to, or null (an orphan the entity removes itself). */
   @Nullable
   public static AssembleScene sceneOf(@Nullable ServerPlayer owner) {
      CaptainState state = owner == null ? null : CaptainState.of(owner);
      return state == null ? null : state.scene;
   }

   /** An Avenger is still part of a live scene (otherwise it leaves). */
   public static boolean member(AvengerEntity a) {
      AssembleScene scene = sceneOf(a.owner());
      return scene != null && scene.has(a);
   }

   public static boolean member(QuinjetEntity q) {
      AssembleScene scene = sceneOf(q.owner());
      return scene != null && scene.has(q);
   }

   /** An Avenger took a would-be lethal hit in the fight: it retreats (departure) instead of dying. */
   public static void retreat(AvengerEntity a) {
      AssembleScene scene = sceneOf(a.owner());
      if (scene != null) {
         scene.retreat(a);
      } else {
         a.vanish();
      }
   }

   public static void onMemberGone(AvengerEntity a) {
      AssembleScene scene = sceneOf(a.owner());
      if (scene != null) {
         scene.memberGone(a);
      }
   }
}
