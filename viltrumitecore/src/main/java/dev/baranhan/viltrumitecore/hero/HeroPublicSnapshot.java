package dev.baranhan.viltrumitecore.hero;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable server-produced public hero snapshot. Serialized into one packed
 * synced string so every tracker (and late trackers) sees the same atomic view.
 * Owner-private data (heart carrier ids) travels separately.
 */
public record HeroPublicSnapshot(
   HeroId heroId,
   int actionId,
   int actionElapsed,
   int actionLength,
   int hearts,
   boolean lionActive,
   int lionWindowLeft,
   int lionWindowMax,
   boolean lionOverheat,
   boolean madness,
   int madnessTicksLeft,
   int ritualTicks,
   int controlTargetId,
   int[] cooldowns,
   boolean actionBusy,
   long availableActions,
   @Nullable Vec3 actionTarget,
   /** Generic hero resource (Homelander: eye heat x10, 0-1000). */
   int resource,
   /** Resource-driven lock (Homelander: overheat). */
   boolean resourceLocked,
   /** Generic hero state bits; meaning owned by the hero (see heroFlag). */
   int heroFlags,
   /**
    * Generic extra cooldowns (page 2 / hero systems), ticks left, at most
    * {@link #EXTRA_COOLDOWN_MAX}. Index meaning is owned by the hero. Encoded
    * only when non-empty: older encodings decode as empty.
    */
   int[] extraCooldowns,
   /**
    * Generic hero-owned packed value for every client's renderer (Iron Man:
    * suit variant + body parts present). Older encodings decode as 0.
    */
   int variant,
   /** Second generic resource (Iron Man: worn mark durability x10). Older encodings decode as 0. */
   int resource2
) {
   public static final int COOLDOWN_COUNT = 6;
   // Bounded wire capacity, including hero-reserved slots (Reinhard mode/Phoenix).
   public static final int EXTRA_COOLDOWN_MAX = 32;
   private static final int[] NO_EXTRA = new int[0];
   public static final HeroPublicSnapshot EMPTY = new HeroPublicSnapshot(
      HeroId.HUMAN, -1, 0, 0, 0, false, 0, 0, false, false, 0, 0, -1, new int[COOLDOWN_COUNT], false
   );

   public HeroPublicSnapshot(HeroId heroId, int actionId, int actionElapsed, int actionLength, int hearts,
      boolean lionActive, int lionWindowLeft, int lionWindowMax, boolean lionOverheat, boolean madness,
      int madnessTicksLeft, int ritualTicks, int controlTargetId, int[] cooldowns, boolean actionBusy) {
      this(heroId, actionId, actionElapsed, actionLength, hearts, lionActive, lionWindowLeft, lionWindowMax,
         lionOverheat, madness, madnessTicksLeft, ritualTicks, controlTargetId, cooldowns, actionBusy, -1, null);
   }

   public HeroPublicSnapshot(HeroId heroId, int actionId, int actionElapsed, int actionLength, int hearts,
      boolean lionActive, int lionWindowLeft, int lionWindowMax, boolean lionOverheat, boolean madness,
      int madnessTicksLeft, int ritualTicks, int controlTargetId, int[] cooldowns, boolean actionBusy,
      long availableActions, @Nullable Vec3 actionTarget) {
      this(heroId, actionId, actionElapsed, actionLength, hearts, lionActive, lionWindowLeft, lionWindowMax,
         lionOverheat, madness, madnessTicksLeft, ritualTicks, controlTargetId, cooldowns, actionBusy,
         availableActions, actionTarget, 0, false, 0);
   }

   public HeroPublicSnapshot(HeroId heroId, int actionId, int actionElapsed, int actionLength, int hearts,
      boolean lionActive, int lionWindowLeft, int lionWindowMax, boolean lionOverheat, boolean madness,
      int madnessTicksLeft, int ritualTicks, int controlTargetId, int[] cooldowns, boolean actionBusy,
      long availableActions, @Nullable Vec3 actionTarget, int resource, boolean resourceLocked, int heroFlags) {
      this(heroId, actionId, actionElapsed, actionLength, hearts, lionActive, lionWindowLeft, lionWindowMax,
         lionOverheat, madness, madnessTicksLeft, ritualTicks, controlTargetId, cooldowns, actionBusy,
         availableActions, actionTarget, resource, resourceLocked, heroFlags, NO_EXTRA);
   }

   public HeroPublicSnapshot(HeroId heroId, int actionId, int actionElapsed, int actionLength, int hearts,
      boolean lionActive, int lionWindowLeft, int lionWindowMax, boolean lionOverheat, boolean madness,
      int madnessTicksLeft, int ritualTicks, int controlTargetId, int[] cooldowns, boolean actionBusy,
      long availableActions, @Nullable Vec3 actionTarget, int resource, boolean resourceLocked, int heroFlags, int[] extraCooldowns) {
      this(heroId, actionId, actionElapsed, actionLength, hearts, lionActive, lionWindowLeft, lionWindowMax,
         lionOverheat, madness, madnessTicksLeft, ritualTicks, controlTargetId, cooldowns, actionBusy,
         availableActions, actionTarget, resource, resourceLocked, heroFlags, extraCooldowns, 0, 0);
   }

   public HeroPublicSnapshot {
      extraCooldowns = extraCooldowns == null ? NO_EXTRA
         : extraCooldowns.length > EXTRA_COOLDOWN_MAX ? Arrays.copyOf(extraCooldowns, EXTRA_COOLDOWN_MAX) : extraCooldowns;
   }

   /** Extra cooldown ticks at an index; 0 when absent. */
   public int extraCooldown(int index) {
      return index >= 0 && index < this.extraCooldowns.length ? this.extraCooldowns[index] : 0;
   }

   public boolean heroFlag(int bit) {
      return (this.heroFlags & (1 << bit)) != 0;
   }

   public boolean actionAvailable(HeroAction action) {
      return (this.availableActions & (1L << action.ordinal())) != 0;
   }

   /** Bit mask with one {@code 1L << ordinal} bit per action (Reinhard is the first real producer). */
   public static long actionMask(HeroAction... actions) {
      long mask = 0L;
      for (HeroAction action : actions) {
         mask |= 1L << action.ordinal();
      }
      return mask;
   }

   public String encode() {
      StringBuilder builder = new StringBuilder(96);
      builder.append(this.heroId.key()).append(';');
      builder.append(this.actionId).append(';');
      builder.append(this.actionElapsed).append(';');
      builder.append(this.actionLength).append(';');
      builder.append(this.hearts).append(';');
      builder.append(this.lionActive ? 1 : 0).append(';');
      builder.append(this.lionWindowLeft).append(';');
      builder.append(this.lionWindowMax).append(';');
      builder.append(this.lionOverheat ? 1 : 0).append(';');
      builder.append(this.madness ? 1 : 0).append(';');
      builder.append(this.madnessTicksLeft).append(';');
      builder.append(this.ritualTicks).append(';');
      builder.append(this.controlTargetId);

      for (int i = 0; i < COOLDOWN_COUNT; i++) {
         builder.append(';').append(i < this.cooldowns.length ? this.cooldowns[i] : 0);
      }

      builder.append(';').append(this.actionBusy ? 1 : 0);
      builder.append(';').append(this.availableActions);
      boolean hasVariant = this.variant != 0 || this.resource2 != 0;
      boolean hasExtra = this.extraCooldowns.length > 0 || hasVariant;
      boolean hasResource = this.resource != 0 || this.resourceLocked || this.heroFlags != 0 || hasExtra;
      if (this.actionTarget != null) {
         builder.append(';').append(this.actionTarget.x).append(';').append(this.actionTarget.y).append(';').append(this.actionTarget.z);
      } else if (hasResource) {
         builder.append(";n;n;n");
      }

      // Resource fields only when set: heroes without a resource keep the v1.13 wire format.
      if (hasResource) {
         builder.append(';').append(this.resource);
         builder.append(';').append(this.resourceLocked ? 1 : 0);
         builder.append(';').append(this.heroFlags);
      }

      // Extra cooldowns only when present: count, then the values.
      if (hasExtra) {
         builder.append(';').append(this.extraCooldowns.length);
         for (int value : this.extraCooldowns) {
            builder.append(';').append(value);
         }
      }

      // Variant fields after the extras, only when set: older clients stop reading at the extras.
      if (hasVariant) {
         builder.append(';').append(this.variant);
         builder.append(';').append(this.resource2);
      }
      return builder.toString();
   }

   public static HeroPublicSnapshot decode(String encoded) {
      try {
         String[] parts = encoded.split(";");
         if (parts.length < 14) {
            return EMPTY;
         }

         HeroId heroId = HeroId.fromKey(parts[0]);
         if (heroId == null) {
            return EMPTY;
         }

         int[] cooldowns = new int[COOLDOWN_COUNT];

         for (int i = 0; i < COOLDOWN_COUNT; i++) {
            int index = 13 + i;
            cooldowns[i] = index < parts.length ? Integer.parseInt(parts[index]) : 0;
         }

         boolean hasTarget = parts.length >= 24 && !"n".equals(parts[21]);
         Vec3 target = hasTarget ? new Vec3(Double.parseDouble(parts[21]), Double.parseDouble(parts[22]), Double.parseDouble(parts[23])) : null;
         if (target != null && (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z))) {
            return EMPTY;
         }

         int[] extra = NO_EXTRA;
         int variant = 0;
         int resource2 = 0;
         if (parts.length > 27) {
            int wireCount = Integer.parseInt(parts[27]);
            if (wireCount < 0) return EMPTY;
            int count = Math.min(EXTRA_COOLDOWN_MAX, wireCount);
            extra = new int[count];
            for (int i = 0; i < count; i++) {
               int index = 28 + i;
               extra[i] = index < parts.length ? Integer.parseInt(parts[index]) : 0;
            }

            // The tail follows the declared wire array, not its capped local copy.
            // Use long arithmetic so an invalid huge count cannot wrap into earlier fields.
            long after = 28L + wireCount;
            if (after + 1 < parts.length) {
               variant = Integer.parseInt(parts[(int) after]);
               resource2 = Integer.parseInt(parts[(int) after + 1]);
            }
         }
         return new HeroPublicSnapshot(
            heroId,
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2]),
            Integer.parseInt(parts[3]),
            Integer.parseInt(parts[4]),
            "1".equals(parts[5]),
            Integer.parseInt(parts[6]),
            Integer.parseInt(parts[7]),
            "1".equals(parts[8]),
            "1".equals(parts[9]),
            Integer.parseInt(parts[10]),
            Integer.parseInt(parts[11]),
            Integer.parseInt(parts[12]),
            cooldowns,
            parts.length > 19 && "1".equals(parts[19]),
            parts.length > 20 ? Long.parseLong(parts[20]) : -1L,
            target,
            parts.length > 24 ? Integer.parseInt(parts[24]) : 0,
            parts.length > 25 && "1".equals(parts[25]),
            parts.length > 26 ? Integer.parseInt(parts[26]) : 0,
            extra,
            variant,
            resource2
         );
      } catch (NumberFormatException exception) {
         return EMPTY;
      }
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      } else if (!(other instanceof HeroPublicSnapshot snapshot)) {
         return false;
      } else {
         return this.heroId == snapshot.heroId
            && this.actionId == snapshot.actionId
            && this.actionElapsed == snapshot.actionElapsed
            && this.actionLength == snapshot.actionLength
            && this.hearts == snapshot.hearts
            && this.lionActive == snapshot.lionActive
            && this.lionWindowLeft == snapshot.lionWindowLeft
            && this.lionWindowMax == snapshot.lionWindowMax
            && this.lionOverheat == snapshot.lionOverheat
            && this.madness == snapshot.madness
            && this.madnessTicksLeft == snapshot.madnessTicksLeft
            && this.ritualTicks == snapshot.ritualTicks
            && this.controlTargetId == snapshot.controlTargetId
            && Arrays.equals(this.cooldowns, snapshot.cooldowns)
            && this.actionBusy == snapshot.actionBusy
            && this.availableActions == snapshot.availableActions
            && Objects.equals(this.actionTarget, snapshot.actionTarget)
            && this.resource == snapshot.resource
            && this.resourceLocked == snapshot.resourceLocked
            && this.heroFlags == snapshot.heroFlags
            && Arrays.equals(this.extraCooldowns, snapshot.extraCooldowns)
            && this.variant == snapshot.variant
            && this.resource2 == snapshot.resource2;
      }
   }

   @Override
   public int hashCode() {
      int result = this.heroId.hashCode();
      result = 31 * result + this.actionId;
      result = 31 * result + this.actionElapsed;
      result = 31 * result + this.actionLength;
      result = 31 * result + this.hearts;
      result = 31 * result + Boolean.hashCode(this.lionActive);
      result = 31 * result + this.lionWindowLeft;
      result = 31 * result + this.lionWindowMax;
      result = 31 * result + Boolean.hashCode(this.lionOverheat);
      result = 31 * result + Boolean.hashCode(this.madness);
      result = 31 * result + this.madnessTicksLeft;
      result = 31 * result + this.ritualTicks;
      result = 31 * result + this.controlTargetId;
      result = 31 * result + Arrays.hashCode(this.cooldowns);
      result = 31 * result + Boolean.hashCode(this.actionBusy);
      result = 31 * result + Long.hashCode(this.availableActions);
      result = 31 * result + Objects.hashCode(this.actionTarget);
      result = 31 * result + this.resource;
      result = 31 * result + Boolean.hashCode(this.resourceLocked);
      result = 31 * result + this.heroFlags;
      result = 31 * result + Arrays.hashCode(this.extraCooldowns);
      result = 31 * result + this.variant;
      result = 31 * result + this.resource2;
      return result;
   }
}
