package dev.baranhan.viltrumitecore.hero;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side store of owner sections, pure (no Minecraft client): each
 * section keeps ids with an absolute expiry in game ticks (0 = none).
 * Reads are per entity per frame (outline glow, silhouettes), so the live id
 * array is cached until the next expiry and never allocated per call.
 */
public final class OwnerSections {
   private static final int[] NONE = new int[0];

   private static final class Store {
      final int[] ids;
      final long[] expiresAt;
      int[] live;
      long liveFrom = Long.MIN_VALUE;
      long liveUntil = Long.MIN_VALUE;

      Store(int[] ids, long[] expiresAt) {
         this.ids = ids;
         this.expiresAt = expiresAt;
      }

      int[] live(long now) {
         if (this.live != null && now >= this.liveFrom && now < this.liveUntil) {
            return this.live;
         }

         int count = 0;
         long next = Long.MAX_VALUE;
         for (long expiry : this.expiresAt) {
            if (expiry == 0L || expiry > now) {
               count++;
               if (expiry != 0L) {
                  next = Math.min(next, expiry);
               }
            }
         }

         int[] out = count == this.ids.length ? this.ids : new int[count];
         if (out != this.ids) {
            int at = 0;
            for (int i = 0; i < this.ids.length; i++) {
               if (this.expiresAt[i] == 0L || this.expiresAt[i] > now) {
                  out[at++] = this.ids[i];
               }
            }
         }

         this.live = out;
         this.liveFrom = now;
         this.liveUntil = next;
         return out;
      }
   }

   private final Map<OwnerSection, Store> sections = new EnumMap<>(OwnerSection.class);

   /** Replaces one section; relative expiry ticks become absolute from {@code now}. */
   public void set(OwnerSection section, HeroOwnerSnapshot.Section value, long now) {
      int n = value.ids().length;
      if (n == 0) {
         this.sections.remove(section);
         return;
      }

      int[] ids = value.ids().clone();
      long[] expires = new long[n];
      for (int i = 0; i < n; i++) {
         int expire = value.expireTicks()[i];
         expires[i] = expire <= 0 ? 0L : now + expire;
      }

      this.sections.put(section, new Store(ids, expires));
   }

   /** Live ids of a section at {@code now}. The array is shared: callers must not change it. */
   public int[] ids(OwnerSection section, long now) {
      Store store = this.sections.get(section);
      return store == null ? NONE : store.live(now);
   }

   /** Is {@code id} live in the section at {@code now}; no allocation. */
   public boolean contains(OwnerSection section, int id, long now) {
      for (int live : this.ids(section, now)) {
         if (live == id) {
            return true;
         }
      }

      return false;
   }

   public void clear() {
      this.sections.clear();
   }
}
