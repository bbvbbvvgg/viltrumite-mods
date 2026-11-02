package dev.baranhan.viltrumitecore.hero.book;

import net.minecraft.world.entity.LivingEntity;

/**
 * A non-player body that knows how its own book behaves (bosses, SCP-096, giants, summons). {@code base} is what
 * {@link BookProfiles} would give it by classification; return it changed or as is.
 */
public interface BookSubject {
   BookProfile bookProfile(BookProfile base);

   /** Extra page lines of this body (already translated components are not allowed: lang keys plus arguments). */
   default void bookPages(LivingEntity self, BookPageSink sink) {
   }
}
