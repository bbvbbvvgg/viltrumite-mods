package dev.baranhan.viltrumitecore.hero.ghostrider;

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
 * Per-player server state of the Ghost Rider. The cooldowns and the form persist (NBT); a respawned body keeps the
 * cooldowns and the form (spec §7): the Rider who died comes back as the Rider, Johnny as Johnny.
 */
public final class GhostRiderState {
   @Nullable public GhostRiderMove move;
   public int elapsed;
   /** Bumped on every move start: the client clock restarts the same clip on it (snapshot variant). */
   public int serial;
   public int comboStep = -1;
   public int comboIdle = 1000;
   public final Set<Integer> hit = new HashSet<>();
   /** The body a PUNCH was thrown at (entity id, -1 = none); re-checked at the contact. Not saved. */
   public int punchTarget = -1;
   /** An LMB clicked before the running lash or punch may be cut: it fires once that strike may be cut (one slot,
    * dies with the move). Not saved. */
   public boolean primaryQueued;
   public Vec3 moveDir = Vec3.ZERO;
   public int tickCount;
   public int idle = 1000;
   public final int[] cooldowns = new int[GhostRiderRules.COOLDOWNS];

   /** The Spirit of Vengeance is out. */
   public boolean rider;
   /** Call the bike as soon as the transformation ends (R pressed as Johnny). */
   public boolean summonAfterTransform;

   // the Hell Cycle
   public int bikeId = -1;
   @Nullable public UUID bikeUuid;

   // the chain on a body (hook, yank, hold, whirl swing, tow)
   public int chainId = -1;
   @Nullable public UUID chainUuid;
   @Nullable public UUID chainEffect;
   public int chainTicks;
   public boolean towing;
   public boolean pulling;

   // Penance Stare
   public int stareId = -1;
   @Nullable public UUID stareUuid;
   @Nullable public UUID stareEffect;
   public int lastSins = -1;
   public boolean lastInnocent;
   public int stareLostTicks;

   // Hellfire Wave
   public Vec3 waveOrigin = Vec3.ZERO;
   public Vec3 waveAxis = Vec3.ZERO;

   public boolean running() {
      return this.move != null;
   }

   public boolean is(GhostRiderMove m) {
      return this.move == m;
   }

   public boolean chained() {
      return this.chainId >= 0;
   }

   public boolean staring() {
      return this.stareId >= 0 && this.move == GhostRiderMove.STARE;
   }

   public void start(GhostRiderMove m) {
      this.move = m;
      this.elapsed = 0;
      this.serial = (this.serial + 1) & 0xFFFF;
      this.hit.clear();
      this.primaryQueued = false;
   }

   public void endMove() {
      this.move = null;
      this.elapsed = 0;
      this.hit.clear();
      this.primaryQueued = false;
   }

   /** True once: a queued LMB may fire now (its strike still runs and may be cut). */
   public boolean takeQueuedPrimary() {
      if (!this.primaryQueued || this.move == null || !this.move.cancellable(this.elapsed)) {
         return false;
      }
      this.primaryQueued = false;
      return true;
   }

   public void tickTimers() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }
      if (this.comboIdle < 1000) this.comboIdle++;
      if (this.idle < 1000) this.idle++;
   }

   public void save(CompoundTag nbt) {
      nbt.putIntArray("GhostRiderCooldowns", this.cooldowns.clone());
      nbt.putBoolean("GhostRiderForm", this.rider);
   }

   public void load(CompoundTag nbt) {
      int[] saved = nbt.getIntArray("GhostRiderCooldowns");
      System.arraycopy(saved, 0, this.cooldowns, 0, Math.min(saved.length, this.cooldowns.length));
      this.rider = nbt.getBoolean("GhostRiderForm");
   }

   /** A respawned body: the cooldowns and the form; no move, no bike, nothing queued. {@code GhostRiderHero.onRespawn}
    * then dresses a Rider in the chassis at full health. */
   public GhostRiderState respawnCopy() {
      GhostRiderState copy = new GhostRiderState();
      System.arraycopy(this.cooldowns, 0, copy.cooldowns, 0, this.cooldowns.length);
      copy.rider = this.rider;
      return copy;
   }

   @Nullable
   public static GhostRiderState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.GHOST_RIDER) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof GhostRiderState state ? state : null;
      }
      return null;
   }

   public static GhostRiderState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof GhostRiderState existing) {
         return existing;
      }
      GhostRiderState state = new GhostRiderState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
