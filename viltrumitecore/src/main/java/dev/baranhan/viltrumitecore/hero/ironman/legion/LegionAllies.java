package dev.baranhan.viltrumitecore.hero.ironman.legion;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.ironman.legion.ThreatScore.Relation;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * The one ally rule of the Iron Legion (design §5): what a suit may hurt, and
 * which hits between Tony and his suits never land. Projectiles, splash,
 * beams and the central attack event all ask here.
 */
public final class LegionAllies {
   private LegionAllies() {
   }

   /** Tony for his suits and for himself; null for anything else. */
   @Nullable
   public static UUID commander(@Nullable Entity entity) {
      if (entity instanceof LegionSuitEntity suit) {
         return suit.ownerUuid();
      }

      return entity instanceof Player player ? player.getUUID() : null;
   }

   /** A hit from {@code source} must not touch {@code target}: Tony and his suits never hurt each other. */
   public static boolean spares(@Nullable Entity source, @Nullable Entity target) {
      if (source == null || target == null) {
         return false;
      }

      if (source instanceof LegionSuitEntity a && a.hijacked() || target instanceof LegionSuitEntity b && b.hijacked()) {
         return false; // a taken-over suit is nobody's squad mate
      }

      return sameSquad(commander(source), source instanceof LegionSuitEntity, commander(target), target instanceof LegionSuitEntity);
   }

   /** Pure form of {@link #spares}: one side is a suit and both answer to the same commander. */
   static boolean sameSquad(@Nullable UUID source, boolean sourceSuit, @Nullable UUID target, boolean targetSuit) {
      return (sourceSuit || targetSuit) && source != null && source.equals(target);
   }

   /** What {@code target} is to the owner of a suit (owner may be null while he is offline: then everything but monsters is spared). */
   public static Relation relation(@Nullable ServerPlayer owner, LegionSuitEntity suit, Entity target) {
      if (owner != null && target == owner) {
         return Relation.OWNER;
      }

      if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isSpectator() || HeroDamage.isProtected(living)) {
         return Relation.PROTECTED;
      }

      if (target instanceof LegionSuitEntity taken && taken.hijacked()) {
         return Relation.HOSTILE; // the Legion fights a brother suit someone took over
      }

      if (spares(suit, target)) {
         return Relation.ALLY;
      }

      if (target instanceof LegionSuitEntity foreign) {
         // Another player's suit is worth exactly what its owner is worth (PvP, teams); no owner online: spared.
         ServerPlayer foreignOwner = foreign.owner();
         return foreignOwner == null || owner == null ? Relation.PROTECTED : relation(owner, suit, foreignOwner);
      }

      if (SummonOwners.isSummon(target)) {
         // Another hero's summon (Captain's Avengers, Jinwoo's shadows): worth what its owner is worth; no owner online: spared.
         ServerPlayer summoner = SummonOwners.ownerOf(target);
         if (summoner == null || owner == null) {
            return Relation.PROTECTED;
         }
         return summoner == owner ? Relation.ALLY : relation(owner, suit, summoner);
      }

      if (owner != null && (owner.isAlliedTo(target) || target.isAlliedTo(owner))) {
         return Relation.ALLY;
      }

      if (target instanceof OwnableEntity pet && pet.getOwnerUUID() != null && pet.getOwnerUUID().equals(suit.ownerUuid())) {
         return Relation.ALLY;
      }

      if (target instanceof Player player) {
         if (player.isCreative() || owner == null) {
            return Relation.PROTECTED;
         }

         return owner.server.isPvpAllowed() && owner.canHarmPlayer(player) ? Relation.PLAYER_PVP : Relation.PLAYER_NO_PVP;
      }

      if (target instanceof AbstractVillager || target instanceof IronGolem || target instanceof SnowGolem) {
         return Relation.VILLAGER;
      }

      if (target instanceof NeutralMob neutral) {
         return owner != null && neutral.isAngryAt(owner) ? Relation.HOSTILE : Relation.NEUTRAL;
      }

      return target instanceof Enemy ? Relation.HOSTILE : Relation.NEUTRAL;
   }

   /**
    * May a suit's hit land on {@code target}? Never on Tony, allies, protected
    * entities or players without PvP; villagers only as the ordered target.
    */
   public static boolean mayHit(LegionSuitEntity suit, Entity target) {
      Relation relation = relation(suit.owner(), suit, target);
      return switch (relation) {
         case OWNER, ALLY, PROTECTED, PLAYER_NO_PVP -> false;
         case VILLAGER -> suit.orderedTarget() == target.getId();
         default -> true;
      };
   }

   /** Missile splash of a suit: the same rule (Tony's own missiles keep the stage 3 behaviour). */
   public static boolean splashAllowed(@Nullable Entity owner, Entity target) {
      return !(owner instanceof LegionSuitEntity suit) || mayHit(suit, target);
   }

   /**
    * The player responsible for a hit: a player himself, or the owner of the
    * Legion suit that fired it (directly or through its projectile); null
    * when no player is behind it or the suit's owner is offline.
    */
   @Nullable
   public static Player playerBehind(@Nullable Entity attacker, @Nullable Entity direct) {
      if (attacker instanceof Player player) {
         return player;
      }

      LegionSuitEntity suit = suitOf(attacker, direct);
      if (suit != null) {
         // a taken-over suit's hits are its controller's, not its owner's
         return suit.hijacked() ? null : suit.owner();
      }

      // Another hero's summon (an Avenger, a shadow) answers for its owner too.
      return SummonOwners.ownerOf(attacker);
   }

   /**
    * May a hit from {@code attacker}/{@code direct} take durability from a
    * suit of {@code owner}? Hits that come from no player pass. Hits from a
    * player, or from another player's Legion, follow the server PvP switch,
    * teams ({@code canHarmPlayer}) and spectator rules (Codex review #4).
    */
   public static boolean playerDamageAllowed(ServerPlayer owner, @Nullable Entity attacker, @Nullable Entity direct) {
      LegionSuitEntity fromSuit = suitOf(attacker, direct);
      boolean fromSummon = fromSuit != null && !fromSuit.hijacked() || SummonOwners.isSummon(attacker);
      Player other = playerBehind(attacker, direct);
      if (other == null) {
         // A suit (or another hero's summon) whose owner is gone cannot claim PvP rights.
         return !fromSummon;
      }

      return pvpAllowed(other == owner, owner.server.isPvpAllowed(), owner.canHarmPlayer(other) && other.canHarmPlayer(owner), other.isSpectator());
   }

   /** Pure PvP rule shared by direct and Legion hits between two owners. */
   static boolean pvpAllowed(boolean sameOwner, boolean serverPvp, boolean teamsAllow, boolean spectator) {
      return !sameOwner && serverPvp && teamsAllow && !spectator;
   }

   /** The suit behind a damage source: the direct attacker or the owner of its projectile. */
   @Nullable
   public static LegionSuitEntity suitOf(@Nullable Entity attacker, @Nullable Entity direct) {
      if (attacker instanceof LegionSuitEntity suit) {
         return suit;
      }

      return direct instanceof LegionSuitEntity suit ? suit : null;
   }
}
