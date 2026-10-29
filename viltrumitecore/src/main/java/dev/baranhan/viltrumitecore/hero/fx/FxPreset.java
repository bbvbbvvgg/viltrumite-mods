package dev.baranhan.viltrumitecore.hero.fx;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A named, reusable world-FX recipe (hero-neutral). Gameplay data (a move
 * table row, a hammer impact) refers to a preset by its string {@link #id};
 * the wire carries the small stable {@link #netId}. The server sends a
 * preset with {@link HeroFx#preset}; the client recipe with the same id
 * ({@code client.render.vfx.FxRecipe}) lists the layers that
 * {@code HeroImpactFx} plays. Never reuse or renumber a net id: old and new
 * clients must agree on it.
 */
public record FxPreset(String id, int netId) {
   /** Net ids fit a packet float exactly (below 2^24) and a short range keeps ids readable. */
   public static final int MAX_NET_ID = 4095;
   private static final Map<String, FxPreset> BY_ID = new LinkedHashMap<>();
   private static final Map<Integer, FxPreset> BY_NET = new LinkedHashMap<>();

   public FxPreset {
      Objects.requireNonNull(id, "id");
      if (id.isEmpty() || id.indexOf(':') <= 0) {
         throw new IllegalArgumentException("preset id needs a namespace: " + id);
      }

      if (netId <= 0 || netId > MAX_NET_ID) {
         throw new IllegalArgumentException("preset net id out of range: " + netId);
      }
   }

   /** Registers a preset once; a second id or net id is a programming error. */
   public static synchronized FxPreset register(String id, int netId) {
      FxPreset preset = new FxPreset(id, netId);
      FxPreset sameId = BY_ID.get(id);
      if (sameId != null) {
         if (sameId.equals(preset)) {
            return sameId;
         }

         throw new IllegalStateException("duplicate preset id " + id);
      }

      if (BY_NET.containsKey(netId)) {
         throw new IllegalStateException("preset net id " + netId + " used by " + BY_NET.get(netId).id() + " and " + id);
      }

      BY_ID.put(id, preset);
      BY_NET.put(netId, preset);
      return preset;
   }

   @Nullable
   public static synchronized FxPreset byId(String id) {
      return BY_ID.get(id);
   }

   @Nullable
   public static synchronized FxPreset byNetId(int netId) {
      return BY_NET.get(netId);
   }

   public static synchronized Collection<FxPreset> all() {
      return Collections.unmodifiableCollection(new java.util.ArrayList<>(BY_ID.values()));
   }

   @Override
   public String toString() {
      return this.id;
   }
}
