package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * BOSS RUSH CHALLENGE: one player against all eight multiverse bosses in a row
 * on the normal map. One run per player; death, logout, a dimension change or
 * {@code /multiverse bossrush stop} ends it and removes its boss.
 */
public final class MultiverseBossRush {
   private static final Map<UUID, MultiverseBossRush> RUNS = new HashMap<>();
   private static int sessions = 5000;

   private final UUID player;
   private final ResourceKey<Level> dimension;
   private final int session;
   private int wave = -1;
   @Nullable
   private UUID boss;
   private boolean bossDead;
   private int pause = 40;

   private MultiverseBossRush(ServerPlayer player) {
      this.player = player.getUUID();
      this.dimension = player.level().dimension();
      this.session = ++sessions;
   }

   public static boolean start(ServerPlayer player) {
      if (RUNS.containsKey(player.getUUID())) {
         return false;
      }

      RUNS.put(player.getUUID(), new MultiverseBossRush(player));
      MultiverseHud.title(List.of(player), "multiverse.viltrumitecore.title.bossrush", "multiverse.viltrumitecore.sub.bossrush", ChatFormatting.GOLD,
         MultiverseBoss.ALL.size());
      return true;
   }

   public static boolean anyRunning() {
      return !RUNS.isEmpty();
   }

   public static boolean running(UUID player) {
      return RUNS.containsKey(player);
   }

   public static int wave(UUID player) {
      MultiverseBossRush run = RUNS.get(player);
      return run == null ? -1 : run.wave;
   }

   public static boolean stop(MinecraftServer server, UUID player, @Nullable String reasonKey) {
      MultiverseBossRush run = RUNS.remove(player);
      if (run == null) {
         return false;
      }

      MultiverseCleanup.discardSession(server, run.session);
      ServerPlayer online = server.getPlayerList().getPlayer(player);
      if (online != null && reasonKey != null) {
         MultiverseHud.title(List.of(online), reasonKey, null, ChatFormatting.DARK_RED);
      }

      return true;
   }

   public static int stopAll(MinecraftServer server) {
      int count = 0;
      for (UUID id : List.copyOf(RUNS.keySet())) {
         if (stop(server, id, null)) {
            count++;
         }
      }

      return count;
   }

   /** MultiverseFighter death hook: lets a run tell "killed" from "unloaded". */
   static void onDeath(MultiverseFighter fighter) {
      for (MultiverseBossRush run : RUNS.values()) {
         if (fighter.getUUID().equals(run.boss)) {
            run.bossDead = true;
         }
      }
   }

   public static void tick(MinecraftServer server) {
      for (UUID id : List.copyOf(RUNS.keySet())) {
         MultiverseBossRush run = RUNS.get(id);
         if (run != null) {
            run.tickRun(server);
         }
      }
   }

   private void tickRun(MinecraftServer server) {
      ServerPlayer player = server.getPlayerList().getPlayer(this.player);
      if (player == null || !player.isAlive() || player.level().dimension() != this.dimension) {
         stop(server, this.player, "multiverse.viltrumitecore.title.bossrush_failed");
         return;
      }

      ServerLevel level = player.serverLevel();
      if (this.pause > 0) {
         this.pause--;
         if (this.pause % 20 == 0 && this.pause > 0 && this.wave >= 0) {
            player.displayClientMessage(Component.translatable("multiverse.viltrumitecore.next_boss", this.pause / 20).withStyle(ChatFormatting.YELLOW), true);
         }
         if (this.pause == 0) {
            this.spawnNext(level, player);
         }
         return;
      }

      Entity entity = this.boss == null ? null : level.getEntity(this.boss);
      if (entity != null && entity.isAlive()) {
         return;
      }

      if (!this.bossDead) {
         // Unloaded or removed without a kill: bring the same wave back, never a second copy.
         this.wave--;
         this.spawnNext(level, player);
         return;
      }

      if (this.wave >= MultiverseBoss.ALL.size() - 1) {
         RUNS.remove(this.player);
         MultiverseHud.title(List.of(player), "multiverse.viltrumitecore.title.bossrush_done", "multiverse.viltrumitecore.sub.bossrush_done",
            ChatFormatting.GOLD, MultiverseBoss.ALL.size());
         MultiverseHud.sound(level, player.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0F, 1.0F);
         return;
      }

      this.pause = MultiverseRules.BOSS_RUSH_PAUSE_TICKS;
      player.displayClientMessage(Component.translatable("multiverse.viltrumitecore.wave_cleared", this.wave + 1, MultiverseBoss.ALL.size())
         .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
   }

   private void spawnNext(ServerLevel level, ServerPlayer player) {
      this.wave++;
      this.bossDead = false;
      MultiverseBoss boss = MultiverseBoss.ALL.get(this.wave);
      Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
      look = look.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.normalize();
      BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(player.position().add(look.scale(8.0))));
      MultiverseFighter fighter = MultiverseFighter.spawn(level, Vec3.atBottomCenterOf(top), MultiverseBosses.hero(boss), this.session, boss, 1.0F, 1.0F, 1.0F);
      if (fighter == null) {
         stop(level.getServer(), this.player, "multiverse.viltrumitecore.title.bossrush_failed");
         return;
      }

      this.boss = fighter.getUUID();
      Component label = Component.translatable("multiverse.viltrumitecore.wave", this.wave + 1, MultiverseBoss.ALL.size(), Component.translatable(boss.langKey()))
         .withStyle(ChatFormatting.BOLD);
      fighter.setBarName(label);
      fighter.setTarget(player);
      MultiverseHud.title(List.of(player), "multiverse.viltrumitecore.title.wave", "multiverse.viltrumitecore.sub.wave", ChatFormatting.RED,
         this.wave + 1, MultiverseBoss.ALL.size(), Component.translatable(boss.langKey()));
      MultiverseHud.sound(level, fighter.position(), SoundEvents.WITHER_SPAWN, 1.5F, 1.2F);
   }
}
