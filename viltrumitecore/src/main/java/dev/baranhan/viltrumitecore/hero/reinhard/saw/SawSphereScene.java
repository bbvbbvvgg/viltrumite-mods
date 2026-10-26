package dev.baranhan.viltrumitecore.hero.reinhard.saw;

import dev.baranhan.viltrumitecore.hero.Annihilation;
import dev.baranhan.viltrumitecore.hero.CarveBudget;
import dev.baranhan.viltrumitecore.hero.HeroDestruction;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardAbility;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardState;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardTargets;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.SawSphereEndS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.SawSphereS2CPacket;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

/**
 * One running Saw Sphere, owned by its level ({@link SawSphereManager}), never by the caster:
 * a caster who dies, leaves, changes hero or dimension ends it at once through the ability's
 * cancel, and the per-tick watchdog ends it when anything else goes wrong. Server only.
 *
 * <p>Each tick: hold the caster on the hover curve, let every materialised disc cut what its
 * slab touches right now (bounded budget, no queue, nothing before or after), hit the bodies
 * it touches (one hit per target per {@link ReinhardRules#SAW_HIT_GATE} ticks), drop pending
 * container items and keep late viewers in sync. The end is idempotent.
 */
public final class SawSphereScene implements SawShells.Barrier {
   public static final TicketType<ChunkPos> TICKET = TicketType.create("viltrumitecore_saw_sphere", Comparator.comparingLong(ChunkPos::toLong),
      ReinhardRules.SAW_TICKET_TICKS);
   /** Server teleports the caster back only past this distance from the hover curve (the client holds him itself). */
   static final double HOLD_SLACK = 0.35;
   private static final int TICKET_DISTANCE = 1;

   private final ServerLevel level;
   private final UUID casterUuid;
   private final int casterId;
   private final ReinhardState state;
   public final double cx;
   public final double y0;
   public final double cz;
   public final float anchorYaw;
   private final SawShell shell;
   private final SawLoot loot = new SawLoot();
   private final List<ChunkPos> tickets = new ArrayList<>();
   private final Int2IntOpenHashMap lastHit = new Int2IntOpenHashMap();
   /** Positions a saw may never cut (protected, unbreakable, under Y0): asked once. */
   private final LongOpenHashSet denied = new LongOpenHashSet();
   private final Map<ServerPlayer, Boolean> viewers = new IdentityHashMap<>();
   private final double[][] discs = new double[SawChoreography.COUNT][3];
   private final boolean[] live = new boolean[SawChoreography.COUNT];
   private final SawSphereS2CPacket packet;
   private int age;
   private boolean ended;
   private int cutTotal;

   SawSphereScene(ServerPlayer caster, ReinhardState state, SawShell shell, float anchorYaw) {
      this.level = caster.serverLevel();
      this.casterUuid = caster.getUUID();
      this.casterId = caster.getId();
      this.state = state;
      this.cx = shell.cx;
      this.y0 = shell.y0;
      this.cz = shell.cz;
      this.anchorYaw = anchorYaw;
      this.shell = shell;
      this.lastHit.defaultReturnValue(Integer.MIN_VALUE / 2);
      this.packet = new SawSphereS2CPacket(this.casterId, this.cx, this.y0, this.cz, anchorYaw, 0, shell.cellCount(), shell.mask());
   }

   @Override
   public SawShell shell() {
      return this.shell;
   }

   @Override
   public boolean solid() {
      return !this.ended && SawChoreography.shellSolid(this.age);
   }

   @Override
   public int casterId() {
      return this.casterId;
   }

   public int age() {
      return this.age;
   }

   public boolean ended() {
      return this.ended;
   }

   public ServerLevel level() {
      return this.level;
   }

   public int cutTotal() {
      return this.cutTotal;
   }

   public int pendingItems() {
      return this.loot.pending();
   }

   /** Chunks the sphere and its discs cover (sphere radius plus a block of margin). */
   static List<ChunkPos> coveredChunks(double cx, double cz) {
      List<ChunkPos> out = new ArrayList<>();
      double r = SawChoreography.RADIUS + 1.0;
      int x0 = ((int) Math.floor(cx - r)) >> 4, x1 = ((int) Math.floor(cx + r)) >> 4;
      int z0 = ((int) Math.floor(cz - r)) >> 4, z1 = ((int) Math.floor(cz + r)) >> 4;
      for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) out.add(new ChunkPos(x, z));
      return out;
   }

   /** Starts the scene: tickets, barrier, first packets. */
   void begin() {
      for (ChunkPos pos : coveredChunks(this.cx, this.cz)) {
         this.level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
         this.tickets.add(pos);
      }
      SawShells.add(this.level, this);
      this.syncViewers();
   }

   /** One level tick; true once the scene is over. */
   public boolean tick() {
      if (this.ended) return true;
      ServerPlayer caster = this.caster();
      if (caster == null) {
         this.end(true);
         return true;
      }

      this.age++;
      if (this.age >= SawChoreography.LENGTH) {
         this.hold(caster);
         this.end(false);
         return true;
      }

      this.hold(caster);
      this.state.reid.combat();
      boolean anyCutting = false;
      for (int s = 0; s < SawChoreography.COUNT; s++) {
         this.live[s] = SawChoreography.cutting(s, this.age);
         if (this.live[s]) {
            SawChoreography.position(s, this.age, this.cx, this.y0, this.cz, this.anchorYaw, this.discs[s]);
            anyCutting = true;
         }
      }

      if (anyCutting) {
         this.cut(caster);
         this.hit(caster);
      }

      this.loot.flush(this.level, SawLoot.SPAWNS_PER_TICK);
      if (this.age % 5 == 0) this.syncViewers();
      return false;
   }

   /** The live caster when every condition of the scene still holds, else null (watchdog). */
   @Nullable
   private ServerPlayer caster() {
      ServerPlayer caster = this.level.getServer().getPlayerList().getPlayer(this.casterUuid);
      if (caster == null || caster.isRemoved() || !caster.isAlive() || caster.level() != this.level || caster.isSpectator()) return null;
      if (ReinhardState.of(caster) != this.state || this.state.sawSphere != this) return null;
      return caster;
   }

   private void hold(ServerPlayer caster) {
      Vec3 target = new Vec3(this.cx, this.y0 + SawChoreography.hover(this.age), this.cz);
      caster.setDeltaMovement(Vec3.ZERO);
      caster.fallDistance = 0.0F;
      if (caster.position().distanceToSqr(target) > HOLD_SLACK * HOLD_SLACK) {
         caster.connection.teleport(target.x, target.y, target.z, caster.getYRot(), caster.getXRot());
      }
   }

   /** Blocks every live disc slab touches this tick, under the tick-time budget shared by the level's scenes. */
   private void cut(ServerPlayer caster) {
      if (!dev.baranhan.viltrumitecore.hero.reinhard.DestructionLevel.of(this.state.mode).mayBreak()) return;
      int scenes = Math.max(1, SawSphereManager.get(this.level).size());
      int budget = Math.max(16, CarveBudget.perTick(ReinhardRules.SAW_BASE_BUDGET, this.level.getServer().getAverageTickTime()) / scenes);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      int n = SawChoreography.COUNT;
      int first = this.age % n;
      for (int k = 0; k < n && budget > 0; k++) {
         int s = (first + k) % n;
         if (this.live[s]) budget = this.cutDisc(caster, this.discs[s], pos, budget);
      }
   }

   private int cutDisc(ServerPlayer caster, double[] p, BlockPos.MutableBlockPos pos, int budget) {
      double rs = SawChoreography.SAW_RADIUS, half = SawChoreography.HALF_THICKNESS;
      double lo = p[1] - half, hi = p[1] + half;
      int by0 = (int) Math.floor(lo), by1 = (int) Math.ceil(hi) - 1;
      int bx0 = (int) Math.floor(p[0] - rs), bx1 = (int) Math.floor(p[0] + rs);
      int bz0 = (int) Math.floor(p[2] - rs), bz1 = (int) Math.floor(p[2] + rs);
      for (int by = by0; by <= by1; by++) {
         if (by + 1 <= this.y0 + 1.0E-3) continue;
         for (int bx = bx0; bx <= bx1; bx++) {
            double qx = Math.max(bx, Math.min(p[0], bx + 1.0)) - p[0];
            for (int bz = bz0; bz <= bz1; bz++) {
               double qz = Math.max(bz, Math.min(p[2], bz + 1.0)) - p[2];
               if (qx * qx + qz * qz > rs * rs) continue;
               long key = BlockPos.asLong(bx, by, bz);
               if (this.denied.contains(key)) continue;
               pos.set(bx, by, bz);
               BlockState block = this.level.getBlockState(pos);
               if (block.isAir()) continue;
               if (block.getBlock() instanceof LiquidBlock) {
                  this.denied.add(key);
                  continue;
               }

               if (by + top(block, pos) <= this.y0 + 1.0E-3) {
                  this.denied.add(key);
                  continue;
               }

               budget--;
               if (!this.mayBreak(caster, pos, block)) {
                  this.denied.add(key);
               } else {
                  if (block.hasBlockEntity()) this.loot.extract(this.level, pos);
                  if (HeroDestruction.setAir(this.level, pos.immutable(), HeroDestruction.CLEAR_FLAGS)) this.cutTotal++;
               }

               if (budget <= 0) return 0;
            }
         }
      }
      return budget;
   }

   /** Top of the block's collision (a full cube for shapeless blocks: grass, flowers, torches). */
   private double top(BlockState block, BlockPos pos) {
      VoxelShape shape = block.getCollisionShape(this.level, pos);
      return shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
   }

   /** Every server rule a player's break would meet: mobGriefing, unbreakable, spawn protection, border, claims (BreakEvent). */
   private boolean mayBreak(ServerPlayer caster, BlockPos pos, BlockState block) {
      if (!HeroDestruction.canDestroy(this.level, pos) || !this.level.mayInteract(caster, pos)) return false;
      return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(this.level, pos.immutable(), block, caster));
   }

   private void hit(ServerPlayer caster) {
      double r = SawChoreography.RADIUS;
      AABB area = new AABB(this.cx - r, this.y0, this.cz - r, this.cx + r, this.y0 + r + 1.0, this.cz + r);
      for (Entity e : this.level.getEntities((Entity) null, area, e -> e instanceof LivingEntity && e.isAlive() && e != caster)) {
         LivingEntity target = (LivingEntity) e;
         if (this.age - this.lastHit.get(target.getId()) < ReinhardRules.SAW_HIT_GATE) continue;
         AABB box = target.getBoundingBox();
         if (box.maxY <= this.y0 + 1.0E-3) continue;
         int disc = this.touching(box);
         if (disc < 0 || !ReinhardTargets.canHit(caster, target)) continue;
         this.lastHit.put(target.getId(), this.age);
         Vec3 contact = new Vec3(target.getX(), Math.max(box.minY, Math.min(box.maxY, this.discs[disc][1])), target.getZ());
         if (mortal(target)) {
            if (Annihilation.strike(caster, target) == Annihilation.Outcome.KILLED) HeroFx.flash(caster, contact);
         } else {
            target.invulnerableTime = 0;
            target.hurt(this.level.damageSources().playerAttack(caster), ReinhardRules.SAW_HERO_DAMAGE * target.getMaxHealth());
            HeroFx.flash(caster, contact);
         }
      }
   }

   /** Mobs and players without a hero form die in one hit; heroes take a share of max HP. */
   static boolean mortal(LivingEntity target) {
      if (!(target instanceof Player player)) return true;
      return player instanceof HeroPlayer hero && hero.getHeroId() == HeroId.HUMAN;
   }

   /** The first live disc whose slab touches the box, or -1. */
   private int touching(AABB box) {
      double rs = SawChoreography.SAW_RADIUS, half = SawChoreography.HALF_THICKNESS;
      for (int s = 0; s < SawChoreography.COUNT; s++) {
         if (!this.live[s]) continue;
         double[] p = this.discs[s];
         if (box.maxY <= p[1] - half || box.minY >= p[1] + half) continue;
         if (discTouches(box, p[0], p[2], rs)) return s;
      }
      return -1;
   }

   /** Pure: the disc (circle of radius {@code rs}) overlaps the box footprint. */
   public static boolean discTouches(AABB box, double x, double z, double rs) {
      double qx = Math.max(box.minX, Math.min(x, box.maxX)) - x;
      double qz = Math.max(box.minZ, Math.min(z, box.maxZ)) - z;
      return qx * qx + qz * qz <= rs * rs;
   }

   /** Start packets for players newly in range (also after a relog or a dimension round trip: {@link #forget}). */
   private void syncViewers() {
      double range = ReinhardRules.SAW_VIEW_RANGE;
      for (ServerPlayer player : this.level.players()) {
         if (this.viewers.containsKey(player)) continue;
         if (player.getId() != this.casterId && player.distanceToSqr(this.cx, this.y0, this.cz) > range * range) continue;
         this.viewers.put(player, Boolean.TRUE);
         CoreMessages.sendToPlayer(this.packet.withAge(this.age), player);
      }
   }

   /** The player's client dropped its scenes (left the level or logged in again): resend on the next sync. */
   void forget(ServerPlayer player) {
      this.viewers.remove(player);
   }

   /** Idempotent end: items dropped, tickets released, barrier gone, clients told, caster freed. */
   public void end(boolean interrupted) {
      if (this.ended) return;
      this.ended = true;
      this.loot.flushAll(this.level);
      for (ChunkPos pos : this.tickets) this.level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
      this.tickets.clear();
      SawShells.remove(this.level, this);
      SawSphereEndS2CPacket end = new SawSphereEndS2CPacket(this.casterId, interrupted);
      for (ServerPlayer viewer : this.viewers.keySet()) {
         if (!viewer.hasDisconnected()) CoreMessages.sendToPlayer(end, viewer);
      }
      this.viewers.clear();
      if (this.state.sawSphere == this) {
         this.state.sawSphere = null;
         if (this.state.action.is(ReinhardAbility.SAW_SPHERE)) this.state.action.cancel();
      }
      ServerPlayer caster = this.level.getServer().getPlayerList().getPlayer(this.casterUuid);
      if (caster != null) caster.fallDistance = 0.0F;
   }
}
