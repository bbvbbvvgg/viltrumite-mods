package dev.baranhan.viltrumitecore.entity;

import dev.baranhan.viltrumitecore.entity.avengers.AvengerEntity;
import dev.baranhan.viltrumitecore.entity.avengers.QuinjetEntity;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Shared seam: a hero summon (an Iron Legion suit, a summoned Avenger or the Quinjet, a shadow of
 * Jinwoo's army) answers for the player who called it. Hero target rules ask here, so the summons
 * of two players follow the same PvP and team rules as the two players, and never hit each other
 * by an area hit when those rules forbid it. Server side.
 */
public final class SummonOwners {
   private SummonOwners() {
   }

   /** Is {@code e} a hero summon (whether or not its owner is online)? */
   public static boolean isSummon(@Nullable Entity e) {
      return e instanceof LegionSuitEntity || e instanceof AvengerEntity || e instanceof QuinjetEntity || e instanceof ShadowSoldierEntity;
   }

   /** The player behind a hero summon; null for anything else or while he is offline. */
   @Nullable
   public static ServerPlayer ownerOf(@Nullable Entity e) {
      if (e instanceof LegionSuitEntity suit) {
         return suit.owner();
      }
      if (e instanceof AvengerEntity avenger) {
         return avenger.owner();
      }
      if (e instanceof QuinjetEntity jet) {
         return jet.owner();
      }
      return e instanceof ShadowSoldierEntity shadow ? shadow.owner() : null;
   }
}
