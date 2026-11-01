package dev.baranhan.viltrumitecore.worldevent.ultron;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The running uprising of the whole save ({@code data/viltrumitecore_ultron.dat} with the overworld
 * data): {@link UltronSession#save()} while one runs, nothing after its end. Restored on server start.
 */
public final class UltronSavedData extends SavedData {
   public static final String NAME = "viltrumitecore_ultron";
   static final String KEY = "Session";
   @Nullable
   private CompoundTag tag;

   UltronSavedData() {
   }

   static UltronSavedData load(CompoundTag root) {
      UltronSavedData data = new UltronSavedData();
      if (root.contains(KEY)) {
         data.tag = root.getCompound(KEY);
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag root) {
      if (this.tag != null) {
         root.put(KEY, this.tag);
      }
      return root;
   }

   @Nullable
   CompoundTag tag() {
      return this.tag;
   }

   void put(@Nullable CompoundTag next) {
      this.tag = next;
      this.setDirty();
   }

   static UltronSavedData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(UltronSavedData::load, UltronSavedData::new, NAME);
   }
}
