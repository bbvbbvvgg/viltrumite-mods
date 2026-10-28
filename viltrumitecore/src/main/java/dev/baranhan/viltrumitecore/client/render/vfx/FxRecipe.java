package dev.baranhan.viltrumitecore.client.render.vfx;

import dev.baranhan.viltrumitecore.hero.fx.FxPreset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Client recipe of an {@link FxPreset}: the timed layers {@code HeroImpactFx}
 * plays for one preset packet. Pure data (unit tested), hero-neutral; a hero
 * registers its recipes from its client init. Sizes are in blocks at scale 1
 * (sized for a 3.2-block hero) and grow with the packet scale; delays and
 * lives are ticks and do not scale.
 */
public record FxRecipe(FxPreset preset, List<Layer> layers) {
   private static final Map<Integer, FxRecipe> BY_NET = new LinkedHashMap<>();

   public FxRecipe {
      Objects.requireNonNull(preset, "preset");
      layers = List.copyOf(layers);
      if (layers.isEmpty()) {
         throw new IllegalArgumentException("empty recipe " + preset.id());
      }
   }

   /** What one layer draws. */
   public enum Kind {
      /** Sky bolt onto the origin: {@code size} channel height, {@code width}, {@code count} branches, {@code spread} random offset. */
      BOLT,
      /** Jagged discharge origin → target: {@code width}, {@code count} re-jitters over its life. */
      ARC,
      /** Discharges on a ground circle; the radius is the origin → target distance times {@code size}. */
      RING,
      /** Micro arcs in a ball of radius {@code size} around the origin, {@code count} arcs. */
      CRACKLE,
      /** {@code count} discharges crawling outward over the ground, each {@code size} long. */
      GROUND_CRAWL,
      /** {@code count} pixel sparks thrown from the origin at {@code power} speed. */
      SPARKS,
      /** Additive light pool on the ground, radius {@code size}, brightness {@code power}. */
      LIGHT_POOL,
      /** Lit cloud sheet {@code size} blocks above the origin, radius {@code width}. */
      SKY_FLASH,
      /** Full-screen flash: strength {@code power} at the source, falls to zero at {@code size} blocks. */
      SCREEN_FLASH,
      /** Burn decal on the ground, radius {@code size}, {@code count} rays; {@code life} is how long it stays. */
      SCORCH,
      /** Ground dust: radius {@code size}, {@code count} particles. */
      DUST,
      /** {@code count} flying shards of the ground, up to {@code size} blocks away, {@code power} lift. */
      DEBRIS,
      /** Expanding ground ring, radius {@code size}; {@code power} > 1 = heavy wall. */
      SHOCKWAVE,
      /** White cross flash of {@code size} blocks at the origin. */
      FLASH,
      /** Camera shake {@code power}, falling to zero at {@code size} blocks. */
      SHAKE
   }

   public record Layer(Kind kind, int delay, int life, float size, float width, float power, int count, float spread) {
      public Layer {
         Objects.requireNonNull(kind, "kind");
         if (delay < 0 || life <= 0) {
            throw new IllegalArgumentException("bad timing " + kind + " " + delay + "/" + life);
         }
      }

      public int end() {
         return this.delay + this.life;
      }
   }

   /** Last tick on which any layer is still alive (the recipe's visual length). */
   public int length() {
      int end = 0;
      for (Layer layer : this.layers) {
         if (layer.kind() != Kind.SCORCH) {
            end = Math.max(end, layer.end());
         }
      }

      return end;
   }

   /** Layers of one kind (tests, tuning). */
   public List<Layer> of(Kind kind) {
      List<Layer> out = new ArrayList<>();
      for (Layer layer : this.layers) {
         if (layer.kind() == kind) {
            out.add(layer);
         }
      }

      return out;
   }

   public static synchronized void register(FxRecipe recipe) {
      FxRecipe previous = BY_NET.put(recipe.preset().netId(), recipe);
      if (previous != null && !previous.preset().equals(recipe.preset())) {
         throw new IllegalStateException("recipe net id clash " + previous.preset() + " / " + recipe.preset());
      }
   }

   @Nullable
   public static synchronized FxRecipe byNetId(int netId) {
      return BY_NET.get(netId);
   }

   @Nullable
   public static FxRecipe of(FxPreset preset) {
      return byNetId(preset.netId());
   }

   public static synchronized Collection<FxRecipe> all() {
      return Collections.unmodifiableCollection(new ArrayList<>(BY_NET.values()));
   }

   public static Builder builder(FxPreset preset) {
      return new Builder(preset);
   }

   /** Fluent recipe writer: one call per layer, in time order. */
   public static final class Builder {
      private final FxPreset preset;
      private final List<Layer> layers = new ArrayList<>();

      private Builder(FxPreset preset) {
         this.preset = preset;
      }

      public Builder layer(Kind kind, int delay, int life, float size, float width, float power, int count, float spread) {
         this.layers.add(new Layer(kind, delay, life, size, width, power, count, spread));
         return this;
      }

      public Builder bolt(int delay, int life, float height, float width, int branches, float spread) {
         return this.layer(Kind.BOLT, delay, life, height, width, 1.0F, branches, spread);
      }

      public Builder arc(int delay, int life, float width, int rejitters) {
         return this.layer(Kind.ARC, delay, life, 1.0F, width, 1.0F, rejitters, 0.0F);
      }

      public Builder ring(int delay, int life, float width, int arcs) {
         return this.layer(Kind.RING, delay, life, 1.0F, width, 1.0F, arcs, 0.0F);
      }

      public Builder crackle(int delay, int life, float radius, int arcs) {
         return this.layer(Kind.CRACKLE, delay, life, radius, 0.05F, 1.0F, arcs, 0.0F);
      }

      public Builder crawl(int delay, int life, float length, int arcs) {
         return this.layer(Kind.GROUND_CRAWL, delay, life, length, 0.08F, 1.0F, arcs, 0.0F);
      }

      public Builder sparks(int delay, int life, int count, float speed) {
         return this.layer(Kind.SPARKS, delay, life, 0.0F, 0.0F, speed, count, 0.0F);
      }

      public Builder lightPool(int delay, int life, float radius, float brightness) {
         return this.layer(Kind.LIGHT_POOL, delay, life, radius, 0.0F, brightness, 0, 0.0F);
      }

      public Builder skyFlash(int delay, int life, float height, float radius) {
         return this.layer(Kind.SKY_FLASH, delay, life, height, radius, 1.0F, 0, 0.0F);
      }

      public Builder screenFlash(int delay, int life, float strength, float range) {
         return this.layer(Kind.SCREEN_FLASH, delay, life, range, 0.0F, strength, 0, 0.0F);
      }

      public Builder scorch(int delay, int life, float radius, int rays) {
         return this.layer(Kind.SCORCH, delay, life, radius, 0.0F, 1.0F, rays, 0.0F);
      }

      public Builder dust(int delay, float radius, int count) {
         return this.layer(Kind.DUST, delay, 1, radius, 0.0F, 1.0F, count, 0.0F);
      }

      public Builder debris(int delay, int count, float distance, float lift) {
         return this.layer(Kind.DEBRIS, delay, 1, distance, 0.0F, lift, count, 0.0F);
      }

      public Builder shockwave(int delay, int life, float radius, float heavy) {
         return this.layer(Kind.SHOCKWAVE, delay, life, radius, 0.0F, heavy, 0, 0.0F);
      }

      public Builder flash(int delay, int life, float size) {
         return this.layer(Kind.FLASH, delay, life, size, 0.0F, 1.0F, 0, 0.0F);
      }

      public Builder shake(int delay, float power, float range) {
         return this.layer(Kind.SHAKE, delay, 1, range, 0.0F, power, 0, 0.0F);
      }

      public FxRecipe build() {
         return new FxRecipe(this.preset, this.layers);
      }
   }
}
