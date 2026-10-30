package dev.baranhan.viltrumitecore.hero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Cooldowns that survive relog, hero change and death, and count only online
 * time (Reinhard spec §4.2). Stored in the persisted Forge player data
 * ({@code Player.PERSISTED_NBT_TAG} → {@code ViltrumiteCooldowns}): Forge
 * copies that sub-compound to the respawned body, and it is saved with the
 * player. Ticked once per server tick for every online player
 * ({@link HeroEvents}). Keys are namespaced by the owner ({@code "reinhard:phoenix"}).
 */
public final class PersistentCooldowns {
   public static final String KEY = "ViltrumiteCooldowns";

   private PersistentCooldowns() {
   }

   /** Ticks left (0 = ready). Reads only; safe on both sides for the server player. */
   public static int get(Player player, String key) {
      CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
      return get(persisted.getCompound(KEY), key);
   }

   public static void set(ServerPlayer player, String key, int ticks) {
      CompoundTag data = player.getPersistentData();
      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      CompoundTag store = persisted.getCompound(KEY);
      set(store, key, ticks);
      persisted.put(KEY, store);
      data.put(Player.PERSISTED_NBT_TAG, persisted);
   }

   /** Debug reset (/hero cooldown reset): every persisted cooldown ends now. */
   public static void clearAll(ServerPlayer player) {
      CompoundTag data = player.getPersistentData();
      if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
         return;
      }

      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      persisted.remove(KEY);
      data.put(Player.PERSISTED_NBT_TAG, persisted);
   }

   /** One online tick of every key. */
   public static void tick(ServerPlayer player) {
      CompoundTag data = player.getPersistentData();
      if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
         return;
      }

      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      if (persisted.contains(KEY, Tag.TAG_COMPOUND)) {
         tickTag(persisted.getCompound(KEY));
      }
   }

   // ---- pure core ----

   public static int get(CompoundTag store, String key) {
      return Math.max(0, store.getInt(key));
   }

   public static void set(CompoundTag store, String key, int ticks) {
      if (ticks > 0) {
         store.putInt(key, ticks);
      } else {
         store.remove(key);
      }
   }

   /** Decrements every key by one; a key that reaches 0 is removed. Returns the same tag. */
   public static CompoundTag tickTag(CompoundTag store) {
      for (String key : store.getAllKeys().toArray(new String[0])) {
         int left = store.getInt(key) - 1;
         set(store, key, left);
      }

      return store;
   }
}
