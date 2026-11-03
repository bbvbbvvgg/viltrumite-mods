package dev.baranhan.viltrumitecore.t1000;

import dev.baranhan.viltrumitecore.city.citizen.CitizenEntity;
import dev.baranhan.viltrumitecore.city.citizen.CitizenLook;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Who the T-1000 can look like. A disguise is a kind plus an int payload, synced on the entity. The built-in kind
 * {@link #CITIZEN} copies a city citizen (the payload is the {@link CitizenLook} seed, so the very same renderer
 * draws it). Other humanoid NPCs can be added later without a hard dependency: register an {@link Adapter} under
 * their entity type id (for example from an optional compat class loaded only when that mod is present). The
 * client draws unknown kinds as a citizen with the same payload, so a missing client adapter never breaks.
 */
public final class T1000Disguises {
   public static final String CITIZEN = "citizen";
   private static final Map<ResourceLocation, Adapter> ADAPTERS = new ConcurrentHashMap<>();

   /** Copies one kind of humanoid. */
   public interface Adapter {
      /** The kind written into the disguise (also the client renderer key). */
      String kind();

      /** May this body be copied right now (alive, humanoid, not something special)? */
      boolean canCopy(LivingEntity source);

      /** The payload that recreates its look. */
      int capture(LivingEntity source);
   }

   /** A captured look. */
   public record Disguise(String kind, int payload) {
   }

   private T1000Disguises() {
   }

   /** Registers an adapter for an entity type id (other mods' humanoids). */
   public static void register(ResourceLocation entityType, Adapter adapter) {
      ADAPTERS.put(entityType, adapter);
   }

   /** The look of this body, or null when it cannot be copied. */
   @Nullable
   public static Disguise capture(LivingEntity source) {
      if (source instanceof T1000Entity || !source.isAlive()) {
         return null;
      }
      if (source instanceof CitizenEntity c) {
         return new Disguise(CITIZEN, c.skin());
      }
      ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(source.getType());
      Adapter a = id == null ? null : ADAPTERS.get(id);
      if (a != null && a.canCopy(source)) {
         return new Disguise(a.kind(), a.capture(source));
      }
      return null;
   }

   /** A fresh look for a new arrival; sometimes the patrol uniform. */
   public static int freshSeed(RandomSource random, boolean preferPolice) {
      int seed = random.nextInt();
      if (preferPolice) {
         for (int i = 0; i < 400; i++) {
            int s = random.nextInt();
            if (CitizenLook.of(s).top() == CitizenLook.Top.POLICE) {
               return s;
            }
         }
      }
      return seed;
   }
}
