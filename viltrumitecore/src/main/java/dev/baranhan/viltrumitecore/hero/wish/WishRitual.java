package dev.baranhan.viltrumitecore.hero.wish;

import dev.baranhan.viltrumitecore.hero.ActionLocks;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.WishSceneS2CPacket;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Server side of a wish (spec §7, §11, §12): validation, one ritual per player, the root, the shared
 * {@link WishTimeline}, the hero change at the swap tick and every interrupt. Server main thread only.
 * The client never decides anything: it sends a wish id and draws what this class broadcasts.
 */
public final class WishRitual {
   public static final String REFUSED_UNKNOWN = "wish.viltrumitecore.refused.unknown";
   public static final String REFUSED_UNAVAILABLE = "wish.viltrumitecore.refused.unavailable";
   public static final String REFUSED_ALREADY = "wish.viltrumitecore.refused.already";
   public static final String REFUSED_NO_ITEM = "wish.viltrumitecore.refused.no_item";
   public static final String REFUSED_BUSY = "wish.viltrumitecore.refused.busy";
   public static final String REFUSED_TIME = "wish.viltrumitecore.refused.time_still";
   public static final String REFUSED_BOUND = "wish.viltrumitecore.refused.bound";
   public static final String REFUSED_NO_ROOM = "wish.viltrumitecore.refused.no_room";
   public static final String REFUSED_FAILED = "wish.viltrumitecore.refused.failed";
   public static final String INTERRUPTED = "wish.viltrumitecore.interrupted";
   private static final UUID ROOT_ID = UUID.fromString("0d1a6a5e-57a1-4c5e-9d3b-0d1a6a5e0001");
   private static final Map<UUID, Ritual> RITUALS = new HashMap<>();
   private static final Map<UUID, Long> REARM = new HashMap<>();
   private static boolean initialised;

   static final class Ritual {
      final Wish wish;
      final boolean firstChoice;
      int tick;
      boolean swapped;

      Ritual(Wish wish, boolean firstChoice) {
         this.wish = wish;
         this.firstChoice = firstChoice;
      }
   }

   private WishRitual() {
   }

   /** Common setup, once: the shared action lock (both sides). */
   public static void init() {
      if (!initialised) {
         initialised = true;
         ActionLocks.register(WishRitual::locks);
      }
   }

   /** A player inside a ritual starts no hero or kit ability (server: the ritual map, client: the scene view). */
   static boolean locks(LivingEntity entity) {
      if (!(entity instanceof Player)) {
         return false;
      }
      return entity.level().isClientSide() ? WishSceneView.active(entity.getId()) : RITUALS.containsKey(entity.getUUID());
   }

   public static boolean active(Player player) {
      return player != null && RITUALS.containsKey(player.getUUID());
   }

   /** The player may speak a wish: holds the item, or has not made the first hero choice yet. */
   static boolean mayWish(ServerPlayer player) {
      return player.getMainHandItem().is(ViltrumiteItems.OD_LAGNA_WISH.get()) || player.getOffhandItem().is(ViltrumiteItems.OD_LAGNA_WISH.get()) || firstChoice(player);
   }

   static boolean firstChoice(ServerPlayer player) {
      return player instanceof ViltrumiteCorePlayer core && !core.hasChosenRace();
   }

   /** WishChoiceC2SPacket: everything the client sent is checked here before anything starts. */
   public static void request(ServerPlayer player, String wishId) {
      Wish wish = OdLagnaWishes.byId(wishId);
      if (wish == null) {
         refuse(player, REFUSED_UNKNOWN);
         return;
      }
      if (!player.isAlive() || player.isSpectator()) {
         return;
      }
      long now = player.serverLevel().getGameTime();
      Long rearm = REARM.get(player.getUUID());
      if (RITUALS.containsKey(player.getUUID()) || rearm != null && now < rearm) {
         // A double click or a replayed packet: the running ritual is the answer.
         return;
      }
      if (!mayWish(player)) {
         refuse(player, REFUSED_NO_ITEM);
         return;
      }
      boolean first = firstChoice(player);
      if (!first && wish.grant().satisfied(player)) {
         refuse(player, REFUSED_ALREADY);
         return;
      }
      String reason = blocked(player);
      if (reason == null) {
         reason = wish.grant().refusal(player);
      }
      if (reason != null) {
         refuse(player, reason);
         return;
      }
      Ritual ritual = new Ritual(wish, first);
      RITUALS.put(player.getUUID(), ritual);
      root(player, true);
      // Held mouse actions of the current hero stop now: the body is the ritual's until the end.
      dev.baranhan.viltrumitecore.hero.HeldInputs.releaseAll(player);
      broadcast(player, WishSceneS2CPacket.START, ritual);
   }

   /** World rules a wish may not bypass (spec §12): a time stop holds the world, a control holds the body. */
   @Nullable
   static String blocked(ServerPlayer player) {
      if (player.getServer() != null && WorldPause.active(player.getServer())) {
         return REFUSED_TIME;
      }
      if (player.level() instanceof ServerLevel level && ControlManager.get(level).isControlled(player)) {
         return REFUSED_BOUND;
      }
      return null;
   }

   /** Server player tick (END): advance, swap, finish. */
   public static void tick(ServerPlayer player) {
      Ritual ritual = RITUALS.get(player.getUUID());
      if (ritual == null) {
         return;
      }
      ritual.tick++;
      if (!ritual.swapped) {
         String reason = blocked(player);
         if (reason != null) {
            abort(player, reason);
            return;
         }
      }
      if (ritual.tick == WishTimeline.SWAP) {
         swap(player, ritual);
         if (!RITUALS.containsKey(player.getUUID())) {
            return;
         }
      }
      if (ritual.tick >= WishTimeline.LENGTH) {
         finish(player, ritual);
      }
   }

   private static void swap(ServerPlayer player, Ritual ritual) {
      String reason = ritual.firstChoice ? null : ritual.wish.grant().refusal(player);
      if (reason != null) {
         abort(player, reason);
         return;
      }
      if (!ritual.wish.grant().grant(player)) {
         abort(player, REFUSED_FAILED);
         return;
      }
      ritual.swapped = true;
      if (player instanceof ViltrumiteCorePlayer core) {
         core.setChosenRace(true);
      }
      HeroRegistry.repairLoadout(player);
      // The hero cleanup of the change may have cleared transient modifiers: the body stays still to the end.
      root(player, true);
      broadcast(player, WishSceneS2CPacket.SWAP, ritual);
   }

   private static void finish(ServerPlayer player, Ritual ritual) {
      RITUALS.remove(player.getUUID());
      root(player, false);
      REARM.put(player.getUUID(), player.serverLevel().getGameTime() + WishTimeline.REARM);
      broadcast(player, WishSceneS2CPacket.END, ritual);
   }

   /** Death, logout, dimension change, a time stop or a control before the swap. */
   public static void abort(ServerPlayer player, @Nullable String reason) {
      Ritual ritual = RITUALS.remove(player.getUUID());
      root(player, false);
      if (ritual == null) {
         return;
      }
      REARM.put(player.getUUID(), player.serverLevel().getGameTime() + WishTimeline.REARM);
      broadcast(player, WishSceneS2CPacket.ABORT, ritual);
      if (reason != null) {
         refuse(player, reason);
      }
   }

   /** Logout: the player entity is leaving, nobody to tell. */
   public static void forget(ServerPlayer player) {
      Ritual ritual = RITUALS.remove(player.getUUID());
      root(player, false);
      REARM.remove(player.getUUID());
      if (ritual != null) {
         CoreMessages.sendToTracking(new WishSceneS2CPacket(WishSceneS2CPacket.ABORT, player.getId(), ritual.wish.id(), ritual.tick), player);
      }
   }

   /** A player started to see a ritual already running: catch up on its clock. */
   public static void startTracking(ServerPlayer tracker, Player target) {
      Ritual ritual = RITUALS.get(target.getUUID());
      if (ritual != null) {
         CoreMessages.sendToPlayer(new WishSceneS2CPacket(WishSceneS2CPacket.START, target.getId(), ritual.wish.id(), ritual.tick), tracker);
      }
   }

   public static void clearAll() {
      RITUALS.clear();
      REARM.clear();
   }

   private static void broadcast(ServerPlayer player, int phase, Ritual ritual) {
      CoreMessages.sendToTrackingAndSelf(new WishSceneS2CPacket(phase, player.getId(), ritual.wish.id(), ritual.tick), player);
   }

   /** The last refusal or interrupt key (the QA self-test reads it; fake players show no messages). */
   @Nullable
   static volatile String lastRefusal;

   private static void refuse(ServerPlayer player, String key) {
      lastRefusal = key;
      player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
   }

   private static void root(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      boolean has = speed.getModifier(ROOT_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(ROOT_ID, "Od Lagna's Wish", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(ROOT_ID);
      }
   }
}
