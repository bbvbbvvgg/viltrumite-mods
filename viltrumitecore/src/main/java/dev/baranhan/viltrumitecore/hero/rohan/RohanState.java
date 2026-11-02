package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Server state of Rohan Kishibe (one per player while he is the hero). Saved: ink, inspiration, cooldowns, read books. */
public final class RohanState {
   public float ink = RohanRules.INK_MAX;
   public float inspiration;
   public final int[] cooldowns = new int[RohanRules.COOLDOWN_COUNT];
   @Nullable
   public RohanMove move;
   public int elapsed;
   /** Bumped at every new move (the client restarts clips on change). */
   public int serial;
   public int tickCount;
   /** Ticks since he last dealt or took damage. */
   public int idle = RohanRules.CALM_TICKS;
   public boolean standOut;
   /** Body the stand is touching / the open book's owner (entity id, -1 = none). Not saved. */
   public int targetId = -1;
   public int sketchTicks;
   /** Where the Air Sketch hangs. Not saved. */
   @Nullable
   public Vec3 sketchAt;
   public int manuscriptTicks;
   public int counterTicks;
   public boolean countered;
   public int flyTicks;
   @Nullable
   public Vec3 flyVector;
   /** Nibs still to throw in the current volley. */
   public int nibsLeft;
   /** Ink marks: entity id -> tick count when the mark fades. Not saved. */
   public final Map<Integer, Integer> marks = new HashMap<>();
   /** Kinds of books already read (entity type ids, "hero:<key>"): the first reading inspires. */
   public final Set<String> read = new HashSet<>();

   public void start(RohanMove next) {
      this.move = next;
      this.elapsed = 0;
      this.serial++;
   }

   public boolean is(RohanMove m) {
      return this.move == m;
   }

   public void tickTimers() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }
      if (this.move != null && ++this.elapsed >= this.move.length) {
         this.move = null;
         this.elapsed = 0;
      }
      if (this.sketchTicks > 0) {
         this.sketchTicks--;
      }
      if (this.manuscriptTicks > 0) {
         this.manuscriptTicks--;
      }
      if (this.counterTicks > 0) {
         this.counterTicks--;
      }
      if (this.idle < 100000) {
         this.idle++;
      }
      Iterator<Map.Entry<Integer, Integer>> it = this.marks.entrySet().iterator();
      while (it.hasNext()) {
         if (it.next().getValue() <= this.tickCount) {
            it.remove();
         }
      }
   }

   public boolean marked(int entityId) {
      Integer until = this.marks.get(entityId);
      return until != null && until > this.tickCount;
   }

   public void regen() {
      this.ink = Math.min(RohanRules.INK_MAX, this.ink + RohanRules.inkPerTick(this.idle));
   }

   public void save(CompoundTag nbt) {
      nbt.putFloat("RohanInk", this.ink);
      nbt.putFloat("RohanInspiration", this.inspiration);
      nbt.putIntArray("RohanCooldowns", this.cooldowns.clone());
      nbt.putBoolean("RohanStandOut", this.standOut);
      ListTag list = new ListTag();
      for (String s : this.read) {
         list.add(StringTag.valueOf(s));
      }
      nbt.put("RohanRead", list);
   }

   public void load(CompoundTag nbt) {
      if (nbt.contains("RohanInk")) {
         this.ink = clamp(nbt.getFloat("RohanInk"), RohanRules.INK_MAX);
      }
      this.inspiration = clamp(nbt.getFloat("RohanInspiration"), RohanRules.INSPIRATION_MAX);
      int[] saved = nbt.getIntArray("RohanCooldowns");
      for (int i = 0; i < Math.min(saved.length, this.cooldowns.length); i++) {
         this.cooldowns[i] = Math.max(0, Math.min(saved[i], RohanRules.COOLDOWN_TICKS[i]));
      }
      this.standOut = nbt.getBoolean("RohanStandOut");
      this.read.clear();
      ListTag list = nbt.getList("RohanRead", Tag.TAG_STRING);
      for (int i = 0; i < list.size() && i < 4096; i++) {
         this.read.add(list.getString(i));
      }
   }

   private static float clamp(float v, float max) {
      return Float.isNaN(v) ? 0.0F : Math.max(0.0F, Math.min(max, v));
   }

   /** What survives death: cooldowns, the read books and the inspiration; ink refills, the stand returns. */
   public RohanState respawnCopy() {
      RohanState copy = new RohanState();
      System.arraycopy(this.cooldowns, 0, copy.cooldowns, 0, this.cooldowns.length);
      copy.inspiration = this.inspiration;
      copy.read.addAll(this.read);
      return copy;
   }

   @Nullable
   public static RohanState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.ROHAN) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof RohanState state ? state : null;
      }
      return null;
   }

   public static RohanState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof RohanState existing) {
         return existing;
      }
      RohanState state = new RohanState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
