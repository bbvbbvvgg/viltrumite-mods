package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.balance.HeroBalanceProfile;
import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.captain.CaptainUse;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Venom (spec docs/design/2026-11-02-venom-design.md): a host and the living symbiote that covers him in a staged
 * transformation. In form: the big body with claws, tendrils, the shell, the frenzy and the host takeover; wall
 * crawling, mega jumps, regeneration; fire and loud sound hurt it. Works fully without Falbiks Heroes; with it the
 * Spider mechanics run through {@link SpiderCompat} (never a hard dependency). Own chassis (form only), no flight.
 */
public final class VenomHero implements HeroDefinition {
   static final UUID HEALTH_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c01");
   static final UUID ARMOR_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c02");
   static final UUID TOUGHNESS_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c03");
   static final UUID KNOCKBACK_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c04");
   static final UUID SPEED_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c05");
   /** Rooted moves, the shell's weight; removed when idle. */
   public static final UUID SLOW_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c06");
   /** The frenzy's stride. */
   public static final UUID RAGE_ID = UUID.fromString("7b2c4d3e-8f5a-4a9b-8c1d-2e3f4a5b6c07");
   public static final VenomHero INSTANCE = new VenomHero();

   private VenomHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.VENOM;
   }

   @Nullable
   static HeroPublicSnapshot view(Player player) {
      HeroPublicSnapshot view = player instanceof HeroPlayer hp ? hp.getHeroSnapshot() : null;
      return view != null && view.heroId() == HeroId.VENOM ? view : null;
   }

   /** A snapshot flag on the client, the state on the server. */
   static boolean flag(Player player, int bit) {
      HeroPublicSnapshot view = view(player);
      return view != null && VenomFlags.has(view.heroFlags(), bit);
   }

   /** The symbiote covers the host (either side). */
   public static boolean form(Player player) {
      if (player.level().isClientSide) {
         return flag(player, VenomFlags.FORM);
      }
      VenomState state = VenomState.of(player);
      return state != null && state.form;
   }

   public static boolean raging(Player player) {
      if (player.level().isClientSide) {
         return flag(player, VenomFlags.RAGE);
      }
      VenomState state = VenomState.of(player);
      return state != null && state.rage;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   @Override
   public HeroBalanceProfile balanceProfile(Player player) {
      return form(player) ? BalanceTable.SYMBIOTE : BalanceTable.UNARMORED;
   }

   @Override
   public boolean hasAbilityPanel(Player player) {
      return true;
   }

   @Override
   public boolean allowsAbilityPages(Player player) {
      return false;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return VenomAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return VenomAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return VenomAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return VenomAbilities.actionFor(abilityId);
   }

   @Override
   public List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return VenomAbilities.panelAbilities();
   }

   @Override
   public dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return VenomLines.LINES;
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return VenomAbilities.icon(abilityId);
   }

   /**
    * The transformation, the engulf and the frenzy's roar are not broken by grabs, freezes or pushes. The symbiote
    * guards its own host: a Venom in form can never be taken over.
    */
   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      VenomState state = target instanceof Player p ? VenomState.of(p) : null;
      if (state == null) {
         return true;
      }
      if (kind == ControlKind.POSSESS && (state.form || state.possessing())) {
         return false;
      }
      VenomMove m = state.move;
      return m == null || !(m == VenomMove.TRANSFORM || m == VenomMove.ENGULF || m == VenomMove.RAGE);
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   @Override
   public boolean sustainsAirborne(Player player) {
      return form(player); // wall crawling and tendril swings are legitimate airborne stretches
   }

   // --- mouse: claws and tendrils in form; the host fights and mines like anybody ---

   @Override
   public boolean claimsBlockAttack(Player player) {
      return form(player);
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return !form(player);
   }

   @Override
   public boolean yieldsToVanillaUse(Player player) {
      return !form(player) || CaptainUse.yields(player);
   }

   @Override
   public boolean claimsBlockUse(Player player) {
      return form(player) && !CaptainUse.yields(player);
   }

   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      if (!form(player)) {
         return null;
      }
      return switch (button) {
         case PRIMARY -> HeroAction.PRIMARY_ATTACK;
         case SECONDARY -> HeroAction.SECONDARY_USE;
         default -> null;
      };
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player) || VenomPossession.isHost(player)) {
         return false;
      }
      VenomState state = VenomState.ensure(player);
      if (state.possessing()) {
         return action == HeroAction.VENOM_TAKEOVER; // the husk can only call the symbiote back
      }
      boolean locked = state.move != null && state.move.locks();
      return switch (action) {
         case PRIMARY_ATTACK, SECONDARY_USE -> !locked && state.form;
         case JUMP, AIR_STEP -> state.move == null || !state.move.roots();
         case VENOM_SYMBIOSIS -> !locked;
         default -> !locked && state.form && VenomAbility.byAction(action) != null;
      };
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      VenomSounds.play(player, VenomSounds.REFUSE, 0.5F, 1.0F);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (!pressed) {
         return;
      }
      VenomState state = VenomState.ensure(player);
      switch (action) {
         case PRIMARY_ATTACK -> VenomCombat.primary(player, state);
         case SECONDARY_USE -> VenomCombat.secondary(player, state);
         default -> {
            VenomAbility ability = VenomAbility.byAction(action);
            if (ability != null) {
               VenomSkills.input(player, state, ability);
            }
         }
      }
   }

   @Override
   public void handleForcedRelease(ServerPlayer player, HeroAction action) {
      // every input is a press; nothing is held
   }

   // --- lifecycle ---

   @Override
   public void tick(ServerPlayer player) {
      VenomState state = VenomState.ensure(player);
      state.tickCount++;
      state.tickTimers();
      if (state.form && state.tickCount % 20 == 0) {
         applyChassis(player);
      }
      VenomSkills.tick(player, state);
      VenomCombat.tick(player, state);
      VenomSpider.tick(player, state);
      VenomMove m = state.move;
      double slow = m != null && m.roots() || state.possessing() ? -0.9 : state.shell ? VenomRules.SHELL_SLOW : 0.0;
      modifierNow(player, SLOW_ID, "Venom rooted", slow);
      modifierNow(player, RAGE_ID, "Venom frenzy", state.rage ? VenomRules.RAGE_SPEED : 0.0);
   }

   @Override
   public void enter(ServerPlayer player) {
      VenomState state = VenomState.ensure(player);
      if (player instanceof ViltrumiteAbilityUser user) {
         user.setActivePage(0);
      }
      if (state.form) {
         applyChassis(player);
         player.setHealth(player.getMaxHealth());
      } else {
         removeChassis(player);
      }
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      VenomState state = VenomState.of(player);
      if (state != null && state.form) {
         applyChassis(player);
         player.setHealth(player.getMaxHealth());
      }
   }

   /** The symbiote's body; the health keeps its fraction (no free healing by changing back and forth). */
   static void applyChassis(ServerPlayer player) {
      float fraction = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
      boolean added = modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Venom health", VenomRules.HEALTH_BONUS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Venom armor", VenomRules.ARMOR, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, "Venom toughness", VenomRules.TOUGHNESS, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Venom weight", VenomRules.KNOCKBACK_RESIST, AttributeModifier.Operation.ADDITION);
      modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Venom stride", VenomRules.SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE);
      if (added) {
         player.setHealth(Mth.clamp(fraction * player.getMaxHealth(), 1.0F, player.getMaxHealth()));
      }
   }

   private static boolean modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, op));
         return true;
      }
      return false;
   }

   static void modifierNow(ServerPlayer player, UUID id, String name, double amount) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      AttributeModifier current = speed.getModifier(id);
      if (amount == 0.0) {
         if (current != null) speed.removeModifier(id);
         return;
      }
      if (current != null && current.getAmount() == amount) {
         return;
      }
      if (current != null) speed.removeModifier(id);
      speed.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
   }

   static void removeChassis(ServerPlayer player) {
      float fraction = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
      boolean had = false;
      for (Object[] pair : new Object[][]{{Attributes.MAX_HEALTH, HEALTH_ID}, {Attributes.ARMOR, ARMOR_ID}, {Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID},
         {Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID}, {Attributes.MOVEMENT_SPEED, SPEED_ID}, {Attributes.MOVEMENT_SPEED, SLOW_ID},
         {Attributes.MOVEMENT_SPEED, RAGE_ID}}) {
         AttributeInstance instance = player.getAttribute((Attribute)pair[0]);
         if (instance != null && instance.getModifier((UUID)pair[1]) != null) {
            instance.removeModifier((UUID)pair[1]);
            had |= pair[1] == HEALTH_ID;
         }
      }
      if (had && player.isAlive()) {
         player.setHealth(Mth.clamp(fraction * player.getMaxHealth(), 1.0F, player.getMaxHealth()));
      }
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   @Override
   public boolean resetCooldowns(ServerPlayer player) {
      VenomState state = VenomState.of(player);
      if (state == null) {
         return false;
      }
      java.util.Arrays.fill(state.cooldowns, 0);
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      VenomPossession.endAllFor(player, VenomPossession.End.of(reason));
      VenomState state = VenomState.of(player);
      if (state != null) {
         VenomSkills.cleanup(player, state);
         if (reason == CleanupReason.HERO_CHANGE) {
            state.form = false;
         }
      }
      modifierNow(player, SLOW_ID, "", 0.0);
      modifierNow(player, RAGE_ID, "", 0.0);
      if (reason == CleanupReason.HERO_CHANGE) {
         removeChassis(player);
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      VenomPossession.endAllFor(player, VenomPossession.End.DIMENSION);
      VenomState state = VenomState.of(player);
      if (state != null) {
         VenomSkills.cleanup(player, state);
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      VenomState state = VenomState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      VenomState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      VenomState previous = VenomState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- body ---

   @Override
   public float bodyScale(Player player) {
      // the husk left behind by a takeover is the bare human again (the symbiote rides the host)
      return !form(player) || flag(player, VenomFlags.POSSESSING) ? 1.0F : raging(player) ? VenomRules.RAGE_HEIGHT_SCALE : VenomRules.BODY_HEIGHT_SCALE;
   }

   @Override
   public float bodyWidthScale(Player player) {
      return !form(player) || flag(player, VenomFlags.POSSESSING) ? 1.0F : raging(player) ? VenomRules.RAGE_WIDTH_SCALE : VenomRules.BODY_WIDTH_SCALE;
   }

   @Override
   public double bodyReachBonus(Player player) {
      return form(player) ? VenomRules.REACH_BONUS : 0.0;
   }

   @Override
   public double bodyStepBonus(Player player) {
      return form(player) ? 0.5 : 0.0;
   }

   @Override
   public float superJumpVelocity(Player player) {
      if (!form(player) || flag(player, VenomFlags.POSSESSING) && player.level().isClientSide) {
         return 0.0F;
      }
      if (!player.level().isClientSide) {
         VenomState state = VenomState.of(player);
         if (state == null || state.possessing() || state.shell) {
            return 0.0F;
         }
      } else if (flag(player, VenomFlags.SHELL)) {
         return 0.0F;
      }
      return raging(player) ? VenomRules.RAGE_SUPER_JUMP : VenomRules.SUPER_JUMP;
   }

   @Override
   public void onSuperJump(ServerPlayer player) {
      VenomFx.send(player, VenomFx.LEAP, player.position(), null, 1.0F, 0);
      VenomSounds.play(player, VenomSounds.SQUIRM, 0.8F, VenomSounds.jitter(player, 0.9F));
   }

   @Override
   public boolean cancelsFallDamage(Player player) {
      return form(player);
   }

   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      if (!form(player)) {
         return; // the host lands like anyone: vanilla fall damage
      }
      if (fallDistance > 8.0F) {
         VenomFx.send(player, VenomFx.LEAP, player.position(), null, Math.min(2.5F, fallDistance / 12.0F), 0);
         VenomSounds.play(player, VenomSounds.SLAM, Math.min(1.0F, fallDistance / 20.0F), VenomSounds.jitter(player, 1.1F));
      }
      float damage = VenomRules.fallDamage(fallDistance);
      if (damage > 0.0F) {
         player.hurt(player.damageSources().fall(), damage);
      }
   }

   @Override
   public boolean knockbackImmune(Player player) {
      VenomState state = VenomState.of(player);
      return state != null && (state.shell || state.possessing() || state.move != null && state.move.unstoppable());
   }

   /** Fire makes the symbiote recoil (VenomWeakness); nothing is absorbed outright. */
   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      VenomState state = VenomState.of(self);
      if (state != null && state.form && raw > 0.0F && VenomTargets.fire(source)) {
         VenomWeakness.burnt(self, state);
      }
      return DamageAbsorb.PASS;
   }

   /** Fire and sound go through the symbiote; the shell drinks the rest; the abandoned husk is soft. */
   @Override
   public float clampFinalDamage(ServerPlayer self, DamageSource source, float afterArmor) {
      VenomState state = VenomState.of(self);
      if (state == null) {
         return afterArmor;
      }
      float f = state.form ? VenomRules.incomingFactor(VenomTargets.fire(source), VenomTargets.sonic(source), state.shell) : 1.0F;
      if (state.possessing()) {
         f *= VenomRules.HUSK_DAMAGE_TAKEN;
      }
      return afterArmor * f;
   }

   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      VenomState state = VenomState.of(player);
      if (state == null) {
         return;
      }
      state.idle = 0;
      state.sinceHurt = 0;
      if (state.possessing()) {
         VenomPossession.huskHurt(player, amount);
      } else if (state.form) {
         VenomSkills.damageReaction(player, state, source, amount);
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      VenomState state = VenomState.of(attacker);
      if (state == null) {
         return amount;
      }
      state.idle = 0;
      return state.rage ? amount * VenomRules.RAGE_DAMAGE : amount;
   }

   // --- webs and other projectiles ---

   @Override
   public boolean interceptsProjectiles(Player self) {
      return form(self) && !flag(self, VenomFlags.POSSESSING);
   }

   /** A Spider's web: the shell throws it back, the symbiote otherwise swallows it (VenomSpider). */
   @Override
   public ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) {
      VenomState state = VenomState.of(self);
      if (state == null || !state.form || state.possessing() || !SpiderCompat.isWebProjectile(projectile)) {
         return ProjectileResponse.PASS;
      }
      if (state.shell) {
         VenomSpider.thrownBack(self, state, projectile);
         return ProjectileResponse.REFLECT;
      }
      return ProjectileResponse.CATCH;
   }

   @Override
   public boolean captureProjectile(ServerPlayer self, Projectile projectile) {
      VenomState state = VenomState.of(self);
      if (state == null || !SpiderCompat.isWebProjectile(projectile)) {
         return false;
      }
      VenomSpider.eat(self, state, projectile);
      return true;
   }

   // --- the body on the tendrils (shared seam: the client mixin places it, no teleport handshake) ---

   @Override
   public int heldTargetId(Player player) {
      if (player.level().isClientSide) {
         HeroPublicSnapshot view = view(player);
         return view != null && VenomFlags.has(view.heroFlags(), VenomFlags.HOLDING) ? view.controlTargetId() : -1;
      }
      VenomState state = VenomState.of(player);
      return state != null && state.holding() ? state.heldId : -1;
   }

   @Override
   @Nullable
   public Vec3 heldTargetPosition(Player player, Entity target, float partialTick) {
      float yaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot());
      return VenomCombat.holdPoint(player.getPosition(partialTick), yaw, player.getBbHeight(), target.getBbHeight());
   }

   // --- snapshot ---

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      VenomState state = VenomState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.VENOM, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1,
            new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }
      return compose(state, player);
   }

   /**
    * actionId = move, variant = move serial, controlTargetId = the held body or the host, actionTarget = the tendril's
    * end, resource = hunger, resource2 = the takeover's remaining ticks.
    */
   static HeroPublicSnapshot compose(VenomState state, Player player) {
      int[] extra = state.cooldowns.clone();
      int flags = 0;
      flags = VenomFlags.set(flags, VenomFlags.FORM, state.form);
      flags = VenomFlags.set(flags, VenomFlags.RAGE, state.rage);
      flags = VenomFlags.set(flags, VenomFlags.SHELL, state.shell);
      flags = VenomFlags.set(flags, VenomFlags.HOLDING, state.holding());
      flags = VenomFlags.set(flags, VenomFlags.TENDRIL, state.tendrilEnd != null);
      flags = VenomFlags.set(flags, VenomFlags.WALL, state.wallTicks > 0);
      flags = VenomFlags.set(flags, VenomFlags.POSSESSING, state.possessing());
      flags = VenomFlags.set(flags, VenomFlags.RECOIL, state.is(VenomMove.RECOIL));
      flags = VenomFlags.set(flags, VenomFlags.IN_COMBAT, state.idle < 100);
      flags = VenomFlags.set(flags, VenomFlags.SPIDER_NEAR, state.spiderNear);
      flags = VenomFlags.set(flags, VenomFlags.CRAWL, VenomSkills.mayCrawl(state));
      flags = VenomFlags.set(flags, VenomFlags.ON_WEB, state.webSpiderId >= 0);
      VenomMove move = state.move;
      int target = state.possessing() ? state.hostId : state.holding() ? state.heldId : state.pullId;
      return new HeroPublicSnapshot(HeroId.VENOM, move == null ? -1 : move.ordinal(), state.elapsed, move == null ? 0 : move.length,
         0, false, 0, 0, false, false, 0, 0, target, new int[HeroPublicSnapshot.COOLDOWN_COUNT],
         move != null, -1L, state.tendrilEnd, state.hunger, false, flags, extra, state.serial, VenomPossession.remainingFor(player));
   }
}
