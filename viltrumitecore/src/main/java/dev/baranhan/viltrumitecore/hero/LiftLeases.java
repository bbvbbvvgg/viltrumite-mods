package dev.baranhan.viltrumitecore.hero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

/**
 * Shared ref-counted {@code noGravity} leases for lifts (Reinhard's Reid draw,
 * any later hero power). Entity UUID → lease {original flag, base Y, owner set}:
 * the original is captured when the first owner arrives, the entity floats
 * while any owner holds it, and the original comes back only when the last
 * owner leaves — two overlapping lifts never leave {@code noGravity = true}
 * behind. Each lease is mirrored into the entity's persistent tag
 * {@value #TAG}, so an unload or a restart restores it on the next join
 * ({@link #onJoin}). Server thread only; {@link #SERVER} is the live registry.
 */
public final class LiftLeases {
   public static final String TAG = "ViltrumiteLifted";
   public static final LiftLeases SERVER = new LiftLeases();

   /** The entity side of a lease (an adapter over {@link Entity}; fakes in tests). */
   public interface Body {
      UUID id();

      boolean noGravity();

      void setNoGravity(boolean value);

      double y();

      CompoundTag persistentData();
   }

   /** What the persistent tag holds. */
   public record Mirror(boolean original, Set<UUID> owners, double baseY) {
   }

   private static final class Lease {
      final boolean original;
      final double baseY;
      final Set<UUID> owners = new LinkedHashSet<>();

      Lease(boolean original, double baseY) {
         this.original = original;
         this.baseY = baseY;
      }
   }

   private final Map<UUID, Lease> leases = new HashMap<>();

   /** Adds {@code owner}; true when it did not hold the lease yet. The entity floats from now on. */
   public boolean acquire(Body body, UUID owner) {
      Lease lease = this.leases.get(body.id());
      if (lease == null) {
         Mirror stale = readTag(body.persistentData());
         // A tag without a live lease is left from before a restart: its original is the true one.
         lease = new Lease(stale != null ? stale.original() : body.noGravity(), stale != null ? stale.baseY() : body.y());
         this.leases.put(body.id(), lease);
      }

      if (!lease.owners.add(owner)) {
         return false;
      }

      body.setNoGravity(true);
      writeTag(body.persistentData(), lease);
      return true;
   }

   /** Removes {@code owner}; true when this ended the lease (the original flag is back). */
   public boolean release(Body body, UUID owner) {
      Lease lease = this.leases.get(body.id());
      if (lease == null || !lease.owners.remove(owner)) {
         return false;
      }

      if (lease.owners.isEmpty()) {
         this.leases.remove(body.id());
         body.setNoGravity(lease.original);
         body.persistentData().remove(TAG);
         return true;
      }

      writeTag(body.persistentData(), lease);
      return false;
   }

   /**
    * Ends every lease of {@code owner} (an event ends or aborts). Returns the
    * loaded bodies whose lease ended. A lease of an unloaded body (resolver
    * null) is dropped here; its tag restores it on the next {@link #onJoin}.
    */
   public List<Body> releaseAll(UUID owner, Function<UUID, ? extends Body> resolver) {
      List<Body> ended = new ArrayList<>();
      Iterator<Map.Entry<UUID, Lease>> it = this.leases.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Lease> entry = it.next();
         Lease lease = entry.getValue();
         if (!lease.owners.remove(owner)) {
            continue;
         }

         Body body = resolver.apply(entry.getKey());
         if (lease.owners.isEmpty()) {
            it.remove();
            if (body != null) {
               body.setNoGravity(lease.original);
               body.persistentData().remove(TAG);
               ended.add(body);
            }
         } else if (body != null) {
            writeTag(body.persistentData(), lease);
         }
      }

      return ended;
   }

   /** Entity join: a live lease lifts it again; a tag with no live lease (restart, ended while unloaded) restores the original. */
   public void onJoin(Body body) {
      Lease lease = this.leases.get(body.id());
      if (lease != null) {
         body.setNoGravity(true);
         writeTag(body.persistentData(), lease);
         return;
      }

      Mirror mirror = readTag(body.persistentData());
      if (mirror != null) {
         body.setNoGravity(mirror.original());
         body.persistentData().remove(TAG);
      }
   }

   public boolean holds(UUID id) {
      return this.leases.containsKey(id);
   }

   public Set<UUID> owners(UUID id) {
      Lease lease = this.leases.get(id);
      return lease == null ? Set.of() : Set.copyOf(lease.owners);
   }

   /** Y of the entity when its first owner arrived; NaN without a lease. */
   public double baseY(UUID id) {
      Lease lease = this.leases.get(id);
      return lease == null ? Double.NaN : lease.baseY;
   }

   /** The flag the entity had before any lift: a control that saves gravity must save this one. */
   public boolean naturalNoGravity(UUID id, boolean current) {
      Lease lease = this.leases.get(id);
      return lease == null ? current : lease.original;
   }

   /** Server stop: leases do not outlive the server (the tags restore on the next start). */
   public void clear() {
      this.leases.clear();
   }

   @Nullable
   public static Mirror readTag(CompoundTag data) {
      if (!data.contains(TAG, Tag.TAG_COMPOUND)) {
         return null;
      }

      CompoundTag tag = data.getCompound(TAG);
      Set<UUID> owners = new LinkedHashSet<>();
      for (Tag owner : tag.getList("owners", Tag.TAG_INT_ARRAY)) {
         owners.add(NbtUtils.loadUUID(owner));
      }

      return new Mirror(tag.getBoolean("orig"), owners, tag.getDouble("baseY"));
   }

   private static void writeTag(CompoundTag data, Lease lease) {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("orig", lease.original);
      tag.putDouble("baseY", lease.baseY);
      ListTag owners = new ListTag();
      for (UUID owner : lease.owners) {
         owners.add(NbtUtils.createUUID(owner));
      }

      tag.put("owners", owners);
      data.put(TAG, tag);
   }

   /** The adapter over a real entity. */
   public static Body body(Entity entity) {
      return new Body() {
         @Override
         public UUID id() {
            return entity.getUUID();
         }

         @Override
         public boolean noGravity() {
            return entity.isNoGravity();
         }

         @Override
         public void setNoGravity(boolean value) {
            entity.setNoGravity(value);
         }

         @Override
         public double y() {
            return entity.getY();
         }

         @Override
         public CompoundTag persistentData() {
            return entity.getPersistentData();
         }
      };
   }
}
