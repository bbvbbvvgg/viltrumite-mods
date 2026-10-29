package dev.baranhan.viltrumitecore.client.thor;

import static dev.baranhan.viltrumitecore.client.anim.pose.PoseKeys.key;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.baranhan.viltrumitecore.client.anim.pose.FirstPersonArm;
import dev.baranhan.viltrumitecore.hero.HeroPublicSnapshot;
import dev.baranhan.viltrumitecore.hero.thor.ThorMove;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorAttacks;
import dev.baranhan.viltrumitecore.hero.thor.combat.ThorStrike;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Session B first-person arm (B-M10, animation-system §5.1 / §6): every
 * attack has its own first-person motion through {@link FirstPersonArm} on
 * the synced action clock, never a second vanilla swing. Strikes and the
 * discharge drive the right arm from the strike pose's hand (a left-hand
 * strike pulls the right arm back to guard); the wave pushes out, the storm
 * holds the arm raised with a tremble, the ultimate charges overhead and
 * drives down at the strike. All keys PLACEHOLDER (storyboards OPEN), tuned
 * like the other heroes' first-person rows; in-game check pending.
 */
public final class ThorFirstPerson {
   /** Right-hand strike: short wind back, thrust, recover (ticks since the strike pose appeared). */
   static final float[][] STRIKE = {key(0, 0, 0, 0, 0, 0, 0), key(2, 18, -8, 0, 0.04F, 0.02F, 0.12F), key(4, -38, 10, 0, -0.06F, 0.08F, -0.26F),
      key(7, -14, 4, 0, -0.02F, 0.03F, -0.08F), key(10, 0, 0, 0, 0, 0, 0)};
   /** A left-hand strike: the right arm draws back to guard. */
   static final float[][] GUARD = {key(0, 0, 0, 0, 0, 0, 0), key(3, 12, 18, 0, -0.06F, 0.05F, 0.10F), key(8, 6, 8, 0, -0.03F, 0.02F, 0.05F),
      key(11, 0, 0, 0, 0, 0, 0)};
   /** The discharge: open palm thrust along the view. */
   static final float[][] DISCHARGE = {key(0, 0, 0, 0, 0, 0, 0), key(2, -30, 6, -12, -0.04F, 0.10F, -0.22F), key(6, -22, 4, -8, -0.03F, 0.07F, -0.14F),
      key(9, 0, 0, 0, 0, 0, 0)};
   /** RMB wave: the arm sweeps out and pushes (action clock). */
   static final float[][] WAVE = {key(0, 0, 0, 0, 0, 0, 0), key(5, 10, 30, 0, 0.06F, 0.02F, 0.10F), key(8, -40, -10, -20, -0.10F, 0.12F, -0.28F),
      key(14, -10, 0, 0, 0, 0.02F, -0.05F), key(18, 0, 0, 0, 0, 0, 0)};
   /** Held storm: raised to the sky. */
   static final float[] STORM = {-105, 8, -10, -0.06F, 0.42F, -0.10F};
   /** Ultimate: charge overhead, then drive down at the strike (action clock). */
   static final float[][] WRATH = {key(0, 0, 0, 0, 0, 0, 0), key(10, -120, 6, -8, -0.05F, 0.46F, -0.06F), key(30, -128, 6, -8, -0.05F, 0.50F, -0.08F),
      key(34, -20, 2, 0, -0.03F, 0.05F, -0.30F), key(44, -8, 0, 0, 0, 0, -0.06F), key(52, 0, 0, 0, 0, 0, 0)};

   private static final FirstPersonArm ARM = new FirstPersonArm();
   private static int strikeKey;
   private static int strikeStart = Integer.MIN_VALUE;
   @Nullable
   private static float[][] strikeRows;

   private ThorFirstPerson() {
   }

   /** Client tick (local player): a new strike pose starts its first-person row (replaces the old vanilla swing). */
   public static void onStrike(int key, int tick, ThorMove move, int resource) {
      if (key == 0 || key == strikeKey) {
         strikeKey = key;
         return;
      }

      strikeKey = key;
      strikeStart = tick;
      strikeRows = rows(move, resource);
   }

   /** The ultimate's row on the server clock (tests). */
   static float[][] wrathRows() {
      return WRATH_ON_CONTACT;
   }

   /** Key index of the contact (the thrust) in {@link #STRIKE} and {@link #DISCHARGE}. */
   static final int STRIKE_CONTACT = 2;
   static final int DISCHARGE_CONTACT = 1;
   private static final Map<ThorAttacks.Attack, float[][]> STRIKES = new HashMap<>();
   /** WRATH_KEY drives down on the server's strike tick (the end of the long charge). */
   static final int WRATH_CONTACT = 3;
   private static final float[][] WRATH_ON_CONTACT = align(WRATH, WRATH_CONTACT, ThorAttacks.envelope(ThorMove.WRATH).phases().contactTick());

   /**
    * Pure: the strike row of a strike pose (tests). The contact key lands on
    * the move's server damage tick (the end of its intro, B-m1), so the
    * first-person thrust and the third-person clip hit together with the damage.
    */
   static float[][] rows(ThorMove move, int resource) {
      if (move == ThorMove.ELECTRIC_COMBO) {
         return resource == ThorCombatClips.LEFT_HAND ? GUARD : contactAligned(ThorAttacks.of(move, resource), DISCHARGE, DISCHARGE_CONTACT);
      }

      ThorStrike pose = ThorStrike.byPose(resource);
      return pose != null && pose.hand() == ThorStrike.Hand.LEFT ? GUARD : contactAligned(ThorAttacks.of(move, resource), STRIKE, STRIKE_CONTACT);
   }

   /** The row's own damage tick (each strike has its own windup, ThorAttacks). */
   private static float[][] contactAligned(@Nullable ThorAttacks.Attack attack, float[][] base, int contactKey) {
      return attack == null ? base : STRIKES.computeIfAbsent(attack, a -> align(base, contactKey, a.phases().contactTick()));
   }

   /** Retime {@code rows} so key {@code contactKey} sits at {@code contactTick}: keys before it scale, keys after it shift. */
   static float[][] align(float[][] rows, int contactKey, float contactTick) {
      float at = rows[contactKey][0];
      if (contactTick <= 0.0F || at <= 0.0F) {
         return rows;
      }

      float[][] out = new float[rows.length][];
      for (int i = 0; i < rows.length; i++) {
         out[i] = rows[i].clone();
         out[i][0] = i <= contactKey ? rows[i][0] * contactTick / at : rows[i][0] - at + contactTick;
      }

      return out;
   }

   /** FirstPersonThorMixin, right arm, after its pushPose. */
   public static void apply(AbstractClientPlayer player, float partialTick, PoseStack stack) {
      HeroPublicSnapshot snapshot = ThorView.of(player);
      ARM.reset();
      if (snapshot == null) {
         return;
      }

      ThorMove move = ThorView.move(snapshot);
      float elapsed = snapshot.actionElapsed() + partialTick;
      if (move == ThorMove.ELECTRIC_WAVE) {
         ARM.blend(WAVE, elapsed, 1.0F);
      } else if (move == ThorMove.ELECTRIC_STORM) {
         float in = Math.min(1.0F, elapsed / 6.0F);
         ARM.blend(STORM, in);
         float t = player.tickCount + partialTick;
         ARM.add((float)Math.sin(t * 1.9F) * 1.6F, 0.0F, (float)Math.cos(t * 2.3F) * 1.4F, 0.0F, 0.0F, 0.0F);
      } else if (move == ThorMove.WRATH) {
         ARM.blend(WRATH_ON_CONTACT, elapsed, 1.0F);
      } else if (strikeRows != null) {
         float since = player.tickCount - strikeStart + partialTick;
         if (since >= 0.0F && since <= strikeRows[strikeRows.length - 1][0]) {
            ARM.blend(strikeRows, since, 1.0F);
         }
      }

      ARM.apply(stack, 1.0F);
   }

   public static void reset() {
      strikeKey = 0;
      strikeRows = null;
      strikeStart = Integer.MIN_VALUE;
   }
}
