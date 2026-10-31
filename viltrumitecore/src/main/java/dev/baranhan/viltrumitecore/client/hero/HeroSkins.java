package dev.baranhan.viltrumitecore.client.hero;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

public final class HeroSkins {
   private static final Map<HeroId, Provider> PROVIDERS = new EnumMap<>(HeroId.class);

   private HeroSkins() {
   }

   public static void register(HeroId id, Provider provider) {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(provider, "provider");
      if (PROVIDERS.putIfAbsent(id, provider) != null) {
         throw new IllegalStateException("Skin provider already registered for " + id);
      }
   }

   /** The skin provider a hero registered, if any (encyclopedia portraits use its default skin). */
   public static Optional<Provider> provider(HeroId id) {
      return Optional.ofNullable(id == null ? null : PROVIDERS.get(id));
   }

   public static Optional<ResolvedHeroSkin> resolve(AbstractClientPlayer player) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return Optional.empty();
      }

      Provider provider = PROVIDERS.get(heroPlayer.getHeroId());
      if (provider == null) {
         return Optional.empty();
      }

      ResourceLocation skin = provider.skinVariant(player).orElseGet(provider::defaultSkin);
      ResourceLocation hand = provider.handTexture(player).orElse(skin);
      String model = provider.modelName(player).orElseGet(provider::defaultModelName);
      return Optional.of(new ResolvedHeroSkin(
         skin, hand, model, provider.suppressOverride(player), provider.suppressCosmetics(player), provider.suppressCape(player)
      ));
   }

   public interface Provider {
      ResourceLocation defaultSkin();

      String defaultModelName();

      default Optional<ResourceLocation> skinVariant(AbstractClientPlayer player) {
         return Optional.empty();
      }

      default Optional<ResourceLocation> handTexture(AbstractClientPlayer player) {
         return Optional.empty();
      }

      default Optional<String> modelName(AbstractClientPlayer player) {
         return Optional.empty();
      }

      default boolean suppressOverride(AbstractClientPlayer player) {
         return false;
      }

      default boolean suppressCosmetics(AbstractClientPlayer player) {
         return false;
      }

      default boolean suppressCape(AbstractClientPlayer player) {
         return false;
      }
   }
}
