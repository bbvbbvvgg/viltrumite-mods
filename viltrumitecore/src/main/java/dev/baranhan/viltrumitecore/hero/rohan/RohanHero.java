package dev.baranhan.viltrumitecore.hero.rohan;

import dev.baranhan.viltrumitecore.balance.BalanceTable;
import dev.baranhan.viltrumitecore.balance.HeroBalanceProfile;
import dev.baranhan.viltrumitecore.hero.CleanupReason;
import dev.baranhan.viltrumitecore.hero.HeroAction;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.HeroDefinition;
import dev.baranhan.viltrumitecore.hero.HeroId;
import dev.baranhan.viltrumitecore.hero.HeroPlayer;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.ScanInfo;
import dev.baranhan.viltrumitecore.hero.ScanLine;
import dev.baranhan.viltrumitecore.hero.book.BookPageSink;
import dev.baranhan.viltrumitecore.hero.book.BookProfile;
import dev.baranhan.viltrumitecore.util.ViltrumiteAbilityUser;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Rohan Kishibe (JoJo's Bizarre Adventure Part 4) and his stand Heaven's Door (spec docs/rohan/heavens-door-design.md):
 * a manga artist with an ordinary body, whose power is control: he opens bodies as books, reads the truth and writes
 * instructions they obey. Tier B, no flight, no Viltrumite kit; vanilla melee and mining stay his.
 */
public final class RohanHero implements HeroDefinition {
   static final UUID HEALTH_ID = UUID.fromString("3c9a7f2e-5b1d-4e8a-a6c4-2f0e9d7b1a01");
   static final UUID ARMOR_ID = UUID.fromString("3c9a7f2e-5b1d-4e8a-a6c4-2f0e9d7b1a02");
   /** Rooted while drawing or presenting the manuscript. */
   static final UUID SLOW_ID = UUID.fromString("3c9a7f2e-5b1d-4e8a-a6c4-2f0e9d7b1a03");
   public static final RohanHero INSTANCE = new RohanHero();

   private RohanHero() {
   }

   @Override
   public HeroId id() {
      return HeroId.ROHAN;
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
      return BalanceTable.MANGAKA;
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
      return RohanAbilities.owns(abilityId);
   }

   @Override
   public String[] defaultLoadout() {
      return RohanAbilities.defaultLoadout();
   }

   @Override
   public String[] heroInputSlots() {
      return RohanAbilities.inputSlots();
   }

   @Override
   public HeroAction heroActionFor(String abilityId) {
      return RohanAbilities.actionFor(abilityId);
   }

   @Override
   public List<dev.baranhan.viltrumitecore.ability.ViltrumiteAbility> panelAbilities() {
      return RohanAbilities.panelAbilities();
   }

   @Override
   public ResourceLocation abilityIcon(String abilityId) {
      return RohanAbilities.icon(abilityId);
   }

   @Override
   public dev.baranhan.viltrumitecore.hero.speech.HeroLines lines() {
      return RohanLines.LINES;
   }

   /** An ordinary body: grabs, freezes and pushes all work on him. */
   @Override
   public boolean allowsExternalControl(LivingEntity target, dev.baranhan.viltrumitecore.hero.control.ControlKind kind) {
      return true;
   }

   @Override
   public boolean usesLegacyRegen(Player player) {
      return false;
   }

   // --- mouse: an ordinary man's hands ---

   @Override
   public boolean claimsBlockAttack(Player player) {
      return false;
   }

   @Override
   public boolean claimsBlockUse(Player player) {
      return false;
   }

   @Override
   public boolean yieldsToVanillaUse(Player player) {
      return true;
   }

   @Override
   public boolean allowsVanillaAttack(Player player) {
      return true;
   }

   @Override
   public HeroAction mouseAction(dev.baranhan.viltrumitecore.hero.MouseButton button, Player player) {
      return null;
   }

   // --- his own book: the author knows his pages; writing on himself is Self-Edit ---

   @Override
   public BookProfile bookProfile(Player self) {
      return BookProfile.PLAYER.withNotes("rohan_self");
   }

   @Override
   public void bookPages(Player self, BookPageSink sink) {
      RohanState state = self.level().isClientSide ? null : RohanState.of(self);
      if (state != null) {
         sink.line("book.viltrumitecore.line.rohan_ink", Integer.toString((int)state.ink), Integer.toString(state.read.size()));
      }
   }

   @Override
   public ScanInfo scanInfo(Player self) {
      return new ScanInfo(List.of(ScanLine.of("scan.viltrumitecore.rohan.stand")), List.of(ScanLine.of("scan.viltrumitecore.rohan.body")),
         List.of(ScanLine.of("scan.viltrumitecore.rohan.sight")));
   }

   // --- input ---

   @Override
   public boolean canAct(ServerPlayer player, HeroAction action) {
      if (!player.isAlive() || HeroDamage.isAnchored(player)) {
         return false;
      }
      RohanAbility ability = RohanAbility.byAction(action);
      return ability != null || action == HeroAction.JUMP || action == HeroAction.AIR_STEP;
   }

   @Override
   public void onInputRefused(ServerPlayer player, HeroAction action) {
      RohanSkills.refuse(player);
   }

   @Override
   public void handleInput(ServerPlayer player, HeroAction action, boolean pressed) {
      if (!pressed) {
         return;
      }
      RohanAbility ability = RohanAbility.byAction(action);
      if (ability != null) {
         RohanSkills.input(player, RohanState.ensure(player), ability);
      }
   }

   @Override
   public void handleForcedRelease(ServerPlayer player, HeroAction action) {
      // every input is a press
   }

   // --- lifecycle ---

   @Override
   public void tick(ServerPlayer player) {
      RohanState state = RohanState.ensure(player);
      state.tickCount++;
      state.tickTimers();
      state.regen();
      if (state.tickCount % 20 == 0) {
         applyChassis(player);
      }
      RohanSkills.tick(player, state);
      RohanMove m = state.move;
      root(player, m != null && m.roots());
   }

   @Override
   public void enter(ServerPlayer player) {
      RohanState.ensure(player);
      if (player instanceof ViltrumiteAbilityUser user) {
         user.setActivePage(0);
      }
      applyChassis(player);
      player.setHealth(player.getMaxHealth());
   }

   @Override
   public void onRespawn(ServerPlayer player) {
      applyChassis(player);
      player.setHealth(player.getMaxHealth());
   }

   static void applyChassis(ServerPlayer player) {
      float fraction = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
      boolean added = modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, "Rohan health", RohanRules.HEALTH_BONUS);
      modifier(player, Attributes.ARMOR, ARMOR_ID, "Rohan armor", RohanRules.ARMOR);
      if (added) {
         player.setHealth(Mth.clamp(fraction * player.getMaxHealth(), 1.0F, player.getMaxHealth()));
      }
   }

   private static boolean modifier(ServerPlayer player, Attribute attribute, UUID id, String name, double amount) {
      AttributeInstance instance = player.getAttribute(attribute);
      if (instance != null && instance.getModifier(id) == null) {
         instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
         return true;
      }
      return false;
   }

   static void removeChassis(ServerPlayer player) {
      float fraction = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
      boolean had = false;
      for (Object[] pair : new Object[][]{{Attributes.MAX_HEALTH, HEALTH_ID}, {Attributes.ARMOR, ARMOR_ID}, {Attributes.MOVEMENT_SPEED, SLOW_ID}}) {
         AttributeInstance instance = player.getAttribute((Attribute)pair[0]);
         if (instance != null && instance.getModifier((UUID)pair[1]) != null) {
            instance.removeModifier((UUID)pair[1]);
            had |= pair[1] == HEALTH_ID;
         }
      }
      if (had && player.isAlive()) {
         player.setHealth(Mth.clamp(fraction * player.getMaxHealth(), 1.0F, player.getMaxHealth()));
      }
   }

   private static void root(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      boolean has = speed.getModifier(SLOW_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(SLOW_ID, "Rohan drawing", -0.75, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(SLOW_ID);
      }
   }

   @Override
   public boolean resetCooldowns(ServerPlayer player) {
      RohanState state = RohanState.of(player);
      if (state == null) {
         return false;
      }
      java.util.Arrays.fill(state.cooldowns, 0);
      state.ink = RohanRules.INK_MAX;
      return true;
   }

   @Override
   public void cleanup(ServerPlayer player, CleanupReason reason) {
      RohanState state = RohanState.of(player);
      if (state != null) {
         RohanSkills.cleanup(player, state);
         if (reason != CleanupReason.DISCONNECT) {
            state.standOut = false;
         }
      }
      RohanBooks.release(player);
      root(player, false);
      if (reason == CleanupReason.HERO_CHANGE) {
         removeChassis(player);
      }
   }

   @Override
   public void onDimensionChange(ServerPlayer player) {
      RohanState state = RohanState.of(player);
      if (state != null) {
         RohanSkills.cleanup(player, state);
      }
      RohanBooks.release(player);
   }

   @Override
   public void saveHeroState(Player player, CompoundTag nbt) {
      RohanState state = RohanState.of(player);
      if (state != null) {
         state.save(nbt);
      }
   }

   @Override
   public void loadHeroState(Player player, CompoundTag nbt) {
      RohanState.ensure(player).load(nbt);
   }

   @Override
   public void cloneHeroState(Player original, Player clone) {
      RohanState previous = RohanState.of(original);
      if (previous != null && clone instanceof HeroPlayer heroPlayer) {
         heroPlayer.viltrumitecore$setHeroState(previous.respawnCopy());
      }
   }

   // --- combat bookkeeping ---

   @Override
   public void onHurt(ServerPlayer player, DamageSource source, float amount) {
      RohanState state = RohanState.of(player);
      if (state != null) {
         state.idle = 0;
      }
      if (amount >= dev.baranhan.viltrumitecore.entity.directive.MobDirectives.HEAVY_HIT) {
         RohanBooks.releaseStrong(player);
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity target, DamageSource source, float amount) {
      RohanState state = RohanState.of(attacker);
      if (state != null) {
         state.idle = 0;
      }
      RohanBooks.onRohanHit(attacker, target);
      return amount;
   }

   // --- snapshot ---

   @Override
   public HeroPublicSnapshot snapshot(Player player) {
      RohanState state = RohanState.of(player);
      if (state == null) {
         return new HeroPublicSnapshot(HeroId.ROHAN, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, new int[HeroPublicSnapshot.COOLDOWN_COUNT], false);
      }
      return compose(state, player);
   }

   /**
    * actionId = move, variant = move serial, controlTargetId = the touched body / the open book's owner, resource = ink,
    * resource2 = inspiration, extra cooldowns = the six ability cooldowns.
    */
   static HeroPublicSnapshot compose(RohanState state, Player player) {
      boolean reading = player instanceof ServerPlayer sp && RohanBooks.reading(sp);
      int flags = 0;
      flags = RohanFlags.set(flags, RohanFlags.STAND_OUT, state.standOut || reading || state.move != null && state.move.stand);
      flags = RohanFlags.set(flags, RohanFlags.SKETCH, state.sketchTicks > 0);
      flags = RohanFlags.set(flags, RohanFlags.MANUSCRIPT, state.manuscriptTicks > 0);
      flags = RohanFlags.set(flags, RohanFlags.READING, reading);
      flags = RohanFlags.set(flags, RohanFlags.COUNTER, state.counterTicks > 0);
      flags = RohanFlags.set(flags, RohanFlags.IN_COMBAT, state.idle < RohanRules.CALM_TICKS);
      flags = RohanFlags.set(flags, RohanFlags.FLYING, state.flyTicks > 0);
      RohanMove move = state.move;
      int target = reading && player instanceof ServerPlayer sp ? RohanBooks.primaryTarget(sp) : state.targetId;
      return new HeroPublicSnapshot(HeroId.ROHAN, move == null ? -1 : move.ordinal(), state.elapsed, move == null ? 0 : move.length,
         0, false, 0, 0, false, false, 0, 0, target, new int[HeroPublicSnapshot.COOLDOWN_COUNT],
         move != null, -1L, state.sketchAt, (int)state.ink, false, flags, state.cooldowns.clone(), state.serial, (int)state.inspiration);
   }
}
