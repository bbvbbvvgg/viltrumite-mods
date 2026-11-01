package dev.baranhan.viltrumitecore.client.ultron;

import dev.baranhan.viltrumitecore.entity.ultron.UltronBrute;
import dev.baranhan.viltrumitecore.entity.ultron.UltronDrone;
import dev.baranhan.viltrumitecore.entity.ultron.UltronEngineer;
import dev.baranhan.viltrumitecore.entity.ultron.UltronInterceptor;
import dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Resource paths of Ultron's models, clips and atlases (own work, CC0: tools/assets/ultron_*.py, ledger in
 * tools/assets/ultron_sources.md). Large files ship split under src/main/binassets and are joined at build time.
 */
public final class UltronAssets {
   public static final Model DRONE = new Model("drone");
   public static final Model SENTRY = new Model("sentry");
   public static final Model BRUTE = new Model("brute");
   public static final Model INTERCEPTOR = new Model("interceptor");
   public static final Model ENGINEER = new Model("engineer");
   public static final Model PRIME = new Model("prime");
   public static final Model NODE = new Model("node");
   public static final Model MISSILE = new Model("missile");
   public static final List<Model> ALL = List.of(DRONE, SENTRY, BRUTE, INTERCEPTOR, ENGINEER, PRIME, NODE, MISSILE);

   private UltronAssets() {
   }

   /** One model: geometry, clips, the metal atlas and the emissive atlas. */
   public record Model(String name, ResourceLocation geo, ResourceLocation anim, ResourceLocation texture, ResourceLocation glow) {
      Model(String name) {
         this(name, rl("geo/ultron/" + name + ".geo.json"), rl("animations/ultron/" + name + ".animation.json"), rl("textures/entity/ultron/" + name + ".png"),
            rl("textures/entity/ultron/" + name + "_glow.png"));
      }
   }

   /** The model a machine renders with (Ultron Prime goes by his type: his kind is a placeholder). */
   @Nullable
   public static Model of(@Nullable Entity e) {
      if (e instanceof UltronPrime) {
         return PRIME;
      } else if (e instanceof UltronDrone) {
         return DRONE;
      } else if (e instanceof UltronBrute) {
         return BRUTE;
      } else if (e instanceof UltronInterceptor) {
         return INTERCEPTOR;
      } else if (e instanceof UltronEngineer) {
         return ENGINEER;
      } else if (e instanceof dev.baranhan.viltrumitecore.entity.ultron.UltronSentry) {
         return SENTRY;
      }
      return null;
   }

   static ResourceLocation rl(String path) {
      return new ResourceLocation("viltrumitecore", path);
   }
}
