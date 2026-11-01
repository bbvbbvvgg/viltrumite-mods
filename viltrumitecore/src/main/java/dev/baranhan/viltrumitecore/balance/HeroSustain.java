package dev.baranhan.viltrumitecore.balance;

import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import dev.baranhan.viltrumitecore.hero.HeroPassiveEffect;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The shared hero support system (balance spec §3). Every player who chose a hero
 * ({@code HeroDefinition.heroSustain}) keeps a full food bar and a permanent
 * Regeneration I; a plain human keeps vanilla hunger. One server pass every
 * {@link BalanceTable#SUSTAIN_INTERVAL} ticks, no packets of its own: the food bar
 * syncs through vanilla only when it actually changed.
 *
 * <p>Regeneration rides {@link HeroPassiveEffect} (the shared permanent-passive seam):
 * refreshed long before it lapses (no flicker), never over a stronger regeneration
 * (Iron Man's Regeneration II, a potion, a beacon), and only our own instance is taken
 * away when the hero is dropped. NPCs, Viltrumite fake players, summons and bosses are
 * not players and never get it.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroSustain {
   private HeroSustain() {
   }

   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
         && player.tickCount % BalanceTable.SUSTAIN_INTERVAL == 0) {
         tick(player);
      }
   }

   /** One sustain pass for a real player. */
   public static void tick(ServerPlayer player) {
      if (player instanceof ViltrumiteFakePlayer || player instanceof FakePlayer || !player.isAlive()) {
         return;
      }

      if (HeroRegistry.get(player).heroSustain(player)) {
         feed(player.getFoodData());
         HeroPassiveEffect.keep(player, MobEffects.REGENERATION, BalanceTable.SUSTAIN_REGEN_AMPLIFIER);
      } else {
         HeroPassiveEffect.drop(player, MobEffects.REGENERATION, BalanceTable.SUSTAIN_REGEN_AMPLIFIER);
      }
   }

   /** Full food bar; saturation held at {@link BalanceTable#SUSTAIN_SATURATION_CAP}. */
   static void feed(FoodData food) {
      if (food.getFoodLevel() < 20) {
         food.setFoodLevel(20);
      }

      if (food.getSaturationLevel() > BalanceTable.SUSTAIN_SATURATION_CAP) {
         food.setSaturation(BalanceTable.SUSTAIN_SATURATION_CAP);
      }

      if (food.getExhaustionLevel() > 0.0F) {
         food.setExhaustion(0.0F);
      }
   }

   /** Pure: the food level a sustain pass leaves. */
   public static int fedLevel(int food, boolean hero) {
      return hero ? 20 : food;
   }
}
