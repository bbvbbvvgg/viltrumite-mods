package dev.baranhan.viltrumitecore.hero.reinhard.limbo;

import dev.baranhan.viltrumitecore.hero.BodyIsolation;
import dev.baranhan.viltrumitecore.hero.HeroEvents;
import dev.baranhan.viltrumitecore.hero.ServerEndTasks;
import dev.baranhan.viltrumitecore.hero.reinhard.ReinhardRules;
import dev.baranhan.viltrumitecore.mixin.PlayerRespawnLogicInvoker;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.LimboS2CPacket;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Persistent, same-body limbo. Time advances only in the isolated online player's tick. */
public final class Limbo {
   public static final String TAG = BodyIsolation.TAG;
   public static final String DIMENSION_ID = BodyIsolation.DIMENSION_ID;
   private static final ThreadLocal<Integer> INTERNAL_TRAVEL = ThreadLocal.withInitial(() -> 0);
   private static final Set<UUID> PENDING = new HashSet<>();
   private Limbo() {}
   // Lazy: plain timer tests do not initialize the Minecraft level registries.
   public static ResourceKey<Level> dimension() {return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DIMENSION_ID));}
   public static boolean available(MinecraftServer server) {return server != null && server.getLevel(dimension()) != null;}
   public static boolean blocksTravel(boolean isolated, boolean internal) {return isolated && !internal;}
   public static boolean internalTravel() {return INTERNAL_TRAVEL.get() > 0;}
   public static void clear() {PENDING.clear(); INTERNAL_TRAVEL.remove();}

   public interface Body {
      CompoundTag data(); boolean online(); boolean inVoid();
      void closeContainer(); void discardCarried(); void clearInventory(); void clearExperience();
      void teleportToVoid(); void holdVoid(); void startPacket(int remaining);
      void requestReturn(); void teleportToSharedSpawn(); void restoreVitals();
      void endPacket(); void freshBody();
   }
   public static void prepareTag(CompoundTag data) {
      CompoundTag tag = new CompoundTag(); tag.putInt("ticksLeft", ReinhardRules.LIMBO_TICKS); tag.putBoolean("pendingStrip", true);
      data.put(TAG, tag);
   }
   public static int remaining(CompoundTag data) {return Math.max(0, data.getCompound(TAG).getInt("ticksLeft"));}
   public static void send(Body body) {prepareTag(body.data()); resume(body);}
   public static void resume(Body body) {
      if(!body.online() || !BodyIsolation.active(body.data())) return;
      if(remaining(body.data()) <= 0) {body.requestReturn(); return;}
      CompoundTag tag = body.data().getCompound(TAG);
      if(tag.getBoolean("pendingStrip")) {
         // Free capacity before vanilla returns personal crafting inputs; consume
         // both the cursor and those returned stacks, never external storage.
         body.clearInventory(); body.discardCarried(); body.closeContainer();
         body.clearInventory(); body.clearExperience(); tag.putBoolean("pendingStrip", false);
      } else body.closeContainer();
      body.teleportToVoid(); body.startPacket(remaining(body.data()));
   }
   public static void tick(Body body) {
      if(!body.online() || !body.inVoid() || !BodyIsolation.active(body.data())) return;
      body.holdVoid();
      int left = Math.max(0, remaining(body.data()) - 1);
      body.data().getCompound(TAG).putInt("ticksLeft", left);
      if(left == 0) body.requestReturn();
   }
   public static void returnToSpawn(Body body) {
      if(!body.online() || !BodyIsolation.active(body.data())) return;
      body.teleportToSharedSpawn(); body.data().remove(TAG); body.restoreVitals(); body.endPacket(); body.freshBody();
   }

   /** Isolate synchronously; actual inventory/dimension operations wait for server END. */
   public static boolean send(ServerPlayer player) {
      MinecraftServer server = player.getServer();
      if(!available(server) || BodyIsolation.active(player)) return false;
      prepareTag(player.getPersistentData()); queueResume(player); return true;
   }
   public static void onLogin(ServerPlayer player) {if(BodyIsolation.active(player)) queueResume(player);}
   public static void onLogout(ServerPlayer player) {PENDING.remove(player.getUUID());}
   public static void tick(ServerPlayer player) {
      if(!BodyIsolation.active(player)) return;
      if(!player.level().dimension().equals(dimension())) {queueResume(player); return;}
      tick(new PlayerBody(player));
   }
   private static void queueResume(ServerPlayer player) {
      MinecraftServer server = player.getServer(); if(server == null || !PENDING.add(player.getUUID())) return;
      ServerEndTasks.enqueue(server, () -> {
         PENDING.remove(player.getUUID());
         PlayerBody body = new PlayerBody(player);
         if(!body.online() || !BodyIsolation.active(player)) return;
         // A removed/broken datapack must not trap an already-isolated player forever.
         if(!available(server)) {returnToSpawn(body); return;}
         resume(body);
      });
   }
   private static void queueReturn(ServerPlayer player) {
      MinecraftServer server = player.getServer(); if(server == null || !PENDING.add(player.getUUID())) return;
      ServerEndTasks.enqueue(server, () -> {PENDING.remove(player.getUUID()); returnToSpawn(new PlayerBody(player));});
   }
   private static void travel(Runnable teleport) {
      INTERNAL_TRAVEL.set(INTERNAL_TRAVEL.get() + 1);
      try {teleport.run();} finally {int depth = INTERNAL_TRAVEL.get() - 1; if(depth == 0) INTERNAL_TRAVEL.remove(); else INTERNAL_TRAVEL.set(depth);}
   }

   /** Vanilla radius/border semantics, randomized coprime permutation, each candidate at most once. */
   public static BlockPos safeSpawn(int x, int z, int radius, long seed, BiFunction<Integer,Integer,BlockPos> safe) {
      int r = Math.max(0, radius); long width = 2L * r + 1;
      int count = (int)Math.min(Integer.MAX_VALUE, width > 46340 ? Integer.MAX_VALUE : width * width);
      Random random = new Random(seed); long first = random.nextInt(count), step = count <= 16 ? Math.max(1, count - 1) : 17;
      while(gcd(step, count) != 1) step++;
      for(long i = 0; i < count; i++) {
         long index = (first + step * i) % count;
         BlockPos pos = safe.apply((int)(x + index % width - r), (int)(z + index / width - r));
         if(pos != null) return pos;
      }
      return null;
   }
   private static long gcd(long a, long b) {while(b != 0) {long c = a % b; a = b; b = c;} return a;}
   private static BlockPos sharedSpawn(ServerPlayer player, ServerLevel level) {
      BlockPos spawn = level.getSharedSpawnPos();
      int radius = Math.max(0, level.getServer().getSpawnRadius(level));
      int border = Mth.floor(level.getWorldBorder().getDistanceToBorder(spawn.getX(), spawn.getZ()));
      radius = Math.min(radius, Math.max(0, border)); if(border <= 1) radius = 1;
      BlockPos found = safeSpawn(spawn.getX(), spawn.getZ(), radius, player.getRandom().nextLong(), (x, z) -> {
         BlockPos pos = PlayerRespawnLogicInvoker.viltrumitecore$safeSpawn(level, x, z);
         return pos != null && collisionFree(player, level, pos) ? pos : null;
      });
      if(found != null) return found;
      // Vanilla fallback: ascend at the shared spawn, without building a platform.
      BlockPos pos = new BlockPos(spawn.getX(), Mth.clamp(spawn.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight()-2), spawn.getZ());
      while(pos.getY() < level.getMaxBuildHeight()-2 && !collisionFree(player, level, pos)) pos = pos.above();
      return pos;
   }
   private static boolean collisionFree(ServerPlayer player, ServerLevel level, BlockPos pos) {
      double half = player.getBbWidth() / 2.0;
      AABB box = new AABB(pos.getX()+.5-half, pos.getY(), pos.getZ()+.5-half, pos.getX()+.5+half, pos.getY()+player.getBbHeight(), pos.getZ()+.5+half);
      return level.getWorldBorder().isWithinBounds(pos) && level.noCollision(player, box);
   }
   private record PlayerBody(ServerPlayer player) implements Body {
      public CompoundTag data() {return player.getPersistentData();}
      public boolean online() {return player.getServer() != null && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;}
      public boolean inVoid() {return player.level().dimension().equals(dimension());}
      public void closeContainer() {player.closeContainer();}
      public void discardCarried() {player.containerMenu.setCarried(ItemStack.EMPTY); player.inventoryMenu.setCarried(ItemStack.EMPTY);}
      public void clearInventory() {player.getInventory().clearContent(); player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();}
      public void clearExperience() {player.setExperienceLevels(0); player.setExperiencePoints(0); player.totalExperience = 0;}
      public void teleportToVoid() {
         ServerLevel level = Objects.requireNonNull(player.getServer().getLevel(dimension()));
         Vec3 pos = ReinhardRules.LIMBO_POS;
         travel(() -> player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), 0));
         player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
      }
      public void holdVoid() {
         player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
         player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
         if(player.position().distanceToSqr(ReinhardRules.LIMBO_POS) > .01) {
            Vec3 pos = ReinhardRules.LIMBO_POS; player.connection.teleport(pos.x, pos.y, pos.z, player.getYRot(), 0);
         }
      }
      public void startPacket(int left) {CoreMessages.sendToPlayer(new LimboS2CPacket(true, left), player);}
      public void requestReturn() {queueReturn(player);}
      public void teleportToSharedSpawn() {
         ServerLevel level = player.getServer().overworld(); BlockPos pos = sharedSpawn(player, level);
         player.setNoGravity(false);
         travel(() -> player.teleportTo(level, pos.getX()+.5, pos.getY(), pos.getZ()+.5, level.getSharedSpawnAngle(), 0));
      }
      public void restoreVitals() {
         player.getAbilities().flying = false;
         dev.baranhan.viltrumiteflight.util.FlightPermissions.resetModFlight(player);
         player.onUpdateAbilities();
         player.setNoGravity(false); player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0; player.clearFire();
         player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5);
      }
      public void endPacket() {CoreMessages.sendToPlayer(new LimboS2CPacket(false, 0), player);}
      public void freshBody() {HeroEvents.freshBody(player);}
   }
}
