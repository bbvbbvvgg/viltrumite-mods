package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side Jinwoo state (spec §8): mana, cooldowns and the roster are saved
 * under NBT key {@code Jinwoo}; everything else (timeline, daggers, orders,
 * holds, the ultimate) is transient and ends with cleanup.
 */
public final class JinwooState {
   static final String KEY = "Jinwoo";

   public float mana = JinwooRules.MANA_MAX;
   public int[] cooldowns = new int[JinwooAbility.COOLDOWNS];
   public final ShadowRoster roster = new ShadowRoster();

   // --- daggers (spec §5.3) ---
   public boolean daggers;
   public int drawLeft;
   public int sheathLeft;
   /** Ticks since the last combat action (dagger auto-dissolve after DAGGER_IDLE). */
   public int idle;

   // --- the one action timeline ---
   @Nullable
   public JinwooMove move;
   public int elapsed;
   public int targetId = -1;
   public Vec3 moveDir = Vec3.ZERO;
   @Nullable
   public Vec3 moveOrigin;
   public final Set<UUID> hit = new HashSet<>();

   // --- combo (spec §5.1) ---
   public int comboStep = -1;
   public int comboIdle = 99;
   public boolean comboQueued;
   public int floatTicks;
   public int airHits;

   // --- block (spec §5.2) ---
   public boolean blocking;
   public int blockTicks;

   // --- passives ---
   public int sinceHurt = 999;
   public int regenTicks;
   public int iframes;
   public int tickCount;

   // --- Monarch's mark (spec §5.1 synergy) ---
   @Nullable
   public UUID markTarget;
   public int markTicks;

   // --- Ruler's Authority (spec §5.6) ---
   public final List<Integer> held = new ArrayList<>();
   public int holdTicks;
   public boolean holdReleased;

   // --- Extraction (spec §5.7) ---
   public int extractRemnant = -1;

   // --- army orders (spec §5.11, §5.12) ---
   public ArmyOrder order = ArmyOrder.FOLLOW;
   public int orderTargetId = -1;
   @Nullable
   public Vec3 orderPoint;
   /** Shadow uuid → ward uuid. */
   public final Map<UUID, UUID> sentinels = new LinkedHashMap<>();
   /** Who last hurt Jinwoo (shadows answer, spec §6.2). */
   public int lastAttackerId = -1;
   public int lastAttackerTicks;
   /** Pending staggered summons (roster serials) and their delay. */
   public final List<Integer> summonQueue = new ArrayList<>();
   public int summonDelay;

   // --- ultimate and Domain (spec §5.9) ---
   @Nullable
   public Vec3 domainCenter;
   public int domainLeft;
   public boolean ultSpoken;
   /** Cached every 5 t: a free remnant within the extraction radius (HUD/slot hint). */
   public boolean remnantNear;

   public enum ArmyOrder {
      FOLLOW, ATTACK, GUARD
   }

   public boolean running() {
      return this.move != null;
   }

   public boolean is(JinwooMove m) {
      return this.move == m;
   }

   public void start(JinwooMove m, int target) {
      this.move = m;
      this.elapsed = 0;
      this.targetId = target;
      this.hit.clear();
   }

   public void endMove() {
      this.move = null;
      this.elapsed = 0;
      this.targetId = -1;
      this.hit.clear();
   }

   public boolean inDomain(Vec3 at) {
      return this.domainCenter != null && this.domainLeft > 0 && at.distanceToSqr(this.domainCenter) <= JinwooRules.ULT_RADIUS * JinwooRules.ULT_RADIUS;
   }

   public int cooldown(JinwooAbility ability) {
      return this.cooldowns[ability.ordinal()];
   }

   public void cool(JinwooAbility ability) {
      this.cooldowns[ability.ordinal()] = ability.cooldown;
   }

   public boolean spend(float amount) {
      if (this.mana + 1.0E-4F < amount) {
         return false;
      }
      this.mana = Math.max(0.0F, this.mana - amount);
      return true;
   }

   public void tickCooldowns() {
      for (int i = 0; i < this.cooldowns.length; i++) {
         if (this.cooldowns[i] > 0) {
            this.cooldowns[i]--;
         }
      }
   }

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      tag.putFloat("Mana", this.mana);
      tag.putIntArray("Cooldowns", this.cooldowns.clone());
      tag.put(ShadowRoster.NBT, this.roster.save());
      nbt.put(KEY, tag);
   }

   public void load(CompoundTag nbt) {
      if (!nbt.contains(KEY, 10)) {
         return;
      }
      CompoundTag tag = nbt.getCompound(KEY);
      this.mana = Math.max(0.0F, Math.min(JinwooRules.MANA_MAX, tag.getFloat("Mana")));
      int[] saved = tag.getIntArray("Cooldowns");
      this.cooldowns = new int[JinwooAbility.COOLDOWNS];
      JinwooAbility[] abilities = JinwooAbility.values();
      for (int i = 0; i < Math.min(saved.length, this.cooldowns.length); i++) {
         int limit = i < abilities.length ? abilities[i].cooldown : JinwooRules.BLOCK_COOLDOWN;
         this.cooldowns[i] = Math.max(0, Math.min(limit, saved[i]));
      }
      this.roster.load(tag.getCompound(ShadowRoster.NBT));
   }

   /** The respawned body keeps roster, cooldowns and mana (spec §8: the roster survives death). */
   public JinwooState respawnCopy() {
      JinwooState next = new JinwooState();
      CompoundTag tag = new CompoundTag();
      this.save(tag);
      next.load(tag);
      next.roster.clearActive();
      next.mana = JinwooRules.MANA_MAX;
      return next;
   }

   /** The roster outlives a hero change (spec §2): kept in the persisted Forge player data. */
   static final String STASH = "JinwooRoster";

   public void stash(Player player) {
      CompoundTag data = player.getPersistentData();
      CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
      persisted.put(STASH, this.roster.save());
      data.put(Player.PERSISTED_NBT_TAG, persisted);
   }

   public void unstash(Player player) {
      CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
      if (this.roster.size() == 0 && persisted.contains(STASH, 10)) {
         this.roster.load(persisted.getCompound(STASH));
      }
   }

   @Nullable
   public static JinwooState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.JINWOO) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof JinwooState state ? state : null;
      }
      return null;
   }

   public static JinwooState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof JinwooState existing) {
         return existing;
      }
      JinwooState state = new JinwooState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
