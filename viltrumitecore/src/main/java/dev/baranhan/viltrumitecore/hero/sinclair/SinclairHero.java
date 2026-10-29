package dev.baranhan.viltrumitecore.hero.sinclair;

import dev.baranhan.viltrumitecore.ability.ViltrumiteAbility;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.LegacyKit;
import dev.baranhan.viltrumitecore.hero.MouseButton;
import dev.baranhan.viltrumitecore.hero.ProjectileResponse;
import dev.baranhan.viltrumitecore.hero.control.ControlKind;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.Assistant;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.AudienceParticipation;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.Misdirection;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.Prestige;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.RabbitHex;
import dev.baranhan.viltrumitecore.hero.sinclair.ability.VexingBolt;
import dev.baranhan.viltrumitecore.hero.sinclair.act.ActScene;
import dev.baranhan.viltrumitecore.hero.sinclair.act.SinclairRepertoire;
import dev.baranhan.viltrumitecore.hero.sinclair.entity.SinclairOwned;
import dev.baranhan.viltrumitecore.hero.speech.HeroLineTrigger;
import dev.baranhan.viltrumitecore.hero.speech.HeroLines;
import dev.baranhan.viltrumitecore.hero.speech.HeroSpeech;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/** The Magnificent Sinclair (spec docs/design/2026-10-28-sinclair-design.md). */
public final class SinclairHero implements HeroDefinition {
   public static final SinclairHero INSTANCE = new SinclairHero();
   static final UUID HEALTH_ID = UUID.fromString("5c1a7f30-2b7e-4c55-9a31-7f0de1b0a001");
   static final UUID ARMOR_ID = UUID.fromString("5c1a7f30-2b7e-4c55-9a31-7f0de1b0a002");

   private SinclairHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.SINCLAIR;
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
   public boolean ownsAbility(String abilityId) {
      return SinclairAbilities.owns(abilityId);
   }

   @Override
   public List<ViltrumiteAbility> panelAbilities() {
      return SinclairAbilities.panelAbilities();
   }

   @Override
   public HeroLines lines() {
      return SinclairLines.LINES;
   }

   @Override
   public String[] heroInputSlots() {
      return SinclairAbilities.OWN.clone();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return SinclairAbilities.actionFor(abilityId);
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return SinclairAbilities.icon(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return SinclairAbilities.defaultLoadout();
   }

   @Override
   public boolean allowsExternalControl(LivingEntity target, ControlKind kind) {
      return true;
   }

   /** §5.0: LMB with an empty main hand is Prestige (both sides; the client reads only its own hand). */
   @Override
   public HeroAction mouseAction(MouseButton button, Player player) {
      if (button == MouseButton.PRIMARY && player != null && player.getMainHandItem().isEmpty() && !HexMarks.hexed(player)) {
         return HeroAction.PRIMARY_ATTACK;
      }

      return null;
   }

   /** Another Sinclair copies the fallback number: no contract of his own (§6.2). */
   @Override
   public dev.baranhan.viltrumitecore.hero.act.StageAct signatureAct(Player self) {
      return null;
   }

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      return player.isAlive() && (action != HeroAction.PRIMARY_ATTACK || player.getMainHandItem().isEmpty());
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      SinclairState state = SinclairState.of(player);
      if (state != null && action != HeroAction.PRIMARY_ATTACK) {
         refuse(player, state);
      }
   }

   /** §5.1: the short "no" with the wand and a quiet refusal sound. */
   public static void refuse(ServerPlayer player, SinclairState state) {
      if (!state.busy()) {
         state.startMove(SinclairMove.REFUSE);
      }

      SinclairSounds.play(player.level(), player.position().add(0.0, 1.4, 0.0), SinclairSounds.REFUSE, 0.5F, 1.0F);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      SinclairState state = SinclairState.ensure(player);
      if (action == HeroAction.PRIMARY_ATTACK) {
         state.triggerHeld = pressed;
         if (pressed) {
            Misdirection.breakVeil(player, state, true);
         }

         return;
      }

      if (!pressed || !player.isAlive()) {
         return;
      }

      if (state.move != null && state.move.locksCasting() && action != HeroAction.AUDIENCE_PARTICIPATION) {
         refuse(player, state);
         return;
      }

      SinclairMove before = state.move;
      switch (action) {
         case VEXING_BOLT -> VexingBolt.press(player, state);
         case SPECTRAL_ASSISTANT -> Assistant.press(player, state);
         case RABBIT_HEX -> RabbitHex.press(player, state);
         case MISDIRECTION -> Misdirection.press(player, state);
         case AUDIENCE_PARTICIPATION -> AudienceParticipation.press(player, state);
         default -> {
            return;
         }
      }

      SinclairMove started = state.move != before ? state.move : null;
      if (started != null && started != SinclairMove.REFUSE) {
         if (action != HeroAction.MISDIRECTION) {
            Misdirection.breakVeil(player, state, false);
         }

         switch (started) {
            case SUMMON -> HeroSpeech.ability(player, SinclairAbilities.SPECTRAL_ASSISTANT);
            case HEX_CAST -> HeroSpeech.ability(player, SinclairAbilities.RABBIT_HEX);
            case SNAP -> HeroSpeech.ability(player, SinclairAbilities.MISDIRECTION);
            default -> {
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayer player) {
      SinclairState state = SinclairState.ensure(player);
      applyPassives(player);
      if (state.boltCooldown > 0) {
         state.boltCooldown--;
      }

      if (state.assistCooldown > 0 && state.assistant == null) {
         state.assistCooldown--;
      }

      if (state.hexCooldown > 0) {
         state.hexCooldown--;
      }

      if (state.misCooldown > 0) {
         state.misCooldown--;
      }

      if (state.ultCooldown > 0 && state.ult != SinclairState.Ult.WINDOW) {
         state.ultCooldown--;
      }

      if (state.ovationPulse > 0) {
         state.ovationPulse--;
      }

      if (HexMarks.hexed(player)) {
         state.triggerHeld = false;
      }

      Prestige.tick(player, state);
      SinclairMove move = state.move;
      if (move != null) {
         switch (move) {
            case BOLT_CAST -> VexingBolt.tickMove(player, state);
            case SUMMON, SWAP -> Assistant.tickMove(player, state);
            case HEX_CAST -> RabbitHex.tickMove(player, state);
            case SNAP -> Misdirection.tickMove(player, state);
            case CAPTURE, ACT_BEAM, ACT_NOVA, ACT_CUT, ACT_BARRAGE, ACT_AEGIS, ACT_FINALE -> AudienceParticipation.tickMove(player, state);
            default -> {
            }
         }

         if (state.move == move && ++state.moveTick >= move.length()) {
            state.clearMove();
            AudienceParticipation.onMoveEnd(player, state, move);
         }
      }

      Assistant.tick(player, state);
      Misdirection.tick(player, state);
      AudienceParticipation.tick(player, state);
   }

   @Override
   public void enter(ServerPlayer player) {
      SinclairState state = new SinclairState();
      ((HeroPlayer) player).viltrumitecore$setHeroState(state);
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
   public void onDimensionChange(ServerPlayer player) {
      SinclairState state = SinclairState.of(player);
      if (state != null) {
         clearStage(player, state);
      }
   }

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      SinclairState state = SinclairState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.SINCLAIR, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }

      int assistantId = -1;
      int assistLeft = 0;
      if (player instanceof ServerPlayer server) {
         var assistant = Stagecraft.onStage(server, state);
         if (assistant != null) {
            assistantId = assistant.getId();
            assistLeft = Math.max(0, SinclairRules.ASSIST_LIFE - assistant.life());
         }
      }

      return compose(state, assistantId, assistLeft, HexMarks.hexed(player));
   }

   /** Pure wire composer (tested): see {@link SinclairFlags} for the layout. */
   static HeroPublicSnapshot compose(SinclairState state, int assistantId, int assistLeft, boolean hexed) {
      int[] cooldowns = new int[HeroPublicSnapshot.COOLDOWN_COUNT];
      cooldowns[SinclairFlags.COOLDOWN_BOLT] = state.boltCooldown;
      cooldowns[SinclairFlags.COOLDOWN_ASSIST] = state.assistCooldown;
      cooldowns[SinclairFlags.COOLDOWN_HEX] = state.hexCooldown;
      cooldowns[SinclairFlags.COOLDOWN_MIS] = state.misCooldown;
      cooldowns[SinclairFlags.COOLDOWN_ULT] = state.ultCooldown;
      cooldowns[SinclairFlags.COOLDOWN_RELOAD] = state.reload;
      int flags = 0;
      flags = SinclairFlags.with(flags, SinclairFlags.VEIL, state.veil > 0);
      flags = SinclairFlags.with(flags, SinclairFlags.ASSIST_ON_STAGE, assistantId >= 0);
      flags = SinclairFlags.with(flags, SinclairFlags.TRIGGER_HELD, state.triggerHeld);
      flags = SinclairFlags.with(flags, SinclairFlags.CARD_HELD, state.ult == SinclairState.Ult.WINDOW);
      flags = SinclairFlags.with(flags, SinclairFlags.RELOADING, state.reload > 0);
      ActScene scene = state.scene;
      boolean hatOff = RabbitHex.hatOff(state) || scene != null && state.move != null && state.move.isAct() && scene.hatAway(state.moveTick);
      flags = SinclairFlags.with(flags, SinclairFlags.HAT_OFF, hatOff);
      flags = SinclairFlags.with(flags, SinclairFlags.HEXED, hexed);
      flags = SinclairFlags.with(flags, SinclairFlags.FROM_CONTRACT, state.capturedFrom == SinclairRepertoire.Source.CONTRACT && state.captured != null);
      flags = SinclairFlags.with(flags, SinclairFlags.FALLBACK, state.capturedFrom == SinclairRepertoire.Source.FALLBACK && state.captured != null);
      int[] extra = new int[SinclairFlags.EXTRA_COUNT];
      extra[SinclairFlags.EXTRA_SINCE_SHOT] = Math.min(999, state.sinceShot);
      extra[SinclairFlags.EXTRA_WINDOW] = state.window;
      var act = scene != null ? scene.act() : state.captured;
      extra[SinclairFlags.EXTRA_SHAPE] = act == null || act.shape() == null ? 0 : act.shape().ordinal() + 1;
      extra[SinclairFlags.EXTRA_VEIL] = state.veil;
      extra[SinclairFlags.EXTRA_OVATION] = state.ovationPulse;
      extra[SinclairFlags.EXTRA_POWER] = act == null ? 100 : Math.round(SinclairRules.actPower(act.power()) * 100.0F);
      extra[SinclairFlags.EXTRA_COLOR] = act == null ? 0 : act.color() & 0xFFFFFF;
      extra[SinclairFlags.EXTRA_ASSIST_LEFT] = assistLeft;
      extra[SinclairFlags.EXTRA_SWAPS_LEFT] = assistantId >= 0 ? Math.max(0, SinclairRules.SWAP_COUNT - state.swapsUsed) : 0;
      extra[SinclairFlags.EXTRA_SHOTS] = state.shots & 0xFFFF;
      SinclairMove move = state.move;
      Vec3 target = scene != null ? scene.focus() : state.move == SinclairMove.HEX_CAST ? state.hexPoint : null;
      return new HeroPublicSnapshot(HeroId.SINCLAIR, move == null ? -1 : move.ordinal(), move == null ? 0 : state.moveTick, move == null ? 0 : move.length(),
         0, false, 0, 0, false, false, 0, 0, state.volunteerId, cooldowns, move != null, -1L, target, state.magazine, state.reload > 0, flags, extra,
         extra[SinclairFlags.EXTRA_SHAPE], assistantId);
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      SinclairState state = SinclairState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      SinclairState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      SinclairState previous = SinclairState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      SinclairState state = SinclairState.of(player);
      if (state != null) {
         clearStage(player, state);
         if (reason == CleanupReason.ERASED) {
            ((HeroPlayer) player).viltrumitecore$setHeroState(state.respawnCopy());
         }
      } else {
         discardOwned(player);
      }

      if (reason == CleanupReason.HERO_CHANGE) {
         removePassives(player);
         LegacyKit.reset(player);
      }
   }

   /** Everything on stage goes (assistant, decoy, projectiles, hats, rabbits, props, veil, card); cooldowns stay. */
   static void clearStage(ServerPlayer player, SinclairState state) {
      AudienceParticipation.abort(player, state);
      discardOwned(player);
      state.resetTransient();
   }

   /** Removes every Sinclair entity this player owns, in every level. */
   static void discardOwned(ServerPlayer player) {
      if (player.getServer() == null) {
         return;
      }

      UUID owner = player.getUUID();
      for (ServerLevel level : player.getServer().getAllLevels()) {
         List<Entity> doomed = new ArrayList<>();
         for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SinclairOwned.Owned owned && owner.equals(owned.ownerId())) {
               doomed.add(entity);
            }
         }

         for (Entity entity : doomed) {
            entity.discard();
         }
      }
   }

   // --- passives (§4) -------------------------------------------------------------------------
   static void applyPassives(ServerPlayer player) {
      addModifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Sinclair chassis", SinclairRules.BONUS_HEALTH);
      addModifier(player, Attributes.ARMOR, ARMOR_ID, "Sinclair chassis armor", SinclairRules.BONUS_ARMOR);
   }

   static void removePassives(ServerPlayer player) {
      removeModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
      removeModifier(player, Attributes.ARMOR, ARMOR_ID);
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.removeModifier(ActScene.STAGE_WALK_ID);
      }

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

   // --- damage seams --------------------------------------------------------------------------
   private static boolean curtainClosed(ServerPlayer self) {
      SinclairState state = SinclairState.of(self);
      return state != null && state.scene != null && state.move != null && state.move.isAct() && state.scene.curtainClosed(state.moveTick);
   }

   /** AEGIS (§6.1): −75 % damage behind the closed curtain. */
   @Override
   public float clampFinalDamage(ServerPlayer self, DamageSource source, float afterArmor) {
      return curtainClosed(self) ? afterArmor * dev.baranhan.viltrumitecore.hero.sinclair.act.ActScript.AEGIS_DAMAGE_FACTOR : afterArmor;
   }

   /** AEGIS (§6.1): projectiles bounce off the closed curtain. */
   @Override
   public ProjectileResponse projectileResponse(ServerPlayer self, Projectile projectile) {
      return self != null && curtainClosed(self) ? ProjectileResponse.REFLECT : ProjectileResponse.PASS;
   }

   @Override
   public boolean interceptsProjectiles(Player self) {
      return true;
   }

   /** §5.5 "Ta-da!": the first landed hit from the veil deals +3 and dazzles. */
   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      SinclairState state = SinclairState.of(attacker);
      if (state == null || target == null || SinclairOwned.ownedBy(target, attacker.getUUID())) {
         return amount;
      }

      if (state.veil > 0) {
         Misdirection.breakVeil(attacker, state, true);
      }

      if (state.tadaWindow > 0) {
         state.tadaWindow = 0;
         SinclairCombat.daze(target);
         SinclairSounds.play(attacker.level(), target.position().add(0.0, target.getBbHeight(), 0.0), SinclairSounds.TADA, 1.0F, 1.0F);
         HeroSpeech.trigger(attacker, HeroLineTrigger.event(SinclairLines.EVENT_TADA));
         return amount + SinclairRules.VEIL_STRIKE_BONUS;
      }

      return amount;
   }

   /** The veiled magician is hidden from focus senses and scans (§5.5). */
   @Override
   public boolean hiddenFromFocus(Player self) {
      SinclairState state = SinclairState.of(self);
      return state != null && state.veil > 0;
   }

   @Override
   public boolean hiddenFromScan(Player self) {
      return this.hiddenFromFocus(self);
   }
}
