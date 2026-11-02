package dev.baranhan.viltrumitecore.entity.directive;

import dev.baranhan.viltrumitecore.entity.Bystander;
import dev.baranhan.viltrumitecore.hero.ActionLocks;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side behaviour-control layer (docs/rohan/heavens-door-design.md §6): directives written into living bodies and
 * enforced the same way for every author. Mobs get an injected top-priority goal (movement), a target veto
 * (LivingChangeTargetEvent and a per-tick check that also clears brain memories) and a damage veto; players get the
 * damage veto, a movement root, flight and per-action seals through {@link ActionLocks}; custom brains get
 * {@link DirectiveAdapter}s. Directives live in the entity's persistent NBT (they survive unloads and restarts) with an
 * in-memory cache; every one ends by game time and {@link DirectiveRules#MAX_TICKS} caps them.
 */
public final class MobDirectives {
   public static final String NBT_KEY = "viltrumitecore:directives";
   public static final double KEEP_AWAY_DISTANCE = 12.0;
   public static final double FOLLOW_DISTANCE = 3.5;
   public static final double GUARD_DISTANCE = 4.0;
   public static final float RIPPED_KNOCKBACK = 1.5F;
   /** A ripped page returns on a hit of this many points or more... */
   public static final float HEAVY_HIT = 6.0F;
   /** ...or when the body falls below this fraction of its health. */
   public static final float LOW_HEALTH = 0.25F;
   static final UUID ROOT_ID = UUID.fromString("5d0c3a7e-91b4-4c2f-8e61-7a2b9c4d1e01");
   private static final Map<LivingEntity, List<Directive>> CACHE = new WeakHashMap<>();
   private static final List<DirectiveAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
   private static boolean initialized;

   private MobDirectives() {
   }

   /** Mod constructor: the ActionLocks seals (players with an open book or a sealed ability start nothing). */
   public static void init() {
      if (initialized) {
         return;
      }
      initialized = true;
      ActionLocks.register(e -> !e.level().isClientSide && has(e, DirectiveEffect.OPEN));
      ActionLocks.registerAction((e, action) -> !e.level().isClientSide && DirectiveRules.seals(active(e), action.ordinal()));
   }

   public static void registerAdapter(DirectiveAdapter adapter) {
      if (adapter != null && !ADAPTERS.contains(adapter)) {
         ADAPTERS.add(adapter);
      }
   }

   @Nullable
   static DirectiveAdapter adapter(LivingEntity e) {
      for (DirectiveAdapter a : ADAPTERS) {
         if (a.handles(e)) {
            return a;
         }
      }
      return null;
   }

   /** Can this body carry an adapter-only effect now? */
   public static boolean supports(LivingEntity e, DirectiveEffect effect) {
      DirectiveAdapter adapter = adapter(e);
      return adapter != null && adapter.supports(e, effect);
   }

   // ------------------------------------------------------------------ store

   /** Active directives of a body (server; empty on the client or when none). */
   public static List<Directive> active(@Nullable LivingEntity e) {
      if (e == null || e.level().isClientSide) {
         return Collections.emptyList();
      }
      List<Directive> list = CACHE.get(e);
      if (list == null) {
         CompoundTag data = e.getPersistentData();
         if (!DirectiveRules.isList(data, NBT_KEY)) {
            return Collections.emptyList();
         }
         list = DirectiveRules.load(data.getList(NBT_KEY, 10), e.level().getGameTime());
         CACHE.put(e, list);
      }
      long now = e.level().getGameTime();
      List<Directive> pruned = DirectiveRules.prune(list, now);
      if (pruned != list) {
         store(e, pruned);
      }
      return pruned;
   }

   public static boolean any(LivingEntity e) {
      return !active(e).isEmpty();
   }

   public static boolean has(LivingEntity e, DirectiveEffect effect) {
      return effect.in(DirectiveRules.effects(active(e)));
   }

   @Nullable
   public static Directive first(LivingEntity e, DirectiveEffect effect) {
      for (Directive d : active(e)) {
         if (d.has(effect)) {
            return d;
         }
      }
      return null;
   }

   /** Writes a directive into a body (server). */
   public static void apply(LivingEntity e, Directive d) {
      if (e.level().isClientSide) {
         return;
      }
      List<Directive> merged = DirectiveRules.merge(active(e), d, e.level().getGameTime());
      store(e, merged);
      if (e instanceof Mob mob) {
         DirectiveEvents.ensureGoal(mob);
         enforceTargets(mob, merged);
      }
      DirectiveAdapter adapter = adapter(e);
      if (adapter != null) {
         adapter.onApply(e, d);
      }
   }

   public static void remove(LivingEntity e, Predicate<Directive> which) {
      List<Directive> list = active(e);
      if (list.isEmpty()) {
         return;
      }
      List<Directive> kept = new java.util.ArrayList<>(list);
      if (kept.removeIf(which)) {
         store(e, kept);
      }
   }

   public static void clearAll(LivingEntity e) {
      if (!e.level().isClientSide && (CACHE.containsKey(e) || e.getPersistentData().contains(NBT_KEY))) {
         store(e, Collections.emptyList());
      }
   }

   static void store(LivingEntity e, List<Directive> list) {
      if (list.isEmpty()) {
         CACHE.remove(e);
         e.getPersistentData().remove(NBT_KEY);
         if (e instanceof Player p) {
            root(p, false);
         }
         return;
      }
      CACHE.put(e, List.copyOf(list));
      e.getPersistentData().put(NBT_KEY, DirectiveRules.save(list));
   }

   /** Called at join: a saved body comes back with its directives (and the goal). */
   static void loadFromSave(LivingEntity e) {
      CACHE.remove(e);
      active(e);
   }

   static boolean cached(LivingEntity e) {
      return CACHE.containsKey(e);
   }

   // ------------------------------------------------------------------ queries

   /** Villagers, citizens and every other bystander. */
   public static boolean peaceful(@Nullable Entity e) {
      return e instanceof AbstractVillager || Bystander.is(e);
   }

   public static boolean forbidsTarget(LivingEntity e, @Nullable Entity target) {
      if (target == null) {
         return false;
      }
      List<Directive> list = active(e);
      if (list.isEmpty()) {
         return false;
      }
      if (DirectiveRules.forbidsTarget(list, target.getUUID(), peaceful(target))) {
         return true;
      }
      DirectiveAdapter adapter = adapter(e);
      return adapter != null && adapter.forbidsTarget(e, target, list);
   }

   public static boolean forbidsDamage(LivingEntity attacker, LivingEntity victim, DamageSource source) {
      List<Directive> list = active(attacker);
      if (list.isEmpty()) {
         return false;
      }
      DirectiveRules.HitKind kind = source.getDirectEntity() instanceof Projectile ? DirectiveRules.HitKind.RANGED
         : source.getDirectEntity() == attacker ? DirectiveRules.HitKind.MELEE : DirectiveRules.HitKind.ABILITY;
      return DirectiveRules.forbidsDamage(list, victim.getUUID(), peaceful(victim), kind);
   }

   // ------------------------------------------------------------------ enforcement

   /** Once per server tick for a body with directives. */
   static void tick(LivingEntity e) {
      List<Directive> list = active(e);
      if (list.isEmpty()) {
         return;
      }
      int effects = DirectiveRules.effects(list);
      if (e instanceof Mob mob) {
         enforceTargets(mob, list);
         guard(mob, list);
      }
      if (e instanceof Player p) {
         root(p, DirectiveEffect.ROOT.in(effects) || DirectiveEffect.OPEN.in(effects));
      }
      if (DirectiveEffect.FLY.in(effects)) {
         Directive fly = first(e, DirectiveEffect.FLY);
         if (fly != null && fly.vector() != null && HeroRegistry.allowsImpulse(e)) {
            e.setDeltaMovement(fly.vector());
            e.hasImpulse = true;
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
         }
      }
      DirectiveAdapter adapter = adapter(e);
      if (adapter != null) {
         adapter.tick(e, list);
      }
   }

   static void enforceTargets(Mob mob, List<Directive> list) {
      LivingEntity target = mob.getTarget();
      if (target != null && forbidsTarget(mob, target)) {
         mob.setTarget(null);
         if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
         }
      }
      var brain = mob.getBrain();
      if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
         brain.getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(t -> {
            if (forbidsTarget(mob, t)) {
               brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
         });
      }
      if (brain.checkMemory(MemoryModuleType.ANGRY_AT, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
         brain.getMemory(MemoryModuleType.ANGRY_AT).ifPresent(id -> {
            Entity t = mob.level() instanceof ServerLevel sl ? sl.getEntity(id) : null;
            if (t != null && forbidsTarget(mob, t)) {
               brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            }
         });
      }
   }

   /** GUARD: whoever hurt the guarded body recently becomes the guard's target. */
   private static void guard(Mob mob, List<Directive> list) {
      for (Directive d : list) {
         if (!d.has(DirectiveEffect.GUARD) || d.subject() == null || !(mob.level() instanceof ServerLevel level)) {
            continue;
         }
         if (level.getEntity(d.subject()) instanceof LivingEntity ward) {
            LivingEntity foe = ward.getLastHurtByMob();
            if (foe != null && foe != mob && foe.isAlive() && ward.tickCount - ward.getLastHurtByMobTimestamp() < 100 && !forbidsTarget(mob, foe)) {
               mob.setTarget(foe);
            }
         }
      }
   }

   static void root(Player p, boolean on) {
      AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      boolean has = speed.getModifier(ROOT_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(ROOT_ID, "Directive root", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(ROOT_ID);
      }
   }

   /** Unit vector of a body's facing on the ground plane (for "fly backwards"). */
   public static Vec3 backwards(LivingEntity e, double speed) {
      Vec3 look = Vec3.directionFromRotation(0.0F, e.getYRot());
      return new Vec3(-look.x * speed, 0.12, -look.z * speed);
   }
}
