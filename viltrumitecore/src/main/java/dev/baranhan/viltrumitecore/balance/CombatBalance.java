package dev.baranhan.viltrumitecore.balance;

import dev.baranhan.viltrumitecore.entity.LegionSuitEntity;
import dev.baranhan.viltrumitecore.entity.SummonOwners;
import dev.baranhan.viltrumitecore.entity.ViltrumiteFakePlayer;
import dev.baranhan.viltrumitecore.entity.chitauri.ChitauriLeviathan;
import dev.baranhan.viltrumitecore.entity.chitauri.LeviathanPart;
import dev.baranhan.viltrumitecore.entity.jinwoo.ShadowSoldierEntity;
import dev.baranhan.viltrumitecore.entity.thanos.ThanosEntity;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.hero.HeroDamageLayers;
import dev.baranhan.viltrumitecore.hero.HeroRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Minecraft glue of the global balance model. Two seams, both on the shared damage path:
 * <ol>
 *   <li>{@link #onHurt} (LivingHurtEvent, before armor): remembers the raw amount of a hit on a
 *       hero player and scales the hits of a summoned group ({@link BalanceRules#groupFactor});</li>
 *   <li>{@link #onHeroDamage} (LivingDamageEvent and the direct path, after armor and the hero's
 *       own layers): the hero's PvE profile ({@link HeroBalanceProfile}).</li>
 * </ol>
 * Nothing here branches on a hero: the profile comes from {@code HeroDefinition.balanceProfile}.
 */
public final class CombatBalance {
   /** Raw (pre-armor) amount of the hit in flight on a hero player, consumed by the same hit's damage step. */
   private static final Map<UUID, Float> RAW = new HashMap<>();
   /** Group size cache: owner + summon class, refreshed once per game tick. */
   private static final Map<String, long[]> GROUPS = new HashMap<>();

   private CombatBalance() {
   }

   /** Logout / death: drop what a hit in flight left behind. */
   public static void forget(UUID player) {
      RAW.remove(player);
   }

   // ---- classification -----------------------------------------------------------------------

   /** Kind of hit for the PvE profile. {@code raw} is the pre-armor amount (or the after-armor one when unknown). */
   public static IncomingKind classify(DamageSource source, float raw) {
      if (HeroDamageLayers.bypassesLayers(source) || HeroDamageLayers.unflooredScope() || DivineDamage.isDivine(source)) {
         return IncomingKind.EXEMPT;
      }

      Entity attacker = source.getEntity();
      if (isPlayerAttacker(attacker) || SummonOwners.isSummon(attacker)) {
         return IncomingKind.PVP;
      }

      if (isBoss(attacker) || isBoss(source.getDirectEntity())) {
         return IncomingKind.BOSS;
      }

      if (source.is(DamageTypeTags.IS_EXPLOSION)) {
         return IncomingKind.BLAST;
      }

      if (source.is(DamageTypeTags.IS_PROJECTILE)) {
         return IncomingKind.PROJECTILE;
      }

      if (attacker instanceof LivingEntity) {
         return raw >= BalanceRules.HEAVY_RAW ? IncomingKind.HEAVY : IncomingKind.MELEE;
      }

      return IncomingKind.WORLD;
   }

   /** A real player on the other end: Viltrumite NPCs and Forge fake players are not. */
   static boolean isPlayerAttacker(@Nullable Entity attacker) {
      return attacker instanceof Player && !(attacker instanceof ViltrumiteFakePlayer) && !(attacker instanceof FakePlayer);
   }

   public static boolean isBoss(@Nullable Entity e) {
      return e instanceof ThanosEntity || e instanceof ChitauriLeviathan || e instanceof LeviathanPart || e instanceof Warden
         || e instanceof WitherBoss || e instanceof EnderDragon || e instanceof ElderGuardian
         || e instanceof dev.baranhan.viltrumitecore.entity.ultron.UltronPrime;
   }

   // ---- LivingHurtEvent ------------------------------------------------------------------------

   /** LivingHurtEvent step (before armor). Returns the amount to continue with. */
   public static float onHurt(LivingEntity target, DamageSource source, float amount) {
      if (amount <= 0.0F || target.level().isClientSide()) {
         return amount;
      }

      amount *= summonFactor(source);
      if (target instanceof ServerPlayer player) {
         RAW.put(player.getUUID(), amount);
      }

      return amount;
   }

   /** Group factor for a hit dealt by a hero summon (1 for anything else). */
   static float summonFactor(DamageSource source) {
      Entity dealer = source.getEntity();
      if (dealer instanceof ShadowSoldierEntity) {
         return BalanceRules.groupFactor(groupSize(dealer, ShadowSoldierEntity.class), BalanceTable.SHADOW_FREE_COUNT);
      }

      if (dealer instanceof LegionSuitEntity) {
         return BalanceRules.groupFactor(groupSize(dealer, LegionSuitEntity.class), BalanceTable.LEGION_FREE_COUNT);
      }

      return 1.0F;
   }

   /** Live summons of {@code dealer}'s owner and class near the dealer (cached per game tick). */
   static int groupSize(Entity dealer, Class<? extends Entity> type) {
      ServerPlayer owner = SummonOwners.ownerOf(dealer);
      if (owner == null) {
         return 1;
      }

      String key = owner.getUUID() + "/" + type.getSimpleName();
      long now = dealer.level().getGameTime();
      long[] cached = GROUPS.get(key);
      if (cached != null && cached[0] == now) {
         return (int)cached[1];
      }

      double r = BalanceTable.GROUP_RADIUS;
      int count = dealer.level().getEntitiesOfClass(type, dealer.getBoundingBox().inflate(r), e -> e.isAlive() && SummonOwners.ownerOf(e) == owner).size();
      count = Math.max(1, count);
      if (GROUPS.size() > 256) {
         GROUPS.clear();
      }

      GROUPS.put(key, new long[]{now, count});
      return count;
   }

   // ---- LivingDamageEvent / direct path ------------------------------------------------------

   /** After armor and the hero's own layers: the hero's PvE profile. Returns the HP to remove. */
   public static float onHeroDamage(ServerPlayer player, DamageSource source, float afterLayers) {
      Float raw = RAW.remove(player.getUUID());
      return heroDamage(player, source, raw == null ? afterLayers : raw, afterLayers);
   }

   /** Direct path ({@code HeroDamage.applyCleanDamage}): raw is the amount handed in. */
   public static float onHeroCleanDamage(ServerPlayer player, DamageSource source, float raw, float afterLayers) {
      return heroDamage(player, source, raw, afterLayers);
   }

   private static float heroDamage(ServerPlayer player, DamageSource source, float raw, float afterLayers) {
      if (afterLayers <= 0.0F || player instanceof ViltrumiteFakePlayer) {
         return afterLayers;
      }

      HeroBalanceProfile profile = HeroRegistry.get(player).balanceProfile(player);
      if (profile == null || profile == HeroBalanceProfile.VANILLA) {
         return afterLayers;
      }

      IncomingKind kind = classify(source, raw);
      if (kind == IncomingKind.EXEMPT || kind == IncomingKind.PVP) {
         return afterLayers;
      }

      return BalanceRules.heroHit(afterLayers, player.getMaxHealth(), profile.resilienceFor(kind), profile.capFor(kind));
   }
}
