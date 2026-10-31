package dev.baranhan.viltrumitecore.hero.captain;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.captain.assemble.AssembleScene;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Per-player server state of Captain America. Only cooldowns persist (NBT and the death copy). */
public final class CaptainState {
   @Nullable public CaptainMove move;
   public int elapsed;
   public int comboStep = -1;
   public int comboIdle = 1000;
   public boolean comboQueued;
   /** The chain of the last finished combo hit: shield (true) or fists. */
   public boolean lastChainShield = true;
   public final Set<Integer> hit = new HashSet<>();
   public Vec3 moveDir = Vec3.ZERO;
   public int tickCount;
   public int idle = 1000;

   // shield
   public int shieldId = -1;
   public boolean throwEmpowered;
   public int aimTicks;
   public boolean aimHeld;
   /** Release mode chosen at the end of the aim: bank shot (true) or target chain (false). */
   public boolean throwBank;
   /** Aim preview point (bank-shot hold) for the owner's HUD line, null when not aiming. */
   @Nullable public Vec3 aimPoint;
   public int throwTarget = -1;

   // guard
   public boolean guarding;
   public int guardTicks;
   /** Ticks the guard stays broken after a contextual interaction (Reid's stroke); 0 = the shield can be raised. */
   public int shieldBroken;

   // resources and windows
   public int kinetic;
   public int kineticIdle;
   public int counterLeft;
   public int catchLeft;
   public int rushFollow;
   public int rollFollow;
   public int iframes;
   public boolean slamEmpowered;
   public int slamTicks;
   public final int[] cooldowns = new int[CaptainRules.COOLDOWNS];

   // vitals
   public int sinceHurt = 1000;
   public int regenTicks;

   // team memory (Avengers target picking)
   @Nullable public UUID markTarget;
   public int markTicks;
   public int lastAttackerId = -1;
   public int lastAttackerTicks;

   @Nullable public AssembleScene scene;

   public boolean running() {
      return this.move != null;
   }

   public boolean is(CaptainMove m) {
      return this.move == m;
   }

   public boolean shieldInHand() {
      return this.shieldId < 0;
   }

   public void start(CaptainMove m) {
      this.move = m;
      this.elapsed = 0;
      this.hit.clear();
      this.comboQueued = false;
   }

   public void endMove() {
      this.move = null;
      this.elapsed = 0;
      this.hit.clear();
   }

   public void tickTimers() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }
      if (this.counterLeft > 0) this.counterLeft--;
      if (this.catchLeft > 0) this.catchLeft--;
      if (this.rushFollow > 0) this.rushFollow--;
      if (this.rollFollow > 0) this.rollFollow--;
      if (this.iframes > 0) this.iframes--;
      if (this.shieldBroken > 0) this.shieldBroken--;
      if (this.markTicks > 0 && --this.markTicks == 0) this.markTarget = null;
      if (this.lastAttackerTicks > 0 && --this.lastAttackerTicks == 0) this.lastAttackerId = -1;
      if (this.comboIdle < 1000) this.comboIdle++;
      if (this.idle < 1000) this.idle++;
      if (this.sinceHurt < 1000) this.sinceHurt++;
   }

   public void save(CompoundTag nbt) {
      nbt.putIntArray("CaptainCooldowns", this.cooldowns.clone());
   }

   public void load(CompoundTag nbt) {
      int[] saved = nbt.getIntArray("CaptainCooldowns");
      System.arraycopy(saved, 0, this.cooldowns, 0, Math.min(saved.length, this.cooldowns.length));
   }

   /** The state a respawned body starts with: cooldowns only. */
   public CaptainState respawnCopy() {
      CaptainState copy = new CaptainState();
      System.arraycopy(this.cooldowns, 0, copy.cooldowns, 0, this.cooldowns.length);
      return copy;
   }

   @Nullable
   public static CaptainState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.CAPTAIN) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof CaptainState state ? state : null;
      }
      return null;
   }

   public static CaptainState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof CaptainState existing) {
         return existing;
      }
      CaptainState state = new CaptainState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
