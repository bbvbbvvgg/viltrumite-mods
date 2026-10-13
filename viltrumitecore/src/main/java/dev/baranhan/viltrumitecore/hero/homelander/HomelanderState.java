package dev.baranhan.viltrumitecore.hero.homelander;

import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

/** Server-side per-player Homelander state. Transient: nothing survives relog (spec §9). */
public final class HomelanderState {
   public final EyeHeat heat = new EyeHeat();
   // Lasers
   public boolean laserHeld;
   /** Ticks since the laser key went down; -1 = off. */
   public int laserAge = -1;
   // Focus: target entity ids in selection order. Fear lingers through the effect duration.
   public boolean focusOn;
   public int focusRefresh;
   public final List<Integer> focusTargets = new ArrayList<>();
   // Roar
   public int roarCooldown;
   /** Ticks left of the roar pose (snapshot timeline). */
   public int roarAnim;
   public final Regen regen = new Regen();

   /** No combat state or regeneration debt survives a lost body. */
   public HomelanderState erasedCopy() {return new HomelanderState();}

   public boolean laserBeamOn() {
      return this.laserAge >= HomelanderRules.LASER_CHARGE_TICKS;
   }

   @Nullable
   public static HomelanderState of(Player player) {
      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroId() == HeroId.HOMELANDER) {
         return heroPlayer.viltrumitecore$getHeroState() instanceof HomelanderState state ? state : null;
      }

      return null;
   }

   public static HomelanderState ensure(Player player) {
      HeroPlayer heroPlayer = (HeroPlayer)player;
      if (heroPlayer.viltrumitecore$getHeroState() instanceof HomelanderState existing) {
         return existing;
      }

      HomelanderState state = new HomelanderState();
      heroPlayer.viltrumitecore$setHeroState(state);
      return state;
   }
}
