package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.HeroTargets;
import dev.baranhan.viltrumitecore.hero.combat.StagedTimeline;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvent;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEventType;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * Session B's damage path: who a strike reaches (along the view only, no
 * target choice, no auto turn), the hit itself (i-frames skipped, player
 * attack source, server authority) and the bridge events: MELEE_HIT after
 * a landed melee hit, LETHAL_HIT from the damage path before the death is
 * final (hit-time hammerInHand snapshot), COMBO_END from the handlers.
 * Knockback only for targets that accept impulses; never a stun (#84).
 */
public final class CombatHits {
   /** Moves whose hits are melee hits for the bridge (MELEE_HIT / LETHAL_HIT). */
   public static final Set<ThorMove> MELEE = EnumSet.of(ThorMove.HAMMER_COMBO, ThorMove.FIST_COMBO, ThorMove.HAMMER_STRIKE);

   /** The hit in progress on this thread (read by the LivingDamageEvent hook). */
   private record Scope(ServerPlayer attacker, LivingEntity target, ThorMove move, int attackId, boolean hammerInHand) {
   }

   private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

   private CombatHits() {
   }

   // --- who ---

   /** The shared hero target rule (HeroTargets.canHit), the same one the MMB resolver and Mjölnir use. */
   public static boolean isEnemy(Player player, Entity entity) {
      return HeroTargets.canHit(player, entity);
   }

   /** Enemies a melee strike reaches from the player's view (line of sight required). */
   public static List<LivingEntity> strikeTargets(ServerPlayer player, ThorStrike strike) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = flat(player.getLookAngle());
      double reach = strike.shape() == ThorStrike.Shape.SLAM ? strike.reach() + strike.shockRadius() : strike.reach();
      List<LivingEntity> found = new ArrayList<>();
      for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0),
         e -> isEnemy(player, e))) {
         if (!player.hasLineOfSight(entity)) {
            continue;
         }

         boolean inside = switch (strike.shape()) {
            case RING -> entity.getBoundingBox().distanceToSqr(eye) <= strike.reach() * strike.reach();
            case ARC, THRUST -> entity.getBoundingBox().distanceToSqr(eye) <= strike.reach() * strike.reach()
               && withinArc(look, entity.getBoundingBox().getCenter().subtract(eye), strike.arc());
            case SLAM -> {
               Vec3 center = player.position().add(look.scale(strike.reach()));
               Vec3 at = entity.position();
               double dx = at.x - center.x;
               double dz = at.z - center.z;
               yield dx * dx + dz * dz <= strike.shockRadius() * strike.shockRadius() && Math.abs(at.y - center.y) <= 2.5;
            }
         };
         if (inside) {
            found.add(entity);
         }
      }

      found.sort(Comparator.comparingDouble(player::distanceToSqr));
      return found;
   }

   /** Pure: is {@code to} within {@code halfAngle} degrees of the flat view {@code look}? A target inside the body always counts. */
   public static boolean withinArc(Vec3 look, Vec3 to, double halfAngle) {
      Vec3 flatTo = flat(to);
      if (flatTo.lengthSqr() < 0.25) {
         return true;
      }

      if (halfAngle >= 180.0) {
         return true;
      }

      double cos = look.normalize().dot(flatTo.normalize());
      return cos >= Math.cos(Math.toRadians(halfAngle));
   }

   /** Enemies within {@code radius} of a point (cylinder of {@code height} up and down), nearest first. */
   public static List<LivingEntity> around(ServerPlayer player, Vec3 center, double radius, double height) {
      AABB box = new AABB(center, center).inflate(radius, height, radius);
      List<LivingEntity> found = new ArrayList<>(player.level().getEntitiesOfClass(LivingEntity.class, box, e -> isEnemy(player, e)
         && horizontalSqr(e.position(), center) <= radius * radius));
      found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
      return found;
   }

   /** The first enemy on a ray from {@code from} along {@code direction} (a narrow beam, not aim assist), or null. */
   @Nullable
   public static LivingEntity alongRay(ServerPlayer player, Vec3 from, Vec3 direction, double range, double width) {
      Vec3 dir = direction.normalize();
      LivingEntity best = null;
      double bestT = Double.MAX_VALUE;
      AABB box = new AABB(from, from.add(dir.scale(range))).inflate(width + 1.0);
      for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box, e -> isEnemy(player, e))) {
         Vec3 center = entity.getBoundingBox().getCenter();
         double t = center.subtract(from).dot(dir);
         if (t <= 0.0 || t > range) {
            continue;
         }

         double off = center.distanceTo(from.add(dir.scale(t)));
         if (off <= width + entity.getBbWidth() * 0.5 && t < bestT && player.hasLineOfSight(entity)) {
            best = entity;
            bestT = t;
         }
      }

      return best;
   }

   public static Vec3 flat(Vec3 v) {
      Vec3 flat = new Vec3(v.x, 0.0, v.z);
      return flat.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
   }

   private static double horizontalSqr(Vec3 a, Vec3 b) {
      double dx = a.x - b.x;
      double dz = a.z - b.z;
      return dx * dx + dz * dz;
   }

   // --- the hit ---

   /**
    * Mjölnir in the hand right now, read through the bridge: attached to Thor
    * and the right hand free (an item there keeps it on the belt, the same
    * rule as MjolnirPlace and ThorCombat.presence).
    */
   public static boolean hammerInHand(ServerPlayer player) {
      return ThorBridge.hammerHeld(player);
   }

   /**
    * Pure: the hit-time snapshot the bridge events carry: Mjölnir in the
    * hand and the blow is not a bare-fist strike (a fist chain finishing
    * right after the catch, #166, is not a hammer blow, so it can never
    * qualify for a fatality).
    */
   public static boolean hammerBlow(ThorMove move, boolean inHand) {
      return inHand && move != ThorMove.FIST_COMBO;
   }

   /**
    * One hit: false when it did not land (i-frames restored). A landed melee
    * hit posts MELEE_HIT; a lethal one has posted LETHAL_HIT already, from
    * the damage path, before the death.
    */
   public static boolean hit(ServerPlayer player, LivingEntity target, float damage, ThorMove move, int attackId) {
      return hit(player, target, damage, move, attackId, true);
   }

   /**
    * An electric area / ranged hit (LMB discharge and its chain, RMB wave,
    * storm pulses and burst, sky splash): the target's vanilla i-frames
    * apply (B-M5). Only authored strikes and combo finishers skip them.
    */
   public static boolean hitArea(ServerPlayer player, LivingEntity target, float damage, ThorMove move, int attackId) {
      return hit(player, target, damage, move, attackId, false);
   }

   /** Thor's lightning damage type (data/viltrumitecore/damage_type, tagged minecraft:is_lightning); built lazily (registries). */
   public static ResourceKey<DamageType> lightningType() {
      return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("viltrumitecore", "thor_lightning"));
   }

   /** Pure: is this move's damage electric (B-m8)? Melee hammer / fist strikes stay a player attack. */
   public static boolean electric(ThorMove move) {
      return switch (move) {
         case ELECTRIC_COMBO, ELECTRIC_WAVE, ELECTRIC_STORM, WRATH -> true;
         default -> false;
      };
   }

   /**
    * The damage source of a Thor hit: electric moves deal {@code thor_lightning}
    * caused by Thor (death messages, is_lightning tag, armour and mods see
    * lightning), the rest a player attack. Both name Thor as the attacker, so
    * PvP rules, kill credit and LETHAL_HIT scoping are unchanged.
    */
   static DamageSource source(ServerPlayer player, ThorMove move) {
      if (electric(move)) {
         var types = player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
         var holder = types.getHolder(lightningType());
         if (holder.isPresent()) {
            return new DamageSource(holder.get(), player);
         }
      }

      return player.damageSources().playerAttack(player);
   }

   /** Pure: may a hit skip the target's i-frames? Authored strikes / finishers yes, electric area and ranged hits no (B-M5). */
   public static boolean skipsIFrames(boolean authoredStrike) {
      return authoredStrike;
   }

   private static boolean hit(ServerPlayer player, LivingEntity target, float damage, ThorMove move, int attackId, boolean authoredStrike) {
      if (damage <= 0.0F || !target.isAlive()) {
         return false;
      }

      boolean hammer = hammerBlow(move, hammerInHand(player));
      Scope previous = SCOPE.get();
      SCOPE.set(new Scope(player, target, move, attackId, hammer));
      int saved = target.invulnerableTime;
      if (skipsIFrames(authoredStrike) && claimSkip(player, target, attackId)) {
         target.invulnerableTime = 0;
      }
      boolean landed;
      try {
         landed = target.hurt(source(player, move), damage);
      } finally {
         if (previous == null) {
            SCOPE.remove();
         } else {
            SCOPE.set(previous);
         }
      }

      if (!landed) {
         target.invulnerableTime = saved;
         return false;
      }

      if (MELEE.contains(move)) {
         ThorEvents.post(event(ThorEventType.MELEE_HIT, player.getUUID(), target.getUUID(), move, attackId, hammer, damage, true)
            .at(ThorEvent.Where.of(player, target)));
      }

      return true;
   }

   /** B-M5: this swing may skip the target's i-frames now (once per swing, target and tick). */
   private static boolean claimSkip(ServerPlayer player, LivingEntity target, int attackId) {
      ThorState state = ThorState.of(player);
      return state == null || ThorCombatState.of(state).claimSkip(target.getId(), attackId, player.level().getGameTime());
   }

   /** LivingDamageEvent (LOWEST, after every clamp): a Thor melee hit that would kill posts LETHAL_HIT before the death. */
   public static void onLivingDamage(LivingDamageEvent event) {
      Scope scope = SCOPE.get();
      LivingEntity victim = event.getEntity();
      if (scope == null || victim != scope.target() || !MELEE.contains(scope.move()) || !lethal(event.getAmount(), victim.getHealth())) {
         return;
      }

      ThorEvents.post(event(ThorEventType.LETHAL_HIT, scope.attacker().getUUID(), victim.getUUID(), scope.move(), scope.attackId(),
         scope.hammerInHand(), event.getAmount(), true).at(ThorEvent.Where.of(scope.attacker(), victim)));
   }

   /** Pure: the final damage (after absorption) takes the last health. */
   public static boolean lethal(float amount, float health) {
      return amount > 0.0F && amount >= health;
   }

   /** Pure event builder (fresh dedupe id). */
   public static ThorEvent event(ThorEventType type, UUID attacker, @Nullable UUID target, @Nullable ThorMove move, int attackId, boolean hammerInHand,
      float amount, boolean success) {
      return new ThorEvent(ThorEvents.nextId(), type, attacker, target, move, attackId, hammerInHand, amount, success);
   }

   /**
    * Bridge STRIKE_START once per swing as it enters its effective part (the
    * server's damage window); a swing still open is closed first, so START /
    * END always pair.
    */
   public static void strikeStart(ServerPlayer player, ThorCombatState combat, ThorMove move, int attackId) {
      strikeEnd(player, combat);
      combat.openStrike = attackId;
      combat.openStrikeMove = move;
      ThorEvents.post(event(ThorEventType.STRIKE_START, player.getUUID(), null, move, attackId, hammerInHand(player), 0.0F, true)
         .at(ThorEvent.Where.of(player, null)));
   }

   /** STRIKE_END of the open swing, if any (phase change, chain, stop). Pure state clear when the player is gone. */
   public static void strikeEnd(@Nullable ServerPlayer player, ThorCombatState combat) {
      int attackId = combat.openStrike;
      ThorMove move = combat.openStrikeMove;
      combat.openStrike = 0;
      combat.openStrikeMove = null;
      if (attackId != 0 && player != null) {
         ThorEvents.post(event(ThorEventType.STRIKE_END, player.getUUID(), null, move, attackId, hammerInHand(player), 0.0F, true)
            .at(ThorEvent.Where.of(player, null)));
      }
   }

   /** Phase hook of every striking move: ENTER_MAIN opens its swing, any other edge closes it. */
   public static void strikePhase(ServerPlayer player, ThorCombatState combat, ThorMove move, int attackId, StagedTimeline.Event event) {
      if (event == StagedTimeline.Event.ENTER_MAIN) {
         strikeStart(player, combat, move, attackId);
      } else if (event != StagedTimeline.Event.NONE) {
         strikeEnd(player, combat);
      }
   }

   /** Push away from {@code from} with an optional lift; only for targets that accept impulses (no stun, no hold). */
   public static void push(LivingEntity target, Vec3 from, double strength, double lift) {
      if ((strength <= 0.0 && lift <= 0.0) || !HeroRegistry.allowsImpulse(target)) {
         return;
      }

      if (strength > 0.0) {
         // LivingEntity.knockback pushes against (x, z): pass the direction back to the source.
         Vec3 away = flat(target.position().subtract(from));
         target.knockback(strength, -away.x, -away.z);
      }

      if (lift > 0.0) {
         Vec3 motion = target.getDeltaMovement();
         target.setDeltaMovement(motion.x, Math.max(motion.y, lift), motion.z);
      }

      target.hurtMarked = true;
   }
}
