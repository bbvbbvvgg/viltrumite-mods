package dev.baranhan.viltrumitecore.city.citizen;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * The one hook a world event uses to hold a person (a Chitauri captor carries them, a containment field keeps them):
 * a tag in the entity's persistent data. While it is set the citizen brain, the stuck check and the population
 * manager leave the person alone (no own walking, no despawn, no density trim). The tag saves with any saved entity
 * (villagers); the event that set it owns clearing it and records the state of non-saved citizens itself.
 */
public final class CitizenCaptivity {
   public static final String TAG = "viltrumitecore:captive";

   private CitizenCaptivity() {
   }

   public static boolean isHeld(@Nullable Entity entity) {
      return entity != null && entity.getPersistentData().contains(TAG);
   }

   /** Holder of the person: an event-owned id (for example the Chitauri containment beacon UUID), or "" while carried. */
   @Nullable
   public static String holder(Entity entity) {
      CompoundTag data = entity.getPersistentData();
      return data.contains(TAG) ? data.getString(TAG) : null;
   }

   public static void hold(Entity entity, String holder) {
      entity.getPersistentData().putString(TAG, holder);
   }

   public static void release(Entity entity) {
      entity.getPersistentData().remove(TAG);
   }
}
