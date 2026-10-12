package dev.baranhan.viltrumitecore.hero.reinhard.blessing;

import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Blessing of Detoxification (spec §4.6): no harmful effect ever applies to him
 * (poison, wither, darkness, nausea, slowness, ...). Beneficial and neutral
 * effects pass (Glowing from someone's spectral arrow stays, so their outlines
 * work; the hidden hero passives are beneficial). Mode-independent.
 */
public final class Detox {
   private Detox() {
   }

   /** Pure: HARMFUL, or a mod effect tagged poison-like. */
   public static boolean blocks(MobEffectCategory category, boolean poisonLike) {
      return category == MobEffectCategory.HARMFUL || poisonLike;
   }

   public static boolean blocks(MobEffectInstance instance) {
      MobEffect effect = instance.getEffect();
      return blocks(effect.getCategory(), PoisonTag.has(effect));
   }

   /** Becoming Reinhard: harmful effects he already carries are washed out. */
   public static void cleanse(Player player) {
      for (MobEffectInstance instance : new ArrayList<>(player.getActiveEffects())) {
         if (blocks(instance)) {
            player.removeEffect(instance.getEffect());
         }
      }
   }

   /** Registry statics apart, so the pure rule loads in unit tests. */
   private static final class PoisonTag {
      /** Mod effects that call themselves poison (Forge convention tag; empty in vanilla). */
      static final TagKey<MobEffect> POISON = TagKey.create(Registries.MOB_EFFECT, new ResourceLocation("forge", "poison"));

      static boolean has(MobEffect effect) {
         return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect).is(POISON);
      }
   }
}
