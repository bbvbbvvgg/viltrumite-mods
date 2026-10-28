package dev.baranhan.viltrumitecore.hero.sinclair.entity;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Owner checks shared by every Sinclair entity (spec §8: they vanish without a living Sinclair in their level). */
public final class SinclairOwned {
   private SinclairOwned() {
   }

   /** Something a Sinclair owns (assistant, decoy, props): never a target of his own tricks. */
   public interface Owned {
      @Nullable
      UUID ownerId();
   }

   /** The owner when he is alive, in this level and still Sinclair; else null. */
   @Nullable
   public static ServerPlayer liveOwner(Entity self, @Nullable UUID owner) {
      if (owner == null || !(self.level() instanceof ServerLevel level)) {
         return null;
      }

      Entity entity = level.getEntity(owner);
      if (entity instanceof ServerPlayer player && player.isAlive() && !player.isRemoved() && player instanceof HeroPlayer hero
         && hero.getHeroId() == HeroId.SINCLAIR) {
         return player;
      }

      return null;
   }

   /** True for the owner himself and every entity he owns. */
   public static boolean ownedBy(Entity entity, @Nullable UUID owner) {
      if (owner == null || entity == null) {
         return false;
      }

      if (owner.equals(entity.getUUID())) {
         return true;
      }

      return entity instanceof Owned owned && owner.equals(owned.ownerId());
   }
}
