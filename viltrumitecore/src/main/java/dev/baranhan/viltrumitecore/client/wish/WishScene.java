package dev.baranhan.viltrumitecore.client.wish;

import dev.baranhan.viltrumitecore.hero.wish.Wish;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** One running ritual as a client sees it (the wisher's own or a nearby player's). */
final class WishScene {
   final int entityId;
   @Nullable
   final Wish wish;
   final WishStyle style;
   final long seed;
   final boolean own;
   /** Ticks since the ritual start (synced by START.elapsed and SWAP). */
   int age;
   int ageO;
   boolean swapped;
   /** Feet position and facing at the start (the wisher is rooted); null until the entity is known. */
   @Nullable
   Vec3 origin;
   float yaw;

   WishScene(int entityId, @Nullable Wish wish, long seed, boolean own, int elapsed) {
      this.entityId = entityId;
      this.wish = wish;
      this.style = WishStyle.of(wish == null ? null : wish.style());
      this.seed = seed;
      this.own = own;
      this.age = this.ageO = elapsed;
   }

   float time(float partial) {
      return this.ageO + (this.age - this.ageO) * partial;
   }
}
