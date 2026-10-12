package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.control.HeroControlSync;
import dev.baranhan.viltrumitecore.hero.regulus.Evangelium;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusHero;
import dev.baranhan.viltrumitecore.hero.regulus.RegulusState;
import dev.baranhan.viltrumitecore.item.ViltrumiteItems;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Hero lifecycle glue on the Forge bus: clone restore, death/disconnect
 * cleanup, world control ticking, fall handling and carrier bookkeeping.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroEvents {
   private HeroEvents() {
   }

   /**
    * Clone (respawn / end-return): restore the SAME hero session — identity
    * and session id — and copy all 18 ability slots. The hero totem refreshes
    * on a death respawn (one second life per life), never on an End return.
    */
   @SubscribeEvent
   public static void onPlayerClone(PlayerEvent.Clone event) {
      Player original = event.getOriginal();
      Player clone = event.getEntity();
      if (!(original instanceof HeroPlayer originalHero) || !(clone instanceof ServerPlayer newPlayer)) {
         return;
      }

      // Same session across respawns; the hero totem is a second life per
      // life, so dying refreshes it (a dimension return does not).
      HeroSession session = originalHero.getHeroSession();
      HeroRegistry.restoreHero(newPlayer, event.isWasDeath() && session != null ? session.refreshTotem() : session);
      // The hero decides what carries over to the clone (Regulus: cooldowns).
      HeroRegistry.get(newPlayer).cloneHeroState(original, newPlayer);
      // Keep the flight-grant marker with the mayfly it describes; the next sync settles it.
      if (newPlayer instanceof HeroPlayer cloneHero) {
         cloneHero.viltrumitecore$setMayflyGranted(originalHero.viltrumitecore$isMayflyGranted());
      }

      if (newPlayer instanceof ViltrumiteAbilityUser newAbility && original instanceof ViltrumiteAbilityUser oldAbility) {
         for (int slot = 0; slot < 18; slot++) {
            newAbility.setAbilityInSlot(slot, oldAbility.getAbilityInSlot(slot));
         }

         newAbility.setActivePage(oldAbility.getActivePage());
         newAbility.setOfferedAbilities(oldAbility.getOfferedAbilities());
         HeroRegistry.repairLoadout(newPlayer);
      }
   }

   @SubscribeEvent
   public static void onPlayerDeath(LivingDeathEvent event) {
      LivingEntity entity = event.getEntity();
      if (entity.level().isClientSide()) {
         return;
      }

      if (entity instanceof ServerPlayer player && player instanceof HeroPlayer heroPlayer) {
         // The hero's own cleanup runs before the death completes.
         HeldInputs.releaseAll(player);
         HeroRegistry.get(player).cleanup(player, CleanupReason.DEATH);
      }

      // A dead carrier costs its Regulus a heart (backlash handled inside).
      // The owner may be in a different dimension than the dying carrier, so
      // scan every online player — spec 5.3 burns the heart regardless.
      MinecraftServer server = entity.getServer();
      if (server != null) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RegulusState state = RegulusHero.stateOf(player);
            if (state != null && state.carriers.contains(entity.getUUID())) {
               dev.baranhan.viltrumitecore.hero.regulus.RegulusHearts.onCarrierDeath(player, state, entity.getUUID());
            }
         }
      }
   }

   /**
    * A carrier that leaves the level without dying (unload, despawn, dimension
    * change) drops the heart silently — no backlash (spec 5.3). Owners in
    * other dimensions are included: the carrier-level prune would read the
    * same departure as "gone" on its next scan either way.
    */
   @SubscribeEvent
   public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
      Entity entity = event.getEntity();
      if (!(entity instanceof LivingEntity)) {
         return;
      }

      MinecraftServer server = entity.getServer();
      if (server == null) {
         return;
      }

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         RegulusState state = RegulusHero.stateOf(player);
         if (state != null && state.carriers.contains(entity.getUUID())) {
            dev.baranhan.viltrumitecore.hero.regulus.RegulusHearts.onCarrierLost(player, state, entity.getUUID());
         }
      }
   }

   /**
    * Anchored victims (FREEZE/STASIS) cannot attack, use items or spawn
    * projectiles — spec 8.3/9.2 denies their own actions outright; PULL keeps
    * them (isAnchored covers only the anchoring kinds).
    */
   private static boolean anchored(Entity entity) {
      return entity instanceof LivingEntity living && HeroDamage.isAnchored(living);
   }

   @SubscribeEvent
   public static void onAttackEntity(AttackEntityEvent event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onArrowLoose(ArrowLooseEvent event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
      if (anchored(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   /** A frozen projectile rejoining a level after its record dropped gets its saved gravity back. */
   @SubscribeEvent
   public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
      if (event.getLevel().isClientSide()) {
         return;
      }

      // Deferred restores run first: a cancelled projectile join below must
      // still consume its pending gravity restore (a4 audit finding).
      Entity entity = event.getEntity();
      ControlManager.onEntityJoinLevel(entity);
      // Lift leases (Reid draw): a live lease lifts again, an orphaned tag restores the original noGravity.
      if (LiftLeases.SERVER.holds(entity.getUUID()) || entity.getPersistentData().contains(LiftLeases.TAG)) {
         LiftLeases.SERVER.onJoin(LiftLeases.body(entity));
      }

      // Projectiles released while the owner is anchored never spawn — the
      // last deny layer for crossbow/trident throws past the item-use gates.
      if (entity instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner && HeroDamage.isAnchored(owner)) {
         event.setCanceled(true);
      }
   }

   /** The bound Evangelium never drops on death. */
   @SubscribeEvent
   public static void onLivingDrops(LivingDropsEvent event) {
      if (event.getEntity() instanceof Player) {
         event.getDrops().removeIf(drop -> drop.getItem().getItem() == ViltrumiteItems.EVANGELIUM.get());
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         HeldInputs.releaseAll(player);
         HeroDamageLayers.forget(player);
         HeroAirJump.forget(player);
         HeroResourceRegen.forget(player);
         HeroRegistry.get(player).cleanup(player, CleanupReason.DISCONNECT);
      }
   }

   // Control-state baselines: a client sees the whole world snapshot on join,
   // respawn and dimension change; mutations re-broadcast from the tick.
   @SubscribeEvent
   public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         HeroControlSync.sendBaseline(player);
         ControlManager.restorePendingTarget(player);
         HeroFlightGrant.sync(player);
         if (player instanceof HeroPlayer heroPlayer && heroPlayer.viltrumitecore$consumeLegacyLoadout()) {
            HeroRegistry.resetLoadout(player);
         } else {
            HeroRegistry.repairLoadout(player);
         }
      }
   }

   /**
    * Guard seam: a hero that owns the swap-hands key cancels the vanilla swap
    * on the server too, so a forged swap packet does nothing.
    */
   @SubscribeEvent
   public static void onSwapHands(net.minecraftforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
      if (event.getEntity() instanceof ServerPlayer player && HeroRegistry.get(player).blocksHandSwap(player)) {
         event.setCanceled(true);
      }
   }

   /** Damage layers, step 1: absorbIncoming before armor and knockback (HeroDamageLayers). */
   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
   public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F && HeroDamageLayers.onAttack(player, event.getSource(), event.getAmount())) {
         event.setCanceled(true);
      }
   }

   /** Damage layers: partial absorb of this hit and the hero attacker's outgoing hook. */
   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
   public static void onLivingHurtLayers(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
      if (!event.getEntity().level().isClientSide() && event.getAmount() > 0.0F) {
         event.setAmount(HeroDamageLayers.onHurt(event.getEntity(), event.getSource(), event.getAmount()));
      }
   }

   /** Damage layers, last step: clampFinalDamage after armor, enchantments and Resistance. */
   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
   public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
         event.setAmount(HeroDamageLayers.onDamage(player, event.getSource(), event.getAmount()));
      }
   }

   /** Damage hook for every hero (HeroDefinition.onHurt). */
   @SubscribeEvent
   public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
         HeroRegistry.get(player).onHurt(player, event.getSource(), event.getAmount());
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         HeroControlSync.sendBaseline(player);
         ControlManager.restorePendingTarget(player);
         HeroFlightGrant.sync(player);
         HeroRegistry.resendOwnerSnapshot(player);
         HeroRegistry.get(player).onRespawn(player);
         // The bound Evangelium returns on respawn when it is missing (spec 11.2).
         if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.REGULUS) {
            Evangelium.grant(player);
            // Respawn is a fresh body: re-apply the chassis and start full.
            dev.baranhan.viltrumitecore.hero.regulus.RegulusPassives.apply(player);
            player.setHealth(player.getMaxHealth());
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         HeroControlSync.sendBaseline(player);
         ControlManager.restorePendingTarget(player);
         HeroRegistry.get(player).onDimensionChange(player);
         HeroRegistry.resendOwnerSnapshot(player);
      }
   }

   /** Landing hook for every hero (HeroDefinition.onLanded / cancelsFallDamage). */
   @SubscribeEvent
   public static void onLivingFall(LivingFallEvent event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }

      HeroDefinition hero = HeroRegistry.get(player);
      // The only place the real landing distance is still known: vanilla
      // resets fallDistance on this same tick, before the hero tick runs.
      if (player instanceof ServerPlayer serverPlayer) {
         hero.onLanded(serverPlayer, event.getDistance());
      }

      if (hero.cancelsFallDamage(player)) {
         event.setCanceled(true);
      }
   }

   /** Server player tick END: shared per-player systems that tick for every hero (online time only). */
   @SubscribeEvent
   public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
         PersistentCooldowns.tick(player);
         HeroAirJump.tick(player);
      }
   }

   @SubscribeEvent
   public static void onEffectApplicable(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
      if (event.getEntity() instanceof Player player && HeroRegistry.get(player).blocksEffect(player, event.getEffectInstance())) {
         event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
      }
   }

   @SubscribeEvent
   public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
      HeroProjectiles.onImpact(event);
   }

   /** Lift leases never outlive the server; the persistent tags restore the entities on the next start. */
   @SubscribeEvent
   public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
      LiftLeases.SERVER.clear();
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (!(event.level instanceof ServerLevel level)) {
         return;
      }

      if (event.phase == TickEvent.Phase.END) {
         HeroProjectiles.tickPending(level);
         HeroResourceRegen.flush(level);
         return;
      }

      ControlManager.get(level).tick(level);
   }
}
