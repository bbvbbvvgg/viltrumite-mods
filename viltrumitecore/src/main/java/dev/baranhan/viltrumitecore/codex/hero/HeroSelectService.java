package dev.baranhan.viltrumitecore.codex.hero;

import dev.baranhan.viltrumitecore.client.gui.HeroChoices;
import dev.baranhan.viltrumitecore.codex.CodexNetwork;
import dev.baranhan.viltrumitecore.codex.CodexProgress;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server rules of the hero select screen (key H / {@code /heroselect}). The client only asks; this class
 * decides: the feature switch, the permission level and the per-player cooldown from the config, then the
 * same safe path as the first-join choice ({@code HeroRegistry.changeHero} + {@code repairLoadout}), which
 * also respects one-holder heroes and running fatality scenes. {@link #decide} is pure and unit-tested.
 */
public final class HeroSelectService {
   static final String LAST_CHANGE = "ViltrumiteHeroSelectLast";
   public static final int MAX_COOLDOWN_SECONDS = 3600;

   private HeroSelectService() {
   }

   public enum Status {
      /** The switch happened. */
      OK(0, true),
      /** Status query: the player may switch now. */
      READY(1, true),
      COOLDOWN(2, false),
      NO_PERMISSION(3, false),
      DISABLED(4, false),
      /** The hero refused the player (one-holder slot taken, entry rules). */
      REFUSED(5, false),
      UNKNOWN_HERO(6, false),
      SAME_HERO(7, true),
      DEAD(8, false);

      public final int id;
      private final boolean canPick;

      Status(int id, boolean canPick) {
         this.id = id;
         this.canPick = canPick;
      }

      /** False when the screen should keep its button locked after this answer. */
      public boolean canPick() {
         return this.canPick;
      }

      public String langKey() {
         return "heroselect.viltrumitecore.status." + this.name().toLowerCase(Locale.ROOT);
      }

      public static Status byId(int id) {
         for (Status s : values()) {
            if (s.id == id) {
               return s;
            }
         }
         return REFUSED;
      }
   }

   /** {@code ticksLeft}: for COOLDOWN the wait, for OK the full cooldown that starts now, else 0. */
   public record Decision(Status status, int ticksLeft) {
   }

   /** Heroes on the screen: wish order, each once, the Human last (return to an ordinary life). */
   public static List<HeroId> selectable() {
      List<HeroId> heroes = new ArrayList<>(HeroChoices.heroes());
      heroes.add(HeroId.HUMAN);
      return heroes;
   }

   /** A selectable hero by key (the legacy viltrumite key means Homelander), or null. */
   @Nullable
   public static HeroId parse(@Nullable String key) {
      if (key == null || key.isBlank()) {
         return null;
      }
      HeroId hero = HeroId.fromKey(key.trim().toLowerCase(Locale.ROOT));
      return hero != null && selectable().contains(hero) ? hero : null;
   }

   public static int cooldownTicks(int seconds) {
      return Math.max(0, Math.min(MAX_COOLDOWN_SECONDS, seconds)) * 20;
   }

   /** Ticks until the next switch; never more than the cooldown even if the clock went back. */
   public static int ticksLeft(long now, long lastChange, int cooldownTicks) {
      if (lastChange < 0 || cooldownTicks <= 0) {
         return 0;
      }
      long left = lastChange + cooldownTicks - now;
      return (int) Math.max(0L, Math.min(cooldownTicks, left));
   }

   /**
    * Pure decision. {@code query} = the screen only asks for the status. {@code requested} is null for an
    * unknown hero.
    */
   public static Decision decide(boolean enabled, boolean permitted, boolean alive, boolean query, @Nullable HeroId requested,
      @Nullable HeroId current, long now, long lastChange, int cooldownTicks) {
      if (!enabled) {
         return new Decision(Status.DISABLED, 0);
      }
      if (!permitted) {
         return new Decision(Status.NO_PERMISSION, 0);
      }
      int left = ticksLeft(now, lastChange, cooldownTicks);
      if (query) {
         return left > 0 ? new Decision(Status.COOLDOWN, left) : new Decision(Status.READY, 0);
      }
      if (requested == null) {
         return new Decision(Status.UNKNOWN_HERO, left);
      }
      if (requested == current) {
         return new Decision(Status.SAME_HERO, left);
      }
      if (left > 0) {
         return new Decision(Status.COOLDOWN, left);
      }
      if (!alive) {
         return new Decision(Status.DEAD, 0);
      }
      return new Decision(Status.OK, Math.max(0, cooldownTicks));
   }

   /** Network entry point (server main thread). */
   public static void handle(ServerPlayer player, String heroKey) {
      if (!CodexProgress.isRealPlayer(player)) {
         return;
      }
      ViltrumiteCoreConfig cfg = ViltrumiteCoreConfig.INSTANCE;
      boolean enabled = cfg == null || cfg.heroSelectEnabled;
      int level = cfg == null ? 0 : Math.max(0, Math.min(4, cfg.heroSelectPermissionLevel));
      int cooldown = cooldownTicks(cfg == null ? 30 : cfg.heroSelectCooldownSeconds);
      boolean query = heroKey == null || heroKey.isBlank();
      HeroId requested = query ? null : parse(heroKey);
      HeroId current = currentHero(player);
      long now = now(player);
      Decision d = decide(enabled, level == 0 || player.hasPermissions(level), player.isAlive(), query, requested, current, now,
         lastChange(player), cooldown);
      Status status = d.status();
      if (status == Status.OK && requested != null) {
         boolean changed = HeroRegistry.changeHero(player, requested);
         if (!changed && HeroRegistry.get(player).id() != requested) {
            status = Status.REFUSED;
         } else {
            HeroRegistry.repairLoadout(player);
            setLastChange(player, now);
         }
      }
      String shown = requested != null ? requested.key() : current != null ? current.key() : "";
      CodexNetwork.heroSelectResult(player, status, shown, status == Status.REFUSED ? 0 : d.ticksLeft());
   }

   @Nullable
   static HeroId currentHero(ServerPlayer player) {
      return player instanceof HeroPlayer hero ? CodexProgress.canonical(hero.getHeroId()) : null;
   }

   /** Overworld game time: it survives restarts and is the same in every dimension. */
   static long now(ServerPlayer player) {
      return player.getServer() != null ? player.getServer().overworld().getGameTime() : player.level().getGameTime();
   }

   static long lastChange(Player player) {
      CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
      return persisted.contains(LAST_CHANGE) ? persisted.getLong(LAST_CHANGE) : -1L;
   }

   static void setLastChange(ServerPlayer player, long time) {
      CompoundTag data = player.getPersistentData();
      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      persisted.putLong(LAST_CHANGE, time);
      data.put(Player.PERSISTED_NBT_TAG, persisted);
   }
}
