package dev.baranhan.viltrumitecore.hero.budget.multiverse;

import dev.baranhan.viltrumitecore.hero.budget.BudgetFaction;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroSpec;
import dev.baranhan.viltrumitecore.hero.budget.BudgetHeroes;
import dev.baranhan.viltrumitecore.hero.budget.BudgetMoves;
import dev.baranhan.viltrumitecore.hero.budget.BudgetScene;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * MULTIVERSE COLLAPSE WORLD EVENT. One running instance per server, in memory
 * only: rift, invasion, faction war, champions, final collapse, closing. The
 * world is never changed (no dimension, no block damage); every NPC is a
 * {@link MultiverseFighter} of this session and is discarded on the end.
 */
public final class MultiverseCollapse {
   public enum Phase {
      RIFT,
      INVASION,
      WAR,
      CHAMPIONS,
      COLLAPSE,
      CLOSING
   }

   @Nullable
   private static MultiverseCollapse active;
   private static int sessions = 1000;

   private final ResourceKey<Level> dimension;
   private final Vec3 origin;
   private final Vec3 portal;
   private final Set<ChaosModifier> mods;
   private final int session;
   private final Random random = new Random();
   private final ServerBossEvent integrity;
   private final List<UUID> wave = new ArrayList<>();
   private Phase phase = Phase.RIFT;
   private int phaseTicks;
   private int pause;
   private int toSpawn;
   private int noPlayerTicks;
   private boolean finalSpawned;

   private MultiverseCollapse(ServerLevel level, Vec3 origin, Set<ChaosModifier> mods) {
      this.dimension = level.dimension();
      this.origin = origin;
      this.portal = origin.add(0.0, 7.0, 0.0);
      this.mods = mods.isEmpty() ? EnumSet.noneOf(ChaosModifier.class) : EnumSet.copyOf(mods);
      this.session = ++sessions;
      this.integrity = new ServerBossEvent(Component.translatable("multiverse.viltrumitecore.integrity.critical").withStyle(ChatFormatting.BOLD),
         BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);
      this.integrity.setCreateWorldFog(true);
   }

   @Nullable
   public static MultiverseCollapse active() {
      return active;
   }

   public static boolean start(ServerLevel level, Vec3 near, Set<ChaosModifier> mods) {
      if (active != null) {
         return false;
      }

      BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(near));
      active = new MultiverseCollapse(level, Vec3.atBottomCenterOf(ground), mods);
      active.enter(level, Phase.RIFT);
      return true;
   }

   /** Ends the event now; {@code reason} null = silent (server stop). */
   public static boolean stop(MinecraftServer server, @Nullable String reasonKey) {
      MultiverseCollapse event = active;
      if (event == null) {
         return false;
      }

      active = null;
      event.integrity.removeAllPlayers();
      MultiverseCleanup.discardSession(server, event.session);
      ServerLevel level = server.getLevel(event.dimension);
      if (reasonKey != null && level != null) {
         MultiverseHud.chat(MultiverseHud.near(level, event.origin, 256.0), Component.translatable(reasonKey).withStyle(ChatFormatting.LIGHT_PURPLE));
      }

      return true;
   }

   public static void tick(MinecraftServer server) {
      MultiverseCollapse event = active;
      if (event == null) {
         return;
      }

      ServerLevel level = server.getLevel(event.dimension);
      if (level == null) {
         stop(server, null);
         return;
      }

      event.tick(level);
   }

   public Phase phase() {
      return this.phase;
   }

   public Set<ChaosModifier> modifiers() {
      return this.mods;
   }

   public Vec3 origin() {
      return this.origin;
   }

   public int aliveWave(ServerLevel level) {
      int alive = 0;
      for (UUID id : this.wave) {
         Entity entity = level.getEntity(id);
         if (entity != null && entity.isAlive()) {
            alive++;
         }
      }

      return alive;
   }

   // ---- state machine --------------------------------------------------

   /**
    * Players of the event: inside the arena, or close to one of its living
    * NPCs (a boss fight may drift past the arena edge; that is not a defeat).
    */
   private List<ServerPlayer> arenaPlayers(ServerLevel level) {
      List<ServerPlayer> players = new ArrayList<>(MultiverseHud.near(level, this.origin, MultiverseRules.ARENA_RADIUS));
      double engage = MultiverseRules.ENGAGE_RADIUS * MultiverseRules.ENGAGE_RADIUS;
      for (ServerPlayer player : level.players()) {
         if (players.contains(player)) {
            continue;
         }
         for (UUID id : this.wave) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive() && entity.distanceToSqr(player) < engage) {
               players.add(player);
               break;
            }
         }
      }
      return players;
   }

   private void tick(ServerLevel level) {
      List<ServerPlayer> players = this.arenaPlayers(level);
      this.updateIntegrity(level, players);
      if (players.stream().noneMatch(p -> p.isAlive() && !p.isSpectator())) {
         if (++this.noPlayerTicks > MultiverseRules.NO_PLAYER_FAIL_TICKS) {
            this.fall(level);
            return;
         }
      } else {
         this.noPlayerTicks = 0;
      }

      this.phaseTicks++;
      this.portalParticles(level);
      if (this.phaseTicks % 20 == 0) {
         this.leash(level);
      }
      if (this.pause > 0) {
         if (--this.pause == 0) {
            this.enter(level, Phase.values()[this.phase.ordinal() + 1]);
         }
         return;
      }

      switch (this.phase) {
         case RIFT -> this.tickRift(level, players);
         case INVASION -> this.tickInvasion(level);
         case WAR -> this.tickWar(level);
         case CHAMPIONS -> {
            if (this.aliveWave(level) == 0) {
               this.next();
            }
         }
         case COLLAPSE -> this.tickCollapse(level, players);
         case CLOSING -> {
            if (this.phaseTicks >= MultiverseRules.CLOSING_TICKS) {
               stop(level.getServer(), "multiverse.viltrumitecore.ended");
            }
         }
      }
   }

   /** Knockback must not carry a wave NPC (e.g. the Final Champion) hundreds of blocks out of the arena. */
   private void leash(ServerLevel level) {
      for (UUID id : this.wave) {
         Entity entity = level.getEntity(id);
         if (entity != null && entity.isAlive() && MultiverseRules.beyondLeash(entity.position().distanceToSqr(this.origin.x, entity.getY(), this.origin.z))) {
            MultiverseFighter.pullBack(level, entity, this.origin, this.random.nextDouble() * Math.PI * 2.0);
         }
      }
   }

   private void next() {
      this.pause = this.phase == Phase.COLLAPSE ? 1 : MultiverseRules.phasePause(this.mods);
   }

   private void enter(ServerLevel level, Phase phase) {
      this.phase = phase;
      this.phaseTicks = 0;
      List<ServerPlayer> players = this.arenaPlayers(level);
      this.wave.clear();
      switch (phase) {
         case RIFT -> {
            MultiverseHud.title(players, "multiverse.viltrumitecore.title.initiated", "multiverse.viltrumitecore.sub.rift", ChatFormatting.DARK_PURPLE);
            MultiverseHud.flash(players, BudgetScene.MULTIVERSE_RIFT);
            MultiverseHud.sound(level, this.portal, SoundEvents.END_PORTAL_SPAWN, 4.0F, 0.6F);
            MultiverseHud.sound(level, this.portal, SoundEvents.WITHER_SPAWN, 3.0F, 0.8F);
            if (!this.mods.isEmpty()) {
               Component list = Component.empty();
               for (ChaosModifier modifier : this.mods) {
                  list = list.copy().append(" [").append(Component.translatable(modifier.langKey())).append("]");
               }
               MultiverseHud.chat(players, Component.translatable("multiverse.viltrumitecore.modifiers", list).withStyle(ChatFormatting.RED));
            }
         }
         case INVASION -> {
            MultiverseHud.title(players, "multiverse.viltrumitecore.title.threat", "multiverse.viltrumitecore.sub.invasion", ChatFormatting.RED);
            MultiverseHud.flash(players, BudgetScene.MULTIVERSE_FLASH);
            this.toSpawn = MultiverseRules.invaders(this.mods);
         }
         case WAR -> {
            MultiverseHud.title(players, "multiverse.viltrumitecore.title.war", "multiverse.viltrumitecore.sub.war", ChatFormatting.GOLD);
            MultiverseHud.sound(level, this.portal, SoundEvents.RAID_HORN.value(), 4.0F, 1.0F);
            this.spawnWar(level);
         }
         case CHAMPIONS -> {
            MultiverseHud.title(players, "multiverse.viltrumitecore.title.champions", "multiverse.viltrumitecore.sub.champions", ChatFormatting.AQUA);
            MultiverseHud.flash(players, BudgetScene.MULTIVERSE_FLASH);
            List<MultiverseBoss> champions = MultiverseRules.champions(this.mods, this.random);
            for (int i = 0; i < champions.size(); i++) {
               double angle = Math.PI * 2.0 * i / champions.size();
               this.spawnBoss(level, champions.get(i), this.origin.add(Math.cos(angle) * 6.0, 2.0, Math.sin(angle) * 6.0),
                  MultiverseRules.CHAMPION_HEALTH_FACTOR);
            }
         }
         case COLLAPSE -> {
            MultiverseHud.title(players, "multiverse.viltrumitecore.title.final", "multiverse.viltrumitecore.sub.final", ChatFormatting.DARK_RED);
            MultiverseHud.sound(level, this.portal, SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.5F);
            this.finalSpawned = false;
         }
         case CLOSING -> {
            MultiverseHud.title(players, "multiverse.viltrumitecore.title.saved", "multiverse.viltrumitecore.sub.closing", ChatFormatting.GREEN);
            MultiverseHud.flash(players, BudgetScene.MULTIVERSE_FLASH);
            MultiverseHud.sound(level, this.portal, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0F, 1.0F);
            MultiverseCleanup.discardSession(level.getServer(), this.session);
         }
      }
   }

   private void tickRift(ServerLevel level, List<ServerPlayer> players) {
      if (this.phaseTicks % 10 == 0) {
         level.sendParticles(ParticleTypes.FLASH, this.portal.x, this.portal.y, this.portal.z, 1, 0.0, 0.0, 0.0, 0.0);
         MultiverseHud.sound(level, this.portal, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.6F + this.random.nextFloat() * 0.4F);
      }

      if (this.phaseTicks == 50) {
         MultiverseHud.actionBar(players, Component.translatable("multiverse.viltrumitecore.integrity.critical").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
      }

      if (this.phaseTicks >= MultiverseRules.RIFT_TICKS) {
         this.next();
      }
   }

   private void tickInvasion(ServerLevel level) {
      if (this.toSpawn > 0 && this.phaseTicks % 10 == 0) {
         this.toSpawn--;
         BudgetHeroSpec spec = this.randomHero(BudgetFaction.DEMONS, BudgetFaction.CHAOS);
         Vec3 at = this.portal.add((this.random.nextDouble() - 0.5) * 3.0, -1.0, (this.random.nextDouble() - 0.5) * 3.0);
         MultiverseFighter fighter = this.spawnFighter(level, spec, at);
         if (fighter != null) {
            fighter.setDeltaMovement((this.random.nextDouble() - 0.5) * 0.8, 0.2, (this.random.nextDouble() - 0.5) * 0.8);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 20, 0.4, 0.6, 0.4, 0.05);
            MultiverseHud.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.7F);
         }
      }

      if (this.toSpawn <= 0 && this.aliveWave(level) == 0 || this.phaseTicks > MultiverseRules.invasionTimeout(this.mods)) {
         this.next();
      }
   }

   private void spawnWar(ServerLevel level) {
      List<BudgetFaction> factions = MultiverseRules.warFactions(this.random);
      int size = MultiverseRules.warGroupSize(this.mods);
      for (int g = 0; g < MultiverseRules.warGroups(); g++) {
         BudgetFaction faction = factions.get(g);
         double angle = Math.PI * 2.0 * g / MultiverseRules.warGroups();
         Vec3 camp = this.origin.add(Math.cos(angle) * 12.0, 0.0, Math.sin(angle) * 12.0);
         for (int i = 0; i < size; i++) {
            Vec3 at = this.ground(level, camp.add((this.random.nextDouble() - 0.5) * 4.0, 0.0, (this.random.nextDouble() - 0.5) * 4.0));
            this.spawnFighter(level, this.randomHero(faction), at);
         }

         MultiverseHud.chat(MultiverseHud.near(level, this.origin, MultiverseRules.ARENA_RADIUS),
            Component.translatable("multiverse.viltrumitecore.faction_arrives", Component.translatable(faction.langKey()))
               .withStyle(style -> style.withColor(faction.color)));
      }
   }

   private void tickWar(ServerLevel level) {
      if (this.phaseTicks % 20 != 0) {
         return;
      }

      Set<BudgetFaction> standing = EnumSet.noneOf(BudgetFaction.class);
      for (UUID id : this.wave) {
         if (level.getEntity(id) instanceof MultiverseFighter fighter && fighter.isAlive() && fighter.faction() != null) {
            standing.add(fighter.faction());
         }
      }

      if (MultiverseRules.warSettled(standing) || this.phaseTicks > MultiverseRules.warTimeout(this.mods)) {
         BudgetFaction winner = this.strongest(level, standing);
         if (winner != null) {
            MultiverseHud.chat(MultiverseHud.near(level, this.origin, MultiverseRules.ARENA_RADIUS),
               Component.translatable("multiverse.viltrumitecore.faction_wins", Component.translatable(winner.langKey()))
                  .withStyle(style -> style.withColor(winner.color).withBold(true)));
         }
         this.next();
      }
   }

   /** The standing faction with the most living fighters (the winner of a settled war). */
   @Nullable
   private BudgetFaction strongest(ServerLevel level, Set<BudgetFaction> standing) {
      BudgetFaction best = null;
      int bestCount = 0;
      for (BudgetFaction faction : standing) {
         int count = 0;
         for (UUID id : this.wave) {
            if (level.getEntity(id) instanceof MultiverseFighter fighter && fighter.isAlive() && fighter.faction() == faction) {
               count++;
            }
         }
         if (count > bestCount) {
            best = faction;
            bestCount = count;
         }
      }
      return best;
   }

   private void tickCollapse(ServerLevel level, List<ServerPlayer> players) {
      if (!this.finalSpawned) {
         int countdown = MultiverseRules.finalCountdown(this.mods);
         if (this.phaseTicks % 20 == 0 && this.phaseTicks < countdown) {
            MultiverseHud.actionBar(players, Component.translatable("multiverse.viltrumitecore.title.final").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
         }

         if (this.phaseTicks >= countdown) {
            this.finalSpawned = true;
            MultiverseHud.flash(players, BudgetScene.MULTIVERSE_FLASH);
            this.spawnBoss(level, MultiverseBoss.finalBoss(), this.portal.add(0.0, -2.0, 0.0), 1.0F);
         }
         return;
      }

      // Block-safe spectacle: particles and sounds only, the world is untouched.
      if (this.phaseTicks % 20 == 0) {
         for (int i = 0; i < 3; i++) {
            Vec3 at = this.origin.add((this.random.nextDouble() - 0.5) * 24.0, 1.0 + this.random.nextDouble() * 6.0, (this.random.nextDouble() - 0.5) * 24.0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(BudgetMoves.dust(0x9040FF + this.random.nextInt(0x60) * 0x10000, 2.5F), at.x, at.y, at.z, 20, 1.0, 2.0, 1.0, 0.0);
         }
         MultiverseHud.sound(level, this.origin, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.5F + this.random.nextFloat() * 0.3F);
      }

      if (this.aliveWave(level) == 0) {
         this.next();
      }
   }

   private void fall(ServerLevel level) {
      List<ServerPlayer> players = MultiverseHud.near(level, this.origin, 256.0);
      MultiverseHud.title(players, "multiverse.viltrumitecore.title.fallen", "multiverse.viltrumitecore.sub.fallen", ChatFormatting.DARK_RED);
      stop(level.getServer(), "multiverse.viltrumitecore.failed");
   }

   // ---- helpers --------------------------------------------------------

   private void updateIntegrity(ServerLevel level, List<ServerPlayer> players) {
      for (ServerPlayer player : List.copyOf(this.integrity.getPlayers())) {
         if (!players.contains(player)) {
            this.integrity.removePlayer(player);
         }
      }

      for (ServerPlayer player : players) {
         this.integrity.addPlayer(player);
      }

      if (this.phase == Phase.CLOSING) {
         this.integrity.setName(Component.translatable("multiverse.viltrumitecore.integrity.restored").withStyle(ChatFormatting.BOLD));
         this.integrity.setColor(BossEvent.BossBarColor.GREEN);
         this.integrity.setProgress(Math.min(1.0F, this.phaseTicks / (float)MultiverseRules.CLOSING_TICKS));
      } else {
         this.integrity.setProgress(Math.max(0.0F, 1.0F - this.phase.ordinal() / 5.0F));
      }
   }

   private void portalParticles(ServerLevel level) {
      if (this.phaseTicks % 4 != 0) {
         return;
      }

      double radius = this.phase == Phase.RIFT ? Math.min(4.0, this.phaseTicks / 20.0)
         : this.phase == Phase.CLOSING ? 4.0 * (1.0 - Math.min(1.0, this.phaseTicks / (double)MultiverseRules.CLOSING_TICKS)) : 4.0;
      int[] colors = {0x9040FF, 0xFF40C0, 0x40C0FF};
      int color = colors[(this.phaseTicks / 4) % colors.length];
      for (int i = 0; i < 24; i++) {
         double angle = Math.PI * 2.0 * i / 24.0;
         double x = this.portal.x + Math.cos(angle) * radius;
         double y = this.portal.y + Math.sin(angle) * radius;
         level.sendParticles(BudgetMoves.dust(color, 2.0F), x, y, this.portal.z, 1, 0.0, 0.0, 0.0, 0.0);
      }

      level.sendParticles(ParticleTypes.PORTAL, this.portal.x, this.portal.y, this.portal.z, 10, radius * 0.5, radius * 0.5, 0.2, 0.3);
   }

   private BudgetHeroSpec randomHero(BudgetFaction... factions) {
      List<BudgetHeroSpec> pool = new ArrayList<>();
      for (BudgetHeroSpec spec : BudgetHeroes.roster()) {
         for (BudgetFaction faction : factions) {
            if (spec.faction() == faction) {
               pool.add(spec);
            }
         }
      }

      return pool.get(this.random.nextInt(pool.size()));
   }

   private Vec3 ground(ServerLevel level, Vec3 at) {
      BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
      return Vec3.atBottomCenterOf(top);
   }

   @Nullable
   private MultiverseFighter spawnFighter(ServerLevel level, BudgetHeroSpec spec, Vec3 at) {
      MultiverseFighter fighter = MultiverseFighter.spawn(level, at, spec, this.session, null, 1.0F, MultiverseRules.npcCooldownScale(this.mods), 1.0F);
      if (fighter != null) {
         this.wave.add(fighter.getUUID());
      }

      return fighter;
   }

   private void spawnBoss(ServerLevel level, MultiverseBoss boss, Vec3 at, float healthFactor) {
      BudgetHeroSpec spec = MultiverseBosses.hero(boss);
      MultiverseFighter fighter = MultiverseFighter.spawn(level, at, spec, this.session, boss, healthFactor,
         MultiverseRules.npcCooldownScale(this.mods), MultiverseRules.bossSpecialScale(this.mods));
      if (fighter != null) {
         this.wave.add(fighter.getUUID());
         level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, 1.0, 1.0, 1.0, 0.0);
         MultiverseHud.sound(level, at, SoundEvents.WITHER_SPAWN, 2.0F, 1.2F);
         MultiverseHud.chat(MultiverseHud.near(level, this.origin, MultiverseRules.ARENA_RADIUS),
            Component.translatable("multiverse.viltrumitecore.boss_arrives", Component.translatable(boss.langKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
      }
   }
}
