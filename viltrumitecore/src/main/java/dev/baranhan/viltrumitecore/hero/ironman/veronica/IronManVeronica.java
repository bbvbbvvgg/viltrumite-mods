package dev.baranhan.viltrumitecore.hero.ironman.veronica;

import dev.baranhan.viltrumitecore.entity.VeronicaPodEntity;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.VeronicaMenuS2CPacket;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Server driver of Veronica (spec §12.1–§12.3): call the pod, open the suit
 * menu while it stands within {@link VeronicaPodEntity#MENU_RANGE}, validate
 * the choice and hand it to {@link IronManMarks}. Cooldown 3 min after the
 * pod leaves (extraCooldowns[2]).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class IronManVeronica {
   public static final int COOLDOWN = 3600;

   private IronManVeronica() {
   }

   /**
    * Slot press: the menu while the own pod stands near; otherwise call a new
    * pod (cooldown from the call) and send the old one away.
    */
   public static void press(ServerPlayer player, IronManState state) {
      VeronicaPodEntity pod = pod(player, state);
      if (pod != null && !pod.landed()) {
         return;
      }

      if (pod != null && player.distanceToSqr(pod) <= VeronicaPodEntity.MENU_RANGE * VeronicaPodEntity.MENU_RANGE) {
         openMenu(player, state, pod);
         return;
      }

      if (state.veronicaCooldown > 0) {
         player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.veronica_cooldown", (state.veronicaCooldown + 19) / 20), true);
         return;
      }

      java.util.UUID old = state.podUuid;
      ServerLevel level = player.serverLevel();
      Vec3 ground = DropPoint.pick(player.getRandom(), player.position(), surface(level));
      state.podUuid = VeronicaPodEntity.drop(player, ground).getUUID();
      state.veronicaCooldown = COOLDOWN;
      dev.baranhan.viltrumitecore.hero.ironman.IronManLines.say(player, dev.baranhan.viltrumitecore.hero.ironman.IronManLines.EVENT_VERONICA);
      if (old != null) {
         sendAway(player, old);
      }
   }

   /**
    * A pod leaves only when its online owner is no longer Iron Man or called a
    * newer pod. A missing state (not loaded yet) keeps it.
    */
   public static boolean retired(ServerPlayer owner, java.util.UUID podId) {
      if (!(owner instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer hero) || hero.getHeroId() != dev.baranhan.viltrumitecore.hero.HeroId.IRON_MAN) {
         return true;
      }

      IronManState state = IronManState.of(owner);
      return state != null && !podId.equals(state.podUuid);
   }

   /** The replaced pod flies off if its chunk is loaded; otherwise it leaves when it next loads (it checks the owner's pod). */
   static void sendAway(ServerPlayer player, java.util.UUID id) {
      for (ServerLevel level : player.server.getAllLevels()) {
         if (level.getEntity(id) instanceof VeronicaPodEntity pod) {
            pod.leave();
            return;
         }
      }
   }

   static void openMenu(ServerPlayer player, IronManState state, VeronicaPodEntity pod) {
      CoreMessages.sendToPlayer(new VeronicaMenuS2CPacket(VeronicaView.of(state.roster, hulkbusterCard(state), VeronicaView.POD_STAYS)), player);
   }

   /** Hulkbuster card: {@link VeronicaView#HULKBUSTER_ON} while any part is on, else its cooldown (0 = ready). */
   static int hulkbusterCard(IronManState state) {
      return state.hulkbuster.present() ? VeronicaView.HULKBUSTER_ON : state.hulkbuster.cooldown();
   }

   /** The menu choice: every rule is checked here, the client only displays (spec §12.3). */
   public static void choose(ServerPlayer player, int choice) {
      IronManState state = IronManState.of(player);
      // Mid-entry into an empty suit: a second delivery would strand the first mark.
      if (state == null || IronManMarks.controlled(player) || state.enteringSuitId >= 0) {
         return;
      }

      VeronicaPodEntity pod = pod(player, state);
      if (pod == null || !pod.landed() || player.distanceToSqr(pod) > VeronicaPodEntity.MENU_RANGE * VeronicaPodEntity.MENU_RANGE) {
         player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.veronica_far"), true);
         return;
      }

      Vec3 podTop = pod.position().add(0.0, 2.2, 0.0);
      if (choice == VeronicaView.HULKBUSTER) {
         switch (dev.baranhan.viltrumitecore.hero.ironman.hulkbuster.IronManHulkbuster.deliver(player, state, podTop)) {
            case BUSY -> player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.veronica_busy"), true);
            case UNAVAILABLE -> player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.mark_unavailable"), true);
            case NO_ROOM -> player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.hulkbuster_no_room"), true);
            default -> {
            }
         }

         return;
      }

      MarkId id = MarkId.byId(choice);
      if (id == null) {
         return;
      }

      // Marks cannot be put on under the Hulkbuster (plan stage 5 Global Constraints).
      if (state.hulkbuster.present()) {
         player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.veronica_busy"), true);
         return;
      }

      switch (IronManMarks.deliver(player, state, id, podTop)) {
         case BUSY -> player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.veronica_busy"), true);
         case UNAVAILABLE -> player.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.mark_unavailable"), true);
         default -> {
         }
      }
   }

   @Nullable
   public static VeronicaPodEntity pod(ServerPlayer player, IronManState state) {
      if (state.podUuid == null) {
         return null;
      }

      Entity entity = player.serverLevel().getEntity(state.podUuid);
      if (entity instanceof VeronicaPodEntity pod && pod.ownedBy(player) && pod.phase() != VeronicaPodEntity.Phase.LEAVING) {
         return pod;
      }

      return null;
   }

   public static void tick(ServerPlayer player, IronManState state) {
      if (state.veronicaCooldown > 0) {
         state.veronicaCooldown--;
      }
   }

   public static void onPodLanded(ServerPlayer owner, VeronicaPodEntity pod) {
   }

   public static void onPodLeft(ServerPlayer owner, VeronicaPodEntity pod) {
      IronManState state = IronManState.of(owner);
      if (state != null && pod.getUUID().equals(state.podUuid)) {
         state.podUuid = null;
      }
   }

   /** The landing blast never hits the owner (it is his delivery). */
   @SubscribeEvent
   public static void onDetonate(ExplosionEvent.Detonate event) {
      if (event.getExplosion().getDirectSourceEntity() instanceof VeronicaPodEntity pod) {
         event.getAffectedEntities().removeIf(entity -> entity == pod || pod.ownedBy(entity));
      }
   }

   static DropPoint.Surface surface(ServerLevel level) {
      return (x, z) -> {
         if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
         }

         BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
         BlockPos below = top.below();
         if (below.getY() < level.getMinBuildHeight()) {
            return null;
         }

         BlockState ground = level.getBlockState(below);
         if (!ground.getFluidState().isEmpty() || !level.getFluidState(top).isEmpty() || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return null;
         }

         return (double)top.getY();
      };
   }
}
