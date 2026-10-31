package dev.baranhan.viltrumitecore.hero.budget;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Shared chassis of every Ultimate Hero Expansion hero. One instance per
 * roster entry; all behaviour comes from the {@link BudgetHeroSpec}. Uses
 * the vanilla player body, the generic slot input pipeline and only
 * transient state, so entering, leaving and saving behave like a human.
 */
public final class BudgetHero implements HeroDefinition {
   private final BudgetHeroSpec spec;
   private final String[] slotIds;

   public BudgetHero(BudgetHeroSpec spec) {
      this.spec = spec;
      this.slotIds = new String[spec.moves().length];
      for (int i = 0; i < this.slotIds.length; i++) {
         this.slotIds[i] = spec.abilityId(spec.moves()[i]);
      }
   }

   public BudgetHeroSpec spec() {
      return this.spec;
   }

   @Override
   public HeroId id() {
      return this.spec.id();
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      for (String id : this.slotIds) {
         if (id.equals(abilityId)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public String[] heroInputSlots() {
      return this.slotIds.clone();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      for (int i = 0; i < this.slotIds.length; i++) {
         if (this.slotIds[i].equals(abilityId)) {
            return this.spec.moves()[i].type().action();
         }
      }

      return null;
   }

   @Override
   public List<ViltrumiteAbility> panelAbilities() {
      List<ViltrumiteAbility> list = new ArrayList<>();
      BudgetMove[] moves = this.spec.moves();
      for (int i = 0; i < moves.length; i++) {
         BudgetMove move = moves[i];
         int slot = i;
         String lang = this.spec.abilityLangKey(move);
         list.add(new ViltrumiteAbility(this.slotIds[i],
            new ResourceLocation("viltrumitecore", "textures/gui/ability/" + move.type().icon() + ".png"),
            lang + ".name", lang + ".desc", 0, player -> this.slotGrey(player, slot)));
      }

      return list;
   }

   private boolean slotGrey(Player player, int slot) {
      if (!(player instanceof HeroPlayer heroPlayer)) {
         return true;
      }

      HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      return snapshot == null || snapshot.heroId() != this.id() || snapshot.actionBusy()
         || (slot < snapshot.cooldowns().length && snapshot.cooldowns()[slot] > 0);
   }

   @Override
   public String[] defaultLoadout() {
      String[] loadout = new String[18];
      Arrays.fill(loadout, "");
      System.arraycopy(this.slotIds, 0, loadout, 0, this.slotIds.length);
      return loadout;
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      int index = this.spec.indexOf(action);
      if (index < 0 || !player.isAlive() || player.isSpectator()) {
         return false;
      }

      BudgetState state = BudgetState.of(player.getUUID());
      return state.sceneTicks <= 0 && now(player) >= state.readyAt[index];
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (!pressed) {
         return;
      }

      int index = this.spec.indexOf(action);
      if (index < 0) {
         return;
      }

      if (!this.canAct(player, action)) {
         BudgetState state = BudgetState.of(player.getUUID());
         long left = Math.max(0L, state.readyAt[index] - now(player));
         if (left > 0) {
            player.displayClientMessage(Component.translatable("message.viltrumitecore.expansion.cooldown",
               Component.translatable(this.spec.abilityLangKey(this.spec.moves()[index]) + ".name"), (left + 19) / 20), true);
         }
         return;
      }

      BudgetMove move = this.spec.moves()[index];
      BudgetState state = BudgetState.of(player.getUUID());
      state.readyAt[index] = now(player) + move.cooldown();
      if (move.type() == BudgetMoveType.E && this.spec.hasScene()) {
         state.sceneTicks = this.spec.scene().windup;
         state.sceneMove = index;
         BudgetMoves.startScene(player, this.spec, state);
         return;
      }

      BudgetMoves.perform(player, this.spec, move, state);
   }

   @Override
   public void tick(ServerPlayer player) {
      BudgetState state = BudgetState.peek(player.getUUID());
      if (state == null || state.sceneTicks <= 0) {
         return;
      }

      state.sceneTicks--;
      BudgetMoves.tickScene(player, this.spec, state);
      if (state.sceneTicks <= 0) {
         int index = state.sceneMove;
         state.sceneMove = -1;
         if (index >= 0 && index < this.spec.moves().length && player.isAlive()) {
            BudgetMoves.perform(player, this.spec, this.spec.moves()[index], state);
         }
      }
   }

   @Override
   public void enter(ServerPlayer player) {
      BudgetState.remove(player.getUUID());
      player.displayClientMessage(Component.translatable("message.viltrumitecore.expansion.enter",
         Component.translatable(this.spec.nameKey())), true);
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      BudgetState state = BudgetState.remove(player.getUUID());
      if (state == null) {
         return;
      }

      // Back to a plain human: drop our own power-ups and any scene hold.
      for (MobEffect effect : state.granted) {
         player.removeEffect(effect);
      }

      if (state.sceneTicks > 0) {
         BudgetMoves.cancelScene(player);
      }
   }

   @Override
   public boolean cancelsFallDamage(Player player) {
      if (!(player instanceof ServerPlayer serverPlayer)) {
         return false;
      }

      BudgetState state = BudgetState.peek(player.getUUID());
      return state != null && now(serverPlayer) < state.noFallUntil;
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      int[] cooldowns = new int[HeroPublicSnapshot.COOLDOWN_COUNT];
      boolean busy = false;
      if (player instanceof ServerPlayer serverPlayer) {
         BudgetState state = BudgetState.peek(player.getUUID());
         if (state != null) {
            long now = now(serverPlayer);
            for (int i = 0; i < this.spec.moves().length && i < cooldowns.length; i++) {
               // Whole seconds only: the snapshot then changes once a second, not every tick.
               cooldowns[i] = (int)Math.max(0L, (state.readyAt[i] - now + 19) / 20);
            }
            busy = state.sceneTicks > 0;
         }
      }

      return new HeroPublicSnapshot(this.id(), -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, cooldowns, busy);
   }

   static long now(ServerPlayer player) {
      return player.getServer() == null ? player.level().getGameTime() : player.getServer().getTickCount();
   }
}
