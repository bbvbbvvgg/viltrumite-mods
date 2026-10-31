package dev.baranhan.viltrumitecore.hero.ironman.mark;

import dev.baranhan.viltrumitecore.entity.EmptySuitEntity;
import dev.baranhan.viltrumitecore.entity.SuitDebrisEntity;
import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.DivineDamage;
import dev.baranhan.viltrumitecore.hero.HeldInputs;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.ironman.IronManMarkSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManSounds;
import dev.baranhan.viltrumitecore.hero.ironman.IronManState;
import dev.baranhan.viltrumitecore.hero.ironman.Suit;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Server driver of the Veronica marks (spec §4.3, §4.5, §12.4–§12.7, §16):
 * delivery by parts, exit and the one empty suit per owner, entering it,
 * damage on durability, breaking into debris with an instant nano, Mark 42
 * part loss and the signature tick. Roster moves are guarded (MarkRoster.move),
 * so a mark exists exactly once: stored, worn, empty or in delivery.
 */
public final class IronManMarks {
   /** Plates moving (equip, exit): Tony is slow (spec §12.4 step 4). */
   private static final UUID SLOW_ID = UUID.fromString("e21f5ab9-9684-44ca-a84d-7cacb06a09ce");
   public static final double EQUIP_SLOW = -0.6;

   public enum Delivery {
      STARTED,
      BUSY,
      UNAVAILABLE
   }

   private IronManMarks() {
   }

   /** Control, death or spectator: nothing starts (spec §16). */
   public static boolean controlled(ServerPlayer player) {
      return HeroDamage.isAnchored(player) || !player.isAlive() || player.isSpectator();
   }

   // ---- delivery (Veronica choice) ----

   /**
    * Start delivering a mark from Veronica (the caller checked the pod and the
    * range). The current suit leaves first: a worn mark becomes the empty suit
    * right here, a partial one sends its parts back, the nano retracts at once.
    * Re-choosing the partial mark sends only its missing parts.
    */
   public static Delivery deliver(ServerPlayer player, IronManState state, MarkId id, Vec3 podTop) {
      Suit suit = state.suit;
      if (suit.busy()) {
         return Delivery.BUSY;
      }

      boolean partialSame = suit.partial() && suit.mark() == id;
      if (!partialSame && !state.roster.choosable(id)) {
         return Delivery.UNAVAILABLE;
      }

      Vec3 source = podTop;
      int present = 0;
      stopSignature(player, state);
      state.stopCombat();
      HeldInputs.releaseAll(player);
      if (partialSame) {
         state.roster.move(id, MarkLocation.WORN, MarkLocation.IN_DELIVERY);
         present = suit.parts();
      } else {
         if (state.roster.location(id) == MarkLocation.EMPTY) {
            // The standing empty suit flies off its spot and arrives in parts (durability kept).
            EmptySuitEntity standing = emptySuit(player, state);
            if (standing != null && standing.mark() == id) {
               source = standing.position().add(0.0, 1.0, 0.0);
               standing.discard();
            }

            state.emptySuitId = -1;
            state.roster.move(id, MarkLocation.EMPTY, MarkLocation.IN_DELIVERY);
         } else {
            state.roster.move(id, MarkLocation.STORED, MarkLocation.IN_DELIVERY);
         }

         leaveCurrentSuit(player, state);
      }

      suit.startEquip(id, true, present);
      state.equipSource = source;
      if (!player.onGround()) {
         slowFall(player, EquipTimeline.DELIVERY + 20);
      }

      player.level().playSound(null, source.x, source.y, source.z, IronManMarkSounds.PART_FLY.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.0F);
      return Delivery.STARTED;
   }

   private static void leaveCurrentSuit(ServerPlayer player, IronManState state) {
      Suit suit = state.suit;
      if (suit.markWorn()) {
         MarkId old = suit.mark();
         float yaw = player.getYRot();
         // Swap: the old suit stands a step ahead, Tony stays where the new parts arrive (no launch).
         Vec3 at = player.position().add(Vec3.directionFromRotation(0.0F, yaw).scale(0.9));
         suit.dropMark();
         placeEmptySuit(player, state, old, at, yaw);
      } else if (suit.partial()) {
         MarkId old = suit.mark();
         suit.dropMark();
         state.roster.move(old, MarkLocation.WORN, MarkLocation.STORED);
      } else if (suit.state() != dev.baranhan.viltrumitecore.hero.ironman.SuitState.NONE) {
         suit.retractNanoInstantly();
         IronManSounds.play(player, dev.baranhan.viltrumitecore.ViltrumiteCore.IRONMAN_NANO_RETRACT.get(), 1.0F, 1.2F);
      }
   }

   // ---- "Костюм" key, exit, empty suit ----

   /** Suit key in the mark states (spec §4.5). True when handled here (the nano path is the caller's). */
   public static boolean suitKey(ServerPlayer player, IronManState state) {
      Suit suit = state.suit;
      if (suit.markWorn()) {
         // Exit only standing on something: Tony out of the suit mid-air would just fall.
         if (!player.onGround()) {
            play(player, dev.baranhan.viltrumitecore.hero.ironman.IronManCombatSounds.REPULSOR_FIZZLE.get(), 0.8F);
            return true;
         }

         MarkId mark = suit.mark();
         stopSignature(player, state);
         state.stopCombat();
         HeldInputs.releaseAll(player);
         Vec3 at = player.position();
         // The suit stays exactly as Tony stood in it: body facing, not the camera.
         float yaw = player.yBodyRot;
         if (suit.startExit(Suit.EXIT_TICKS)) {
            placeEmptySuit(player, state, mark, at, yaw);
            state.exitDir = Vec3.directionFromRotation(0.0F, yaw);
         }

         return true;
      }

      if (suit.partial()) {
         // Locked parts drop off and fly back to Veronica (plan stage 4 Task 7).
         MarkId mark = suit.mark();
         suit.dropMark();
         state.roster.move(mark, MarkLocation.WORN, MarkLocation.STORED);
         play(player, IronManMarkSounds.PART_FLY.get(), 0.8F);
         return true;
      }

      return suit.equipping() || suit.exiting();
   }

   /** Exit walk window (suit ticks): the plates are open, Tony walks out of the front (no launch). */
   public static final int WALK_OUT_FROM = 8;
   public static final int WALK_OUT_TO = 20;
   public static final double WALK_OUT_SPEED = 0.11;

   /** Tony steps out of the open suit at a walking pace along the suit's facing. */
   private static void walkOut(ServerPlayer player, IronManState state) {
      int t = state.suit.ticks();
      Vec3 dir = state.exitDir;
      if (dir == null || t < WALK_OUT_FROM || t > WALK_OUT_TO || !player.onGround()) {
         return;
      }

      Vec3 motion = player.getDeltaMovement();
      player.setDeltaMovement(dir.x * WALK_OUT_SPEED, motion.y, dir.z * WALK_OUT_SPEED);
      player.hurtMarked = true;
   }

   /** The mark Tony left stands here; the previous empty suit flies away (one per owner, spec §12.6). */
   static void placeEmptySuit(ServerPlayer player, IronManState state, MarkId mark, Vec3 at, float yaw) {
      EmptySuitEntity old = emptySuit(player, state);
      state.emptySuitId = -1;
      if (old != null) {
         old.leave();
      }

      state.roster.move(mark, MarkLocation.WORN, MarkLocation.EMPTY);
      state.emptySuitId = EmptySuitEntity.place(player, mark, at, yaw).getId();
   }

   @Nullable
   static EmptySuitEntity emptySuit(ServerPlayer player, IronManState state) {
      if (state.emptySuitId < 0) {
         return null;
      }

      Entity entity = player.level().getEntity(state.emptySuitId);
      return entity instanceof EmptySuitEntity suit && suit.ownerId().map(player.getUUID()::equals).orElse(false) ? suit : null;
   }

   public static void onEmptySuitLeft(ServerPlayer owner, EmptySuitEntity suit) {
      IronManState state = IronManState.of(owner);
      if (state == null) {
         return;
      }

      MarkId mark = suit.mark();
      if (mark != null) {
         state.roster.move(mark, MarkLocation.EMPTY, MarkLocation.STORED);
      }

      if (state.emptySuitId == suit.getId()) {
         state.emptySuitId = -1;
      }
   }

   /** Client-readable gate of entering an empty suit: Iron Man without a mark and no plates moving (spec §12.6). */
   public static boolean mayEnter(net.minecraft.world.entity.player.Player who) {
      if (!(who instanceof dev.baranhan.viltrumitecore.hero.HeroPlayer heroPlayer)) {
         return false;
      }

      dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot snapshot = heroPlayer.getHeroSnapshot();
      return snapshot.heroId() == dev.baranhan.viltrumitecore.hero.HeroId.IRON_MAN
         && dev.baranhan.viltrumitecore.hero.ironman.IronManVariant.mark(snapshot.variant()) == null
         && dev.baranhan.viltrumitecore.hero.ironman.IronManFlags.get(snapshot.heroFlags(), dev.baranhan.viltrumitecore.hero.ironman.IronManFlags.Field.EQUIP_PHASE)
            == dev.baranhan.viltrumitecore.hero.ironman.IronManFlags.EQUIP_NONE;
   }

   /** RMB on the own empty suit (spec §12.6): from no armor or nano; the durability is kept. */
   public static boolean enterEmptySuit(ServerPlayer who, EmptySuitEntity suit) {
      IronManState state = IronManState.of(who);
      MarkId mark = suit.mark();
      if (state == null || mark == null || controlled(who) || state.suit.busy() || state.roster.location(mark) != MarkLocation.EMPTY) {
         return false;
      }

      if (state.suit.markOn()) {
         who.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.enter_refused"), true);
         return false;
      }

      if (!suit.inFront(who.position())) {
         // Only from the front (spec §12.6): the plates open towards the face.
         who.displayClientMessage(Component.translatable("hud.viltrumitecore.ironman.enter_front"), true);
         return false;
      }

      if (state.suit.state() != dev.baranhan.viltrumitecore.hero.ironman.SuitState.NONE) {
         state.suit.retractNanoInstantly();
         IronManSounds.play(who, dev.baranhan.viltrumitecore.ViltrumiteCore.IRONMAN_NANO_RETRACT.get(), 1.0F, 1.2F);
      }

      stopSignature(who, state);
      state.stopCombat();
      HeldInputs.releaseAll(who);
      state.roster.move(mark, MarkLocation.EMPTY, MarkLocation.IN_DELIVERY);
      state.emptySuitId = -1;
      state.enteringSuitId = suit.getId();
      state.equipSource = null;
      // No teleport and no flying parts: the client walks Tony in (SuitEntryDriver), the suit stays where it stood.
      suit.startEntering();
      return true;
   }

   /** The plates closed around Tony: the mark is on (spec §12.6). */
   public static void finishEntering(ServerPlayer who, IronManState state, EmptySuitEntity suit) {
      MarkId mark = suit.mark();
      state.enteringSuitId = -1;
      if (mark == null || state.roster.location(mark) != MarkLocation.IN_DELIVERY || !state.suit.equipNow(mark)) {
         if (mark != null && state.roster.location(mark) == MarkLocation.IN_DELIVERY) {
            state.roster.move(mark, MarkLocation.IN_DELIVERY, MarkLocation.STORED);
         }

         return;
      }

      onSuitEvent(who, state, Suit.Event.MARK_ON);
   }

   /** Entering interrupted (control, death...): the suit stays where it stands, empty. */
   public static void abortEntering(ServerPlayer who, IronManState state, EmptySuitEntity suit) {
      MarkId mark = suit.mark();
      if (state.enteringSuitId == suit.getId()) {
         state.enteringSuitId = -1;
      }

      if (mark != null && state.roster.location(mark) == MarkLocation.IN_DELIVERY) {
         state.roster.move(mark, MarkLocation.IN_DELIVERY, MarkLocation.EMPTY);
         state.emptySuitId = suit.getId();
      }
   }

   // ---- per tick ----

   /** Before Suit.tick: control interrupts the equip; locked parts stay (spec §16, plan Task 7). */
   public static void onControl(ServerPlayer player, IronManState state) {
      if (state.enteringSuitId >= 0) {
         // Control breaks the walk-in; the suit entity sees it and stays standing.
         state.enteringSuitId = -1;
      }

      boolean equipping = state.suit.equipping();
      MarkId mark = state.suit.mark();
      MarkId gone = state.suit.interrupt();
      if (!equipping) {
         return;
      }

      state.equipSource = null;
      if (gone != null) {
         state.roster.move(gone, MarkLocation.IN_DELIVERY, MarkLocation.STORED);
         if (!player.onGround()) {
            slowFall(player, 200);
         }
      } else if (state.suit.partial() && mark != null) {
         state.roster.move(mark, MarkLocation.IN_DELIVERY, MarkLocation.WORN);
      }
   }

   /** After Suit.tick. */
   public static void onSuitEvent(ServerPlayer player, IronManState state, Suit.Event event) {
      if (event == Suit.Event.MARK_ON) {
         MarkId mark = state.suit.mark();
         state.roster.move(mark, MarkLocation.IN_DELIVERY, MarkLocation.WORN);
         state.equipSource = null;
         // The helmet closes last with a click and an eye flash (spec §12.4 step 3).
         state.helmet.reset();
         state.signature.clear();
         dev.baranhan.viltrumitecore.hero.ironman.IronManLines.say(player, dev.baranhan.viltrumitecore.hero.ironman.IronManLines.EVENT_SUIT_UP);
      }
   }

   public static void tick(ServerPlayer player, IronManState state, boolean controlled) {
      state.roster.tick();
      state.signature.tickCooldown();
      Suit suit = state.suit;
      // A world instance that vanished (unloaded, removed) gives its mark back.
      if (state.emptySuitId >= 0 && emptySuit(player, state) == null) {
         state.emptySuitId = -1;
      }

      if (state.emptySuitId < 0) {
         MarkId standing = state.roster.at(MarkLocation.EMPTY);
         if (standing != null) {
            state.roster.move(standing, MarkLocation.EMPTY, MarkLocation.STORED);
         }
      }

      if (suit.markOn()) {
         MarkId mark = suit.mark();
         if (state.roster.durability(mark) <= 0.0F) {
            breakMark(player, state);
         } else if (mark == MarkId.MARK_42 && suit.markWorn()) {
            mark42Parts(player, state);
         }
      }

      if (suit.markWorn()) {
         MarkSignature signature = MarkSignatures.of(suit.mark());
         if (controlled) {
            signature.stop(player, state);
         } else {
            signature.tick(player, state);
         }

         if (state.spec().helmetForcedOpen() && state.helmet.closed()) {
            state.helmet.toggle();
         }
      }

      if (suit.exiting()) {
         walkOut(player, state);
      } else {
         state.exitDir = null;
      }

      slow(player, suit.equipping() || suit.exiting());
   }

   private static void mark42Parts(ServerPlayer player, IronManState state) {
      MarkId mark = MarkId.MARK_42;
      int lost = Mark42Parts.lostMask(state.roster.durability(mark), state.roster.maxDurability(mark));
      int full = SuitPart.fullMask(mark);
      int fresh = lost & state.suit.parts();
      if (fresh == 0) {
         return;
      }

      state.suit.removeParts(lost);
      debris(player, mark, fresh, 14);
      play(player, IronManMarkSounds.MARK_BREAK.get(), 1.6F);
      if ((state.suit.parts() & full) == 0) {
         breakMark(player, state);
      }
   }

   /** Durability 0 (spec §4.3): sparks, plates fall off as debris, the nano is on at once, ~5 min cooldown. */
   public static void breakMark(ServerPlayer player, IronManState state) {
      MarkId mark = state.suit.mark();
      if (mark == null) {
         return;
      }

      int parts = state.suit.parts();
      stopSignature(player, state);
      state.stopCombat();
      HeldInputs.releaseAll(player);
      state.roster.breakMark(mark);
      debris(player, mark, parts, 9);
      Vec3 chest = player.position().add(0.0, 1.1, 0.0);
      HeroFx.flash(player, chest);
      HeroFx.shockwave(player, player.position(), 0.6F, null, 2.5F);
      play(player, IronManMarkSounds.MARK_BREAK.get(), 1.0F);
      state.suit.autoNano();
   }

   /** Debris chunks for the parts in {@code mask} (at most {@code max}), placed on their bones. */
   public static void debris(net.minecraft.world.entity.LivingEntity player, MarkId mark, int mask, int max) {
      List<SuitPart> parts = SuitPart.of(mark);
      float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
      Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
      int spawned = 0;
      for (int i = 0; i < parts.size() && spawned < max; i++) {
         if ((mask & 1 << i) == 0) {
            continue;
         }

         SuitPart part = parts.get(i);
         Vec3 offset = switch (part.bone()) {
            case HEAD -> new Vec3(0.0, 1.65, 0.0);
            case BODY -> new Vec3(0.0, 1.1, 0.0);
            case RIGHT_ARM -> right.scale(0.38).add(0.0, 1.15, 0.0);
            case LEFT_ARM -> right.scale(-0.38).add(0.0, 1.15, 0.0);
            case RIGHT_LEG -> right.scale(0.12).add(0.0, 0.45, 0.0);
            case LEFT_LEG -> right.scale(-0.12).add(0.0, 0.45, 0.0);
         };
         Vec3 out = new Vec3(offset.x, 0.0, offset.z);
         out = out.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getRandom().nextFloat() * 360.0F) : out.normalize();
         Vec3 velocity = out.scale(0.15 + player.getRandom().nextDouble() * 0.2).add(0.0, 0.25 + player.getRandom().nextDouble() * 0.25, 0.0);
         SuitDebrisEntity.spawn(player.level(), player.position().add(offset), mark, i, velocity);
         spawned++;
      }
   }

   /** Stop the worn mark's signature before the mark changes. */
   public static void stopSignature(ServerPlayer player, IronManState state) {
      MarkId mark = state.suit.mark();
      if (mark != null && state.suit.markWorn()) {
         MarkSignatures.of(mark).stop(player, state);
      }

      state.signature.clear();
   }

   /**
    * Damage layer after the shield (spec §4.3): the mark takes the whole hit while durability lasts.
    * Like vanilla armor it lets divine and invulnerability-bypassing damage through ({@link DivineDamage#piercesHeroArmor}).
    */
   public static DamageAbsorb absorb(IronManState state, DamageSource source, float amount) {
      if (!state.suit.markOn() || DivineDamage.piercesHeroArmor(source)) {
         return DamageAbsorb.PASS;
      }

      MarkId mark = state.suit.mark();
      MarkDamage.Result result = MarkDamage.apply(state.roster.durability(mark), amount, MarkSpec.of(mark).plating());
      if (!result.absorbed()) {
         return DamageAbsorb.PASS;
      }

      state.roster.setDurability(mark, result.durability());
      return DamageAbsorb.ABSORBED;
   }

   /** Dimension change (spec §16): the suit on Tony stays, a delivery and the empty suit go back; Veronica stays where it landed. */
   public static void onDimensionChange(ServerPlayer player, IronManState state) {
      if (state.suit.equipping()) {
         MarkId mark = state.suit.mark();
         state.suit.dropMark();
         state.roster.move(mark, MarkLocation.IN_DELIVERY, MarkLocation.STORED);
      }

      state.roster.recallWorld();
      state.emptySuitId = -1;
      state.enteringSuitId = -1;
      state.equipSource = null;
   }

   private static void slow(ServerPlayer player, boolean on) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }

      boolean has = speed.getModifier(SLOW_ID) != null;
      if (on && !has) {
         speed.addTransientModifier(new AttributeModifier(SLOW_ID, "Iron Man suit plates moving", EQUIP_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
      } else if (!on && has) {
         speed.removeModifier(SLOW_ID);
      }
   }

   private static void slowFall(ServerPlayer player, int ticks) {
      player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, false, false));
   }

   private static void play(ServerPlayer player, SoundEvent sound, float pitch) {
      IronManSounds.play(player, sound, 1.0F, pitch);
   }
}
