package dev.baranhan.viltrumitecore.hero;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared "this body cannot act" gate (spec docs/design/2026-10-28-sinclair-design.md §7, §11).
 * Heroes register providers (Sinclair: a player turned into a rabbit); shared input paths ask
 * {@link #locked} before they start any hero or kit ability. Releases are never blocked, so a
 * channel that started before the lock can still stop. Both sides.
 */
public final class ActionLocks {
   private static final List<Predicate<LivingEntity>> PROVIDERS = new CopyOnWriteArrayList<>();
   /** Per-action seals (Heaven's Door "cannot use X", a hero's sealed time stop): only the named action is refused. */
   private static final List<BiPredicate<LivingEntity, HeroAction>> ACTION_PROVIDERS = new CopyOnWriteArrayList<>();

   private ActionLocks() {
   }

   public static void register(Predicate<LivingEntity> provider) {
      if (provider != null && !PROVIDERS.contains(provider)) {
         PROVIDERS.add(provider);
      }
   }

   /** Registers a per-action seal provider (both sides; the server decides). */
   public static void registerAction(BiPredicate<LivingEntity, HeroAction> provider) {
      if (provider != null && !ACTION_PROVIDERS.contains(provider)) {
         ACTION_PROVIDERS.add(provider);
      }
   }

   /** True when the whole body is locked or a seal forbids this one action now. */
   public static boolean locked(LivingEntity entity, HeroAction action) {
      if (entity == null) {
         return false;
      }
      if (locked(entity)) {
         return true;
      }
      if (action == null) {
         return false;
      }
      for (BiPredicate<LivingEntity, HeroAction> provider : ACTION_PROVIDERS) {
         if (provider.test(entity, action)) {
            return true;
         }
      }
      return false;
   }

   /** True when any provider forbids the entity to start an action now. */
   public static boolean locked(LivingEntity entity) {
      if (entity == null) {
         return false;
      }

      return anyLocks(PROVIDERS, entity);
   }

   /** Pure: does any provider lock the value. */
   static <T> boolean anyLocks(List<? extends Predicate<? super T>> providers, T value) {
      for (Predicate<? super T> provider : providers) {
         if (provider.test(value)) {
            return true;
         }
      }

      return false;
   }

   static int providerCount() {
      return PROVIDERS.size();
   }

   /** Test hook: forget every provider. */
   static void clearForTests() {
      PROVIDERS.clear();
      ACTION_PROVIDERS.clear();
   }
}
