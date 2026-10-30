package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Evangelium (spec §11/§16): the bound ritual book — granted on hero entry and
 * on respawn when missing, never dropped or thrown. Holding it channels a 60t
 * ritual under Slowness II; only an early book release interrupts into the
 * 400t cooldown. Completion grants 900t of madness
 * paid 0.6 HP per second, and the 1800t book cooldown starts when madness ENDS.
 */
public final class Evangelium {
   private Evangelium() {
   }

   /** Grant the bound book once (hero entry + respawn); never duplicates. */
   public static void grant(ServerPlayer player) {
      if (!hasBook(player)) {
         player.getInventory().add(new ItemStack(ViltrumiteItems.EVANGELIUM.get()));
      }
   }

   public static boolean hasBook(Player player) {
      Inventory inventory = player.getInventory();
      for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
         if (inventory.getItem(slot).is(ViltrumiteItems.EVANGELIUM.get())) {
            return true;
         }
      }

      // The ender chest is still the player's own storage: a book stashed there
      // must not dupe the respawn grant (a1/a3 audit finding, cosmetic).
      Container enderChest = player.getEnderChestInventory();
      for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
         if (enderChest.getItem(slot).is(ViltrumiteItems.EVANGELIUM.get())) {
            return true;
         }
      }

      return false;
   }

   /** Item use -> begin the 60t channel. Server-authoritative gate. */
   public static boolean beginRitual(ServerPlayer player) {
      RegulusState state = RegulusHero.stateOf(player);
      if (state == null || !canBegin(state, HeroDamage.isAnchored(player))) {
         return false;
      }

      state.ritualTicks = 0;
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, true, false, true));
      player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
      return true;
   }

   /** The ritual needs an idle, unanchored Regulus who is not mad, not on cooldown and not channeling (spec 8.2). */
   static boolean canBegin(RegulusState state) {
      return canBegin(state, false);
   }

   static boolean canBegin(RegulusState state, boolean anchored) {
      return !anchored
         && state.ritualTicks < 0
         && state.madnessTicksLeft <= 0
         && state.cooldownOf(RegulusAbilities.EVANGELIUM) <= 0
         && !state.busy()
         && state.channelTargetId == null;
   }

   static boolean ritualFinished(RegulusState state) {
      return state.ritualTicks >= RegulusRules.RITUAL_TICKS;
   }

   /** 0.6 HP every 20 ticks of elapsed madness; the first hit lands one second in. */
   static boolean bloodPriceDue(int madnessElapsed) {
      return madnessElapsed > 0 && madnessElapsed % 20 == 0;
   }

   /** Pure interrupt: 400t cooldown with hearts sampled at the interrupt. */
   static boolean interruptState(RegulusState state) {
      if (state.ritualTicks < 0) {
         return false;
      }

      state.ritualTicks = -1;
      state.startCooldown(RegulusAbilities.EVANGELIUM, RegulusRules.RITUAL_CANCEL_COOLDOWN);
      return true;
   }

   /** Pure completion: the ritual ends and 900t of madness begins. */
   static boolean beginMadnessState(RegulusState state) {
      if (!ritualFinished(state)) {
         return false;
      }

      state.ritualTicks = -1;
      state.madnessTicksLeft = RegulusRules.MADNESS_TICKS;
      return true;
   }

   /** Pure madness end: the 1800t cooldown is charged here, hearts sampled now. */
   static boolean endMadnessState(RegulusState state) {
      if (state.madnessTicksLeft <= 0) {
         return false;
      }

      state.madnessTicksLeft = 0;
      state.startCooldown(RegulusAbilities.EVANGELIUM, RegulusRules.EVANGELIUM_COOLDOWN);
      return true;
   }

   /** The item channel finished naturally — madness starts. */
   public static void completeRitual(ServerPlayer player) {
      RegulusState state = RegulusHero.stateOf(player);
      if (state == null || !beginMadnessState(state)) {
         return;
      }

      RegulusPassives.applyMadness(player);
      player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viltrumitecore.evangelium.complete"), true);
      player.stopUsingItem();
      player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
   }

   public static void finishBookUse(ServerPlayer player) {
      RegulusState state = RegulusHero.stateOf(player);
      if (state == null || state.ritualTicks < 0 || !player.isUsingItem()
         || !player.getUseItem().is(ViltrumiteItems.EVANGELIUM.get()) || player.getUseItemRemainingTicks() > 0) {
         return;
      }
      state.ritualTicks = RegulusRules.RITUAL_TICKS;
      completeRitual(player);
   }

   /** Releasing the book early cancels the ritual for 400t. */
   public static void interrupt(ServerPlayer player, RegulusState state) {
      if (!interruptState(state)) {
         return;
      }

      player.stopUsingItem();
      player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.viltrumitecore.evangelium.cancelled"), true);
   }

   /** Item-side interrupt without a state at hand. */
   public static void interrupt(ServerPlayer player) {
      RegulusState state = RegulusHero.stateOf(player);
      if (state != null) {
         interrupt(player, state);
      }
   }

   /** Madness expiry: strip modifiers and charge the 1800t cooldown. */
   static void endMadness(ServerPlayer player, RegulusState state) {
      if (!endMadnessState(state)) {
         return;
      }

      RegulusPassives.removeMadness(player);
   }

   public static void tick(ServerPlayer player, RegulusState state) {
      if (state.ritualTicks >= 0) {
         if (!player.isAlive()) {
            // Death drops the ritual quietly — a corpse must not pay the cancel fee.
            state.ritualTicks = -1;
         } else if (!player.isUsingItem() || !player.getUseItem().is(ViltrumiteItems.EVANGELIUM.get())) {
            interrupt(player, state);
         } else {
            state.ritualTicks = Math.max(state.ritualTicks, RegulusRules.RITUAL_TICKS - player.getUseItemRemainingTicks());
            // Short refresh: a cancelled ritual sheds Slowness II on its own.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, true, false, true));
            if (ritualFinished(state)) {
               completeRitual(player);
            }
         }
      }

      if (state.madnessTicksLeft > 0) {
         state.madnessTicksLeft--;
         int elapsed = RegulusRules.MADNESS_TICKS - state.madnessTicksLeft;
         if (bloodPriceDue(elapsed)) {
            HeroDamage.applyInternal(player, RegulusRules.BLOOD_PRICE_PER_20_TICKS);
         }

         if (state.madnessTicksLeft <= 0) {
            endMadness(player, state);
         }
      }
   }

   /**
    * Lifecycle cleanup: strip the madness modifiers. Transient fields (ritual
    * counters, positions) are wiped by RegulusState.resetTransient afterwards;
    * cleanup never charges a cooldown — death/change discard the state anyway.
    */
   public static void cleanup(ServerPlayer player, RegulusState state, CleanupReason reason) {
      RegulusPassives.removeMadness(player);
   }
}
