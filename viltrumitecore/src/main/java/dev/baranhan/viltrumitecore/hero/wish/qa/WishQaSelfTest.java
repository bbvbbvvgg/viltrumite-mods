package dev.baranhan.viltrumitecore.hero.wish.qa;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.wish.OdLagnaWishes;
import dev.baranhan.viltrumitecore.hero.wish.Wish;
import dev.baranhan.viltrumitecore.hero.wish.WishQaAccess;
import dev.baranhan.viltrumitecore.hero.wish.WishRitual;
import dev.baranhan.viltrumitecore.hero.wish.WishTimeline;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

/**
 * Dedicated-server self-test of the wish ritual ({@code -Dviltrumitecore.wish.qa=true}, off in normal play,
 * like the DIO / Jinwoo QA hooks). A Forge fake player walks through every wish and the refusals on a real
 * running server: the real {@link WishRitual#request}, ticks, the hero change at the swap, the root and the
 * cleanup. Prints {@code [WishQA] PASS/FAIL} lines and a summary, then stops the server when
 * {@code -Dviltrumitecore.wish.qa.halt=true}.
 */
public final class WishQaSelfTest {
   public static final boolean ENABLED = Boolean.getBoolean("viltrumitecore.wish.qa");
   private static final boolean HALT = Boolean.getBoolean("viltrumitecore.wish.qa.halt");
   private static final Logger LOG = LogUtils.getLogger();
   private static final Deque<Step> STEPS = new ArrayDeque<>();
   private static final List<String> FAILS = new ArrayList<>();
   private static FakePlayer player;
   private static int passes;
   private static int warmup = 40;
   private static int wait;
   private static boolean done;

   /** One scripted action, then a condition polled every tick (with a time limit). */
   private record Step(String name, Runnable action, BooleanSupplier until, int limit, BooleanSupplier check) {
   }

   private WishQaSelfTest() {
   }

   public static void init() {
      if (ENABLED) {
         MinecraftForge.EVENT_BUS.addListener(WishQaSelfTest::onServerTick);
         LOG.info("[WishQA] enabled");
      }
   }

   private static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END || done) {
         return;
      }
      MinecraftServer server = event.getServer();
      if (warmup-- > 0) {
         return;
      }
      if (player == null) {
         setUp(server);
      }
      // a fake player is not ticked by the level: drive the ritual like the PlayerTick hook does
      WishRitual.tick(player);
      Step step = STEPS.peekFirst();
      if (step == null) {
         finish(server);
         return;
      }
      if (wait == 0) {
         WishQaAccess.clearLastRefusal();
         step.action().run();
      }
      wait++;
      if (step.until().getAsBoolean() || wait >= step.limit()) {
         boolean ok;
         try {
            ok = step.check().getAsBoolean();
         } catch (RuntimeException e) {
            LOG.error("[WishQA] {} threw", step.name(), e);
            ok = false;
         }
         if (ok) {
            passes++;
            LOG.info("[WishQA] PASS {} ({} ticks)", step.name(), wait);
         } else {
            FAILS.add(step.name());
            LOG.error("[WishQA] FAIL {} ({} ticks, hero={}, refusal={})", step.name(), wait, hero(), WishQaAccess.lastRefusal());
         }
         STEPS.pollFirst();
         wait = 0;
      }
   }

   private static void setUp(MinecraftServer server) {
      ServerLevel level = server.overworld();
      player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("0d1a6a5e-57a1-4c5e-9d3b-0d1a6a5e0a11"), "WishQA"));
      BlockPos spawn = level.getSharedSpawnPos();
      BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
      player.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0, 0);
      // a fresh profile carries the legacy default hero: start from the plain human like a new world
      dev.baranhan.viltrumitecore.hero.HeroRegistry.changeHero(player, dev.baranhan.viltrumitecore.hero.HeroId.HUMAN);
      chosen(true);
      LOG.info("[WishQA] fake player at {} hero={}", player.blockPosition(), hero());

      // refusals first
      STEPS.add(new Step("refuse: no item in hand", () -> {
         hand(false);
         WishRitual.request(player, "time");
      }, () -> false, 2, () -> !WishRitual.active(player) && WishRitual.REFUSED_NO_ITEM.equals(WishQaAccess.lastRefusal())));
      STEPS.add(new Step("refuse: unknown wish id", () -> {
         hand(true);
         WishRitual.request(player, "no_such_wish");
      }, () -> false, 2, () -> !WishRitual.active(player) && WishRitual.REFUSED_UNKNOWN.equals(WishQaAccess.lastRefusal())));
      STEPS.add(new Step("refuse: already the human (return wish)", () -> {
         hand(true);
         WishRitual.request(player, OdLagnaWishes.RETURN);
      }, () -> false, 2, () -> !WishRitual.active(player) && WishRitual.REFUSED_ALREADY.equals(WishQaAccess.lastRefusal())));
      // (the time-stop and control refusals need an online caster / controller: not reachable with a fake player)

      // every wish, in screen order, through the whole ritual
      for (Wish wish : OdLagnaWishes.all()) {
         if (wish.id().equals(OdLagnaWishes.RETURN)) {
            continue;
         }
         addWish(wish);
      }
      // spam: a second request inside a running ritual is ignored, the clock is not reset
      STEPS.add(new Step("double request keeps one ritual of " + WishTimeline.LENGTH + " ticks", () -> {
         hand(true);
         WishRitual.request(player, "time");
         WishRitual.request(player, "time");
      }, () -> !WishRitual.active(player), WishTimeline.LENGTH + 20, () -> WishQaAccess.lastRefusal() == null && "dio".equals(hero()) && wait == WishTimeline.LENGTH + 1));
      STEPS.add(new Step("rearm: an instant replay right after the end is ignored", () -> {
         hand(true);
         WishRitual.request(player, "death");
      }, () -> false, 2, () -> !WishRitual.active(player) && "dio".equals(hero())));
      pause("after the replay");
      // interrupt before the swap: nothing changes, the root is lifted
      STEPS.add(new Step("abort before the swap keeps the hero", () -> {
         hand(true);
         WishRitual.request(player, "thunder");
      }, () -> wait >= WishTimeline.SWAP - 10, WishTimeline.SWAP, () -> {
         boolean rooted = rooted();
         WishRitual.abort(player, WishRitual.INTERRUPTED);
         return rooted && !rooted() && !WishRitual.active(player) && "dio".equals(hero());
      }));
      pause("after the abort");
      // the return wish brings the human back
      addWish(OdLagnaWishes.byId(OdLagnaWishes.RETURN));
      // a fresh player (no hero chosen yet) still needs the item: there is no free first choice any more
      STEPS.add(new Step("refuse: fresh player without the item", () -> {
         hand(false);
         chosen(false);
         WishRitual.request(player, "sword");
      }, () -> false, 2, () -> !WishRitual.active(player) && "human".equals(hero()) && WishRitual.REFUSED_NO_ITEM.equals(WishQaAccess.lastRefusal())));
      // with the item the fresh player gets the hero and the choice is marked made
      addWish(OdLagnaWishes.byId("sword"));
      // the item is not consumed (the wish can be spoken again)
      STEPS.add(new Step("the item stays in hand", () -> hand(true), () -> true, 1,
         () -> player.getItemInHand(InteractionHand.MAIN_HAND).is(ViltrumiteItems.OD_LAGNA_WISH.get())));
      pause("after the fresh player's wish");
      addWish(OdLagnaWishes.byId(OdLagnaWishes.RETURN));
   }

   private static void addWish(Wish wish) {
      STEPS.add(new Step("wish " + wish.id() + " -> " + wish.heroKey(), () -> {
         hand(true);
         WishRitual.request(player, wish.id());
      }, () -> !WishRitual.active(player) && wait > 1, WishTimeline.LENGTH + 30, () -> {
         if (WishQaAccess.lastRefusal() != null) {
            return false;
         }
         return wish.heroKey().equals(hero()) && WishQaAccess.satisfied(wish, player) && !rooted() && chosen();
      }));
      pause("after " + wish.id());
   }

   private static void pause(String name) {
      STEPS.add(new Step("rearm pause " + name, () -> {
      }, () -> wait >= WishTimeline.REARM + 2, WishTimeline.REARM + 4, () -> true));
   }

   private static void finish(MinecraftServer server) {
      done = true;
      LOG.info("[WishQA] SUMMARY pass={} fail={} {}", passes, FAILS.size(), FAILS);
      if (HALT) {
         server.halt(false);
      }
   }

   private static void hand(boolean item) {
      player.setItemInHand(InteractionHand.MAIN_HAND, item ? new ItemStack(ViltrumiteItems.OD_LAGNA_WISH.get()) : ItemStack.EMPTY);
   }

   private static void chosen(boolean value) {
      if (player instanceof ViltrumiteCorePlayer core) {
         core.setChosenRace(value);
      }
   }

   private static boolean chosen() {
      return player instanceof ViltrumiteCorePlayer core && core.hasChosenRace();
   }

   private static String hero() {
      return player instanceof HeroPlayer hero && hero.getHeroId() != null ? hero.getHeroId().key() : "?";
   }

   private static boolean rooted() {
      var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      return speed != null && speed.getModifiers().stream().anyMatch(m -> "Od Lagna's Wish".equals(m.getName()));
   }
}
