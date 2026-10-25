package dev.baranhan.viltrumitecore.hero.control;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Durable originals for a temporary pause-owned freeze, not a persisted control. */
public final class PauseFlagRecovery {
   public static final String TAG = "viltrumitecore:world_pause_flags";

   private PauseFlagRecovery() {
   }

   public record Flags(boolean noGravity, boolean noAi) {
   }

   public static void remember(CompoundTag data, UUID effect, Flags flags) {
      CompoundTag existing = data.getCompound(TAG);
      if (valid(existing) && effect.equals(existing.getUUID("effect"))) {
         return; // Refresh must never capture the already-frozen body.
      }

      CompoundTag saved = new CompoundTag();
      saved.putUUID("effect", effect);
      saved.putBoolean("noGravity", flags.noGravity());
      saved.putBoolean("noAi", flags.noAi());
      data.put(TAG, saved);
   }

   public static void forget(CompoundTag data, UUID effect) {
      CompoundTag saved = data.getCompound(TAG);
      if (saved.hasUUID("effect") && effect.equals(saved.getUUID("effect"))) {
         data.remove(TAG);
      }
   }

   /**
    * Consume an orphan after a new process/unload. A live matching lease keeps
    * its originals; another control or an isolated body owns the current flags.
    * Invalid NBT is discarded rather than guessed.
    */
   @Nullable
   public static Flags recover(CompoundTag data, @Nullable UUID currentEffect, boolean preserveBodyFlags) {
      if (!data.contains(TAG)) {
         return null;
      }

      CompoundTag saved = data.getCompound(TAG);
      if (!data.contains(TAG, Tag.TAG_COMPOUND) || !valid(saved)) {
         data.remove(TAG);
         return null;
      }

      if (!preserveBodyFlags && saved.getUUID("effect").equals(currentEffect)) {
         return null;
      }

      data.remove(TAG);
      return currentEffect != null || preserveBodyFlags
         ? null
         : new Flags(saved.getBoolean("noGravity"), saved.getBoolean("noAi"));
   }

   private static boolean valid(CompoundTag saved) {
      return saved.hasUUID("effect")
         && saved.contains("noGravity", Tag.TAG_BYTE)
         && saved.contains("noAi", Tag.TAG_BYTE);
   }
}