package dev.baranhan.viltrumitecore.hero;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Mobs that flee a hero the way a creeper flees a cat ({@link HeroDefinition#scaresMobType},
 * {@link HeroDefinition#scaresMob}). Vanilla AI only: an {@link AvoidEntityGoal} at the
 * cat's priority and speeds (Creeper.registerGoals: priority 3, walk 1.0, sprint 1.2), and
 * a target veto so the mob never hunts that hero — a creeper that cannot target him never
 * swells at him either. Only mob types a hero declares get the goal; every other mob keeps
 * its vanilla goals untouched. Server only.
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroMobFear {
   /** Creeper: cats are avoided at priority 3. */
   public static final int PRIORITY = 3;
   /** Cats scare from 6 blocks; a hero moves much faster, so the mob reacts a little earlier. */
   public static final float RANGE = 8.0F;
   public static final double WALK_SPEED = 1.0;
   public static final double SPRINT_SPEED = 1.2;

   private HeroMobFear() {
   }

   /** Does this mob flee this player now (both checks of the hero; spectators never scare). */
   public static boolean scares(Player player, Mob mob) {
      return player != null && mob != null && player.isAlive() && !player.isSpectator()
         && HeroRegistry.get(player).scaresMob(player, mob);
   }

   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob) || !HeroRegistry.anyScares(mob.getType())) {
         return;
      }
      for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
         if (wrapped.getGoal() instanceof FleeHeroGoal) {
            return;
         }
      }
      mob.goalSelector.addGoal(PRIORITY, new FleeHeroGoal(mob));
   }

   /** Never lock onto a scaring hero (nearest-target and hurt-by-target goals alike). */
   @SubscribeEvent
   public static void onTarget(LivingChangeTargetEvent event) {
      if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()
         && event.getNewTarget() instanceof Player player && scares(player, mob)) {
         event.setNewTarget(null);
      }
   }

   /** The cat-style flight from a scaring hero; also drops a target picked before he became scary. */
   static final class FleeHeroGoal extends AvoidEntityGoal<Player> {
      private final PathfinderMob owner;

      FleeHeroGoal(PathfinderMob mob) {
         super(mob, Player.class, RANGE, WALK_SPEED, SPRINT_SPEED, entity -> entity instanceof Player player && scares(player, mob));
         this.owner = mob;
      }

      @Override
      public boolean canUse() {
         if (this.owner.getTarget() instanceof Player target && scares(target, this.owner)) {
            this.owner.setTarget(null);
         }
         return super.canUse();
      }
   }
}
