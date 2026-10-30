package dev.baranhan.viltrumitecore.hero.thor.combat;

import dev.baranhan.viltrumitecore.hero.DamageAbsorb;
import dev.baranhan.viltrumitecore.hero.HeroDamage;
import dev.baranhan.viltrumitecore.hero.fx.HeroFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorActions;
import dev.baranhan.viltrumitecore.hero.thor.ThorCue;
import dev.baranhan.viltrumitecore.hero.thor.ThorFx;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.ThorSounds;
import dev.baranhan.viltrumitecore.hero.thor.ThorState;
import dev.baranhan.viltrumitecore.hero.thor.bridge.ThorBridge;
import dev.baranhan.viltrumitecore.hero.thor.scene.ThorScenes;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Audit v3 (item 4): Thor's guard on F (the guard key, vanilla swap-hands). Held, it raises Mjölnir across
 * the body (or a high guard with the fists) as soon as Thor is free: a running strike's recovery is cut for
 * it, anything else is waited out. Hits from the front 150° lose 70 % with Mjölnir in the hand, 40 % bare
 * handed; sources that bypass a shield (fall, fire, magic, ...) are never blocked. The first
 * {@link #PARRY_WINDOW} ticks of a raise parry instead: the hit is absorbed, a melee attacker is thrown
 * back and stunned, and with the hammer an arc of lightning answers it. A raise opens a parry window
 * only every {@link #PARRY_REARM} ticks, so tapping F is not a parry wall. The guard slows Thor down,
 * an attack press lowers it (it comes back up after the move while F is still held), a scene or full
 * flight drops it. Different from Jinwoo's dagger block (no mana, no riposte move, a wider arc, the
 * lightning counter) and from DIO's stand guard (it parries).
 */
public final class ThorGuard {
   public static final double HALF_ARC = 75.0;
   public static final float HAMMER_FACTOR = 0.3F;
   public static final float FIST_FACTOR = 0.6F;
   public static final int PARRY_WINDOW = 5;
   public static final int PARRY_REARM = 15;
   public static final int PARRY_STUN = 25;
   public static final float PARRY_ZAP = 4.0F;
   public static final double PARRY_REACH = 6.0;
   public static final int BLOCK_STOP = 2;
   public static final int PARRY_STOP = 4;
   /** Movement while the guard is up (multiplier of the total speed). */
   public static final double GUARD_SLOW = -0.4;
   static final UUID SLOW_ID = UUID.fromString("7c1f3e2a-6b4d-4f8e-9a31-5d7e00000001");

   private ThorGuard() {
   }

   /** F pressed / released (ThorHero.handleInput). */
   public static void hold(ServerPlayer player, ThorState state, boolean held) {
      ThorCombatState combat = ThorCombatState.of(state);
      combat.guardHeld = held;
      if (!held) {
         lower(player, combat);
      }
   }

   public static boolean guarding(ThorState state) {
      return ThorCombatState.of(state).guardTicks >= 0;
   }

   /** Pure: may the guard be up now. */
   public static boolean allowed(boolean alive, boolean anchored, boolean scene, boolean fullFlight) {
      return alive && !anchored && !scene && !fullFlight;
   }

   /** Server tick (after the move flow). */
   public static void tick(ServerPlayer player, ThorState state) {
      ThorCombatState combat = ThorCombatState.of(state);
      if (combat.parryRearm > 0) {
         combat.parryRearm--;
      }

      boolean allowed = allowed(player.isAlive(), HeroDamage.isAnchored(player), ThorBridge.sceneLocked(state), ThorScenes.fullFlight(player));
      if (!combat.guardHeld || !allowed) {
         lower(player, combat);
         return;
      }

      if (combat.guardTicks >= 0) {
         if (state.timeline.running()) {
            // A move started from the guard (an attack press): the guard waits for it.
            lower(player, combat);
         } else {
            combat.guardTicks++;
         }

         return;
      }

      if (state.timeline.running() && !ThorActions.finishRecovery(player, state)) {
         return;
      }

      raise(player, combat);
   }

   private static void raise(ServerPlayer player, ThorCombatState combat) {
      combat.guardTicks = 0;
      combat.parryOpen = combat.parryRearm == 0;
      if (combat.parryOpen) {
         combat.parryRearm = PARRY_REARM;
      }

      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null && speed.getModifier(SLOW_ID) == null) {
         speed.addTransientModifier(new AttributeModifier(SLOW_ID, "Thor guard", GUARD_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
      }

      ThorSounds.play(player, ThorCue.GUARD_UP);
   }

   /** Lower the guard (idempotent). */
   public static void lower(@Nullable ServerPlayer player, ThorCombatState combat) {
      combat.guardTicks = -1;
      combat.parryOpen = false;
      if (player != null) {
         AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speed != null) {
            speed.removeModifier(SLOW_ID);
         }
      }
   }

   /** Everything off (death, hero change, dimension change, clone). */
   public static void stop(@Nullable ServerPlayer player, ThorState state) {
      ThorCombatState combat = ThorCombatState.of(state);
      combat.guardHeld = false;
      lower(player, combat);
   }

   /** Pure: the share of a blocked hit that still lands. */
   public static float factor(boolean hammerInHand) {
      return hammerInHand ? HAMMER_FACTOR : FIST_FACTOR;
   }

   /** Pure: is a raise this old still parrying. */
   public static boolean parrying(int guardTicks, boolean parryOpen) {
      return parryOpen && guardTicks >= 0 && guardTicks < PARRY_WINDOW;
   }

   /** ThorHero.absorbIncoming: a hit on a guarding Thor. */
   public static DamageAbsorb absorb(ServerPlayer self, ThorState state, DamageSource source, float raw) {
      ThorCombatState combat = ThorCombatState.of(state);
      if (combat.guardTicks < 0 || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
         return DamageAbsorb.PASS;
      }

      Entity attacker = source.getEntity();
      Vec3 from = source.getSourcePosition();
      if (from == null && attacker != null) {
         from = attacker.position();
      }

      Vec3 look = CombatHits.flat(self.getLookAngle());
      if (from == null || look.lengthSqr() < 1.0E-6 || !CombatHits.withinArc(look, from.subtract(self.position()), HALF_ARC)) {
         return DamageAbsorb.PASS;
      }

      boolean hammer = CombatHits.hammerInHand(self);
      Vec3 at = self.getEyePosition().add(self.getLookAngle().scale(1.1)).add(0.0, -0.35, 0.0);
      LivingEntity near = attacker instanceof LivingEntity living && living != self && living.isAlive()
         && living.distanceToSqr(self) <= PARRY_REACH * PARRY_REACH ? living : null;
      if (parrying(combat.guardTicks, combat.parryOpen)) {
         combat.parryOpen = false;
         int[] ids = near == null ? new int[]{self.getId()} : new int[]{self.getId(), near.getId()};
         if (near != null) {
            near.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PARRY_STUN, 3, false, false, true));
            near.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PARRY_STUN, 1, false, false, true));
            CombatHits.push(near, self.position(), hammer ? 1.2 : 0.8, 0.25);
            if (hammer) {
               CombatHits.hitArea(self, near, PARRY_ZAP, ThorMove.ELECTRIC_COMBO, combat.nextAttackId());
               HeroFx.preset(self, ThorFx.ARC, at, near.getBoundingBox().getCenter(), 1.0F, null, 0);
            }
         }

         HeroFx.preset(self, hammer ? ThorFx.IMPACT_ELECTRIC : ThorFx.IMPACT_FIST, at, from, hammer ? 1.2F : 0.9F, null, PARRY_STOP, ids);
         ThorSounds.playAt(self.serverLevel(), at, hammer ? ThorCue.PARRY : ThorCue.PARRY_FIST);
         return DamageAbsorb.ABSORBED;
      }

      int[] ids = near == null ? new int[]{self.getId()} : new int[]{self.getId(), near.getId()};
      HeroFx.preset(self, hammer ? ThorFx.IMPACT_HAMMER : ThorFx.IMPACT_FIST, at, from, 0.6F, null, BLOCK_STOP, ids);
      ThorSounds.playAt(self.serverLevel(), at, hammer ? ThorCue.GUARD_BLOCK : ThorCue.GUARD_BLOCK_FIST);
      return DamageAbsorb.reduceTo(raw * factor(hammer));
   }
}
