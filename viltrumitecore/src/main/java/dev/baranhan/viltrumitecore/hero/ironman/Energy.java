package dev.baranhan.viltrumitecore.hero.ironman;

import net.minecraft.nbt.CompoundTag;

/**
 * Reactor energy, 0-100, shared by every suit (spec §5). Server-side.
 * Regenerates after a pause since the last spend or drain. Hitting 0 locks
 * weapons and shield until the value is back at {@link IronManRules#WEAPONS_UNLOCK}.
 */
public final class Energy {
   private static final String KEY = "Energy";
   private float value = IronManRules.ENERGY_MAX;
   private int idleTicks = IronManRules.ENERGY_REGEN_DELAY;
   private boolean locked;

   public float value() {
      return this.value;
   }

   /** One-shot cost: all or nothing. */
   public boolean spend(float amount) {
      if (amount > this.value) {
         return false;
      }

      this.set(this.value - amount);
      this.idleTicks = 0;
      return true;
   }

   /** Continuous cost (flight, channels): clamps at 0. */
   public void drain(float amount) {
      if (amount <= 0.0F) {
         return;
      }

      this.set(this.value - amount);
      this.idleTicks = 0;
   }

   public void tick() {
      this.tick(1.0F);
   }

   /** @param regenScale foreign slow-down of the refill only (HeroResourceRegen), 0..1 */
   public void tick(float regenScale) {
      if (this.idleTicks < IronManRules.ENERGY_REGEN_DELAY) {
         this.idleTicks++;
         return;
      }

      if (this.value < IronManRules.ENERGY_MAX) {
         this.set(this.value + IronManRules.ENERGY_REGEN_PER_TICK * regenScale);
      }
   }

   public boolean empty() {
      return this.value <= 0.0F;
   }

   public boolean weaponsLocked() {
      return this.locked;
   }

   /** Full tank, no lock (death clone, cleanup). */
   public void reset() {
      this.value = IronManRules.ENERGY_MAX;
      this.idleTicks = IronManRules.ENERGY_REGEN_DELAY;
      this.locked = false;
   }

   private void set(float next) {
      this.value = Math.max(0.0F, Math.min(IronManRules.ENERGY_MAX, next));
      if (this.value <= 0.0F) {
         this.locked = true;
      } else if (this.locked && this.value >= IronManRules.WEAPONS_UNLOCK) {
         this.locked = false;
      }
   }

   public void save(CompoundTag tag) {
      CompoundTag energy = new CompoundTag();
      energy.putFloat("Value", this.value);
      energy.putBoolean("Locked", this.locked);
      tag.put(KEY, energy);
   }

   public void load(CompoundTag tag) {
      if (!tag.contains(KEY, 10)) {
         this.reset();
         return;
      }

      CompoundTag energy = tag.getCompound(KEY);
      this.value = Math.max(0.0F, Math.min(IronManRules.ENERGY_MAX, energy.getFloat("Value")));
      this.locked = energy.getBoolean("Locked") && this.value < IronManRules.WEAPONS_UNLOCK;
      this.idleTicks = IronManRules.ENERGY_REGEN_DELAY;
   }
}
