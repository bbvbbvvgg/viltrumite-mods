package dev.baranhan.viltrumitecore.hero.control;

import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusRules;
import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * World-owned control effects (PULL, FREEZE, STASIS, domes, frozen
 * projectiles). Targets are soft: release fully restores the victim's saved
 * position/flags and then applies the queued damage once, in release order.
 * The legacy viltrumite grab is a separate tag-based system; this manager only
 * consults it for precedence.
 */
public final class ControlManager {
   private static final Map<ServerLevel, ControlManager> INSTANCES = new WeakHashMap<>();

   /**
    * Projectiles whose freeze record was dropped while they were unreachable
    * (unloaded chunk or another level). The saved noGravity value is applied
    * the next time the projectile joins any level, so it can never hang
    * mid-air with noGravity baked into NBT. Bounded: entries for entities
    * that never rejoin evict the eldest first.
    */
   private static final int PENDING_RESTORE_CAP = 512;
   private static final Map<UUID, Boolean> PENDING_GRAVITY_RESTORE = new BoundedMap<>(PENDING_RESTORE_CAP);

   /**
    * Living targets (FREEZE/STASIS) whose record dropped while they were
    * unreachable — disconnect or a dimension change. Their entity NBT already
    * carries the frozen flags; the saved values come back the next time the
    * entity joins any level, so it cannot stay frozen forever.
    */
   private static final Map<UUID, SavedFlags> PENDING_TARGET_RESTORE = new BoundedMap<>(PENDING_RESTORE_CAP);

   /** Saved victim flags deferred for a target that left its level unreachable. */
   private record SavedFlags(boolean noGravity, boolean noAi) {
   }

   private final Map<UUID, ControlRecord> controls = new HashMap<>();
   private final Map<UUID, DomeRecord> domes = new HashMap<>();
   private final Map<UUID, ProjectileFreezeRecord> frozenProjectiles = new HashMap<>();
   // Structural mutations set this; the level tick flushes one full snapshot.
   private boolean dirty;

   private ControlManager() {
   }

   public static ControlManager get(ServerLevel level) {
      return INSTANCES.computeIfAbsent(level, key -> new ControlManager());
   }

   // --- target queries -----------------------------------------------------

   /** Target anchored in place (FREEZE/STASIS) or teleported by a dome. */
   public boolean isAnchored(@Nullable LivingEntity target) {
      if (target == null) {
         return false;
      }

      ControlRecord record = this.controls.get(target.getUUID());
      return record != null && deniesActions(record.kind);
   }

   /**
    * Anchoring kinds strip the victim's own attacks, item use, flight and
    * abilities outright (spec 8.3/9.2); PULL leaves them and the policy-only
    * kinds never anchor.
    */
   public static boolean deniesActions(ControlKind kind) {
      return kind == ControlKind.FREEZE || kind == ControlKind.STASIS;
   }

   /** Any world-owned control, including the non-anchoring PULL. */
   public boolean isControlled(@Nullable LivingEntity target) {
      return target != null && this.controls.containsKey(target.getUUID());
   }

   /** Mod flight must be denied (anchored controls and domes). */
   public boolean preventsFlight(@Nullable LivingEntity target) {
      if (target == null) {
         return false;
      }

      if (this.isAnchored(target) || WorldPause.isPaused(target) || dev.baranhan.viltrumitecore.hero.BodyIsolation.active(target)) {
         return true;
      }

      if (target instanceof ServerPlayer player) {
         for (DomeRecord dome : this.domes.values()) {
            if (dome.captured().contains(player.getUUID())) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   public ControlKind controlKind(@Nullable LivingEntity target) {
      ControlRecord record = target == null ? null : this.controls.get(target.getUUID());
      return record == null ? null : record.kind;
   }

   @Nullable
   public UUID effectIdOf(@Nullable LivingEntity target) {
      ControlRecord record = target == null ? null : this.controls.get(target.getUUID());
      return record == null ? null : record.effectId;
   }

   /** Every control currently cast by this player (for "one Mania at a time" checks). */
   public boolean hasControlFrom(UUID caster, ControlKind kind) {
      for (ControlRecord record : this.controls.values()) {
         if (record.caster.equals(caster) && record.kind == kind) {
            return true;
         }
      }

      return false;
   }

   // --- acquire / transition / release -------------------------------------

   /**
    * Acquire a control on a target. Fails fast when the target already carries
    * a control, when the legacy viltrumite grab owns it, or when the target's
    * hero policy refuses the kind.
    */
   public boolean tryAcquire(LivingEntity target, UUID caster, UUID effectId, ControlKind kind) {
      return this.tryAcquire(target, caster, effectId, kind, -1L);
   }

   /** Same as {@link #tryAcquire} plus an absolute game-time expiry. */
   public boolean tryAcquire(LivingEntity target, UUID caster, UUID effectId, ControlKind kind, long expiresAt) {
      if (!mayAcquire(kind, this.controls.containsKey(target.getUUID()), target.getTags().contains("ViltrumiteGrabbed"), this.isAllowedOnTarget(target, kind))) {
         return false;
      }

      ControlRecord record = new ControlRecord(target, caster, effectId, kind);
      record.expiresAt = expiresAt;
      this.controls.put(target.getUUID(), record);
      record.anchor(target);
      this.dirty = true;
      return true;
   }

   /**
    * Precedence predicate (pure, unit-tested): an existing control or a grabbed
    * target rejects every new acquire; a hero may also refuse the kind. PULL
    * still permits a later grab latch as counterplay — that is a release path
    * (HANDOFF_TO_GRAB), not an acquire rule.
    */
   static boolean mayAcquire(ControlKind kind, boolean alreadyControlled, boolean grabbedTag, boolean heroAllowed) {
      if (!heroAllowed || alreadyControlled) {
         return false;
      }

      return !grabbedTag;
   }

   /** Extend only our matching control. No re-anchor, flag recapture, or structural sync. */
   public boolean refresh(UUID targetId, UUID caster, UUID effectId, ControlKind kind, long expiresAt) {
      return refreshRecord(this.controls.get(targetId), caster, effectId, kind, expiresAt);
   }

   static boolean mayRefresh(@Nullable ControlRecord record, UUID caster, UUID effectId, ControlKind kind) {
      return record != null && record.caster.equals(caster) && record.effectId.equals(effectId) && record.kind == kind;
   }

   static boolean refreshRecord(@Nullable ControlRecord record, UUID caster, UUID effectId, ControlKind kind, long expiresAt) {
      if (!mayRefresh(record, caster, effectId, kind)) return false;
      record.expiresAt = expiresAt;
      return true;
   }

   /**
    * Atomic transition: keep the ownership record (saved values stay stored)
    * while switching the applied kind, e.g. PULL -> FREEZE. Anchoring kinds
    * re-anchor at the target's current position, not the acquire position.
    */
   public boolean transition(UUID targetId, UUID effectId, ControlKind next) {
      return this.transition(targetId, effectId, next, -1L);
   }

   /** Same as {@link #transition} plus an absolute game-time expiry. */
   public boolean transition(UUID targetId, UUID effectId, ControlKind next, long expiresAt) {
      ControlRecord record = this.controls.get(targetId);
      if (record == null || !record.effectId.equals(effectId)) {
         return false;
      }

      record.kind = next;
      record.expiresAt = expiresAt;
      ServerLevel level = this.firstLevel();
      LivingEntity target = find(level, record.targetId);
      if (target != null) {
         // Hand the captured flags back before re-applying under the new kind:
         // an anchor -> non-anchor move would otherwise leak noGravity/noAi.
         record.unapply(target);
         if (deniesActions(next)) {
            record.anchor = target.position();
         }

         record.apply(target);
      }

      this.dirty = true;
      return true;
   }

   /** One release order: remove ownership first, then restore, then damage once. */
   public void release(UUID targetId, UUID effectId, ReleaseReason reason) {
      ControlRecord record = this.controls.remove(targetId);
      if (record == null) {
         return;
      }

      if (!record.effectId.equals(effectId)) {
         this.controls.put(targetId, record);
         return;
      }

      this.dirty = true;
      ServerLevel level = this.firstLevel();
      LivingEntity target = find(level, record.targetId);
      if (target == null) {
         PENDING_TARGET_RESTORE.put(record.targetId, new SavedFlags(record.savedNoGravity, record.savedNoAi));
         return;
      }

      record.restore(target);
      // Clamp against the CURRENT max health, in case it changed mid-control.
      float cap = record.kind == ControlKind.STASIS
         ? RegulusRules.domeDeferredCap(target.getMaxHealth())
         : RegulusRules.freezeDeferredCap(target.getMaxHealth());
      float payable = Math.min(record.queuedDamage, cap);
      if (payable > 0.0F) {
         HeroDamage.applyCleanDamage(target, record.lastDamageSource == null ? target.damageSources().generic() : record.lastDamageSource, payable);
      }
   }

   /**
    * Caster lifecycle: DEATH/DISCONNECT end channels and freezes and PULLs; a
    * HERO_CHANGE also removes the caster's domes. Dome records are world-owned
    * and survive a dead caster — so do the STASIS records the dome owns (spec
    * 9.2/16: a caster death keeps the dome and its captured targets).
    * Caster-owned records can sit in another level's manager (frozen
    * projectiles stay behind on a dimension change), so the cleanup sweeps
    * every instance.
    */
   public void cleanupCaster(UUID caster, CleanupReason reason) {
      for (ControlManager manager : INSTANCES.values()) {
         manager.cleanupCasterHere(caster, reason);
      }
   }

   private void cleanupCasterHere(UUID caster, CleanupReason reason) {
      for (Map.Entry<UUID, ControlRecord> entry : new HashMap<>(this.controls).entrySet()) {
         ControlRecord record = entry.getValue();
         if (!record.caster.equals(caster)) {
            continue;
         }

         if (!releaseOnCasterCleanup(record.kind, this.domes.containsKey(record.effectId), reason)) {
            continue;
         }

         this.release(entry.getKey(), record.effectId, cleanupReleaseReason(reason));
      }

      if (!reason.losesBody()) {
         if (this.domes.values().removeIf(dome -> dome.caster().equals(caster))) {
            this.dirty = true;
         }
      }

      this.releaseCasterFromProjectiles(caster);
   }

   /** Pure predicate: dome-owned STASIS is the only control a death keeps. */
   static boolean releaseOnCasterCleanup(ControlKind kind, boolean domeLinked, CleanupReason reason) {
      return !(reason.losesBody() && kind == ControlKind.STASIS && domeLinked);
   }

   /** Maps a caster cleanup to the release reason victims see (no freeze). */
   public static ReleaseReason cleanupReleaseReason(CleanupReason reason) {
      return switch (reason) {
         case DEATH, ERASED -> ReleaseReason.CASTER_DEATH;
         case HERO_CHANGE -> ReleaseReason.HERO_CHANGE;
         case DISCONNECT -> ReleaseReason.CASTER_DISCONNECT;
      };
   }

   /**
    * Target lifecycle: detach the record entirely (targets vanish on death),
    * restoring the victim's saved flags when it can still be resolved — and
    * deferring that restore to its next level join when it cannot.
    */
   public void cleanupTarget(UUID targetId) {
      ControlRecord record = this.controls.remove(targetId);
      if (record == null) {
         return;
      }

      this.dirty = true;
      LivingEntity target = find(this.firstLevel(), targetId);
      if (target != null) {
         record.restore(target);
      } else {
         PENDING_TARGET_RESTORE.put(targetId, new SavedFlags(record.savedNoGravity, record.savedNoAi));
      }
   }

   // --- deferred damage -----------------------------------------------------

   /**
    * Queue one blocked external hit against an anchored victim. The caller has
    * already evaluated the payable amount; the cap keeps it bounded.
    */
   public boolean queueDamage(LivingEntity target, DamageSource source, float amount) {
      ControlRecord record = this.controls.get(target.getUUID());
      if (record == null) {
         return false;
      }

      float cap = record.kind == ControlKind.STASIS
         ? RegulusRules.domeDeferredCap(target.getMaxHealth())
         : RegulusRules.freezeDeferredCap(target.getMaxHealth());
      record.queuedDamage = RegulusRules.deferredAccumulate(record.queuedDamage, amount, cap);
      record.lastDamageSource = source;
      return true;
   }

   public float queuedDamage(LivingEntity target) {
      ControlRecord record = this.controls.get(target.getUUID());
      return record == null ? 0.0F : record.queuedDamage;
   }

   // --- domes ---------------------------------------------------------------

   public void addDome(DomeRecord dome) {
      this.domes.put(dome.id(), dome);
      this.dirty = true;
   }

   /** One live dome per caster (spec 9.2), enforced on the world index. */
   public boolean hasDomeFrom(UUID caster) {
      for (DomeRecord dome : this.domes.values()) {
         if (dome.caster().equals(caster)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   public DomeRecord dome(UUID id) {
      return this.domes.get(id);
   }

   public Map<UUID, DomeRecord> domes() {
      return Map.copyOf(this.domes);
   }

   public boolean isInsideDome(UUID entityId) {
      for (DomeRecord dome : this.domes.values()) {
         if (dome.captured().contains(entityId)) {
            return true;
         }
      }

      return false;
   }

   // --- projectiles ----------------------------------------------------------

   public void freezeProjectile(Projectile projectile, UUID caster) {
      ProjectileFreezeRecord record = this.frozenProjectiles.computeIfAbsent(
         projectile.getUUID(), id -> new ProjectileFreezeRecord(projectile)
      );
      record.casters.add(caster);
      projectile.setNoGravity(true);
      projectile.setDeltaMovement(Vec3.ZERO);
   }

   /**
    * Drop one caster's ownership on every projectile it froze, in every level.
    * When the last caster releases, the projectile keeps velocity zero and
    * gets its original gravity back, so ordinary projectiles simply fall; if
    * the projectile is unreachable the restore is deferred to its next join.
    */
   public static void releaseProjectilesFor(UUID caster) {
      for (ControlManager manager : INSTANCES.values()) {
         manager.releaseCasterFromProjectiles(caster);
      }
   }

   private void releaseCasterFromProjectiles(UUID caster) {
      this.frozenProjectiles.values().removeIf(record -> {
         if (!record.casters.remove(caster)) {
            return false;
         }

         if (!record.casters.isEmpty()) {
            return false;
         }

         record.restoreOrDefer();
         return true;
      });
   }

   /**
    * Entity join hook: a frozen projectile that rejoins any level after its
    * record was dropped while unreachable gets its saved gravity back.
    */
   public static void onEntityJoinLevel(Entity entity) {
      if (entity instanceof Projectile) {
         Boolean savedNoGravity = PENDING_GRAVITY_RESTORE.remove(entity.getUUID());
         if (savedNoGravity != null) {
            entity.setNoGravity(savedNoGravity);
         }
      }

      if (entity instanceof LivingEntity) {
         restorePendingTarget(entity);
      }
   }

   /**
    * A living target whose control record dropped while it was unreachable
    * (disconnect, dimension change) gets its saved noGravity/noAi flags back
    * on the next level join — the deferred mirror of the frozen-projectile
    * restore. Called from the join event and the player login/respawn/dimension
    * handlers; a repeat call is a no-op.
    */
   public static void restorePendingTarget(Entity entity) {
      SavedFlags flags = PENDING_TARGET_RESTORE.remove(entity.getUUID());
      if (flags == null) {
         return;
      }

      entity.setNoGravity(flags.noGravity());
      if (entity instanceof Mob mob) {
         mob.setNoAi(flags.noAi());
      }
   }

   // --- tick -----------------------------------------------------------------

   public void tick(ServerLevel level) {
      long now = level.getGameTime();
      List<ControlRecord> expired = null;
      for (java.util.Iterator<ControlRecord> it = this.controls.values().iterator(); it.hasNext();) {
         ControlRecord record = it.next();
         LivingEntity target = find(level, record.targetId);
         if (target == null || !target.isAlive()) {
            if (target == null) {
               // Unreachable but not provably dead (disconnect, dimension
               // change): the entity was serialized with the frozen flags —
               // defer their restore to its next level join.
               PENDING_TARGET_RESTORE.put(record.targetId, new SavedFlags(record.savedNoGravity, record.savedNoAi));
            }
            // Targets vanish on death/despawn: detach and discard the queue.
            it.remove();
            this.dirty = true;
            continue;
         }

         if (record.expiresAt >= 0L && now >= record.expiresAt) {
            if (expired == null) {
               expired = new ArrayList<>();
            }

            expired.add(record);
            continue;
         }

         record.apply(target);
      }

      if (expired != null) {
         for (ControlRecord record : expired) {
            this.release(record.targetId, record.effectId, ReleaseReason.EXPIRED);
         }
      }

      this.domes.values().removeIf(dome -> {
         if (now < dome.expiresAt()) {
            return false;
         }

         this.expireDome(level, dome);
         return true;
      });

      // A projectile in an unloaded chunk keeps its record: it still exists
      // and re-resolves on reload. Only a loaded-but-missing entity (removed
      // or moved to another level) drops the record, with the gravity restore
      // deferred so it cannot hang mid-air with noGravity in NBT.
      this.frozenProjectiles.values().removeIf(record -> {
         if (!shouldDropFreezeRecord(record.projectile() != null, record.isLoaded())) {
            return false;
         }

         record.deferRestore();
         return true;
      });

      if (this.dirty) {
         this.dirty = false;
         HeroControlSync.broadcast(level);
      }
   }

   /**
    * Dome close: every captured target releases its control (the queued damage
    * lands once per target) and then gets an impulse away from the center.
    */
   private void expireDome(ServerLevel level, DomeRecord dome) {
      for (UUID targetId : dome.captured()) {
         this.release(targetId, dome.id(), ReleaseReason.EXPIRED);
      }

      for (UUID targetId : dome.captured()) {
         Entity entity = level.getEntity(targetId);
         if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            continue;
         }

         Vec3 away = target.position().subtract(dome.center());
         Vec3 push = new Vec3(away.x, 0.0, away.z);
         if (push.lengthSqr() < 1.0E-4) {
            push = new Vec3(1.0, 0.0, 0.0);
         }

         push = push.normalize().scale(RegulusRules.EMBRACE_RELEASE_IMPULSE);
         target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.3, push.z));
         target.hasImpulse = true;
         if (target instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
         }
      }

      this.dirty = true;
   }

   /** Internal view for the world-control snapshot sync (same-package use). */
   Collection<ControlRecord> records() {
      return this.controls.values();
   }

   // --- internals -------------------------------------------------------------

   /**
    * Drop only a record whose projectile is provably gone: resolved entities
    * keep their record, and an unloaded chunk means the projectile still
    * exists and re-resolves on reload. On drop the gravity restore is
    * deferred to the next entity join.
    */
   static boolean shouldDropFreezeRecord(boolean projectileResolved, boolean chunkLoaded) {
      return !projectileResolved && chunkLoaded;
   }

   private boolean isAllowedOnTarget(LivingEntity target, ControlKind kind) {
      if (target instanceof ServerPlayer player && player instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer) {
         return dev.baranhan.viltrumitecore.hero.HeroRegistry.get(player).allowsExternalControl(target, kind);
      }

      return true;
   }

   private ServerLevel firstLevel() {
      // Controls live inside the ServerLevel they were acquired on; callers keep
      // a lookup table of instances, so use the instance's own level.
      for (Map.Entry<ServerLevel, ControlManager> entry : INSTANCES.entrySet()) {
         if (entry.getValue() == this) {
            return entry.getKey();
         }
      }

      return null;
   }

   @Nullable
   private static LivingEntity find(@Nullable ServerLevel level, UUID id) {
      if (level == null || id == null) {
         return null;
      }

      Entity entity = level.getEntity(id);
      return entity instanceof LivingEntity living ? living : null;
   }

   /** Saves and re-applies everything a control changed on its victim. */
   static final class ControlRecord {
      final UUID targetId;
      final UUID caster;
      final UUID effectId;
      ControlKind kind;

      Vec3 anchor;
      boolean savedNoGravity;
      boolean savedNoAi;
      float queuedDamage;
      DamageSource lastDamageSource;
      // Absolute game time when the control ends on its own; -1 = no expiry.
      long expiresAt = -1L;

      ControlRecord(LivingEntity target, UUID caster, UUID effectId, ControlKind kind) {
         this(target.getUUID(), caster, effectId, kind, target.position(),
            dev.baranhan.viltrumitecore.hero.LiftLeases.SERVER.naturalNoGravity(target.getUUID(), target.isNoGravity()),
            target instanceof Mob mob && mob.isNoAi());
      }

      /** Captured control state. Refresh never reconstructs this capture. */
      ControlRecord(UUID targetId, UUID caster, UUID effectId, ControlKind kind, Vec3 anchor, boolean noGravity, boolean noAi) {
         this.targetId = targetId;
         this.caster = caster;
         this.effectId = effectId;
         this.kind = kind;
         this.anchor = anchor;
         this.savedNoGravity = noGravity;
         this.savedNoAi = noAi;
      }

      void anchor(LivingEntity target) {
         this.apply(target);
      }

      void apply(LivingEntity target) {
         if (deniesActions(this.kind)) {
            target.setNoGravity(true);
            if (target instanceof Mob mob) {
               mob.setNoAi(true);
            }

            // An anchored victim cannot act (spec 8.3/9.2): a held item
            // channel ends on the anchor tick, flight state drops, and any
            // armed ability toggles unwind.
            if (target instanceof Player player && player.isUsingItem()) {
               player.stopUsingItem();
            }

            if (target instanceof ViltrumiteFlightPlayer flightPlayer) {
               flightPlayer.stopFlight();
            }

            if (target instanceof ViltrumiteCorePlayer corePlayer) {
               corePlayer.setBarraging(false);
               corePlayer.setBlocking(false);
               corePlayer.setTryingToGrab(false);
            }

            target.setDeltaMovement(Vec3.ZERO);
            if (target.distanceToSqr(this.anchor) > 0.01) {
               if (target instanceof ServerPlayer sp) {
                  sp.connection.teleport(this.anchor.x, this.anchor.y, this.anchor.z, sp.getYRot(), sp.getXRot());
               } else {
                  target.teleportTo(this.anchor.x, this.anchor.y, this.anchor.z);
               }
            }
         }
      }

      void unapply(LivingEntity target) {
         target.setNoGravity(this.savedNoGravity);
         if (target instanceof Mob mob) {
            mob.setNoAi(this.savedNoAi);
         }
      }

      void restore(LivingEntity target) {
         target.setNoGravity(this.savedNoGravity);
         if (target instanceof Mob mob) {
            mob.setNoAi(this.savedNoAi);
         }

         if (deniesActions(this.kind)) {
            // The freeze clears the victim's own velocity; leave gravity to
            // reassert naturally.
            target.hurtMarked = true;
         }
      }
   }

   static final class ProjectileFreezeRecord {
      private final UUID projectileId;
      // The frozen-in level is kept by dimension key, not by reference — a
      // strong ServerLevel here would pin the WeakHashMap INSTANCES key.
      private final MinecraftServer server;
      private final ResourceKey<Level> dimension;
      private final BlockPos position;
      final Set<UUID> casters = ConcurrentHashMap.newKeySet();
      boolean savedNoGravity;

      ProjectileFreezeRecord(Projectile projectile) {
         this.projectileId = projectile.getUUID();
         this.server = projectile.level().getServer();
         this.dimension = projectile.level().dimension();
         this.position = projectile.blockPosition();
         // A restore deferred while this projectile was unreachable carries
         // the original flag; re-saving the current value (true while frozen)
         // would freeze its gravity forever.
         Boolean pending = PENDING_GRAVITY_RESTORE.remove(this.projectileId);
         this.savedNoGravity = pending != null ? pending : projectile.isNoGravity();
      }

      @Nullable
      private ServerLevel level() {
         return this.server == null ? null : this.server.getLevel(this.dimension);
      }

      @Nullable
      Projectile projectile() {
         ServerLevel level = this.level();
         Entity entity = level == null ? null : level.getEntity(this.projectileId);
         return entity instanceof Projectile p ? p : null;
      }

      boolean isLoaded() {
         ServerLevel level = this.level();
         return level != null && level.hasChunkAt(this.position);
      }

      void restoreOrDefer() {
         Projectile projectile = this.projectile();
         if (projectile != null && !projectile.isRemoved()) {
            projectile.setNoGravity(this.savedNoGravity);
            projectile.setDeltaMovement(Vec3.ZERO);
         } else if (projectile == null) {
            this.deferRestore();
         }
      }

      void deferRestore() {
         PENDING_GRAVITY_RESTORE.put(this.projectileId, this.savedNoGravity);
      }
   }
}
