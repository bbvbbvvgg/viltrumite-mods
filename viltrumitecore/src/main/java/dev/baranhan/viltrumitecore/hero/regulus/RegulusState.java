package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server state of one Regulus (spec §5). Persistent: cooldowns, strain and the Lion lock (NBT
 * compound {@value #TAG}); everything else is transient and dies with the body, the logout, a
 * dimension change or a hero change. The first kit's {@code RegulusCooldowns} tag is read once as
 * a migration and dropped on the next save.
 */
public final class RegulusState {
   public static final String TAG = "RegulusReborn";
   static final String LEGACY_TAG = "RegulusCooldowns";

   // persistent
   public int lionCooldown;
   public int sandCooldown;
   public int severCooldown;
   public int processionCooldown;
   public int replyCooldown;
   public int ultCooldown;
   public int strain;
   public int lionLock;

   // body
   @Nullable
   public RegulusMove move;
   public int moveTick;
   public int seed;

   // Lion's Heart
   public boolean lion;
   public int lionTicks;
   float lionHealth;
   public int recoilGap;
   public int recoilPulse;

   // Greed's Touch
   public int comboStep = -1;
   public int chainLeft;
   public boolean touchQueued;
   final List<Rupture> ruptures = new ArrayList<>();

   // Stopped Sand / Air Severance
   final List<StoppedGrains.Grain> grains = new ArrayList<>();
   final List<AirSeverance.Blade> blades = new ArrayList<>();
   @Nullable
   Vec3 castLook;
   int castMaterial;

   // Procession
   final Map<UUID, Long> rammed = new HashMap<>();
   int debrisBudget;

   // Right of Reply
   @Nullable
   public UUID replyAttacker;
   public float replyBlocked;
   public boolean replyLanded;

   // Little King
   public final List<UUID> subjects = new ArrayList<>();
   @Nullable
   public UUID host;
   public int hopTimer;
   public int hops;
   @Nullable
   UUID castSubject;

   // Kingdom of One
   @Nullable
   KingdomOfOne.Scene ult;

   record Rupture(UUID target, long at, float damage) {
   }

   @Nullable
   public static RegulusState of(@Nullable Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.REGULUS) {
         Object state = heroPlayer.viltrumitecore$getHeroState();
         return state instanceof RegulusState regulus ? regulus : null;
      }

      return null;
   }

   public static RegulusState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer) player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof RegulusState state) {
         return state;
      }

      RegulusState created = new RegulusState();
      heroPlayer.viltrumitecore$setHeroState(created);
      return created;
   }

   public boolean busy() {
      return this.move != null;
   }

   public void startMove(RegulusMove next) {
      this.move = next;
      this.moveTick = 0;
      this.seed = this.seed * 1103515245 + 12345 & 0x7FFF;
   }

   public void clearMove() {
      this.move = null;
      this.moveTick = 0;
   }

   public boolean seized() {
      return this.move == RegulusMove.SEIZURE;
   }

   public int cooldown(int index) {
      return switch (index) {
         case RegulusFlags.COOLDOWN_LION -> this.lionCooldown;
         case RegulusFlags.COOLDOWN_SAND -> this.sandCooldown;
         case RegulusFlags.COOLDOWN_SEVER -> this.severCooldown;
         case RegulusFlags.COOLDOWN_PROCESSION -> this.processionCooldown;
         case RegulusFlags.COOLDOWN_REPLY -> this.replyCooldown;
         case RegulusFlags.COOLDOWN_ULT -> this.ultCooldown;
         default -> 0;
      };
   }

   void tickCooldowns() {
      if (this.lionCooldown > 0) {
         this.lionCooldown--;
      }

      if (this.sandCooldown > 0) {
         this.sandCooldown--;
      }

      if (this.severCooldown > 0) {
         this.severCooldown--;
      }

      if (this.processionCooldown > 0) {
         this.processionCooldown--;
      }

      if (this.replyCooldown > 0) {
         this.replyCooldown--;
      }

      if (this.ultCooldown > 0 && this.ult == null) {
         this.ultCooldown--;
      }

      if (this.lionLock > 0) {
         this.lionLock--;
      }

      if (this.recoilGap > 0) {
         this.recoilGap--;
      }

      if (this.chainLeft > 0) {
         this.chainLeft--;
      }
   }

   /** Drops every transient effect (cooldowns, strain and the Lion lock stay). */
   public void resetTransient() {
      this.move = null;
      this.moveTick = 0;
      this.lion = false;
      this.lionTicks = 0;
      this.comboStep = -1;
      this.chainLeft = 0;
      this.touchQueued = false;
      this.ruptures.clear();
      this.grains.clear();
      this.blades.clear();
      this.castLook = null;
      this.rammed.clear();
      this.replyAttacker = null;
      this.replyBlocked = 0.0F;
      this.replyLanded = false;
      this.subjects.clear();
      this.host = null;
      this.hopTimer = 0;
      this.castSubject = null;
      this.ult = null;
   }

   public void save(CompoundTag nbt) {
      CompoundTag tag = new CompoundTag();
      tag.putInt("Lion", this.lionCooldown);
      tag.putInt("Sand", this.sandCooldown);
      tag.putInt("Sever", this.severCooldown);
      tag.putInt("Procession", this.processionCooldown);
      tag.putInt("Reply", this.replyCooldown);
      tag.putInt("Ult", this.ultCooldown);
      tag.putInt("Strain", this.strain);
      tag.putInt("LionLock", this.lionLock);
      nbt.put(TAG, tag);
      nbt.remove(LEGACY_TAG);
   }

   public void load(CompoundTag nbt) {
      if (nbt.contains(TAG)) {
         CompoundTag tag = nbt.getCompound(TAG);
         this.lionCooldown = clampTicks(tag.getInt("Lion"));
         this.sandCooldown = clampTicks(tag.getInt("Sand"));
         this.severCooldown = clampTicks(tag.getInt("Sever"));
         this.processionCooldown = clampTicks(tag.getInt("Procession"));
         this.replyCooldown = clampTicks(tag.getInt("Reply"));
         this.ultCooldown = clampTicks(tag.getInt("Ult"));
         this.strain = Math.max(0, Math.min(RegulusRules.STRAIN_MAX, tag.getInt("Strain")));
         this.lionLock = clampTicks(tag.getInt("LionLock"));
      } else if (nbt.contains(LEGACY_TAG)) {
         migrateLegacy(nbt.getCompound(LEGACY_TAG));
      }
   }

   /** First kit → Reborn: the old cooldowns carry over to their successor slots (pure on the tag). */
   void migrateLegacy(CompoundTag old) {
      this.lionCooldown = clampTicks(old.getInt("regulus:lions_heart"));
      this.sandCooldown = clampTicks(old.getInt("regulus:debris_kick"));
      this.severCooldown = clampTicks(old.getInt("regulus:mania"));
      this.processionCooldown = clampTicks(old.getInt("regulus:greeds_embrace"));
      this.replyCooldown = clampTicks(old.getInt("regulus:counter"));
      this.ultCooldown = clampTicks(old.getInt("regulus:evangelium"));
   }

   private static int clampTicks(int ticks) {
      return Math.max(0, Math.min(20 * 60 * 10, ticks));
   }

   /** What a new body inherits: cooldowns, strain, the lock. */
   public RegulusState respawnCopy() {
      RegulusState copy = new RegulusState();
      copy.lionCooldown = this.lionCooldown;
      copy.sandCooldown = this.sandCooldown;
      copy.severCooldown = this.severCooldown;
      copy.processionCooldown = this.processionCooldown;
      copy.replyCooldown = this.replyCooldown;
      copy.ultCooldown = this.ultCooldown;
      copy.strain = 0;
      copy.lionLock = 0;
      return copy;
   }
}
