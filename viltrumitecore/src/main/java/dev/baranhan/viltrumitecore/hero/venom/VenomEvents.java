package dev.baranhan.viltrumitecore.hero.venom;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Server events of Venom: the takeover's clock and every end path that does not run through the hero's own cleanup
 * (a host's death, logout or dimension change, a server stop), the host's own hands kept off his inventory and the
 * world while he is driven, and the weaknesses' triggers (loud sounds, explosions).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class VenomEvents {
   private VenomEvents() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         VenomPossession.tick(event.getServer());
         VenomThrows.tick(event.getServer());
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeath(LivingDeathEvent event) {
      if (!event.getEntity().level().isClientSide && !event.isCanceled()) {
         VenomPossession.endAllFor(event.getEntity(), VenomPossession.End.DEATH);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      VenomPossession.endAllFor(event.getEntity(), VenomPossession.End.LOGOUT);
   }

   @SubscribeEvent
   public static void onTravel(EntityTravelToDimensionEvent event) {
      if (event.getEntity() instanceof LivingEntity living && !living.level().isClientSide) {
         VenomPossession.endAllFor(living, VenomPossession.End.DIMENSION);
      }
   }

   @SubscribeEvent
   public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
      VenomPossession.endAllFor(event.getEntity(), VenomPossession.End.DIMENSION);
   }

   @SubscribeEvent
   public static void onStopping(ServerStoppingEvent event) {
      VenomPossession.endAll(VenomPossession.End.SERVER_STOP);
      VenomThrows.clear();
   }

   @SubscribeEvent
   public static void onStartTracking(PlayerEvent.StartTracking event) {
      if (event.getEntity() instanceof ServerPlayer viewer) {
         VenomPossession.startTracking(viewer, event.getTarget());
      }
   }

   // --- a driven host's own hands -----------------------------------------------------------------------------

   /** Never an item lost: a toss is refused and the stack goes straight back into the inventory. */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onToss(ItemTossEvent event) {
      if (event.getPlayer() instanceof ServerPlayer host && VenomPossession.isHost(host)) {
         ItemEntity entity = event.getEntity();
         ItemStack stack = entity.getItem().copy();
         event.setCanceled(true);
         entity.discard();
         if (!host.getInventory().add(stack)) {
            host.drop(stack, false, true); // a full inventory: at his feet, owned by him (no other pickup)
         }
         host.inventoryMenu.broadcastChanges();
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onAttack(AttackEntityEvent event) {
      if (VenomPossession.isHost(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onInteract(PlayerInteractEvent event) {
      if (event.isCancelable() && !event.getLevel().isClientSide && VenomPossession.isHost(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onBreak(BlockEvent.BreakEvent event) {
      if (VenomPossession.isHost(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   /** The ground, lava, fire and the like cannot hurt a driven host (or one just released): nobody dies of the drive. Nor can the symbiote that released him, for that window. */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onHurt(LivingAttackEvent event) {
      LivingEntity e = event.getEntity();
      if (e.level().isClientSide || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return;
      }
      if (event.getSource().getEntity() != null) {
         // the symbiote that just spat its host out does not finish him in the same breath (§8.5)
         if (VenomPossession.sparedFrom(e, event.getSource().getEntity())) {
            event.setCanceled(true);
         }
         return;
      }
      if (e instanceof ServerPlayer && VenomPossession.shielded(e)) {
         event.setCanceled(true);
         e.clearFire();
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onFall(LivingFallEvent event) {
      if (event.getEntity() instanceof ServerPlayer p && VenomPossession.shielded(p)) {
         event.setDamageMultiplier(0.0F);
      }
   }

   // --- the weaknesses ----------------------------------------------------------------------------------------

   @SubscribeEvent
   public static void onSound(PlayLevelSoundEvent event) {
      if (!(event.getLevel() instanceof ServerLevel level) || event.getSound() == null) {
         return;
      }
      net.minecraft.world.phys.Vec3 at = event instanceof PlayLevelSoundEvent.AtPosition pos ? pos.getPosition()
         : event instanceof PlayLevelSoundEvent.AtEntity ent ? ent.getEntity().position() : null;
      if (at != null) {
         VenomWeakness.sound(level, at, event.getSound().value(), event.getNewVolume());
      }
   }

   @SubscribeEvent
   public static void onExplosion(ExplosionEvent.Detonate event) {
      if (event.getLevel() instanceof ServerLevel level) {
         VenomWeakness.explosion(level, event.getExplosion().getPosition(), 4.0F);
      }
   }
}
