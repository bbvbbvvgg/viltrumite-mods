package dev.baranhan.viltrumitecore.hero.venom;

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
 * Per-player server state of Venom. Saved (NBT): the form, the hunger and the cooldowns, nothing else. Rage, the
 * shell, the tendrils, a held body and above all a host takeover live in memory only (spec §8): a save, a crash, a
 * logout or a time rewind can never bring a takeover back or leave a body half-controlled.
 */
public final class VenomState {
   @Nullable public VenomMove move;
   public int elapsed;
   /** Bumped on every move start: the client clock restarts the same clip on it (snapshot variant). */
   public int serial;
   public int comboStep = -1;
   public int comboIdle = 1000;
   public int lashStep = -1;
   public int lashIdle = 1000;
   public final Set<Integer> hit = new HashSet<>();
   public boolean primaryQueued;
   public int tickCount;
   public int idle = 1000;
   public final int[] cooldowns = new int[VenomRules.COOLDOWNS];

   /** The symbiote covers the host. */
   public boolean form;
   /** 0..1000, fed by fighting and webs, burnt by rage and the shell. */
   public int hunger;

   // rage, shell, weakness
   public int rageTicks;
   public boolean rage;
   public boolean shell;
   public int sinceHurt = 1000;
   public int fireBlock;
   public int recoilImmune;

   // tendrils
   public int tendrilTicks;
   @Nullable public Vec3 tendrilEnd;
   @Nullable public Vec3 zipTarget;
   public int pullId = -1;
   @Nullable public UUID pullEffect;
   /** The block face a zip goes to, the tendril's root side (+1 right, -1 left). */
   public int tendrilSide = 1;
   /** Spider whose web line the tendril wraps (entity id), and the fraction where it holds. */
   public int webSpiderId = -1;

   // a body on the tendrils (RMB)
   public int heldId = -1;
   @Nullable public UUID heldUuid;
   @Nullable public UUID heldEffect;
   public int holdTicks;

   // walls and clashes
   public int wallTicks;
   public int clashId = -1;
   public boolean spiderNear;
   public int cocoonTicks;

   // host takeover: the victim's entity id while this body is the husk (memory only, VenomPossession owns the truth)
   public int hostId = -1;

   public boolean running() {
      return this.move != null;
   }

   public boolean is(VenomMove m) {
      return this.move == m;
   }

   public boolean holding() {
      return this.heldId >= 0;
   }

   public boolean possessing() {
      return this.hostId >= 0;
   }

   public void start(VenomMove m) {
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
      if (this.lashIdle < 1000) this.lashIdle++;
      if (this.idle < 1000) this.idle++;
      if (this.sinceHurt < 1000) this.sinceHurt++;
      if (this.fireBlock > 0) this.fireBlock--;
      if (this.recoilImmune > 0) this.recoilImmune--;
   }

   public void save(CompoundTag nbt) {
      nbt.putIntArray("VenomCooldowns", this.cooldowns.clone());
      nbt.putBoolean("VenomForm", this.form);
      nbt.putInt("VenomHunger", this.hunger);
   }

   public void load(CompoundTag nbt) {
      int[] saved = nbt.getIntArray("VenomCooldowns");
      System.arraycopy(saved, 0, this.cooldowns, 0, Math.min(saved.length, this.cooldowns.length));
      this.form = nbt.getBoolean("VenomForm");
      this.hunger = Math.max(0, Math.min(VenomRules.HUNGER_MAX, nbt.getInt("VenomHunger")));
      // a loaded state never carries a move, a hold, rage, the shell or a takeover
      this.move = null;
      this.rage = false;
      this.shell = false;
      this.heldId = -1;
      this.hostId = -1;
   }

   /** A respawned body: the form and the cooldowns; the hunger halves; nothing running. */
   public VenomState respawnCopy() {
      VenomState copy = new VenomState();
      System.arraycopy(this.cooldowns, 0, copy.cooldowns, 0, this.cooldowns.length);
      copy.form = this.form;
      copy.hunger = this.hunger / 2;
      return copy;
   }

   @Nullable
   public static VenomState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.VENOM) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof VenomState state ? state : null;
      }
      return null;
   }

   public static VenomState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof VenomState existing) {
         return existing;
      }
      VenomState state = new VenomState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
