package dev.baranhan.viltrumitecore.hero.thor.hammer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The one saved Mjölnir of the whole save (all dimensions), stored with the
 * overworld data ({@code data/viltrumitecore_mjolnir.dat}). Only session A
 * (MjolnirWorld) reads and writes it.
 */
public final class MjolnirSavedData extends SavedData {
   public static final String NAME = "viltrumitecore_mjolnir";
   static final String KEY = "Mjolnir";

   final MjolnirRecord record;

   MjolnirSavedData() {
      this(new MjolnirRecord());
   }

   MjolnirSavedData(MjolnirRecord record) {
      this.record = record;
   }

   static MjolnirSavedData load(CompoundTag tag) {
      return new MjolnirSavedData(MjolnirRecord.load(tag.getCompound(KEY)));
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.put(KEY, this.record.save());
      return tag;
   }

   static MjolnirSavedData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(MjolnirSavedData::load, MjolnirSavedData::new, NAME);
   }
}
