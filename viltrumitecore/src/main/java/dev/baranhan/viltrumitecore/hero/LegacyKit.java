package dev.baranhan.viltrumitecore.hero;

import dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The shared legacy (ex-Viltrumite) kit: punch, dash, thunderclap, grab, block,
 * chop, barrage, super speed, target lock. Every start of a kit ability asks
 * the hero through {@link #allows}; shared code never asks which hero it is.
 */
public final class LegacyKit {
   public static final String PUNCH = "viltrumite:punch";
   public static final String DASH = "viltrumite:dash";
   public static final String THUNDERCLAP = "viltrumite:thunderclap";
   public static final String GRAB = "viltrumite:grab";
   public static final String BLOCK = "viltrumite:block";
   public static final String CHOP = "viltrumite:chop";
   public static final String BARRAGE = "viltrumite:barrage";
   public static final String SPEED = "viltrumite:speed";
   public static final String LOCK = "viltrumite:lock";
   private static final UUID BASE_STRENGTH_MODIFIER_ID = UUID.fromString("e7208d13-6453-4cae-908c-9c3f508a6b12");

   private LegacyKit() {
   }

   /**
    * The kit grants vanilla {@code mayfly} on the server only; the client takes it from the
    * abilities packet. A client-side grant raced the hero sync: a fresh LocalPlayer (login,
    * respawn, dimension change) starts with the default hero id (the kit hero), so its first
    * ticks set a local mayfly that nothing ever cleared once the real hero (Reinhard) arrived
    * — survival double-tap flight "at random". Pure.
    */
   public static boolean forcesMayfly(boolean serverSide, boolean kitUser, boolean mayflyNow) {
      return serverSide && kitUser && !mayflyNow;
   }

   /** May this player start the given kit ability right now (both sides). */
   public static boolean allows(Player player, String abilityId) {
      // Spec 2026-10-28-sinclair §7: a locked body (rabbit hex) uses no kit ability.
      return player != null && !ActionLocks.locked(player) && HeroRegistry.get(player).allowsLegacyAbility(player, abilityId);
   }

   /** Reset every kit action flag/timer and the owned strength modifier. */
   public static void reset(ServerPlayer player) {
      if (!(player instanceof ViltrumiteCorePlayer corePlayer)) {
         return;
      }

      resetActions(corePlayer);
      AttributeInstance damageAttribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
      if (damageAttribute != null && damageAttribute.getModifier(BASE_STRENGTH_MODIFIER_ID) != null) {
         damageAttribute.removeModifier(BASE_STRENGTH_MODIFIER_ID);
      }
   }
   /** Only transient kit state. Same-entity death keeps the hero's chassis modifiers. */
   public static void resetActions(ViltrumiteCorePlayer corePlayer) {
      corePlayer.setPunchTicks(0);
      corePlayer.setPunchCooldown(0);
      corePlayer.setChopTicks(0);
      corePlayer.setThunderclapTicks(0);
      corePlayer.setBlocking(false);
      corePlayer.setTryingToGrab(false);
      corePlayer.releaseTarget();
      corePlayer.setSuperSpeed(false);
      corePlayer.setBarraging(false);
      corePlayer.setBarrageTicks(0);
      corePlayer.setBarrageCooldown(0);
      corePlayer.setBlockCooldown(0);
      corePlayer.setDashing(false);
   }

}
