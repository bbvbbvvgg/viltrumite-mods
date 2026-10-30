package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Server-only transient Regulus state. Persistent data (hero id, session id,
 * totem) lives on HeroPlayer/HeroData instead; the cooldown map rides along
 * through saveHeroData/loadHeroData; the rest of this object is rebuilt empty
 * on login and is discarded on death/disconnect/hero change.
 */
public final class RegulusState {
   /** HeroData key carrying the cooldown map across relog and respawn. */
   public static final String COOLDOWNS_TAG = "RegulusCooldowns";

   // Timed action lock: the current cast. Event rules live in the abilities;
   // until eventFired cancellation is free and starts no cooldown.
   @Nullable
   public String actionId;
   public int actionElapsed;
   public int actionLength;
   public int actionEventTick;
   public int actionUnlockTick;
   public boolean eventFired;
   // Per-action context (dome point, counter target...) written by abilities.
   public Vec3 actionPoint;
   public UUID actionTargetId;

   public final Map<String, Integer> cooldowns = new HashMap<>();

   // Hearts (carriers are owner-private; only the count is public).
   public final Set<UUID> carriers = new LinkedHashSet<>();
   // The dimension each carrier was bound in: the heart lives there, so death
   // checks resolve the carrier in its own level even when the owner is
   // elsewhere (spec 5.3 — a death always burns, an unload drops silently).
   public final Map<UUID, ResourceLocation> carrierLevels = new LinkedHashMap<>();
   // Carriers the owner was last notified about; snapshot pushes dedupe on it.
   public final Set<UUID> pushedCarriers = new LinkedHashSet<>();
   public long nextCarrierScan;

   // Lion's Heart.
   public boolean lionActive;
   public int lionWindowMax;
   public int lionElapsed;
   public int overheatTicks;
   public long lionStartTick;
   public int lionWindowFloorHearts = -1;

   // Attacker record for Counter: last real living damage source.
   public UUID attackerId;
   public long attackerTick = Long.MIN_VALUE;
   public Vec3 attackerLastPos;
   // Counter sequence: the thrown target, its launch start/apex and the dive.
   public Vec3 counterLiftStart;
   public double counterApexY;
   public UUID counterDiveTargetId;
   public int counterDiveTicks = -1;

   // Evangelium / madness.
   public int ritualTicks = -1;
   public int madnessTicksLeft;
   /** Last Evangelium cooldown mirrored onto the book item (vanilla cooldown overlay); transient. */
   public int bookCooldownShown;

   // Mania channel.
   public UUID channelTargetId;
   public UUID channelEffectId;
   public int channelTicks;

   // Super jump / landing.
   public boolean wasOnGround = true;
   public float lastFallDistance;

   // Damage bookkeeping for interruption rules.
   public float lastSeenHealth = -1.0F;
   // Hero-internal damage applied since the last bookkeeping read (blood price,
   // heart backlash, overheat). The read subtracts it from the observed loss so
   // self-inflicted drains never count as an interrupting hit (spec 7.1/10/11).
   public float internalDamage;
   // A non-ambient (externally applied) Slowness stashed before an ability's
   // own slow overwrites it, restored when that slow ends (a3 audit finding).
   @Nullable
   public MobEffectInstance savedSlowness;

   /**
    * Action lock: busy while a cast runs before its event; once the event has
    * fired and the unlock tick passed the remaining animation is free — the
    * player may start another action while the tail plays out (spec 13.2).
    */
   public boolean busy() {
      return this.actionId != null && !(this.eventFired && this.actionElapsed >= this.actionUnlockTick);
   }

   public int hearts() {
      return this.carriers.size();
   }

   public int cooldownOf(String abilityId) {
      return this.cooldowns.getOrDefault(abilityId, 0);
   }

   public void startCooldown(String abilityId, int baseTicks) {
      this.cooldowns.put(abilityId, RegulusRules.cooldown(baseTicks, this.hearts()));
   }

   public void tickCooldowns() {
      this.cooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
   }

   /** Cooldowns into HeroData; only unfinished, known regulus ids load back. */
   public void saveHeroData(CompoundTag nbt) {
      CompoundTag cooldowns = new CompoundTag();
      for (Map.Entry<String, Integer> entry : this.cooldowns.entrySet()) {
         if (entry.getValue() != null && entry.getValue() > 0) {
            cooldowns.putInt(entry.getKey(), entry.getValue());
         }
      }

      if (!cooldowns.isEmpty()) {
         nbt.put(COOLDOWNS_TAG, cooldowns);
      }
   }

   public void loadHeroData(CompoundTag nbt) {
      if (!nbt.contains(COOLDOWNS_TAG)) {
         return;
      }

      CompoundTag cooldowns = nbt.getCompound(COOLDOWNS_TAG);
      for (String key : cooldowns.getAllKeys()) {
         int ticks = cooldowns.getInt(key);
         if (ticks > 0 && RegulusAbilities.isRegulusAbility(key)) {
            this.cooldowns.put(key, ticks);
         }
      }
   }

   /** Respawn clone: duration bookkeeping carries over; combat state does not. */
   public void inheritPersistent(RegulusState previous) {
      this.cooldowns.putAll(previous.cooldowns);
   }

   public void clearAction() {
      this.actionId = null;
      this.actionElapsed = 0;
      this.actionLength = 0;
      this.actionEventTick = 0;
      this.actionUnlockTick = 0;
      this.eventFired = false;
      this.actionPoint = null;
      this.actionTargetId = null;
   }

   public void beginAction(String actionId, int length, int eventTick, int unlockTick) {
      this.actionId = actionId;
      this.actionElapsed = 0;
      this.actionLength = length;
      this.actionEventTick = eventTick;
      this.actionUnlockTick = unlockTick;
      this.eventFired = false;
      this.actionPoint = null;
      this.actionTargetId = null;
   }

   /**
    * Pure half of lifecycle cleanup: every transient combat field returns to
    * its fresh-state default. Ability cleanups handle the Minecraft side
    * (modifiers, control ownership, channel ends) before this runs; only a
    * hero change or erasure clears the cast lock — on death/disconnect this object is
    * discarded with the entity anyway. Cooldowns are duration bookkeeping:
    * untouched here, persisted through saveHeroData into HeroData (relogin)
    * and inheritPersistent (respawn clone).
    */
   public void resetTransient(CleanupReason reason) {
      if (reason == CleanupReason.HERO_CHANGE || reason == CleanupReason.ERASED) {
         this.clearAction();
      }

      this.carriers.clear();
      this.carrierLevels.clear();
      this.pushedCarriers.clear();
      this.nextCarrierScan = 0L;

      this.lionActive = false;
      this.lionWindowMax = 0;
      this.lionElapsed = 0;
      this.overheatTicks = 0;
      this.lionStartTick = 0L;
      this.lionWindowFloorHearts = -1;

      this.attackerId = null;
      this.attackerTick = Long.MIN_VALUE;
      this.attackerLastPos = null;
      this.counterLiftStart = null;
      this.counterApexY = 0.0;
      this.counterDiveTargetId = null;
      this.counterDiveTicks = -1;

      this.ritualTicks = -1;
      this.madnessTicksLeft = 0;

      this.channelTargetId = null;
      this.channelEffectId = null;
      this.channelTicks = 0;

      this.wasOnGround = true;
      this.lastFallDistance = 0.0F;

      this.lastSeenHealth = -1.0F;
      this.internalDamage = 0.0F;
      this.savedSlowness = null;
   }
   /** Same kept fields as a death clone; no stale action/channel state on the same body. */
   public RegulusState erasedCopy() {
      RegulusState fresh = new RegulusState();
      fresh.inheritPersistent(this);
      return fresh;
   }

}
