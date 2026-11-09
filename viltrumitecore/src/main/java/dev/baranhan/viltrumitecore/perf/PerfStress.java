package dev.baranhan.viltrumitecore.perf;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.baranhan.viltrumitecore.ViltrumiteCore;
import dev.baranhan.viltrumitecore.entity.SuitDebrisEntity;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /vperf stress particles|entities|blocks|chaos|stop}: controlled, reversible load to measure the budgets.
 * Particles are sent to every player of the level, half near (6..40 blocks) and half far (60..240) so the
 * distance LOD shows; entities are suit debris (purely visual, they expire on their own); blocks toggle glass in
 * the air of a 48x12x48 box 24 blocks above the player through the shared block budget at LOW priority. Only air
 * is ever replaced and every touched block is put back on stop, at the end of the run and on server stop.
 */
@Mod.EventBusSubscriber(modid = ViltrumiteCore.MOD_ID)
public final class PerfStress {
   public static final int MAX_SECONDS = 600;
   static final int BOX_XZ = 48;
   static final int BOX_Y = 12;
   private static final int DEFAULT_SECONDS = 60;
   private static final int PARTICLE_BATCHES = 32;
   private static final int ENTITY_SPAWNS_PER_TICK = 40;
   private static final ParticleOptions[] PARTICLES = {
      ParticleTypes.LARGE_SMOKE, ParticleTypes.FLAME, ParticleTypes.CLOUD, ParticleTypes.END_ROD, ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.POOF
   };
   private static final BlockState FILL = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
   private static final RandomSource RANDOM = RandomSource.create(0x57E55L);
   private static Run run;

   private PerfStress() {
   }

   private static final class Run {
      final ServerLevel level;
      final UUID owner;
      final Vec3 center;
      final BlockPos origin;
      final long endTick;
      final int particlesPerTick;
      final int entityTarget;
      final int blocksPerTick;
      final List<Entity> entities = new ArrayList<>();
      final Long2ObjectOpenHashMap<BlockState> originals = new Long2ObjectOpenHashMap<>();
      int cursor;
      long particlesSent;
      long entitiesSpawned;
      long entitiesRefused;
      long blocksDone;
      long blocksDeferred;

      Run(ServerLevel level, UUID owner, Vec3 center, BlockPos origin, long endTick, int particles, int entities, int blocks) {
         this.level = level;
         this.owner = owner;
         this.center = center;
         this.origin = origin;
         this.endTick = endTick;
         this.particlesPerTick = particles;
         this.entityTarget = entities;
         this.blocksPerTick = blocks;
      }
   }

   @FunctionalInterface
   private interface Starter {
      int start(CommandContext<CommandSourceStack> ctx, int amount, int seconds) throws CommandSyntaxException;
   }

   static LiteralArgumentBuilder<CommandSourceStack> node() {
      return Commands.literal("stress")
         .executes(PerfStress::status)
         .then(kind("particles", "perTick", 4000, 50000, (ctx, n, s) -> start(ctx, n, 0, 0, s)))
         .then(kind("entities", "count", 600, 5000, (ctx, n, s) -> start(ctx, 0, n, 0, s)))
         .then(kind("blocks", "perTick", 4096, 20000, (ctx, n, s) -> start(ctx, 0, 0, n, s)))
         .then(Commands.literal("chaos")
            .executes(ctx -> start(ctx, 4000, 600, 4096, DEFAULT_SECONDS))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
               .executes(ctx -> start(ctx, 4000, 600, 4096, IntegerArgumentType.getInteger(ctx, "seconds")))))
         .then(Commands.literal("stop").executes(ctx -> {
            if (run == null) {
               ctx.getSource().sendFailure(Component.translatableWithFallback("viltrumitecore.perf.stress.none", "No stress is running"));
               return 0;
            }

            int restored = stopAll();
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.stress.stopped",
               "Stress stopped, %s blocks restored", restored), true);
            return 1;
         }));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> kind(String name, String arg, int fallback, int max, Starter starter) {
      return Commands.literal(name)
         .executes(ctx -> starter.start(ctx, fallback, DEFAULT_SECONDS))
         .then(Commands.argument(arg, IntegerArgumentType.integer(1, max))
            .executes(ctx -> starter.start(ctx, IntegerArgumentType.getInteger(ctx, arg), DEFAULT_SECONDS))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
               .executes(ctx -> starter.start(ctx, IntegerArgumentType.getInteger(ctx, arg), IntegerArgumentType.getInteger(ctx, "seconds")))));
   }

   private static int start(CommandContext<CommandSourceStack> ctx, int particles, int entities, int blocks, int seconds) throws CommandSyntaxException {
      ServerPlayer player = ctx.getSource().getPlayerOrException();
      stopAll();
      ServerLevel level = player.serverLevel();
      BlockPos feet = player.blockPosition();
      int top = level.getMaxBuildHeight() - BOX_Y - 1;
      BlockPos origin = new BlockPos(feet.getX() - BOX_XZ / 2, Math.min(top, feet.getY() + 24), feet.getZ() - BOX_XZ / 2);
      run = new Run(level, player.getUUID(), player.position(), origin, level.getServer().getTickCount() + seconds * 20L, particles, entities, blocks);
      ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.stress.started",
         "Stress for %s s: %s particles/tick, %s debris, %s blocks/tick (/vperf stress stop)", seconds, particles, entities, blocks), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> ctx) {
      Run r = run;
      if (r == null) {
         ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.stress.none", "No stress is running"), false);
         return 0;
      }

      long left = Math.max(0L, (r.endTick - r.level.getServer().getTickCount()) / 20L);
      ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("viltrumitecore.perf.stress.status",
         "Stress: %s s left · %s particles sent · %s debris alive (%s refused) · %s blocks changed, %s deferred",
         left, r.particlesSent, r.entities.size(), r.entitiesRefused, r.blocksDone, r.blocksDeferred), false);
      return 1;
   }

   public static boolean running() {
      return run != null;
   }

   /** Ends the run, removes its debris and puts every touched block back; returns how many blocks were restored. */
   public static int stopAll() {
      Run r = run;
      run = null;
      if (r == null) {
         return 0;
      }

      for (Entity entity : r.entities) {
         if (!entity.isRemoved()) {
            entity.discard();
         }
      }

      int restored = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (Long2ObjectMap.Entry<BlockState> entry : r.originals.long2ObjectEntrySet()) {
         long key = entry.getLongKey();
         m.set(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
         if (r.level.getBlockState(m).is(FILL.getBlock())) {
            r.level.setBlock(m, entry.getValue(), Block.UPDATE_CLIENTS);
            restored++;
         }
      }

      return restored;
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      stopAll();
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      Run r = run;
      if (event.phase != TickEvent.Phase.END || r == null) {
         return;
      }

      if (r.level.getServer().getTickCount() >= r.endTick) {
         int restored = stopAll();
         ServerPlayer owner = r.level.getServer().getPlayerList().getPlayer(r.owner);
         if (owner != null) {
            owner.sendSystemMessage(Component.translatableWithFallback("viltrumitecore.perf.stress.stopped", "Stress stopped, %s blocks restored", restored));
         }

         return;
      }

      Vec3 center = center(r);
      if (r.particlesPerTick > 0) {
         particles(r, center);
      }

      if (r.entityTarget > 0) {
         entities(r, center);
      }

      if (r.blocksPerTick > 0) {
         blocks(r);
      }
   }

   private static Vec3 center(Run r) {
      ServerPlayer owner = r.level.getServer().getPlayerList().getPlayer(r.owner);
      return owner != null && owner.level() == r.level ? owner.position() : r.center;
   }

   private static void particles(Run r, Vec3 c) {
      int per = Math.max(1, r.particlesPerTick / PARTICLE_BATCHES);
      List<ServerPlayer> viewers = r.level.players();
      for (int i = 0; i < PARTICLE_BATCHES; i++) {
         boolean far = (i & 1) == 1;
         double distance = far ? 60.0 + RANDOM.nextDouble() * 180.0 : 6.0 + RANDOM.nextDouble() * 34.0;
         double angle = RANDOM.nextDouble() * Math.PI * 2.0;
         double x = c.x + Math.cos(angle) * distance;
         double y = c.y + 2.0 + RANDOM.nextDouble() * 14.0;
         double z = c.z + Math.sin(angle) * distance;
         ParticleOptions type = PARTICLES[i % PARTICLES.length];
         for (ServerPlayer viewer : viewers) {
            r.level.sendParticles(viewer, type, true, x, y, z, per, 2.5, 2.0, 2.5, 0.02);
         }
      }

      r.particlesSent += (long)per * PARTICLE_BATCHES;
   }

   private static void entities(Run r, Vec3 c) {
      r.entities.removeIf(Entity::isRemoved);
      int missing = Math.min(ENTITY_SPAWNS_PER_TICK, r.entityTarget - r.entities.size());
      MarkId[] marks = MarkId.values();
      for (int i = 0; i < missing; i++) {
         double angle = RANDOM.nextDouble() * Math.PI * 2.0;
         double distance = 4.0 + RANDOM.nextDouble() * 80.0;
         Vec3 at = new Vec3(c.x + Math.cos(angle) * distance, c.y + 6.0 + RANDOM.nextDouble() * 10.0, c.z + Math.sin(angle) * distance);
         Vec3 velocity = new Vec3((RANDOM.nextDouble() - 0.5) * 0.6, 0.2 + RANDOM.nextDouble() * 0.4, (RANDOM.nextDouble() - 0.5) * 0.6);
         SuitDebrisEntity debris = SuitDebrisEntity.spawn(r.level, at, marks[RANDOM.nextInt(marks.length)], 0, velocity);
         if (debris.isAddedToWorld() && !debris.isRemoved()) {
            r.entities.add(debris);
            r.entitiesSpawned++;
         } else {
            r.entitiesRefused++;
         }
      }
   }

   private static void blocks(Run r) {
      int got = BlockUpdateBudget.take(r.level, PerfPriority.LOW, r.blocksPerTick);
      r.blocksDeferred += r.blocksPerTick - got;
      int volume = BOX_XZ * BOX_XZ * BOX_Y;
      int done = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int scanned = 0; done < got && scanned < volume; scanned++) {
         int i = r.cursor;
         r.cursor = (r.cursor + 1) % volume;
         m.set(r.origin.getX() + i % BOX_XZ, r.origin.getY() + i / (BOX_XZ * BOX_XZ), r.origin.getZ() + (i / BOX_XZ) % BOX_XZ);
         if (!r.level.isLoaded(m)) {
            continue;
         }

         long key = m.asLong();
         BlockState now = r.level.getBlockState(m);
         BlockState original = r.originals.get(key);
         if (original == null) {
            if (!now.isAir()) {
               continue;
            }

            original = now;
            r.originals.put(key, now);
         }

         BlockState next;
         if (now.is(FILL.getBlock())) {
            next = original;
         } else if (now == original) {
            next = FILL;
         } else {
            r.originals.remove(key);
            continue;
         }

         r.level.setBlock(m, next, Block.UPDATE_CLIENTS);
         done++;
      }

      if (done < got) {
         BlockUpdateBudget.refund(r.level, PerfPriority.LOW, got - done);
      }

      r.blocksDone += done;
   }
}
