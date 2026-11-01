package dev.baranhan.viltrumitecore.hero.dio;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
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
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.control.ReleaseReason;
import dev.baranhan.viltrumitecore.hero.dio.net.DioFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.dio.net.DioNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * DIO (JoJo part 3) with his stand The World. Vanilla body; the stand is a
 * client-rendered rigged model driven by the public snapshot. Server owns
 * every hit, the time-stop fields ({@link DioTimeStop}) and the blood resource.
 */
public final class DioHero implements HeroDefinition {
   public static final DioHero INSTANCE = new DioHero();
   private static final UUID HEALTH_ID = UUID.fromString("5b0e7f9e-3d2a-4c61-9a47-d10000000001");
   private static final UUID ARMOR_ID = UUID.fromString("5b0e7f9e-3d2a-4c61-9a47-d10000000002");
   private static final UUID ATTACK_ID = UUID.fromString("5b0e7f9e-3d2a-4c61-9a47-d10000000003");
   private static final UUID SPEED_ID = UUID.fromString("5b0e7f9e-3d2a-4c61-9a47-d10000000004");
   /** Blood DIO starts with when he becomes DIO (one short time stop). */
   public static final int ENTER_BLOOD = 40;
   private static final int STAND_TOGGLE_COOLDOWN = 8;

   private DioHero() {
   }

   /** Server: blood from a hit or a drain pulse. */
   public static void addBlood(ServerPlayer player, int amount) {
      DioState state = DioState.of(player);
      if (state != null) {
         state.blood = DioRules.clampBlood(state.blood + amount);
      }
   }

   @Override
   public HeroId id() {
      return HeroId.DIO;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean grantsFlightAbility(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   /** Global balance profile (BalanceTable): DIO; tier A+. */
   @Override
   public dev.baranhan.viltrumitecore.balance.HeroBalanceProfile balanceProfile(Player player) {
      return dev.baranhan.viltrumitecore.balance.BalanceTable.VAMPIRE;
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
      return DioAbilities.owns(abilityId);
   }

   @Override
   public List<ViltrumiteAbility> panelAbilities() {
      return DioAbilities.panelAbilities();
   }

   @Override
   public HeroLines lines() {
      return DioLines.LINES;
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return DioAbilities.icon(abilityId);
   }

   @Override
   public String[] heroInputSlots() {
      return DioAbilities.slotIds();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return DioAbilities.actionFor(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return DioAbilities.defaultLoadout();
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   // --- both-side views ---------------------------------------------------------------

   @Nullable
   private static HeroPublicSnapshot view(Player player) {
      if (player instanceof HeroPlayer heroPlayer) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         return snapshot != null && snapshot.heroId() == HeroId.DIO ? snapshot : null;
      }

      return null;
   }

   /** Both sides: the stand is out (or coming out). */
   public static boolean standOut(Player player) {
      if (player == null) {
         return false;
      }

      if (!player.level().isClientSide) {
         DioState state = DioState.of(player);
         return state != null && state.standOut();
      }

      HeroPublicSnapshot snapshot = view(player);
      return snapshot != null && snapshot.heroFlag(DioRules.FLAG_STAND);
   }

   private static boolean ulting(Player player) {
      if (!player.level().isClientSide) {
         DioState state = DioState.of(player);
         return state != null && state.action == HeroAction.ROAD_ROLLER;
      }

      HeroPublicSnapshot snapshot = view(player);
      return snapshot != null && snapshot.heroFlag(DioRules.FLAG_ULT);
   }

   @Override
   public boolean sustainsAirborne(Player player) {
      return player != null && ulting(player);
   }

   @Override
   public boolean claimsBlockAttack(Player player) {
      return false;
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return player == null || !standOut(player);
   }

   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      return button == MouseButton.PRIMARY && standOut(player) ? HeroAction.PRIMARY_ATTACK : null;
   }

   @Override
   public HeroAction guardAction(Player player) {
      return standOut(player) ? HeroAction.GUARD : null;
   }

   @Override
   public boolean blocksHandSwap(Player player) {
      return standOut(player);
   }

   @Override
   public boolean interceptsProjectiles(Player self) {
      return true;
   }

   // --- server --------------------------------------------------------------------------

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         return false;
      }

      DioState state = DioState.ensure(player);
      return switch (action) {
         case JUMP, AIR_STEP -> false;
         case PRIMARY_ATTACK -> state.action == null || state.action == HeroAction.PRIMARY_ATTACK;
         case GUARD -> state.standOut();
         default -> true;
      };
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      DioSounds.play(player.serverLevel(), player.getEyePosition(), DioSounds.REFUSE, 0.7F, 1.0F);
   }

   @Override
   public void tick(ServerPlayer player) {
      DioState state = DioState.ensure(player);
      if (player.tickCount % 20 == 0) {
         applyPassives(player);
      }

      tickBody(player, state);
      for (int i = 0; i < state.cooldowns.length; i++) {
         if (state.cooldowns[i] > 0) {
            state.cooldowns[i]--;
         }
      }

      if (state.sinceStrikeEnd >= 0 && state.sinceStrikeEnd < 10000) {
         state.sinceStrikeEnd++;
      }

      if (state.standPhase != DioRules.STAND_NONE) {
         DioCombat.tickStand(state);
      }

      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         abort(player, state);
      } else if (state.action != null) {
         state.elapsed++;
         switch (state.action) {
            case PRIMARY_ATTACK -> DioCombat.tickCombo(player, state);
            case MUDA_BARRAGE -> DioCombat.tickBarrage(player, state);
            case KNIFE_THROW -> DioAbilitiesRuntime.tickKnives(player, state);
            case TIME_STOP -> DioAbilitiesRuntime.tickTimeStop(player, state);
            case VAMPIRE_DRAIN -> DioAbilitiesRuntime.tickDrain(player, state);
            case ROAD_ROLLER -> DioAbilitiesRuntime.tickRoller(player, state);
            default -> state.clearAction();
         }
      }

      DioTimeStop.tick(player);
   }

   /** Sunlight, blood, regeneration. */
   private static void tickBody(ServerPlayer player, DioState state) {
      if (state.infiniteBlood) {
         state.blood = DioRules.BLOOD_MAX;
      }

      boolean sun = player.isAlive() && !player.isSpectator() && inSunlight(player);
      state.inSun = sun;
      Level level = player.level();
      if (sun) {
         state.regenTimer = 0;
         if (++state.bloodTimer >= DioRules.BLOOD_SUN_INTERVAL) {
            state.bloodTimer = 0;
            state.blood = state.infiniteBlood ? DioRules.BLOOD_MAX : DioRules.clampBlood(state.blood - 1);
         }

         if (++state.burnTimer >= DioRules.SUN_BURN_INTERVAL) {
            state.burnTimer = 0;
            DioSounds.play(player.serverLevel(), player.position().add(0, 1.2, 0), DioSounds.SUN_SIZZLE, 0.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            if (state.blood <= 0 && !player.isCreative()) {
               player.hurt(player.damageSources().magic(), DioRules.SUN_BURN_DAMAGE);
            }
         }

         return;
      }

      state.burnTimer = 0;
      if (!level.isDay() && ++state.bloodTimer >= DioRules.BLOOD_NIGHT_INTERVAL) {
         state.bloodTimer = 0;
         state.blood = DioRules.clampBlood(state.blood + 1);
      }

      if (player.isAlive() && ++state.regenTimer >= DioRules.REGEN_INTERVAL) {
         state.regenTimer = 0;
         if (player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0F);
         }
      }
   }

   /** Open daylight on the bare head (a helmet is a hat). */
   public static boolean inSunlight(Player player) {
      Level level = player.level();
      if (!level.dimensionType().hasSkyLight() || !level.isDay() || level.isRaining() || level.isThundering()) {
         return false;
      }

      if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || player.isInWaterOrBubble()) {
         return false;
      }

      return level.canSeeSky(BlockPos.containing(player.getEyePosition()));
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      DioState state = DioState.ensure(player);
      if (!pressed) {
         if (action == HeroAction.MUDA_BARRAGE) {
            state.barrageHeld = false;
         } else if (action == HeroAction.GUARD) {
            state.guardHeld = false;
         }

         return;
      }

      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         this.onInputRefused(player, action);
         return;
      }

      int cd = DioAbilities.cooldownIndex(action);
      boolean cooling = cd >= 0 && state.cooldowns[cd] > 0;
      switch (action) {
         case THE_WORLD -> {
            boolean standAction = state.action == HeroAction.PRIMARY_ATTACK || state.action == HeroAction.MUDA_BARRAGE;
            if (cooling || state.busy() && !standAction) {
               this.onInputRefused(player, action);
               return;
            }

            DioCombat.toggle(player, state);
            state.cooldowns[DioRules.CD_STAND] = STAND_TOGGLE_COOLDOWN;
         }
         case MUDA_BARRAGE -> {
            if (cooling || state.busy()) {
               this.onInputRefused(player, action);
               return;
            }

            DioCombat.startBarrage(player, state);
         }
         case KNIFE_THROW -> {
            if (cooling || state.busy()) {
               this.onInputRefused(player, action);
               return;
            }

            DioAbilitiesRuntime.startKnives(player, state);
         }
         case TIME_STOP -> {
            TimeStopField own = DioTimeStop.fieldOf(player.getUUID());
            if (own != null && !own.scripted) {
               DioTimeStop.hurry(player);
               return;
            }

            if (cooling || state.busy() || own != null || state.blood < DioRules.TIME_STOP_MIN_BLOOD) {
               this.onInputRefused(player, action);
               return;
            }

            DioAbilitiesRuntime.startTimeStop(player, state);
         }
         case VAMPIRE_DRAIN -> {
            if (cooling || state.busy() || !DioAbilitiesRuntime.startDrain(player, state)) {
               this.onInputRefused(player, action);
            }
         }
         case ROAD_ROLLER -> {
            if (cooling || state.busy() || state.blood < DioRules.ROLLER_BLOOD || DioTimeStop.fieldOf(player.getUUID()) != null) {
               this.onInputRefused(player, action);
               return;
            }

            DioAbilitiesRuntime.startRoller(player, state);
         }
         case PRIMARY_ATTACK -> {
            if (state.action != HeroAction.MUDA_BARRAGE) {
               DioCombat.press(player, state);
            }
         }
         case GUARD -> state.guardHeld = true;
         default -> {
         }
      }
   }

   /** Ends whatever DIO was doing (control, death, dimension change). */
   static void abort(ServerPlayer player, DioState state) {
      if (state.action == null) {
         return;
      }

      switch (state.action) {
         case TIME_STOP -> DioAbilitiesRuntime.cancelTimeStop(player, state);
         case VAMPIRE_DRAIN -> DioAbilitiesRuntime.stopDrain(player, state);
         case ROAD_ROLLER -> {
            DioAbilitiesRuntime.cancelRoller(player, state);
            TimeStopField field = DioTimeStop.fieldOf(player.getUUID());
            if (field != null && field.scripted) {
               DioTimeStop.end(field, ReleaseReason.INTERRUPTED);
            }
         }
         default -> {
         }
      }

      state.clearAction();
   }

   @Override
   public void enter(ServerPlayer player) {
      DioState state = DioState.ensure(player);
      state.blood = ENTER_BLOOD;
      applyPassives(player);
      player.setHealth(player.getMaxHealth());
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      if (player != null) {
         applyPassives(player);
      }
   }

   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      DioState state = DioState.of(player);
      if (state != null && state.action == HeroAction.VAMPIRE_DRAIN && amount >= DioRules.DRAIN_BREAK_DAMAGE) {
         DioAbilitiesRuntime.stopDrain(player, state);
      }
   }

   /** The World's guard: hits from the front lose 70 %. */
   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      DioState state = DioState.of(self);
      Vec3 from = source.getSourcePosition();
      if (state == null || from == null || !DioCombat.guarding(state) || !covers(self, from)) {
         return DamageAbsorb.PASS;
      }

      Vec3 contact = self.getEyePosition().add(self.getLookAngle().scale(0.9));
      DioNet.fx(self, DioFxS2CPacket.GUARD_BLOCK, contact, from.subtract(contact).normalize(), raw, 0);
      DioSounds.play(self.serverLevel(), contact, DioSounds.GUARD, 1.0F, 0.9F + self.getRandom().nextFloat() * 0.2F);
      return DamageAbsorb.reduceTo(raw * DioRules.GUARD_FACTOR);
   }

   /** The World swats projectiles aside while guarding. */
   @Override
   public ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) {
      DioState state = DioState.of(self);
      if (state == null || !DioCombat.guarding(state) || !covers(self, projectile.position())) {
         return ProjectileResponse.PASS;
      }

      Vec3 at = projectile.position();
      DioNet.fx(self, DioFxS2CPacket.DEFLECT, at, projectile.getDeltaMovement().normalize(), 1.0F, 0);
      DioSounds.play(self.serverLevel(), at, DioSounds.DEFLECT, 1.0F, 1.0F);
      return ProjectileResponse.DEFLECT;
   }

   private static boolean covers(ServerPlayer self, Vec3 from) {
      Vec3 look = self.getLookAngle();
      return DioRules.guardCovers(look.x, look.z, from.x - self.getX(), from.z - self.getZ());
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      DioState state = rawState(player);
      if (state != null) {
         stopEverything(player, state);
      }
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      DioState state = DioState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      DioState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      DioState previous = rawState(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   /** Debug (/hero cooldown reset, PR #87): every DIO timer ends; a running stand, stop or drain stays as it is. */
   @Override
   public boolean resetCooldowns(ServerPlayer player) {
      DioState state = DioState.of(player);
      if (state == null) {
         return false;
      }

      java.util.Arrays.fill(state.cooldowns, 0);
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      DioState state = rawState(player);
      if (state != null) {
         stopEverything(player, state);
      } else {
         DioTimeStop.cleanupCaster(player.getUUID());
      }

      if (reason == CleanupReason.HERO_CHANGE) {
         removePassives(player);
      }
   }

   private static void stopEverything(ServerPlayer player, DioState state) {
      abort(player, state);
      DioAbilitiesRuntime.stopDrain(player, state);
      DioTimeStop.cleanupCaster(player.getUUID());
      state.standPhase = DioRules.STAND_NONE;
      state.standElapsed = 0;
      state.guardHeld = false;
      state.comboStep = -1;
      state.lastStep = -1;
      state.sinceStrikeEnd = -1;
   }

   @Nullable
   private static DioState rawState(Player player) {
      return player instanceof HeroPlayer heroPlayer && heroPlayer.viltrumitecore$getHeroState() instanceof DioState state ? state : null;
   }

   @Override
   public int heldTargetId(Player holder) {
      if (holder.level().isClientSide) {
         HeroPublicSnapshot snapshot = view(holder);
         return snapshot != null && snapshot.heroFlag(DioRules.FLAG_DRAIN_HOLD) ? snapshot.controlTargetId() : -1;
      }

      DioState state = DioState.of(holder);
      return state != null && state.action == HeroAction.VAMPIRE_DRAIN ? state.drainTargetId : -1;
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      DioState state = DioState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.DIO, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }

      TimeStopField field = DioTimeStop.fieldOf(player.getUUID());
      int flags = 0;
      if (state.standOut()) flags |= 1 << DioRules.FLAG_STAND;
      if (field != null) flags |= 1 << DioRules.FLAG_TIME_STOP;
      if (state.inSun) flags |= 1 << DioRules.FLAG_SUN;
      if (DioCombat.guarding(state)) flags |= 1 << DioRules.FLAG_GUARD;
      if (field != null && field.resuming()) flags |= 1 << DioRules.FLAG_RESUMING;
      if (state.action == HeroAction.ROAD_ROLLER) flags |= 1 << DioRules.FLAG_ULT;
      if (state.finisher) flags |= 1 << DioRules.FLAG_FINISHER;
      if (state.action == HeroAction.VAMPIRE_DRAIN) flags |= 1 << DioRules.FLAG_DRAIN_HOLD;
      if (state.infiniteBlood) flags |= 1 << DioRules.FLAG_INFINITE_BLOOD;
      int[] extra = new int[DioRules.EXTRA_COUNT];
      extra[DioRules.EX_STAND_PHASE] = state.standPhase;
      extra[DioRules.EX_STAND_ELAPSED] = Mth.clamp(state.standElapsed, 0, 1000);
      extra[DioRules.EX_FIELD_LEFT] = field == null ? 0 : field.left();
      extra[DioRules.EX_FIELD_TOTAL] = field == null ? 0 : field.duration;
      extra[DioRules.EX_COMBO_STEP] = Math.max(0, state.comboStep);
      Vec3 target = state.action == HeroAction.ROAD_ROLLER && state.rollerTarget != null ? state.rollerTarget : field != null ? field.center : null;
      int actionId = state.action == null ? -1 : state.action.ordinal();
      return new HeroPublicSnapshot(HeroId.DIO, actionId, state.elapsed, state.length, 0, false, 0, 0, false, false, 0, 0,
         state.action == HeroAction.VAMPIRE_DRAIN ? state.drainTargetId : -1, state.cooldowns.clone(), state.busy(), -1L, target,
         state.blood * 10, state.inSun, flags, extra, 0, 0);
   }

   // --- passives ------------------------------------------------------------------------

   static void applyPassives(ServerPlayer player) {
      add(player, Attributes.MAX_HEALTH, HEALTH_ID, "DIO vitality", DioRules.BONUS_MAX_HEALTH, AttributeModifier.Operation.ADDITION);
      add(player, Attributes.ARMOR, ARMOR_ID, "DIO body", DioRules.BONUS_ARMOR, AttributeModifier.Operation.ADDITION);
      add(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, "DIO strength", DioRules.BONUS_ATTACK, AttributeModifier.Operation.ADDITION);
      add(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "DIO speed", DioRules.BONUS_SPEED, AttributeModifier.Operation.MULTIPLY_BASE);
   }

   private static void add(ServerPlayer player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, op));
      }
   }

   static void removePassives(ServerPlayer player) {
      remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
      remove(player, Attributes.ARMOR, ARMOR_ID);
      remove(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
      remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   private static void remove(ServerPlayer player, Attribute attribute, UUID id) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null) {
         instance.removeModifier(id);
      }
   }
}
