package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.Misdirection;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.DecoyEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * World rules of Sinclair's tricks (spec §5.4, §5.5): the rabbit's size, damage taken and every forbidden
 * action; the veil's aggro switch; the dazzle timers. Both sides where the vanilla event runs on both.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class SinclairEvents {
   private SinclairEvents() {
   }

   /** LOW: after the shared hero body scale, so the rabbit size wins. */
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onSize(EntityEvent.Size event) {
      Entity entity = event.getEntity();
      if (entity instanceof LivingEntity && entity.level() != null && HexMarks.hexed(entity)) {
         event.setNewSize(EntityDimensions.scalable(SinclairRules.HEX_WIDTH, SinclairRules.HEX_HEIGHT));
         event.setNewEyeHeight(SinclairRules.HEX_HEIGHT * 0.78F);
      }
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setAmount(event.getAmount() * SinclairRules.HEX_DAMAGE_TAKEN);
      }
   }

   /** A rabbit cannot strike: melee from a hexed body is cancelled (projectiles in flight stay). */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onAttack(LivingAttackEvent event) {
      Entity attacker = event.getSource().getEntity();
      if (attacker != null && attacker == event.getSource().getDirectEntity() && HexMarks.hexed(attacker)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onPlayerAttack(AttackEntityEvent event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   /** New shots of a rabbit never leave (already flying ones do, like a started channel). */
   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onJoin(EntityJoinLevelEvent event) {
      if (!event.loadedFromDisk() && event.getEntity() instanceof Projectile projectile && projectile.tickCount == 0
         && HexMarks.hexed(projectile.getOwner())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onExplode(ExplosionEvent.Start event) {
      Entity source = event.getExplosion().getDirectSourceEntity();
      if (source != null && HexMarks.hexed(source)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onUseItem(LivingEntityUseItemEvent.Start event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRightItem(PlayerInteractEvent.RightClickItem event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onRightBlock(PlayerInteractEvent.RightClickBlock event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onLeftBlock(PlayerInteractEvent.LeftClickBlock event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
      if (HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onBreak(BlockEvent.BreakEvent event) {
      if (HexMarks.hexed(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void onPlace(BlockEvent.EntityPlaceEvent event) {
      if (event.getEntity() != null && HexMarks.hexed(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   /** §5.5 veil: no mob picks the veiled Sinclair (the decoy takes the aggro); dazzled and hexed mobs pick no one. */
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onTarget(LivingChangeTargetEvent event) {
      LivingEntity mob = event.getEntity();
      LivingEntity next = event.getNewTarget();
      if (next == null || mob.level().isClientSide) {
         return;
      }

      if (HexMarks.hexed(mob) || Daze.blinded(mob)) {
         event.setCanceled(true);
         return;
      }

      if (next instanceof ServerPlayer player && player instanceof HeroPlayer hero && hero.getHeroId() == HeroId.SINCLAIR) {
         SinclairState state = SinclairState.of(player);
         if (state != null && state.veil > 0) {
            DecoyEntity decoy = Misdirection.decoy(player, state);
            if (decoy != null && mob instanceof Mob && mob.distanceTo(player) <= SinclairRules.VEIL_AGGRO_RANGE) {
               event.setNewTarget(decoy);
            } else {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLivingTick(LivingEvent.LivingTickEvent event) {
      LivingEntity entity = event.getEntity();
      if (!entity.level().isClientSide) {
         Daze.tick(entity);
      }
   }
}
