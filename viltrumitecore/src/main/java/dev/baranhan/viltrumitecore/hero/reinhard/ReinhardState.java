package dev.baranhan.viltrumitecore.hero.reinhard;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.reinhard.blessing.Adaptation;
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
   /** Protection Acquisition (spec §4.8): saved, lost with the body (not in {@link #respawnCopy}). */
   public Adaptation adaptation = Adaptation.NONE;
   /** Blessing of the Holy Sword (spec §4.1): ticks left of the parry pose flag; transient. */
   public int parryTicks;
   /** Wind Reading (spec §4.5): players who hit him → game time of the hit; transient. */
   public final java.util.Map<java.util.UUID, Long> recentAttackers = new java.util.HashMap<>();
   /** Stage 3: the one action timeline (ability, elapsed, length, target, phase); transient. */
   public final ActionTimeline action = new ActionTimeline();
   /** Catch Projectile (stage 3): ticks the palm stays open; transient. */
   public int catchWindow;
   /** Reflect Projectile (stage 3): ticks the backhand window stays open; transient. */
   public int reflectWindow;
   /** Fist Combo (stage 3): the last hit of the chain (0-2, −1 none), idle ticks since it ended, a press queued during a hit; transient. */
   public int comboStep = -1;
   public int comboIdle;
   public boolean comboQueued;
   /** Grab &amp; Throw (stage 3): the held target and the one control effect id of this grab, null = none; transient. */
   public dev.baranhan.viltrumitecore.hero.reinhard.ability.GrabThrow.Hold grab;
   /** Grab &amp; Throw: the thrown target (−1 none), sustain ticks left / done and the re-applied velocity; transient. */
   public int throwTargetId = -1;
   public int throwSustain;
   public int throwSustained;
   public net.minecraft.world.phys.Vec3 throwVelocity;
   /** Dash Behind (stage 3): the packet's request while its press is gated, null otherwise; transient. */
   public dev.baranhan.viltrumitecore.hero.reinhard.ability.DashBehind.Request behindRequest;

   /** Stage 4: the Dragon Sword Reid (spec §7.3); saved by phase, sheathed on a fresh body. */
   public final dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid reid = new dev.baranhan.viltrumitecore.hero.reinhard.reid.Reid();
   /** Stage 4: the running draw scene (lift, wave, sounds, client scene), null outside a draw; transient. */
   public dev.baranhan.viltrumitecore.hero.reinhard.reid.ReidDrawEvent drawEvent;

   /**
    * Busy = an action that must not be interrupted by a mode switch: Reid
    * drawing / sheathing (Stage 4); World Cut, World Erasure (Stages 5-6).
    */
   public dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut worldCut;

   public dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldErasure worldErasure;

   public boolean busy() {
      // Stage 5 plan: the cast locks the caster until the explosion tick; the travelling wave
      // afterwards does not (the caster regains control). A held grab also locks the caster.
      return (worldErasure != null && !worldErasure.ended()) || this.reid.busy()
         || (this.worldCut != null ? !this.worldCut.blasted() : this.action.is(ReinhardAbility.WORLD_CUT))
         || this.grab != null;
   }

   /** Leaving Holy Sword (spec §5): Reid goes back into the sheath. */
   public void onLeaveHolySword() {
      this.reid.sheatheNow();
   }

   /** Counts every cooldown down by one tick. */
   public void tick() {
      if (this.modeCooldown > 0) {
         this.modeCooldown--;
      }

      if (this.parryTicks > 0) {
         this.parryTicks--;
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
      tag.put(Adaptation.NBT_KEY, this.adaptation.save());
      tag.put("Reid", this.reid.save());
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

      this.adaptation = Adaptation.load(tag.getCompound(Adaptation.NBT_KEY));
      this.reid.load(tag.getCompound("Reid"));
   }

   /** What the respawned body keeps: mode and cooldowns; adaptations die with the body (spec §4.8). */
   public ReinhardState respawnCopy() {
      ReinhardState next = new ReinhardState();
      next.mode = this.mode;
      next.modeCooldown = this.modeCooldown;
      next.cooldowns = this.cooldowns.clone();
      return next;
   }

   /** Same-entity death keeps mode/cooldowns, not Reid, adaptation or combat. */
   public ReinhardState erasedCopy() {return this.respawnCopy();}

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
