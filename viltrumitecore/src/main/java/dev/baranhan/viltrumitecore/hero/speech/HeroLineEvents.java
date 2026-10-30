package dev.baranhan.viltrumitecore.hero.speech;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Shared hero-line events every hero may bind in its {@link HeroLines} table (global subtitles
 * expansion, docs/reports/2026-10-31-global-subtitles-audit.md). A hero without a line on the event
 * says nothing ({@link HeroSpeech#trigger} is a no-op). Bind them as {@link HeroLine.Priority#AMBIENT}
 * with a long cooldown: an ambient remark never cuts an ability or ultimate line
 * ({@link HeroSpeechRules.Decision#BUSY}).
 */
@EventBusSubscriber(modid = "viltrumitecore")
public final class HeroLineEvents {
   /** The speaker killed a notable target ({@link HeroSpeechRules#notableKill}). */
   public static final String KILL = "viltrumitecore:kill";
   /** The speaker just became this hero (hero choice / command), after the hero's enter(). */
   public static final String HERO_ENTER = "viltrumitecore:hero_enter";
   /** A survived hit took the speaker to 30% health or below. */
   public static final String LOW_HEALTH = "viltrumitecore:low_health";

   private HeroLineEvents() {
   }

   /** Fires a shared event for the speaker's current hero. */
   public static boolean fire(ServerPlayer speaker, String event) {
      return HeroSpeech.trigger(speaker, HeroLineTrigger.event(event));
   }

   /** Called by HeroRegistry.changeHero once the new hero has entered. */
   public static void entered(ServerPlayer player) {
      fire(player, HERO_ENTER);
   }

   /** Lowest priority and never on a cancelled death: a revive (Phoenix, totem-like saves) is not a kill. */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim) {
         return;
      }

      if (HeroSpeechRules.notableKill(victim instanceof Player, victim instanceof Enemy, victim.getMaxHealth())) {
         fire(killer, KILL);
      }
   }

   /**
    * Called by HeroEvents.onLivingDamage right after the damage layers set the final amount (same
    * LOWEST step). A separate LOWEST subscriber here could run before that clamp (the order of two
    * classes on one priority is not defined) and remark on damage a shield, a Hulkbuster or an HP
    * floor would still absorb.
    */
   public static void damaged(ServerPlayer player, float finalAmount) {
      if (!player.isCreative() && HeroSpeechRules.crossedLowHealth(player.getHealth(), finalAmount, player.getMaxHealth())) {
         fire(player, LOW_HEALTH);
      }
   }
}
