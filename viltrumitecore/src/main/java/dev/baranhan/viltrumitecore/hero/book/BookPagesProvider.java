package dev.baranhan.viltrumitecore.hero.book;

import net.minecraft.world.entity.LivingEntity;

/**
 * Adapter that adds pages for bodies of a system the reader does not know (city citizens, SCP-096, another mod's
 * entity through that mod's public API). Register with {@link BookReader#registerProvider}; providers must only use
 * public data.
 */
public interface BookPagesProvider {
   boolean handles(LivingEntity target);

   void addPages(LivingEntity target, BookPageSink sink);
}
