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
   public static final int COOLDOWN_SLOTS = ReinhardAbility.values().length;
   static final String KEY = "Reinhard";

   public ReinhardMode mode = ReinhardMode.RESTRAINED;
   public int modeCooldown;
   public int regenTicks;
   public int[] cooldowns = new int[COOLDOWN_SLOTS];
   /** Protection Acquisition (spec §4.8): saved, lost with the body (not in {@link #respawnCopy}). */
   public Adaptation adaptation = Adaptation.NONE;
   /** Blessing of the Holy Sword (spec §4.1): ticks left of the parry pose flag; transient. */
   public int parryTicks;
   public final dev.baranhan.viltrumitecore.hero.reinhard.blessing.ArrowCapture arrowCapture = new dev.baranhan.viltrumitecore.hero.reinhard.blessing.ArrowCapture();
   public final dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction defense = new dev.baranhan.viltrumitecore.hero.reinhard.blessing.DefenseReaction();
   /** Last parry contact burst (game tick, attacker id); transient. */
   private long parryFxTick = Long.MIN_VALUE;
   private int parryFxAttacker = -1;

   /** Every parried hit shows its contact burst; repeated hurt calls of one attacker in one tick share it. */
   public boolean parryFx(long gameTime, int attackerId) {
      if (gameTime == this.parryFxTick && attackerId == this.parryFxAttacker) return false;
      this.parryFxTick = gameTime;
      this.parryFxAttacker = attackerId;
      return true;
   }
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
   public boolean resolvingMelee;
   /** LMB strike chain (fists outside Knight, drawn Reid): owner, last move (−1 none), idle ticks, buffered press; transient. */
   @javax.annotation.Nullable
   public ReinhardAbility meleeAbility;
   public int meleeStep = -1;
   public int meleeIdle;
   public boolean meleeQueued;
   /** Bodies the running strike already hit (one hit per body per strike); transient. */
   public final java.util.Set<java.util.UUID> meleeHit = new java.util.HashSet<>();
   /** Transient self-root; no gravity/AI flag ownership and nothing to restore after logout. */
   public net.minecraft.world.phys.Vec3 cutAnchor;
   public net.minecraft.world.phys.Vec3 kickDirection=new net.minecraft.world.phys.Vec3(0,0,1);
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
    * drawing / sheathing (Stage 4); World Cut, World Erasure (Stages 5-6); the Saw Sphere.
    */
   public dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldCut worldCut;

   public dev.baranhan.viltrumitecore.hero.reinhard.ability.WorldErasure worldErasure;

   /** Overhaul item 19: the running Saw Sphere scene (level-owned), null outside it; transient. */
   public dev.baranhan.viltrumitecore.hero.reinhard.saw.SawSphereScene sawSphere;

   /** The Saw Sphere locks, protects and spins its caster for its whole 13 s. */
   public boolean sawSphereActive() {
      return this.sawSphere != null && !this.sawSphere.ended();
   }

   public boolean busy() {
      // The detached cut blasts at tick 80, but the cast pose and movement lock
      // keep running until the action ends (tick 100): both must block MODE_CYCLE.
      // A cast dropped with its level never blasts and must not lock the caster.
      return (worldErasure != null && !worldErasure.ended()) || this.reid.busy() || this.sawSphereActive()
         || (worldCut != null && !worldCut.blasted() && !worldCut.dropped())
         || this.action.is(ReinhardAbility.WORLD_CUT) || this.action.is(ReinhardAbility.DIAGONAL_WORLD_CUT);
   }

   /** Leaving Holy Sword (spec §5): Reid goes back into the sheath. */
   public void onLeaveHolySword() {
      this.reid.sheatheNow();
   }

   /** Counts every cooldown down by one tick. */
   public void tick() {
      defense.tick();
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
      this.modeCooldown = Math.min(ReinhardRules.MODE_COOLDOWN,Math.max(0, tag.getInt("ModeCd")));
      int[] saved = tag.getIntArray("Cooldowns");
      this.cooldowns = new int[COOLDOWN_SLOTS];
      for (int i = 0; i < Math.min(saved.length, COOLDOWN_SLOTS); i++) {
         this.cooldowns[i] = Math.min(ReinhardRules.cooldownLimit(ReinhardAbility.values()[i]),Math.max(0, saved[i]));
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
