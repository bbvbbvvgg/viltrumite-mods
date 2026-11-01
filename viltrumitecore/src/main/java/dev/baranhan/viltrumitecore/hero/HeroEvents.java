package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.hero.control.ControlManager;
import dev.baranhan.viltrumitecore.hero.reinhard.limbo.Limbo;
import dev.baranhan.viltrumitecore.hero.control.WorldPause;
import dev.baranhan.viltrumitecore.hero.control.HeroControlSync;
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

   /**
    * World Pause overlay (server): a paused hostile held by a foreign control does
    * not act or move until the pause ends; the foreign control is left intact.
    */
   @SubscribeEvent
   public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
      LivingEntity entity = event.getEntity();
      if (!entity.level().isClientSide() && dev.baranhan.viltrumitecore.hero.control.WorldPause.suspendsMob(entity)) {
         entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onPlayerDeath(LivingDeathEvent event) {
      LivingEntity entity = event.getEntity();
      if (entity.level().isClientSide()) {
         return;
      }
      dev.baranhan.viltrumitecore.entity.avengers.TeamDamage.onDeath(entity, event.getSource());

      if (entity instanceof ServerPlayer player && player instanceof HeroPlayer heroPlayer) {
         WorldPause.ownerLeft(player.getServer(),player.getUUID());
         // The hero's own cleanup runs before the death completes.
         HeldInputs.releaseAll(player);
         HeroRegistry.get(player).cleanup(player, CleanupReason.DEATH);
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
      if (anchored(event.getEntity()) || !HeroRegistry.get(event.getEntity()).allowsVanillaAttack(event.getEntity())) {
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
         Limbo.onLogout(player);
         WorldPause.onLeave(player);
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
         dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(
            new dev.baranhan.viltrumitecore.network.packet.ServerRulesS2CPacket(player.server.isPvpAllowed()), player);
         HeroControlSync.sendBaseline(player);
         ControlManager.restorePendingTarget(player);
         HeroFlightGrant.sync(player);
         if (player instanceof HeroPlayer heroPlayer && heroPlayer.viltrumitecore$consumeLegacyLoadout()) {
            HeroRegistry.resetLoadout(player);
         } else {
            HeroRegistry.repairLoadout(player);
         }
         WorldPause.onJoin(player);
         Limbo.onLogin(player);
      }
   }

   /**
    * Guard seam: a hero that owns the swap-hands key cancels the vanilla swap
    * on the server too, so a forged swap packet does nothing.
    */
   @SubscribeEvent
   public static void onSwapHands(net.minecraftforge.event.entity.living.LivingSwapItemsEvent.Hands event) {
      if (event.getEntity() instanceof ServerPlayer player && (HeroDamage.isAnchored(player) || HeroRegistry.get(player).blocksHandSwap(player))) {
         event.setCanceled(true);
      }
   }

   /** Damage layers, step 1: absorbIncoming before armor and knockback (HeroDamageLayers). */
   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
   public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
      if (HeroDamage.isProtected(event.getEntity())) {event.setCanceled(true); return;}
      if (event.getAmount() > 0.0F) {
         HeroDamageLayers.notifyAttack(event.getEntity(), event.getSource());
      }
      // Contextual interactions (interaction/): a special matchup may consume the hit before any layer sees it.
      if (event.getAmount() > 0.0F && dev.baranhan.viltrumitecore.interaction.ContextualInteractions.onAttack(event.getEntity(), event.getSource(), event.getAmount())) {
         event.setCanceled(true);
         return;
      }
      if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F && HeroDamageLayers.onAttack(player, event.getSource(), event.getAmount())) {
         event.setCanceled(true);
      }
   }

   /** Damage layers: partial absorb of this hit and the hero attacker's outgoing hook. */
   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
   public static void onLivingHurtLayers(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
      if (!event.getEntity().level().isClientSide() && event.getAmount() > 0.0F) {
         event.setAmount(dev.baranhan.viltrumitecore.interaction.ContextualInteractions.onHurt(event.getEntity(), event.getSource(), event.getAmount()));
         event.setAmount(HeroDamageLayers.onHurt(event.getEntity(), event.getSource(), event.getAmount()));
         // Global balance model: summoned groups and the raw amount a hero's PvE profile reads.
         event.setAmount(dev.baranhan.viltrumitecore.balance.CombatBalance.onHurt(event.getEntity(), event.getSource(), event.getAmount()));
      }
   }

   /** Damage layers, last step: clampFinalDamage after armor, enchantments and Resistance. */
   @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
   public static void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F) {
         event.setAmount(HeroDamageLayers.onDamage(player, event.getSource(), event.getAmount()));
         dev.baranhan.viltrumitecore.hero.speech.HeroLineEvents.damaged(player, event.getAmount());
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
      if (event.getEntity() instanceof ServerPlayer player) freshBody(player);
   }

   /** Used by normal respawn and same-entity returns from an erased body. */
   public static void freshBody(ServerPlayer player) {
      BodyLoss.fresh(new BodyLoss.FreshBody() {
         public void controlBaseline() {HeroControlSync.sendBaseline(player);}
         public void restorePendingFlags() {ControlManager.restorePendingTarget(player);}
         public void flightSync() {HeroFlightGrant.sync(player);}
         public void resendOwner() {HeroRegistry.resendOwnerSnapshot(player);}
         public void heroRespawn() {HeroRegistry.get(player).onRespawn(player);}
         public void legacyRespawn() {
         }
      });
   }

   /** Death without die()/clone()/drops. Must run before the limbo transfer. */
   public static void prepareErasure(ServerPlayer player) {
      BodyLoss.erase(new BodyLoss.ErasedBody() {
         public void cleanupHero(CleanupReason reason) {HeroRegistry.get(player).cleanup(player, reason);}
         public void clearHeldInputs() {HeldInputs.releaseAll(player);}
         public void releaseControls() {
            MinecraftServer server=player.getServer();
            if(server!=null) for(ServerLevel level:server.getAllLevels()) {
               ControlManager.get(level).cleanupCaster(player.getUUID(), CleanupReason.ERASED);
               ControlManager.get(level).cleanupTarget(player.getUUID());
            }
            ControlManager.releaseProjectilesFor(player.getUUID());
            ControlManager.restorePendingTarget(player);
         }
         public void releaseLegacyGrabs() {
            if(player instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer core) LegacyKit.resetActions(core);
            MinecraftServer server=player.getServer();
            if(server!=null) for(ServerPlayer other:server.getPlayerList().getPlayers()) {
               if(other instanceof dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer otherCore && otherCore.getGrabbedTarget()!=null
                  && otherCore.getGrabbedTarget().getUUID().equals(player.getUUID())) otherCore.releaseTarget();
            }
            player.removeTag("ViltrumiteGrabbed");
            dev.baranhan.viltrumitecore.network.CoreMessages.sendToPlayer(
               new dev.baranhan.viltrumitecore.network.packet.PlayerGrabStateSyncS2CPacket(false,null),player);
         }
         public void resetMotionAndSession() {
            if(player instanceof dev.baranhan.viltrumiteflight.util.ViltrumiteFlightPlayer flight) flight.stopFlight();
            player.stopUsingItem(); player.stopRiding(); player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.setNoGravity(false); player.fallDistance=0; player.clearFire(); player.removeAllEffects();
            player.invulnerableTime=0; player.hurtTime=0; player.deathTime=0;
            HeroDamageLayers.forget(player); HeroAirJump.forget(player); HeroResourceRegen.forget(player);
            if(player instanceof HeroPlayer hero && hero.getHeroSession()!=null)
               hero.viltrumitecore$setHeroSession(hero.getHeroSession().refreshTotem());
         }
         public void clearOwnerSections() {HeroRegistry.clearOwnerSections(player, OwnerSection.values());}
         public void sync() {HeroFlightGrant.sync(player); HeroRegistry.syncSnapshot(player); HeroRegistry.resendOwnerSnapshot(player);}
      });
   }

   @SubscribeEvent
   public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         WorldPause.ownerLeft(player.getServer(),player.getUUID());
         WorldPause.onJoin(player);
         HeroControlSync.sendBaseline(player);
         ControlManager.restorePendingTarget(player);
         HeroRegistry.get(player).onDimensionChange(player);
         HeroRegistry.resendOwnerSnapshot(player);
      }
   }

   @SubscribeEvent
   public static void onTravel(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
      if(event.getEntity() instanceof ServerPlayer player && Limbo.blocksTravel(BodyIsolation.active(player), Limbo.internalTravel())) event.setCanceled(true);
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
         Limbo.tick(player);
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
      if (dev.baranhan.viltrumitecore.interaction.ContextualInteractions.onProjectile(event.getProjectile(), event.getRayTraceResult())) {
         event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
         return;
      }
      HeroProjectiles.onImpact(event);
   }

   /** Lift leases never outlive the server; the persistent tags restore the entities on the next start. */
   @SubscribeEvent
   public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
      LiftLeases.SERVER.clear();
      dev.baranhan.viltrumitecore.interaction.ContextualInteractions.reset();
   }

   @SubscribeEvent
   public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
      WorldPause.end(event.getServer(),WorldPause.EndReason.SERVER_STOP);
      ServerEndTasks.clear(event.getServer());
      Limbo.clear();
      dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutManager.clearAll();
      dev.baranhan.viltrumitecore.hero.reinhard.saw.SawSphereManager.clearAll();
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if(event.phase==TickEvent.Phase.END) {WorldPause.tick(event.getServer()); ServerEndTasks.flush(event.getServer());}
   }

   @SubscribeEvent
   public static void onDatapackSync(net.minecraftforge.event.OnDatapackSyncEvent event) {
      if(event.getPlayer()==null) WorldPause.end(event.getPlayerList().getServer(),WorldPause.EndReason.RELOAD);
   }

   @SubscribeEvent
   public static void onLevelTick(TickEvent.LevelTickEvent event) {
      if (!(event.level instanceof ServerLevel level)) {
         return;
      }

      for (LevelTickPhases.System system : LevelTickPhases.systemsFor(event.phase)) {
         switch (system) {
            case CONTROL -> ControlManager.get(level).tick(level);
            case PROJECTILES -> HeroProjectiles.tickPending(level);
            case RESOURCE_REGEN -> HeroResourceRegen.flush(level);
            case CARVE -> CarveJobs.tick(level);
            case WORLD_CUT -> dev.baranhan.viltrumitecore.hero.reinhard.cut.WorldCutManager.get(level).tick(level);
            case SAW_SPHERE -> dev.baranhan.viltrumitecore.hero.reinhard.saw.SawSphereManager.get(level).tick();
         }
      }
   }
}
