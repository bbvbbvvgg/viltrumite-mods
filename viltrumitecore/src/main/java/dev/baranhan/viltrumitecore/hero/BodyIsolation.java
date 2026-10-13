package dev.baranhan.viltrumitecore.hero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

/** Hero-neutral persistent isolation marker; prevents input and every damage path. */
public final class BodyIsolation {
   public static final String TAG = "viltrumitecore:limbo";
   public static final String DIMENSION_ID = "viltrumitecore:the_void";
   private BodyIsolation() {}
   public static boolean active(CompoundTag data) {return data.contains(TAG, Tag.TAG_COMPOUND);}
   public static boolean active(Entity entity) {return entity != null && active(entity.getPersistentData());}
}
