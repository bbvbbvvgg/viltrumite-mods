package dev.baranhan.viltrumitecore.hero.redhulk;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player server state of the Red Hulk. Cooldowns and the heat persist (NBT); a respawned body keeps the
 * cooldowns and starts cool.
 */
public final class RedHulkState {
   @Nullable public RedHulkMove move;
   public int elapsed;
   /** Bumped on every move start: the client clock restarts the same clip on it (snapshot variant). */
   public int serial;
   public int comboStep = -1;
   public int comboIdle = 1000;
   public boolean comboQueued;
   /** An ability pressed during a cancellable recovery, started at the next tick. */
   @Nullable public RedHulkAbility queuedAbility;
   public final Set<Integer> hit = new HashSet<>();
   public Vec3 moveDir = Vec3.ZERO;
   public int tickCount;
   public int idle = 1000;

   // temperature
   public int heat;
   public int sinceHeat = 1000;
   public int overheat;
   public int fury;
   /** Heat the running Heat Blast was fired with (its damage scale). */
   public int blastHeat;
   public int lastTier;

   // grab / hold / throw
   public int heldId = -1;
   @Nullable public UUID heldUuid;
   @Nullable public UUID holdEffect;
   public int holdTicks;
   public int thrownId = -1;
   public int thrownTicks;
   public Vec3 throwVelocity = Vec3.ZERO;

   // leap
   public double leapStartY;
   public boolean leapDive;

   public final int[] cooldowns = new int[RedHulkRules.COOLDOWNS];
   public int sinceHurt = 1000;

   public boolean running() {
      return this.move != null;
   }

   public boolean is(RedHulkMove m) {
      return this.move == m;
   }

   public boolean holding() {
      return this.heldId >= 0;
   }

   public boolean overheated() {
      return this.overheat > 0;
   }

   public boolean furious() {
      return this.fury > 0;
   }

   public void start(RedHulkMove m) {
      this.move = m;
      this.elapsed = 0;
      this.serial = (this.serial + 1) & 0xFFFF;
      this.hit.clear();
      this.comboQueued = false;
   }

   public void endMove() {
      this.move = null;
      this.elapsed = 0;
      this.hit.clear();
   }

   /** Adds heat (clamped) and resets the cooling delay. */
   public void addHeat(int amount) {
      if (amount <= 0) {
         return;
      }
      this.heat = RedHulkRules.clampHeat(this.heat + amount);
      this.sinceHeat = 0;
   }

   public void tickTimers() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }
      if (this.overheat > 0) this.overheat--;
      if (this.fury > 0) this.fury--;
      if (this.comboIdle < 1000) this.comboIdle++;
      if (this.idle < 1000) this.idle++;
      if (this.sinceHurt < 1000) this.sinceHurt++;
      if (this.sinceHeat < 1000) this.sinceHeat++;
   }

   public void save(CompoundTag nbt) {
      nbt.putIntArray("RedHulkCooldowns", this.cooldowns.clone());
      nbt.putInt("RedHulkHeat", this.heat);
   }

   public void load(CompoundTag nbt) {
      int[] saved = nbt.getIntArray("RedHulkCooldowns");
      System.arraycopy(saved, 0, this.cooldowns, 0, Math.min(saved.length, this.cooldowns.length));
      this.heat = RedHulkRules.clampHeat(nbt.getInt("RedHulkHeat"));
      this.lastTier = RedHulkRules.tier(this.heat);
   }

   /** The state a respawned body starts with: cooldowns only, cool. */
   public RedHulkState respawnCopy() {
      RedHulkState copy = new RedHulkState();
      System.arraycopy(this.cooldowns, 0, copy.cooldowns, 0, this.cooldowns.length);
      return copy;
   }

   @Nullable
   public static RedHulkState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.RED_HULK) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof RedHulkState state ? state : null;
      }
      return null;
   }

   public static RedHulkState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof RedHulkState existing) {
         return existing;
      }
      RedHulkState state = new RedHulkState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
