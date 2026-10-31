package dev.baranhan.viltrumitecore.hero.regulus;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.LegacyKit;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.ScanInfo;
import dev.baranhan.viltrumitecore.hero.ScanLine;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusFxS2CPacket;
import dev.baranhan.viltrumitecore.hero.regulus.net.RegulusNet;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Regulus Corneas, Archbishop of Greed — the Reborn kit (spec docs/design/2026-10-30-regulus-reborn-design.md).
 * Authority of Greed: he stops the time of his body (Lion's Heart) and of what he touches (sand, air,
 * blocks), and hides his stopped heart in the living (Little King). Everything is resolved here on the
 * server; clients draw what the snapshot and {@link RegulusFxS2CPacket} report.
 */
public final class RegulusHero implements HeroDefinition {
   public static final RegulusHero INSTANCE = new RegulusHero();
   static final UUID HEALTH_ID = UUID.fromString("4e5a1c0b-7d3e-4a7c-9b1f-2c6d8e0f1b01");
   static final UUID ARMOR_ID = UUID.fromString("4e5a1c0b-7d3e-4a7c-9b1f-2c6d8e0f1b02");

   private RegulusHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.REGULUS;
   }

   @Override
   public boolean allowsFlight(Player player) {
      return false;
   }

   @Override
   public boolean allowsLegacyAbilities(Player player) {
      return false;
   }

   /** Global balance profile (BalanceTable): Regulus outside the stopped heart (the Lion absorbs by itself); tier S. */
   @Override
   public dev.baranhan.viltrumitecore.balance.HeroBalanceProfile balanceProfile(Player player) {
      return dev.baranhan.viltrumitecore.balance.BalanceTable.LION;
   }

   @Override
   public boolean allowsAbilityPages(Player player) {
      return false;
   }

   @Override
   public boolean ownsAbility(String abilityId) {
      return RegulusAbilities.owns(abilityId);
   }

   @Override
   public List<ViltrumiteAbility> panelAbilities() {
      return RegulusAbilities.panelAbilities();
   }

   @Override
   public HeroLines lines() {
      return RegulusLines.LINES;
   }

   @Override
   public String[] heroInputSlots() {
      return RegulusAbilities.OWN.clone();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return RegulusAbilities.actionFor(abilityId);
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return RegulusAbilities.icon(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return RegulusAbilities.defaultLoadout();
   }

   @Override
   public String[][] previousDefaultLoadouts() {
      return RegulusAbilities.previousDefaultLoadouts();
   }

   @Override
   public String[] migrateLoadout(String[] slots) {
      return RegulusAbilities.migrate(slots);
   }

   // --- protection -------------------------------------------------------------------------------
   /** Lion's Heart and the judgement: the time of his body is stopped (both sides via the snapshot). */
   static boolean stopped(@Nullable RegulusState state) {
      return state != null && (state.lion || state.ult != null);
   }

   private static boolean stoppedOnWire(Player player) {
      RegulusState state = RegulusState.of(player);
      if (state != null) {
         return stopped(state);
      }

      if (player instanceof HeroPlayer heroPlayer && heroPlayer.getHeroSnapshot() != null && heroPlayer.getHeroSnapshot().heroId() == HeroId.REGULUS) {
         HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
         return snapshot.lionActive() || RegulusFlags.has(snapshot.heroFlags(), RegulusFlags.ULT);
      }

      return false;
   }

   /** Pure rule (tests): the Lion is absolute invulnerability. */
   public static boolean absolutelyInvulnerable(boolean lion, boolean ult) {
      return lion || ult;
   }

   @Override
   public boolean isAbsolutelyInvulnerable(Player self) {
      return self != null && stoppedOnWire(self);
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return !(target instanceof Player player) || !stoppedOnWire(player);
   }

   @Override
   public boolean knockbackImmune(Player player) {
      return player != null && stoppedOnWire(player);
   }

   @Override
   public boolean preventsExhaustion(Player player) {
      return player != null && stoppedOnWire(player);
   }

   @Override
   public DamageAbsorb absorbIncoming(ServerPlayer self, DamageSource source, float raw) {
      RegulusState state = RegulusState.of(self);
      if (state == null) {
         return DamageAbsorb.PASS;
      }

      if (stopped(state)) {
         // Global balance (BalanceTable.DIVINE_LION_STRAIN): divine hits, and every hit of the one
         // hero ranked above S (the Sword Saint), strain even a hosted heart.
         state.strain = RegulusRules.strainFromHit(state.strain, overwhelms(source));
         LionsHeart.recoil(self, state, source, raw);
         return DamageAbsorb.ABSORBED;
      }

      if (RightOfReply.windowOpen(state) && source.getEntity() != self) {
         RightOfReply.refuse(self, state, source, raw);
         return DamageAbsorb.ABSORBED;
      }

      return DamageAbsorb.PASS;
   }

   /** Divine damage, or a hit of a hero whose balance tier is above S (tier S+). */
   static boolean overwhelms(DamageSource source) {
      if (dev.baranhan.viltrumitecore.hero.DivineDamage.isDivine(source)) {
         return true;
      }

      return source.getEntity() instanceof Player attacker && dev.baranhan.viltrumitecore.hero.HeroRegistry.get(attacker).balanceProfile(attacker).tier()
         .above(dev.baranhan.viltrumitecore.balance.HeroTier.S);
   }

   @Override
   public ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) {
      RegulusState state = RegulusState.of(self);
      if (state == null || projectile == null || projectile.getOwner() == self) {
         return ProjectileResponse.PASS;
      }

      if (stopped(state)) {
         return ProjectileResponse.CATCH;
      }

      return RightOfReply.windowOpen(state) ? ProjectileResponse.REFLECT : ProjectileResponse.PASS;
   }

   /** CATCH: the projectile stops dead in the air before him (fully handled here). */
   @Override
   public boolean captureProjectile(ServerPlayer self, Projectile projectile) {
      RegulusState state = RegulusState.of(self);
      if (state == null || !stopped(state)) {
         return false;
      }

      LionsHeart.stopProjectile(self, state, projectile);
      return true;
   }

   @Override
   public boolean interceptsProjectiles(Player self) {
      return true;
   }

   // --- movement ---------------------------------------------------------------------------------
   @Override
   public boolean cancelsFallDamage(Player player) {
      return true;
   }

   @Override
   public void onLanded(ServerPlayer player, float fallDistance) {
      if (fallDistance > 3.0F && player.level() instanceof ServerLevel level) {
         RegulusNet.fx(level, RegulusFxS2CPacket.LAND, player, -1, player.position(), Vec3.ZERO, Math.min(fallDistance, 40.0F), 0);
      }
   }

   @Override
   public float superJumpVelocity(Player player) {
      return RegulusRules.SUPER_JUMP_VELOCITY;
   }

   @Override
   public int airJumps(Player player) {
      return player != null && stoppedOnWire(player) ? RegulusRules.LION_AIR_STEPS : 0;
   }

   // --- input ------------------------------------------------------------------------------------
   /** LMB with an empty main hand is Greed's Touch; MMB (the heart key) is the Little King. */
   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      if (button == MouseButton.PRIMARY && player != null && player.getMainHandItem().isEmpty()) {
         return HeroAction.PRIMARY_ATTACK;
      }

      return button == MouseButton.MIDDLE ? HeroAction.ASSIGN_HEART : null;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      return player.isAlive() && (action != HeroAction.PRIMARY_ATTACK || player.getMainHandItem().isEmpty());
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      RegulusState state = RegulusState.of(player);
      if (state != null && action != HeroAction.PRIMARY_ATTACK) {
         refuse(player, state);
      }
   }

   /** The short dismissive gesture and a dry click: "no". */
   static void refuse(ServerPlayer player, RegulusState state) {
      if (!state.busy()) {
         state.startMove(RegulusMove.REFUSE);
      }

      RegulusSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), RegulusSounds.REFUSE, 0.5F, 1.0F);
      if (player.level() instanceof ServerLevel level) {
         RegulusNet.fx(level, RegulusFxS2CPacket.REFUSE, player, -1, player.getEyePosition());
      }
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (!pressed || !player.isAlive()) {
         return;
      }

      RegulusState state = RegulusState.ensure(player);
      if (HeroDamage.isAnchored(player)) {
         return;
      }

      // the Lion's off-toggle always reaches the body; everything else waits for a committed move
      if (action == HeroAction.LIONS_HEART && state.lion && state.move != RegulusMove.ULT) {
         LionsHeart.turnOff(player, state, true);
         return;
      }

      if (state.move != null && state.move.locksCasting()) {
         if (action != HeroAction.PRIMARY_ATTACK) {
            refuse(player, state);
         }

         return;
      }

      if (state.move == RegulusMove.REFUSE) {
         state.clearMove();
      }

      switch (action) {
         case PRIMARY_ATTACK -> GreedsTouch.press(player, state);
         case ASSIGN_HEART -> LittleKing.press(player, state);
         case LIONS_HEART -> LionsHeart.press(player, state);
         case DEBRIS_KICK -> StoppedSand.press(player, state);
         case MANIA -> AirSeverance.press(player, state);
         case GREEDS_EMBRACE -> Trample.press(player, state);
         case COUNTER -> RightOfReply.press(player, state);
         case ULTIMATE -> KingdomOfOne.press(player, state);
         default -> {
         }
      }
   }

   // --- tick -------------------------------------------------------------------------------------
   @Override
   public void tick(ServerPlayer player) {
      RegulusState state = RegulusState.ensure(player);
      applyPassives(player);
      state.tickCooldowns();
      if (!player.isAlive()) {
         return;
      }

      boolean anchored = HeroDamage.isAnchored(player);
      LionsHeart.tick(player, state);
      RegulusMove move = state.move;
      if (move != null && !anchored) {
         switch (move) {
            case LION_ON -> LionsHeart.tickMove(player, state);
            case TOUCH_BACKHAND, TOUCH_PALM, TOUCH_FLICK -> GreedsTouch.tickMove(player, state);
            case SAND -> StoppedSand.tickMove(player, state);
            case AIR_CUT, AIR_CUT_VERTICAL -> AirSeverance.tickMove(player, state);
            case TRAMPLE -> Trample.tickMove(player, state);
            case REPLY, REPLY_STRIKE -> RightOfReply.tickMove(player, state);
            case LITTLE_KING -> LittleKing.tickMove(player, state);
            case ULT -> KingdomOfOne.tickMove(player, state);
            default -> {
            }
         }

         if (state.move == move && ++state.moveTick >= move.length()) {
            state.clearMove();
            onMoveEnd(player, state, move);
         }
      }

      GreedsTouch.tickRuptures(player, state);
      StoppedGrains.tick(player, state);
      AirSeverance.tick(player, state);
      LittleKing.tick(player, state);
      if (state.ult != null && state.move != RegulusMove.ULT) {
         // a scene without its move (seizure, external clear): end it safely
         KingdomOfOne.finish(player, state);
      }
   }

   private static void onMoveEnd(ServerPlayer player, RegulusState state, RegulusMove move) {
      switch (move) {
         case TOUCH_BACKHAND, TOUCH_PALM, TOUCH_FLICK -> GreedsTouch.onEnd(player, state, move);
         case REPLY, REPLY_STRIKE -> RightOfReply.onEnd(player, state, move);
         case ULT -> KingdomOfOne.onEnd(player, state, move);
         default -> {
         }
      }
   }

   // --- lifecycle --------------------------------------------------------------------------------
   @Override
   public void enter(ServerPlayer player) {
      RegulusState state = new RegulusState();
      ((HeroPlayer) player).viltrumitecore$setHeroState(state);
      applyPassives(player);
      player.setHealth(player.getMaxHealth());
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      if (player != null) {
         // a fresh body starts whole
         applyPassives(player);
         player.setHealth(player.getMaxHealth());
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      RegulusState state = RegulusState.of(player);
      if (state != null) {
         clearTransient(player, state);
      }
   }

   @Override
   public boolean resetCooldowns(ServerPlayer player) {
      RegulusState state = RegulusState.of(player);
      if (state == null) {
         return false;
      }
      // Regulus Reborn keeps its timers in RegulusState (the HUD reads them from the snapshot that
      // HeroCommands pushes right after); the old Evangelium book is an inert keepsake with no timer.
      state.clearCooldowns();
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      RegulusState state = RegulusState.of(player);
      if (state != null) {
         clearTransient(player, state);
         if (reason == CleanupReason.ERASED) {
            ((HeroPlayer) player).viltrumitecore$setHeroState(state.respawnCopy());
         }
      } else {
         removeSpeed(player);
      }

      if (reason == CleanupReason.HERO_CHANGE) {
         removePassives(player);
         LegacyKit.reset(player);
      }
   }

   /** Every transient effect ends (scene, control, subjects, Lion, projectiles); cooldowns stay. Idempotent. */
   static void clearTransient(ServerPlayer player, RegulusState state) {
      KingdomOfOne.releaseEverywhere(player, state);
      KingdomOfOne.finish(player, state);
      if (state.lion && player.level() instanceof ServerLevel level) {
         RegulusNet.fx(level, RegulusFxS2CPacket.LION_OFF, player, -1, player.position().add(0.0, 1.0, 0.0));
      }

      LittleKing.releaseAll(player, state, false);
      state.resetTransient();
      removeSpeed(player);
   }

   private static void removeSpeed(ServerPlayer player) {
      LionsHeart.speed(player, false);
      KingdomOfOne.lock(player, false);
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      RegulusState state = RegulusState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      RegulusState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      RegulusState previous = RegulusState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- passives ---------------------------------------------------------------------------------
   static void applyPassives(ServerPlayer player) {
      addModifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Regulus body", RegulusRules.BONUS_HEALTH);
      addModifier(player, Attributes.ARMOR, ARMOR_ID, "Regulus body armor", RegulusRules.BONUS_ARMOR);
   }

   static void removePassives(ServerPlayer player) {
      removeModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
      removeModifier(player, Attributes.ARMOR, ARMOR_ID);
      removeSpeed(player);
      if (player.getHealth() > player.getMaxHealth()) {
         player.setHealth(player.getMaxHealth());
      }
   }

   private static void addModifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
      }
   }

   private static void removeModifier(ServerPlayer player, Attribute attribute, UUID id) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null) {
         instance.removeModifier(id);
      }
   }

   // --- scan / snapshot --------------------------------------------------------------------------
   @Override
   public ScanInfo scanInfo(Player self) {
      return scanInfoFor(self != null && stoppedOnWire(self));
   }

   public static ScanInfo scanInfoFor(boolean lionActive) {
      if (!lionActive) {
         return ScanInfo.EMPTY;
      }

      return new ScanInfo(List.of(ScanLine.of("scan.viltrumitecore.regulus.stopped_time")), List.of(), List.of());
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      RegulusState state = RegulusState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.REGULUS, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }

      int hostId = -1;
      boolean hostAlive = false;
      if (player instanceof ServerPlayer server && state.host != null) {
         var host = ((ServerLevel) server.level()).getEntity(state.host);
         if (host != null) {
            hostId = host.getId();
            hostAlive = LittleKing.hostAlive(server, state);
         }
      }

      return compose(state, hostId, hostAlive, KingdomOfOne.targetEntityId(state));
   }

   /** Pure wire composer (tested); see {@link RegulusFlags} for the layout. */
   static HeroPublicSnapshot compose(RegulusState state, int hostId, boolean hostAlive, int ultTargetId) {
      int[] cooldowns = new int[HeroPublicSnapshot.COOLDOWN_COUNT];
      for (int i = 0; i < cooldowns.length; i++) {
         cooldowns[i] = state.cooldown(i);
      }

      int flags = 0;
      flags = RegulusFlags.with(flags, RegulusFlags.LION, state.lion);
      flags = RegulusFlags.with(flags, RegulusFlags.HOST_ALIVE, hostAlive);
      flags = RegulusFlags.with(flags, RegulusFlags.SEIZED, state.seized());
      flags = RegulusFlags.with(flags, RegulusFlags.REPLY_WINDOW, RightOfReply.windowOpen(state));
      flags = RegulusFlags.with(flags, RegulusFlags.TRAMPLE, state.move == RegulusMove.TRAMPLE);
      flags = RegulusFlags.with(flags, RegulusFlags.ULT, state.ult != null);
      flags = RegulusFlags.with(flags, RegulusFlags.TARGET_HELD, state.ult != null && (state.ult.frozen || state.ult.held));
      flags = RegulusFlags.with(flags, RegulusFlags.STRAIN_WARN, state.strain >= RegulusRules.STRAIN_WARN);
      flags = RegulusFlags.with(flags, RegulusFlags.LION_LOCKED, state.lionLock > 0);
      flags = RegulusFlags.with(flags, RegulusFlags.REPLY_ARMED, state.move == RegulusMove.REPLY);
      int[] extra = new int[RegulusFlags.EXTRA_COUNT];
      extra[RegulusFlags.EXTRA_COMBO] = state.comboStep + 1;
      extra[RegulusFlags.EXTRA_CHAIN] = state.chainLeft;
      extra[RegulusFlags.EXTRA_SUBJECTS] = state.subjects.size();
      extra[RegulusFlags.EXTRA_HOP] = state.hopTimer;
      extra[RegulusFlags.EXTRA_LION_LOCK] = state.lionLock;
      extra[RegulusFlags.EXTRA_RECOIL] = state.recoilPulse & 0xFFFF;
      extra[RegulusFlags.EXTRA_SEED] = state.seed & 0x7FFF;
      extra[RegulusFlags.EXTRA_LION_TICKS] = Math.min(30000, state.lionTicks);
      extra[RegulusFlags.EXTRA_HOPS] = state.hops & 0xFFFF;
      RegulusMove move = state.move;
      Vec3 target = state.ult != null ? state.ult.anchor : null;
      return new HeroPublicSnapshot(HeroId.REGULUS, move == null ? -1 : move.ordinal(), move == null ? 0 : state.moveTick, move == null ? 0 : move.length(),
         state.subjects.size(), state.lion, 0, 0, false, false, 0, 0, hostId, cooldowns, move != null, -1L, target, state.strain,
         state.lionLock > 0, flags, extra, 0, ultTargetId);
   }

   // --- contextual interactions ------------------------------------------------------------------

   /**
    * reid_strains_lions_heart: a Reid stroke does not wound the stopped body, it strains the stopped heart. The
    * strain may tip into the seizure (§5.0): the Lion gives out, he reels and is locked. Only the Lion (the
    * ultimate's own stop is untouched).
    */
   @Override
   public boolean react(ServerPlayer self, dev.baranhan.viltrumitecore.interaction.InteractionReaction reaction,
      dev.baranhan.viltrumitecore.interaction.InteractionContext ctx) {
      RegulusState state = RegulusState.of(self);
      if (reaction != dev.baranhan.viltrumitecore.interaction.InteractionReaction.LION_STRAINED || state == null || !state.lion || state.ult != null) {
         return false;
      }
      state.strain = strainedBy(state.strain, dev.baranhan.viltrumitecore.interaction.InteractionTuning.REID_STRAIN);
      RegulusSounds.play(self.level(), self.position().add(0.0, 1.4, 0.0), RegulusSounds.STRAIN, 1.2F, 0.7F);
      if (RegulusRules.seizes(state.strain)) {
         LionsHeart.seizure(self, state);
      }
      return true;
   }

   /** Pure rule (tests): the strain a Reid stroke leaves on the stopped heart. */
   public static int strainedBy(int strain, int amount) {
      return RegulusRules.strainAdd(strain, amount);
   }
}
