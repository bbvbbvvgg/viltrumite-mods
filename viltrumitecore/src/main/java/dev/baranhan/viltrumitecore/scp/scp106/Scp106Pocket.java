package dev.baranhan.viltrumitecore.scp.scp106;

import com.mojang.logging.LogUtils;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.Scp106S2CPacket;
import dev.baranhan.viltrumitecore.scp.scp106.block.PocketBlocks;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Kind;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Portal;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.PortalType;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Purpose;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketLayout.Room;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketPainter;
import dev.baranhan.viltrumitecore.scp.scp106.pocket.PocketWorld;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Server side of the pocket dimension (design §5-§7): taking a player in, the anomalies, the abyss, sigils, the
 * key and the doors, the three real ways out, the moving mazes, the hunts of the Old Man inside, and every
 * safety net (death, logout, restart, a lost session, a player stuck in a wall or outside the instance).
 */
public final class Scp106Pocket {
   private static final Logger LOG = LogUtils.getLogger();
   public static final int ROUTE_BREACH = 1;
   public static final int ROUTE_SECRET = 2;
   public static final int ROUTE_HOME = 3;
   public static final int ROUTE_RELEASED = 4;
   public static final int ROUTE_RESCUED = 5;
   public static final int SCARE_ABYSS = 0;
   public static final int SCARE_DECOY = 1;
   public static final int SCARE_DRAG = 2;
   public static final int SCARE_STING = 3;
   public static final int SCARE_DEATH = 4;
   static final int HINT_ARRIVAL = 1;
   static final int HINT_ABYSS = 2;
   static final int HINT_SANCTUARY = 4;
   static final int HINT_BREACH = 8;
   static final int HINT_DECOY = 16;
   static final int HINT_ESCHER = 32;
   static final int HINT_GALLERY = 64;
   private static final Map<UUID, Vec3> LAST = new HashMap<>();
   private static final Map<UUID, Integer> STUCK = new HashMap<>();
   private static final Map<UUID, Long> NEXT_MESSAGE = new HashMap<>();
   private static final List<Runnable> QUEUE = new ArrayList<>();
   private static final List<Repaint> REPAINTS = new ArrayList<>();
   private static final Map<Integer, Long> NEXT_SHIFT = new HashMap<>();
   private static final RandomSource RANDOM = RandomSource.create();
   private static final int REPAINT_COLUMNS_PER_TICK = 640;

   private Scp106Pocket() {
   }

   private record Repaint(ServerLevel level, int slot, Room room, int[] cursor) {
   }

   // ------------------------------------------------------------------ queries

   public static boolean inPocket(ServerPlayer p) {
      return PocketWorld.is(p.level());
   }

   /** Can the Old Man take this player now (alive, survival, not already inside, not just escaped)? */
   public static boolean canTake(ServerPlayer p) {
      if (!p.isAlive() || p.isCreative() || p.isSpectator() || inPocket(p)) {
         return false;
      }
      Scp106SavedData data = Scp106SavedData.get(p.server);
      return data.session(p.getUUID()) == null && !data.immune(p.getUUID(), p.server.overworld().getGameTime());
   }

   private static Scp106SavedData data(MinecraftServer server) {
      return Scp106SavedData.get(server);
   }

   private static long now(MinecraftServer server) {
      return server.overworld().getGameTime();
   }

   // ------------------------------------------------------------------ in and out

   /** The transfer happens after the current tick (never in the middle of another entity's tick). */
   public static void queueCapture(ServerPlayer p) {
      QUEUE.add(() -> capture(p));
   }

   /** Takes a player into a brand new instance. False when not possible (already inside, no dimension). */
   public static boolean capture(ServerPlayer p) {
      if (!p.isAlive() || inPocket(p) || p.hasDisconnected()) {
         return false;
      }
      ServerLevel pocket = p.server.getLevel(PocketWorld.dimension());
      if (pocket == null) {
         LOG.error("SCP-106: the pocket dimension {} is missing (data pack disabled?)", PocketWorld.DIMENSION_ID);
         return false;
      }
      Scp106SavedData data = data(p.server);
      if (data.session(p.getUUID()) != null) {
         return false;
      }
      PocketSession s = data.open(p.getUUID());
      s.originDim = p.level().dimension().location().toString();
      s.ox = p.getX();
      s.oy = p.getY();
      s.oz = p.getZ();
      s.oyaw = p.getYRot();
      s.entered = now(p.server);
      s.nextStalk = s.entered + 3_600 + RANDOM.nextInt(1_200);
      data.setDirty();
      LOG.info("SCP-106 took {} into pocket instance {}", p.getGameProfile().getName(), s.slot);
      return toPocket(p, s);
   }

   /** Moves the player to the checkpoint of their instance (first arrival, reconnect, death, emergency). */
   static boolean toPocket(ServerPlayer p, PocketSession s) {
      ServerLevel pocket = p.server.getLevel(PocketWorld.dimension());
      if (pocket == null) {
         return false;
      }
      PocketLayout l = PocketWorld.layout(s.slot);
      int[] cp = checkpoint(l, s);
      double x = PocketLayout.slotOriginX(s.slot) + cp[0] + 0.5;
      double z = PocketLayout.slotOriginZ(s.slot) + cp[2] + 0.5;
      p.stopRiding();
      p.teleportTo(pocket, x, cp[1], z, p.getYRot(), 0.0F);
      p.setDeltaMovement(Vec3.ZERO);
      p.fallDistance = 0;
      boolean first = s.phase == PocketSession.ENTERING;
      s.phase = PocketSession.INSIDE;
      data(p.server).setDirty();
      LAST.put(p.getUUID(), p.position());
      if (first) {
         p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
         p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
      }
      sendState(p, s);
      return true;
   }

   private static int[] checkpoint(PocketLayout l, PocketSession s) {
      if (s.checkpoint >= 0 && s.checkpoint < l.rooms().size() && l.room(s.checkpoint).kind == Kind.SANCTUARY) {
         return PocketPainter.sanctuaryCheckpoint(l.room(s.checkpoint));
      }
      return PocketPainter.checkpoint(l.arrival());
   }

   /** A real way out (or an admin release): back where they were taken, then a short immunity. */
   public static void escape(ServerPlayer p, PocketSession s, int route) {
      s.phase = PocketSession.RETURNING;
      Scp106SavedData data = data(p.server);
      data.setDirty();
      ServerLevel target = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(s.originDim)));
      if (target == null || PocketWorld.is(target)) {
         target = p.server.overworld();
      }
      Vec3 at = safeReturn(target, new Vec3(s.ox, s.oy, s.oz));
      p.teleportTo(target, at.x, at.y, at.z, s.oyaw, 0.0F);
      p.setDeltaMovement(Vec3.ZERO);
      p.fallDistance = 0;
      p.removeEffect(MobEffects.WITHER);
      p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
      data.close(p.getUUID());
      data.grantImmunity(p.getUUID(), now(p.server) + Scp106Rules.RECAPTURE_COOLDOWN);
      LAST.remove(p.getUUID());
      Scp106Entity.dismissHunters(p);
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.ESCAPE, route, 0, 0L), p);
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.STATE, -1, 0, 0L), p);
      p.connection.send(new ClientboundSoundPacket(Holder.direct(Scp106Sounds.ESCAPE.get()), SoundSource.AMBIENT, at.x, at.y, at.z, 1.0F, 1.0F, RANDOM.nextLong()));
      LOG.info("{} left the SCP-106 pocket instance {} (route {})", p.getGameProfile().getName(), s.slot, route);
   }

   /** Emergency recovery: whatever the state, the player goes home (or to the world spawn) and the session ends. */
   public static boolean rescue(ServerPlayer p) {
      PocketSession s = data(p.server).session(p.getUUID());
      if (s != null) {
         escape(p, s, ROUTE_RESCUED);
         return true;
      }
      if (inPocket(p)) {
         ServerLevel level = p.server.overworld();
         BlockPos spawn = level.getSharedSpawnPos();
         Vec3 at = safeReturn(level, Vec3.atBottomCenterOf(spawn));
         p.teleportTo(level, at.x, at.y, at.z, level.getSharedSpawnAngle(), 0.0F);
         p.fallDistance = 0;
         CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.STATE, -1, 0, 0L), p);
         return true;
      }
      return false;
   }

   /** The original spot when a body fits there, else the first free spot above it, else the world spawn. */
   static Vec3 safeReturn(ServerLevel level, Vec3 want) {
      BlockPos base = BlockPos.containing(want);
      for (int dy = 0; dy < 48; dy++) {
         BlockPos feet = base.above(dy);
         if (fits(level, feet) && (dy == 0 || !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty())) {
            return dy == 0 ? want : Vec3.atBottomCenterOf(feet);
         }
      }
      BlockPos spawn = level.getSharedSpawnPos();
      int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
      return new Vec3(spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
   }

   private static boolean fits(ServerLevel level, BlockPos feet) {
      if (!level.getWorldBorder().isWithinBounds(feet) || feet.getY() < level.getMinBuildHeight() || feet.getY() > level.getMaxBuildHeight() - 2) {
         return false;
      }
      AABB body = new AABB(feet.getX() + 0.2, feet.getY() + 0.01, feet.getZ() + 0.2, feet.getX() + 0.8, feet.getY() + 1.8, feet.getZ() + 0.8);
      return level.noCollision(body) && level.getFluidState(feet).isEmpty();
   }

   static void sendState(ServerPlayer p, PocketSession s) {
      int flags = s.sigils & 7 | (s.key ? 8 : 0);
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.STATE, s.slot, flags, PocketWorld.layout(s.slot).seed), p);
   }

   // ------------------------------------------------------------------ lifecycle hooks

   public static void onServerStart(MinecraftServer server) {
      PocketWorld.start(server.getWorldData().worldGenOptions().seed());
      LAST.clear();
      STUCK.clear();
      QUEUE.clear();
      REPAINTS.clear();
      NEXT_SHIFT.clear();
   }

   /** After the overworld data storage exists: the moved maze walls of every instance. */
   public static void onServerStarted(MinecraftServer server) {
      for (Map.Entry<Integer, int[]> e : data(server).allVariants().entrySet()) {
         PocketWorld.setVariants(e.getKey(), e.getValue());
      }
   }

   public static void onServerTick(MinecraftServer server) {
      if (!QUEUE.isEmpty()) {
         List<Runnable> run = new ArrayList<>(QUEUE);
         QUEUE.clear();
         for (Runnable r : run) {
            try {
               r.run();
            } catch (RuntimeException ex) {
               LOG.error("SCP-106 transfer failed", ex);
            }
         }
      }
      repaint();
      if (server.getTickCount() % 20 == 0) {
         shiftMazes(server);
      }
   }

   public static void onLogin(ServerPlayer p) {
      PocketSession s = data(p.server).session(p.getUUID());
      LAST.remove(p.getUUID());
      if (s == null) {
         if (inPocket(p) && !p.isCreative() && !p.isSpectator()) {
            LOG.warn("{} logged in inside the pocket dimension without a session: rescued", p.getGameProfile().getName());
            rescue(p);
         } else {
            CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.STATE, -1, 0, 0L), p);
         }
         return;
      }
      switch (s.phase) {
         case PocketSession.ENTERING -> toPocket(p, s);
         case PocketSession.RETURNING -> escape(p, s, ROUTE_RESCUED);
         default -> {
            if (inPocket(p)) {
               sendState(p, s);
            } else {
               toPocket(p, s);
            }
         }
      }
   }

   public static void onLogout(ServerPlayer p) {
      LAST.remove(p.getUUID());
      STUCK.remove(p.getUUID());
      NEXT_MESSAGE.remove(p.getUUID());
   }

   /** Death inside is not a way out: the player wakes up at the checkpoint, nothing is lost. True when handled. */
   public static boolean onDeath(ServerPlayer p) {
      PocketSession s = data(p.server).session(p.getUUID());
      if (s == null || !inPocket(p)) {
         return false;
      }
      p.setHealth(Math.max(2.0F, p.getMaxHealth() * 0.5F));
      p.clearFire();
      p.removeEffect(MobEffects.WITHER);
      p.removeEffect(MobEffects.POISON);
      p.getFoodData().setFoodLevel(Math.max(p.getFoodData().getFoodLevel(), 14));
      s.deaths++;
      data(p.server).setDirty();
      toPocket(p, s);
      p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0, false, false));
      tell(p, "scp106.pocket.death", "You wake up where you started. The Old Man does not let go that easily.");
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.SCARE, SCARE_DEATH, 0, 0L), p);
      return true;
   }

   // ------------------------------------------------------------------ every tick of a player

   public static void tickPlayer(ServerPlayer p) {
      MinecraftServer server = p.server;
      Scp106SavedData data = data(server);
      PocketSession s = data.session(p.getUUID());
      boolean inside = inPocket(p);
      if (!inside) {
         if (s != null && p.isAlive()) {
            if (s.phase == PocketSession.RETURNING) {
               data.close(p.getUUID());
            } else if (p.isCreative() || p.isSpectator()) {
               data.close(p.getUUID());
            } else if (p.tickCount > 20) {
               // respawned elsewhere, moved by another mod, logged in outside: the place takes them back
               toPocket(p, s);
               tell(p, "scp106.pocket.pulled_back", "The floor gives way. You are back in the Old Man's place.");
            }
         }
         return;
      }
      if (s == null) {
         if (!p.isCreative() && !p.isSpectator() && p.tickCount > 40) {
            LOG.warn("{} is inside the pocket dimension without a session: rescued", p.getGameProfile().getName());
            rescue(p);
         }
         return;
      }
      if (s.phase == PocketSession.RETURNING) {
         escape(p, s, ROUTE_RESCUED);
         return;
      }
      if (s.phase == PocketSession.ENTERING) {
         s.phase = PocketSession.INSIDE;
         data.setDirty();
      }
      PocketLayout l = PocketWorld.layout(s.slot);
      int ox = PocketLayout.slotOriginX(s.slot);
      int oz = PocketLayout.slotOriginZ(s.slot);
      double lx = p.getX() - ox;
      double lz = p.getZ() - oz;
      double y = p.getY();
      Vec3 last = LAST.put(p.getUUID(), p.position());
      if (lx < -3 || lz < -3 || lx > PocketLayout.SIZE + 3 || lz > PocketLayout.SIZE + 3 || y > 250) {
         LOG.warn("{} left the bounds of pocket instance {}: back to the checkpoint", p.getGameProfile().getName(), s.slot);
         toPocket(p, s);
         return;
      }
      if (y < PocketPainter.ABYSS_TRIGGER) {
         abyss(p, s, l);
         return;
      }
      if (p.isInWall()) {
         if (STUCK.merge(p.getUUID(), 1, Integer::sum) > 60) {
            STUCK.remove(p.getUUID());
            toPocket(p, s);
            return;
         }
      } else {
         STUCK.remove(p.getUUID());
      }
      if (last != null && portals(p, s, l, last.x - ox, last.y, last.z - oz, lx, y, lz)) {
         return;
      }
      if (exits(p, s, l, lx, y, lz)) {
         return;
      }
      sigils(p, s, l, lx, y, lz);
      Room room = l.roomAtBlock((int)Math.floor(lx), (int)Math.floor(lz));
      long now = now(server);
      if (room != null && room.kind == Kind.SANCTUARY) {
         if (s.checkpoint != room.id) {
            s.checkpoint = room.id;
            data.setDirty();
            tell(p, "scp106.pocket.sanctuary", "It is quiet here. Warm. If you fall, you will wake up here.");
         }
         s.nextStalk = Math.max(s.nextStalk, now + 600);
         Scp106Entity.dismissHunters(p);
      }
      if (room != null && room.kind == Kind.ESCHER && y > PocketPainter.F + 40 && s.hint(HINT_ESCHER)) {
         tell(p, "scp106.pocket.hint.escher", "You have climbed this flight before. Haven't you?");
      }
      if (p.tickCount % 20 == 0) {
         cues(p, s, l, lx, y, lz);
      }
      if (now >= s.nextStalk && room != null && room.kind != Kind.SANCTUARY && !p.isCreative() && !p.isSpectator()) {
         s.nextStalk = now + Scp106Rules.stalkerDelay(RANDOM.nextDouble());
         data.setDirty();
         Scp106Entity.spawnPocketHunter((ServerLevel)p.level(), p, l, room);
      }
      if (s.hint(HINT_ARRIVAL)) {
         tell(p, "scp106.pocket.arrival", "Somewhere below the world. Read what others left behind.");
      }
   }

   /** Caught by a hunter inside: hurt (never to death), blinded, and dropped somewhere far away in the same instance. */
   public static void drag(ServerPlayer p, Scp106Entity by) {
      PocketSession s = data(p.server).session(p.getUUID());
      if (s == null || !inPocket(p)) {
         return;
      }
      float dmg = Math.min(Scp106Rules.DRAG_DAMAGE, p.getHealth() - 1.0F);
      if (dmg > 0) {
         p.hurt(p.damageSources().mobAttack(by), dmg);
      }
      PocketLayout l = PocketWorld.layout(s.slot);
      List<int[]> landings = l.abyssLandings();
      int ox = PocketLayout.slotOriginX(s.slot);
      int oz = PocketLayout.slotOriginZ(s.slot);
      int[] at = landings.get(RANDOM.nextInt(landings.size()));
      for (int i = 0; i < 8; i++) {
         int[] c = landings.get(RANDOM.nextInt(landings.size()));
         if (p.distanceToSqr(ox + c[0] + 0.5, c[1], oz + c[2] + 0.5) > 80 * 80) {
            at = c;
            break;
         }
      }
      p.connection.teleport(ox + at[0] + 0.5, at[1], oz + at[2] + 0.5, p.getYRot(), p.getXRot());
      p.setDeltaMovement(Vec3.ZERO);
      p.fallDistance = 0;
      LAST.put(p.getUUID(), p.position());
      p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
      s.drags++;
      s.nextStalk = Math.max(s.nextStalk, now(p.server) + Scp106Rules.stalkerDelay(RANDOM.nextDouble()));
      data(p.server).setDirty();
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.SCARE, SCARE_DRAG, 0, 0L), p);
      tell(p, "scp106.pocket.dragged", "It dragged you through the walls. You are somewhere else now.");
   }

   /** Falling into a bottomless place never kills: the place puts the body somewhere else. */
   private static void abyss(ServerPlayer p, PocketSession s, PocketLayout l) {
      List<int[]> landings = l.abyssLandings();
      int[] at = landings.get(RANDOM.nextInt(landings.size()));
      double x = PocketLayout.slotOriginX(s.slot) + at[0] + 0.5;
      double z = PocketLayout.slotOriginZ(s.slot) + at[2] + 0.5;
      p.connection.teleport(x, at[1], z, p.getYRot(), p.getXRot());
      p.setDeltaMovement(Vec3.ZERO);
      p.fallDistance = 0;
      LAST.put(p.getUUID(), p.position());
      p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
      p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.SCARE, SCARE_ABYSS, 0, 0L), p);
      if (s.hint(HINT_ABYSS)) {
         data(p.server).setDirty();
         tell(p, "scp106.pocket.hint.abyss", "The abyss does not kill. It only moves you.");
      }
   }

   /** Controlled anomalies: entering a box moves the body (relative and seamless, or to a fixed spot for decoys). */
   private static boolean portals(ServerPlayer p, PocketSession s, PocketLayout l, double px, double py, double pz, double x, double y, double z) {
      for (Portal pt : l.portals()) {
         if (!pt.contains(x, y + 0.1, z) || pt.contains(px, py + 0.1, pz)) {
            continue;
         }
         if (pt.dir() >= 0) {
            double mx = x - px;
            double mz = z - pz;
            boolean ok = switch (pt.dir()) {
               case PocketLayout.N -> mz < 0;
               case PocketLayout.S -> mz > 0;
               case PocketLayout.E -> mx > 0;
               default -> mx < 0;
            };
            if (!ok) {
               continue;
            }
         }
         if (pt.limit() > 0) {
            int used = s.portalUses.getOrDefault(pt.id(), 0);
            if (used >= pt.limit()) {
               continue;
            }
            s.portalUses.put(pt.id(), used + 1);
            data(p.server).setDirty();
         }
         if (pt.chance() < 1.0F && RANDOM.nextFloat() >= pt.chance()) {
            continue;
         }
         if (pt.type() == PortalType.DECOY) {
            int[] t = pt.target(0, 0, 0);
            p.connection.teleport(PocketLayout.slotOriginX(s.slot) + t[0] + 0.5, t[1], PocketLayout.slotOriginZ(s.slot) + t[2] + 0.5, p.getYRot(), 0.0F);
            p.setDeltaMovement(Vec3.ZERO);
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.SCARE, SCARE_DECOY, 0, 0L), p);
            if (s.hint(HINT_DECOY)) {
               tell(p, "scp106.pocket.hint.decoy", "That was not daylight.");
            }
         } else {
            p.connection.teleport(pt.dx(), pt.dy(), pt.dz(), 0.0F, 0.0F, EnumSet.allOf(RelativeMovement.class));
            if (pt.type() == PortalType.ENDLESS && s.hint(HINT_GALLERY)) {
               tell(p, "scp106.pocket.hint.gallery", "This gallery is longer than it was a moment ago.");
            }
            if (pt.type() == PortalType.SHIFTING || pt.type() == PortalType.WRONG_DOOR) {
               sound(p, Scp106Sounds.SHIFT.get(), p.getX(), p.getY(), p.getZ(), 0.35F, 0.9F + RANDOM.nextFloat() * 0.2F);
            }
         }
         p.fallDistance = 0;
         LAST.put(p.getUUID(), p.position());
         return true;
      }
      return false;
   }

   private static boolean inBox(int[] b, double x, double y, double z) {
      return x >= b[0] && x < b[3] && y >= b[1] && y < b[4] && z >= b[2] && z < b[5];
   }

   /** The three real ways out (design §5.4). */
   private static boolean exits(ServerPlayer p, PocketSession s, PocketLayout l, double x, double y, double z) {
      if (inBox(PocketPainter.breachBox(l.breach()), x, y, z)) {
         if (s.sigilCount() >= 3) {
            tell(p, "scp106.pocket.escape.breach", "The breach takes the three signs and opens.");
            escape(p, s, ROUTE_BREACH);
            return true;
         }
         if (cooldown(p, 60)) {
            tellArgs(p, "scp106.pocket.breach_sealed", "The breach is sealed. Signs: %s/3.", s.sigilCount());
            sound(p, Scp106Sounds.BREACH.get(), p.getX(), p.getY(), p.getZ(), 0.8F, 0.6F);
            Vec3 away = p.position().subtract(PocketLayout.slotOriginX(s.slot) + l.domainCenterX(), p.getY(), PocketLayout.slotOriginZ(s.slot)
               + l.domainCenterZ()).multiply(1, 0, 1).normalize().scale(0.9);
            p.setDeltaMovement(away.x, 0.35, away.z);
            p.hurtMarked = true;
         }
         return false;
      }
      if (inBox(PocketPainter.secretExitBox(l.secret()), x, y, z)) {
         tell(p, "scp106.pocket.escape.secret", "Above the ladder there is a crack of grey light. You squeeze through.");
         escape(p, s, ROUTE_SECRET);
         return true;
      }
      int[] hc = PocketPainter.homeCloset(l.home());
      if (s.key && x >= hc[0] && x < hc[0] + 1 && z >= hc[2] && z < hc[2] + 1 && y >= hc[1] - 0.5 && y < hc[1] + 2) {
         tell(p, "scp106.pocket.escape.home", "Behind the iron door is your own hallway. You do not look back.");
         escape(p, s, ROUTE_HOME);
         return true;
      }
      return false;
   }

   private static void sigils(ServerPlayer p, PocketSession s, PocketLayout l, double x, double y, double z) {
      for (int i = 1; i <= 3; i++) {
         int bit = 1 << (i - 1);
         if ((s.sigils & bit) != 0) {
            continue;
         }
         double[] c = PocketPainter.sigilCenter(l.sigil(i));
         double dx = x - c[0];
         double dz = z - c[2];
         if (dx * dx + dz * dz < 2.4 * 2.4 && Math.abs(y - c[1]) < 3.0) {
            s.sigils |= bit;
            data(p.server).setDirty();
            sendState(p, s);
            sound(p, Scp106Sounds.SIGIL.get(), p.getX(), p.getY(), p.getZ(), 1.0F, 0.8F + 0.1F * i);
            ((ServerLevel)p.level()).sendParticles(p, ParticleTypes.SCULK_SOUL, true, p.getX(), p.getY() + 0.3, p.getZ(), 30, 1.2, 0.2, 1.2, 0.02);
            tellArgs(p, "scp106.pocket.sigil", "A sign burns into your palm: %s. Signs: %s/3.", roman(i), s.sigilCount());
         }
      }
   }

   private static String roman(int i) {
      return i == 1 ? "I" : i == 2 ? "II" : "III";
   }

   /** Once a second: knocking behind listening doors, the draft from the thin wall, the breach's rumble. */
   private static void cues(ServerPlayer p, PocketSession s, PocketLayout l, double x, double y, double z) {
      int ox = PocketLayout.slotOriginX(s.slot);
      int oz = PocketLayout.slotOriginZ(s.slot);
      if (!s.key && RANDOM.nextInt(5) == 0) {
         // only the nearest listening door knocks, every few seconds, so the sound leads somewhere
         int[] best = null;
         double bestD2 = 28 * 28;
         for (Room r : l.rooms()) {
            if (r.purpose != Purpose.LISTENING) {
               continue;
            }
            int[] k = PocketPainter.listeningPoint(r);
            double d2 = (k[0] - x) * (k[0] - x) + (k[2] - z) * (k[2] - z);
            if (d2 < bestD2) {
               bestD2 = d2;
               best = k;
            }
         }
         if (best != null) {
            sound(p, Scp106Sounds.KNOCK.get(), ox + best[0] + 0.5, best[1], oz + best[2] + 0.5, 0.9F, 0.9F + RANDOM.nextFloat() * 0.15F);
         }
      }
      int[] w = PocketPainter.secretWall(l.secret());
      double d2 = (w[0] - x) * (w[0] - x) + (w[2] - z) * (w[2] - z);
      if (d2 < 16 * 16) {
         ((ServerLevel)p.level()).sendParticles(p, ParticleTypes.WHITE_ASH, true, ox + w[0] + 0.5, w[1] + 0.5, oz + w[2] + 0.5, 12, 0.6, 0.8, 0.6, 0.01);
         if (RANDOM.nextInt(5) == 0) {
            sound(p, Scp106Sounds.DRAFT.get(), ox + w[0] + 0.5, w[1], oz + w[2] + 0.5, 0.7F, 1.0F);
         }
      }
      double bx = l.domainCenterX() - x;
      double bz = l.domainCenterZ() - z;
      if (bx * bx + bz * bz < 48 * 48 && RANDOM.nextInt(6) == 0) {
         sound(p, Scp106Sounds.BREACH.get(), ox + l.domainCenterX() + 0.5, PocketPainter.F + 3, oz + l.domainCenterZ() + 0.5, 1.2F, 0.7F);
      }
   }

   // ------------------------------------------------------------------ interaction

   /** Doors: the listening door gives the key once the knocking is answered, the iron home door opens only with it. */
   public static boolean onUseBlock(ServerPlayer p, BlockPos pos) {
      if (!inPocket(p)) {
         return false;
      }
      PocketSession s = data(p.server).session(p.getUUID());
      int slot = PocketLayout.slotAt(pos.getX(), pos.getZ());
      if (s == null || slot != s.slot) {
         return false;
      }
      BlockState state = p.level().getBlockState(pos);
      if (!(state.getBlock() instanceof DoorBlock)) {
         return false;
      }
      BlockPos low = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
      PocketLayout l = PocketWorld.layout(slot);
      int lx = low.getX() - PocketLayout.slotOriginX(slot);
      int lz = low.getZ() - PocketLayout.slotOriginZ(slot);
      int[] hd = PocketPainter.homeDoor(l.home());
      if (lx == hd[0] && lz == hd[2] && low.getY() == hd[1]) {
         if (s.key) {
            BlockState lower = p.level().getBlockState(low);
            if (lower.getBlock() instanceof DoorBlock door && !lower.getValue(DoorBlock.OPEN)) {
               door.setOpen(p, p.level(), lower, low, true);
               sound(p, Scp106Sounds.KEY.get(), low.getX() + 0.5, low.getY() + 1, low.getZ() + 0.5, 1.0F, 1.0F);
               tell(p, "scp106.pocket.home_open", "The key fits. The iron door opens onto a hallway you know.");
            }
         } else {
            sound(p, Scp106Sounds.LOCKED.get(), low.getX() + 0.5, low.getY() + 1, low.getZ() + 0.5, 1.0F, 1.0F);
            tell(p, "scp106.pocket.home_locked", "Locked. Your own front door, locked from this side. There must be a key.");
         }
         return true;
      }
      if (!s.key) {
         for (Room r : l.rooms()) {
            if (r.purpose != Purpose.LISTENING) {
               continue;
            }
            int[] ld = PocketPainter.listeningDoor(r);
            if (lx == ld[0] && lz == ld[2] && low.getY() == ld[1]) {
               s.key = true;
               data(p.server).setDirty();
               sendState(p, s);
               sound(p, Scp106Sounds.KEY.get(), low.getX() + 0.5, low.getY() + 1, low.getZ() + 0.5, 1.0F, 0.8F);
               tell(p, "scp106.pocket.key", "The knocking stops. Nobody is there. On the floor lies a key to a door you know.");
               return false;
            }
         }
      }
      return false;
   }

   /** A note on the floor: the rules of the place, or the diary of somebody who did not make it. */
   public static void readNote(ServerPlayer p, BlockPos pos) {
      int slot = PocketLayout.slotAt(pos.getX(), pos.getZ());
      if (slot < 0 || !inPocket(p)) {
         CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.NOTE, 0, 0, 0L), p);
         return;
      }
      PocketLayout l = PocketWorld.layout(slot);
      Room r = l.roomAtBlock(pos.getX() - PocketLayout.slotOriginX(slot), pos.getZ() - PocketLayout.slotOriginZ(slot));
      if (r == null) {
         return;
      }
      boolean diary = r.diary >= 0;
      int idx = diary ? r.diary : Math.max(0, r.note);
      CoreMessages.sendToPlayer(new Scp106S2CPacket(Scp106S2CPacket.NOTE, idx, diary ? 1 : 0, 0L), p);
      p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.BOOK_PAGE_TURN), SoundSource.BLOCKS, pos.getX() + 0.5, pos.getY(),
         pos.getZ() + 0.5, 1.0F, 0.8F, RANDOM.nextLong()));
   }

   /** Human-readable map of the exits for admins (/scp106 route). */
   public static List<String> route(PocketSession s) {
      PocketLayout l = PocketWorld.layout(s.slot);
      int ox = PocketLayout.slotOriginX(s.slot);
      int oz = PocketLayout.slotOriginZ(s.slot);
      List<String> out = new ArrayList<>();
      int[] cp = PocketPainter.checkpoint(l.arrival());
      out.add("arrival " + (ox + cp[0]) + " " + cp[1] + " " + (oz + cp[2]));
      for (int i = 1; i <= 3; i++) {
         double[] c = PocketPainter.sigilCenter(l.sigil(i));
         out.add("sigil " + i + ((s.sigils & 1 << (i - 1)) != 0 ? " (taken)" : "") + " " + (int)(ox + c[0]) + " " + (int)c[1] + " " + (int)(oz + c[2])
            + " in " + l.sigil(i).kind);
      }
      int[] b = PocketPainter.breachBox(l.breach());
      out.add("breach " + (ox + b[0] + 2) + " " + b[1] + " " + (oz + b[2] + 2));
      int[] w = PocketPainter.secretWall(l.secret());
      out.add("thin wall " + (ox + w[0]) + " " + w[1] + " " + (oz + w[2]));
      int[] hd = PocketPainter.homeDoor(l.home());
      out.add("home door " + (ox + hd[0]) + " " + hd[1] + " " + (oz + hd[2]) + (s.key ? " (key)" : ""));
      for (Room r : l.rooms()) {
         if (r.purpose == Purpose.LISTENING) {
            int[] ld = PocketPainter.listeningDoor(r);
            out.add("listening door " + (ox + ld[0]) + " " + ld[1] + " " + (oz + ld[2]));
         }
      }
      return out;
   }

   // ------------------------------------------------------------------ moving mazes

   private static void shiftMazes(MinecraftServer server) {
      ServerLevel pocket = server.getLevel(PocketWorld.dimension());
      if (pocket == null || pocket.players().isEmpty()) {
         return;
      }
      Scp106SavedData data = data(server);
      long now = now(server);
      Map<Integer, List<ServerPlayer>> bySlot = new HashMap<>();
      for (ServerPlayer p : pocket.players()) {
         int slot = PocketLayout.slotAt(p.getBlockX(), p.getBlockZ());
         if (slot >= 0) {
            bySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(p);
         }
      }
      for (Map.Entry<Integer, List<ServerPlayer>> e : bySlot.entrySet()) {
         int slot = e.getKey();
         long next = NEXT_SHIFT.computeIfAbsent(slot, k -> now + 900 + RANDOM.nextInt(900));
         if (now < next) {
            continue;
         }
         NEXT_SHIFT.put(slot, now + 900 + RANDOM.nextInt(900));
         PocketLayout l = PocketWorld.layout(slot);
         int[] v = data.variants(slot);
         v = v == null || v.length != l.rooms().size() ? new int[l.rooms().size()] : v.clone();
         List<Room> moved = new ArrayList<>();
         for (Room r : l.rooms()) {
            if (r.kind != Kind.MAZE || !loaded(pocket, slot, r) || occupied(e.getValue(), slot, r)) {
               continue;
            }
            v[r.id] ^= 1;
            moved.add(r);
         }
         if (moved.isEmpty()) {
            continue;
         }
         data.setVariants(slot, v);
         PocketWorld.setVariants(slot, v);
         for (Room r : moved) {
            REPAINTS.add(new Repaint(pocket, slot, r, new int[1]));
         }
         for (ServerPlayer p : e.getValue()) {
            sound(p, Scp106Sounds.METAL.get(), p.getX() + RANDOM.nextGaussian() * 20, p.getY(), p.getZ() + RANDOM.nextGaussian() * 20, 1.5F, 0.5F);
         }
      }
   }

   private static boolean loaded(ServerLevel level, int slot, Room r) {
      int x0 = PocketLayout.slotOriginX(slot) + r.minX();
      int z0 = PocketLayout.slotOriginZ(slot) + r.minZ();
      for (int cz = 0; cz < r.h; cz++) {
         for (int cx = 0; cx < r.w; cx++) {
            if (!level.hasChunk((x0 >> 4) + cx, (z0 >> 4) + cz)) {
               return false;
            }
         }
      }
      return true;
   }

   private static boolean occupied(List<ServerPlayer> players, int slot, Room r) {
      double x0 = PocketLayout.slotOriginX(slot) + r.minX() - 3;
      double z0 = PocketLayout.slotOriginZ(slot) + r.minZ() - 3;
      for (ServerPlayer p : players) {
         if (p.getX() >= x0 && p.getX() <= x0 + r.sizeX() + 6 && p.getZ() >= z0 && p.getZ() <= z0 + r.sizeZ() + 6) {
            return true;
         }
      }
      return false;
   }

   private static void repaint() {
      int budget = REPAINT_COLUMNS_PER_TICK;
      byte[] col = new byte[PocketPainter.HEIGHT];
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
      Iterator<Repaint> it = REPAINTS.iterator();
      while (it.hasNext() && budget > 0) {
         Repaint job = it.next();
         Room r = job.room;
         PocketPainter painter = PocketWorld.painter(job.slot);
         int ox = PocketLayout.slotOriginX(job.slot);
         int oz = PocketLayout.slotOriginZ(job.slot);
         int total = r.sizeX() * r.sizeZ();
         while (job.cursor[0] < total && budget > 0) {
            int i = job.cursor[0]++;
            budget--;
            int lx = r.minX() + i % r.sizeX();
            int lz = r.minZ() + i / r.sizeX();
            painter.column(lx, lz, col);
            for (int y = PocketPainter.F + 1; y <= PocketPainter.F + 5; y++) {
               BlockState want = PocketBlocks.state(col[y]);
               pos.set(ox + lx, y, oz + lz);
               if (job.level.getBlockState(pos) != want) {
                  job.level.setBlock(pos, want, 2 | 16);
               }
            }
         }
         if (job.cursor[0] >= total) {
            it.remove();
         }
      }
   }

   // ------------------------------------------------------------------ small helpers

   static void sound(ServerPlayer p, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
      p.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.AMBIENT, x, y, z, volume, pitch, RANDOM.nextLong()));
   }

   static void tell(ServerPlayer p, String key, String fallback) {
      p.displayClientMessage(Component.translatableWithFallback(key, fallback), true);
   }

   static void tellArgs(ServerPlayer p, String key, String fallback, Object... args) {
      p.displayClientMessage(Component.translatableWithFallback(key, fallback, args), true);
   }

   private static boolean cooldown(ServerPlayer p, int ticks) {
      long now = now(p.server);
      Long next = NEXT_MESSAGE.get(p.getUUID());
      if (next != null && now < next) {
         return false;
      }
      NEXT_MESSAGE.put(p.getUUID(), now + ticks);
      return true;
   }

   /** Is this level the pocket dimension (helper for event filters). */
   public static boolean pocket(Level level) {
      return PocketWorld.is(level);
   }
}
