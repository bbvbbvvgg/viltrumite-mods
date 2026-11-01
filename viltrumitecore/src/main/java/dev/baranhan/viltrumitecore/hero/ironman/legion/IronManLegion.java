package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.VeronicaPodEntity;
import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.hero.ActionLocks;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroHeldTargets;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import dev.baranhan.viltrumitecore.hero.ironman.IronManHero;
import dev.baranhan.viltrumitecore.hero.ironman.IronManLegionSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.mark.IronManMarks;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkId;
import dev.baranhan.viltrumitecore.hero.ironman.mark.MarkLocation;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.IronManVeronica;
import dev.baranhan.viltrumitecore.hero.ironman.veronica.VeronicaView;
import dev.baranhan.viltrumitecore.network.CoreMessages;
import dev.baranhan.viltrumitecore.network.packet.LegionMenuS2CPacket;
import dev.baranhan.viltrumitecore.network.packet.LegionStatusS2CPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server entry of the Iron Legion (design §2–§4): the ability key, the call,
 * the orders, the per-owner squads and every lifecycle exit. The client only
 * shows menus and sends wishes; every rule is checked here.
 */
public final class IronManLegion {
   /** Why a call is refused (lang key suffix under gui.viltrumitecore.legion.refuse_*). */
   public enum Refusal {
      NONE,
      COOLDOWN,
      VERONICA_BUSY,
      NO_SUITS,
      CONTROLLED
   }

   private static final Map<UUID, LegionSquad> SQUADS = new HashMap<>();

   private IronManLegion() {
   }

   @Nullable
   public static LegionSquad squad(ServerPlayer owner) {
      return SQUADS.get(owner.getUUID());
   }

   public static boolean owns(ServerPlayer owner, LegionSuitEntity suit) {
      LegionSquad squad = SQUADS.get(owner.getUUID());
      return squad != null && squad.suits.contains(suit);
   }

   /** Call cooldown left for the panel (extraCooldowns[4]). */
   public static int cooldown(UUID owner) {
      LegionSquad squad = SQUADS.get(owner);
      return squad == null ? 0 : squad.cooldown;
   }

   public static boolean active(ServerPlayer owner) {
      LegionSquad squad = squad(owner);
      return squad != null && squad.active();
   }

   /** A suit body left the world: off the squad; the last one starts the call cooldown. */
   public static void forget(ServerPlayer owner, LegionSuitEntity suit) {
      LegionSquad squad = squad(owner);
      if (squad == null || !squad.suits.remove(suit)) {
         return;
      }

      squad.dirty = true;
      if (squad.suits.isEmpty()) {
         squad.cooldown = LegionRules.CALL_COOLDOWN;
         squad.plan.clear();
         sendStatus(owner, squad);
      }
   }

   // ---------------------------------------------------------------- key

   /** Ability key: orders while the Legion flies, else the suit selection (or the reason it cannot come). */
   public static void press(ServerPlayer player, IronManState state) {
      LegionSquad squad = squad(player);
      if (squad != null && squad.active()) {
         openMenu(player, state, squad, true);
         return;
      }

      Refusal refusal = refusal(player, state, squad);
      if (refusal != Refusal.NONE && refusal != Refusal.NO_SUITS) {
         refuse(player, refusal, squad);
         return;
      }

      openMenu(player, state, squad, false);
   }

   static void openMenu(ServerPlayer player, IronManState state, @Nullable LegionSquad squad, boolean active) {
      VeronicaView view = VeronicaView.of(state.roster, -1, VeronicaView.POD_STAYS);
      LegionOrder order = squad == null ? LegionOrder.GUARD : squad.order;
      LegionPriority priority = squad == null ? LegionPriority.NEAREST : squad.priority;
      List<MarkId> suggested = LegionCall.suggest(state.roster);
      int[] picks = new int[suggested.size()];
      for (int i = 0; i < picks.length; i++) {
         picks[i] = suggested.get(i).ordinal();
      }

      CoreMessages.sendToPlayer(new LegionMenuS2CPacket(view, active, order.ordinal(), priority.ordinal(), picks), player);
   }

   /** Every reason a new Legion cannot be called now (the menu is display only). */
   public static Refusal refusal(ServerPlayer player, IronManState state, @Nullable LegionSquad squad) {
      if (IronManMarks.controlled(player)) {
         return Refusal.CONTROLLED;
      }

      if (squad != null && squad.cooldown > 0) {
         return Refusal.COOLDOWN;
      }

      if (veronicaBusy(player, state)) {
         return Refusal.VERONICA_BUSY;
      }

      return LegionCall.suggest(state.roster).isEmpty() ? Refusal.NO_SUITS : Refusal.NONE;
   }

   /** Veronica is in the middle of something: a pod falling, parts flying, a suit being entered, the Hulkbuster assembling. */
   public static boolean veronicaBusy(ServerPlayer player, IronManState state) {
      VeronicaPodEntity pod = IronManVeronica.pod(player, state);
      if (pod != null && !pod.landed()) {
         return true;
      }

      return state.enteringSuitId >= 0 || state.suit.equipping() || state.suit.exiting() || state.hulkbuster.busy()
         || state.roster.at(MarkLocation.IN_DELIVERY) != null;
   }

   private static void refuse(ServerPlayer player, Refusal refusal, @Nullable LegionSquad squad) {
      Component message = switch (refusal) {
         case COOLDOWN -> Component.translatable("gui.viltrumitecore.legion.refuse_cooldown", (squad == null ? 0 : squad.cooldown + 19) / 20);
         case VERONICA_BUSY -> Component.translatable("gui.viltrumitecore.legion.refuse_busy");
         case NO_SUITS -> Component.translatable("gui.viltrumitecore.legion.refuse_none");
         default -> Component.translatable("gui.viltrumitecore.legion.refuse_controlled");
      };
      player.displayClientMessage(message, true);
   }

   // --------------------------------------------------------------- call

   /** The selection from the menu: up to three callable marks fly in. */
   public static void call(ServerPlayer player, int[] marks) {
      IronManState state = IronManState.of(player);
      if (state == null || !mayCommand(player)) {
         return;
      }

      LegionSquad squad = SQUADS.computeIfAbsent(player.getUUID(), k -> new LegionSquad());
      if (squad.active()) {
         // A second call while one flies: the first one stands (no double spawn).
         return;
      }

      Refusal refusal = refusal(player, state, squad);
      if (refusal != Refusal.NONE) {
         refuse(player, refusal, squad);
         return;
      }

      List<MarkId> chosen = LegionCall.validate(marks, state.roster);
      if (chosen.isEmpty()) {
         refuse(player, Refusal.NO_SUITS, squad);
         return;
      }

      ServerLevel level = player.serverLevel();
      squad.order = LegionOrder.GUARD;
      squad.priority = LegionPriority.NEAREST;
      squad.holdPoint = null;
      squad.orderedTargetId = -1;
      squad.announcedReady = false;
      squad.hadTargets = false;
      squad.plan.clear();
      squad.callYaw = player.getYRot();
      int n = chosen.size();
      for (int i = 0; i < n; i++) {
         MarkId mark = chosen.get(i);
         if (!state.roster.move(mark, MarkLocation.STORED, MarkLocation.LEGION)) {
            continue;
         }

         LegionSuitEntity suit = new LegionSuitEntity(ViltrumiteEntities.LEGION_SUIT.get(), level);
         float fraction = LegionRules.fraction(state.roster.durability(mark), state.roster.maxDurability(mark));
         suit.setup(player, mark, i, fraction);
         Vec3 slot = LegionSteering.slot(LegionOrder.GUARD, i, n, player.position(), player.getYRot(), level.getGameTime());
         suit.brain.arrive(slot, player.getYRot());
         Vec3 start = suit.brain.startPoint(level);
         suit.moveTo(start.x, start.y, start.z, player.getYRot() + 180.0F, 30.0F);
         suit.yBodyRot = suit.getYRot();
         suit.yHeadRot = suit.getYRot();
         suit.syncFraction(fraction);
         squad.suits.add(suit);
         level.addFreshEntity(suit);
      }

      if (squad.suits.isEmpty()) {
         return;
      }

      squad.event(LegionSquad.EVENT_ON);
      level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), IronManLegionSounds.SIGNAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
      sendStatus(player, squad);
   }

   /** Every suit of this call is on its slot. */
   static void onSuitReady(ServerPlayer owner, LegionSquad squad) {
      if (squad.announcedReady) {
         return;
      }

      for (LegionSuitEntity suit : squad.suits) {
         LegionPhase phase = suit.phase();
         if (phase == LegionPhase.APPROACH || phase == LegionPhase.BRAKE || phase == LegionPhase.READY) {
            return;
         }
      }

      squad.announcedReady = true;
      if (squad.suits.size() >= LegionRules.MAX_SUITS) {
         squad.event(LegionSquad.EVENT_THREE);
      }
   }

   // -------------------------------------------------------------- orders

   /**
    * An order from the command menu. {@code order} / {@code priority} -1 =
    * unchanged. ATTACK takes the entity under Tony's crosshair, HOLD the
    * point under it.
    */
   public static void order(ServerPlayer player, int orderId, int priorityId) {
      LegionSquad squad = squad(player);
      if (squad == null || !squad.active() || !mayCommand(player)) {
         return;
      }

      LegionPriority priority = LegionPriority.values().length > priorityId && priorityId >= 0 ? LegionPriority.values()[priorityId] : null;
      if (priority != null) {
         squad.priority = priority;
      }

      LegionOrder order = LegionOrder.byId(orderId);
      if (order != null && squad.order != LegionOrder.RETURN) {
         switch (order) {
            case ATTACK -> {
               Entity picked = pickEntity(player);
               squad.orderedTargetId = -1;
               if (picked instanceof LivingEntity living) {
                  ThreatScore.Relation relation = LegionAllies.relation(player, squad.suits.get(0), living);
                  if (relation == ThreatScore.Relation.OWNER || relation == ThreatScore.Relation.ALLY || relation == ThreatScore.Relation.PROTECTED
                     || relation == ThreatScore.Relation.PLAYER_NO_PVP) {
                     player.displayClientMessage(Component.translatable("gui.viltrumitecore.legion.target_refused"), true);
                  } else {
                     squad.orderedTargetId = living.getId();
                  }
               }
            }
            case HOLD -> squad.holdPoint = pickPoint(player);
            case RETURN -> {
               squad.event(LegionSquad.EVENT_RETURN);
               for (LegionSuitEntity suit : new ArrayList<>(squad.suits)) {
                  // a taken-over suit does not answer the recall (its controller jams it)
                  if (suit.brain != null && suit.phase() != LegionPhase.CRASH && !suit.hijacked()) {
                     suit.brain.leave(false);
                  }
               }
            }
            default -> squad.orderedTargetId = -1;
         }

         if (order != LegionOrder.HOLD) {
            squad.holdPoint = order == LegionOrder.ATTACK ? null : squad.holdPoint;
         }

         squad.order = order;
      }

      for (LegionSuitEntity suit : squad.suits) {
         suit.setOrderedTarget(squad.orderedTargetId);
      }

      squad.plan(player);
      squad.dirty = true;
      player.playNotifySound(IronManLegionSounds.ORDER.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
      sendStatus(player, squad);
   }

   /**
    * The same gate as the LEGION key (Codex review #5): an order or a call
    * packet re-checks it on the server, so a menu left open while Tony becomes
    * anchored, a spectator, locked or another hero sends nothing.
    */
   static boolean mayCommand(ServerPlayer player) {
      HeroDefinition hero = HeroRegistry.get(player);
      return commandAllowed(IronManState.of(player) != null && hero instanceof IronManHero, player.isAlive(),
         hero.canAct(player, HeroAction.LEGION), ActionLocks.locked(player), HeroHeldTargets.anchored(player));
   }

   static boolean commandAllowed(boolean ironMan, boolean alive, boolean canAct, boolean locked, boolean held) {
      return ironMan && alive && canAct && !locked && !held;
   }

   @Nullable
   static Entity pickEntity(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(LegionRules.ORDER_REACH));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      Vec3 limit = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
      AABB box = player.getBoundingBox().expandTowards(limit.subtract(eye)).inflate(1.5);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, limit, box,
         e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(e instanceof LegionSuitEntity), eye.distanceToSqr(limit));
      if (hit != null) {
         return hit.getEntity();
      }

      // A forgiving cone: the closest living thing near the ray.
      Entity best = null;
      double bestScore = 0.985;
      for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box.inflate(2.0),
         e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof LegionSuitEntity))) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         double cos = to.normalize().dot(player.getLookAngle());
         if (cos > bestScore && to.length() <= eye.distanceTo(limit) + 1.0) {
            bestScore = cos;
            best = e;
         }
      }

      return best;
   }

   static Vec3 pickPoint(ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getLookAngle().scale(LegionRules.ORDER_REACH));
      BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      return block.getType() == HitResult.Type.BLOCK ? block.getLocation().add(0.0, 0.1, 0.0) : player.position();
   }

   // ---------------------------------------------------------------- tick

   /** /hero reset-cooldowns. */
   public static void clearCooldown(ServerPlayer player, IronManState state) {
      LegionSquad squad = squad(player);
      if (squad != null) {
         squad.cooldown = 0;
         squad.dirty = true;
      }

      state.legionCooldown = 0;
   }

   /** Owner tick (IronManHero.tick): cooldown, coordinator rounds, HUD status. */
   public static void tick(ServerPlayer player, IronManState state) {
      LegionSquad squad = squad(player);
      state.legionCooldown = squad == null ? 0 : squad.cooldown;
      if (squad == null) {
         return;
      }

      squad.prune();
      if (!squad.active()) {
         if (squad.cooldown > 0) {
            squad.cooldown--;
            if (squad.cooldown == 0) {
               squad.dirty = true;
            }
         }

         if (squad.dirty) {
            sendStatus(player, squad);
         }

         return;
      }

      long now = player.level().getGameTime();
      if (LegionRules.squadDue(now, player.getUUID().hashCode())) {
         squad.plan(player);
         refreshFractions(state, squad);
      }

      if (squad.dirty || --squad.statusTimer <= 0) {
         sendStatus(player, squad);
      }
   }

   /** The roster is the truth: the bodies mirror it (repairs, resets). */
   private static void refreshFractions(IronManState state, LegionSquad squad) {
      for (LegionSuitEntity suit : squad.suits) {
         MarkId mark = suit.mark();
         if (suit.phase() != LegionPhase.CRASH && state.roster.location(mark) == MarkLocation.LEGION) {
            float fraction = LegionRules.fraction(state.roster.durability(mark), state.roster.maxDurability(mark));
            if (Math.abs(fraction - suit.fraction()) > 1.0E-3F) {
               suit.syncFraction(fraction);
               squad.dirty = true;
            }
         }
      }
   }

   static void sendStatus(ServerPlayer player, LegionSquad squad) {
      squad.dirty = false;
      squad.statusTimer = LegionRules.SQUAD_PERIOD;
      CoreMessages.sendToPlayer(LegionStatusS2CPacket.of(squad), player);
      squad.events = 0;
   }

   /** Tony was hit: remember the attacker and let the coordinator answer at once. */
   public static void onOwnerHurt(ServerPlayer player, DamageSource source) {
      LegionSquad squad = squad(player);
      if (squad == null || !squad.active()) {
         return;
      }

      Entity attacker = source.getEntity();
      if (attacker == null || attacker == player || attacker instanceof LegionSuitEntity) {
         return;
      }

      squad.noteOwnerHit(attacker, player.level().getGameTime());
      squad.plan(player);
   }

   // ----------------------------------------------------------- lifecycle

   /**
    * HeroDefinition.cleanup, before the state's own cleanup recalls the
    * roster: death, erase and hero change send the suits away in flight;
    * a logout removes them at once.
    */
   public static void onCleanup(ServerPlayer player, CleanupReason reason) {
      LegionSquad squad = squad(player);
      if (squad == null) {
         return;
      }

      if (reason == CleanupReason.DISCONNECT) {
         vanish(squad);
         SQUADS.remove(player.getUUID());
         return;
      }

      for (LegionSuitEntity suit : new ArrayList<>(squad.suits)) {
         if (suit.brain != null && suit.phase() != LegionPhase.CRASH) {
            suit.brain.leave(false);
         }
      }

      squad.order = LegionOrder.RETURN;
      squad.dirty = true;
      if (reason == CleanupReason.HERO_CHANGE) {
         squad.cooldown = 0;
      }
   }

   /** The owner changed dimension: the suits cannot follow; the marks go back to Veronica as they are. */
   public static void onDimensionChange(ServerPlayer player, IronManState state) {
      LegionSquad squad = squad(player);
      if (squad == null || !squad.active()) {
         return;
      }

      vanish(squad);
      state.roster.recallLegion();
      squad.cooldown = LegionRules.CALL_COOLDOWN;
      squad.dirty = true;
      sendStatus(player, squad);
   }

   private static void vanish(LegionSquad squad) {
      List<LegionSuitEntity> suits = new ArrayList<>(squad.suits);
      squad.suits.clear();
      squad.plan.clear();
      for (LegionSuitEntity suit : suits) {
         if (suit.brain != null) {
            suit.brain.abort();
         }

         suit.markReleased();
         suit.discard();
      }
   }

   /** Server stop: every squad is gone with its bodies (the roster was saved with LEGION → STORED by the cleanup). */
   public static void clear() {
      for (LegionSquad squad : SQUADS.values()) {
         vanish(squad);
      }

      SQUADS.clear();
   }
}
