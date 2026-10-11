package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side per-player Reinhard state. No shared resource (spec §1.2.4):
 * only the mode and cooldowns. Saved under NBT key {@code Reinhard}.
 */
public final class ReinhardState {
   /** One cooldown per ability (ReinhardAbility ordinal). */
   public static final int COOLDOWN_SLOTS = 14;
   static final String KEY = "Reinhard";

   public ReinhardMode mode = ReinhardMode.RESTRAINED;
   public int modeCooldown;
   public int regenTicks;
   public int[] cooldowns = new int[COOLDOWN_SLOTS];

   /** Counts every cooldown down by one tick. */
   public void tick() {
      if (this.modeCooldown > 0) {
         this.modeCooldown--;
      }

      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }
   }

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      tag.putString("Mode", this.mode.key());
      tag.putInt("ModeCd", this.modeCooldown);
      tag.putIntArray("Cooldowns", this.cooldowns.clone());
      nbt.put(KEY, tag);
   }

   public void load(CompoundTag nbt) {
      if (!nbt.contains(KEY, 10)) {
         return;
      }

      CompoundTag tag = nbt.getCompound(KEY);
      this.mode = ReinhardMode.fromKey(tag.getString("Mode"));
      this.modeCooldown = Math.max(0, tag.getInt("ModeCd"));
      int[] saved = tag.getIntArray("Cooldowns");
      this.cooldowns = new int[COOLDOWN_SLOTS];
      for (int i = 0; i < Math.min(saved.length, COOLDOWN_SLOTS); i++) {
         this.cooldowns[i] = Math.max(0, saved[i]);
      }
   }

   @Nullable
   public static ReinhardState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.REINHARD) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof ReinhardState state ? state : null;
      }

      return null;
   }

   public static ReinhardState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof ReinhardState existing) {
         return existing;
      }

      ReinhardState state = new ReinhardState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
