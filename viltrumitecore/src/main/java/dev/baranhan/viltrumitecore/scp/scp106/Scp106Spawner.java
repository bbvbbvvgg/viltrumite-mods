package dev.baranhan.viltrumitecore.scp.scp106;

import dev.baranhan.viltrumitecore.city.CityManager;
import dev.baranhan.viltrumitecore.city.profile.WalkGrid;
import dev.baranhan.viltrumitecore.config.ViltrumiteCoreConfig;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Natural appearance of SCP-106 (design §2.1): a rare roll every few minutes, only in the prepared city of Atheria,
 * twice as likely at night, one Old Man at a time and a long cooldown after each. It rises out of the pavement
 * 14..28 blocks from one player who can be taken, out of sight when possible, and hunts that player.
 */
public final class Scp106Spawner {
   private static final RandomSource RANDOM = RandomSource.create();

   public enum Result {
      SPAWNED, DISABLED, NO_CITY, PEACEFUL, EXISTS, COOLDOWN, ROLL_FAILED, NO_PLAYER, NO_SITE
   }

   public record Attempt(Result result, @Nullable Scp106Entity spawned) {
   }

   private Scp106Spawner() {
   }

   static void tick(MinecraftServer server) {
      if (server.getTickCount() % 20 != 11) {
         return;
      }
      Scp106SavedData data = Scp106SavedData.get(server);
      if (!data.naturalEnabled()) {
         return;
      }
      long now = server.overworld().getGameTime();
      if (now < data.nextCheck()) {
         return;
      }
      data.setNextCheck(now + Scp106Rules.checkTicks(ViltrumiteCoreConfig.INSTANCE.scp106NaturalCheckSeconds));
      Attempt a = attempt(server, false);
      Scp106Entity.LOG.debug("SCP-106 natural check: {}", a.result());
   }

   /** One natural attempt now; {@code force} skips the roll and the cooldown (QA), never the other rules. */
   public static Attempt attempt(MinecraftServer server, boolean force) {
      Scp106SavedData data = Scp106SavedData.get(server);
      if (!data.naturalEnabled() && !force) {
         return new Attempt(Result.DISABLED, null);
      }
      ServerLevel level = null;
      CityManager city = null;
      for (ServerLevel l : server.getAllLevels()) {
         CityManager c = CityManager.of(l);
         if (c != null) {
            level = l;
            city = c;
            break;
         }
      }
      if (city == null) {
         return new Attempt(Result.NO_CITY, null);
      }
      if (level.getDifficulty() == Difficulty.PEACEFUL) {
         return new Attempt(Result.PEACEFUL, null);
      }
      for (Scp106Entity e : Scp106Entity.loaded()) {
         if (!e.pocketHunter()) {
            return new Attempt(Result.EXISTS, null);
         }
      }
      long now = server.overworld().getGameTime();
      long cd = Scp106Rules.cooldownTicks(ViltrumiteCoreConfig.INSTANCE.scp106NaturalCooldownMinutes);
      if (!force && data.lastNatural() != Long.MIN_VALUE && now - data.lastNatural() < cd) {
         return new Attempt(Result.COOLDOWN, null);
      }
      double chance = Scp106Rules.naturalChance(ViltrumiteCoreConfig.INSTANCE.scp106NaturalChancePercent, level.isNight());
      if (!force && RANDOM.nextDouble() >= chance) {
         return new Attempt(Result.ROLL_FAILED, null);
      }
      List<ServerPlayer> prey = new ArrayList<>();
      for (ServerPlayer p : level.players()) {
         if (Scp106Pocket.canTake(p) && city.profile().inArena(p.getBlockX(), p.getBlockY(), p.getBlockZ())) {
            prey.add(p);
         }
      }
      if (prey.isEmpty()) {
         return new Attempt(Result.NO_PLAYER, null);
      }
      ServerPlayer anchor = prey.get(RANDOM.nextInt(prey.size()));
      final ServerLevel lv = level;
      final CityManager ct = city;
      WalkGrid grid = city.grid();
      for (int round = 0; round < 4; round++) {
         final boolean hide = round < 2;
         long cell = grid.randomCell(RANDOM::nextDouble, anchor.getX(), anchor.getZ(), 14.0, 28.0, false, 24,
            (x, feetY, z) -> validSite(lv, ct, x, feetY, z, hide));
         if (cell == Long.MIN_VALUE) {
            continue;
         }
         int x = WalkGrid.unpackX(cell);
         int z = WalkGrid.unpackZ(cell);
         Vec3 at = new Vec3(x + 0.5, grid.feetY(x, z), z + 0.5);
         Scp106Entity e = Scp106Entity.emergeAt(level, at, anchor, true);
         if (e == null) {
            return new Attempt(Result.NO_SITE, null);
         }
         data.markNatural(now);
         Scp106Entity.LOG.info("SCP-106 rose in the city at {} {} {} after {}", x, (int)at.y, z, anchor.getGameProfile().getName());
         return new Attempt(Result.SPAWNED, e);
      }
      return new Attempt(Result.NO_SITE, null);
   }

   /** A free spot on the pedestrian grid, loaded and ticking; first rounds also out of every player's sight. */
   static boolean validSite(ServerLevel level, CityManager city, int x, int feetY, int z, boolean hidden) {
      BlockPos feet = new BlockPos(x, feetY, z);
      if (!city.profile().inArena(x, feetY, z) || !level.isPositionEntityTicking(feet) || !Scp106Entity.standable(level, feet)) {
         return false;
      }
      Vec3 head = new Vec3(x + 0.5, feetY + 1.6, z + 0.5);
      for (ServerPlayer p : level.players()) {
         if (p.isSpectator()) {
            continue;
         }
         double d2 = p.distanceToSqr(head);
         if (d2 < 10.0 * 10.0) {
            return false;
         }
         if (hidden && d2 < 64.0 * 64.0) {
            Vec3 look = p.getViewVector(1.0F);
            Vec3 to = head.subtract(p.getEyePosition()).normalize();
            if (look.dot(to) > 0.35) {
               HitResult hit = level.clip(new ClipContext(p.getEyePosition(), head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
               if (hit.getType() == HitResult.Type.MISS) {
                  return false;
               }
            }
         }
      }
      return true;
   }
}
