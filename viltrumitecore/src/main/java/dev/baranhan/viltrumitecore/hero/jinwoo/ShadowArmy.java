package dev.baranhan.viltrumitecore.hero.jinwoo;

import dev.baranhan.viltrumitecore.entity.ViltrumiteEntities;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowMode;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.SafeSpot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The army side of Jinwoo (spec §5.10-5.13, §6): spawning from the roster, the
 * staggered summon, recall, orders, sentinels, the Monarch's Charge and every
 * cleanup path. Shadows are real entities; the roster is the source of truth.
 */
public final class ShadowArmy {
   private ShadowArmy() {
   }

   // --- geometry ---

   /** A point {@code right} blocks to the owner's right and {@code back} blocks behind him. */
   public static Vec3 local(Entity owner, double right, double back) {
      float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
      double fx = -Mth.sin(yaw);
      double fz = Mth.cos(yaw);
      double rx = -fz;
      double rz = fx;
      return owner.position().add(rx * right - fx * back, 0, rz * right - fz * back);
   }

   // --- lookups ---

   public static List<ShadowSoldierEntity> active(ServerPlayer owner, JinwooState state) {
      List<ShadowSoldierEntity> out = new ArrayList<>();
      ServerLevel level = owner.serverLevel();
      for (ShadowRoster.Entry entry : state.roster.entries()) {
         if (entry.active != null && level.getEntity(entry.active) instanceof ShadowSoldierEntity s && s.isAlive() && !s.dissolving()) {
            out.add(s);
         }
      }
      return out;
   }

   /** Active shadows that follow orders (not sentinels). */
   public static List<ShadowSoldierEntity> squad(ServerPlayer owner, JinwooState state) {
      List<ShadowSoldierEntity> out = active(owner, state);
      out.removeIf(s -> s.mode() == ShadowMode.SENTINEL);
      return out;
   }

   public static int guardCount(ServerPlayer owner) {
      JinwooState state = JinwooState.of(owner);
      return state == null ? 0 : squad(owner, state).size();
   }

   public static boolean stillOwned(ServerPlayer owner, JinwooState state, ShadowSoldierEntity shadow) {
      ShadowRoster.Entry entry = state.roster.byActive(shadow.getUUID());
      return entry != null && entry.serial == shadow.serial;
   }

   /** Entity death / dissolve / unload: the roster entry goes on revive cooldown (died) or is free. */
   public static void onShadowGone(ShadowSoldierEntity shadow, boolean died) {
      ServerPlayer owner = shadow.owner();
      JinwooState state = owner == null ? null : JinwooState.of(owner);
      if (state == null) {
         return;
      }
      ShadowRoster.Entry entry = state.roster.byActive(shadow.getUUID());
      if (entry != null) {
         if (died) {
            state.roster.fell(entry);
         } else {
            state.roster.released(entry);
         }
      }
      state.sentinels.remove(shadow.getUUID());
      if (!died) {
         JinwooSounds.play(shadow, JinwooSounds.SHADOW_DISSOLVE, 0.6F, 0.9F + shadow.getRandom().nextFloat() * 0.2F);
      } else {
         JinwooSounds.play(shadow, JinwooSounds.SHADOW_DISSOLVE, 0.9F, 0.7F);
      }
   }

   // --- spawning ---

   @Nullable
   public static ShadowSoldierEntity spawn(ServerPlayer owner, JinwooState state, ShadowRoster.Entry entry, Vec3 at, int slot) {
      ServerLevel level = owner.serverLevel();
      if (!entry.ready() || state.roster.activeCount() >= JinwooRules.ARMY_ACTIVE || ShadowSoldierEntity.live(level) >= JinwooRules.ARMY_LEVEL) {
         return null;
      }
      ShadowSoldierEntity shadow = ViltrumiteEntities.SHADOW_SOLDIER.get().create(level);
      if (shadow == null) {
         return null;
      }
      Vec3 spot = SafeSpot.find(at, new SafeSpot.LevelProbe(level, shadow), 3.0, 1.0).orElse(owner.position());
      shadow.moveTo(spot.x, spot.y, spot.z, owner.getYRot(), 0.0F);
      shadow.setYHeadRot(owner.getYRot());
      shadow.setYBodyRot(owner.getYRot());
      shadow.setup(owner, entry.serial, entry.kind, entry.elite, slot);
      shadow.setMode(modeFor(state));
      if (!level.addFreshEntity(shadow)) {
         return null;
      }
      entry.active = shadow.getUUID();
      JinwooSounds.play(shadow, JinwooSounds.SHADOW_RISE, 0.8F, entry.elite ? 0.8F : 1.0F + shadow.getRandom().nextFloat() * 0.15F);
      return shadow;
   }

   private static ShadowMode modeFor(JinwooState state) {
      return switch (state.order) {
         case ATTACK -> ShadowMode.ATTACK;
         case GUARD -> ShadowMode.GUARD;
         default -> ShadowMode.FOLLOW;
      };
   }

   /** Ring point around the owner for the n-th staggered rise. */
   static Vec3 ringPoint(ServerPlayer owner, int index, int count, double radius) {
      double a = owner.getYRot() * Mth.DEG_TO_RAD + Math.PI * 2.0 * index / Math.max(1, count) + Math.PI;
      return owner.position().add(-Math.sin(a) * radius, 0, Math.cos(a) * radius);
   }

   // --- Summon / Recall (spec §5.10) ---

   /** Returns false = refused (nothing to summon, no mana). */
   public static boolean summonOrRecall(ServerPlayer owner, JinwooState state) {
      List<ShadowSoldierEntity> active = active(owner, state);
      if (!active.isEmpty() || !state.summonQueue.isEmpty()) {
         state.summonQueue.clear();
         recall(owner, state, active);
         return true;
      }
      int room = Math.min(JinwooRules.ARMY_ACTIVE, JinwooRules.ARMY_LEVEL - ShadowSoldierEntity.live(owner.serverLevel()));
      int affordable = (int)Math.floor((state.mana + 1.0E-3F) / JinwooRules.SUMMON_MANA);
      List<ShadowRoster.Entry> order = state.roster.summonOrder(Math.min(room, affordable));
      if (order.isEmpty()) {
         return false;
      }
      for (ShadowRoster.Entry entry : order) {
         state.summonQueue.add(entry.serial);
      }
      state.summonDelay = 0;
      state.order = JinwooState.ArmyOrder.FOLLOW;
      JinwooSounds.play(owner, JinwooSounds.SUMMON, 1.0F, 1.0F);
      return true;
   }

   public static void recall(ServerPlayer owner, JinwooState state, List<ShadowSoldierEntity> active) {
      for (ShadowSoldierEntity s : active) {
         JinwooFx.around(owner, JinwooFx.STREAM, 0, s.position(), owner.position());
         s.dissolve();
      }
      state.sentinels.clear();
      JinwooSounds.play(owner, JinwooSounds.RECALL, 1.0F, 1.0F);
   }

   /** One server tick of the army side: staggered rises, revive timers, attacker memory. */
   public static void tick(ServerPlayer owner, JinwooState state) {
      state.roster.tick();
      if (state.lastAttackerTicks > 0) {
         state.lastAttackerTicks--;
      }
      if (!state.summonQueue.isEmpty() && --state.summonDelay <= 0) {
         state.summonDelay = JinwooRules.SUMMON_STAGGER;
         int serial = state.summonQueue.remove(0);
         ShadowRoster.Entry entry = state.roster.bySerial(serial);
         if (entry != null && state.spend(JinwooRules.SUMMON_MANA)) {
            int index = state.roster.activeCount();
            Vec3 at = ringPoint(owner, index, Math.max(4, Math.min(JinwooRules.ARMY_ACTIVE, index + 1 + state.summonQueue.size())), 2.6 + (index % 2) * 0.8);
            ShadowSoldierEntity s = spawn(owner, state, entry, at, index);
            if (s != null) {
               JinwooFx.around(owner, JinwooFx.STREAM, 1, owner.position(), s.position());
            }
         } else {
            state.summonQueue.clear();
         }
      }
      if (owner.tickCount % 10 == 0) {
         reslot(owner, state);
      }
   }

   /** Dense formation slots in roster order. */
   static void reslot(ServerPlayer owner, JinwooState state) {
      int i = 0;
      for (ShadowSoldierEntity s : squad(owner, state)) {
         s.slot = i++;
      }
   }

   /** Every active shadow dissolves (death, logout, hero/dimension change, spec §8). */
   public static void dissolveAll(ServerPlayer owner, JinwooState state) {
      state.summonQueue.clear();
      Set<UUID> ids = new HashSet<>();
      for (ShadowRoster.Entry entry : state.roster.entries()) {
         if (entry.active != null) {
            ids.add(entry.active);
         }
      }
      if (owner.getServer() != null) {
         for (ServerLevel level : owner.getServer().getAllLevels()) {
            for (UUID id : ids) {
               if (level.getEntity(id) instanceof ShadowSoldierEntity s) {
                  s.dissolve();
                  if (owner.level() != level || owner.hasDisconnected()) {
                     s.discard();
                  }
               }
            }
         }
      }
      state.roster.clearActive();
      state.sentinels.clear();
      state.order = JinwooState.ArmyOrder.FOLLOW;
   }

   // --- orders (spec §5.11, §5.12) ---

   /** In the Domain the eyes flash on every order. */
   private static void eyes(List<ShadowSoldierEntity> squad) {
      for (ShadowSoldierEntity s : squad) {
         s.level().broadcastEntityEvent(s, ShadowSoldierEntity.EVENT_EYES);
      }
   }

   public static boolean commandAttack(ServerPlayer owner, JinwooState state) {
      List<ShadowSoldierEntity> squad = squad(owner, state);
      if (squad.isEmpty()) {
         return false;
      }
      LivingEntity target = JinwooTargets.lookEntity(owner, LivingEntity.class, JinwooRules.COMMAND_RANGE, JinwooRules.COMMAND_TOLERANCE,
         e -> e.isAlive() && !(e instanceof ShadowSoldierEntity) && !e.isAlliedTo(owner) && !(e instanceof AbstractVillager) && !(e instanceof IronGolem));
      Vec3 point = null;
      if (target == null) {
         point = JinwooTargets.lookBlock(owner, JinwooRules.COMMAND_RANGE);
         if (point == null) {
            return false;
         }
      }
      state.order = JinwooState.ArmyOrder.ATTACK;
      state.orderTargetId = target == null ? -1 : target.getId();
      state.orderPoint = point;
      for (ShadowSoldierEntity s : squad) {
         s.brain.reset();
         s.setMode(ShadowMode.ATTACK);
      }
      eyes(squad);
      Vec3 at = target != null ? target.position().add(0, target.getBbHeight() + 0.4, 0) : point;
      JinwooFx.around(owner, JinwooFx.COMMAND, target == null ? -1 : target.getId(), at, new Vec3(0, 0, 0));
      JinwooSounds.play(owner, JinwooSounds.COMMAND_ATTACK, 1.0F, 1.0F);
      return true;
   }

   public static boolean commandGuard(ServerPlayer owner, JinwooState state) {
      List<ShadowSoldierEntity> squad = squad(owner, state);
      if (squad.isEmpty()) {
         return false;
      }
      boolean on = state.order != JinwooState.ArmyOrder.GUARD;
      state.order = on ? JinwooState.ArmyOrder.GUARD : JinwooState.ArmyOrder.FOLLOW;
      state.orderTargetId = -1;
      state.orderPoint = null;
      for (ShadowSoldierEntity s : squad) {
         s.brain.reset();
         s.setMode(on ? ShadowMode.GUARD : ShadowMode.FOLLOW);
      }
      eyes(squad);
      JinwooFx.around(owner, JinwooFx.COMMAND, owner.getId(), owner.position(), new Vec3(on ? 1 : 3, 0, 0));
      JinwooSounds.play(owner, JinwooSounds.COMMAND_GUARD, 1.0F, on ? 1.0F : 0.85F);
      return true;
   }

   /** True when Jinwoo takes the guard reduction now (≥ 3 shadows close, spec §5.12). */
   public static boolean guarded(ServerPlayer owner, JinwooState state) {
      if (state.order != JinwooState.ArmyOrder.GUARD) {
         return false;
      }
      int near = 0;
      for (ShadowSoldierEntity s : squad(owner, state)) {
         if (s.distanceToSqr(owner) <= (JinwooRules.GUARD_RADIUS + 2.5) * (JinwooRules.GUARD_RADIUS + 2.5)) {
            near++;
         }
      }
      return near >= JinwooRules.GUARD_MIN;
   }

   /** Valid ward: villager, player, iron golem (or a bell block position is not an entity: decision, wards are creatures). */
   public static boolean wardable(LivingEntity e, ServerPlayer owner) {
      return e instanceof AbstractVillager || e instanceof IronGolem || (e instanceof Player p && p != owner);
   }

   public static boolean commandSentinel(ServerPlayer owner, JinwooState state) {
      LivingEntity ward = JinwooTargets.lookEntity(owner, LivingEntity.class, JinwooRules.COMMAND_RANGE, JinwooRules.COMMAND_TOLERANCE,
         e -> e.isAlive() && wardable(e, owner));
      if (ward == null) {
         return bellSentinel(owner, state);
      }
      // Repeat on the same ward removes its sentinel.
      for (Iterator<Map.Entry<UUID, UUID>> it = state.sentinels.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<UUID, UUID> e = it.next();
         if (e.getValue().equals(ward.getUUID())) {
            it.remove();
            if (owner.serverLevel().getEntity(e.getKey()) instanceof ShadowSoldierEntity s) {
               s.ward = null;
               s.brain.reset();
               s.setMode(modeFor(state));
               s.setHidden(false);
               s.level().broadcastEntityEvent(s, ShadowSoldierEntity.EVENT_RISE);
            }
            JinwooFx.around(owner, JinwooFx.COMMAND, ward.getId(), ward.position(), new Vec3(3, 0, 0));
            JinwooSounds.play(owner, JinwooSounds.COMMAND_SENTINEL, 0.9F, 0.8F);
            return true;
         }
      }
      if (state.sentinels.size() >= JinwooRules.SENTINEL_MAX) {
         return false;
      }
      List<ShadowSoldierEntity> squad = squad(owner, state);
      if (squad.isEmpty()) {
         return false;
      }
      ShadowSoldierEntity pick = squad.get(squad.size() - 1);
      for (ShadowSoldierEntity s : squad) {
         if (!s.elite()) {
            pick = s;
         }
      }
      state.sentinels.put(pick.getUUID(), ward.getUUID());
      pick.ward = ward.getUUID();
      pick.brain.reset();
      pick.setMode(ShadowMode.SENTINEL);
      pick.setHidden(true);
      JinwooFx.around(owner, JinwooFx.STREAM, 1, pick.position(), ward.position());
      pick.teleportTo(ward.getX(), ward.getY(), ward.getZ());
      JinwooFx.around(owner, JinwooFx.COMMAND, ward.getId(), ward.position(), new Vec3(2, 0, 0));
      JinwooSounds.play(owner, JinwooSounds.COMMAND_SENTINEL, 1.0F, 1.0F);
      return true;
   }

   /** A village bell under the crosshair: the sentinel guards that point (spec §5.12). */
   private static boolean bellSentinel(ServerPlayer owner, JinwooState state) {
      net.minecraft.world.phys.BlockHitResult hit = owner.level().clip(new net.minecraft.world.level.ClipContext(owner.getEyePosition(),
         owner.getEyePosition().add(owner.getLookAngle().scale(JinwooRules.COMMAND_RANGE)), net.minecraft.world.level.ClipContext.Block.OUTLINE,
         net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
      if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
         || !owner.level().getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.BELL)) {
         return false;
      }
      net.minecraft.core.BlockPos pos = hit.getBlockPos();
      UUID key = UUID.nameUUIDFromBytes(("bell:" + pos.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      Vec3 point = Vec3.atBottomCenterOf(pos);
      for (Iterator<Map.Entry<UUID, UUID>> it = state.sentinels.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<UUID, UUID> e = it.next();
         if (e.getValue().equals(key)) {
            it.remove();
            if (owner.serverLevel().getEntity(e.getKey()) instanceof ShadowSoldierEntity s) {
               dropSentinel(owner, state, s);
            }
            JinwooSounds.play(owner, JinwooSounds.COMMAND_SENTINEL, 0.9F, 0.8F);
            return true;
         }
      }
      List<ShadowSoldierEntity> squad = squad(owner, state);
      if (state.sentinels.size() >= JinwooRules.SENTINEL_MAX || squad.isEmpty()) {
         return false;
      }
      ShadowSoldierEntity pick = squad.get(squad.size() - 1);
      state.sentinels.put(pick.getUUID(), key);
      pick.ward = key;
      pick.wardPoint = point;
      pick.brain.reset();
      pick.setMode(ShadowMode.SENTINEL);
      pick.setHidden(true);
      JinwooFx.around(owner, JinwooFx.STREAM, 1, pick.position(), point);
      SafeSpot.find(point, new SafeSpot.LevelProbe(owner.level(), pick), 3.0, 1.0).ifPresent(p -> pick.teleportTo(p.x, p.y, p.z));
      JinwooFx.around(owner, JinwooFx.COMMAND, -1, point.add(0, 1.2, 0), new Vec3(2, 0, 0));
      JinwooSounds.play(owner, JinwooSounds.COMMAND_SENTINEL, 1.0F, 1.0F);
      return true;
   }

   public static void dropSentinel(ServerPlayer owner, JinwooState state, ShadowSoldierEntity s) {
      state.sentinels.remove(s.getUUID());
      s.ward = null;
      s.wardPoint = null;
      s.brain.reset();
      s.setHidden(false);
      s.setMode(modeFor(state));
   }

   /** Exchange partner: the shadow under the crosshair, else the farthest sentinel in this dimension (spec §5.8). */
   @Nullable
   public static ShadowSoldierEntity exchangePartner(ServerPlayer owner, JinwooState state) {
      ShadowSoldierEntity aimed = JinwooTargets.lookEntity(owner, ShadowSoldierEntity.class, JinwooRules.EXCHANGE_RANGE, 1.0,
         s -> owner.getUUID().equals(s.ownerUuid) && !s.dissolving());
      if (aimed != null) {
         return aimed;
      }
      ShadowSoldierEntity best = null;
      double bestD = -1;
      for (ShadowSoldierEntity s : active(owner, state)) {
         if (s.mode() == ShadowMode.SENTINEL && s.distanceToSqr(owner) > bestD) {
            best = s;
            bestD = s.distanceToSqr(owner);
         }
      }
      return best;
   }

   /** Ult wave and Domain: every active shadow back to the fight. */
   public static void rally(ServerPlayer owner, JinwooState state) {
      List<ShadowSoldierEntity> squad = squad(owner, state);
      eyes(squad);
   }

   /** Allies of Jinwoo never take his area hits (spec §7). */
   public static boolean ally(ServerPlayer owner, LivingEntity e) {
      return e instanceof ShadowSoldierEntity s && owner.getUUID().equals(s.ownerUuid) || e.isAlliedTo(owner) || owner.isAlliedTo(e) || HeroDamage.isProtected(e);
   }
}
